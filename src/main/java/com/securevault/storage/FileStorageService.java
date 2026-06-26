package com.securevault.storage;

import com.securevault.analytics.AnalyticsService;
import com.securevault.cache.CacheEntry;
import com.securevault.cache.LRUCache;
import com.securevault.config.ConfigurationManager;
import com.securevault.database.DatabaseManager;
import com.securevault.encryption.AESStrategy;
import com.securevault.encryption.EncryptionFactory;
import com.securevault.encryption.EncryptionStrategy;
import com.securevault.events.EventBus;
import com.securevault.events.SecureVaultEvent;
import com.securevault.exceptions.FileStorageException;
import com.securevault.integrity.ChecksumManager;
import com.securevault.util.HashUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Core file storage service: encrypts, stores, retrieves, and decrypts files.
 *
 * <p><b>Streaming strategy — two modes:</b>
 * <ul>
 *   <li><b>Small files (&lt; NIO threshold):</b> {@link BufferedInputStream} /
 *       {@link BufferedOutputStream} — simpler API, automatic buffering.</li>
 *   <li><b>Large files (&ge; NIO threshold, default 10 MB):</b> NIO {@link FileChannel}
 *       + {@link ByteBuffer} — bypasses Java heap for the buffer (direct I/O),
 *       reduces GC pressure, and uses OS-level scatter/gather I/O.</li>
 * </ul>
 *
 * <p><b>Interview Q:</b> "Difference between InputStream and FileChannel?"
 * <ul>
 *   <li>{@code InputStream} is byte-stream oriented. Reads go through heap buffers.</li>
 *   <li>{@code FileChannel} is channel + buffer oriented. Supports direct byte buffers
 *       (off-heap), memory-mapped files, and {@code transferTo} for zero-copy I/O.</li>
 * </ul>
 *
 * <p><b>Cache integration:</b> File metadata is cached by content hash.
 * Uploading the same file twice hits the cache on the second call.
 *
 * <p><b>Search support:</b> {@link #searchFiles} queries the DB with
 * SQL filtering on name, algorithm, size, and upload date.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class FileStorageService {

    private static volatile FileStorageService instance;

    public static FileStorageService getInstance() {
        if (instance == null) {
            synchronized (FileStorageService.class) {
                if (instance == null) {
                    instance = new FileStorageService();
                }
            }
        }
        return instance;
    }

    private final DatabaseManager    db;
    private final ChecksumManager    checksumManager;
    private final AnalyticsService   analytics;
    private final EventBus           eventBus;
    private final LRUCache<String, CacheEntry> cache;
    private final Path               storageRoot;
    private final long               nioThreshold;
    private final int                bufferSize;

    public FileStorageService() {
        ConfigurationManager cfg = ConfigurationManager.getInstance();
        this.db             = DatabaseManager.getInstance();
        this.checksumManager = new ChecksumManager();
        this.analytics      = AnalyticsService.getInstance();
        this.eventBus       = EventBus.getInstance();
        this.cache          = new LRUCache<>();
        this.storageRoot    = Path.of(cfg.getEncryptedFilesDir());
        this.nioThreshold   = cfg.getNioThresholdBytes();
        this.bufferSize     = cfg.getBufferSize();

        // Ensure storage directory exists
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new FileStorageException("Cannot create storage directory: " + storageRoot,
                    FileStorageException.WRITE_FAILED, e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Upload (Encrypt + Store)
    // ------------------------------------------------------------------ //

    /**
     * Encrypts and stores a file in the vault.
     *
     * <ol>
     *   <li>Compute SHA-256 of original file (content hash)</li>
     *   <li>Check LRU cache — cache hit means identical file already stored</li>
     *   <li>Read file bytes (Buffered or NIO based on size)</li>
     *   <li>Encrypt using chosen algorithm</li>
     *   <li>Write encrypted bytes to disk</li>
     *   <li>Persist metadata to DB</li>
     *   <li>Update LRU cache</li>
     *   <li>Publish FILE_ENCRYPTED event</li>
     * </ol>
     *
     * @param source         path of the plaintext file to encrypt
     * @param userId         ID of the owning user
     * @param algorithmName  algorithm key: "AES", "XOR", or "CAESAR"
     * @return the database ID of the stored file record
     */
    public long uploadFile(Path source, int userId, String algorithmName) {
        if (!Files.exists(source)) {
            throw new FileStorageException("Source file not found: " + source,
                    FileStorageException.FILE_NOT_FOUND);
        }

        long startMs = System.currentTimeMillis();
        long startMem = usedMemoryKb();

        // ---- Step 1: Compute content hash (LRU cache key) ----
        String contentHash = checksumManager.compute(source);

        // ---- Step 2: Check cache ----
        CacheEntry cached = cache.get(contentHash);
        if (cached != null && cached.algorithm().equals(algorithmName)) {
            System.out.println("[Storage] Cache HIT for: " + source.getFileName());
            // Still create a new DB record (different upload instance)
            return persistFileRecord(userId, source.getFileName().toString(),
                    cached.encryptedPath(), contentHash, algorithmName,
                    cached.fileSize());
        }
        System.out.println("[Storage] Cache MISS for: " + source.getFileName());

        // ---- Step 3: Read file bytes ----
        long fileSize = getFileSize(source);
        byte[] plaintext = fileSize >= nioThreshold
                ? readWithNIO(source, fileSize)
                : readWithBufferedStream(source);

        // ---- Step 4: Encrypt ----
        EncryptionStrategy strategy = EncryptionFactory.createStrategy(algorithmName);
        byte[] ciphertext = strategy.encrypt(plaintext);

        // If AES, save the key alongside the file for decryption
        String keyFilePath = null;
        if (strategy instanceof AESStrategy aes) {
            keyFilePath = saveAESKey(contentHash, aes.getKeyBytes());
        }

        // ---- Step 5: Write encrypted file ----
        String encryptedFileName = contentHash + ".svp";
        Path encryptedPath = storageRoot.resolve(encryptedFileName);
        if (fileSize >= nioThreshold) {
            writeWithNIO(encryptedPath, ciphertext);
        } else {
            writeWithBufferedStream(encryptedPath, ciphertext);
        }

        // ---- Step 6: Persist to DB ----
        long fileId = persistFileRecord(userId, source.getFileName().toString(),
                encryptedPath.toString(), contentHash, algorithmName, fileSize);

        // ---- Step 7: Update cache ----
        cache.put(contentHash, new CacheEntry(
                encryptedPath.toString(), algorithmName, contentHash,
                fileSize, System.currentTimeMillis()
        ));

        // ---- Step 8: Analytics + Event ----
        long durationMs = System.currentTimeMillis() - startMs;
        long memUsed    = usedMemoryKb() - startMem;
        analytics.record(userId, algorithmName, "ENCRYPT", durationMs, fileSize, 0, memUsed, true);
        eventBus.publish(SecureVaultEvent.fileEncrypted(userId, source.getFileName().toString(), algorithmName));

        System.out.printf("[Storage] Encrypted '%s' → '%s' [%dms]%n",
                source.getFileName(), encryptedFileName, durationMs);
        return fileId;
    }

    // ------------------------------------------------------------------ //
    //  Download (Decrypt + Verify)
    // ------------------------------------------------------------------ //

    /**
     * Retrieves and decrypts a file from the vault to the destination path.
     *
     * @param fileId  the database file ID
     * @param dest    where to write the decrypted file
     * @throws FileStorageException if the file is not found
     */
    public void downloadFile(int fileId, Path dest) {
        long startMs = System.currentTimeMillis();

        // ---- Load metadata from DB ----
        FileMetadata meta = loadMetadata(fileId);
        if (meta == null) {
            throw new FileStorageException("File not found in DB: " + fileId,
                    FileStorageException.FILE_NOT_FOUND);
        }

        // ---- Read encrypted bytes ----
        Path encryptedPath = Path.of(meta.encryptedPath());
        if (!Files.exists(encryptedPath)) {
            throw new FileStorageException("Encrypted file missing from disk: " + encryptedPath,
                    FileStorageException.FILE_NOT_FOUND);
        }

        long fileSize  = getFileSize(encryptedPath);
        byte[] ciphertext = fileSize >= nioThreshold
                ? readWithNIO(encryptedPath, fileSize)
                : readWithBufferedStream(encryptedPath);

        // ---- Decrypt ----
        EncryptionStrategy strategy;
        if ("AES".equals(meta.algorithm())) {
            byte[] keyBytes = loadAESKey(meta.checksum());
            strategy = new com.securevault.encryption.AESStrategy(keyBytes);
        } else {
            strategy = EncryptionFactory.createStrategy(meta.algorithm());
        }
        byte[] plaintext = strategy.decrypt(ciphertext);

        // ---- Integrity verification ----
        checksumManager.verify(plaintext, meta.checksum());

        // ---- Write decrypted file ----
        if (fileSize >= nioThreshold) {
            writeWithNIO(dest, plaintext);
        } else {
            writeWithBufferedStream(dest, plaintext);
        }

        // ---- Analytics + Event ----
        long durationMs = System.currentTimeMillis() - startMs;
        analytics.record(meta.userId(), meta.algorithm(), "DECRYPT",
                durationMs, plaintext.length, 0, 0, true);
        eventBus.publish(SecureVaultEvent.fileDecrypted(meta.userId(), meta.originalName()));

        System.out.printf("[Storage] Decrypted file %d → '%s' [%dms]%n", fileId, dest, durationMs);
    }

    // ------------------------------------------------------------------ //
    //  Search
    // ------------------------------------------------------------------ //

    /**
     * Searches files in the database with optional filters.
     *
     * <p>Uses SQL {@code LIKE} for name search, and exact match for algorithm.
     * Results support pagination (offset + limit).
     *
     * @param userId       filter by owner (required)
     * @param nameFilter   partial file name match (null = no filter)
     * @param algorithm    exact algorithm match (null = no filter)
     * @param minSize      minimum file size in bytes (0 = no filter)
     * @param maxSize      maximum file size in bytes (Long.MAX_VALUE = no filter)
     * @param limit        max results to return
     * @param offset       pagination offset
     * @return list of matching file metadata
     */
    public List<FileMetadata> searchFiles(int userId, String nameFilter, String algorithm,
                                          long minSize, long maxSize, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM files WHERE owner_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (nameFilter != null && !nameFilter.isBlank()) {
            sql.append(" AND original_name LIKE ?");
            params.add("%" + nameFilter + "%");
        }
        if (algorithm != null && !algorithm.isBlank()) {
            sql.append(" AND algorithm = ?");
            params.add(algorithm);
        }
        if (minSize > 0) {
            sql.append(" AND file_size >= ?");
            params.add(minSize);
        }
        if (maxSize < Long.MAX_VALUE) {
            sql.append(" AND file_size <= ?");
            params.add(maxSize);
        }
        sql.append(" ORDER BY upload_time DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<FileMetadata> results = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement(sql.toString(), params.toArray());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new FileMetadata(
                        rs.getInt("file_id"),
                        rs.getInt("owner_id"),
                        rs.getString("original_name"),
                        rs.getString("encrypted_path"),
                        rs.getString("checksum"),
                        rs.getString("algorithm"),
                        rs.getLong("file_size"),
                        rs.getTimestamp("upload_time") != null
                                ? rs.getTimestamp("upload_time").toLocalDateTime().toString()
                                : ""
                ));
            }
        } catch (SQLException e) {
            throw new FileStorageException("Search query failed: " + e.getMessage(),
                    FileStorageException.READ_FAILED, e);
        }
        return results;
    }

    /**
     * Deletes a file from DB and disk.
     */
    public void deleteFile(int fileId, int userId) {
        FileMetadata meta = loadMetadata(fileId);
        if (meta == null || meta.userId() != userId) {
            throw new FileStorageException("File not found or access denied: " + fileId,
                    FileStorageException.FILE_NOT_FOUND);
        }
        // Delete from disk
        try {
            Files.deleteIfExists(Path.of(meta.encryptedPath()));
        } catch (IOException e) {
            System.err.println("[Storage] Warning: could not delete file from disk: " + e.getMessage());
        }
        // Delete from DB
        db.executeUpdate("DELETE FROM files WHERE file_id = ? AND owner_id = ?", fileId, userId);
        // Evict from cache
        cache.evict(meta.checksum());
        System.out.println("[Storage] Deleted file: " + fileId);
    }

    public LRUCache<String, CacheEntry> getCache() { return cache; }

    // ------------------------------------------------------------------ //
    //  Buffered Stream I/O (small files)
    // ------------------------------------------------------------------ //

    private byte[] readWithBufferedStream(Path path) {
        try (BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(path.toFile()), bufferSize)) {
            return bis.readAllBytes();
        } catch (IOException e) {
            throw new FileStorageException("BufferedStream read failed: " + path,
                    FileStorageException.READ_FAILED, e);
        }
    }

    private void writeWithBufferedStream(Path path, byte[] data) {
        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(path.toFile()), bufferSize)) {
            bos.write(data);
        } catch (IOException e) {
            throw new FileStorageException("BufferedStream write failed: " + path,
                    FileStorageException.WRITE_FAILED, e);
        }
    }

    // ------------------------------------------------------------------ //
    //  NIO FileChannel I/O (large files)
    // ------------------------------------------------------------------ //

    /**
     * Reads a file using NIO {@link FileChannel} + direct {@link ByteBuffer}.
     *
     * <p><b>Interview Q — Why NIO for large files?</b>
     * {@code FileChannel} allows allocating a {@code DirectByteBuffer} (off-heap).
     * The OS can then DMA directly to/from the buffer without copying through the JVM heap,
     * reducing GC pressure on large payloads.
     */
    private byte[] readWithNIO(Path path, long fileSize) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect((int) Math.min(fileSize, Integer.MAX_VALUE));
            byte[] result = new byte[(int) fileSize];
            int totalRead = 0;
            while (totalRead < fileSize) {
                int read = channel.read(buffer);
                if (read == -1) break;
                buffer.flip();
                buffer.get(result, totalRead, read);
                buffer.clear();
                totalRead += read;
            }
            return result;
        } catch (IOException e) {
            throw new FileStorageException("NIO read failed: " + path,
                    FileStorageException.READ_FAILED, e);
        }
    }

    private void writeWithNIO(Path path, byte[] data) {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            throw new FileStorageException("NIO write failed: " + path,
                    FileStorageException.WRITE_FAILED, e);
        }
    }

    // ------------------------------------------------------------------ //
    //  AES Key persistence
    // ------------------------------------------------------------------ //

    private String saveAESKey(String contentHash, byte[] keyBytes) {
        Path keyPath = storageRoot.resolve(contentHash + ".key");
        try {
            Files.write(keyPath, keyBytes);
            return keyPath.toString();
        } catch (IOException e) {
            throw new FileStorageException("Failed to save AES key", FileStorageException.WRITE_FAILED, e);
        }
    }

    private byte[] loadAESKey(String contentHash) {
        Path keyPath = storageRoot.resolve(contentHash + ".key");
        if (!Files.exists(keyPath)) {
            throw new FileStorageException("AES key file missing: " + keyPath,
                    FileStorageException.FILE_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(keyPath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to load AES key", FileStorageException.READ_FAILED, e);
        }
    }

    // ------------------------------------------------------------------ //
    //  DB helpers
    // ------------------------------------------------------------------ //

    private long persistFileRecord(int userId, String originalName, String encryptedPath,
                                   String checksum, String algorithm, long fileSize) {
        return db.executeUpdate(
                "INSERT INTO files (owner_id, original_name, encrypted_path, checksum, algorithm, file_size) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                userId, originalName, encryptedPath, checksum, algorithm, fileSize
        );
    }

    public FileMetadata loadMetadata(int fileId) {
        String sql = "SELECT * FROM files WHERE file_id = ?";
        try (PreparedStatement ps = db.prepareStatement(sql, fileId);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new FileMetadata(
                        rs.getInt("file_id"),
                        rs.getInt("owner_id"),
                        rs.getString("original_name"),
                        rs.getString("encrypted_path"),
                        rs.getString("checksum"),
                        rs.getString("algorithm"),
                        rs.getLong("file_size"),
                        rs.getTimestamp("upload_time") != null
                                ? rs.getTimestamp("upload_time").toLocalDateTime().toString()
                                : ""
                );
            }
        } catch (SQLException e) {
            throw new FileStorageException("DB metadata load failed: " + e.getMessage(),
                    FileStorageException.READ_FAILED, e);
        }
        return null;
    }

    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private long usedMemoryKb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024;
    }
}
