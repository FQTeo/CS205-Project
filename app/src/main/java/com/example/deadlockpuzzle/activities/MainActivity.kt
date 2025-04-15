package com.example.deadlockpuzzle.activities

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.deadlockpuzzle.R
import com.example.deadlockpuzzle.data.GameDatabase
import com.example.deadlockpuzzle.graphics.ParticleSystem
import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.threading.AsyncTaskManager
import com.example.deadlockpuzzle.threading.BackgroundMessageHandler
import com.example.deadlockpuzzle.threading.GameTaskProcessor
import com.example.deadlockpuzzle.threading.GameThreadPool
import com.example.deadlockpuzzle.threading.InteractionThreadPool
import com.example.deadlockpuzzle.threading.IntervalManager
import com.example.deadlockpuzzle.threading.SharedStateManager
import com.example.deadlockpuzzle.utils.SoundManager
import com.example.deadlockpuzzle.utils.TimerManager
import com.example.deadlockpuzzle.viewModels.GameViewModel
import com.example.deadlockpuzzle.views.GameView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main activity for the Deadlock Puzzle game.
 * This implements all the required features:
 * 1. 2D graphics drawn by code (GameView)
 * 2. Real-time elements (frame-based, interval-based, and asynchronous)
 * 3. Interactive elements (touch events)
 * 4. Worker threads (GameThreadPool, AsyncTaskManager)
 * 5. Thread synchronization (SharedStateManager, locks)
 * 6. Standard GUI components (buttons, layouts)
 * 7. Thread pool for human interactions (InteractionThreadPool)
 * 8. Producer-consumer pattern (GameTaskProcessor)
 * 9. Advanced multi-threading abstractions (BackgroundMessageHandler)
 * 12. Mobile features integration (vibration)
 * 17. Advanced 2D graphics features (ParticleSystem, ShaderFactory)
 * 20. Simulation with natural forces (PhysicsSystem)
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Views
    private lateinit var difficultyLayout: LinearLayout
    private lateinit var gameContainer: ConstraintLayout
    private lateinit var gameView: GameView
    private lateinit var timerText: TextView
    private lateinit var runButton: Button
    private lateinit var returnHomeButton: Button
    private lateinit var anotherRoundButton: Button
    private lateinit var timeoutModal: androidx.cardview.widget.CardView
    private lateinit var timeoutHomeButton: Button
    private lateinit var timeoutRestartButton: Button
    private lateinit var pauseButton: Button
    private lateinit var continueButton: Button
    private lateinit var confirmDiscardModal: androidx.cardview.widget.CardView
    private lateinit var discardCancelButton: Button
    private lateinit var discardConfirmButton: Button

    // Game components
    private lateinit var viewModel: GameViewModel
    private lateinit var timerManager: TimerManager
    private lateinit var soundManager: SoundManager
    private lateinit var vibrator: Vibrator
    private lateinit var gameDatabase: GameDatabase
    
    // Threading components
    private lateinit var gameThreadPool: GameThreadPool
    private lateinit var asyncTaskManager: AsyncTaskManager
    private lateinit var interactionThreadPool: InteractionThreadPool
    private lateinit var intervalManager: IntervalManager
    private lateinit var backgroundMessageHandler: BackgroundMessageHandler
    private lateinit var gameTaskProcessor: GameTaskProcessor
    
    // Shared state
    private lateinit var gameStateManager: SharedStateManager<GameState>
    
    // Flags for thread synchronization
    private val isProcessingGame = AtomicBoolean(false)
    
    // Flag to track if the game was explicitly paused
    private var gameExplicitlyPaused = false
    
    // Variable to store pending difficulty selection
    private var pendingDifficulty: GameDifficulty? = null
    
    // Data class for shared game state
    data class GameState(
        val isRunning: Boolean = false,
        val isCompleted: Boolean = false,
        val difficulty: GameDifficulty = GameDifficulty.EASY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize threading components first
        initializeThreadingComponents()
        
        // Initialize game components
        initializeGameComponents()
        
        // Initialize views
        initializeViews()
        
        // Observe timer updates
        observeTimerUpdates()
        
        // Check for and clean up any invalid saved games
        cleanupInvalidSavedGames()
        
        // Show difficulty selection initially
        showDifficultySelection()
    }
    
    /**
     * Initializes all threading components
     */
    private fun initializeThreadingComponents() {
        // Create thread pool for background tasks
        gameThreadPool = GameThreadPool()
        
        // Create async task manager
        asyncTaskManager = AsyncTaskManager(gameThreadPool)
        
        // Create interaction thread pool for user interactions
        interactionThreadPool = InteractionThreadPool()
        
        // Create interval manager for fixed-interval tasks
        intervalManager = IntervalManager()
        
        // Create background message handler for advanced threading
        backgroundMessageHandler = BackgroundMessageHandler()
        
        // Create game task processor with producer-consumer pattern
        gameTaskProcessor = GameTaskProcessor()
        
        // Initialize shared state manager
        gameStateManager = SharedStateManager(GameState())
        
        // Set up interval tasks
        setupIntervalTasks()
    }
    
    /**
     * Sets up interval-based tasks
     */
    private fun setupIntervalTasks() {
        // Add a task to perform background processing
        intervalManager.addTask("backgroundProcessor", 2000, false) {
            // This runs on a background thread and only logs occasionally to prevent spam
            gameStateManager.withReadLock { gameState ->
                if (gameState.isRunning && !gameState.isCompleted) {
                    if (System.currentTimeMillis() % 10000 < 100) { // Log roughly once every 10 seconds
                        Log.d(TAG, "Background processing for difficulty: ${gameState.difficulty}")
                    }
                }
            }
        }
    }

    private fun initializeGameComponents() {
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(GameViewModel::class.java)
        
        // Initialize timer manager
        timerManager = TimerManager(intervalManager)
        
        // Initialize sound manager
        soundManager = SoundManager(this)
        
        // Initialize vibrator service using the new recommended approach
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Initialize game database using singleton pattern
        gameDatabase = GameDatabase.getInstance(this)
    }

    private fun initializeViews() {
        // Get view references
        difficultyLayout = findViewById(R.id.difficulty_layout)
        gameContainer = findViewById(R.id.game_container)
        timerText = findViewById(R.id.timer_text)
        runButton = findViewById(R.id.run_button)
        returnHomeButton = findViewById(R.id.return_home_button)
        anotherRoundButton = findViewById(R.id.another_round_button)
        timeoutModal = findViewById(R.id.timeout_modal)
        timeoutHomeButton = findViewById(R.id.timeout_home_button)
        timeoutRestartButton = findViewById(R.id.timeout_restart_button)
        pauseButton = findViewById(R.id.pause_button)
        continueButton = findViewById(R.id.continue_button)
        confirmDiscardModal = findViewById(R.id.confirm_discard_modal)
        discardCancelButton = findViewById(R.id.discard_cancel_button)
        discardConfirmButton = findViewById(R.id.discard_confirm_button)

        // Set up GameView with threading components
        gameView = GameView(this, viewModel.gameLogic).apply {
            setAsyncTaskManager(asyncTaskManager)
            setInteractionThreadPool(interactionThreadPool)
            setIntervalManager(intervalManager)
        }
        gameContainer.addView(gameView, 0) // Add at index 0 to be below other views

        // Set up difficulty selection buttons
        val setupDifficultyButton = { button: Button, difficulty: GameDifficulty ->
            button.setOnClickListener {
                // Disable button immediately to prevent double-clicks
                button.isEnabled = false
                
                // Process button click in thread pool
                interactionThreadPool.processInteraction {
                    handleDifficultySelection(difficulty, button)
                }
            }
        }
        
        setupDifficultyButton(findViewById(R.id.easy_button), GameDifficulty.EASY)
        setupDifficultyButton(findViewById(R.id.medium_button), GameDifficulty.MEDIUM)
        setupDifficultyButton(findViewById(R.id.hard_button), GameDifficulty.HARD)

        // Set up high scores button
        val setupButton = { button: Button, action: () -> Unit ->
            button.setOnClickListener {
                // Disable button immediately to prevent double-clicks
                button.isEnabled = false
                
                // Process button click in thread pool
                interactionThreadPool.processInteraction {
                    try {
                        action()
                    } finally {
                        // Re-enable button on completion (on main thread)
                        runOnUiThread { button.isEnabled = true }
                    }
                }
            }
        }
        
        setupButton(findViewById(R.id.high_scores_button)) { showHighScores() }
        
        // Set up continue button
        setupButton(continueButton) { loadSavedGame() }

        // Set up pause button (special case - button is re-enabled in pauseGame method)
        pauseButton.setOnClickListener {
            // Disable button immediately to prevent double-clicks
            pauseButton.isEnabled = false
            
            interactionThreadPool.processInteraction {
                pauseGame()
                // Button will be re-enabled in pauseGame() method when needed
            }
        }

        // Set up run button (special case - button is hidden/re-enabled in runGame method)
        runButton.setOnClickListener {
            // Disable button immediately to prevent double-clicks
            runButton.isEnabled = false
            
            interactionThreadPool.processInteraction {
                runGame()
                // Button will be re-enabled or hidden in runGame() method
            }
        }

        returnHomeButton.setOnClickListener {
            // Disable button immediately to prevent double-clicks
            returnHomeButton.isEnabled = false
            
            // Log that the return home button was clicked
            Log.d(TAG, "Return Home button clicked")
            
            // Process in UI thread first to ensure immediate feedback
            runOnUiThread {
                // Provide visual feedback for button press
                returnHomeButton.alpha = 0.5f
            }
            
            interactionThreadPool.processInteraction {
                try {
                    Log.d(TAG, "Processing Return Home action")
                    // Make sure to stop any ongoing activities
                    timerManager.stopTimer()
                    
                    // Force stop any background processing
                    viewModel.stopAllProcessing()
                    
                    // Don't call resetGame() here as it sets up a new game with the same difficulty
                    // Clear any saved game state when explicitly returning to home
                    gameDatabase.clearSavedGame()
                    
                    // Just go directly to the difficulty selection screen
                    showDifficultySelection()
                    
                    // Restore button state on main thread
                    runOnUiThread { 
                        returnHomeButton.alpha = 1.0f
                        returnHomeButton.isEnabled = true 
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error returning to home screen", e)
                    // Try alternative approach on failure
                    runOnUiThread {
                        // Show error message
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Error returning to main menu. Trying again...",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        // Force reset UI state directly
                        difficultyLayout.visibility = View.VISIBLE
                        gameContainer.visibility = View.GONE
                        
                        // Restore button state
                        returnHomeButton.alpha = 1.0f
                        returnHomeButton.isEnabled = true
                    }
                }
            }
        }

        anotherRoundButton.setOnClickListener {
            // Disable button immediately to prevent double-clicks
            anotherRoundButton.isEnabled = false
            
            // Log button click
            Log.d(TAG, "Another Round button clicked")
            
            // Process in UI thread first to ensure immediate feedback
            runOnUiThread {
                // Provide visual feedback for button press
                anotherRoundButton.alpha = 0.5f
            }
            
            interactionThreadPool.processInteraction {
                try {
                    Log.d(TAG, "Processing Another Round action")
                    resetGame()
                    
                    // Always restore button state even though it will be hidden
                    // This ensures it's enabled the next time it becomes visible
                    runOnUiThread { 
                        anotherRoundButton.alpha = 1.0f
                        anotherRoundButton.isEnabled = true 
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting game", e)
                    // Restore button state on error
                    runOnUiThread {
                        // Show error message
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Error starting another round. Please try again.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        // Restore button state
                        anotherRoundButton.alpha = 1.0f
                        anotherRoundButton.isEnabled = true
                    }
                }
            }
        }

        // Set up timeout modal buttons
        setupButton(timeoutHomeButton) {
            hideTimeoutModal()
            gameDatabase.clearSavedGame()
            showDifficultySelection()
        }
        
        setupButton(timeoutRestartButton) {
            hideTimeoutModal()
            resetGame()
        }

        // Set up discard confirmation dialog buttons
        setupButton(discardCancelButton) {
            hideDiscardConfirmationDialog()
            enableAllDifficultyButtons()
        }
        
        discardConfirmButton.setOnClickListener {
            // Disable button immediately to prevent double-clicks
            discardConfirmButton.isEnabled = false
            
            // Process the pending difficulty after confirming discard
            pendingDifficulty?.let { difficulty ->
                // Store button reference before hiding dialog
                val difficultyButton = when(difficulty) {
                    GameDifficulty.EASY -> findViewById<Button>(R.id.easy_button)
                    GameDifficulty.MEDIUM -> findViewById<Button>(R.id.medium_button)
                    GameDifficulty.HARD -> findViewById<Button>(R.id.hard_button)
                }
                
                hideDiscardConfirmationDialog()
                
                // Clear saved game
                lifecycleScope.launch(Dispatchers.IO) {
                    gameDatabase.clearSavedGame()
                    gameExplicitlyPaused = false
                    
                    // Now start the new game
                    withContext(Dispatchers.Main) {
                        startGame(difficulty)
                        // Re-enable both the discard button and the difficulty button
                        discardConfirmButton.isEnabled = true
                        difficultyButton.isEnabled = true
                    }
                }
            } ?: run {
                // If pendingDifficulty is null, just re-enable the button
                discardConfirmButton.isEnabled = true
            }
        }

        // Set callbacks for game events
        gameView.onGameSuccessListener = {
            lifecycleScope.launch {
                // Use task processor for sound playback
                gameTaskProcessor.playSound(R.raw.success)
                
                // Play success sound directly as well
                soundManager.playSuccessSound()
                
                // Show game results
                showGameResults(true)
            }
        }

        gameView.onGameFailureListener = {
            lifecycleScope.launch {
                // Use task processor for sound playback
                gameTaskProcessor.playSound(R.raw.failure)
                
                // Play failure sound directly as well
                soundManager.playFailureSound()
                
                // Vibrate device
                vibrateDevice()
                
                // Do NOT show timeout modal for deadlock detection
                // Just let the deadlock visualization and the normal game results show
            }
        }
    }
//
//    private fun observeTimerUpdates() {
//        // Observe timer updates
//        timerManager.remainingTime.observe(this) { timeRemaining ->
//            updateTimerDisplay(timeRemaining)
//
//            // Check if time ran out
//            if (timeRemaining <= 0) {
//                lifecycleScope.launch {
//                    timerManager.stopTimer()
//
//                    // Use background message handler to process game over
//                    backgroundMessageHandler.queueGameLogicProcessing(viewModel.gameLogic)
//
//                    // Show failure animation
//                    gameView.showFailureAnimation()
//
//                    // Vibrate device
//                    vibrateDevice()
//
//                    // Play failure sound
//                    soundManager.playFailureSound()
//
//                    // Show timeout modal instead of regular game results
//                    showTimeoutModal()
//                }
//            }
//        }
//
//        // Observe game state changes
//        viewModel.monsters.observe(this) { monsters ->
//            // Update game state in GameView
//            gameView.updateGameState(monsters)
//        }
//    }

    private fun observeTimerUpdates() {
        // Observe timer updates
        timerManager.remainingTime.observe(this) { timeRemaining ->
            updateTimerDisplay(timeRemaining)

            // Check if time ran out
            if (timeRemaining <= 0) {
                lifecycleScope.launch {
                    timerManager.stopTimer()

                    // Check for deadlock first using viewModel
                    val isDeadlocked = withContext(Dispatchers.Default) {
                        !viewModel.runGame() // runGame returns true if deadlock-free
                    }

                    if (isDeadlocked) {
                        // Use background message handler to process game over
                        backgroundMessageHandler.queueGameLogicProcessing(viewModel.gameLogic)

                        // Show failure animation
                        gameView.showFailureAnimation()

                        // Vibrate device
                        vibrateDevice()

                        // Play failure sound
                        soundManager.playFailureSound()

                        // Show timeout modal instead of regular game results
                        showTimeoutModal()
                    } else {
                        // If no deadlock, show success animation and results
                        gameView.showSuccessAnimation()

                        // Play success sound
                        soundManager.playSuccessSound()

                        // Show success game results
                        showGameResults(true)
                    }
                }
            }
        }

        // Observe game state changes
        viewModel.monsters.observe(this) { monsters ->
            // Update game state in GameView
            gameView.updateGameState(monsters)
        }
    }

    private fun showDifficultySelection() {
        // Update shared game state (thread-safe operation)
        gameStateManager.write(GameState(isRunning = false, isCompleted = false))
        
        // Check for saved games only if the game was explicitly paused
        if (gameExplicitlyPaused) {
            lifecycleScope.launch(Dispatchers.IO) {
                val hasSavedGame = gameDatabase.hasSavedGame()
                withContext(Dispatchers.Main) {
                    // Show or hide continue button based on whether there's a saved game
                    continueButton.visibility = if (hasSavedGame) View.VISIBLE else View.GONE
                }
            }
        } else {
            // If game was not explicitly paused, hide continue button and clear any saved game
            continueButton.visibility = View.GONE
            lifecycleScope.launch(Dispatchers.IO) {
                gameDatabase.clearSavedGame()
            }
        }
        
        // All UI operations must be on the main thread
        runOnUiThread {
            // Show difficulty selection, hide game
            difficultyLayout.visibility = View.VISIBLE
            gameContainer.visibility = View.GONE
            
            // Ensure all difficulty buttons are enabled
            enableAllDifficultyButtons()
            
            // Reset game state - Force a full reset of the game state
            timerManager.stopTimer()
            
            // Force a complete game reset to clear any remaining state
            viewModel.resetGame()
            
            // Make sure the game view is reset
            gameView.resetView()
        }
    }

    private fun startGame(difficulty: GameDifficulty) {
        // Reset the explicitly paused flag since we're starting a new game
        gameExplicitlyPaused = false
        
        // Update shared game state
        gameStateManager.write(GameState(isRunning = true, isCompleted = false, difficulty = difficulty))
        
        // Hide difficulty selection, show game
        runOnUiThread {
            difficultyLayout.visibility = View.GONE
            gameContainer.visibility = View.VISIBLE

            // Show only Run button at start and ensure it's enabled
            runButton.visibility = View.VISIBLE
            runButton.isEnabled = true
            returnHomeButton.visibility = View.GONE
            anotherRoundButton.visibility = View.GONE
            
            // Ensure pause button is enabled
            pauseButton.isEnabled = true
        }

        // Set up new game using AsyncTaskManager - ensure complete fresh setup
        asyncTaskManager.executeAsync(
            task = {
                // This runs in a background thread
                try {
                    // First reset completely to clear any previous state
                    viewModel.resetGame()
                    // Then set up the new game with the selected difficulty
                    viewModel.setupGame(difficulty)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up game with difficulty $difficulty", e)
                    // Return to difficulty selection on error
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Error setting up game: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        showDifficultySelection()
                    }
                }
            },
            onComplete = { result ->
                // This runs on the main thread
                try {
                    // Start timer
                    timerManager.startTimer(difficulty.timeSeconds)
                    
                    // Ensure game view is properly refreshed
                    gameView.updateGameState(viewModel.monsters.value ?: emptyList())
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating game view", e)
                    // Return to difficulty selection on error
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Error updating game view: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    showDifficultySelection()
                }
            }
        )
    }

    private fun runGame() {
        // Only allow one game run at a time
        if (isProcessingGame.compareAndSet(false, true)) {
            lifecycleScope.launch {
                try {
                    // Disable run button while checking
                    runButton.isEnabled = false

                    // Verify that the game state is valid before running
                    val monsters = viewModel.monsters.value
                    if (monsters.isNullOrEmpty()) {
                        Log.e(TAG, "Cannot run game - no monsters available")
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Error: Game not properly initialized. Please try another difficulty.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        showDifficultySelection()
                        return@launch
                    }

                    // Check if arrangement avoids deadlock
                    val isDeadlockFree = withContext(Dispatchers.Default) {
                        try {
                            viewModel.runGame()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error running game", e)
                            null // Return null to indicate error
                        }
                    }

                    // Handle error case
                    if (isDeadlockFree == null) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Error checking for deadlock. Please try again.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        // Re-enable run button
                        runButton.isEnabled = true
                        isProcessingGame.set(false)
                        return@launch
                    }

                    // Stop timer
                    timerManager.stopTimer()

                    // Update shared game state
                    gameStateManager.write(GameState(
                        isRunning = false,
                        isCompleted = true,
                        difficulty = viewModel.gameLogic.getDifficulty()
                    ))

                    // Show appropriate animation
                    if (isDeadlockFree) {
                        gameView.showSuccessAnimation()
                        
                        // Show success game results
                        showGameResults(true)
                    } else {
                        // Show failure animation with deadlock visualization
                        gameView.showFailureAnimation()
                        
                        // Show failure game results
                        showGameResults(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Critical error in runGame", e)
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Something went wrong. Please try again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    // Return to difficulty selection on critical error
                    showDifficultySelection()
                } finally {
                    isProcessingGame.set(false)
                }
            }
        } else {
            Log.d(TAG, "Game is already being processed")
            // Provide feedback to user
            android.widget.Toast.makeText(
                this@MainActivity,
                "Please wait, game is processing...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showGameResults(success: Boolean) {
        // Clear any saved game state since the game is now completed
        lifecycleScope.launch(Dispatchers.IO) {
            gameDatabase.clearSavedGame()
        }
        
        // Get current difficulty and time
        val difficulty = viewModel.gameLogic.getDifficulty()
        val remainingTime = timerManager.getCurrentTime()
        // Calculate the actual elapsed time (total time minus remaining time)
        val timeElapsed = difficulty.timeSeconds - remainingTime
        
        // Save high score if successful
        if (success) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Attempting to check/save high score for difficulty: $difficulty, time: $timeElapsed")
                    
                    // First check if the database is accessible
                    val testHighScores = gameDatabase.getHighScores(difficulty, 1)
                    
                    // If we can access the database, proceed with high score check and save
                    // Check if the time is a high score and show a message
                    val isNewHighScore = gameDatabase.isHighScore(difficulty, timeElapsed)
                    
                    // Save the score (will check if high score internally)
                    // Note: We're saving the elapsed time (lower is better)
                    val result = gameDatabase.saveHighScore(difficulty, timeElapsed)
                    if (result > 0) {
                        Log.d(TAG, "Successfully saved score: $timeElapsed seconds for $difficulty (completed with $remainingTime seconds remaining)")
                    } else {
                        Log.w(TAG, "Score may not have been saved properly, result: $result")
                    }
                    
                    // Update UI to show high score message
                    if (isNewHighScore) {
                        withContext(Dispatchers.Main) {
                            // Show a toast message about the new high score
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "New high score: $timeElapsed seconds!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving high score", e)
                    // Show the error on the main thread
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Couldn't save your score: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        // Hide run button, show result buttons
        runOnUiThread {
            runButton.visibility = View.GONE
            
            // Disable pause button as game is completed
            pauseButton.isEnabled = false
            
            // Ensure buttons are enabled before showing them
            returnHomeButton.isEnabled = true
            returnHomeButton.alpha = 1f
            anotherRoundButton.isEnabled = true
            anotherRoundButton.alpha = 1f
            
            returnHomeButton.visibility = View.VISIBLE
            anotherRoundButton.visibility = View.VISIBLE
            
            // Add a short delay before showing buttons to let users see the deadlock visualization
            if (!success) {
                returnHomeButton.alpha = 0f
                anotherRoundButton.alpha = 0f
                
                returnHomeButton.animate()
                    .alpha(1f)
                    .setStartDelay(2000) // 2 second delay to let users see the deadlock
                    .setDuration(300)
                    .start()
                    
                anotherRoundButton.animate()
                    .alpha(1f)
                    .setStartDelay(2000)
                    .setDuration(300)
                    .start()
            }
            
            Log.d(TAG, "Game results shown. anotherRoundButton.isEnabled=${anotherRoundButton.isEnabled}")
        }
        
        // Create particles based on result using GameTaskProcessor
        if (success) {
            gameTaskProcessor.createParticles(
                x = gameView.width / 2f,
                y = gameView.height / 3f,
                count = 50,
                color = android.graphics.Color.GREEN
            )
        } else {
            gameTaskProcessor.createParticles(
                x = gameView.width / 2f,
                y = gameView.height / 3f,
                count = 50,
                color = android.graphics.Color.RED
            )
        }
    }

    private fun resetGame() {
        // Reset for another round with same difficulty
        asyncTaskManager.executeAsync(
            task = {
                // This runs in a background thread
                // Only do thread-safe operations here, not LiveData updates
                val difficulty = viewModel.gameLogic.getDifficulty()
                difficulty // Return the difficulty to use in onComplete
            },
            onComplete = { difficulty ->
                // This runs on the main thread where it's safe to update LiveData
                // Reset the game in ViewModel
                viewModel.resetGame()
                
                // Show only run button
                runButton.visibility = View.VISIBLE
                runButton.isEnabled = true
                returnHomeButton.visibility = View.GONE
                anotherRoundButton.visibility = View.GONE
                
                // Ensure pause button is enabled
                pauseButton.isEnabled = true
                
                // Update shared game state
                gameStateManager.write(GameState(
                    isRunning = true,
                    isCompleted = false,
                    difficulty = difficulty as GameDifficulty // Cast is needed as asyncTask returns Any
                ))
                
                // Restart timer
                timerManager.startTimer(difficulty.timeSeconds)
            }
        )
    }

    private fun vibrateDevice() {
        // Vibrate to indicate failure
        val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(vibrationEffect)
    }

    private fun updateTimerDisplay(timeRemaining: Int) {
        runOnUiThread {
            timerText.text = "$timeRemaining"
            
            // Apply visual feedback based on time remaining
            when {
                timeRemaining <= 10 -> {
                    // Critical time remaining - highlight in red
                    timerText.setTextColor(resources.getColor(R.color.error_color, theme))
                }
                timeRemaining <= 20 -> {
                    // Low time remaining - highlight in orange/yellow
                    timerText.setTextColor(resources.getColor(R.color.accent, theme))
                }
                else -> {
                    // Normal time - use default color
                    timerText.setTextColor(resources.getColor(R.color.white, theme))
                }
            }
        }
    }

    /**
     * Shows or hides a modal dialog with animation
     */
    private fun animateModal(modal: View, show: Boolean, endAction: (() -> Unit)? = null) {
        runOnUiThread {
            if (show) {
                // Show modal with animation
                modal.visibility = View.VISIBLE
                modal.alpha = 0f
                modal.scaleX = 0.8f
                modal.scaleY = 0.8f
                
                modal.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .withEndAction { endAction?.invoke() }
                    .start()
            } else {
                // Hide modal with animation
                modal.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction {
                        modal.visibility = View.GONE
                        endAction?.invoke()
                    }
                    .start()
            }
        }
    }
    
    /**
     * Shows the timeout modal dialog
     */
    private fun showTimeoutModal() {
        // Disable pause button as game is over
        pauseButton.isEnabled = false
        
        // Hide run button
        runButton.visibility = View.GONE
        
        // Show timeout modal with animation
        animateModal(timeoutModal, true)
    }

    /**
     * Hides the timeout modal dialog
     */
    private fun hideTimeoutModal() {
        animateModal(timeoutModal, false)
    }
    
    /**
     * Shows the confirmation dialog for discarding saved game
     */
    private fun showDiscardConfirmationDialog() {
        animateModal(confirmDiscardModal, true)
    }
    
    /**
     * Hides the confirmation dialog
     */
    private fun hideDiscardConfirmationDialog() {
        animateModal(confirmDiscardModal, false) {
            pendingDifficulty = null
        }
    }

    /**
     * Shows the high scores screen
     */
    private fun showHighScores() {
        val intent = android.content.Intent(this, HighScoresActivity::class.java)
        startActivity(intent)
    }

    /**
     * Pauses the current game and saves its state
     */
    private fun pauseGame() {
        // Stop the timer
        timerManager.stopTimer()
        
        // Save the current game state
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val difficulty = viewModel.gameLogic.getDifficulty()
                val monsters = viewModel.monsters.value ?: emptyList()
                val remainingTime = timerManager.getCurrentTime()
                
                // Save game state to database
                val result = gameDatabase.saveGameState(difficulty, monsters, remainingTime)
                
                withContext(Dispatchers.Main) {
                    if (result > 0) {
                        // Mark that the game was explicitly paused
                        gameExplicitlyPaused = true
                        
                        // Show success message and return to main menu
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Game paused. You can continue later.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        showDifficultySelection()
                    } else {
                        // Show error message
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Could not save game state. Try again.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        // Re-enable pause button
                        pauseButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving game state", e)
                withContext(Dispatchers.Main) {
                    // Show error message
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    // Re-enable pause button
                    pauseButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * Loads a saved game and resumes play
     */
    private fun loadSavedGame() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // First verify a saved game actually exists
                if (!gameDatabase.hasSavedGame()) {
                    withContext(Dispatchers.Main) {
                        // Show error message
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "No valid saved game found.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        // Hide continue button
                        continueButton.visibility = View.GONE
                    }
                    return@launch
                }
                
                // Try to load the saved game
                val savedGame = gameDatabase.loadSavedGame()
                
                if (savedGame != null) {
                    // Verify the saved game data is valid
                    val monsters = savedGame.monsters
                    val difficulty = savedGame.difficulty
                    val remainingTime = savedGame.remainingTime
                    
                    // Ensure we have valid data
                    if (monsters.isEmpty() || remainingTime <= 0) {
                        Log.e(TAG, "Invalid saved game data: monsters=${monsters.size}, time=$remainingTime")
                        gameDatabase.clearSavedGame() // Clean up the invalid saved game
                        
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Saved game data was corrupted. Starting new game.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            
                            // Hide continue button
                            continueButton.visibility = View.GONE
                        }
                        return@launch
                    }
                    
                    withContext(Dispatchers.Main) {
                        // Update UI
                        difficultyLayout.visibility = View.GONE
                        gameContainer.visibility = View.VISIBLE
                        
                        // Show only Run button at start and ensure it's enabled
                        runButton.visibility = View.VISIBLE
                        runButton.isEnabled = true
                        returnHomeButton.visibility = View.GONE
                        anotherRoundButton.visibility = View.GONE
                    }
                    
                    // Update game state in background
                    withContext(Dispatchers.Default) {
                        // Set up game with saved state
                        viewModel.setupGame(difficulty)
                        viewModel.restoreGameState(monsters)
                    }
                    
                    // Start timer with remaining time
                    withContext(Dispatchers.Main) {
                        timerManager.startTimer(remainingTime)
                        
                        // Update game view
                        gameView.updateGameState(monsters)
                        
                        // Make sure pause button is enabled
                        pauseButton.isEnabled = true
                        
                        // Reset the explicitly paused flag since we've now loaded the game
                        gameExplicitlyPaused = false
                        
                        // Show success message
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Game restored. Difficulty: ${difficulty.name}, Time remaining: $remainingTime seconds",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // If we get here, hasSavedGame was true but loadSavedGame returned null
                    // This means the saved game is corrupted - clean it up
                    gameDatabase.clearSavedGame()
                    
                    withContext(Dispatchers.Main) {
                        // Show error message
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Could not load saved game. Data may be corrupted.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        // Hide continue button
                        continueButton.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved game", e)
                
                // Clean up any saved games on error
                gameDatabase.clearSavedGame()
                
                withContext(Dispatchers.Main) {
                    // Show error message
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Error loading saved game: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Hide continue button
                    continueButton.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Checks for and cleans up any invalid saved games that might exist
     */
    private fun cleanupInvalidSavedGames() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Try to load the saved game
                val savedGame = gameDatabase.loadSavedGame()
                
                // If loading returns null but hasSavedGame returns true,
                // there's an inconsistency - clear all saved games
                if (savedGame == null && gameDatabase.hasSavedGame()) {
                    Log.w(TAG, "Found invalid saved game state, clearing it")
                    gameDatabase.clearSavedGame()
                    
                    withContext(Dispatchers.Main) {
                        // Make sure continue button is hidden
                        continueButton.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking saved games", e)
                // Clean up any saved games on error to be safe
                gameDatabase.clearSavedGame()
            }
        }
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
            
            // Ensure pause button is enabled when resuming an active game
            pauseButton.isEnabled = true
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy called, cleaning up resources")
        
        try {
            // Clean up threading resources
            gameThreadPool.shutdown()
            asyncTaskManager.shutdown()
            interactionThreadPool.shutdown()
            intervalManager.shutdown()
            backgroundMessageHandler.shutdown()
            gameTaskProcessor.shutdown()
            
            // Note: Don't close the database here as it's a singleton
            // The system will handle closing it when appropriate
            
            Log.d(TAG, "All threading resources have been shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup in onDestroy", e)
        }
        
        super.onDestroy()
    }

    /**
     * Handles difficulty selection, checking if there's a saved game first
     */
    private fun handleDifficultySelection(difficulty: GameDifficulty, button: Button) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val hasSavedGame = gameDatabase.hasSavedGame() && gameExplicitlyPaused
                
                withContext(Dispatchers.Main) {
                    if (hasSavedGame && continueButton.visibility == View.VISIBLE) {
                        // Store pending difficulty and show confirmation dialog
                        pendingDifficulty = difficulty
                        showDiscardConfirmationDialog()
                    } else {
                        // No saved game, proceed directly
                        startGame(difficulty)
                        button.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for saved game", e)
                withContext(Dispatchers.Main) {
                    // On error, proceed with new game anyway
                    startGame(difficulty)
                    button.isEnabled = true
                }
            }
        }
    }
    
    /**
     * Re-enables all difficulty buttons
     */
    private fun enableAllDifficultyButtons() {
        runOnUiThread {
            listOf(R.id.easy_button, R.id.medium_button, R.id.hard_button).forEach {
                findViewById<Button>(it).isEnabled = true
            }
        }
    }
}
