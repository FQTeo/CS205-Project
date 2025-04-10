package com.example.deadlockpuzzle.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.deadlockpuzzle.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages sound effects and background music for the game
 */
class SoundManager(private val context: Context) {
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
    private var successSoundId: Int = 0
    private var failureSoundId: Int = 0
    private var dragSoundId: Int = 0
    private var dropSoundId: Int = 0

    // Volume controls
    private var soundEffectsVolume = 1.0f
    private var musicVolume = 0.5f

    init {
        // Load sound effects
        loadSounds()
    }

    /**
     * Loads all sound resources
     */
    private fun loadSounds() {
        // Load sound effects
        // In a real app, you'd have actual sound resources
        // For this example, we're using placeholder resource IDs
        successSoundId = soundPool.load(context, R.raw.success, 1)
        failureSoundId = soundPool.load(context, R.raw.failure, 1)
    }

    /**
     * Plays success sound effect
     */
    suspend fun playSuccessSound() = withContext(Dispatchers.IO) {
        soundPool.play(successSoundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
    }

    /**
     * Plays failure sound effect
     */
    suspend fun playFailureSound() = withContext(Dispatchers.IO) {
        soundPool.play(failureSoundId, soundEffectsVolume, soundEffectsVolume, 1, 0, 1.0f)
    }


}