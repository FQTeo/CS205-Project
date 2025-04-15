package com.example.deadlockpuzzle.physics

import android.util.Log
import kotlin.math.sqrt

/**
 * Physics system for simulating natural forces and collisions.
 * This implements requirement #20 (simulation with natural forces).
 */
class PhysicsSystem {
    private val TAG = "PhysicsSystem"
    
    // Physics objects in the system
    private val objects = mutableListOf<PhysicsObject>()
    
    // Springs connecting objects
    private val springs = mutableListOf<Spring>()
    
    // World bounds
    private var worldWidth = 0f
    private var worldHeight = 0f
    
    // Physics constants
    private var gravity = 9.8f * 50f // Scaled gravity
    private var enableGravity = true
    
    // Collision detection grid for optimization
    private val collisionGrid = mutableMapOf<Pair<Int, Int>, MutableList<PhysicsObject>>()
    private var gridCellSize = 100f
    
    /**
     * Updates all physics objects and resolves collisions
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    fun update(deltaTime: Float) {
        // Cap delta time to prevent instability with large time steps
        val cappedDeltaTime = deltaTime.coerceAtMost(0.05f)
        
        // Apply forces
        applyForces(cappedDeltaTime)
        
        // Update springs
        updateSprings(cappedDeltaTime)
        
        // Update object positions
        updateObjects(cappedDeltaTime)
        
        // Detect and resolve collisions
        handleCollisions()
        
        // Apply world bounds
        applyWorldBounds()
    }
    
    /**
     * Applies forces to all objects
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    private fun applyForces(deltaTime: Float) {
        if (enableGravity) {
            // Create a safe copy of the objects list to prevent ConcurrentModificationException
            val objectsCopy = ArrayList(objects)
            
            objectsCopy.forEach { obj ->
                if (obj.affectedByGravity) {
                    obj.applyForce(0f, gravity * obj.mass)
                }
            }
        }
    }
    
    /**
     * Updates all springs
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    private fun updateSprings(deltaTime: Float) {
        // Create a safe copy of the springs list to prevent ConcurrentModificationException
        val springsCopy = ArrayList(springs)
        
        // Process each spring
        springsCopy.forEach { spring ->
            if (spring.isBroken()) return@forEach
            
            val objA = spring.objectA
            val objB = spring.objectB
            
            // Calculate distance between objects
            val dx = objB.x - objA.x
            val dy = objB.y - objA.y
            val distance = sqrt(dx * dx + dy * dy)
            
            // Skip if objects are at the same position
            if (distance < 0.0001f) return@forEach
            
            // Calculate spring force
            val springForce = spring.stiffness * (distance - spring.restLength)
            
            // Calculate damping force
            val dvx = objB.vx - objA.vx
            val dvy = objB.vy - objA.vy
            val dampingForce = spring.damping * ((dx * dvx + dy * dvy) / distance)
            
            // Calculate total force
            val totalForce = springForce + dampingForce
            
            // Calculate force components
            val fx = totalForce * dx / distance
            val fy = totalForce * dy / distance
            
            // Apply forces to objects
            if (!objA.isFixed) {
                objA.applyForce(fx, fy)
            }
            
            if (!objB.isFixed) {
                objB.applyForce(-fx, -fy)
            }
            
            // Check if spring should break
            spring.checkBreak()
        }
    }
    
    /**
     * Updates all physics objects
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    private fun updateObjects(deltaTime: Float) {
        // Create a safe copy of the objects list to prevent ConcurrentModificationException
        val objectsCopy = ArrayList(objects)
        
        objectsCopy.forEach { obj ->
            obj.update(deltaTime)
        }
    }
    
    /**
     * Detects and resolves collisions between objects
     */
    private fun handleCollisions() {
        // Clear collision grid
        collisionGrid.clear()
        
        // Create a safe copy of the objects list to prevent ConcurrentModificationException
        val objectsCopy = ArrayList(objects)
        
        // Populate collision grid
        objectsCopy.forEach { obj ->
            if (!obj.collidable) return@forEach
            
            val gridX = (obj.x / gridCellSize).toInt()
            val gridY = (obj.y / gridCellSize).toInt()
            
            // Add object to its grid cell
            val cell = Pair(gridX, gridY)
            if (!collisionGrid.containsKey(cell)) {
                collisionGrid[cell] = mutableListOf()
            }
            collisionGrid[cell]?.add(obj)
            
            // Also add to neighboring cells if the object spans multiple cells
            val radius = obj.radius
            val minGridX = ((obj.x - radius) / gridCellSize).toInt()
            val maxGridX = ((obj.x + radius) / gridCellSize).toInt()
            val minGridY = ((obj.y - radius) / gridCellSize).toInt()
            val maxGridY = ((obj.y + radius) / gridCellSize).toInt()
            
            for (gx in minGridX..maxGridX) {
                for (gy in minGridY..maxGridY) {
                    val neighborCell = Pair(gx, gy)
                    if (neighborCell != cell) {
                        if (!collisionGrid.containsKey(neighborCell)) {
                            collisionGrid[neighborCell] = mutableListOf()
                        }
                        collisionGrid[neighborCell]?.add(obj)
                    }
                }
            }
        }
        
        // Make a safe copy of grid values to prevent concurrency issues
        val gridValuesCopy = HashMap<Pair<Int, Int>, List<PhysicsObject>>()
        collisionGrid.forEach { (cell, objects) ->
            gridValuesCopy[cell] = ArrayList(objects)
        }
        
        // Check for collisions within each grid cell
        gridValuesCopy.values.forEach { cellObjects ->
            for (i in 0 until cellObjects.size) {
                val objA = cellObjects[i]
                
                for (j in i + 1 until cellObjects.size) {
                    val objB = cellObjects[j]
                    
                    // Skip if both objects are fixed
                    if (objA.isFixed && objB.isFixed) continue
                    
                    // Check and resolve collision
                    if (objA.isColliding(objB)) {
                        objA.resolveCollision(objB)
                    }
                }
            }
        }
    }
    
