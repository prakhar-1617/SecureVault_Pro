package com.securevault.logger;

import com.securevault.config.ConfigurationManager;
import com.securevault.database.DatabaseManager;
import com.securevault.events.EventBus;
import com.securevault.events.SecureVaultEvent;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Singleton audit logger that records all application events to an
 * in-memory queue, then asynchronously flushes them to the database.
 *
 * <p><b>Design Pattern:</b> Singleton + Observer.
 * AuditLogger subscribes to ALL events on the {@link EventBus}. When
 * any event fires (login, file encrypt, task complete), this logger's
 * {@code onEvent()} handler is automatically invoked — no explicit
 * {@code auditLogger.log()} calls scattered throughout the codebase.
 *
 * <p><b>Why ConcurrentLinkedQueue?</b>
 * Multiple worker threads may complete tasks simultaneously, each publishing
 * events on the EventBus. {@link ConcurrentLinkedQueue} is a non-blocking,
 * lock-free queue that supports concurrent producers without synchronization
 * overhead. It guarantees FIFO ordering for each thread.
 *
 * <p><b>Async flush:</b> A {@link ScheduledExecutorService} flushes the queue
 * to the DB every N seconds (configurable). This batches DB writes, reducing
 * I/O overhead compared to writing every event immediately.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class AuditLogger {

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    private static volatile AuditLogger instance;

    /** Lock-free queue supporting concurrent producers. */
    private final ConcurrentLinkedQueue<LogEntry> queue;

    private final DatabaseManager db;
    private final ScheduledExecutorService scheduler;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ------------------------------------------------------------------ //
    //  LogEntry (inner record)
    // ------------------------------------------------------------------ //

    /**
     * Immutable log entry stored in the queue before DB flush.
     */
    public record LogEntry(
            Integer userId,
            String action,
            String detail,
            LocalDateTime timestamp
    ) {
        @Override public String toString() {
            return String.format("[%s] %-20s | user=%-5s | %s",
                    FORMATTER.format(timestamp),
                    action,
                    userId == null ? "sys" : userId,
                    detail);
        }
    }

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //

    private AuditLogger() {
        this.queue     = new ConcurrentLinkedQueue<>();
        this.db        = DatabaseManager.getInstance();

        int flushInterval = ConfigurationManager.getInstance().getAuditFlushIntervalSeconds();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SVP-AuditFlusher");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic flush
        scheduler.scheduleAtFixedRate(this::flushToDatabase,
                flushInterval, flushInterval, TimeUnit.SECONDS);

        // Register as Observer — listens to ALL events
        EventBus.getInstance().subscribeAll(this::onEvent);

        System.out.println("[AuditLogger] Started. Flush interval: " + flushInterval + "s");
    }

    public static AuditLogger getInstance() {
        if (instance == null) {
            synchronized (AuditLogger.class) {
                if (instance == null) {
                    instance = new AuditLogger();
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Observer callback
    // ------------------------------------------------------------------ //

    /**
     * Called by {@link EventBus} whenever any application event fires.
     * Adds a log entry to the in-memory queue (non-blocking).
     *
     * @param event the application event
     */
    private void onEvent(SecureVaultEvent event) {
        log(event.getUserId() == -1 ? null : event.getUserId(),
                event.getType().name(),
                event.getDetail());
    }

    // ------------------------------------------------------------------ //
    //  Public logging API
    // ------------------------------------------------------------------ //

    /**
     * Manually logs an event (for cases not covered by the EventBus).
     *
     * @param userId  the acting user's ID (null for system events)
     * @param action  short action name (e.g., "FILE_UPLOAD")
     * @param detail  descriptive message
     */
    public void log(Integer userId, String action, String detail) {
        LogEntry entry = new LogEntry(userId, action, detail, LocalDateTime.now());
        queue.offer(entry);   // Non-blocking: O(1) lock-free enqueue
        System.out.println("[AUDIT] " + entry);
    }

    // ------------------------------------------------------------------ //
    //  Query
    // ------------------------------------------------------------------ //

    /**
     * Returns all log entries currently in the in-memory queue (not yet flushed).
     */
    public List<LogEntry> getPendingLogs() {
        return List.copyOf(queue);
    }

    // ------------------------------------------------------------------ //
    //  Export
    // ------------------------------------------------------------------ //

    /**
     * Exports audit logs from the database to a CSV file.
     *
     * @param outputPath the target CSV file path
     * @param userId     filter by user (null = all users)
     */
    public void exportToCSV(Path outputPath, Integer userId) {
        String sql = userId == null
                ? "SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 10000"
                : "SELECT * FROM audit_logs WHERE user_id = ? ORDER BY timestamp DESC LIMIT 10000";

        try (var ps = userId == null
                    ? db.prepareStatement(sql)
                    : db.prepareStatement(sql, userId);
             var rs = ps.executeQuery();
             var writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {

            writer.write("log_id,user_id,action,detail,timestamp");
            writer.newLine();

            while (rs.next()) {
                writer.write(String.format("%d,%s,%s,\"%s\",%s",
                        rs.getLong("log_id"),
                        rs.getObject("user_id"),
                        rs.getString("action"),
                        rs.getString("detail") != null ? rs.getString("detail").replace("\"", "'") : "",
                        rs.getString("timestamp")
                ));
                writer.newLine();
            }
            System.out.println("[AuditLogger] Exported CSV to: " + outputPath);

        } catch (Exception e) {
            System.err.println("[AuditLogger] CSV export failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Async flush
    // ------------------------------------------------------------------ //

    /**
     * Drains the in-memory queue and bulk-inserts all entries into the DB.
     * Called periodically by the scheduler and on shutdown.
     */
    private void flushToDatabase() {
        if (queue.isEmpty()) return;

        int flushed = 0;
        LogEntry entry;
        while ((entry = queue.poll()) != null) {
            try {
                db.executeUpdate(
                        "INSERT INTO audit_logs (user_id, action, detail) VALUES (?, ?, ?)",
                        entry.userId(), entry.action(), entry.detail()
                );
                flushed++;
            } catch (Exception e) {
                System.err.println("[AuditLogger] DB insert failed: " + e.getMessage());
            }
        }
        if (flushed > 0) {
            System.out.printf("[AuditLogger] Flushed %d entries to DB.%n", flushed);
        }
    }

    /**
     * Flushes remaining entries and shuts down the scheduler.
     * Call this on application exit.
     */
    public void shutdown() {
        flushToDatabase();
        scheduler.shutdown();
    }
}
