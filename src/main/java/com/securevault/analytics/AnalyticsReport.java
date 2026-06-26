package com.securevault.analytics;

import java.util.PriorityQueue;

/**
 * Structured analytics report returned by {@link AnalyticsService#generateReport()}.
 */
public class AnalyticsReport {

    private long totalEncryptions;
    private long totalDecryptions;
    private long totalFailures;
    private long totalBytesEncrypted;
    private PriorityQueue<AlgorithmStat> algorithmStats;

    // Getters and setters
    public long getTotalEncryptions()    { return totalEncryptions; }
    public long getTotalDecryptions()    { return totalDecryptions; }
    public long getTotalFailures()       { return totalFailures; }
    public long getTotalBytesEncrypted() { return totalBytesEncrypted; }
    public PriorityQueue<AlgorithmStat> getAlgorithmStats() { return algorithmStats; }

    public void setTotalEncryptions(long v)    { totalEncryptions = v; }
    public void setTotalDecryptions(long v)    { totalDecryptions = v; }
    public void setTotalFailures(long v)       { totalFailures = v; }
    public void setTotalBytesEncrypted(long v) { totalBytesEncrypted = v; }
    public void setAlgorithmStats(PriorityQueue<AlgorithmStat> q) { algorithmStats = q; }

    @Override
    public String toString() {
        return String.format("""
                ╔══════════════════════ ANALYTICS REPORT ══════════════════════╗
                  Total Encryptions:  %d
                  Total Decryptions:  %d
                  Total Failures:     %d
                  Bytes Encrypted:    %.2f MB
                ╚═══════════════════════════════════════════════════════════════╝
                """,
                totalEncryptions, totalDecryptions, totalFailures,
                totalBytesEncrypted / 1_048_576.0
        );
    }
}
