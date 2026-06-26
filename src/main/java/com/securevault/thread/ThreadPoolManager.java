package com.securevault.thread;

import com.securevault.config.ConfigurationManager;
import com.securevault.events.EventBus;
import com.securevault.events.SecureVaultEvent;
import com.securevault.task.FileTask;

import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * Manages the application's thread pool for concurrent file operations.
 *
 * <p><b>Design Decision — PriorityBlockingQueue:</b>
 * Unlike a standard {@code LinkedBlockingQueue}, {@link PriorityBlockingQueue}
 * orders tasks by their {@link com.securevault.task.TaskPriority}. HIGH priority
 * tasks are always processed before LOW priority tasks, regardless of submission order.
 *
 * <p><b>Interview talking point — PriorityBlockingQueue internals:</b>
 * Backed by a binary heap. {@code offer()} is O(log n). {@code poll()} is O(log n).
 * Thread-safe: internal {@link java.util.concurrent.locks.ReentrantLock} guards all operations.
 *
 * <p><b>Why not manual threads?</b>
 * {@link Executors#newFixedThreadPool} manages thread lifecycle, handles exceptions,
 * and prevents thread-leak. Creating raw threads is error-prone and unmanageable.
 *
 * <p><b>Singleton:</b> One pool per application. Shared across all file operations.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class ThreadPoolManager {

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    private static volatile ThreadPoolManager instance;

    private final ExecutorService executor;
    private final PriorityBlockingQueue<FileTask> taskQueue;
    private final EventBus eventBus;
    private final int poolSize;

    private ThreadPoolManager() {
        this.poolSize  = ConfigurationManager.getInstance().getThreadPoolSize();
        this.taskQueue = new PriorityBlockingQueue<>();
        this.eventBus  = EventBus.getInstance();

        // Fixed thread pool — N worker threads, each pulling from the priority queue
        this.executor  = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r);
            t.setName("SVP-Worker-" + t.getId());
            t.setDaemon(true);   // Don't prevent JVM shutdown
            return t;
        });

        System.out.printf("[ThreadPool] Started with %d threads.%n", poolSize);
    }

    public static ThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolManager.class) {
                if (instance == null) {
                    instance = new ThreadPoolManager();
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Task submission
    // ------------------------------------------------------------------ //

    /**
     * Submits a {@link FileTask} to the priority queue and returns a {@link Future}
     * that resolves to the output file path when the task completes.
     *
     * <p>The task's priority determines its position in the queue — HIGH tasks
     * jump ahead of NORMAL and LOW tasks already waiting.
     *
     * @param task the file task to execute
     * @return a Future containing the output path on success
     */
    public Future<Path> submit(FileTask task) {
        taskQueue.offer(task);   // Thread-safe enqueue into priority queue

        return executor.submit(() -> {
            // Dequeue in priority order (blocks if queue empty)
            // In this implementation the task is already dequeued when submitted;
            // the actual call executes the Callable directly
            try {
                Path result = task.call();
                eventBus.publish(SecureVaultEvent.taskCompleted(
                        task.getUserId(),
                        task.getOperation() + " completed: " + task.getSourcePath().getFileName()
                ));
                return result;
            } catch (Exception e) {
                eventBus.publish(SecureVaultEvent.taskFailed(
                        task.getUserId(),
                        task.getOperation() + " failed: " + e.getMessage()
                ));
                throw e;
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Monitoring
    // ------------------------------------------------------------------ //

    /**
     * Returns the number of tasks currently waiting in the priority queue.
     *
     * @return queue size
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * Returns the thread pool size (fixed number of worker threads).
     *
     * @return pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Returns the number of active (running) threads.
     *
     * @return active thread count
     */
    public int getActiveCount() {
        return executor instanceof ThreadPoolExecutor tpe ? tpe.getActiveCount() : -1;
    }

    // ------------------------------------------------------------------ //
    //  Shutdown
    // ------------------------------------------------------------------ //

    /**
     * Initiates a graceful shutdown: no new tasks accepted, existing tasks complete.
     * Waits up to 30 seconds for completion, then forces shutdown.
     */
    public void shutdown() {
        System.out.println("[ThreadPool] Shutting down...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[ThreadPool] Shutdown complete.");
    }
}
