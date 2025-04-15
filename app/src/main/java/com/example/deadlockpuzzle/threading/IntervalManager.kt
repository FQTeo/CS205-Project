package com.example.deadlockpuzzle.threading

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages interval-based tasks that run at fixed time intervals.
 * This implements part of requirement #2 (interval elements).
 */
class IntervalManager {
    private val TAG = "IntervalManager"
    
    // Handler for posting tasks to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Map of task name to task info
    private val tasks = ConcurrentHashMap<String, IntervalTask>()
    
    // Is the manager running?
    private val isRunning = AtomicBoolean(true)
    
    /**
     * Adds a task to be executed at fixed intervals
     * 
     * @param name Unique name for the task
     * @param intervalMs Interval in milliseconds between executions
     * @param runOnMainThread Whether to run the task on the main thread
     * @param task The task to execute
     */
    fun addTask(name: String, intervalMs: Long, runOnMainThread: Boolean, task: () -> Unit) {
        // Remove any existing task with the same name
        removeTask(name)
        
        // Create a new task
        val intervalTask = IntervalTask(
            name = name,
            intervalMs = intervalMs,
            runOnMainThread = runOnMainThread,
            task = task
        )
        
        // Add to map
        tasks[name] = intervalTask
        
        // Start the task
        scheduleTask(intervalTask)
        
        Log.d(TAG, "Added task: $name with interval: $intervalMs ms")
    }
    
    /**
     * Removes a task
     * 
     * @param name Name of the task to remove
     */
    fun removeTask(name: String) {
        tasks.remove(name)?.let { task ->
            task.isActive.set(false)
            Log.d(TAG, "Removed task: $name")
        }
    }
    
    /**
     * Checks if a task exists
     * 
     * @param name Name of the task to check
     * @return True if the task exists, false otherwise
     */
    fun hasTask(name: String): Boolean {
        return tasks.containsKey(name)
    }
    
    /**
     * Gets the interval of a task
     * 
     * @param name Name of the task
     * @return Interval in milliseconds, or -1 if the task doesn't exist
     */
    fun getTaskInterval(name: String): Long {
        return tasks[name]?.intervalMs ?: -1
    }
    
    /**
     * Schedules a task to be executed after its interval
     * 
     * @param task The task to schedule
     */
    private fun scheduleTask(task: IntervalTask) {
        if (!isRunning.get() || !task.isActive.get()) return
        
        val runnable = Runnable {
            if (!isRunning.get() || !task.isActive.get()) return@Runnable
            
            try {
                // Execute the task
                if (task.runOnMainThread) {
                    task.task()
                } else {
                    Thread {
                        task.task()
                    }.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing task: ${task.name}", e)
            }
            
            // Schedule the next execution
            scheduleTask(task)
        }
        
        // Post with delay
        mainHandler.postDelayed(runnable, task.intervalMs)
    }
    
    /**
     * Shuts down the manager and removes all tasks
     */
    fun shutdown() {
        isRunning.set(false)
        tasks.clear()
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Shut down interval manager")
    }
    
    /**
     * Represents an interval task
     */
    private inner class IntervalTask(
        val name: String,
        var intervalMs: Long,
        val runOnMainThread: Boolean,
        val task: () -> Unit
    ) {
        val isActive = AtomicBoolean(true)
    }
}
