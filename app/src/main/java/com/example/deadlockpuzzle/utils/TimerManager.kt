package com.example.deadlockpuzzle.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.deadlockpuzzle.threading.IntervalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the game timer with LiveData to observe remaining time.
 * This implements part of requirement #2 (real-time elements - interval elements).
 */
class TimerManager(private val intervalManager: IntervalManager? = null) {
    // LiveData for timer updates
    private val _remainingTime = MutableLiveData<Int>()
    val remainingTime: LiveData<Int> = _remainingTime

    // Coroutine for timer (fallback if intervalManager is not provided)
    private val timerScope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null
    
    // Atomic variables for thread-safe timer state
    private val isRunning = AtomicBoolean(false)
    private val currentTimeSeconds = AtomicInteger(0)
    
    // Timer task ID for interval manager
    private val TIMER_TASK_ID = "gameTimer"

    /**
     * Starts the timer with the specified duration in seconds
     */
    fun startTimer(durationSeconds: Int) {
        // Cancel any existing timer
        stopTimer()

        // Initialize timer value
        currentTimeSeconds.set(durationSeconds)
        _remainingTime.postValue(durationSeconds)
        isRunning.set(true)

        // Use interval manager if available
        if (intervalManager != null) {
            startIntervalTimer()
        } else {
            // Fall back to coroutine-based timer
            startCoroutineTimer()
        }
    }
    
    /**
     * Starts a timer using the IntervalManager
     */
    private fun startIntervalTimer() {
        // Add a task that runs every second
        intervalManager?.addTask(TIMER_TASK_ID, 1000, true) {
            // This runs on the main thread
            if (isRunning.get()) {
                val currentTime = currentTimeSeconds.decrementAndGet()
                _remainingTime.value = currentTime
                
                // Check if timer has reached zero
                if (currentTime <= 0) {
                    stopTimer()
                }
            }
        }
    }
    
    /**
     * Starts a timer using coroutines (fallback method)
     */
    private fun startCoroutineTimer() {
        // Start a new timer
        timerJob = timerScope.launch {
            val startTime = currentTimeSeconds.get()
            for (second in startTime downTo 0) {
                if (!isRunning.get()) break
                
                currentTimeSeconds.set(second)
                _remainingTime.value = second
                delay(1000) // Wait one second
            }
        }
    }

    /**
     * Stops the current timer if running
     */
    fun stopTimer() {
        isRunning.set(false)
        
        // Stop interval task if using interval manager
        intervalManager?.removeTask(TIMER_TASK_ID)
        
        // Cancel coroutine job if using coroutines
        timerJob?.cancel()
        timerJob = null
    }
    
    /**
     * Gets the current remaining time
     */
    fun getCurrentTime(): Int {
        return currentTimeSeconds.get()
    }
    
    /**
     * Checks if the timer is currently running
     */
    fun isTimerRunning(): Boolean {
        return isRunning.get()
    }
}
