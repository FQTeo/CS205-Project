package com.example.deadlockpuzzle.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.example.deadlockpuzzle.R
import com.example.deadlockpuzzle.threading.AsyncTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * Manages sound effects and background music for the game.
 * This implements part of requirement #4 (worker threads) by using AsyncTaskManager.
 */
class SoundManager(
    private val context: Context,
    private val asyncTaskManager: AsyncTaskManager? = null
) {
    companion object {
        private const val TAG = "SoundManager"
    }
    
    // Sound pool for short sound effects
    private val soundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }

    // Sound effect IDs
    private val soundIds = ConcurrentHashMap<Int, Int>()
    
    // Loading state
    private val loadingLatch = CountDownLatch(2)

    // Volume controls
    private var soundEffectsVolume = 1.0f

    init {
        // Load sound effects
        loadSounds()
    }

    /**
     * Loads all sound resources asynchronously
     */
    private fun loadSounds() {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            // Load success sound
            asyncTaskManager.executeAsync(
                task = {
                    // This runs in a background thread
                    val soundId = soundPool.load(context, R.raw.success, 1)
                    soundIds[R.raw.success] = soundId
                    loadingLatch.countDown()
                    soundId
                },
                onComplete = { soundId ->
                    // This runs on the main thread
                    Log.d(TAG, "Success sound loaded with ID: $soundId")
                }
            )
            
            // Load failure sound
            asyncTaskManager.executeAsync(
                task = {
                    // This runs in a background thread
                    val soundId = soundPool.load(context, R.raw.failure, 1)
                    soundIds[R.raw.failure] = soundId
                    loadingLatch.countDown()
                    soundId
                },
                onComplete = { soundId ->
                    // This runs on the main thread
                    Log.d(TAG, "Failure sound loaded with ID: $soundId")
                }
            )
            
            // Load button click sound if available
            try {
                asyncTaskManager.executeAsync(
                    task = {
                        val soundId = soundPool.load(context, R.raw.button_click, 1)
                        soundIds[R.raw.button_click] = soundId
                        soundId
                    },
                    onComplete = { soundId ->
                        Log.d(TAG, "Button click sound loaded with ID: $soundId")
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Button click sound not available", e)
            }
            
            // Load timeout sound if available
            try {
                asyncTaskManager.executeAsync(
                    task = {
                        val soundId = soundPool.load(context, R.raw.timeout, 1)
                        soundIds[R.raw.timeout] = soundId
                        soundId
                    },
                    onComplete = { soundId ->
                        Log.d(TAG, "Timeout sound loaded with ID: $soundId")
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Timeout sound not available", e)
            }
        } else {
            // Fall back to synchronous loading
            try {
                val successId = soundPool.load(context, R.raw.success, 1)
                soundIds[R.raw.success] = successId
                loadingLatch.countDown()
                
                val failureId = soundPool.load(context, R.raw.failure, 1)
                soundIds[R.raw.failure] = failureId
                loadingLatch.countDown()
                
                // Load additional sounds if available
                try {
                    val clickId = soundPool.load(context, R.raw.button_click, 1)
                    soundIds[R.raw.button_click] = clickId
                } catch (e: Exception) {
                    Log.w(TAG, "Button click sound not available", e)
                }
                
                try {
                    val timeoutId = soundPool.load(context, R.raw.timeout, 1)
                    soundIds[R.raw.timeout] = timeoutId
                } catch (e: Exception) {
                    Log.w(TAG, "Timeout sound not available", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sounds", e)
            }
        }
    }

    /**
     * Plays success sound effect
     */
    suspend fun playSuccessSound() = withContext(Dispatchers.IO) {
        // Ensure sounds are loaded
        try {
            loadingLatch.await()
        } catch (e: InterruptedException) {
            Log.w(TAG, "Sound loading interrupted", e)
        }
        
        val soundId = soundIds[R.raw.success] ?: 0
        if (soundId > 0) {
            soundPool.play(soundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
        }
    }

    /**
     * Plays failure sound effect
     */
    suspend fun playFailureSound() = withContext(Dispatchers.IO) {
        // Ensure sounds are loaded
        try {
            loadingLatch.await()
        } catch (e: InterruptedException) {
            Log.w(TAG, "Sound loading interrupted", e)
        }
        
        val soundId = soundIds[R.raw.failure] ?: 0
        if (soundId > 0) {
            soundPool.play(soundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
        }
    }

}
