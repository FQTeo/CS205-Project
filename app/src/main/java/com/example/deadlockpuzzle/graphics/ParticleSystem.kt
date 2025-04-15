package com.example.deadlockpuzzle.graphics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle system for visual effects.
 * This implements part of requirement #17 (advanced 2D graphics).
 */
class ParticleSystem {
    private val particles = mutableListOf<Particle>()
    private val particlesToAdd = mutableListOf<Particle>()
    private val particlesToRemove = mutableListOf<Particle>()
    private var isUpdating = false
    private val paint = Paint()
    
    init {
        paint.isAntiAlias = true
    }
    
    /**
     * Updates all particles based on delta time
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    fun update(deltaTime: Float) {
        // Mark that we're updating to handle concurrent modifications safely
        isUpdating = true
        
        try {
            // Update all active particles
            particles.forEach { particle ->
                particle.update(deltaTime)
                
                // Mark dead particles for removal
                if (particle.isDead()) {
                    particlesToRemove.add(particle)
                }
            }
        } finally {
            isUpdating = false
        }
        
        // Process pending additions and removals
        if (particlesToAdd.isNotEmpty()) {
            particles.addAll(particlesToAdd)
            particlesToAdd.clear()
        }
        
        if (particlesToRemove.isNotEmpty()) {
            particles.removeAll(particlesToRemove)
            particlesToRemove.clear()
        }
    }
    
    /**
     * Draws all particles on the canvas
     * 
     * @param canvas Canvas to draw on
     */
    fun draw(canvas: Canvas) {
        // Create a safe copy of the particles list to avoid ConcurrentModificationException
        val particlesCopy = ArrayList(particles)
        
        // Draw from the copy
        particlesCopy.forEach { particle ->
            particle.draw(canvas, paint)
        }
    }
    
    /**
     * Emits particles at the specified position
     * 
     * @param x X coordinate to emit from
     * @param y Y coordinate to emit from
     * @param count Number of particles to emit
     * @param color Base color of particles
     * @param minSpeed Minimum speed of particles
     * @param maxSpeed Maximum speed of particles
     * @param minLifetime Minimum lifetime of particles in frames
     * @param maxLifetime Maximum lifetime of particles in frames
     */
    fun emit(
        x: Float,
        y: Float,
        count: Int,
        color: Int,
        minSpeed: Float = 50f,
        maxSpeed: Float = 150f,
        minLifetime: Int = 20,
        maxLifetime: Int = 40
    ) {
        for (i in 0 until count) {
            // Random angle
            val angle = Random.nextFloat() * Math.PI * 2
            
            // Random speed
            val speed = minSpeed + Random.nextFloat() * (maxSpeed - minSpeed)
            
            // Calculate velocity components
            val vx = cos(angle).toFloat() * speed
            val vy = sin(angle).toFloat() * speed
            
            // Random size
            val size = 5f + Random.nextFloat() * 15f
            
            // Random lifetime
            val lifetime = minLifetime + Random.nextInt(maxLifetime - minLifetime)
            
            // Create particle
            val particle = Particle(
                x = x,
                y = y,
                vx = vx,
                vy = vy,
                size = size,
                color = color,
                lifetime = lifetime
            )
            
            // Add to system
            if (isUpdating) {
                particlesToAdd.add(particle)
            } else {
                particles.add(particle)
            }
        }
    }
    
