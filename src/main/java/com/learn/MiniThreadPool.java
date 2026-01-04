package com.learn;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MiniThreadPool - A simple thread pool implementation.
 * 
 * This class manages a pool of worker threads and a queue of tasks.
 * 
 * How it works:
 * 1. Creates a fixed number of worker threads (poolSize)
 * 2. Maintains a queue to hold tasks waiting to be processed
 * 3. Workers continuously take tasks from the queue and execute them
 * 4. When shutdown is called, sends "poison pills" to stop workers gracefully
 * 
 * Thread Safety:
 * - BlockingQueue is thread-safe (multiple threads can safely add/take items)
 * - AtomicLong is thread-safe (used for generating unique task IDs)
 * - volatile boolean ensures visibility of shutdown flag across threads
 */
public class MiniThreadPool {

    /**
     * Task queue - holds tasks waiting to be processed by workers.
     * 
     * BlockingQueue means:
     * - put() blocks if queue is full (waits until space is available)
     * - take() blocks if queue is empty (waits until a task arrives)
     * 
     * This is thread-safe - multiple threads can safely add/take tasks.
     */
    private BlockingQueue<WorkTask> taskQueue = null;
    
    /**
     * Number of worker threads in the pool.
     * These are the threads that will actually execute the tasks.
     */
    private final int poolSize;
    
    /**
     * Maximum number of tasks that can wait in the queue.
     * If queue is full and we try to submit a task, put() will block.
     */
    private final int maxQueueSize;
    
    /**
     * List of all worker threads in the pool.
     * We keep references to them so we can manage their lifecycle.
     */
    private final List<PoolWorker> workers;

    /**
     * Generates unique IDs for each submitted task.
     * 
     * AtomicLong is used because:
     * - Multiple threads might submit tasks concurrently
     * - We need thread-safe increment operation
     * - incrementAndGet() is atomic (happens all at once, no race conditions)
     */
    private final AtomicLong taskIdGenerator = new AtomicLong(0);
    
    /**
     * Tracks how many tasks have finished executing.
     * Used to detect when all submitted tasks are complete.
     */
    private final AtomicLong finishedTaskCounter = new AtomicLong(0);

    /**
     * Flag indicating if the pool is shutting down.
     * 
     * volatile keyword ensures:
     * - Changes are immediately visible to all threads
     * - Prevents compiler optimizations that might cache the value
     * 
     * When true, no new tasks will be accepted.
     */
    private volatile boolean isShutdown = false;

    /**
     * Constructor - Creates and initializes the thread pool.
     * 
     * Steps:
     * 1. Store the pool size and queue size
     * 2. Create a blocking queue to hold tasks
     * 3. Create worker threads
     * 4. Start all worker threads (they begin waiting for tasks)
     * 
     * @param poolSize Number of worker threads to create
     * @param maxQueueSize Maximum number of tasks that can wait in the queue
     */
    public MiniThreadPool(int poolSize, int maxQueueSize) {
        // Store configuration
        this.poolSize = poolSize;
        this.maxQueueSize = maxQueueSize;
        
        // Create a blocking queue with fixed capacity
        // ArrayBlockingQueue is a bounded queue (has a maximum size)
        // If queue is full, put() will block until space is available
        this.taskQueue = new ArrayBlockingQueue<>(maxQueueSize);
        
        // Create a list to hold references to all worker threads
        this.workers = new java.util.ArrayList<>(poolSize);

        // Create and start all worker threads
        // Each worker will run in its own thread and wait for tasks
        for (int i = 0; i < poolSize; i++) {
            // Create a worker thread with a unique name
            PoolWorker worker = new PoolWorker("Pool-Worker-" + i, taskQueue);
            workers.add(worker);
            
            // Start the thread - this calls the run() method in PoolWorker
            // The thread will now be running and waiting for tasks
            worker.start();
        }
        System.out.println("[MiniThreadPool] Started " + poolSize + " workers");
    }

