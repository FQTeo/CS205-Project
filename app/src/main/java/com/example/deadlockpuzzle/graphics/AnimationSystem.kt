package com.example.deadlockpuzzle.graphics

import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * System for managing frame-based animations.
 * This implements part of requirement #2 (real-time elements - frame-based updates).
 */
class AnimationSystem {
    private val TAG = "AnimationSystem"
    
    // List of active animations
    private val animations = CopyOnWriteArrayList<Animation>()
    
    // Map of animation types to factory functions
    private val animationFactories = ConcurrentHashMap<String, (AnimationParams) -> Animation>()
    
    // Counter for generating unique animation IDs
    private var nextAnimationId = 0
    
    init {
        // Register built-in animation types
        registerAnimationType("fade") { params -> FadeAnimation(params) }
        registerAnimationType("scale") { params -> ScaleAnimation(params) }
        registerAnimationType("rotate") { params -> RotateAnimation(params) }
        registerAnimationType("translate") { params -> TranslateAnimation(params) }
        registerAnimationType("color") { params -> ColorAnimation(params) }
        registerAnimationType("sequence") { params -> SequenceAnimation(params) }
        registerAnimationType("parallel") { params -> ParallelAnimation(params) }
    }
    
    /**
     * Updates all animations based on delta time
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     */
    fun update(deltaTime: Float) {
        // Update all animations
        animations.forEach { animation ->
            animation.update(deltaTime)
            
            // Remove completed animations
            if (animation.isCompleted()) {
                animations.remove(animation)
            }
        }
    }
    
    /**
     * Draws all animations on the canvas
     * 
     * @param canvas Canvas to draw on
     * @param paint Paint to use for drawing
     */
    fun draw(canvas: Canvas, paint: Paint) {
        animations.forEach { animation ->
            animation.draw(canvas, paint)
        }
    }
    
    /**
     * Creates and starts a new animation
     * 
     * @param type Type of animation to create
     * @param params Parameters for the animation
     * @return ID of the created animation
     */
    fun createAnimation(type: String, params: AnimationParams): Int {
        val factory = animationFactories[type] ?: run {
            Log.e(TAG, "Unknown animation type: $type")
            return -1
        }
        
        val animation = factory(params)
        animation.id = nextAnimationId++
        animations.add(animation)
        
        return animation.id
    }
    
    /**
     * Stops an animation by ID
     * 
     * @param id ID of the animation to stop
     * @return True if the animation was found and stopped, false otherwise
     */
    fun stopAnimation(id: Int): Boolean {
        val animation = animations.find { it.id == id } ?: return false
        animations.remove(animation)
        return true
    }
    
    /**
     * Pauses an animation by ID
     * 
     * @param id ID of the animation to pause
     * @return True if the animation was found and paused, false otherwise
     */
    fun pauseAnimation(id: Int): Boolean {
        val animation = animations.find { it.id == id } ?: return false
        animation.pause()
        return true
    }
    
    /**
     * Resumes an animation by ID
     * 
     * @param id ID of the animation to resume
     * @return True if the animation was found and resumed, false otherwise
     */
    fun resumeAnimation(id: Int): Boolean {
        val animation = animations.find { it.id == id } ?: return false
        animation.resume()
        return true
    }
    
    /**
     * Registers a new animation type
     * 
     * @param type Type name for the animation
     * @param factory Factory function to create animations of this type
     */
    fun registerAnimationType(type: String, factory: (AnimationParams) -> Animation) {
        animationFactories[type] = factory
    }
    
    /**
     * Gets the number of active animations
     * 
     * @return Number of active animations
     */
    fun getActiveAnimationCount(): Int {
        return animations.size
    }
    
    /**
     * Stops all animations
     */
    fun stopAllAnimations() {
        animations.clear()
    }
    
    /**
     * Parameters for creating animations
     */
    data class AnimationParams(
        val target: Any? = null,
        val duration: Float = 1.0f,
        val delay: Float = 0.0f,
        val repeatCount: Int = 0,
        val repeatMode: RepeatMode = RepeatMode.RESTART,
        val interpolator: Interpolator = Interpolator.LINEAR,
        val fromValue: Any? = null,
        val toValue: Any? = null,
        val onStart: (() -> Unit)? = null,
        val onUpdate: ((Float) -> Unit)? = null,
        val onComplete: (() -> Unit)? = null,
        val children: List<Pair<String, AnimationParams>>? = null
    )
    
