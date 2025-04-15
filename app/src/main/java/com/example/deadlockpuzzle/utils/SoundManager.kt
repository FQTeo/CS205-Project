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

    // For longer audio like background music
    private var backgroundMusic: MediaPlayer? = null

    // Sound effect IDs
    private val soundIds = ConcurrentHashMap<Int, Int>()
    
    // Loading state
    private val loadingLatch = CountDownLatch(2) // Wait for success and failure sounds

    // Volume controls
    private var soundEffectsVolume = 1.0f
    private var musicVolume = 0.5f

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
    
    /**
     * Plays a sound by resource ID
     */
    fun playSound(resourceId: Int) {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            asyncTaskManager.executeAsync(
                task = {
                    // This runs in a background thread
                    val soundId = soundIds[resourceId]
                    if (soundId != null && soundId > 0) {
                        soundPool.play(soundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
                    } else {
                        // Try to load and play the sound
                        try {
                            val newSoundId = soundPool.load(context, resourceId, 1)
                            soundIds[resourceId] = newSoundId
                            
                            // Wait for loading to complete
                            Thread.sleep(100)
                            
                            soundPool.play(newSoundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error playing sound: $resourceId", e)
                        }
                    }
                }
            )
        } else {
            // Play directly
            val soundId = soundIds[resourceId]
            if (soundId != null && soundId > 0) {
                soundPool.play(soundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
            }
        }
    }
    
    /**
     * Plays background music
     */
    fun playBackgroundMusic(resourceId: Int, loop: Boolean = true) {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            asyncTaskManager.executeAsync(
                task = {
                    // This runs in a background thread
                    stopBackgroundMusic()
                    
                    try {
                        val mediaPlayer = MediaPlayer.create(context, resourceId)
                        mediaPlayer.setVolume(musicVolume, musicVolume)
                        if (loop) {
                            mediaPlayer.isLooping = true
                        }
                        mediaPlayer.start()
                        backgroundMusic = mediaPlayer
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing background music", e)
                    }
                }
            )
        } else {
            // Play directly
            stopBackgroundMusic()
            
            try {
                val mediaPlayer = MediaPlayer.create(context, resourceId)
                mediaPlayer.setVolume(musicVolume, musicVolume)
                if (loop) {
                    mediaPlayer.isLooping = true
                }
                mediaPlayer.start()
                backgroundMusic = mediaPlayer
            } catch (e: Exception) {
                Log.e(TAG, "Error playing background music", e)
            }
        }
    }
    
    /**
     * Stops background music
     */
    fun stopBackgroundMusic() {
        backgroundMusic?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
            backgroundMusic = null
        }
    }
    
    /**
     * Sets the volume for sound effects
     */
    fun setSoundEffectsVolume(volume: Float) {
        soundEffectsVolume = volume.coerceIn(0f, 1f)
    }
    
    /**
     * Sets the volume for background music
     */
    fun setMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        backgroundMusic?.setVolume(musicVolume, musicVolume)
    }
    
    /**
     * Releases all resources
     */
    fun release() {
        stopBackgroundMusic()
        soundPool.release()
    }
}
