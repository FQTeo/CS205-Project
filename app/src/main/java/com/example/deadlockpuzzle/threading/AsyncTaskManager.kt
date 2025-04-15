package com.example.deadlockpuzzle.threading

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages asynchronous tasks using worker threads.
 * This implements requirement #4 (worker threads).
 */
class AsyncTaskManager(private val threadPool: GameThreadPool) {
    private val TAG = "AsyncTaskManager"
    
    // Handler for posting results to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Map of task ID to future
    private val taskFutures = ConcurrentHashMap<Int, Future<*>>()
    
    // Task ID counter
    private val taskIdCounter = AtomicInteger(0)
    
    /**
     * Executes a task asynchronously
     * 
     * @param task The task to execute
     * @param onComplete Callback to receive the result on the main thread (optional)
     * @param onError Callback to receive any errors on the main thread (optional)
     * @return Task ID that can be used to cancel the task
     */
    fun <T> executeAsync(
        task: () -> T,
        onComplete: ((T) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): Int {
        val taskId = taskIdCounter.incrementAndGet()
        
        val future = threadPool.submit(Callable {
            try {
                val result = task()
                
                // Post result to main thread if callback provided
                if (onComplete != null) {
                    mainHandler.post {
                        try {
                            onComplete(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onComplete callback", e)
                            onError?.let { errorHandler ->
                                mainHandler.post { errorHandler(e) }
                            }
                        }
                    }
                }
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error executing async task", e)
                
                // Post error to main thread if callback provided
                onError?.let { errorHandler ->
                    mainHandler.post { errorHandler(e) }
                }
                
                throw e
            } finally {
                // Remove task from map when complete
                taskFutures.remove(taskId)
            }
        })
        
        // Store future for potential cancellation
        taskFutures[taskId] = future
        
        return taskId
    }
    
    /**
     * Cancels all tasks
     * 
     * @param mayInterruptIfRunning Whether to interrupt tasks if they're running
     */
    fun cancelAllTasks(mayInterruptIfRunning: Boolean = true) {
        taskFutures.forEach { (_, future) ->
            future.cancel(mayInterruptIfRunning)
        }
        taskFutures.clear()
    }
    
    /**
     * Shuts down the async task manager
     */
    fun shutdown() {
        cancelAllTasks(true)
        mainHandler.removeCallbacksAndMessages(null)
    }
}