    /**
     * Submits a task to the thread pool for execution.
     * 
     * Flow:
     * 1. Check if pool is shutting down (reject if so)
     * 2. Generate a unique ID for the task
     * 3. Create a completion callback (runs when task finishes)
     * 4. Wrap the task in a WorkTask object (adds metadata)
     * 5. Add the task to the queue (a worker will pick it up)
     * 
     * Important: This method returns immediately - it doesn't wait for the task
     * to complete. The task will be executed by one of the worker threads.
     * 
     * @param task The Runnable task to execute (the actual work to do)
     * @throws IllegalStateException If the pool is already shut down
     */
    public void submit(Runnable task) {
        // Reject new tasks if pool is shutting down
        // This prevents adding tasks after shutdown has been initiated
        if (isShutdown) {
            throw new IllegalStateException("ThreadPool is in shutdown mode. Cannot accept new tasks.");
        }

        // Generate a unique ID for this task
        // incrementAndGet() is atomic - safe for concurrent access
        // Returns the new value after incrementing
        long taskId = taskIdGenerator.incrementAndGet();
        
        // Create a callback that will be executed when the task completes
        // This allows us to track when all tasks are finished
        Runnable completionCallback = () -> {
            // Increment the finished counter
            long total = finishedTaskCounter.incrementAndGet();
            
            // Check if all submitted tasks are now complete
            // If finished count equals total submitted count, we're done
            if (total == taskIdGenerator.get()) {
                System.out.println("[MiniThreadPool] All submitted tasks complete");
            }
        };

        try {
            // Wrap the user's task in a WorkTask object
            // WorkTask adds metadata like:
            // - Task ID
            // - Submission timestamp (to calculate wait time)
            // - Completion callback
            WorkTask workTask = new WorkTask("Task-" + taskId, task, completionCallback);
            
            // Add the task to the queue
            // put() will block if the queue is full (waits until space is available)
            // This is thread-safe - multiple threads can call submit() concurrently
            taskQueue.put(workTask);
        } catch (InterruptedException e) {
            // If interrupted while waiting for queue space, restore interrupt flag
            // This allows calling code to handle the interrupt appropriately
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates a graceful shutdown of the thread pool.
     * 
     * Graceful shutdown means:
     * - No new tasks will be accepted (isShutdown flag set to true)
     * - Workers finish their current tasks
     * - Workers then exit when they receive a poison pill
     * 
     * Poison Pill Pattern:
     * - A special "task" that signals workers to stop
     * - Each worker checks if a task is a poison pill
     * - If yes, worker exits its loop and terminates
     * - This is cleaner than interrupting threads (which can cause issues)
     * 
     * Why use poison pills instead of interrupt?
     * - Interrupts can happen at any time (even during critical operations)
     * - Poison pills are processed like normal tasks (safer)
     * - Workers can finish current work before checking for poison pill
     */
    public void shutDown() {
        // Set shutdown flag - submit() will now reject new tasks
        isShutdown = true;
        System.out.println("[MiniThreadPool] Shutting down (sending poison pills to workers)");
        
        // Send one poison pill to each worker
        // Each worker will pick up a poison pill, recognize it, and exit
        for (int i = 0; i < workers.size(); i++) {
            // offer() adds to queue if space is available, returns false if full
            // We use offer() instead of put() because we don't want to block during shutdown
            // If queue is full, the poison pill might not be added, but that's okay
            // (workers will eventually process existing tasks and then check for poison)
            taskQueue.offer(WorkTask.createPoisonPill());
        }
    }

    /**
     * Prints statistics about the thread pool.
     * 
     * Useful for debugging and monitoring:
     * - How many tasks are waiting in queue
     * - How many tasks can still be queued
     * - Status of each worker (busy or idle)
     */
    public void printStats() {
        System.out.println("Current Queue Size: " + taskQueue.size());
        System.out.println("Until Queue full : " + (maxQueueSize - taskQueue.size()));

        // Check status of each worker thread
        for (PoolWorker worker : workers) {
            if (worker.isAlive()) {
                // Thread is still running - check if it's currently processing a task
                System.out.println(worker.getName() + " isBusy: " + worker.isBusy());
            } else {
                // Thread has terminated
                System.out.println(worker.getName() + " isIdle: " + worker.isInterrupted());
            }
        }

    }

    /**
     * Waits for all worker threads to terminate.
     * 
     * This is useful after calling shutDown() to ensure all workers
     * have finished and exited before the program ends.
     * 
     * @param timeoutMillis Maximum time to wait (in milliseconds)
     * @throws InterruptedException If interrupted while waiting
     */
    public void awaitTermination(long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        
        // Wait for each worker thread to finish
        // We track elapsed time to ensure we don't exceed the timeout
        for (PoolWorker worker : workers) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeoutMillis - elapsed;
            
            // Only wait if we haven't exceeded the timeout
            if (remaining > 0) {
                // join() waits for the thread to finish
                // We pass remaining time so we don't wait longer than timeout
                worker.join(remaining);
            }
        }
    }

    /**
     * Returns the task queue (for testing/monitoring purposes).
     * 
     * @return The blocking queue that holds tasks
     */
    public BlockingQueue<WorkTask> getTaskQueue() {
        return taskQueue;
    }

    /**
     * Unused constant - kept for reference.
     * The actual poison pill is created via WorkTask.createPoisonPill()
     */
    private static final Runnable POISON_PILL = () -> {
        // This doesn't need to do anything, it's just a marker
    };

}
