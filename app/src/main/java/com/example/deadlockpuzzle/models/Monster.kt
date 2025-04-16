package com.example.deadlockpuzzle.models

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Represents a monster in the deadlock puzzle game.
 * Each monster holds one resource and needs another to complete its task.
 */
data class Monster(
    val id: Int,
    val name: String,
    val heldResourceId: Int,  // The resource this monster currently holds
    val neededResourceId: Int,  // The resource this monster needs to complete its task
    var position: Int,  // Position in the sequence
    var bitmap: Bitmap? = null,  // Monster appearance
    var isTaskCompleted: Boolean = false
) {

    val bounds = RectF()

    // Animation properties
    var xPos: Float = 0f
    var yPos: Float = 0f
    var targetXPos: Float = 0f
    var targetYPos: Float = 0f
    var scale: Float = 1.0f
    var alpha: Int = 255

    // For drag and drop
    var isDragging: Boolean = false

    /**
     * Draws the monster along with its held and needed resources
     */
    fun draw(canvas: Canvas, paint: Paint, resourceBitmaps: Map<Int, Bitmap>) {
        // Save canvas state
        canvas.save()

        // Apply transformations for animations
        canvas.translate(xPos, yPos)
        canvas.scale(scale, scale, bounds.width() / 2, bounds.height() / 2)

        // Set alpha for animations
        paint.alpha = alpha

        // Draw monster bitmap
        bitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, paint)

            // Draw "held" resource above the monster (smaller size)
            val heldResource = resourceBitmaps[heldResourceId]
            heldResource?.let { resource ->
                val resourceSize = bounds.width() * 0.4f
                canvas.drawBitmap(
                    resource,
                    null,
                    RectF(
                        bounds.width() * 0.1f,
                        -resourceSize * 0.8f,
                        bounds.width() * 0.1f + resourceSize,
                        -resourceSize * 0.8f + resourceSize
                    ),
                    paint
                )
            }

            // Draw "needed" resource as thought bubble
            val neededResource = resourceBitmaps[neededResourceId]
            neededResource?.let { resource ->
                val resourceSize = bounds.width() * 0.4f

                // Draw thought bubble
                paint.alpha = 200
                canvas.drawCircle(
                    bounds.width() * 0.8f,
                    -resourceSize * 0.5f,
                    resourceSize * 0.6f,
                    paint
                )
                paint.alpha = alpha

                // Draw needed resource inside thought bubble
                canvas.drawBitmap(
                    resource,
                    null,
                    RectF(
                        bounds.width() * 0.8f - resourceSize * 0.4f,
                        -resourceSize * 0.5f - resourceSize * 0.4f,
                        bounds.width() * 0.8f + resourceSize * 0.4f,
                        -resourceSize * 0.5f + resourceSize * 0.4f
                    ),
                    paint
                )
            }
        }

        canvas.restore()
    }

    /**
     * Updates monster's animation properties
     */
    fun update() {
        xPos += (targetXPos - xPos) * 0.2f
        yPos += (targetYPos - yPos) * 0.2f

        if (isTaskCompleted) {
            scale = 1.2f
        }
    }

    /**
     * Sets the bounds for this monster based on its position
     */
    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        bounds.set(left, top, right, bottom)
    }

    /**
     * Checks if a point is within this monster's bounds
     */
    fun containsPoint(x: Float, y: Float): Boolean {
        return bounds.contains(x - xPos, y - yPos)
    }
}

/**
 * Factory for creating monsters with different characteristics
 */
object MonsterFactory {
    // Resource type constants
    const val RESOURCE_WAND = 1
    const val RESOURCE_POTION = 2
    const val RESOURCE_CRYSTAL = 3
    const val RESOURCE_BOOK = 4
    const val RESOURCE_SWORD = 5
    const val RESOURCE_SHIELD = 6
    const val RESOURCE_KEY = 7
    const val RESOURCE_GEM = 8
    const val RESOURCE_SCROLL = 9


    // List of available resources
    val resources = listOf(
        RESOURCE_WAND, RESOURCE_POTION, RESOURCE_CRYSTAL, RESOURCE_BOOK,
        RESOURCE_SWORD, RESOURCE_SHIELD, RESOURCE_KEY, RESOURCE_GEM, RESOURCE_SCROLL
    )

    private val monsterNames = listOf(
        "Blinky", "Pinky", "Inky", "Clyde", "Spooky",
        "Fuzzy", "Grumpy", "Slimy", "Toothy", "Buggy"
    )

    /**
     * Creates a list of monsters based on difficulty level
     */
    fun createMonstersForDifficulty(difficulty: GameDifficulty): List<Monster> {
        val monsterCount = when (difficulty) {
            GameDifficulty.EASY -> 3
            GameDifficulty.MEDIUM -> 5
            GameDifficulty.HARD -> 8
        }

        return createMonsters(monsterCount)
    }

    /**
     * Creates a specified number of monsters with resource dependencies
     * that guarantee at least one possible deadlock-free arrangement
     */
    private fun createMonsters(count: Int): List<Monster> {
        if (count <= 0) {
            throw IllegalArgumentException("Monster count must be positive, received: $count")
        }

        // Make sure we have enough resources for the requested monster count
        if (count > resources.size - 1) { // Reserve at least one resource for breaking deadlocks
            throw IllegalArgumentException("Not enough resource types for $count monsters with solution guarantee. Maximum is ${resources.size - 1}")
        }

        val monsters = mutableListOf<Monster>()

        // Create a list of resources we'll use for the monsters initially
        val usedResources = resources.take(count).toMutableList()
        usedResources.shuffle()

        // Create monsters with meaningful resource dependencies
        for (i in 0 until count) {
            val heldResource = usedResources[i]
            // Create dependency pattern based on count
            val neededIndex = when {
                count <= 3 -> (i + 1) % count
                count <= 5 -> (i + 2) % count
                else -> (i + 3) % count
            }
            val neededResource = usedResources[neededIndex]

            monsters.add(
                Monster(
                    id = i,
                    name = monsterNames[i % monsterNames.size],
                    heldResourceId = heldResource,
                    neededResourceId = neededResource,
                    position = i
                )
            )
        }

        // For all difficulties, always ensure at least one valid solution
        if (count >= 2) {
            // Break one dependency in the chain
            val breakIndex = (0 until count).random()

            // Always use a resource outside the chain to guarantee a solution
            val freeResource = if (count == GameDifficulty.HARD.monsterCount) {
                RESOURCE_SCROLL
            } else {
                resources[count]
            }

            monsters[breakIndex] = monsters[breakIndex].copy(
                neededResourceId = freeResource
            )
        }

        // Shuffle positions to create the puzzle
        monsters.shuffle()
        monsters.forEachIndexed { index, monster ->
            monster.position = index
        }

        return monsters
    }
}

/**
 * Enum defining the difficulty levels of the game
 */
enum class GameDifficulty(val monsterCount: Int, val timeSeconds: Int) {
    EASY(3, 30),
    MEDIUM(5, 45),
    HARD(8, 60)
}