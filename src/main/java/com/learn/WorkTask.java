package com.learn;

/**
 * WorkTask - A wrapper around a user's task with additional metadata.
 * 
 * This class wraps the user's Runnable task and adds:
 * - Task ID (for tracking/logging)
 * - Submission timestamp (to calculate wait time)
 * - Completion callback (runs when task finishes)
 * - Poison pill flag (special shutdown signal)
 * 
 * Why wrap the task?
 * - We need to track metadata (ID, timing, etc.)
 * - We need to run a callback when task completes
 * - We need to distinguish normal tasks from poison pills
 */
public class WorkTask implements Runnable {

    /**
     * Timestamp when this task was submitted to the pool.
     * Used to calculate how long the task waited in the queue.
     */
    private long submissionTime;
    
    /**
     * Unique identifier for this task (e.g., "Task-1", "Task-2").
     * Used for logging and tracking.
     */
    private String taskId;
    
    /**
     * The actual task to execute (the user's Runnable).
     * This is what does the real work.
     */
    private Runnable task;
    
    /**
     * Callback to execute when the task completes.
     * Used to track completion and update statistics.
     * Can be null (e.g., for poison pills).
     */
    private final Runnable onComplete;

    /**
     * Flag indicating if this is a poison pill (shutdown signal).
     * 
     * Poison pills are special "tasks" that don't do any work.
     * When a worker receives one, it knows to shut down gracefully.
     */
    private final boolean isPoisonPill;

    /**
     * Constructor for normal tasks (not poison pills).
     * 
     * Creates a WorkTask that wraps a user's Runnable task.
     * 
     * @param taskId Unique identifier for this task
     * @param task The actual Runnable task to execute
     * @param onComplete Callback to run when task completes (can be null)
     */
    public WorkTask(String taskId, Runnable task, Runnable onComplete) {
        this.taskId = taskId;
        this.task = task;
        isPoisonPill = false; // This is a normal task, not a poison pill
        this.onComplete = onComplete;
        // Record when task was submitted (for calculating wait time)
        this.submissionTime = System.currentTimeMillis();
    }

    /**
     * Private constructor for creating poison pills.
     * 
     * Poison pills are special "tasks" used to signal workers to shut down.
     * They don't do any actual work - they're just a signal.
     * 
     * This constructor is private - use createPoisonPill() factory method instead.
     * 
     * @param onComplete Not used for poison pills (always null)
     * @param isPoisonPill Must be true (indicates this is a poison pill)
     */
    private WorkTask(Runnable onComplete, boolean isPoisonPill) {
        this.onComplete = null; // Poison pills don't have completion callbacks
        this.taskId = "KILL_SIGNAL"; // Special ID to identify poison pills
        // Empty task - poison pills don't do any work
        this.task = () -> {
            // Intentionally empty - this is just a signal, not actual work
        };
        this.submissionTime = System.currentTimeMillis();
        this.isPoisonPill = isPoisonPill; // Mark this as a poison pill
    }

    /**
     * Factory method to create a poison pill.
     * 
     * This is the only way to create poison pills (constructor is private).
     * 
     * @return A WorkTask that is marked as a poison pill
     */
    public static WorkTask createPoisonPill() {
        return new WorkTask(null, true);
    }

    /**
     * Checks if this WorkTask is a poison pill.
     * 
     * Workers check this to know if they should shut down.
     * 
     * @return true if this is a poison pill, false otherwise
     */
    public boolean isPoisonPill() {
        return isPoisonPill;
    }

    /**
     * Executes the wrapped task and runs the completion callback.
     * 
     * This method is called by PoolWorker when it's time to execute the task.
     * 
     * Flow:
     * 1. Execute the user's task (the actual work)
     * 2. Run the completion callback (if provided)
     * 
     * We use try-finally to ensure the callback runs even if the task throws an exception.
     */
    @Override
    public void run() {
        if (task != null) {
            try {
                // Execute the user's actual task
                // This is the Runnable that was submitted via pool.submit()
                task.run();
            } finally {
                // Always run the completion callback, even if task threw an exception
                // This ensures statistics are updated correctly
                if (onComplete != null) {
                    // Callback typically updates counters or logs completion
                    onComplete.run();
                }
            }
        }
        // If task is null, do nothing (shouldn't happen for normal tasks)
    }


    /**
     * Gets the timestamp when this task was submitted.
     * 
     * Used by PoolWorker to calculate how long the task waited in the queue.
     * 
     * @return Submission timestamp in milliseconds
     */
    public long getSubmissionTime() {
        return submissionTime;
    }

    /**
     * Gets the unique identifier for this task.
     * 
     * Used for logging and tracking which task is being executed.
     * 
     * @return Task ID string (e.g., "Task-1", "Task-2", or "KILL_SIGNAL" for poison pills)
     */
    public String getTaskId() {
        return taskId;
    }
}
