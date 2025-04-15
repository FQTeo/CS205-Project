package com.example.deadlockpuzzle.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deadlockpuzzle.logic.GameLogic
import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.models.Monster
import com.example.deadlockpuzzle.threading.AsyncTaskManager
import com.example.deadlockpuzzle.threading.BackgroundMessageHandler
import com.example.deadlockpuzzle.threading.SharedStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel for the Deadlock Puzzle game implementing the MVVM pattern.
 * This implements requirements #4 (worker threads) and #5 (thread synchronization).
 */
class GameViewModel : ViewModel() {
    companion object {
        private const val TAG = "GameViewModel"
    }
    
    // Game logic
    val gameLogic = GameLogic()

    // LiveData for monsters (UI observes this)
    private val _monsters = MutableLiveData<List<Monster>>()
    val monsters: LiveData<List<Monster>> = _monsters

    // Game state
    private val _isGameCompleted = MutableLiveData(false)
    val isGameCompleted: LiveData<Boolean> = _isGameCompleted
    
    // Thread-safe state management
    private val gameStateManager = SharedStateManager(GameState())
    
    // Background processing components
    private var asyncTaskManager: AsyncTaskManager? = null
    private var backgroundMessageHandler: BackgroundMessageHandler? = null
    
    // Background processing job
    private var backgroundJob: Job? = null
    private val isBackgroundProcessingActive = AtomicBoolean(false)
    
    // Data class for game state
    data class GameState(
        val isRunning: Boolean = false,
        val isCompleted: Boolean = false,
        val difficulty: GameDifficulty? = null,
        val monsters: List<Monster> = emptyList()
    )
    
    /**
     * Sets the AsyncTaskManager for background processing
     */
    fun setAsyncTaskManager(manager: AsyncTaskManager) {
        asyncTaskManager = manager
    }
    
    /**
     * Sets the BackgroundMessageHandler for advanced threading
     */
    fun setBackgroundMessageHandler(handler: BackgroundMessageHandler) {
        backgroundMessageHandler = handler
    }

