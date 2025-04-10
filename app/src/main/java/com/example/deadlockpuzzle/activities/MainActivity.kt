package com.example.deadlockpuzzle.activities

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.deadlockpuzzle.R
import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.utils.SoundManager
import com.example.deadlockpuzzle.utils.TimerManager
import com.example.deadlockpuzzle.viewModels.GameViewModel
import com.example.deadlockpuzzle.views.GameView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    // Views
    private lateinit var difficultyLayout: LinearLayout
    private lateinit var gameContainer: ConstraintLayout
    private lateinit var gameView: GameView
    private lateinit var timerText: TextView
    private lateinit var runButton: Button
    private lateinit var returnHomeButton: Button
    private lateinit var anotherRoundButton: Button

    // Game components
    private lateinit var viewModel: GameViewModel
    private lateinit var timerManager: TimerManager
    private lateinit var soundManager: SoundManager
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[GameViewModel::class.java]

        // Initialize game components
        timerManager = TimerManager()
        soundManager = SoundManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Set up UI
        setupViews()
        setupObservers()

        // Start at difficulty selection
        showDifficultySelection()
    }

    private fun setupViews() {
        // Get view references
        difficultyLayout = findViewById(R.id.difficulty_layout)
        gameContainer = findViewById(R.id.game_container)
        timerText = findViewById(R.id.timer_text)
        runButton = findViewById(R.id.run_button)
        returnHomeButton = findViewById(R.id.return_home_button)
        anotherRoundButton = findViewById(R.id.another_round_button)

        // Set up GameView
        gameView = GameView(this, viewModel.gameLogic)
        gameContainer.addView(gameView, 0) // Add at index 0 to be below other views

        // Set up difficulty selection buttons
        findViewById<Button>(R.id.easy_button).setOnClickListener {
            startGame(GameDifficulty.EASY)
        }

        findViewById<Button>(R.id.medium_button).setOnClickListener {
            startGame(GameDifficulty.MEDIUM)
        }

        findViewById<Button>(R.id.hard_button).setOnClickListener {
            startGame(GameDifficulty.HARD)
        }

        // Set up game control buttons
        runButton.setOnClickListener {
            runGame()
        }

        returnHomeButton.setOnClickListener {
            resetGame()
            showDifficultySelection()
        }

        anotherRoundButton.setOnClickListener {
            resetGame()
        }

        // Set callbacks for game events
        gameView.onGameSuccessListener = {
            lifecycleScope.launch {
                soundManager.playSuccessSound()
                showGameResults(true)
            }
        }

        gameView.onGameFailureListener = {
            lifecycleScope.launch {
                soundManager.playFailureSound()
                vibrateDevice()
                showGameResults(false)
            }
        }
    }

    private fun setupObservers() {
        // Observe timer updates
        timerManager.remainingTime.observe(this) { timeRemaining ->
            timerText.text = "Time: $timeRemaining"

            // Check if time ran out
            if (timeRemaining <= 0) {
                lifecycleScope.launch {
                    timerManager.stopTimer()
                    gameView.showFailureAnimation()
                    vibrateDevice()
                    soundManager.playFailureSound()
                    showGameResults(false)
                }
            }
        }

        // Observe game state changes
        viewModel.monsters.observe(this) { monsters ->
            gameView.updateGameState(monsters)
        }
    }

    private fun showDifficultySelection() {
        // Show difficulty selection, hide game
        difficultyLayout.visibility = View.VISIBLE
        gameContainer.visibility = View.GONE

        // Reset game state
        timerManager.stopTimer()
        viewModel.resetGame()
    }

    private fun startGame(difficulty: GameDifficulty) {
        // Hide difficulty selection, show game
        difficultyLayout.visibility = View.GONE
        gameContainer.visibility = View.VISIBLE

        // Show only Run button at start
        runButton.visibility = View.VISIBLE
        returnHomeButton.visibility = View.GONE
        anotherRoundButton.visibility = View.GONE

        // Set up new game
        viewModel.setupGame(difficulty)

        // Start timer
        timerManager.startTimer(difficulty.timeSeconds)
    }

    private fun runGame() {
        lifecycleScope.launch {
            // Disable run button while checking
            runButton.isEnabled = false

            // Check if arrangement avoids deadlock
            val isDeadlockFree = viewModel.runGame()

            // Stop timer
            timerManager.stopTimer()

            // Show appropriate animation
            if (isDeadlockFree) {
                gameView.showSuccessAnimation()
            } else {
                gameView.showFailureAnimation()
            }
        }
    }

    private fun showGameResults(success: Boolean) {
        // Hide run button, show result buttons
        runButton.visibility = View.GONE
        returnHomeButton.visibility = View.VISIBLE
        anotherRoundButton.visibility = View.VISIBLE
    }

    private fun resetGame() {
        // Reset for another round with same difficulty
        viewModel.resetGame()

        // Show only run button
        runButton.visibility = View.VISIBLE
        runButton.isEnabled = true
        returnHomeButton.visibility = View.GONE
        anotherRoundButton.visibility = View.GONE

        // Restart timer
        val difficulty = viewModel.gameLogic.getDifficulty()
        timerManager.startTimer(difficulty.timeSeconds)
    }

    private fun vibrateDevice() {
        // Vibrate to indicate failure
        val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(vibrationEffect)
    }

    override fun onPause() {
        super.onPause()
        timerManager.stopTimer()
    }

    override fun onResume() {
        super.onResume()

        // Only restart timer if in game mode
        if (gameContainer.visibility == View.VISIBLE &&
            runButton.visibility == View.VISIBLE &&
            !viewModel.gameLogic.isGameCompleted()) {

            val difficulty = viewModel.gameLogic.getDifficulty()
            timerManager.startTimer(difficulty.timeSeconds)
        }
    }
}