package com.example.deadlockpuzzle.threading

import android.os.Handler
import android.os.Looper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A task queue implementing the producer-consumer pattern.
 * This implements requirement #8 (producer-consumer pattern).
 */
class TaskQueue<T>(private val numConsumers: Int = 2) {
    private val queue = LinkedBlockingQueue<T>()
    private val consumers = mutableListOf<Thread>()
    private val running = AtomicBoolean(true)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeConsumers = AtomicInteger(0)
    
    /**
     * Starts the consumer threads
     * 
     * @param processTask Function to process each task
     * @param onMainThread Whether to execute the task on the main thread
     */
    fun start(processTask: (T) -> Unit, onMainThread: Boolean = false) {
        for (i in 0 until numConsumers) {
            val consumer = Thread {
                activeConsumers.incrementAndGet()
                try {
                    while (running.get()) {
                        try {
                            val task = queue.take() // Blocks until a task is available
                            
                            if (onMainThread) {
                                mainHandler.post { processTask(task) }
                            } else {
                                processTask(task)
                            }
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                } finally {
                    activeConsumers.decrementAndGet()
                }
            }
            consumer.name = "TaskConsumer-$i"
            consumer.start()
            consumers.add(consumer)
        }
    }
    
    /**
     * Produces a task to be consumed (blocking if queue is full)
     */
    fun produce(task: T) {
        if (running.get()) {
            queue.put(task) // Will block if queue is full
        }
    }
    
    /**
     * Gets the number of tasks waiting to be processed
     */
    fun size(): Int {
        return queue.size
    }
    
    /**
     * Shuts down the task queue
     */
    fun shutdown() {
        running.set(false)
        consumers.forEach { it.interrupt() }
        
        // Wait for consumers to finish
        consumers.forEach { 
            try {
                it.join(500) // Wait up to 500ms per thread
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        
        queue.clear()
    }
}
