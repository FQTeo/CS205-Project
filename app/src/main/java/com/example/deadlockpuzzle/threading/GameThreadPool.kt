package com.example.deadlockpuzzle.threading

import android.util.Log
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread pool for executing game-related tasks.
 * This implements part of requirement #4 (worker threads).
 */
class GameThreadPool(
    private val corePoolSize: Int = 2,
    private val maxPoolSize: Int = 4,
    private val keepAliveTimeSeconds: Long = 60
) {
    private val TAG = "GameThreadPool"
    
    // Thread counter for naming threads
    private val threadCounter = AtomicInteger(0)
    
    // Thread factory for creating worker threads
    private val threadFactory = ThreadFactory { runnable ->
        Thread(runnable).apply {
            name = "GameWorker-${threadCounter.getAndIncrement()}"
            priority = Thread.NORM_PRIORITY
            isDaemon = true
        }
    }
    
    // Thread pool executor
    private val executor = ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        keepAliveTimeSeconds,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        threadFactory
    )

    /**
     * Submits a callable task to the thread pool and returns a Future
     * 
     * @param task The callable task to submit
     * @return A Future representing the task
     */
    fun <T> submit(task: Callable<T>): Future<T> {
        return executor.submit(task)
    }
    
    /**
     * Shuts down the thread pool
     * 
     * @param awaitTermination Whether to wait for all tasks to complete
     * @param timeoutSeconds Timeout in seconds if waiting for termination
     */
    fun shutdown(awaitTermination: Boolean = true, timeoutSeconds: Long = 5) {
        executor.shutdown()
        
        if (awaitTermination) {
            try {
                if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Thread pool did not terminate in time, forcing shutdown")
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                Log.w(TAG, "Thread pool shutdown interrupted", e)
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        
        Log.d(TAG, "Thread pool shut down")
    }
}
