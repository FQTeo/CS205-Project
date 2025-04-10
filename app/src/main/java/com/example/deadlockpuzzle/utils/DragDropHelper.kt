package com.example.deadlockpuzzle.utils

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs

/**
 * Helper class to manage drag and drop operations
 * Note: This is a separate utility class from GameView's built-in drag-drop,
 * which could be used for more complex drag-drop scenarios or reused elsewhere
 */
class DragDropHelper(
    private val view: View,
    private val onDragStarted: (Float, Float) -> Unit = { _, _ -> },
    private val onDragMoved: (Float, Float) -> Unit = { _, _ -> },
    private val onDragEnded: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> }
) {
    // Tracking touch state
    private var isDragging = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f

    // For tracking velocity
    private var velocityTracker: VelocityTracker? = null

    // Animation
    private var snapBackAnimator: ValueAnimator? = null

    /**
     * Call this from your View's onTouchEvent method
     */
    fun handleTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start tracking velocity
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                // Record starting position
                startX = x
                startY = y
                lastX = x
                lastY = y

                // Not dragging yet, just recording touch position
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Update velocity tracker
                velocityTracker?.addMovement(event)

                // Check if we should start dragging (moved beyond threshold)
                if (!isDragging) {
                    val dx = x - startX
                    val dy = y - startY

                    // Start dragging if moved beyond threshold
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) {
                        isDragging = true
                        onDragStarted(x, y)
                    }
                }

                // If dragging, notify of movement
                if (isDragging) {
                    onDragMoved(x, y)
                    lastX = x
                    lastY = y
                }

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Calculate final velocity
                velocityTracker?.apply {
                    addMovement(event)
                    computeCurrentVelocity(1000)  // in pixels per second
                    val xVelocity = xVelocity
                    val yVelocity = yVelocity

                    if (isDragging) {
                        onDragEnded(x, y, xVelocity, yVelocity)
                    }

                    recycle()
                }

                velocityTracker = null
                isDragging = false
                return true
            }
        }

        return false
    }

    /**
     * Provides a smooth snap-back animation for objects that should
     * return to a specific position after being dragged
     */
    fun snapBackToPosition(
        currentX: Float,
        currentY: Float,
        targetX: Float,
        targetY: Float,
        duration: Long = 300,
        onUpdate: (Float, Float) -> Unit,
        onComplete: () -> Unit = {}
    ) {
        // Cancel any existing animation
        snapBackAnimator?.cancel()

        // Create new animator
        snapBackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val newX = currentX + (targetX - currentX) * fraction
                val newY = currentY + (targetY - currentY) * fraction
                onUpdate(newX, newY)
            }

            doOnEnd { onComplete() }
            start()
        }
    }

    /**
     * Extension function for ValueAnimator to add an end listener
     */
    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
        })
    }

    companion object {
        // Minimum distance to consider as a drag
        private const val TOUCH_SLOP = 8f
    }
}