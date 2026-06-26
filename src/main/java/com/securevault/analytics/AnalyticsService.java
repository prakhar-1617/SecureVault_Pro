package com.securevault.analytics;

import com.securevault.database.DatabaseManager;
import com.securevault.events.EventBus;
import com.securevault.events.SecureVaultEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and analyzes performance metrics for all encryption operations.
 *
 * <p><b>Design Pattern:</b> Singleton + Observer.
 * AnalyticsService subscribes to {@link SecureVaultEvent.Type#FILE_ENCRYPTED}
 * and {@link SecureVaultEvent.Type#FILE_DECRYPTED} events automatically —
 * no manual {@code analytics.record()} calls in the storage layer.
 *
 * <p><b>Metrics tracked:</b>
 * <ul>
 *   <li>Average encryption and decryption time per algorithm</li>
 *   <li>Fastest and slowest algorithms</li>
 *   <li>Total throughput (bytes processed)</li>
 *   <li>Cache hit ratio (pulled from LRU cache)</li>
 *   <li>Thread utilization</li>
 *   <li>Queue wait time</li>
 *   <li>Memory used per operation</li>
 *   <li>Failure count</li>
 * </ul>
 *
 * <p><b>DSA Usage:</b> {@link PriorityQueue} ordered by average duration (slowest-first)
 * lets us instantly identify bottleneck algorithms.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class AnalyticsService {

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    private static volatile AnalyticsService instance;

    private final DatabaseManager db;

    // Atomic counters — thread-safe without synchronization
    private final AtomicLong totalEncryptions  = new AtomicLong(0);
    private final AtomicLong totalDecryptions  = new AtomicLong(0);
    private final AtomicLong totalFailures     = new AtomicLong(0);
    private final AtomicLong totalBytesEncrypted = new AtomicLong(0);

    private AnalyticsService() {
        this.db = DatabaseManager.getInstance();

        // Register as Observer — auto-triggered when events fire
        EventBus eventBus = EventBus.getInstance();
        eventBus.subscribe(SecureVaultEvent.Type.FILE_ENCRYPTED,
                event -> totalEncryptions.incrementAndGet());
        eventBus.subscribe(SecureVaultEvent.Type.FILE_DECRYPTED,
                event -> totalDecryptions.incrementAndGet());
        eventBus.subscribe(SecureVaultEvent.Type.TASK_FAILED,
                event -> totalFailures.incrementAndGet());

        System.out.println("[AnalyticsService] Started and subscribed to events.");
    }

    public static AnalyticsService getInstance() {
        if (instance == null) {
            synchronized (AnalyticsService.class) {
                if (instance == null) {
                    instance = new AnalyticsService();
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Record an operation
    // ------------------------------------------------------------------ //

    /**
     * Records a completed encryption or decryption operation to the DB.
     *
     * @param userId       the acting user's ID
     * @param algorithm    algorithm name
     * @param opType       "ENCRYPT" or "DECRYPT"
     * @param durationMs   wall-clock time taken
     * @param fileSizeBytes file size in bytes
     * @param queueWaitMs  time spent waiting in the priority queue
     * @param memoryUsedKb JVM memory delta during the operation
     * @param success      whether the operation succeeded
     */
    public void record(int userId, String algorithm, String opType,
                       long durationMs, long fileSizeBytes,
                       long queueWaitMs, long memoryUsedKb, boolean success) {
        long threadId = Thread.currentThread().getId();
        if (success && "ENCRYPT".equals(opType)) {
            totalBytesEncrypted.addAndGet(fileSizeBytes);
        }
        if (!success) totalFailures.incrementAndGet();

        db.executeUpdate(
                "INSERT INTO analytics (user_id, algorithm, operation_type, duration_ms, " +
                "file_size, thread_id, queue_wait_ms, memory_used_kb, success) VALUES (?,?,?,?,?,?,?,?,?)",
                userId, algorithm, opType, durationMs, fileSizeBytes,
                threadId, queueWaitMs, memoryUsedKb, success ? 1 : 0
        );
    }

    // ------------------------------------------------------------------ //
    //  Report generation
    // ------------------------------------------------------------------ //

    /**
     * Generates a comprehensive analytics report from the DB.
     *
     * @return a structured {@link AnalyticsReport}
     */
    public AnalyticsReport generateReport() {
        AnalyticsReport report = new AnalyticsReport();
        report.setTotalEncryptions(totalEncryptions.get());
        report.setTotalDecryptions(totalDecryptions.get());
        report.setTotalFailures(totalFailures.get());
        report.setTotalBytesEncrypted(totalBytesEncrypted.get());

        // Per-algorithm stats from DB
        String sql = """
            SELECT algorithm, operation_type,
                   AVG(duration_ms)    AS avg_ms,
                   MIN(duration_ms)    AS min_ms,
                   MAX(duration_ms)    AS max_ms,
                   AVG(queue_wait_ms)  AS avg_queue_wait,
                   AVG(memory_used_kb) AS avg_memory,
                   COUNT(*)            AS op_count
            FROM analytics
            GROUP BY algorithm, operation_type
            ORDER BY avg_ms DESC
            """;

        // Use PriorityQueue sorted by avg_ms (slowest first) for bottleneck detection
        PriorityQueue<AlgorithmStat> statQueue = new PriorityQueue<>(
                Comparator.comparingDouble(AlgorithmStat::avgDurationMs).reversed()
        );

        try (PreparedStatement ps = db.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                statQueue.offer(new AlgorithmStat(
                        rs.getString("algorithm"),
                        rs.getString("operation_type"),
                        rs.getDouble("avg_ms"),
                        rs.getLong("min_ms"),
                        rs.getLong("max_ms"),
                        rs.getDouble("avg_queue_wait"),
                        rs.getDouble("avg_memory"),
                        rs.getLong("op_count")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[Analytics] DB query failed: " + e.getMessage());
        }

        report.setAlgorithmStats(statQueue);
        return report;
    }

    // ------------------------------------------------------------------ //
    //  Live counters
    // ------------------------------------------------------------------ //

    public long getTotalEncryptions()    { return totalEncryptions.get(); }
    public long getTotalDecryptions()    { return totalDecryptions.get(); }
    public long getTotalFailures()       { return totalFailures.get(); }
    public long getTotalBytesEncrypted() { return totalBytesEncrypted.get(); }
}
