package com.learn;

import java.util.concurrent.BlockingQueue;

/**
 * PoolWorker - A worker thread that processes tasks from the queue.
 * 
 * This class extends Thread, meaning each PoolWorker runs in its own thread.
 * 
 * Lifecycle:
 * 1. Created by MiniThreadPool
 * 2. Started (begins running)
 * 3. Continuously takes tasks from queue and executes them
 * 4. Exits when it receives a poison pill (shutdown signal)
 * 
 * The worker runs in a loop:
 * - Take a task from the queue (blocks if queue is empty)
 * - Check if it's a poison pill (if yes, exit)
 * - Execute the task
 * - Mark itself as not busy
 * - Repeat
 */
public class PoolWorker extends Thread {

    /**
     * Reference to the shared task queue.
     * All workers share the same queue - they compete for tasks.
     * This is thread-safe because BlockingQueue handles synchronization.
     */
    private final BlockingQueue<WorkTask> taskQueue;
    
    /**
     * Flag indicating if this worker is currently processing a task.
     * 
     * volatile ensures:
     * - Changes are immediately visible to other threads
     * - Used by MiniThreadPool.printStats() to check worker status
     * 
     * true = worker is executing a task
     * false = worker is idle (waiting for a task)
     */
    private volatile boolean isBusy = false;

    /**
     * Constructor - Creates a new worker thread.
     * 
     * @param name The name of this worker thread (for logging/debugging)
     * @param taskQueue The shared queue from which this worker will take tasks
     */
    public PoolWorker(final String name, final BlockingQueue taskQueue) {
        // Call Thread constructor to set the thread name
        super(name);
        // Store reference to the shared task queue
        this.taskQueue = taskQueue;
    }

    /**
     * The main method that runs when the thread starts.
     * 
     * This method runs in a loop until:
     * - The thread is interrupted, OR
     * - A poison pill is received (shutdown signal)
     * 
     * In each iteration:
     * 1. Take a task from the queue (blocks if queue is empty)
     * 2. Check if it's a poison pill (if yes, exit loop)
     * 3. Execute the task
     * 4. Calculate and log timing information
     * 5. Mark worker as idle
     * 6. Repeat
     */
    @Override
    public void run() {
        // Main loop - continues until thread is interrupted or poison pill received
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Take a task from the queue
                // This BLOCKS if the queue is empty - thread waits here until a task arrives
                // This is efficient - no busy waiting, thread sleeps until work is available
                WorkTask task = taskQueue.take();

                // Check if this is a poison pill (shutdown signal)
                // Poison pills are special tasks that tell workers to stop
                if (task.isPoisonPill()) {
                    System.out.println("[" + getName() + "] Received poison pill, shutting down");
                    // Exit the loop - this will cause the thread to terminate
                    break;
                }

                // Calculate how long the task waited in the queue
                // This helps us understand queue congestion
                long startTime = System.currentTimeMillis();
                long queueWaitTime = startTime - task.getSubmissionTime();
                System.out.println("[" + getName() + "] Picked up " + task.getTaskId());
                
                // Execute the task
                // We use try-finally to ensure isBusy is always reset, even if task throws exception
                try {
                    // Mark worker as busy - other code can check this status
                    isBusy = true;
                    
                    // Actually execute the task
                    // This calls WorkTask.run(), which then calls the user's Runnable
                    task.run();
                    
                } finally {
                    // Always mark worker as idle, even if task threw an exception
                    // This ensures worker status is accurate
                    isBusy = false;
                    
                    // Calculate execution time and log metrics
                    long executionTime = System.currentTimeMillis() - startTime;
                    System.out.printf("[%s] Completed %s | Wait Time: %dms | Execution Time: %dms%n",
                            getName(), task.getTaskId(), queueWaitTime, executionTime);
                }

            } catch (InterruptedException e) {
                // Thread was interrupted (external signal to stop)
                // Restore interrupt flag and exit loop
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // Catch any exception thrown by the task
                // We catch Throwable (not just Exception) to catch Errors too
                // This prevents one bad task from killing the worker thread
                isBusy = false;
                System.err.println("[" + getName() + "] Task failed: " + t.getMessage());
                // Continue loop - worker will try to process next task
            }
        }
        // Loop exited - thread will now terminate
    }

    /**
     * Checks if this worker is currently processing a task.
     * 
     * Used by MiniThreadPool.printStats() to show worker status.
     * 
     * @return true if worker is executing a task, false if idle
     */
    public final boolean isBusy() {
        return isBusy;
    }

}
