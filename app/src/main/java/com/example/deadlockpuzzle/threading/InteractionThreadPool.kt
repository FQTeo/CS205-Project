package com.example.deadlockpuzzle.threading

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread pool for processing user interactions.
 * This implements requirement #7 (thread pool for human interactions).
 */
class InteractionThreadPool(private val numThreads: Int = 3) {
    private val TAG = "InteractionThreadPool"
    
    // Thread pool executor
    private val executor = Executors.newFixedThreadPool(numThreads) { runnable ->
        Thread(runnable).apply {
            name = "InteractionThread-${threadCounter.getAndIncrement()}"
            priority = Thread.NORM_PRIORITY
        }
    }
    
    // Handler for posting results to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Counter for thread naming
    private val threadCounter = AtomicInteger(0)
    
    // Counter for active tasks
    private val activeTaskCount = AtomicInteger(0)
    
    /**
     * Processes an interaction in the thread pool
     * 
     * @param task The task to execute
     */
    fun processInteraction(task: () -> Unit) {
        activeTaskCount.incrementAndGet()
        
        executor.execute {
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing interaction", e)
            } finally {
                activeTaskCount.decrementAndGet()
            }
        }
    }
    
    /**
     * Processes an interaction in the thread pool and returns a result on the main thread
     * 
     * @param task The task to execute, returning a result
     * @param onComplete Callback to receive the result on the main thread
     * @return A Future that can be used to cancel the task
     */
    fun <T> processInteractionWithResult(task: () -> T, onComplete: (T) -> Unit): Future<*> {
        activeTaskCount.incrementAndGet()
        
        return executor.submit {
            try {
                val result = task()
                
                // Post result to main thread
                mainHandler.post {
                    try {
                        onComplete(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onComplete callback", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing interaction with result", e)
            } finally {
                activeTaskCount.decrementAndGet()
            }
        }
    }
    
    /**
     * Shuts down the thread pool
     * 
     * @param awaitTermination Whether to wait for all tasks to complete
     * @param timeoutMs Timeout in milliseconds if waiting for termination
     */
    fun shutdown(awaitTermination: Boolean = true, timeoutMs: Long = 1000) {
        executor.shutdown()
        
        if (awaitTermination) {
            try {
                if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        
        Log.d(TAG, "Shut down interaction thread pool")
    }
}
