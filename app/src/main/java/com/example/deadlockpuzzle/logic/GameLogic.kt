package com.example.deadlockpuzzle.logic

import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.models.Monster
import com.example.deadlockpuzzle.models.MonsterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contains the game logic and deadlock detection algorithms
 */
class GameLogic {
    // Current game state
    private var monsters = listOf<Monster>()
    private var difficulty = GameDifficulty.EASY
    private var gameRunning = false
    private var gameCompleted = false

    // Synchronization lock for thread safety
    private val stateLock = Any()

    /**
     * Sets up a new game with a given difficulty
     */
    fun setupGame(difficulty: GameDifficulty) {
        synchronized(stateLock) {
            this.difficulty = difficulty
            this.monsters = MonsterFactory.createMonstersForDifficulty(difficulty)
            this.gameRunning = false
            this.gameCompleted = false
        }
    }

    /**
     * Gets the current monster list (thread-safe)
     */
    fun getMonsters(): List<Monster> {
        synchronized(stateLock) {
            return monsters.toList()
        }
    }

    /**
     * Updates a monster's position after drag and drop
     */
    fun updateMonsterPosition(monsterId: Int, newPosition: Int) {
        synchronized(stateLock) {
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
        }
    }

    /**
     * Runs the game to check if the current arrangement avoids deadlock
     * Returns true if the arrangement is deadlock-free, false otherwise
     */
    suspend fun runGame(): Boolean = withContext(Dispatchers.Default) {
        synchronized(stateLock) {
            if (gameRunning || gameCompleted) return@withContext false
            gameRunning = true
        }

        // Sort monsters by their current positions
        val sortedMonsters = synchronized(stateLock) {
            monsters.sortedBy { it.position }
        }

        // Check for deadlock using resource allocation graph
        val isDeadlockFree = !hasDeadlock(sortedMonsters)

        // Update state based on result
        synchronized(stateLock) {
            gameRunning = false
            gameCompleted = true

            // If successful, mark monsters as completed
            if (isDeadlockFree) {
                monsters.forEach { monster ->
                    monster.isTaskCompleted = true
                }
            }
        }

        return@withContext isDeadlockFree
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

        // Process monsters in sequence order
        for (monster in sortedMonsters) {
            if (monster.neededResourceId in availableResources) {
                // Monster can complete its task
                availableResources.add(monster.heldResourceId) // Release its resource
            } else {
                // Monster is blocked - cannot proceed further
                // This means remaining monsters can't execute
                return true // Deadlock detected!
            }
        }

        // All monsters completed successfully
        return false
    }


    /**
     * Resets the game for another round with the same difficulty
     */
    fun resetGame() {
        synchronized(stateLock) {
            this.monsters = MonsterFactory.createMonstersForDifficulty(difficulty)
            this.gameRunning = false
            this.gameCompleted = false
        }
    }

    /**
     * Checks if the game is completed
     */
    fun isGameCompleted(): Boolean {
        synchronized(stateLock) {
            return gameCompleted
        }
    }

    /**
     * Gets the current difficulty level
     */
    fun getDifficulty(): GameDifficulty {
        synchronized(stateLock) {
            return difficulty
        }
    }
}