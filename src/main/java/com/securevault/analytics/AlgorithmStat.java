package com.securevault.analytics;

/**
 * Per-algorithm statistics entry stored in the {@link AnalyticsReport}.
 * Ordered by {@code avgDurationMs} (slowest first) via PriorityQueue.
 */
public record AlgorithmStat(
        String algorithm,
        String operationType,
        double avgDurationMs,
        long   minDurationMs,
        long   maxDurationMs,
        double avgQueueWaitMs,
        double avgMemoryKb,
        long   operationCount
) {
    @Override
    public String toString() {
        return String.format("%-8s %-8s | avg=%6.1fms | min=%4dms | max=%6dms | ops=%d",
                algorithm, operationType, avgDurationMs, minDurationMs, maxDurationMs, operationCount);
    }
}
