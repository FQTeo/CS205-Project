package com.example.deadlockpuzzle.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.deadlockpuzzle.logic.GameLogic
import com.example.deadlockpuzzle.models.Monster
import com.example.deadlockpuzzle.models.MonsterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Custom SurfaceView that handles the game rendering and animation
 */
class GameView(context: Context, private val gameLogic: GameLogic) : SurfaceView(context), SurfaceHolder.Callback {

    // Drawing tools
    private val paint = Paint()
    private val backgroundPaint = Paint()
    private val textPaint = Paint()

    // Game rendering thread
    private var renderThread: RenderThread? = null
    private var surfaceCreated = false

    // For monster rendering
    private var monsterBitmaps = mutableMapOf<Int, Bitmap>()
    private var resourceBitmaps = mutableMapOf<Int, Bitmap>()

    // Game state
    private var monsters = listOf<Monster>()
    private var selectedMonster: Monster? = null
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    // Game dimensions
    private var monsterSize = 0f
    private var monsterSpacing = 0f
    private var startY = 0f

    // Animation states
    private var successAnimation = false
    private var failureAnimation = false
    private var animationProgress = 0f

    // Game UI callbacks
    var onGameSuccessListener: (() -> Unit)? = null
    var onGameFailureListener: (() -> Unit)? = null

    // Coroutine scope for background tasks
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var animationJob: Job? = null

    init {
        holder.addCallback(this)

        // Set up paint objects
        backgroundPaint.color = Color.rgb(240, 240, 255)

        textPaint.color = Color.BLACK
        textPaint.textSize = 40f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER

        paint.isAntiAlias = true

        // Load resources
        loadResources()
    }

    /**
     * Loads monster and resource graphics
     */
    private fun loadResources() {
        // In a real app, you'd load actual graphics
        // For this example, we'll create colored squares as placeholder graphics

        // Create placeholder monster bitmaps (would be replaced with actual graphics)
        val monsterColors = listOf(
            Color.rgb(255, 100, 100),  // Red
            Color.rgb(100, 255, 100),  // Green
            Color.rgb(100, 100, 255),  // Blue
            Color.rgb(255, 255, 100),  // Yellow
            Color.rgb(255, 100, 255),  // Purple
            Color.rgb(100, 255, 255),  // Cyan
            Color.rgb(255, 150, 100),  // Orange
            Color.rgb(150, 100, 255)   // Violet
        )

        val tempBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)

        monsterColors.forEachIndexed { index, color ->
            val monsterBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(monsterBitmap)

            // Draw monster body
            paint.color = color
            canvas.drawRect(20f, 40f, 180f, 200f, paint)

            // Draw eyes
            paint.color = Color.WHITE
            canvas.drawCircle(70f, 100f, 30f, paint)
            canvas.drawCircle(130f, 100f, 30f, paint)

            paint.color = Color.BLACK
            canvas.drawCircle(70f, 100f, 15f, paint)
            canvas.drawCircle(130f, 100f, 15f, paint)

            monsterBitmaps[index] = monsterBitmap
        }

        // Create placeholder resource bitmaps
        val resourceColors = listOf(
            Color.rgb(200, 50, 50),    // Wand - Red
            Color.rgb(50, 200, 50),    // Potion - Green
            Color.rgb(50, 50, 200),    // Crystal - Blue
            Color.rgb(200, 200, 50),   // Book - Yellow
            Color.rgb(200, 50, 200),   // Sword - Purple
            Color.rgb(50, 200, 200),   // Shield - Cyan
            Color.rgb(200, 100, 50),   // Key - Orange
            Color.rgb(100, 50, 200)    // Gem - Violet
        )

