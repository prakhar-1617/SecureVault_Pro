package com.securevault.task;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Represents a file encryption or decryption task submitted to the thread pool.
 *
 * <p>Implements {@link Comparable} so it can be ordered inside a
 * {@link java.util.concurrent.PriorityBlockingQueue}: higher-priority tasks
 * are dequeued first.
 *
 * <p>Implements {@link Callable} so the thread pool can execute it and return
 * a result (the output file path) via a {@link java.util.concurrent.Future}.
 *
 * <p><b>DSA Usage:</b> {@code PriorityBlockingQueue} is backed by a binary heap.
 * Enqueue is O(log n), dequeue is O(log n). Natural order comes from
 * {@link #compareTo} which compares {@link TaskPriority} values.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class FileTask implements Comparable<FileTask>, Callable<Path> {

    // ------------------------------------------------------------------ //
    //  Fields
    // ------------------------------------------------------------------ //

    private final String        taskId;
    private final Path          sourcePath;
    private final int           userId;
    private final Operation     operation;
    private final TaskPriority  priority;
    private final LocalDateTime submittedAt;
    private final String        algorithm;

    /** Whether to encrypt or decrypt. */
    public enum Operation { ENCRYPT, DECRYPT }

    // ------------------------------------------------------------------ //
    //  The actual work — injected via Callable pattern
    // ------------------------------------------------------------------ //

    private final Callable<Path> work;

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //

    /**
     * @param sourcePath  path of the file to process
     * @param userId      owner's user ID (for audit logging)
     * @param operation   ENCRYPT or DECRYPT
     * @param priority    task priority level
     * @param algorithm   encryption algorithm name
     * @param work        lambda containing the actual encryption/decryption logic
     */
    public FileTask(Path sourcePath, int userId, Operation operation,
                    TaskPriority priority, String algorithm, Callable<Path> work) {
        this.taskId      = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.sourcePath  = sourcePath;
        this.userId      = userId;
        this.operation   = operation;
        this.priority    = priority;
        this.algorithm   = algorithm;
        this.submittedAt = LocalDateTime.now();
        this.work        = work;
    }

    // ------------------------------------------------------------------ //
    //  Callable — executes the task
    // ------------------------------------------------------------------ //

    /**
     * Executes the task's work and returns the output path.
     *
     * @return path of the resulting (encrypted or decrypted) file
     * @throws Exception if the operation fails
     */
    @Override
    public Path call() throws Exception {
        System.out.printf("[Task %s] %s | priority=%s | file=%s | thread=%s%n",
                taskId, operation, priority,
                sourcePath.getFileName(), Thread.currentThread().getName());
        return work.call();
    }

    // ------------------------------------------------------------------ //
    //  Comparable — drives PriorityBlockingQueue ordering
    // ------------------------------------------------------------------ //

    /**
     * Higher priority tasks sort first (max-heap behaviour).
     * If priorities are equal, earlier submission time wins (FIFO within priority).
     */
    @Override
    public int compareTo(FileTask other) {
        int priorityCmp = Integer.compare(other.priority.getValue(), this.priority.getValue());
        if (priorityCmp != 0) return priorityCmp;
        return this.submittedAt.compareTo(other.submittedAt);
    }

    // ------------------------------------------------------------------ //
    //  Getters
    // ------------------------------------------------------------------ //

    public String        getTaskId()      { return taskId;      }
    public Path          getSourcePath()  { return sourcePath;  }
    public int           getUserId()      { return userId;      }
    public Operation     getOperation()   { return operation;   }
    public TaskPriority  getPriority()    { return priority;    }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String        getAlgorithm()   { return algorithm;   }

    @Override
    public String toString() {
        return String.format("FileTask[%s | %s | %s | %s]",
                taskId, operation, priority, sourcePath.getFileName());
    }
}