    /**
     * Repeat modes for animations
     */
    enum class RepeatMode {
        RESTART,
        REVERSE
    }
    
    /**
     * Interpolators for animations
     */
    enum class Interpolator {
        LINEAR,
        ACCELERATE,
        DECELERATE,
        ACCELERATE_DECELERATE,
        ANTICIPATE,
        OVERSHOOT,
        ANTICIPATE_OVERSHOOT,
        BOUNCE,
        SPRING;
        
        /**
         * Applies the interpolation to a value
         * 
         * @param input Input value between 0 and 1
         * @return Interpolated value between 0 and 1
         */
        fun getInterpolation(input: Float): Float {
            // Simplified implementation to avoid potential type issues
            when (this) {
                LINEAR -> return input
                ACCELERATE -> return input * input
                DECELERATE -> return 1f - (1f - input) * (1f - input)
                ACCELERATE_DECELERATE -> {
                    val value = input * 2f
                    return if (value < 1f) {
                        0.5f * value * value
                    } else {
                        val value2 = value - 1f
                        -0.5f * (value2 * (value2 - 2f) - 1f)
                    }
                }
                ANTICIPATE -> return input * input * ((2f + 1) * input - 2f)
                OVERSHOOT -> {
                    val value = input - 1f
                    return value * value * ((2f + 1) * value + 2f) + 1f
                }
                ANTICIPATE_OVERSHOOT -> {
                    val value = input * 2f
                    return if (value < 1f) {
                        0.5f * value * value * ((5f + 1) * value - 5f)
                    } else {
                        val value2 = value - 2f
                        0.5f * (value2 * value2 * ((5f + 1) * value2 + 5f) + 2f)
                    }
                }
                BOUNCE -> {
                    val bounce = 7.5625f
                    val value = 1f - input
                    return if (value < 1f / 2.75f) {
                        1f - (bounce * value * value)
                    } else if (value < 2f / 2.75f) {
                        val value2 = value - 1.5f / 2.75f
                        1f - (bounce * value2 * value2 + 0.75f)
                    } else if (value < 2.5f / 2.75f) {
                        val value2 = value - 2.25f / 2.75f
                        1f - (bounce * value2 * value2 + 0.9375f)
                    } else {
                        val value2 = value - 2.625f / 2.75f
                        1f - (bounce * value2 * value2 + 0.984375f)
                    }
                }
                SPRING -> {
                    val value = input * 10f
                    // Explicitly cast Double results to Float
                    return 1f - (kotlin.math.cos(value * kotlin.math.PI * 4) * kotlin.math.exp(-value)).toFloat()
                }
            }
        }
    }
    
    /**
     * Base class for all animations
     */
    abstract class Animation(protected val params: AnimationParams) {
        var id: Int = -1
        protected var currentTime: Float = 0f
        protected var isPaused: Boolean = false
        protected var isStarted: Boolean = false
        protected var currentRepeatCount: Int = 0
        protected var isReversing: Boolean = false
        
        /**
         * Updates the animation
         * 
         * @param deltaTime Time elapsed since last frame in seconds
         */
        open fun update(deltaTime: Float) {
            if (isPaused) return
            
            // Handle delay
            if (currentTime < params.delay) {
                currentTime += deltaTime
                return
            }
            
            // Start animation if not started
            if (!isStarted) {
                isStarted = true
                params.onStart?.invoke()
            }
            
            // Update time
            currentTime += deltaTime
            
            // Calculate progress
            val elapsedTime = currentTime - params.delay
            val duration = params.duration
            var progress = (elapsedTime / duration).coerceIn(0f, 1f)
            
            // Apply reversing if needed
            if (isReversing) {
                progress = 1f - progress
            }
            
            // Apply interpolation
            val interpolatedProgress: Float = params.interpolator.getInterpolation(progress)
            
            // Update animation
            updateAnimation(interpolatedProgress)
            
            // Safely invoke the callback with explicit type
            if (params.onUpdate != null) {
                val callback = params.onUpdate
                val progressValue: Float = interpolatedProgress
                callback.invoke(progressValue)
            }
            
            // Check if animation is complete
            if (elapsedTime >= duration) {
                if (params.repeatCount == 0 || currentRepeatCount >= params.repeatCount) {
                    // Animation is complete
                    params.onComplete?.invoke()
                } else {
                    // Repeat animation
                    currentRepeatCount++
                    currentTime = params.delay
                    
                    // Handle repeat mode
                    if (params.repeatMode == RepeatMode.REVERSE) {
                        isReversing = !isReversing
                    }
                }
            }
        }
        
