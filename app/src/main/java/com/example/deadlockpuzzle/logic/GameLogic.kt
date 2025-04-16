package com.example.deadlockpuzzle.logic

import android.util.Log
import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.models.Monster
import com.example.deadlockpuzzle.models.MonsterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Contains the game logic and deadlock detection algorithms.
 * This implements requirement #5 (thread synchronization) with advanced locking mechanisms.
 */
class GameLogic {
    companion object {
        private const val TAG = "GameLogic"
    }
    
    // Current game state
    private var monsters = listOf<Monster>()
    private var difficulty = GameDifficulty.EASY
    private var gameRunning = false
    private var gameCompleted = false

    // Enhanced synchronization with ReadWriteLock for better concurrency
    private val stateLock = ReentrantReadWriteLock()
    private val readLock = stateLock.readLock()
    private val writeLock = stateLock.writeLock()
    
    // Condition variable for signaling state changes
    private val stateChangedCondition: Condition = writeLock.newCondition()
    
    // Atomic flags for thread-safe state checks
    private val isProcessing = AtomicBoolean(false)

    /**
     * Sets up a new game with a given difficulty
     */
    fun setupGame(difficulty: GameDifficulty) {
        writeLock.lock()
        try {
            Log.d(TAG, "Setting up game with difficulty: $difficulty")
            this.difficulty = difficulty
            
            try {
                this.monsters = MonsterFactory.createMonstersForDifficulty(difficulty)
                Log.d(TAG, "Successfully created ${monsters.size} monsters for difficulty: $difficulty")
                
                if (monsters.isEmpty()) {
                    Log.e(TAG, "Critical error: MonsterFactory returned empty list for difficulty: $difficulty")
                    // Create a fallback set of monsters to prevent crashing
                    this.monsters = when (difficulty) {
                        GameDifficulty.EASY -> List(3) { index ->
                            Monster(
                                id = index,
                                name = "Monster $index",
                                heldResourceId = index + 1,
                                neededResourceId = ((index + 1) % 3) + 1, 
                                position = index
                            )
                        }
                        GameDifficulty.MEDIUM -> List(5) { index ->
                            Monster(
                                id = index,
                                name = "Monster $index",
                                heldResourceId = index + 1,
                                neededResourceId = ((index + 1) % 5) + 1,
                                position = index
                            )
                        }
                        GameDifficulty.HARD -> List(8) { index ->
                            Monster(
                                id = index,
                                name = "Monster $index",
                                heldResourceId = index + 1,
                                neededResourceId = ((index + 1) % 8) + 1,
                                position = index
                            )
                        }
                    }
                    Log.d(TAG, "Applied fallback monsters: ${monsters.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating monsters for difficulty: $difficulty", e)
                // Create a fallback set of monsters to prevent crashing
                this.monsters = when (difficulty) {
                    GameDifficulty.EASY -> List(3) { index ->
                        Monster(
                            id = index,
                            name = "Monster $index",
                            heldResourceId = index + 1,
                            neededResourceId = ((index + 1) % 3) + 1, 
                            position = index
                        )
                    }
                    GameDifficulty.MEDIUM -> List(5) { index ->
                        Monster(
                            id = index,
                            name = "Monster $index",
                            heldResourceId = index + 1,
                            neededResourceId = ((index + 1) % 5) + 1,
                            position = index
                        )
                    }
                    GameDifficulty.HARD -> List(8) { index ->
                        Monster(
                            id = index,
                            name = "Monster $index",
                            heldResourceId = index + 1,
                            neededResourceId = ((index + 1) % 8) + 1,
                            position = index
                        )
                    }
                }
                Log.d(TAG, "Applied fallback monsters after error: ${monsters.size}")
            }
            
            this.gameRunning = false
            this.gameCompleted = false
            
            // Signal that state has changed
            stateChangedCondition.signalAll()
            
            Log.d(TAG, "Game setup completed with difficulty: $difficulty, ${monsters.size} monsters")
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Gets the current monster list (thread-safe)
     */
    fun getMonsters(): List<Monster> {
        readLock.lock()
        try {
            return monsters.toList()
        } finally {
            readLock.unlock()
        }
    }

    /**
     * Updates a monster's position after drag and drop
     */
    fun updateMonsterPosition(monsterId: Int, newPosition: Int) {
        writeLock.lock()
        try {
            if (gameRunning || gameCompleted) return

            // Find the monster being moved
            val movedMonster = monsters.find { it.id == monsterId } ?: return
            val oldPosition = movedMonster.position

            // Update all positions between old and new
            monsters.forEach { monster ->
                when {
                    monster.id == monsterId -> monster.position = newPosition
                    oldPosition < newPosition && monster.position in (oldPosition + 1)..newPosition ->
                        monster.position--
                    oldPosition > newPosition && monster.position in newPosition until oldPosition ->
                        monster.position++
                }
            }
            
            // Signal that state has changed
            stateChangedCondition.signalAll()
            
            Log.d(TAG, "Monster $monsterId moved from position $oldPosition to $newPosition")
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Runs the game to check if the current arrangement avoids deadlock
     * Returns true if the arrangement is deadlock-free, false otherwise
     */
    suspend fun runGame(): Boolean = withContext(Dispatchers.Default) {
        // Try to set processing flag atomically
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Game is already running, ignoring request")
            return@withContext false
        }
        
        try {
            writeLock.lock()
            try {
                if (gameRunning || gameCompleted) {
                    Log.d(TAG, "Game is already completed or running, ignoring request")
                    return@withContext false
                }
                gameRunning = true
            } finally {
                writeLock.unlock()
            }

            // Sort monsters by their current positions
            val sortedMonsters = readLock.withLock {
                monsters.sortedBy { it.position }
            }

            Log.d(TAG, "Checking for deadlock with ${sortedMonsters.size} monsters")
            
            // Check for deadlock using resource allocation graph
            val isDeadlockFree = !hasDeadlock(sortedMonsters)

            // Update state based on result
            writeLock.lock()
            try {
                gameRunning = false
                gameCompleted = true

                // If successful, mark monsters as completed
                if (isDeadlockFree) {
                    monsters.forEach { monster ->
                        monster.isTaskCompleted = true
                    }
                    Log.d(TAG, "Game completed successfully - no deadlock detected")
                } else {
                    Log.d(TAG, "Game completed with failure - deadlock detected")
                }
                
                // Signal that state has changed
                stateChangedCondition.signalAll()
            } finally {
                writeLock.unlock()
            }

            return@withContext isDeadlockFree
        } finally {
            isProcessing.set(false)
        }
    }

    /**
     * Checks if the current arrangement would result in a deadlock
     * using a resource allocation graph and banker's algorithm
     */
    private fun hasDeadlock(sortedMonsters: List<Monster>): Boolean {
        // Start with resources that are free at the beginning
        val availableResources = mutableSetOf<Int>()
        val allResourceIds = sortedMonsters.flatMap {
            listOf(it.heldResourceId, it.neededResourceId)
        }.toSet()

        // Find initially available resources (not held by any monster)
        val heldResources = sortedMonsters.map { it.heldResourceId }.toSet()
        allResourceIds.forEach { resourceId ->
            if (resourceId !in heldResources) {
                availableResources.add(resourceId)
            }
        }

        Log.d(TAG, "Initial available resources: $availableResources")
        
        // Process monsters in sequence order
        for (monster in sortedMonsters) {
            if (monster.neededResourceId in availableResources) {
                // Monster can complete its task
                availableResources.add(monster.heldResourceId) // Release its resource
                Log.d(TAG, "Monster ${monster.id} can complete its task, releasing resource ${monster.heldResourceId}")
            } else {
                // Monster is blocked - cannot proceed further
                Log.d(TAG, "Monster ${monster.id} is blocked waiting for resource ${monster.neededResourceId}")
                return true // Deadlock detected
            }
        }

        // All monsters completed successfully
        return false
    }

    /**
     * Resets the game for another round with the same difficulty
     */
    fun resetGame() {
        writeLock.lock()
        try {
            this.monsters = MonsterFactory.createMonstersForDifficulty(difficulty)
            this.gameRunning = false
            this.gameCompleted = false
            
            // Signal that state has changed
            stateChangedCondition.signalAll()
            
            Log.d(TAG, "Game reset with difficulty: $difficulty")
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Checks if the game is completed
     */
    fun isGameCompleted(): Boolean {
        readLock.lock()
        try {
            return gameCompleted
        } finally {
            readLock.unlock()
        }
    }

    /**
     * Gets the current difficulty level
     */
    fun getDifficulty(): GameDifficulty {
        readLock.lock()
        try {
            return difficulty
        } finally {
            readLock.unlock()
        }
    }
    
    /**
     * Waits until the game is completed
     * 
     * @param timeoutMs Timeout in milliseconds, or 0 for no timeout
     * @return true if the game completed, false if timed out
     */
    fun waitForCompletion(timeoutMs: Long = 0): Boolean {
        writeLock.lock()
        try {
            if (gameCompleted) return true
            
            if (timeoutMs <= 0) {
                // Wait indefinitely
                while (!gameCompleted) {
                    try {
                        stateChangedCondition.await()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
                return true
            } else {
                // Wait with timeout
                var remainingTime = timeoutMs
                val endTime = System.currentTimeMillis() + timeoutMs
                
                while (!gameCompleted && remainingTime > 0) {
                    try {
                        stateChangedCondition.await(remainingTime, java.util.concurrent.TimeUnit.MILLISECONDS)
                        remainingTime = endTime - System.currentTimeMillis()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
                
                return gameCompleted
            }
        } finally {
            writeLock.unlock()
        }
    }
    
    /**
     * Extension function to execute a block with read lock
     */
    private inline fun <T> ReentrantReadWriteLock.ReadLock.withLock(block: () -> T): T {
        lock()
        try {
            return block()
        } finally {
            unlock()
        }
    }

    /**
     * Restores monsters from a saved game state
     */
    fun restoreMonsters(savedMonsters: List<Monster>) {
        writeLock.lock()
        try {
            Log.d(TAG, "Restoring ${savedMonsters.size} monsters from saved state")
            
            // Since the Monster class might have behavior beyond just data,
            // create new instances to ensure all state is properly initialized
            this.monsters = savedMonsters.map { it.copy() }
            
            // Ensure difficulty matches the number of monsters
            this.difficulty = when {
                savedMonsters.size <= 3 -> GameDifficulty.EASY
                savedMonsters.size <= 5 -> GameDifficulty.MEDIUM
                else -> GameDifficulty.HARD
            }
            
            this.gameRunning = false
            this.gameCompleted = false
            
            // Signal that state has changed
            stateChangedCondition.signalAll()
            
            Log.d(TAG, "Game state restored with difficulty: $difficulty, ${monsters.size} monsters")
        } finally {
            writeLock.unlock()
        }
    }
}
