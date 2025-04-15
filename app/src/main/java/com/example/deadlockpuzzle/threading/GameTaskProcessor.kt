package com.example.deadlockpuzzle.threading

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Processes game tasks using the producer-consumer pattern.
 * This implements requirement #8 (producer-consumer pattern).
 */
class GameTaskProcessor {
    private val TAG = "GameTaskProcessor"
    
    /**
     * Sealed class representing different types of game tasks
     */
    sealed class GameTask {
        data class UpdateMonster(val monsterId: Int, val newX: Float, val newY: Float) : GameTask()
        data class CreateParticles(val x: Float, val y: Float, val count: Int, val color: Int) : GameTask()
        data class PlaySound(val soundId: Int, val volume: Float) : GameTask()
        data class CheckCollision(val objectId: Int, val x: Float, val y: Float) : GameTask()
        data class CalculatePhysics(val deltaTime: Float, val objectIds: List<Int>) : GameTask()
    }
    
    private val taskQueue = TaskQueue<GameTask>(numConsumers = 2)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Callbacks for task results
    private val collisionCallbacks = ConcurrentHashMap<Int, (Boolean) -> Unit>()
    private val physicsCallbacks = ConcurrentHashMap<Int, (Boolean) -> Unit>()
    
    init {
        // Start the task queue with a processor function
        taskQueue.start(processTask = { task ->
            try {
                processTask(task)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing task: $task", e)
            }
        }, onMainThread = false)
    }
    
    /**
     * Processes a game task
     */
    private fun processTask(task: GameTask) {
        when (task) {
            is GameTask.UpdateMonster -> {
                // Process monster update
                Log.d(TAG, "Updating monster ${task.monsterId} to position (${task.newX}, ${task.newY})")
                // Simulate work
                Thread.sleep(5)
            }
            
            is GameTask.CreateParticles -> {
                // Create particles
                Log.d(TAG, "Creating ${task.count} particles at (${task.x}, ${task.y})")
                // Simulate work
                Thread.sleep(10)
            }
            
            is GameTask.PlaySound -> {
                // Play sound
                Log.d(TAG, "Playing sound ${task.soundId} at volume ${task.volume}")
                // Simulate work
                Thread.sleep(5)
            }
            
            is GameTask.CheckCollision -> {
                // Check collision
                Log.d(TAG, "Checking collision for object ${task.objectId} at (${task.x}, ${task.y})")
                // Simulate complex collision detection
                Thread.sleep(15)
                
                val hasCollision = Math.random() < 0.3 // Simulate collision result
                
                // Notify on main thread
                mainHandler.post {
                    collisionCallbacks[task.objectId]?.invoke(hasCollision)
                    collisionCallbacks.remove(task.objectId)
                }
            }
            
            is GameTask.CalculatePhysics -> {
                // Calculate physics
                Log.d(TAG, "Calculating physics for ${task.objectIds.size} objects with delta ${task.deltaTime}")
                // Simulate complex physics calculation
                Thread.sleep(20)
                
                // Notify on main thread
                mainHandler.post {
                    task.objectIds.forEach { id ->
                        physicsCallbacks[id]?.invoke(true)
                        physicsCallbacks.remove(id)
                    }
                }
            }

            else -> {}
        }
    }
    
    /**
     * Produces a task to create particles
     */
    fun createParticles(x: Float, y: Float, count: Int, color: Int) {
        taskQueue.produce(GameTask.CreateParticles(x, y, count, color))
    }
    
    /**
     * Produces a task to play a sound
     */
    fun playSound(soundId: Int, volume: Float = 1.0f) {
        taskQueue.produce(GameTask.PlaySound(soundId, volume))
    }

    /**
     * Shuts down the task processor
     */
    fun shutdown() {
        taskQueue.shutdown()
        collisionCallbacks.clear()
        physicsCallbacks.clear()
    }
}