    /**
     * Applies world bounds to keep objects within the world
     */
    private fun applyWorldBounds() {
        if (worldWidth <= 0 || worldHeight <= 0) return
        
        // Create a safe copy of the objects list to prevent ConcurrentModificationException
        val objectsCopy = ArrayList(objects)
        
        objectsCopy.forEach { obj ->
            if (obj.isFixed) return@forEach
            
            val radius = obj.radius
            
            // Constrain X position
            if (obj.x - radius < 0) {
                obj.x = radius
                if (obj.vx < 0) obj.vx = -obj.vx * obj.elasticity
            } else if (obj.x + radius > worldWidth) {
                obj.x = worldWidth - radius
                if (obj.vx > 0) obj.vx = -obj.vx * obj.elasticity
            }
            
            // Constrain Y position
            if (obj.y - radius < 0) {
                obj.y = radius
                if (obj.vy < 0) obj.vy = -obj.vy * obj.elasticity
            } else if (obj.y + radius > worldHeight) {
                obj.y = worldHeight - radius
                if (obj.vy > 0) obj.vy = -obj.vy * obj.elasticity
            }
        }
    }
    
    /**
     * Adds a physics object to the system
     * 
     * @param obj The physics object to add
     */
    @Synchronized
    fun addObject(obj: PhysicsObject) {
        try {
            objects.add(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding physics object", e)
        }
    }
    
    /**
     * Removes a physics object from the system
     * 
     * @param obj The physics object to remove
     */
    @Synchronized
    fun removeObject(obj: PhysicsObject) {
        try {
            objects.remove(obj)
            
            // Remove any springs connected to this object
            springs.removeAll { it.objectA == obj || it.objectB == obj }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing physics object", e)
        }
    }
    
    /**
     * Adds a spring to the system
     * 
     * @param spring The spring to add
     */
    @Synchronized
    fun addSpring(spring: Spring) {
        try {
            springs.add(spring)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding spring", e)
        }
    }
    
    /**
     * Sets the world bounds
     * 
     * @param width Width of the world
     * @param height Height of the world
     */
    fun setWorldBounds(width: Float, height: Float) {
        worldWidth = width
        worldHeight = height
        
        // Update grid cell size based on world size
        gridCellSize = (width.coerceAtLeast(height) / 10f).coerceAtLeast(50f)
    }

    /**
     * Applies an explosion force to all objects within a radius
     * 
     * @param x X coordinate of explosion center
     * @param y Y coordinate of explosion center
     * @param radius Radius of explosion
     * @param force Strength of explosion force
     */
    fun applyExplosion(x: Float, y: Float, radius: Float, force: Float) {
        // Create a thread-safe copy of the objects list
        val objectsCopy = synchronized(this) { ArrayList(objects) }
        
        objectsCopy.forEach { obj ->
            if (obj.isFixed) return@forEach
            
            // Calculate distance from explosion to object
            val dx = obj.x - x
            val dy = obj.y - y
            val distance = sqrt(dx * dx + dy * dy)
            
            // Skip if object is outside explosion radius
            if (distance > radius) return@forEach
            
            // Calculate force based on distance (decreases with distance)
            val forceScale = 1 - (distance / radius)
            val explosionForce = force * forceScale
            
            // Calculate force direction (away from explosion)
            val dirX = if (distance > 0.0001f) dx / distance else 0f
            val dirY = if (distance > 0.0001f) dy / distance else 0f
            
            // Apply impulse
            obj.applyImpulse(dirX * explosionForce, dirY * explosionForce)
        }
    }
    
    /**
     * Clears all objects and springs
     */
    @Synchronized
    fun clear() {
        try {
            Log.d(TAG, "Clearing physics system: ${objects.size} objects, ${springs.size} springs")
            objects.clear()
            springs.clear()
            collisionGrid.clear()
            Log.d(TAG, "Physics system cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing physics system", e)
        }
    }
}
