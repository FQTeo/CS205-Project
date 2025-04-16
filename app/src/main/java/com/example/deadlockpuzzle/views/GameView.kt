package com.example.deadlockpuzzle.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.example.deadlockpuzzle.graphics.AnimationSystem
import com.example.deadlockpuzzle.graphics.ParticleSystem
import com.example.deadlockpuzzle.logic.GameLogic
import com.example.deadlockpuzzle.models.Monster
import com.example.deadlockpuzzle.models.MonsterFactory
import com.example.deadlockpuzzle.physics.PhysicsObject
import com.example.deadlockpuzzle.physics.PhysicsSystem
import com.example.deadlockpuzzle.physics.Spring
import com.example.deadlockpuzzle.threading.AsyncTaskManager
import com.example.deadlockpuzzle.threading.InteractionThreadPool
import com.example.deadlockpuzzle.threading.IntervalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/**
 * Game view with real-time elements, physics, and advanced graphics.
 *
 * This implements requirements:
 * - #2 (real-time elements)
 * - #17 (advanced 2D graphics)
 * - #20 (simulation with natural forces)
 */
class GameView(
    context: Context,
    private val gameLogic: GameLogic
) : SurfaceView(context), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "GameView"
    }
    
    // Drawing tools
    private val paint = Paint()
    private val backgroundPaint = Paint()
    private val textPaint = Paint()
    private val glowPaint = Paint()
    
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
    
    // Real-time animation system (frame-based updates)
    private val animationSystem = AnimationSystem()
    
    // Particle system for visual effects
    private val particleSystem = ParticleSystem()
    
    // Physics system for natural forces
    private val physicsSystem = PhysicsSystem()
    
    // Map of monster IDs to physics objects
    private val monsterPhysicsObjects = mutableMapOf<Int, PhysicsObject>()
    
    // Threading components
    private var asyncTaskManager: AsyncTaskManager? = null
    private var interactionThreadPool: InteractionThreadPool? = null
    private var intervalManager: IntervalManager? = null
    
    // Time tracking for delta time calculation
    private var lastFrameTime = System.nanoTime()
    
    // Background effects
    private var backgroundEffectTime = 0f
    private var backgroundShader: Shader? = null
    
    // New properties for deadlock visualization
    private var deadlockAnimationTime = 0f
    private var deadlockMonsters = mutableListOf<Monster>()
    private var showDeadlockHighlight = false
    private val deadlockConnections = mutableListOf<Pair<Monster, Monster>>()

    init {
        holder.addCallback(this)
        
        // Set up paint objects
        backgroundPaint.color = Color.rgb(240, 240, 255)
        backgroundPaint.style = Paint.Style.FILL
        
        textPaint.color = Color.BLACK
        textPaint.textSize = 40f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        
        paint.isAntiAlias = true
        
        glowPaint.style = Paint.Style.FILL
        glowPaint.isAntiAlias = true
        
        // Load resources
        loadResources()
    }
    
    /**
     * Sets the AsyncTaskManager for background processing
     */
    fun setAsyncTaskManager(manager: AsyncTaskManager) {
        asyncTaskManager = manager
    }
    
    /**
     * Sets the InteractionThreadPool for handling user interactions
     */
    fun setInteractionThreadPool(pool: InteractionThreadPool) {
        interactionThreadPool = pool
    }
    
    /**
     * Sets the IntervalManager for interval-based updates
     */
    fun setIntervalManager(manager: IntervalManager) {
        intervalManager = manager
        setupIntervalTasks()
    }
    
    /**
     * Sets up interval-based tasks
     */
    private fun setupIntervalTasks() {
        intervalManager?.let { manager ->
            if (!manager.hasTask("backgroundEffects")) {
                manager.addTask("backgroundEffects", 100, true) {
                    backgroundEffectTime += 0.1f
                    updateBackgroundShader()
                    invalidate()
                }
            }

            if (!manager.hasTask("ambientParticles")) {
                manager.addTask("ambientParticles", 500, false) {
                    if (width > 0 && height > 0) {
                        // Emit particles at random positions
                        val x = (Math.random() * width).toFloat()
                        val y = (Math.random() * height).toFloat()
                        particleSystem.emit(
                            x = x,
                            y = y,
                            count = 3,
                            color = Color.argb(100, 255, 255, 255),
                            minSpeed = 20f,
                            maxSpeed = 50f,
                            minLifetime = 30,
                            maxLifetime = 60
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Updates the background shader effect
     */
    private fun updateBackgroundShader() {
        if (width <= 0 || height <= 0) return

        val centerX = width / 2f + sin(backgroundEffectTime * 0.2f) * width * 0.1f
        val centerY = height / 2f + sin(backgroundEffectTime * 0.3f) * height * 0.1f
        
        backgroundShader = RadialGradient(
            centerX, centerY, width * 0.8f,
            intArrayOf(
                Color.rgb(250, 250, 255),
                Color.rgb(240, 245, 255),
                Color.rgb(230, 240, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        
        backgroundPaint.shader = backgroundShader
    }
    
    /**
     * Loads monster and resource graphics
     */
    private fun loadResources() {
        // Create placeholder monster bitmaps
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
            Color.rgb(100, 50, 200),   // Gem - Violet
            Color.rgb(150, 150, 200)   // Scroll - Light Purple/Blue
        )

        MonsterFactory.resources.forEachIndexed { index, resourceId ->
            if (index < resourceColors.size) {
                val resourceBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resourceBitmap)

                paint.color = resourceColors[index]

                // Draw different shapes based on resource type
                when (resourceId) {
                    MonsterFactory.RESOURCE_WAND -> {
                        // Wand - A vertical rectangle with a star tip
                        canvas.drawRect(40f, 30f, 60f, 90f, paint)

                        // Star tip
                        paint.color = Color.YELLOW
                        val path = Path()
                        path.moveTo(50f, 10f)
                        path.lineTo(60f, 25f)
                        path.lineTo(75f, 25f)
                        path.lineTo(65f, 35f)
                        path.lineTo(70f, 50f)
                        path.lineTo(50f, 40f)
                        path.lineTo(30f, 50f)
                        path.lineTo(35f, 35f)
                        path.lineTo(25f, 25f)
                        path.lineTo(40f, 25f)
                        path.close()
                        canvas.drawPath(path, paint)

                        paint.color = Color.rgb(220, 180, 50)
                        canvas.drawRect(40f, 45f, 60f, 50f, paint)
                        canvas.drawRect(40f, 70f, 60f, 75f, paint)
                    }

                    MonsterFactory.RESOURCE_POTION -> {
                        paint.color = Color.rgb(180, 230, 255)
                        canvas.drawRect(40f, 20f, 60f, 40f, paint)

                        val bottlePath = Path()
                        bottlePath.moveTo(30f, 40f)
                        bottlePath.lineTo(70f, 40f)
                        bottlePath.lineTo(65f, 90f)
                        bottlePath.lineTo(35f, 90f)
                        bottlePath.close()
                        canvas.drawPath(bottlePath, paint)

                        paint.color = resourceColors[index]
                        val liquidPath = Path()
                        liquidPath.moveTo(35f, 60f)
                        liquidPath.lineTo(65f, 60f)
                        liquidPath.lineTo(63f, 88f)
                        liquidPath.lineTo(37f, 88f)
                        liquidPath.close()
                        canvas.drawPath(liquidPath, paint)

                        paint.color = Color.WHITE
                        canvas.drawCircle(45f, 75f, 5f, paint)
                        canvas.drawCircle(55f, 65f, 3f, paint)
                        canvas.drawCircle(50f, 80f, 4f, paint)
                        paint.color = Color.rgb(150, 100, 50) // Brown cork
                        canvas.drawRect(42f, 15f, 58f, 22f, paint)
                    }

                    MonsterFactory.RESOURCE_CRYSTAL -> {
                        val center = 50f
                        paint.color = resourceColors[index]

                        val crystalPath = Path()
                        crystalPath.moveTo(center, 15f)
                        crystalPath.lineTo(center + 15f, 35f)
                        crystalPath.lineTo(center + 25f, 60f)
                        crystalPath.lineTo(center + 10f, 85f)
                        crystalPath.lineTo(center - 10f, 85f)
                        crystalPath.lineTo(center - 25f, 60f)
                        crystalPath.lineTo(center - 15f, 35f)
                        crystalPath.close()
                        canvas.drawPath(crystalPath, paint)

                        paint.color = Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f

                        canvas.drawLine(center, 15f, center, 85f, paint)
                        canvas.drawLine(center - 15f, 35f, center + 10f, 85f, paint)
                        canvas.drawLine(center + 15f, 35f, center - 10f, 85f, paint)

                        paint.style = Paint.Style.FILL

                        paint.color = Color.argb(100, 255, 255, 255)
                        val shinePath = Path()
                        shinePath.moveTo(center - 5f, 25f)
                        shinePath.lineTo(center + 8f, 40f)
                        shinePath.lineTo(center + 2f, 55f)
                        shinePath.lineTo(center - 10f, 35f)
                        shinePath.close()
                        canvas.drawPath(shinePath, paint)
                    }

                    MonsterFactory.RESOURCE_BOOK -> {
                        paint.color = resourceColors[index]
                        canvas.drawRect(25f, 30f, 75f, 80f, paint)

                        paint.color = darkenColor(resourceColors[index])
                        canvas.drawRect(23f, 30f, 29f, 80f, paint)

                        paint.color = Color.WHITE
                        canvas.drawRect(32f, 33f, 72f, 77f, paint)

                        paint.color = Color.GRAY
                        paint.strokeWidth = 1f
                        for (i in 0 until 7) {
                            val y = 40f + i * 5f
                            canvas.drawLine(35f, y, 69f, y, paint)
                        }

                        paint.color = Color.DKGRAY
                        canvas.drawRect(40f, 20f, 60f, 30f, paint)

                        paint.color = Color.RED
                        val bookmarkPath = Path()
                        bookmarkPath.moveTo(65f, 33f)
                        bookmarkPath.lineTo(70f, 33f)
                        bookmarkPath.lineTo(70f, 45f)
                        bookmarkPath.lineTo(67.5f, 42f)
                        bookmarkPath.lineTo(65f, 45f)
                        bookmarkPath.close()
                        canvas.drawPath(bookmarkPath, paint)
                    }

                    MonsterFactory.RESOURCE_SWORD -> {
                        paint.color = Color.rgb(200, 200, 220)
                        val bladePath = Path()
                        bladePath.moveTo(50f, 15f)
                        bladePath.lineTo(60f, 25f)
                        bladePath.lineTo(55f, 60f)
                        bladePath.lineTo(45f, 60f)
                        bladePath.lineTo(40f, 25f)
                        bladePath.close()
                        canvas.drawPath(bladePath, paint)

                        paint.color = Color.WHITE
                        paint.strokeWidth = 2f
                        canvas.drawLine(50f, 17f, 50f, 58f, paint)

                        paint.color = Color.rgb(180, 150, 50) // Gold guard
                        canvas.drawRect(35f, 60f, 65f, 65f, paint)

                        paint.color = resourceColors[index]
                        canvas.drawRect(45f, 65f, 55f, 85f, paint)

                        paint.color = Color.rgb(180, 150, 50) // Gold pommel
                        canvas.drawCircle(50f, 90f, 8f, paint)

                        paint.color = Color.BLACK
                        paint.strokeWidth = 1f
                        for (i in 0 until 4) {
                            val y = 68f + i * 5f
                            canvas.drawLine(45f, y, 55f, y, paint)
                        }
                    }

                    MonsterFactory.RESOURCE_SHIELD -> {
                        paint.color = resourceColors[index]
                        val shieldPath = Path()
                        shieldPath.moveTo(50f, 15f) // Top center
                        shieldPath.lineTo(75f, 25f) // Top right
                        shieldPath.lineTo(70f, 60f) // Middle right
                        shieldPath.lineTo(50f, 85f) // Bottom point
                        shieldPath.lineTo(30f, 60f) // Middle left
                        shieldPath.lineTo(25f, 25f) // Top left
                        shieldPath.close()
                        canvas.drawPath(shieldPath, paint)

                        paint.color = Color.rgb(150, 120, 20) // Gold border
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        canvas.drawPath(shieldPath, paint)
                        paint.style = Paint.Style.FILL

                        paint.color = Color.WHITE
                        val emblemPath = Path()
                        val centerX = 50f
                        val centerY = 45f
                        val outerRadius = 15f
                        val innerRadius = 7f

                        for (i in 0 until 10) {
                            val angle = Math.PI * 2 * i / 10
                            val radius = if (i % 2 == 0) outerRadius else innerRadius
                            val x = centerX + (radius * Math.cos(angle)).toFloat()
                            val y = centerY + (radius * Math.sin(angle)).toFloat()

                            if (i == 0) {
                                emblemPath.moveTo(x, y)
                            } else {
                                emblemPath.lineTo(x, y)
                            }
                        }
                        emblemPath.close()
                        canvas.drawPath(emblemPath, paint)

                        paint.color = Color.rgb(80, 80, 80)
                        canvas.drawCircle(35f, 30f, 3f, paint)
                        canvas.drawCircle(65f, 30f, 3f, paint)
                        canvas.drawCircle(30f, 50f, 3f, paint)
                        canvas.drawCircle(70f, 50f, 3f, paint)
                    }

                    MonsterFactory.RESOURCE_KEY -> {
                        paint.color = resourceColors[index]
                        canvas.drawCircle(50f, 35f, 20f, paint)

                        paint.color = Color.WHITE
                        canvas.drawCircle(50f, 35f, 10f, paint)

                        paint.color = resourceColors[index]
                        canvas.drawRect(45f, 55f, 55f, 90f, paint)

                        val teethPath = Path()
                        teethPath.moveTo(55f, 75f)
                        teethPath.lineTo(65f, 75f)
                        teethPath.lineTo(65f, 80f)
                        teethPath.lineTo(55f, 80f)
                        teethPath.moveTo(55f, 85f)
                        teethPath.lineTo(70f, 85f)
                        teethPath.lineTo(70f, 90f)
                        teethPath.lineTo(55f, 90f)
                        canvas.drawPath(teethPath, paint)

                        paint.color = Color.rgb(250, 220, 150)
                        for (i in 0 until 4) {
                            val angle = Math.PI / 2 * i + Math.PI / 4
                            val x = 50f + (12f * Math.cos(angle)).toFloat()
                            val y = 35f + (12f * Math.sin(angle)).toFloat()
                            canvas.drawCircle(x, y, 3f, paint)
                        }
                    }

                    MonsterFactory.RESOURCE_GEM -> {
                        paint.color = resourceColors[index]
                        val gemPath = Path()
                        val center = 50f
                        val gemSize = 30f

                        for (i in 0 until 8) {
                            val angle = Math.PI * i / 4 + Math.PI / 8
                            val x = center + (gemSize * Math.cos(angle)).toFloat()
                            val y = center + (gemSize * Math.sin(angle)).toFloat()

                            if (i == 0) {
                                gemPath.moveTo(x, y)
                            } else {
                                gemPath.lineTo(x, y)
                            }
                        }
                        gemPath.close()
                        canvas.drawPath(gemPath, paint)

                        paint.color = lightenColor(resourceColors[index])
                        val innerPath = Path()
                        val innerSize = gemSize * 0.6f

                        for (i in 0 until 8) {
                            val angle = Math.PI * i / 4 + Math.PI / 8
                            val x = center + (innerSize * Math.cos(angle)).toFloat()
                            val y = center + (innerSize * Math.sin(angle)).toFloat()

                            if (i == 0) {
                                innerPath.moveTo(x, y)
                            } else {
                                innerPath.lineTo(x, y)
                            }
                        }
                        innerPath.close()
                        canvas.drawPath(innerPath, paint)

                        paint.color = Color.WHITE
                        canvas.drawCircle(center, center, innerSize * 0.5f, paint)

                        paint.color = Color.WHITE

                        val sparkle = Path()
                        sparkle.moveTo(35f, 30f)
                        sparkle.lineTo(40f, 30f)
                        sparkle.lineTo(40f, 25f)
                        sparkle.lineTo(45f, 30f)
                        sparkle.lineTo(40f, 35f)
                        sparkle.lineTo(40f, 30f)
                        sparkle.close()
                        canvas.drawPath(sparkle, paint)

                        canvas.drawCircle(60f, 65f, 2f, paint)
                        canvas.drawCircle(65f, 40f, 3f, paint)
                    }

                    MonsterFactory.RESOURCE_SCROLL -> {
                        paint.color = Color.rgb(250, 240, 200)

                        val scrollPath = Path()
                        scrollPath.moveTo(30f, 25f)
                        scrollPath.quadTo(50f, 20f, 70f, 25f)
                        scrollPath.lineTo(70f, 75f)
                        scrollPath.quadTo(50f, 80f, 30f, 75f)
                        scrollPath.close()
                        canvas.drawPath(scrollPath, paint)

                        paint.color = Color.rgb(230, 220, 180)
                        val leftRollPath = Path()
                        leftRollPath.moveTo(30f, 25f)
                        leftRollPath.quadTo(25f, 50f, 30f, 75f)
                        leftRollPath.lineTo(35f, 75f)
                        leftRollPath.quadTo(30f, 50f, 35f, 25f)
                        leftRollPath.close()
                        canvas.drawPath(leftRollPath, paint)

                        val rightRollPath = Path()
                        rightRollPath.moveTo(70f, 25f)
                        rightRollPath.quadTo(75f, 50f, 70f, 75f)
                        rightRollPath.lineTo(65f, 75f)
                        rightRollPath.quadTo(70f, 50f, 65f, 25f)
                        rightRollPath.close()
                        canvas.drawPath(rightRollPath, paint)

                        paint.color = Color.rgb(80, 50, 20)
                        paint.strokeWidth = 1f
                        for (i in 0 until 6) {
                            val y = 30f + i * 8f
                            val path = Path()
                            path.moveTo(40f, y)
                            path.quadTo(50f, y + 2f, 60f, y)
                            canvas.drawPath(path, paint)
                        }

                        paint.color = Color.rgb(180, 50, 50)
                        canvas.drawCircle(45f, 65f, 8f, paint)

                        paint.color = Color.rgb(150, 30, 30)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        canvas.drawCircle(45f, 65f, 5f, paint)
                        canvas.drawLine(41f, 65f, 49f, 65f, paint)
                        canvas.drawLine(45f, 61f, 45f, 69f, paint)
                        paint.style = Paint.Style.FILL
                    }

                    else -> {
                        // Default circular shape for any other resources
                        paint.color = resourceColors[index % resourceColors.size]
                        canvas.drawCircle(50f, 50f, 40f, paint)
                    }
                }

                resourceBitmaps[resourceId] = resourceBitmap
            }
        }
    }

    // Helper functions to manipulate colors
    private fun darkenColor(color: Int): Int {
        val factor = 0.7f
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun lightenColor(color: Int): Int {
        val factor = 1.3f
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).coerceAtMost(255f).toInt()
        val g = (Color.green(color) * factor).coerceAtMost(255f).toInt()
        val b = (Color.blue(color) * factor).coerceAtMost(255f).toInt()
        return Color.argb(a, r, g, b)
    }
    
    /**
     * Resets the view entirely, clearing all state
     */
    fun resetView() {
        // Reset animation states
        successAnimation = false
        failureAnimation = false
        showDeadlockHighlight = false
        
        // Reset particles and physics
        particleSystem.clear()
        physicsSystem.clear()
        
        // Clear deadlock visualization
        resetDeadlockVisualization()
        
        // Force redraw
        invalidate()
    }
    
    /**
     * Cleans up and resets the deadlock visualization
     */
    private fun resetDeadlockVisualization() {
        showDeadlockHighlight = false
        deadlockMonsters.clear()
        deadlockConnections.clear()
        
        // Remove deadlock animation task if it exists
        intervalManager?.let { manager ->
            if (manager.hasTask("deadlockAnimation")) {
                manager.removeTask("deadlockAnimation")
            }
        }
    }
    
    /**
     * Updates the game state with new monsters
     */
    fun updateGameState(monsters: List<Monster>) {
        // Reset any deadlock visualization
        resetDeadlockVisualization()
        
        // Clear current state
        successAnimation = false
        failureAnimation = false
        showDeadlockHighlight = false
        selectedMonster = null
        
        // Store new monsters
        this.monsters = monsters
        
        // Assign bitmaps to monsters
        monsters.forEach { monster ->
            monster.bitmap = monsterBitmaps[monster.id % monsterBitmaps.size]
            // Reset any persisting animation state
            monster.isTaskCompleted = false
            monster.isDragging = false
            monster.scale = 1.0f
            monster.alpha = 255
        }
        
        // Calculate positions
        calculateMonsterPositions()
        
        // Update physics objects
        updatePhysicsObjects()
        
        // Force a redraw
        invalidate()
    }
    
    /**
     * Updates physics objects for monsters
     */
    private fun updatePhysicsObjects() {
        // Clear existing physics objects
        monsterPhysicsObjects.values.forEach { physicsSystem.removeObject(it) }
        monsterPhysicsObjects.clear()
        physicsSystem.clear()
        
        // Set physics world bounds
        physicsSystem.setWorldBounds(width.toFloat(), height.toFloat())
        
        // Create physics objects for monsters
        monsters.forEach { monster ->
            // Create physics object at the monster's position
            val physicsObject = PhysicsObject(
                x = monster.xPos + monster.bounds.width() / 2,
                y = monster.yPos + monster.bounds.height() / 2,
                radius = monster.bounds.width() / 2,
                mass = 1f,
                affectedByGravity = false
            )
            
            // Ensure zero initial velocity
            physicsObject.setVelocity(0f, 0f)
            
            // Add to physics system
            physicsSystem.addObject(physicsObject)
            monsterPhysicsObjects[monster.id] = physicsObject
            
            // Create spring to target position
            val targetX = monster.targetXPos + monster.bounds.width() / 2
            val targetY = monster.targetYPos + monster.bounds.height() / 2
            
            // Create an anchor object (immovable)
            val anchor = PhysicsObject(
                x = targetX,
                y = targetY,
                radius = 1f,
                mass = 0f, // Immovable
                affectedByGravity = false,
                collidable = false
            )
            physicsSystem.addObject(anchor)
            
            // Create spring between monster and anchor
            val spring = Spring(
                objectA = physicsObject,
                objectB = anchor,
                restLength = 0f,
                stiffness = 100f,
                damping = 10f
            )
            physicsSystem.addSpring(spring)
        }
    }

    /**
     * Calculates the positions of all monsters based on screen size
     * Arranges monsters in two rows when there are more than 5 monsters
     */
    private fun calculateMonsterPositions() {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        // Calculate monster size based on screen width and count
        val difficulty = gameLogic.getDifficulty()
        val totalCount = difficulty.monsterCount

        // Determine if we need two rows (when there are more than 5 monsters)
        val useDoubleRow = totalCount > 5

        // Calculate how many monsters per row
        val monstersPerRow = if (useDoubleRow) {
            ceil(totalCount / 2.0).toInt()
        } else {
            totalCount
        }

        // Calculate monster size and spacing
        monsterSize = min(screenWidth / (monstersPerRow + 1), screenHeight / 6)
        monsterSpacing = (screenWidth - (monsterSize * monstersPerRow)) / (monstersPerRow + 1)

        val firstRowY = if (useDoubleRow) {
            screenHeight * 0.5f
        } else {
            screenHeight * 0.6f
        }

        // Position of the second row (only used if double row)
        val secondRowY = screenHeight * 0.7f

        monsters.forEach { monster ->
            val position = monster.position

            if (useDoubleRow) {
                // Use two rows
                if (position < monstersPerRow) {
                    // First row
                    val x = monsterSpacing + position * (monsterSize + monsterSpacing)
                    val y = firstRowY

                    monster.targetXPos = x
                    monster.targetYPos = y
                    monster.xPos = x
                    monster.yPos = y
                } else {
                    // Second row
                    val adjustedPosition = position - monstersPerRow
                    val x = monsterSpacing + adjustedPosition * (monsterSize + monsterSpacing)
                    val y = secondRowY

                    monster.targetXPos = x
                    monster.targetYPos = y
                    monster.xPos = x
                    monster.yPos = y
                }
            } else {
                // Single row layout (original)
                val x = monsterSpacing + position * (monsterSize + monsterSpacing)
                val y = firstRowY

                monster.targetXPos = x
                monster.targetYPos = y
                monster.xPos = x
                monster.yPos = y
            }

            // Set proper bounds for the monster
            monster.setBounds(0f, 0f, monsterSize, monsterSize)
        }

        // Reset the physics system
        updatePhysicsObjects()
    }

    /**
     * Displays a success animation with enhanced effects
     */
    fun showSuccessAnimation() {
        animationJob?.cancel()
        successAnimation = true
        failureAnimation = false
        animationProgress = 0f
        
        // Add particle explosion
        asyncTaskManager?.executeAsync(
            task = {
                particleSystem.explode(
                    x = width / 2f,
                    y = height / 3f,
                    count = 100,
                    color = Color.rgb(100, 255, 100)
                )
            }
        )
        
        // Add physics explosion force
        physicsSystem.applyExplosion(
            x = width / 2f,
            y = height / 3f,
            radius = width * 0.8f,
            force = 500f
        )
        
        animationJob = coroutineScope.launch {
            // Animation logic will be handled in onDraw
            invalidate()
            onGameSuccessListener?.invoke()
        }
    }
    
    /**
     * Displays a failure animation with enhanced effects and highlights deadlock
     */
    fun showFailureAnimation() {
        animationJob?.cancel()
        successAnimation = false
        failureAnimation = true
        animationProgress = 0f
        
        // Identify deadlock cycles (simplified for demo - just show dependencies)
        deadlockMonsters.clear()
        deadlockConnections.clear()

        val sortedMonsters = monsters.sortedBy { it.position }
        
        // Create a dependency map to find cycles
        val waitingFor = mutableMapOf<Int, Int>() // Monster ID -> Monster ID it's waiting for
        val resourceHeldBy = mutableMapOf<Int, Int>() // Resource ID -> Monster ID holding it
        
        // Populate the maps
        sortedMonsters.forEach { monster ->
            resourceHeldBy[monster.heldResourceId] = monster.id
        }
        
        // Find deadlock relationships
        sortedMonsters.forEach { monster ->
            val neededResource = monster.neededResourceId
            
            // If another monster holds the resource this monster needs
            if (resourceHeldBy.containsKey(neededResource)) {
                val holderMonsterId = resourceHeldBy[neededResource]!!
                waitingFor[monster.id] = holderMonsterId
                
                // Add this monster to deadlock monsters list
                deadlockMonsters.add(monster)
                
                // Find the monster it's waiting for
                val holderMonster = monsters.find { it.id == holderMonsterId }
                if (holderMonster != null) {
                    // Add connection for visualization
                    deadlockConnections.add(Pair(monster, holderMonster))
                }
            }
        }

        showDeadlockHighlight = true
        
        // Add particle explosion
        asyncTaskManager?.executeAsync(
            task = {
                // Add angry particle effects above deadlock monsters
                deadlockMonsters.forEach { monster ->
                    particleSystem.explode(
                        x = monster.xPos + monster.bounds.width()/2,
                        y = monster.yPos,
                        count = 20,
                        color = Color.rgb(255, 100, 0)
                    )
                }
                
                // Also add main explosion
                particleSystem.explode(
                    x = width / 2f,
                    y = height / 3f,
                    count = 80,
                    color = Color.rgb(255, 100, 100)
                )
            }
        )
        
        // Add physics explosion force
        physicsSystem.applyExplosion(
            x = width / 2f,
            y = height / 3f,
            radius = width * 0.8f,
            force = 500f
        )
        
        animationJob = coroutineScope.launch {
            // Animation logic will be handled in onDraw
            invalidate()
            onGameFailureListener?.invoke()
        }
        
        // Start deadlock animation on interval
        intervalManager?.let { manager ->
            if (!manager.hasTask("deadlockAnimation")) {
                manager.addTask("deadlockAnimation", 50, true) {
                    deadlockAnimationTime += 0.1f
                    invalidate()
                }
            }
        }
    }

    /**
     * Handles touch events for drag and drop with thread pool
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameLogic.isGameCompleted()) return false

        val x = event.x
        val y = event.y

        // Process using the interaction thread pool if available
        interactionThreadPool?.let { pool ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Process touch down in thread pool with result
                    pool.processInteractionWithResult(
                        task = {
                            // Find which monster was touched
                            val monster = monsters.find { it.containsPoint(x, y) }

                            monster?.let {
                                touchOffsetX = x - it.xPos
                                touchOffsetY = y - it.yPos
                                it.isDragging = true
                                selectedMonster = it
                                true
                            } ?: false
                        },
                        onComplete = { result ->
                            result as Boolean
                        }
                    )

                    // If we found a monster to select, return true
                    return selectedMonster != null
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
                    // Handle drop in thread pool
                    selectedMonster?.let { monster ->
                        pool.processInteraction {
                            monster.isDragging = false

                            // Pass both x and y coordinates to determine row and column
                            val newPosition = calculateDropPosition(monster, x, y)

                            // Update in game logic
                            gameLogic.updateMonsterPosition(monster.id, newPosition)

                            // Update our local copy
                            val updatedMonsters = gameLogic.getMonsters()
                            post {
                                updateGameState(updatedMonsters)
                            }

                            selectedMonster = null
                        }
                        return true
                    }
                }
            }

            return false
        }

        // Fallback to direct handling if no thread pool
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectedMonster = monsters.find { monster ->
                    monster.containsPoint(x, y)
                }

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

                    val newPosition = calculateDropPosition(monster, x, y)
                    gameLogic.updateMonsterPosition(monster.id, newPosition)
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
     * Handles both single-row and double-row layouts with proper row switching
     */
    private fun calculateDropPosition(monster: Monster, dropX: Float, dropY: Float): Int {
        val count = monsters.size

        // Determine if we're using double row
        val useDoubleRow = count > 5
        val monstersPerRow = if (useDoubleRow) {
            ceil(count / 2.0).toInt()
        } else {
            count
        }

        // Calculate which slot the monster was dropped in horizontally
        val slotWidth = monsterSize + monsterSpacing
        val adjustedX = (dropX - monsterSpacing / 2) / slotWidth
        val slotPosition = adjustedX.toInt().coerceIn(0, monstersPerRow - 1)

        // If we're using double row, determine which row it was dropped in based on Y position
        if (useDoubleRow) {
            val screenHeight = height.toFloat()
            val firstRowY = screenHeight * 0.5f
            val secondRowY = screenHeight * 0.7f

            // Calculate middle point between rows to determine drop row
            val midPoint = (firstRowY + secondRowY) / 2

            // If dropped in second row, adjust the position
            if (dropY > midPoint) {
                return monstersPerRow + slotPosition
            }
        }

        return slotPosition
    }

    /**
     * Updates monsters with physics system positions
     */
    private fun updateMonstersFromPhysics() {
        // Update monster positions from physics objects
        monsters.forEach { monster ->
            monsterPhysicsObjects[monster.id]?.let { physicsObj ->
                // Only update if not being dragged
                if (!monster.isDragging) {
                    monster.xPos = physicsObj.x - monster.bounds.width() / 2
                    monster.yPos = physicsObj.y - monster.bounds.height() / 2
                } else {
                    // Update physics object to match monster position
                    physicsObj.x = monster.xPos + monster.bounds.width() / 2
                    physicsObj.y = monster.yPos + monster.bounds.height() / 2
                }
            }
        }
    }
    
    /**
     * Draws the game state on the canvas
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        // Update physics system
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
        lastFrameTime = currentTime
        
        physicsSystem.update(deltaTime)
        
        // Update monster positions from physics
        updateMonstersFromPhysics()
        
        // Update particles
        particleSystem.update(deltaTime)
        
        // Draw solid white background first to ensure maximum brightness
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw gradient background over solid white
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Draw particles (behind everything)
        particleSystem.draw(canvas)
        
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
        if (gameLogic.isGameCompleted() || successAnimation || failureAnimation) {
            if (successAnimation) {
                // Success message
                textPaint.textSize = 60f
                textPaint.color = Color.rgb(0, 150, 0)
                canvas.drawText("PUZZLE SOLVED!", width / 2f, height / 3f, textPaint)
            } else if (failureAnimation) {
                // Failure message with background for better visibility
                textPaint.textSize = 60f
                
                // Draw text background
                paint.color = Color.WHITE
                paint.alpha = 180
                val textBounds = RectF(
                    width * 0.1f,
                    height / 3f - 60f,
                    width * 0.9f,
                    height / 3f + 20f
                )
                canvas.drawRect(textBounds, paint)
                
                // Draw text
                textPaint.color = Color.rgb(200, 0, 0)
                canvas.drawText("DEADLOCK DETECTED!", width / 2f, height / 3f, textPaint)
            }
        }
        
        // Draw deadlock connections if needed
        if (showDeadlockHighlight) {
            paint.color = Color.rgb(255, 0, 0)
            paint.alpha = 180
            paint.strokeWidth = 5f
            
            // Add a pulsating effect to the deadlock connections
            val pulse = (Math.sin(deadlockAnimationTime.toDouble() * 5) * 0.5 + 0.5).toFloat()
            
            // Draw pulsating circles around deadlocked monsters
            deadlockMonsters.forEach { monster ->
                val centerX = monster.xPos + monster.bounds.width() / 2
                val centerY = monster.yPos + monster.bounds.height() / 2
                
                // Create gradient-filled pulsating circle with more dramatic size change
                val baseRadius = monster.bounds.width() * 0.65f
                val pulseAmount = 0.3f // Increased from 0.2f for more dramatic pulse
                val radius = baseRadius * (1.0f + pulse * pulseAmount)
                
                // Create a more intense pulsating gradient with ShaderFactory
                // Make radius larger for a better glow effect
                val glowRadius = radius * 1.3f
                
                // Create a custom gradient with more opacity for better visibility
                // Use pulse to also alter the color intensity for more dramatic effect
                val centerAlpha = (160 + pulse * 60).toInt().coerceIn(0, 255)
                val midAlpha = (120 + pulse * 50).toInt().coerceIn(0, 255)
                
                val circleGradient = RadialGradient(
                    centerX, centerY, glowRadius,
                    intArrayOf(
                        Color.argb(centerAlpha, 255, 50, 50),    // Pulsing red center
                        Color.argb(midAlpha, 255, 0, 0),         // Pulsing pure red
                        Color.argb(70, 200, 0, 0),               // Semi-transparent dark red
                        Color.argb(0, 150, 0, 0)                 // Transparent outer edge
                    ),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                
                // Apply the gradient
                glowPaint.shader = circleGradient
                glowPaint.style = Paint.Style.FILL
                
                // Draw a slightly larger circle to make the effect more visible
                canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)
                
                // Reset paint
                glowPaint.shader = null
                glowPaint.style = Paint.Style.FILL
                
                // Draw improved angry emoji above monster
                val emojiSize = monster.bounds.width() * 0.45f // Slightly larger
                val emojiY = monster.yPos - emojiSize - 15f - pulse * 6f
                val emojiX = centerX - emojiSize / 2
                
                // Make emoji pulse slightly with size
                val pulsedSize = emojiSize * (1f + pulse * 0.15f)
                
                // Create gradient for emoji
                val emojiGradient = RadialGradient(
                    emojiX + emojiSize/2, 
                    emojiY + emojiSize/2, 
                    pulsedSize/2,
                    intArrayOf(
                        Color.rgb(255, 220, 50),  // Bright yellow center
                        Color.rgb(255, 180, 0),   // Orange-yellow edge
                        Color.rgb(255, 150, 0)    // Darker orange outline
                    ),
                    floatArrayOf(0f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                
                // Draw emoji background with gradient
                glowPaint.shader = emojiGradient
                canvas.drawCircle(emojiX + emojiSize/2, emojiY + emojiSize/2, pulsedSize/2, glowPaint)
                glowPaint.shader = null
                
                // Draw emoji border
                glowPaint.style = Paint.Style.STROKE
                glowPaint.color = Color.rgb(200, 100, 0)
                glowPaint.strokeWidth = 2f
                canvas.drawCircle(emojiX + emojiSize/2, emojiY + emojiSize/2, pulsedSize/2, glowPaint)
                glowPaint.style = Paint.Style.FILL
                
                // Draw angry eyebrows with shadow effect
                paint.color = Color.BLACK
                paint.strokeWidth = 5f
                paint.strokeCap = Paint.Cap.ROUND
                
                // Left eyebrow - angled down toward center
                canvas.drawLine(
                    emojiX + emojiSize * 0.15f, emojiY + emojiSize * 0.25f,
                    emojiX + emojiSize * 0.45f, emojiY + emojiSize * 0.38f,
                    paint
                )
                
                // Right eyebrow - angled down toward center
                canvas.drawLine(
                    emojiX + emojiSize * 0.55f, emojiY + emojiSize * 0.38f,
                    emojiX + emojiSize * 0.85f, emojiY + emojiSize * 0.25f,
                    paint
                )
                
                // Draw angry eyes as small ovals
                paint.style = Paint.Style.FILL
                
                // Create eye shadow
                paint.color = Color.rgb(50, 20, 0)
                val eyeOffset = 2f
                
                // Left eye shadow
                canvas.drawOval(
                    emojiX + emojiSize * 0.23f + eyeOffset, 
                    emojiY + emojiSize * 0.45f + eyeOffset,
                    emojiX + emojiSize * 0.43f + eyeOffset,
                    emojiY + emojiSize * 0.55f + eyeOffset,
                    paint
                )
                
                // Right eye shadow
                canvas.drawOval(
                    emojiX + emojiSize * 0.57f + eyeOffset,
                    emojiY + emojiSize * 0.45f + eyeOffset,
                    emojiX + emojiSize * 0.77f + eyeOffset,
                    emojiY + emojiSize * 0.55f + eyeOffset,
                    paint
                )
                
                // Draw eyes
                paint.color = Color.BLACK
                
                // Left eye
                canvas.drawOval(
                    emojiX + emojiSize * 0.23f, 
                    emojiY + emojiSize * 0.45f,
                    emojiX + emojiSize * 0.43f,
                    emojiY + emojiSize * 0.55f,
                    paint
                )
                
                // Right eye
                canvas.drawOval(
                    emojiX + emojiSize * 0.57f,
                    emojiY + emojiSize * 0.45f,
                    emojiX + emojiSize * 0.77f,
                    emojiY + emojiSize * 0.55f,
                    paint
                )
                
                // Draw angry mouth with better curve
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.strokeCap = Paint.Cap.ROUND
                
                // Draw frowning mouth
                val mouthY = emojiY + emojiSize * 0.75f
                val mouthStartX = emojiX + emojiSize * 0.25f
                val mouthEndX = emojiX + emojiSize * 0.75f
                
                // Draw mouth with shadow
                paint.color = Color.rgb(50, 20, 0)
                canvas.drawArc(
                    RectF(
                        mouthStartX, 
                        mouthY - emojiSize * 0.1f, 
                        mouthEndX, 
                        mouthY + emojiSize * 0.3f
                    ), 
                    25f, 
                    130f, 
                    false, 
                    paint
                )
                
                // Draw actual mouth
                paint.color = Color.BLACK
                canvas.drawArc(
                    RectF(
                        mouthStartX - 2f, 
                        mouthY - emojiSize * 0.1f - 2f, 
                        mouthEndX - 2f, 
                        mouthY + emojiSize * 0.3f - 2f
                    ), 
                    25f, 
                    130f, 
                    false, 
                    paint
                )
                
                // Reset paint
                paint.style = Paint.Style.FILL
                paint.strokeCap = Paint.Cap.BUTT
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
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceCreated = true
        renderThread = RenderThread().apply { start() }
        
        // Initialize background shader
        updateBackgroundShader()
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        calculateMonsterPositions()
        
        // Update physics world bounds
        physicsSystem.setWorldBounds(width.toFloat(), height.toFloat())
        
        // Update background shader
        updateBackgroundShader()
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceCreated = false
        renderThread?.stopRendering()
        renderThread?.join()
        renderThread = null
        
        // Clean up resources
        intervalManager?.let { manager ->
            manager.removeTask("backgroundEffects")
            manager.removeTask("ambientParticles")
            manager.removeTask("deadlockAnimation")
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
            var lastFrameTime = System.nanoTime()
            
            while (running && surfaceCreated) {
                try {
                    val canvas = holder.lockCanvas() ?: continue
                    
                    // Calculate delta time for animations
                    val currentTime = System.nanoTime()
                    val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = currentTime
                    
                    try {
                        synchronized(holder) {
                            // Draw the game state
                            draw(canvas)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during rendering", e)
                    } finally {
                        // Always release the canvas
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unlocking canvas", e)
                        }
                    }
                    
                    // Frame rate control - aim for 60fps
                    val targetFrameTime = 16 // ~60fps in milliseconds
                    val elapsedTime = ((System.nanoTime() - currentTime) / 1_000_000).toInt()
                    val sleepTime = maxOf(0, targetFrameTime - elapsedTime)
                    
                    if (sleepTime > 0) {
                        sleep(sleepTime.toLong())
                    }
                } catch (e: InterruptedException) {
                    // Handle thread interruption
                    Log.w(TAG, "RenderThread interrupted", e)
                } catch (e: Exception) {
                    // Handle other exceptions
                    Log.e(TAG, "Error in render loop", e)
                }
            }
        }
    }
} 