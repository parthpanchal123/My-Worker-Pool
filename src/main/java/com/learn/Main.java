package com.learn;

import java.util.concurrent.CountDownLatch;

/**
 * Main class that demonstrates the Worker Pool pattern.
 * 
 * This is the entry point of the application. It creates a thread pool,
 * submits multiple tasks to it, waits for all tasks to complete, and then
 * shuts down the pool gracefully.
 * 
 * The Worker Pool pattern allows us to:
 * - Reuse threads instead of creating new ones for each task (more efficient)
 * - Control the number of concurrent tasks (prevents resource exhaustion)
 * - Queue tasks when all workers are busy (handles bursts of work)
 */
public class Main {
    /**
     * Main method - the starting point of our application.
     * 
     * Flow:
     * 1. Create a CountDownLatch to track when all tasks finish
     * 2. Create a thread pool with a fixed number of workers
     * 3. Submit multiple tasks to the pool
     * 4. Wait for all tasks to complete
     * 5. Shut down the pool gracefully
     * 
     * @param args Command line arguments (not used in this example)
     * @throws InterruptedException If the main thread is interrupted while waiting
     */
    public static void main(String[] args) throws InterruptedException {
        // Number of tasks we want to execute
        // In a real application, this could be tasks from a database, API calls, etc.
        int totalTasks = 10;
        
        // CountDownLatch is a synchronization tool that allows one or more threads
        // to wait until a set of operations being performed in other threads completes.
        // 
        // Think of it like a counter:
        // - Starts at totalTasks (10 in this case)
        // - Each task calls latch.countDown() when it finishes (decrements by 1)
        // - Main thread calls latch.await() which blocks until counter reaches 0
        // 
        // This ensures the main thread waits for all tasks before shutting down
        CountDownLatch latch = new CountDownLatch(totalTasks);

        // Create a thread pool with:
        // - 2 workers: Two threads that will process tasks concurrently
        // - Queue size 5: Maximum 5 tasks can wait in the queue when all workers are busy
        // 
        // Why use a thread pool?
        // - Creating threads is expensive (memory, CPU overhead)
        // - Too many threads can overwhelm the system
        // - A pool reuses threads, making it more efficient
        System.out.println("[MAIN] Creating thread pool (2 workers, queue size: 5)");
        MiniThreadPool pool = new MiniThreadPool(2, 5);

        // Submit all tasks to the thread pool
        // Each task is a Runnable (a piece of code to execute)
        // 
        // Note: submit() returns immediately - it doesn't wait for the task to complete.
        // The task is added to a queue, and a worker thread will pick it up when available.
        System.out.println("[MAIN] Submitting " + totalTasks + " tasks...");
        for (int i = 1; i <= totalTasks; i++) {
            // Submit a lambda expression (anonymous function) as a task
            // This lambda will be executed by one of the worker threads
            pool.submit(() -> {
                try {
                    // Simulate some work by sleeping for 500 milliseconds
                    // In a real application, this could be:
                    // - Processing data
                    // - Making API calls
                    // - Reading/writing files
                    // - Database operations
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // If the thread is interrupted, restore the interrupt flag
                    // This is a best practice - it allows other code to see the interrupt
                    Thread.currentThread().interrupt();
                } finally {
                    // Always decrement the latch, even if an exception occurs
                    // This ensures the main thread doesn't wait forever
                    latch.countDown();
                }
            });
        }

        // Wait for all tasks to complete
        // This blocks the main thread until the CountDownLatch reaches 0
        // (i.e., until all tasks have called countDown())
        latch.await();
        System.out.println("[MAIN] All " + totalTasks + " tasks completed");
        
        // Shut down the thread pool gracefully
        // This sends "poison pills" to all workers, telling them to stop
        // Workers finish their current tasks and then exit
        pool.shutDown();
    }
}