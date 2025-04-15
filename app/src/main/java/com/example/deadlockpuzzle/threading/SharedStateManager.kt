package com.example.deadlockpuzzle.threading

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.locks.Condition

/**
 * Manages shared state with thread-safe access using read-write locks.
 * This implements requirement #5 (thread synchronization).
 */
class SharedStateManager<T>(initialValue: T) {
    private val lock = ReentrantReadWriteLock()
    private val readLock = lock.readLock()
    private val writeLock = lock.writeLock()
    private var value: T = initialValue
    
    // Condition for waiting on state changes
    private val stateChangedCondition: Condition = writeLock.newCondition()
    
    /**
     * Reads the current value (thread-safe)
     */
    fun read(): T {
        readLock.lock()
        try {
            return value
        } finally {
            readLock.unlock()
        }
    }
    
    /**
     * Writes a new value (thread-safe)
     * 
     * @param newValue The new value to set
     * @param signalChange Whether to signal waiting threads about the change
     */
    fun write(newValue: T, signalChange: Boolean = true) {
        writeLock.lock()
        try {
            value = newValue
            if (signalChange) {
                stateChangedCondition.signalAll()
            }
        } finally {
            writeLock.unlock()
        }
    }
    
    /**
     * Updates the value using a transform function (thread-safe)
     * 
     * @param transform Function that takes the current value and returns a new value
     * @param signalChange Whether to signal waiting threads about the change
     */
    fun update(transform: (T) -> T, signalChange: Boolean = true) {
        writeLock.lock()
        try {
            value = transform(value)
            if (signalChange) {
                stateChangedCondition.signalAll()
            }
        } finally {
            writeLock.unlock()
        }
    }
    
    /**
     * Executes a block with read lock and provides the current value
     */
    fun <R> withReadLock(block: (T) -> R): R {
        readLock.lock()
        try {
            return block(value)
        } finally {
            readLock.unlock()
        }
    }
}