        /**
         * Updates the animation with the current progress
         * 
         * @param progress Current progress between 0 and 1
         */
        protected abstract fun updateAnimation(progress: Float)
        
        /**
         * Draws the animation on the canvas
         * 
         * @param canvas Canvas to draw on
         * @param paint Paint to use for drawing
         */
        open fun draw(canvas: Canvas, paint: Paint) {
            // Default implementation does nothing
        }
        
        /**
         * Checks if the animation is completed
         * 
         * @return True if the animation is completed, false otherwise
         */
        fun isCompleted(): Boolean {
            if (isPaused) return false
            
            val elapsedTime = currentTime - params.delay
            return isStarted && elapsedTime >= params.duration && 
                   (params.repeatCount == 0 || currentRepeatCount >= params.repeatCount)
        }
        
        /**
         * Pauses the animation
         */
        fun pause() {
            isPaused = true
        }
        
        /**
         * Resumes the animation
         */
        fun resume() {
            isPaused = false
        }
    }
    
    /**
     * Fade animation that changes alpha over time
     */
    class FadeAnimation(params: AnimationParams) : Animation(params) {
        private val fromAlpha = (params.fromValue as? Float) ?: 0f
        private val toAlpha = (params.toValue as? Float) ?: 1f
        private var currentAlpha = fromAlpha
        
        override fun updateAnimation(progress: Float) {
            currentAlpha = fromAlpha + (toAlpha - fromAlpha) * progress
        }
    }
    
    /**
     * Scale animation that changes size over time
     */
    class ScaleAnimation(params: AnimationParams) : Animation(params) {
        private val fromScaleX = (params.fromValue as? Pair<*, *>)?.first as? Float ?: 1f
        private val fromScaleY = (params.fromValue as? Pair<*, *>)?.second as? Float ?: 1f
        private val toScaleX = (params.toValue as? Pair<*, *>)?.first as? Float ?: 1f
        private val toScaleY = (params.toValue as? Pair<*, *>)?.second as? Float ?: 1f
        private var currentScaleX = fromScaleX
        private var currentScaleY = fromScaleY
        
        override fun updateAnimation(progress: Float) {
            currentScaleX = fromScaleX + (toScaleX - fromScaleX) * progress
            currentScaleY = fromScaleY + (toScaleY - fromScaleY) * progress
        }
    }
    
    /**
     * Rotate animation that changes rotation over time
     */
    class RotateAnimation(params: AnimationParams) : Animation(params) {
        private val fromRotation = (params.fromValue as? Float) ?: 0f
        private val toRotation = (params.toValue as? Float) ?: 360f
        private var currentRotation = fromRotation
        
        override fun updateAnimation(progress: Float) {
            currentRotation = fromRotation + (toRotation - fromRotation) * progress
        }
    }
    
    /**
     * Translate animation that changes position over time
     */
    class TranslateAnimation(params: AnimationParams) : Animation(params) {
        private val fromX = (params.fromValue as? Pair<*, *>)?.first as? Float ?: 0f
        private val fromY = (params.fromValue as? Pair<*, *>)?.second as? Float ?: 0f
        private val toX = (params.toValue as? Pair<*, *>)?.first as? Float ?: 0f
        private val toY = (params.toValue as? Pair<*, *>)?.second as? Float ?: 0f
        private var currentX = fromX
        private var currentY = fromY
        
        override fun updateAnimation(progress: Float) {
            currentX = fromX + (toX - fromX) * progress
            currentY = fromY + (toY - fromY) * progress
        }
    }
    
    /**
     * Color animation that changes color over time
     */
    class ColorAnimation(params: AnimationParams) : Animation(params) {
        private val fromColor = (params.fromValue as? Int) ?: 0
        private val toColor = (params.toValue as? Int) ?: 0
        private var currentColor = fromColor
        