    /**
     * Creates an explosion of particles at the specified position
     * 
     * @param x X coordinate of explosion center
     * @param y Y coordinate of explosion center
     * @param count Number of particles to create
     * @param color Base color of particles
     */
    fun explode(
        x: Float,
        y: Float,
        count: Int,
        color: Int
    ) {
        // Create particles in rings
        val rings = 3
        val particlesPerRing = count / rings
        
        for (ring in 0 until rings) {
            val ringSpeed = 100f + ring * 100f
            val ringDelay = ring * 5
            
            for (i in 0 until particlesPerRing) {
                // Evenly distribute angles
                val angle = (i.toFloat() / particlesPerRing) * Math.PI * 2
                
                // Calculate velocity components
                val vx = cos(angle).toFloat() * ringSpeed
                val vy = sin(angle).toFloat() * ringSpeed
                
                // Size based on ring
                val size = 15f - ring * 3f
                
                // Lifetime based on ring
                val lifetime = 30 + ring * 10
                
                // Create particle with delay
                val particle = Particle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    size = size,
                    color = color,
                    lifetime = lifetime,
                    delay = ringDelay
                )
                
                // Add to system
                if (isUpdating) {
                    particlesToAdd.add(particle)
                } else {
                    particles.add(particle)
                }
            }
        }
        
        // Add some random particles for variety
        for (i in 0 until count / 3) {
            // Random angle
            val angle = Random.nextFloat() * Math.PI * 2
            
            // Random speed
            val speed = 50f + Random.nextFloat() * 250f
            
            // Calculate velocity components
            val vx = cos(angle).toFloat() * speed
            val vy = sin(angle).toFloat() * speed
            
            // Random size
            val size = 3f + Random.nextFloat() * 12f
            
            // Random lifetime
            val lifetime = 20 + Random.nextInt(30)
            
            // Random delay
            val delay = Random.nextInt(10)
            
            // Create particle
            val particle = Particle(
                x = x,
                y = y,
                vx = vx,
                vy = vy,
                size = size,
                color = color,
                lifetime = lifetime,
                delay = delay
            )
            
            // Add to system
            if (isUpdating) {
                particlesToAdd.add(particle)
            } else {
                particles.add(particle)
            }
        }
    }

    /**
     * Clears all particles
     */
    fun clear() {
        if (isUpdating) {
            particlesToRemove.addAll(particles)
        } else {
            particles.clear()
        }
    }
    
    /**
     * Represents a single particle in the system
     */
    private inner class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var color: Int,
        val lifetime: Int,
        var delay: Int = 0
    ) {
        private var age = 0
        private var shader: Shader? = null
        
        /**
         * Updates the particle state
         * 
         * @param deltaTime Time elapsed since last frame in seconds
         */
        fun update(deltaTime: Float) {
            // Don't update if still in delay
            if (delay > 0) {
                delay--
                return
            }
            
            // Update position
            x += vx * deltaTime
            y += vy * deltaTime
            
            // Apply gravity
            vy += 50f * deltaTime
            
            // Apply drag
            val drag = 0.98f
            vx *= drag
            vy *= drag
            
            // Age the particle
            age++
            
            // Update shader
            updateShader()
        }
        
        /**
         * Updates the particle's shader
         */
        private fun updateShader() {
            // Calculate alpha based on age
            val lifeRatio = age.toFloat() / lifetime
            val alpha = (255 * (1 - lifeRatio)).toInt().coerceIn(0, 255)
            
            // Extract color components
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            
            // Create colors for gradient
            val colors = intArrayOf(
                Color.argb(alpha, red, green, blue),
                Color.argb((alpha * 0.5f).toInt(), red, green, blue),
                Color.argb(0, red, green, blue)
            )
            
            // Create positions for gradient
            val positions = floatArrayOf(0f, 0.7f, 1f)
            
            // Create shader
            shader = RadialGradient(
                x, y, size,
                colors, positions,
                Shader.TileMode.CLAMP
            )
        }
        
        /**
         * Draws the particle on the canvas
         * 
         * @param canvas Canvas to draw on
         * @param paint Paint to use for drawing
         */
        fun draw(canvas: Canvas, paint: Paint) {
            // Don't draw if still in delay
            if (delay > 0) return
            
            // Set shader
            if (shader == null) {
                updateShader()
            }
            paint.shader = shader
            
            // Draw particle
            canvas.drawCircle(x, y, size, paint)
            
            // Reset shader
            paint.shader = null
        }
        
        /**
         * Checks if the particle is dead (exceeded its lifetime)
         */
        fun isDead(): Boolean {
            return age >= lifetime
        }
    }
}
