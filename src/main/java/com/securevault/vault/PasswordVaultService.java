package com.securevault.vault;

import com.securevault.auth.SessionManager;
import com.securevault.database.DatabaseManager;
import com.securevault.encryption.AESStrategy;
import com.securevault.exceptions.SecureVaultException;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service managing CRUD operations and search for stored credentials.
 *
 * <p><b>Security Concept:</b>
 * website passwords are encrypted on-the-fly using AES-256-GCM before writing to the database,
 * utilizing the active user's derived key from the session. On retrieval, passwords are
 * decrypted in-memory. Plaintext passwords never touch the database.
 *
 * <p><b>DSA Concept — TreeMap & Linear Scan:</b>
 * The {@link #search(int, String)} method performs a linear scan filtering credentials
 * based on search criteria, and stores matches in a {@link TreeMap} sorted alphabetically
 * by website to guarantee an O(log N) insertion / sorted display structure.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class PasswordVaultService {

    private final DatabaseManager db;

    public PasswordVaultService() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * Helper to retrieve the current session key.
     */
    private byte[] getSessionKey() {
        SessionManager session = SessionManager.getInstance();
        if (!session.isActive()) {
            throw new SecureVaultException("No active session. Please log in first.");
        }
        byte[] key = session.getDerivedKey();
        if (key == null) {
            throw new SecureVaultException("Cryptographic key is not available in active session.");
        }
        return key;
    }

    /**
     * Adds a new credential, encrypting the password.
     */
    public void addCredential(int ownerId, String website, String username, String plaintextPassword, String notes) {
        byte[] sessionKey = getSessionKey();
        try {
            AESStrategy aes = new AESStrategy(sessionKey);
            byte[] encryptedPassword = aes.encrypt(plaintextPassword.getBytes(StandardCharsets.UTF_8));

            db.executeUpdate(
                    "INSERT INTO credentials (owner_id, website, username, encrypted_password, notes, last_modified) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    ownerId, website, username, encryptedPassword, notes, Timestamp.valueOf(LocalDateTime.now()).toString()
            );
            System.out.println("[PasswordVaultService] Credential saved for website: " + website);
        } finally {
            // Securely overwrite key array copy in memory
            java.util.Arrays.fill(sessionKey, (byte) 0);
        }
    }

    /**
     * Retrieves all credentials for a user, leaving them encrypted (decryption happens on demand).
     */
    public List<Credential> getCredentialsForUser(int ownerId) {
        List<Credential> list = new ArrayList<>();
        String sql = "SELECT * FROM credentials WHERE owner_id = ?";
        try (PreparedStatement ps = db.prepareStatement(sql, ownerId);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new SecureVaultException("Failed to retrieve credentials: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Decrypts the password of a given credential.
     */
    public String decryptPassword(Credential credential) {
        byte[] sessionKey = getSessionKey();
        try {
            AESStrategy aes = new AESStrategy(sessionKey);
            byte[] decryptedBytes = aes.decrypt(credential.getEncryptedPassword());
            String plaintext = new String(decryptedBytes, StandardCharsets.UTF_8);
            // Zero out decrypted bytes immediately
            java.util.Arrays.fill(decryptedBytes, (byte) 0);
            return plaintext;
        } finally {
            java.util.Arrays.fill(sessionKey, (byte) 0);
        }
    }

    /**
     * Updates an existing credential.
     */
    public void updateCredential(int credId, String website, String username, String plaintextPassword, String notes) {
        byte[] sessionKey = getSessionKey();
        try {
            AESStrategy aes = new AESStrategy(sessionKey);
            byte[] encryptedPassword = aes.encrypt(plaintextPassword.getBytes(StandardCharsets.UTF_8));

            db.executeUpdate(
                    "UPDATE credentials SET website = ?, username = ?, encrypted_password = ?, notes = ?, last_modified = ? " +
                            "WHERE cred_id = ?",
                    website, username, encryptedPassword, notes, Timestamp.valueOf(LocalDateTime.now()).toString(), credId
            );
            System.out.println("[PasswordVaultService] Credential updated for ID: " + credId);
        } finally {
            java.util.Arrays.fill(sessionKey, (byte) 0);
        }
    }

    /**
     * Deletes a credential.
     */
    public void deleteCredential(int credId) {
        db.executeUpdate("DELETE FROM credentials WHERE cred_id = ?", credId);
        System.out.println("[PasswordVaultService] Credential deleted with ID: " + credId);
    }

    /**
     * Searches credentials for a given user, returning a TreeMap sorted by website name.
     * Implements linear scan search and stores matching records in a sorted tree structure.
     */
    public Map<String, Credential> search(int ownerId, String keyword) {
        List<Credential> all = getCredentialsForUser(ownerId);
        Map<String, Credential> sortedResults = new TreeMap<>();

        String query = keyword == null ? "" : keyword.toLowerCase().trim();

        for (Credential cred : all) {
            boolean match = query.isEmpty()
                    || cred.getWebsite().toLowerCase().contains(query)
                    || cred.getUsername().toLowerCase().contains(query)
                    || (cred.getNotes() != null && cred.getNotes().toLowerCase().contains(query));

            if (match) {
                // TreeMap key guarantees alphabetical ordering.
                // Include unique ID at the end to prevent collisions if website names are duplicate.
                String sortKey = cred.getWebsite().toLowerCase() + "_" + cred.getCredId();
                sortedResults.put(sortKey, cred);
            }
        }

        return sortedResults;
    }

    private Credential mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("last_modified");
        return new Credential(
                rs.getInt("cred_id"),
                rs.getInt("owner_id"),
                rs.getString("website"),
                rs.getString("username"),
                rs.getBytes("encrypted_password"),
                rs.getString("notes"),
                ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
        );
    }
}