        override fun updateAnimation(progress: Float) {
            // Interpolate each color component
            val fromA = (fromColor shr 24) and 0xFF
            val fromR = (fromColor shr 16) and 0xFF
            val fromG = (fromColor shr 8) and 0xFF
            val fromB = fromColor and 0xFF
            
            val toA = (toColor shr 24) and 0xFF
            val toR = (toColor shr 16) and 0xFF
            val toG = (toColor shr 8) and 0xFF
            val toB = toColor and 0xFF
            
            val a = fromA + ((toA - fromA) * progress).toInt()
            val r = fromR + ((toR - fromR) * progress).toInt()
            val g = fromG + ((toG - fromG) * progress).toInt()
            val b = fromB + ((toB - fromB) * progress).toInt()
            
            currentColor = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    
    /**
     * Sequence animation that plays multiple animations in sequence
     */
    inner class SequenceAnimation(params: AnimationParams) : Animation(params) {
        private val childAnimations = mutableListOf<Animation>()
        private var currentAnimationIndex = 0
        
        init {
            // Create child animations
            params.children?.forEach { (type, childParams) ->
                val factory = animationFactories[type] ?: return@forEach
                childAnimations.add(factory(childParams))
            }
        }
        
        override fun update(deltaTime: Float) {
            if (isPaused) return
            
            // Handle delay
            if (currentTime < params.delay) {
                currentTime += deltaTime
                return
            }
            
            // Start animation if not started
            if (!isStarted) {
                isStarted = true
                params.onStart?.invoke()
            }
            
            // Update current animation
            if (currentAnimationIndex < childAnimations.size) {
                val currentAnimation = childAnimations[currentAnimationIndex]
                currentAnimation.update(deltaTime)
                
                // Move to next animation if current one is completed
                if (currentAnimation.isCompleted()) {
                    currentAnimationIndex++
                    
                    // Check if all animations are completed
                    if (currentAnimationIndex >= childAnimations.size) {
                        if (params.repeatCount == 0 || currentRepeatCount >= params.repeatCount) {
                            // Sequence is complete
                            params.onComplete?.invoke()
                        } else {
                            // Repeat sequence
                            currentRepeatCount++
                            currentAnimationIndex = 0
                            childAnimations.forEach { it.id = -1 }
                        }
                    }
                }
            }
            
            // Update overall progress for onUpdate callback
            val progress = if (childAnimations.isEmpty()) {
                1f
            } else {
                min(1f, currentAnimationIndex.toFloat() / childAnimations.size)
            }
            
            params.onUpdate?.invoke(progress)
        }
        
        override fun updateAnimation(progress: Float) {
            // Not used for sequence animation
        }
        
        override fun draw(canvas: Canvas, paint: Paint) {
            // Draw current animation
            if (currentAnimationIndex < childAnimations.size) {
                childAnimations[currentAnimationIndex].draw(canvas, paint)
            }
        }
    }
    
    /**
     * Parallel animation that plays multiple animations simultaneously
     */
    inner class ParallelAnimation(params: AnimationParams) : Animation(params) {
        private val childAnimations = mutableListOf<Animation>()
        
        init {
            // Create child animations
            params.children?.forEach { (type, childParams) ->
                val factory = animationFactories[type] ?: return@forEach
                childAnimations.add(factory(childParams))
            }
        }
        
        override fun update(deltaTime: Float) {
            if (isPaused) return
            
            // Handle delay
            if (currentTime < params.delay) {
                currentTime += deltaTime
                return
            }
            
            // Start animation if not started
            if (!isStarted) {
                isStarted = true
                params.onStart?.invoke()
            }
            
            // Update all child animations
            childAnimations.forEach { it.update(deltaTime) }
            
            // Check if all animations are completed
            val allCompleted = childAnimations.all { it.isCompleted() }
            if (allCompleted) {
                if (params.repeatCount == 0 || currentRepeatCount >= params.repeatCount) {
                    // Parallel animation is complete
                    params.onComplete?.invoke()
                } else {
                    // Repeat parallel animation
                    currentRepeatCount++
                    childAnimations.forEach { it.id = -1 }
                }
            }
            
            // Update overall progress for onUpdate callback
            val progress: Float = if (childAnimations.isEmpty()) {
                1.0f
            } else {
                // Calculate the ratio of completed animations
                var completedCount = 0
                for (animation in childAnimations) {
                    if (animation.isCompleted()) {
                        completedCount += 1
                    }
                }
                min(1.0f, completedCount.toFloat() / childAnimations.size.toFloat())
            }
            
            // Safely invoke the callback with explicit type
            if (params.onUpdate != null) {
                val callback = params.onUpdate
                val progressValue: Float = progress
                callback.invoke(progressValue)
            }
        }
        
        override fun updateAnimation(progress: Float) {
            // Not used for parallel animation
        }
        
        override fun draw(canvas: Canvas, paint: Paint) {
            // Draw all child animations
            childAnimations.forEach { it.draw(canvas, paint) }
        }
    }
}
