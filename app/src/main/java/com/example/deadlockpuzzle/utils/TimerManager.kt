package com.example.deadlockpuzzle.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the game timer with LiveData to observe remaining time
 */
class TimerManager {
    // LiveData for timer updates
    private val _remainingTime = MutableLiveData<Int>()
    val remainingTime: LiveData<Int> = _remainingTime

    // Coroutine for timer
    private val timerScope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    /**
     * Starts the timer with the specified duration in seconds
     */
    fun startTimer(durationSeconds: Int) {
        // Cancel any existing timer
        stopTimer()

        // Initialize timer value
        _remainingTime.value = durationSeconds

        // Start a new timer
        timerJob = timerScope.launch {
            for (second in durationSeconds downTo 0) {
                _remainingTime.value = second
                delay(1000) // Wait one second
            }
        }
    }

    /**
     * Stops the current timer if running
     */
    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Pauses the timer at the current value
     */
    fun pauseTimer() {
        val currentValue = _remainingTime.value ?: 0
        stopTimer()
        _remainingTime.value = currentValue
    }

    /**
     * Resumes the timer from the current value
     */
    fun resumeTimer() {
        val currentValue = _remainingTime.value ?: 0
        if (currentValue > 0) {
            startTimer(currentValue)
        }
    }

    /**
     * Adds time to the current timer
     */
    fun addTime(secondsToAdd: Int) {
        val currentValue = _remainingTime.value ?: 0
        val newValue = currentValue + secondsToAdd

        // If timer is running, restart with new value
        val isRunning = timerJob?.isActive == true
        stopTimer()
        _remainingTime.value = newValue

        if (isRunning && newValue > 0) {
            startTimer(newValue)
        }
    }
}