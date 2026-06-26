package com.securevault.task;

/**
 * Priority levels for file tasks submitted to the thread pool.
 *
 * <p>Higher ordinal = higher priority. {@link java.util.concurrent.PriorityBlockingQueue}
 * uses the natural ordering from {@link Comparable}, which we map to these levels.
 *
 * <p><b>Interview example:</b>
 * <ul>
 *   <li>Bank transaction encryption → {@code HIGH}</li>
 *   <li>Regular document → {@code NORMAL}</li>
 *   <li>Large media file → {@code LOW}</li>
 * </ul>
 */
public enum TaskPriority {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    private final int value;

    TaskPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