    /**
     * Sets up a new game with the given difficulty
     */
    fun setupGame(difficulty: GameDifficulty) {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            asyncTaskManager?.executeAsync(
                task = {
                    // This runs in a background thread
                    try {
                        Log.d(TAG, "Setting up game with difficulty: $difficulty")
                        gameLogic.setupGame(difficulty)
                        val monsters = gameLogic.getMonsters()
                        Log.d(TAG, "Created ${monsters.size} monsters for difficulty: $difficulty")

                        if (monsters.isEmpty()) {
                            throw IllegalStateException("No monsters were created for difficulty: $difficulty")
                        }
                        
                        // Update shared state - this is thread safe
                        gameStateManager.write(GameState(
                            isRunning = true,
                            isCompleted = false,
                            difficulty = difficulty,
                            monsters = monsters
                        ))
                        
                        monsters
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in setupGame for difficulty: $difficulty", e)
                        throw e
                    }
                },
                onComplete = { monsters ->
                    // This runs on the main thread
                    try {
                        _monsters.value = monsters
                        _isGameCompleted.value = false
                        
                        // Start background processing
                        startBackgroundProcessing()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in setupGame onComplete callback", e)
                    }
                }
            )
        } else {
            // Fallback to direct setup - ensure we're on the main thread
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    // Do heavy work on background thread
                    val monsters = withContext(Dispatchers.Default) {
                        Log.d(TAG, "Setting up game with difficulty: $difficulty (direct setup)")
                        gameLogic.setupGame(difficulty)
                        gameLogic.getMonsters()
                    }
                    
                    // Update shared state - this is thread safe
                    gameStateManager.write(GameState(
                        isRunning = true,
                        isCompleted = false,
                        difficulty = difficulty,
                        monsters = monsters
                    ))
                    
                    // Update LiveData on main thread
                    _monsters.value = monsters
                    _isGameCompleted.value = false
                    
                    // Start background processing
                    startBackgroundProcessing()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in direct setupGame for difficulty: $difficulty", e)
                }
            }
        }
    }

    /**
     * Runs the game to check if the current monster arrangement avoids deadlock
     * Returns true if the arrangement is deadlock-free, false otherwise
     */
    suspend fun runGame(): Boolean {
        // Use BackgroundMessageHandler if available
        if (backgroundMessageHandler != null) {
            // Queue game logic processing and wait for completion
            backgroundMessageHandler?.queueGameLogicProcessing(gameLogic, true)
        }
        
        // Run the game logic
        val result = withContext(Dispatchers.Default) {
            gameLogic.runGame()
        }
        
        // Update shared state
        gameStateManager.write(GameState(
            isRunning = false,
            isCompleted = true,
            difficulty = gameLogic.getDifficulty(),
            monsters = gameLogic.getMonsters()
        ))
        
        // Update LiveData
        withContext(Dispatchers.Main) {
            _monsters.value = gameLogic.getMonsters()
            _isGameCompleted.value = true
        }
        
        // Stop background processing
        stopBackgroundProcessing()
        
        return result
    }

    /**
     * Resets the game for another round
     */
    fun resetGame() {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            asyncTaskManager?.executeAsync(
                task = {
                    // This runs in a background thread
                    // Only do thread-safe operations here, not LiveData updates
                    gameLogic.resetGame()
                    val monsters = gameLogic.getMonsters()
                    
                    // Update shared state (this is thread-safe)
                    gameStateManager.write(GameState(
                        isRunning = true,
                        isCompleted = false,
                        difficulty = gameLogic.getDifficulty(),
                        monsters = monsters
                    ))
                    
                    monsters
                },
                onComplete = { monsters ->
                    // This runs on the main thread
                    // Update LiveData on main thread
                    _monsters.value = monsters
                    _isGameCompleted.value = false
                    
                    // Start background processing
                    startBackgroundProcessing()
                }
            )
        } else {
            // Fallback to direct reset
            // Since we don't have AsyncTaskManager, we need to ensure we're on main thread
            viewModelScope.launch {
                try {
                    // Reset game logic
                    gameLogic.resetGame()
                    val monsters = gameLogic.getMonsters()
                    
                    // Update shared state
                    gameStateManager.write(GameState(
                        isRunning = true,
                        isCompleted = false,
                        difficulty = gameLogic.getDifficulty(),
                        monsters = monsters
                    ))
                    
                    // Update LiveData
                    _monsters.value = monsters
                    _isGameCompleted.value = false
                    
                    // Start background processing
                    startBackgroundProcessing()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in direct resetGame", e)
                }
            }
        }
    }
    
    /**
     * Starts background processing for the game
     */
    private fun startBackgroundProcessing() {
        // Only start if not already running
        if (isBackgroundProcessingActive.compareAndSet(false, true)) {
            backgroundJob = viewModelScope.launch {
                Log.d(TAG, "Starting background processing")
                
                var cycleCount = 0
                
                while (isActive) {
                    // Access shared state safely
                    val currentState = gameStateManager.read()
                    
                    if (currentState.isRunning && !currentState.isCompleted) {
                        // Perform background processing for the game
                        performBackgroundProcessing(cycleCount)
                        cycleCount++
                    }
                    
                    // Increase delay to reduce processing frequency
                    delay(500) // Changed from 100ms to 500ms
                }
            }
        }
    }
    
    /**
     * Stops background processing
     */
    private fun stopBackgroundProcessing() {
        isBackgroundProcessingActive.set(false)
        backgroundJob?.cancel()
        backgroundJob = null
    }
    
    /**
     * Performs background processing for the game
     */
    private suspend fun performBackgroundProcessing(cycleCount: Int) {
        // This is where you would do any continuous background work
        // For example, AI calculations, physics simulations, etc.
        
        // Only log occasionally to reduce log spam
        if (cycleCount % 20 == 0) { // Only log every 20 cycles
            Log.d(TAG, "Performing background processing for difficulty: ${gameStateManager.read().difficulty}")
        }
    }
    
    /**
     * Gets the current game state
     */
    fun getGameState(): GameState {
        return gameStateManager.read()
    }
    
    /**
     * Updates a monster's position with thread safety
     */
    fun updateMonsterPosition(monsterId: Int, newPosition: Int) {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            asyncTaskManager?.executeAsync(
                task = {
                    // This runs in a background thread
                    gameLogic.updateMonsterPosition(monsterId, newPosition)
                    val monsters = gameLogic.getMonsters()
                    
                    // Update shared state
                    gameStateManager.update({ currentState ->
                        currentState.copy(monsters = monsters)
                    })
                    
                    monsters
                },
                onComplete = { monsters ->
                    // This runs on the main thread
                    _monsters.value = monsters
                }
            )
        } else {
            // Fallback to direct update
            gameLogic.updateMonsterPosition(monsterId, newPosition)
            val monsters = gameLogic.getMonsters()
            
            // Update shared state
            gameStateManager.update({ currentState ->
                currentState.copy(monsters = monsters)
            })
            
            _monsters.value = monsters
        }
    }
    
    /**
     * Restores a previously saved game state
     */
    fun restoreGameState(savedMonsters: List<Monster>) {
        // Use AsyncTaskManager if available
        if (asyncTaskManager != null) {
            asyncTaskManager?.executeAsync(
                task = {
                    // This runs in a background thread
                    gameLogic.restoreMonsters(savedMonsters)
                    val monsters = gameLogic.getMonsters()
                    
                    // Update shared state
                    gameStateManager.write(GameState(
                        isRunning = true,
                        isCompleted = false,
                        difficulty = gameLogic.getDifficulty(),
                        monsters = monsters
                    ))
                    
                    monsters
                },
                onComplete = { monsters ->
                    // This runs on the main thread
                    _monsters.value = monsters
                    _isGameCompleted.value = false
                    
                    // Start background processing
                    startBackgroundProcessing()
                }
            )
        } else {
            // Fallback to direct restore
            viewModelScope.launch {
                try {
                    val monsters = withContext(Dispatchers.Default) {
                        gameLogic.restoreMonsters(savedMonsters)
                        gameLogic.getMonsters()
                    }
                    
                    // Update shared state
                    gameStateManager.write(GameState(
                        isRunning = true,
                        isCompleted = false,
                        difficulty = gameLogic.getDifficulty(),
                        monsters = monsters
                    ))
                    
                    // Update LiveData
                    _monsters.value = monsters
                    _isGameCompleted.value = false
                    
                    // Start background processing
                    startBackgroundProcessing()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in direct restoreGameState", e)
                }
            }
        }
    }
    
    /**
     * Stops all background processing and ensures a clean state
     * This is called when returning to the home screen to prevent lingering processes
     */
    fun stopAllProcessing() {
        Log.d(TAG, "Stopping all background processing")
        
        // Stop background processing
        stopBackgroundProcessing()
        
        // Cancel any pending AsyncTaskManager tasks
        asyncTaskManager?.cancelAllTasks()
        
        // Reset game state on main thread safely
        viewModelScope.launch(Dispatchers.Main) {
            try {
                _isGameCompleted.value = false
                _monsters.value = emptyList()
            
                // Reset shared state
                gameStateManager.write(GameState(
                    isRunning = false,
                    isCompleted = false,
                    difficulty = null,
                    monsters = emptyList()
                ))
                
                Log.d(TAG, "Successfully reset game state in stopAllProcessing")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping all processing", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Clean up resources
        stopBackgroundProcessing()
    }
}
