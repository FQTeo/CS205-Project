package com.example.deadlockpuzzle.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deadlockpuzzle.logic.GameLogic
import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.models.Monster
import kotlinx.coroutines.launch

/**
 * ViewModel for the Deadlock Puzzle game implementing the MVVM pattern
 */
class GameViewModel : ViewModel() {
    // Game logic
    val gameLogic = GameLogic()

    // LiveData for monsters (UI observes this)
    private val _monsters = MutableLiveData<List<Monster>>()
    val monsters: LiveData<List<Monster>> = _monsters

    // Game state
    private val _isGameCompleted = MutableLiveData(false)
    val isGameCompleted: LiveData<Boolean> = _isGameCompleted

    /**
     * Sets up a new game with the given difficulty
     */
    fun setupGame(difficulty: GameDifficulty) {
        gameLogic.setupGame(difficulty)
        _monsters.value = gameLogic.getMonsters()
        _isGameCompleted.value = false
    }

    /**
     * Runs the game to check if the current monster arrangement avoids deadlock
     * Returns true if the arrangement is deadlock-free, false otherwise
     */
    suspend fun runGame(): Boolean {
        val result = gameLogic.runGame()
        _monsters.value = gameLogic.getMonsters()
        _isGameCompleted.value = true
        return result
    }

    /**
     * Resets the game for another round
     */
    fun resetGame() {
        gameLogic.resetGame()
        _monsters.value = gameLogic.getMonsters()
        _isGameCompleted.value = false
    }
}