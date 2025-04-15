package com.example.deadlockpuzzle.threading

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import com.example.deadlockpuzzle.logic.GameLogic
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles background processing using Android's Looper and Handler system.
 * This implements requirement #9 (advanced threading abstractions).
 */
class BackgroundMessageHandler {
    private val TAG = "BackgroundMessageHandler"
    
    // Message types
    private val MSG_PROCESS_GAME_LOGIC = 1
    private val MSG_PROCESS_PHYSICS = 2
    private val MSG_PROCESS_AI = 3
    private val MSG_CUSTOM_TASK = 4
    private val MSG_SHUTDOWN = 5
    
    // Background thread for processing
    private val handlerThread: HandlerThread
    
    // Handler for processing messages on the background thread
    private val backgroundHandler: Handler
    
    // Handler for posting results to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Is the handler initialized?
    private val initialized = AtomicBoolean(false)
    
    // Is the handler running?
    private val running = AtomicBoolean(true)
    
    init {
        // Create and start the background thread
        handlerThread = HandlerThread("BackgroundProcessingThread").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
        
        // Create the handler on the background thread
        backgroundHandler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                if (!running.get()) return
                
                try {
                    when (msg.what) {
                        MSG_PROCESS_GAME_LOGIC -> handleGameLogicProcessing(msg)
                        MSG_PROCESS_PHYSICS -> handlePhysicsProcessing(msg)
                        MSG_PROCESS_AI -> handleAIProcessing(msg)
                        MSG_CUSTOM_TASK -> handleCustomTask(msg)
                        MSG_SHUTDOWN -> handleShutdown()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling message: ${msg.what}", e)
                }
            }
        }
        
        initialized.set(true)
        Log.d(TAG, "Background message handler initialized")
    }
    
    /**
     * Handles game logic processing
     * 
     * @param msg Message containing game logic data
     */
    private fun handleGameLogicProcessing(msg: Message) {
        val gameLogic = msg.obj as? GameLogic ?: return
        val waitForCompletion = msg.arg1 == 1
        val latch = msg.obj as? CountDownLatch
        
        try {
            Log.d(TAG, "Processing game logic")
            
            // Simulate complex game logic processing
            Thread.sleep(50)
            
            // Signal completion if needed
            if (waitForCompletion) {
                latch?.countDown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing game logic", e)
        }
    }
    
    /**
     * Handles physics processing
     * 
     * @param msg Message containing physics data
     */
    private fun handlePhysicsProcessing(msg: Message) {
        val deltaTime = msg.obj as? Float ?: 0.016f // Default to 60fps
        
        try {
            Log.d(TAG, "Processing physics with delta time: $deltaTime")
            
            // Simulate physics processing
            Thread.sleep(20)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing physics", e)
        }
    }
    
    /**
     * Handles AI processing
     * 
     * @param msg Message containing AI data
     */
    private fun handleAIProcessing(msg: Message) {
        try {
            Log.d(TAG, "Processing AI")
            
            // Simulate AI processing
            Thread.sleep(30)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing AI", e)
        }
    }
    
    /**
     * Handles a custom task
     * 
     * @param msg Message containing task data
     */
    private fun handleCustomTask(msg: Message) {
        val task = msg.obj as? Runnable ?: return
        
        try {
            Log.d(TAG, "Processing custom task")
            task.run()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing custom task", e)
        }
    }
    
    /**
     * Handles shutdown
     */
    private fun handleShutdown() {
        running.set(false)
        Log.d(TAG, "Shutting down background message handler")
        handlerThread.quitSafely()
    }
    
    /**
     * Queues game logic processing
     * 
     * @param gameLogic The game logic to process
     * @param waitForCompletion Whether to wait for completion
     * @param timeoutMs Timeout in milliseconds if waiting for completion
     * @return True if the processing completed successfully, false otherwise
     */
    fun queueGameLogicProcessing(
        gameLogic: GameLogic,
        waitForCompletion: Boolean = false,
        timeoutMs: Long = 1000
    ): Boolean {
        if (!initialized.get() || !running.get()) return false
        
        if (waitForCompletion) {
            val latch = CountDownLatch(1)
            
            val msg = Message.obtain().apply {
                what = MSG_PROCESS_GAME_LOGIC
                obj = gameLogic
                arg1 = 1 // Wait for completion
                obj = latch
            }
            
            backgroundHandler.sendMessage(msg)
            
            // Wait for completion
            try {
                return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Interrupted while waiting for game logic processing", e)
                Thread.currentThread().interrupt()
                return false
            }
        } else {
            val msg = Message.obtain().apply {
                what = MSG_PROCESS_GAME_LOGIC
                obj = gameLogic
                arg1 = 0 // Don't wait for completion
            }
            
            backgroundHandler.sendMessage(msg)
            return true
        }
    }
    
    /**
     * Shuts down the background message handler
     */
    fun shutdown() {
        if (!initialized.get() || !running.get()) return
        
        val msg = Message.obtain().apply {
            what = MSG_SHUTDOWN
        }
        
        backgroundHandler.sendMessage(msg)
    }
}
