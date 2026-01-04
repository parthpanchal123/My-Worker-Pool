# Worker Pool

A lightweight, educational implementation of a thread pool (worker pool) pattern in Java. This project demonstrates how to build a thread pool from scratch using core Java concurrency utilities, providing a reusable pool of worker threads that efficiently process tasks from a shared queue.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Building the Project](#building-the-project)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Key Concepts](#key-concepts)
- [API Reference](#api-reference)
- [Example Output](#example-output)

## 🎯 Overview

The Worker Pool pattern is a fundamental concurrency design pattern that allows you to:
- **Reuse threads** instead of creating new ones for each task (more efficient)
- **Control concurrency** by limiting the number of concurrent tasks (prevents resource exhaustion)
- **Queue tasks** when all workers are busy (handles bursts of work gracefully)

This implementation provides a simple yet complete thread pool that can be used as a learning resource or as a foundation for more complex thread pool implementations.

## ✨ Features

- **Configurable Pool Size**: Set the number of worker threads
- **Bounded Task Queue**: Control the maximum number of queued tasks
- **Graceful Shutdown**: Uses the poison pill pattern for clean thread termination
- **Task Tracking**: Unique task IDs and timestamps for monitoring
- **Thread-Safe Operations**: Built using Java's `BlockingQueue` and `AtomicLong` for thread safety
- **Statistics & Monitoring**: Built-in methods to check pool status and worker activity
- **Completion Callbacks**: Support for callbacks when tasks complete

## 🏗️ Architecture

The project consists of four main components:

### 1. **MiniThreadPool**
The main thread pool manager that:
- Creates and manages worker threads
- Maintains a task queue
- Handles task submission
- Manages graceful shutdown

### 2. **PoolWorker**
Worker threads that:
- Continuously poll the task queue for work
- Execute tasks in their own thread
- Track their busy/idle status
- Respond to poison pills for shutdown

### 3. **WorkTask**
A wrapper around user tasks that:
- Adds metadata (ID, submission time)
- Supports completion callbacks
- Implements the poison pill pattern

### 4. **Main**
A demonstration class showing how to use the thread pool.

## 📦 Requirements

- **Java**: JDK 23 or higher
- **Maven**: 3.6+ (for building)

## 🔨 Building the Project

### Using Maven

```bash
# Compile the project
mvn compile

# Run the application
mvn exec:java -Dexec.mainClass="com.learn.Main"

# Or compile and run manually
mvn compile
java -cp target/classes com.learn.Main
```

## 💻 Usage

### Basic Example

```java
import com.learn.MiniThreadPool;
import java.util.concurrent.CountDownLatch;

public class Example {
    public static void main(String[] args) throws InterruptedException {
        // Create a thread pool with 2 workers and a queue size of 5
        MiniThreadPool pool = new MiniThreadPool(2, 5);
        
        // Create a latch to wait for all tasks to complete
        CountDownLatch latch = new CountDownLatch(10);
        
        // Submit 10 tasks
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            pool.submit(() -> {
                try {
                    // Simulate some work
                    Thread.sleep(500);
                    System.out.println("Task " + taskNum + " completed");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all tasks to complete
        latch.await();
        
        // Shutdown gracefully
        pool.shutDown();
        pool.awaitTermination(5000); // Wait up to 5 seconds
    }
}
```

### Advanced Example with Statistics

```java
MiniThreadPool pool = new MiniThreadPool(4, 10);

// Submit tasks
for (int i = 0; i < 20; i++) {
    pool.submit(() -> {
        // Your task logic here
        processData();
    });
}

// Check pool statistics
pool.printStats();

// Shutdown when done
pool.shutDown();
pool.awaitTermination(10000);
```

## 📁 Project Structure

```
Worker-Pool/
├── pom.xml                          # Maven configuration
└── src/
    └── main/
        └── java/
            └── com/
                └── learn/
                    ├── Main.java           # Demo application
                    ├── MiniThreadPool.java # Thread pool implementation
                    ├── PoolWorker.java     # Worker thread implementation
                    └── WorkTask.java       # Task wrapper with metadata
```

## 🔑 Key Concepts

### Thread Pool Pattern
Instead of creating a new thread for each task, a pool of worker threads is created once and reused. Tasks are submitted to a queue, and workers pick them up when available.

### Blocking Queue
A thread-safe queue that blocks when:
- **Full**: `put()` blocks until space is available
- **Empty**: `take()` blocks until a task arrives

This eliminates busy-waiting and makes threads efficient.

### Poison Pill Pattern
A special "task" (poison pill) is sent to each worker to signal shutdown. Workers check for poison pills and exit gracefully when received. This is safer than interrupting threads.

### Thread Safety
- `BlockingQueue` handles synchronization for task queue
- `AtomicLong` ensures thread-safe counter operations
- `volatile` ensures visibility of flags across threads

## 📚 API Reference

### `MiniThreadPool`

#### Constructor
```java
MiniThreadPool(int poolSize, int maxQueueSize)
```
- `poolSize`: Number of worker threads to create
- `maxQueueSize`: Maximum number of tasks that can wait in the queue

#### Methods

##### `void submit(Runnable task)`
Submits a task to the thread pool for execution. Returns immediately without waiting for completion.

**Throws**: `IllegalStateException` if pool is shutting down

##### `void shutDown()`
Initiates graceful shutdown:
- Rejects new task submissions
- Sends poison pills to all workers
- Workers finish current tasks and exit

##### `void awaitTermination(long timeoutMillis)`
Waits for all worker threads to terminate.

**Parameters**:
- `timeoutMillis`: Maximum time to wait in milliseconds

**Throws**: `InterruptedException` if interrupted while waiting

##### `void printStats()`
Prints current pool statistics:
- Queue size
- Available queue capacity
- Status of each worker (busy/idle)

##### `BlockingQueue<WorkTask> getTaskQueue()`
Returns the task queue (useful for monitoring/testing).

### `PoolWorker`

#### Methods

##### `boolean isBusy()`
Returns `true` if the worker is currently processing a task, `false` if idle.

### `WorkTask`

#### Static Factory Method

##### `static WorkTask createPoisonPill()`
Creates a poison pill task used for graceful shutdown.

#### Methods

##### `boolean isPoisonPill()`
Returns `true` if this is a poison pill, `false` otherwise.

##### `long getSubmissionTime()`
Returns the timestamp when the task was submitted (milliseconds since epoch).

##### `String getTaskId()`
Returns the unique identifier for this task.

## 📊 Example Output

When running the `Main` class, you'll see output like:

```
[MAIN] Creating thread pool (2 workers, queue size: 5)
[MiniThreadPool] Started 2 workers
[MAIN] Submitting 10 tasks...
[Pool-Worker-0] Picked up Task-1
[Pool-Worker-1] Picked up Task-2
[Pool-Worker-0] Completed Task-1 | Wait Time: 0ms | Execution Time: 500ms
[Pool-Worker-0] Picked up Task-3
[Pool-Worker-1] Completed Task-2 | Wait Time: 0ms | Execution Time: 500ms
[Pool-Worker-1] Picked up Task-4
...
[MAIN] All 10 tasks completed
[MiniThreadPool] Shutting down (sending poison pills to workers)
[Pool-Worker-0] Received poison pill, shutting down
[Pool-Worker-1] Received poison pill, shutting down
```

## 🤝 Contributing

This is an educational project. Feel free to fork, experiment, and learn from it!

## 📝 License

This project is provided as-is for educational purposes.

---

**Note**: This is a simplified implementation for educational purposes. For production use, consider using Java's built-in `ExecutorService` or more robust thread pool libraries.

