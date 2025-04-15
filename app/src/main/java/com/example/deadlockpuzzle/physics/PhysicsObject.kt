package com.example.deadlockpuzzle.physics

import kotlin.math.sqrt

/**
 * Represents a physical object in the physics simulation.
 * This is part of requirement #20 (simulation with natural forces).
 */
class PhysicsObject(
    var x: Float,
    var y: Float,
    var radius: Float,
    var mass: Float,
    var affectedByGravity: Boolean = true,
    var collidable: Boolean = true,
    var id: Int = nextId()
) {
    // Velocity components
    var vx: Float = 0f
    var vy: Float = 0f
    
    // Acceleration components
    var ax: Float = 0f
    var ay: Float = 0f
    
    // Damping factor (air resistance)
    var damping: Float = 0.98f
    
    // Elasticity (bounciness) for collisions
    var elasticity: Float = 0.7f
    
    // Is the object fixed in place?
    var isFixed: Boolean = mass <= 0f
    
    // Custom properties for game-specific behavior
    val properties = mutableMapOf<String, Any>()
    
    /**
     * Updates the object's position and velocity based on forces
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    fun update(deltaTime: Float) {
        if (isFixed) return
        
        // Update velocity based on acceleration
        vx += ax * deltaTime
        vy += ay * deltaTime
        
        // Apply damping
        vx *= damping
        vy *= damping
        
        // Update position based on velocity
        x += vx * deltaTime
        y += vy * deltaTime
        
        // Reset acceleration for next frame
        ax = 0f
        ay = 0f
    }
    
    /**
     * Applies a force to the object
     * 
     * @param fx Force in x direction
     * @param fy Force in y direction
     */
    fun applyForce(fx: Float, fy: Float) {
        if (isFixed) return
        
        // F = ma, so a = F/m
        ax += fx / mass
        ay += fy / mass
    }
    
    /**
     * Applies an impulse to the object (instantaneous change in velocity)
     * 
     * @param ix Impulse in x direction
     * @param iy Impulse in y direction
     */
    fun applyImpulse(ix: Float, iy: Float) {
        if (isFixed) return
        
        // p = mv, so v = p/m
        vx += ix / mass
        vy += iy / mass
    }
    
    /**
     * Sets the object's position
     * 
     * @param newX New x coordinate
     * @param newY New y coordinate
     */
    fun setPosition(newX: Float, newY: Float) {
        x = newX
        y = newY
    }
    
    /**
     * Sets the object's velocity
     * 
     * @param newVx New x velocity
     * @param newVy New y velocity
     */
    fun setVelocity(newVx: Float, newVy: Float) {
        vx = newVx
        vy = newVy
    }
    
    /**
     * Checks if this object is colliding with another object
     * 
     * @param other The other physics object
     * @return True if the objects are colliding, false otherwise
     */
    fun isColliding(other: PhysicsObject): Boolean {
        if (!collidable || !other.collidable) return false
        
        val dx = other.x - x
        val dy = other.y - y
        val distance = sqrt(dx * dx + dy * dy)
        
        return distance < (radius + other.radius)
    }
    
    /**
     * Resolves a collision with another object
     * 
     * @param other The other physics object
     */
    fun resolveCollision(other: PhysicsObject) {
        if (!collidable || !other.collidable) return
        if (isFixed && other.isFixed) return
        
        // Calculate distance between objects
        val dx = other.x - x
        val dy = other.y - y
        val distance = sqrt(dx * dx + dy * dy)
        
        // Check if objects are colliding
        if (distance >= radius + other.radius) return
        
        // Calculate normal vector
        val nx = dx / distance
        val ny = dy / distance
        
        // Calculate relative velocity
        val rvx = other.vx - vx
        val rvy = other.vy - vy
        
        // Calculate relative velocity along normal
        val velAlongNormal = rvx * nx + rvy * ny
        
        // Do not resolve if objects are moving away from each other
        if (velAlongNormal > 0) return
        
        // Calculate elasticity
        val e = (elasticity + other.elasticity) / 2
        
        // Calculate impulse scalar
        var j = -(1 + e) * velAlongNormal
        j /= (1 / mass) + (1 / other.mass)
        
        // Apply impulse
        val impx = j * nx
        val impy = j * ny
        
        if (!isFixed) {
            vx -= impx / mass
            vy -= impy / mass
        }
        
        if (!other.isFixed) {
            other.vx += impx / other.mass
            other.vy += impy / other.mass
        }
        
        // Correct position to prevent objects from sinking into each other
        val correctionPercent = 0.2f // Penetration correction factor
        val correctionDistance = (radius + other.radius - distance) * correctionPercent
        
        if (!isFixed && !other.isFixed) {
            // Both objects move
            val correctionX = nx * correctionDistance / 2
            val correctionY = ny * correctionDistance / 2
            
            x -= correctionX
            y -= correctionY
            other.x += correctionX
            other.y += correctionY
        } else if (!isFixed) {
            // Only this object moves
            x -= nx * correctionDistance
            y -= ny * correctionDistance
        } else if (!other.isFixed) {
            // Only the other object moves
            other.x += nx * correctionDistance
            other.y += ny * correctionDistance
        }
    }
    
    companion object {
        private var idCounter = 0
        
        /**
         * Generates a unique ID for each physics object
         */
        private fun nextId(): Int {
            return idCounter++
        }
    }
}
