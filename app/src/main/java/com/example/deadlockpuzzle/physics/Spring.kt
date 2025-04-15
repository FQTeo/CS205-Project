package com.example.deadlockpuzzle.physics

import kotlin.math.sqrt

/**
 * Represents a spring connecting two physics objects.
 * This is part of requirement #20 (simulation with natural forces).
 */
class Spring(
    val objectA: PhysicsObject,
    val objectB: PhysicsObject,
    var restLength: Float,
    var stiffness: Float,
    var damping: Float,
    var breakingForce: Float = Float.MAX_VALUE
) {
    // Is the spring broken?
    private var broken = false
    
    // Maximum force experienced by the spring
    private var maxForce = 0f
    
    /**
     * Checks if the spring is broken
     * 
     * @return True if the spring is broken, false otherwise
     */
    fun isBroken(): Boolean {
        return broken
    }
    
    /**
     * Checks if the spring should break based on the current force
     */
    fun checkBreak() {
        if (broken) return
        
        // Calculate distance between objects
        val dx = objectB.x - objectA.x
        val dy = objectB.y - objectA.y
        val distance = sqrt(dx * dx + dy * dy)
        
        // Calculate spring force
        val springForce = stiffness * (distance - restLength)
        
        // Update maximum force
        if (springForce > maxForce) {
            maxForce = springForce
        }
        
        // Check if spring should break
        if (springForce > breakingForce) {
            broken = true
        }
    }
}