        MonsterFactory.resources.forEachIndexed { index, resourceId ->
            if (index < resourceColors.size) {
                val resourceBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resourceBitmap)

                paint.color = resourceColors[index]

                // Draw different shapes based on resource type
                when (resourceId) {
                    MonsterFactory.RESOURCE_WAND -> {
                        // Wand
                        canvas.drawRect(40f, 10f, 60f, 90f, paint)
                        paint.color = Color.YELLOW
                        canvas.drawCircle(50f, 20f, 15f, paint)
                    }
                    MonsterFactory.RESOURCE_POTION -> {
                        // Potion
                        paint.color = resourceColors[index]
                        canvas.drawRect(30f, 40f, 70f, 90f, paint)
                        canvas.drawRect(40f, 20f, 60f, 40f, paint)
                    }
                    MonsterFactory.RESOURCE_CRYSTAL -> {
                        // Crystal
                        val points = floatArrayOf(
                            50f, 10f,
                            80f, 40f,
                            65f, 90f,
                            35f, 90f,
                            20f, 40f
                        )
                        for (i in 0 until 5) {
                            canvas.drawLine(
                                points[i * 2], points[i * 2 + 1],
                                points[(i * 2 + 2) % 10], points[(i * 2 + 3) % 10],
                                paint
                            )
                        }
                    }
                    else -> {
                        // Default shape for other resources
                        canvas.drawCircle(50f, 50f, 40f, paint)
                    }
                }

                resourceBitmaps[resourceId] = resourceBitmap
            }
        }
    }

    /**
     * Updates the game state with new monsters
     */
    fun updateGameState(monsters: List<Monster>) {
        this.monsters = monsters

        // Assign bitmaps to monsters
        monsters.forEach { monster ->
            monster.bitmap = monsterBitmaps[monster.id % monsterBitmaps.size]
        }

        // Calculate positions
        calculateMonsterPositions()
    }

    /**
     * Calculates the positions of all monsters based on screen size
     */
    private fun calculateMonsterPositions() {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        // Calculate monster size based on screen width and count
        val difficulty = gameLogic.getDifficulty()
        val count = difficulty.monsterCount

        monsterSize = min(screenWidth / (count + 1), screenHeight / 5)
        monsterSpacing = (screenWidth - (monsterSize * count)) / (count + 1)

        // Position monsters in a row at the bottom of the screen
        startY = screenHeight * 0.6f

        monsters.forEach { monster ->
            val position = monster.position
            val x = monsterSpacing + position * (monsterSize + monsterSpacing)
            val y = startY

            monster.targetXPos = x
            monster.targetYPos = y

            // Set initial position if not already set
            if (monster.xPos == 0f && monster.yPos == 0f) {
                monster.xPos = x
                monster.yPos = y
            }

            monster.setBounds(0f, 0f, monsterSize, monsterSize)
        }
    }

    /**
     * Displays a success animation
     */
    fun showSuccessAnimation() {
        animationJob?.cancel()
        successAnimation = true
        failureAnimation = false
        animationProgress = 0f

        animationJob = coroutineScope.launch {
            // Animation logic will be handled in onDraw
            invalidate()
            onGameSuccessListener?.invoke()
        }
    }

    /**
     * Displays a failure animation
     */
    fun showFailureAnimation() {
        animationJob?.cancel()
        successAnimation = false
        failureAnimation = true
        animationProgress = 0f

        animationJob = coroutineScope.launch {
            // Animation logic will be handled in onDraw
            invalidate()
            onGameFailureListener?.invoke()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceCreated = true
        renderThread = RenderThread().apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        calculateMonsterPositions()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceCreated = false
        renderThread?.stopRendering()
        renderThread?.join()
        renderThread = null
    }

    /**
     * Handles touch events for drag and drop
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameLogic.isGameCompleted()) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Find which monster was touched
                selectedMonster = monsters.find { monster ->
                    monster.containsPoint(x, y)
                }

                // Record touch offset for smoother dragging
                selectedMonster?.let { monster ->
                    touchOffsetX = x - monster.xPos
                    touchOffsetY = y - monster.yPos
                    monster.isDragging = true
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Update position while dragging
                selectedMonster?.let { monster ->
                    monster.xPos = x - touchOffsetX
                    monster.yPos = y - touchOffsetY
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                // Handle drop
                selectedMonster?.let { monster ->
                    monster.isDragging = false

                    // Determine new position based on drop location
                    val newPosition = calculateDropPosition(monster, x)

                    // Update in game logic
                    gameLogic.updateMonsterPosition(monster.id, newPosition)

                    // Update our local copy
                    updateGameState(gameLogic.getMonsters())

                    selectedMonster = null
                    return true
                }
            }
        }

        return false
    }

    /**
     * Calculates the new position for a monster being dropped
     */
    private fun calculateDropPosition(monster: Monster, dropX: Float): Int {
        val count = monsters.size

        // Calculate which slot the monster was dropped in
        val slotWidth = monsterSize + monsterSpacing
        val adjustedX = (dropX - monsterSpacing / 2) / slotWidth

        return adjustedX.toInt().coerceIn(0, count - 1)
    }

    /**
     * Draws the game state on the canvas
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        // Clear background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw title
        textPaint.textSize = 60f
        textPaint.color = Color.rgb(50, 50, 100)
        canvas.drawText("Deadlock Puzzle", width / 2f, 80f, textPaint)

        // Draw instructions
        textPaint.textSize = 30f
        textPaint.color = Color.rgb(50, 50, 50)
        canvas.drawText("Arrange monsters to avoid deadlock", width / 2f, 140f, textPaint)

        // Draw difficulty
        val difficulty = gameLogic.getDifficulty().name
        canvas.drawText("Difficulty: $difficulty", width / 2f, 180f, textPaint)

        // Draw result if game is completed
        if (gameLogic.isGameCompleted()) {
            if (successAnimation) {
                // Success message
                textPaint.textSize = 60f
                textPaint.color = Color.rgb(0, 150, 0)
                canvas.drawText("PUZZLE SOLVED!", width / 2f, height / 3f, textPaint)
            } else if (failureAnimation) {
                // Failure message
                textPaint.textSize = 60f
                textPaint.color = Color.rgb(200, 0, 0)
                canvas.drawText("DEADLOCK DETECTED!", width / 2f, height / 3f, textPaint)
            }
        }

        // Draw non-selected monsters first
        monsters.filter { it != selectedMonster }.forEach { monster ->
            monster.update()
            monster.draw(canvas, paint, resourceBitmaps)
        }

        // Draw selected monster last (on top)
        selectedMonster?.let { monster ->
            monster.update()
            monster.draw(canvas, paint, resourceBitmaps)
        }
    }

    /**
     * Thread that handles the game rendering
     */
    private inner class RenderThread : Thread() {
        private var running = true

        fun stopRendering() {
            running = false
        }

        override fun run() {
            while (running && surfaceCreated) {
                val canvas = holder.lockCanvas() ?: continue

                try {
                    draw(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }

                // Frame rate control
                try {
                    sleep(16) // ~60fps
                } catch (e: InterruptedException) {
                    // Handle interruption
                }
            }
        }
    }
}