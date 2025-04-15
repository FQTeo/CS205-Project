# Deadlock Puzzle Game

A mobile puzzle game that demonstrates concurrent programming and multithreading concepts through a deadlock visualization.

## Project Overview

This Android game is designed to fulfill all required features and additional features for the CS205 project:

### Required Features Implementation

1. **2D Graphics Drawn by Code**
   - Custom graphics rendered from code using the `GameView` class.
   - All game elements including monsters, resources, and animations are drawn programmatically.
   - Thread synchronization during drawing ensures visual consistency even when game state changes from background threads.

```kotlin
// app/src/main/java/com/example/deadlockpuzzle/views/GameView.kt
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    // Clear the canvas
    canvas.drawColor(backgroundColor)
    
    // Draw monsters with thread synchronization
    synchronized(monsters) {
        monsters.forEach { monster ->
            drawMonster(canvas, monster)
        }
    }
    
    // Draw particle effects
    particleSystem.draw(canvas)
}
```

2. **Real-time Elements**
   - The game implements three types of dynamic elements:
     - **Frame-based**: Smooth animations updated each frame based on delta time.
     - **Interval-based**: Fixed-interval tasks with the `IntervalManager` class.
     - **Asynchronous**: Background tasks processing with worker threads.
   - **Gameplay Impact**: The timer continues to count down smoothly while players drag monsters, creating time pressure without UI freezes or stuttering that would disrupt the player experience.

```kotlin
// IntervalManager setup in MainActivity.kt
private fun setupIntervalTasks() {
    // Timer updates at fixed intervals, independent of UI interactions
    intervalManager.addTask("timerUpdater", 1000, true) {
        decrementRemainingTime()
    }
    
    // Add a task to perform background processing
    intervalManager.addTask("backgroundProcessor", 2000, false) {
        // This runs on a background thread
        gameStateManager.withReadLock { gameState ->
            if (gameState.isRunning && !gameState.isCompleted) {
                // Process game state
            }
        }
    }
}
```

3. **Interactive Elements**
   - The game responds to user interactions including touch events for dragging monsters.
   - All user interactions are processed in separate threads to keep the UI responsive.
   - **Gameplay Impact**: Players can drag multiple monsters quickly without experiencing lag, making the puzzle-solving experience fluid and intuitive.

```kotlin
// Inside GameView.kt
override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            // Handle touch down event
            val x = event.x
            val y = event.y
            
            // Process touch in separate thread to avoid blocking the UI
            interactionThreadPool.processInteraction {
                handleTouchDown(x, y)
            }
            return true
        }
        // Other touch events...
    }
    return super.onTouchEvent(event)
}
```

4. **Worker Threads**
   - Multiple worker threads are created using the `GameThreadPool` and `AsyncTaskManager`.
   - These threads handle background tasks without blocking the UI.
   - **Gameplay Impact**: Complex deadlock detection algorithms run in the background, allowing the game to analyze sophisticated resource dependency graphs without freezing the UI. This enables more challenging puzzles without sacrificing responsiveness.

```kotlin
// GameThreadPool.kt
class GameThreadPool(
    private val corePoolSize: Int = 2,
    private val maxPoolSize: Int = 4,
    private val keepAliveTimeSeconds: Long = 60
) {
    private val executor = ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        keepAliveTimeSeconds,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        threadFactory
    )
    
    fun execute(task: Runnable) {
        executor.execute(task)
    }
    
    fun <T> submit(task: Callable<T>): Future<T> {
        return executor.submit(task)
    }
}

// Deadlock detection using worker threads
private fun runGame() {
    asyncTaskManager.executeAsync(
        task = {
            // Complex deadlock detection algorithm on background thread
            viewModel.runGame()
        },
        onComplete = { isDeadlockFree ->
            // Update UI with results without lag
            if (isDeadlockFree) {
                gameView.showSuccessAnimation()
            } else {
                gameView.showFailureAnimation()
            }
        }
    )
}
```

5. **Thread Synchronization**
   - Thread safety ensured with various synchronization mechanisms:
     - `SharedStateManager` for safe state access
     - Atomic variables to prevent race conditions
     - Locks and synchronized blocks
   - **Gameplay Impact**: Prevents visual glitches and gameplay inconsistencies by ensuring that game state is always coherent, even when modified by multiple threads. This creates a stable, predictable experience during complex operations.

```kotlin
// SharedStateManager.kt
class SharedStateManager<T>(initialState: T) {
    private val readWriteLock = ReentrantReadWriteLock()
    private val readLock = readWriteLock.readLock()
    private val writeLock = readWriteLock.writeLock()
    
    @Volatile
    private var state: T = initialState
    
    fun read(): T {
        readLock.lock()
        try {
            return state
        } finally {
            readLock.unlock()
        }
    }
    
    fun write(newState: T) {
        writeLock.lock()
        try {
            state = newState
        } finally {
            writeLock.unlock()
        }
    }
}

// In GameView - ensuring thread-safe monster updates
synchronized(monsters) {
    val monster = monsters.find { it.id == updatedMonster.id }
    monster?.apply {
        x = updatedMonster.x
        y = updatedMonster.y
        // Other updates...
    }
}
```

### Additional Features Implementation

6. **Standard GUI Components**
   - The game uses standard Android UI elements for various screens and interactions.
   - Includes buttons, layouts, modals, and text views.
   - **Gameplay Impact**: Creates an intuitive, familiar interface that allows players to focus on the puzzle challenges rather than learning a custom UI system.

```kotlin
// MainActivity.kt
private fun initializeViews() {
    // Get view references
    difficultyLayout = findViewById(R.id.difficulty_layout)
    gameContainer = findViewById(R.id.game_container)
    timerText = findViewById(R.id.timer_text)
    runButton = findViewById(R.id.run_button)
    returnHomeButton = findViewById(R.id.return_home_button)
    anotherRoundButton = findViewById(R.id.another_round_button)
    timeoutModal = findViewById(R.id.timeout_modal)
    // Setup more UI components...
}
```

7. **Thread Pool for Human Interactions**
   - The `InteractionThreadPool` specifically handles user interactions without blocking the UI.
   - This ensures the UI remains responsive even during complex processing.
   - **Gameplay Impact**: Critical for maintaining responsiveness during intense gameplay moments. When players quickly rearrange multiple monsters, each interaction is processed in a dedicated thread, preventing input lag that would otherwise make the game frustrating to play.

```kotlin
// InteractionThreadPool.kt
class InteractionThreadPool(private val numThreads: Int = 3) {
    private val executor = Executors.newFixedThreadPool(numThreads) { runnable ->
        Thread(runnable).apply {
            name = "InteractionThread-${threadCounter.getAndIncrement()}"
            priority = Thread.NORM_PRIORITY
        }
    }
    
    fun processInteraction(task: () -> Unit) {
        activeTaskCount.incrementAndGet()
        
        executor.execute {
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing interaction", e)
            } finally {
                activeTaskCount.decrementAndGet()
            }
        }
    }
}

// Button clicks processed without blocking UI
runButton.setOnClickListener {
    // Disable button immediately to prevent double-clicks
    runButton.isEnabled = false
    
    interactionThreadPool.processInteraction {
        runGame()
    }
}
```

8. **Producer-Consumer Pattern**
   - The `GameTaskProcessor` and `TaskQueue` implement the producer-consumer pattern.
   - Tasks are produced and added to a queue, then consumed by worker threads.
   - **Gameplay Impact**: Enables efficient processing of visual effects and game events. When deadlocks occur or puzzles are solved, the game can trigger multiple particle effects and sound effects without any performance drop, enhancing the sensory feedback for players.

```kotlin
// TaskQueue.kt
class TaskQueue<T>(private val numConsumers: Int = 2) {
    private val queue = LinkedBlockingQueue<T>()
    
    fun start(processTask: (T) -> Unit, onMainThread: Boolean = false) {
        for (i in 0 until numConsumers) {
            val consumer = Thread {
                while (running.get()) {
                    try {
                        val task = queue.take() // Blocks until a task is available
                        
                        if (onMainThread) {
                            mainHandler.post { processTask(task) }
                        } else {
                            processTask(task)
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            consumer.name = "TaskConsumer-$i"
            consumer.start()
        }
    }
    
    // Producer method
    fun produce(task: T) {
        if (running.get()) {
            queue.put(task) // Will block if queue is full
        }
    }
}

// Using GameTaskProcessor to create rich visual effects
gameTaskProcessor.createParticles(
    x = gameView.width / 2f,
    y = gameView.height / 3f,
    count = 50,
    color = if (success) android.graphics.Color.GREEN else android.graphics.Color.RED
)
```

9. **Advanced Multi-threading Abstractions**
   - The `BackgroundMessageHandler` uses Android's Handler and Looper system.
   - Provides message queuing and processing in background threads.
   - **Gameplay Impact**: Allows for sophisticated background processing of game state and AI calculations. This enables features like automatic detection of deadlock situations and animated visualization of resource dependency circles, creating an educational tool that's also fun to play.

```kotlin
// BackgroundMessageHandler.kt
class BackgroundMessageHandler {
    // Background thread for processing
    private val handlerThread: HandlerThread
    
    // Handler for processing messages on the background thread
    private val backgroundHandler: Handler
    
    // Handler for posting results to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    fun queueGameLogicProcessing(gameLogic: GameLogic) {
        val message = backgroundHandler.obtainMessage(MSG_PROCESS_GAME_LOGIC)
        message.obj = gameLogic
        backgroundHandler.sendMessage(message)
    }
}

// When time runs out, process game over in background to keep UI responsive
if (timeRemaining <= 0) {
    lifecycleScope.launch {
        timerManager.stopTimer()
        
        // Use background message handler to process game over
        backgroundMessageHandler.queueGameLogicProcessing(viewModel.gameLogic)
        
        // Show failure animation
        gameView.showFailureAnimation()
        
        // Vibrate device
        vibrateDevice()
    }
}
```

12. **Mobile Features Integration**
   - The game integrates device vibration for feedback when deadlocks occur.
   - Uses the system vibrator service for haptic feedback.
   - **Gameplay Impact**: Provides tactile feedback when deadlocks occur, reinforcing the learning experience through multiple sensory channels. This makes the consequences of deadlocks more impactful and memorable.

```kotlin
// MainActivity.kt
private fun vibrateDevice() {
    // Vibrate to indicate failure
    val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
    vibrator.vibrate(vibrationEffect)
}
```

13. **State Preservation in SQLite**
   - Game state is preserved in SQLite database through the `GameDatabase` class.
   - Players can pause the game and continue later from the same state.
   - **Gameplay Impact**: Database operations occur in background threads, allowing players to pause/save their game instantly without waiting for disk operations to complete. This creates a seamless experience when transitioning between play sessions.

```kotlin
// GameDatabase.kt
@Synchronized
fun saveGameState(
    difficulty: GameDifficulty,
    monsters: List<Monster>,
    remainingTime: Int
): Long {
    checkInitialization()
    
    try {
        // Convert monsters to JSON
        val monstersJson = gson.toJson(monsters)
        
        val values = ContentValues().apply {
            put(COLUMN_DIFFICULTY, difficulty.name)
            put(COLUMN_MONSTERS_DATA, monstersJson)
            put(COLUMN_REMAINING_TIME, remainingTime)
            put(COLUMN_TIMESTAMP, Date().time.toString())
        }
        
        return dbWritable.insert(TABLE_SAVED_GAMES, null, values)
    } catch (e: Exception) {
        Log.e(TAG, "Error saving game state", e)
        return -1
    }
}

// Save operation runs in background IO thread
private fun pauseGame() {
    // Stop the timer
    timerManager.stopTimer()
    
    // Save the current game state in the background
    lifecycleScope.launch(Dispatchers.IO) {
        val difficulty = viewModel.gameLogic.getDifficulty()
        val monsters = viewModel.monsters.value ?: emptyList()
        val remainingTime = timerManager.getCurrentTime()
        
        // Background database operation
        val result = gameDatabase.saveGameState(difficulty, monsters, remainingTime)
        
        withContext(Dispatchers.Main) {
            if (result > 0) {
                gameExplicitlyPaused = true
                showDifficultySelection()
            }
        }
    }
}
```

17. **Advanced 2D Graphics Features**
   - The game implements advanced graphics features through the `ParticleSystem` class.
   - Includes particle effects, transparency, and complex animations.
   - **Gameplay Impact**: Particle effects run on separate threads, allowing for rich visual feedback (celebration effects, error indicators) without impacting the game's core performance. This creates a more polished, responsive feel.

```kotlin
// ParticleSystem implementation
fun createParticles(x: Float, y: Float, count: Int, color: Int) {
    synchronized(particles) {
        repeat(count) {
            val particle = Particle(
                x = x + (Math.random() * 20 - 10).toFloat(),
                y = y + (Math.random() * 20 - 10).toFloat(),
                velocityX = (Math.random() * 100 - 50).toFloat(),
                velocityY = (Math.random() * -150).toFloat(),
                color = color,
                size = (Math.random() * 10 + 5).toFloat(),
                lifetime = (Math.random() * 2 + 1).toFloat()
            )
            particles.add(particle)
        }
    }
}

// Particle system updates in separate thread
class ParticleSystem(width: Float, height: Float) {
    private val updateThread = Thread {
        var lastUpdateTime = System.nanoTime()
        
        while (!Thread.currentThread().isInterrupted) {
            val currentTime = System.nanoTime()
            val deltaTime = (currentTime - lastUpdateTime) / 1_000_000_000f
            lastUpdateTime = currentTime
            
            update(deltaTime)
            
            try {
                Thread.sleep(16) // ~60 FPS
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }
}
```

20. **Simulation with Natural Forces**
   - The `PhysicsSystem` implements simulation with natural forces like gravity.
   - Particles and game elements respond to physical forces.
   - **Gameplay Impact**: Physics calculations run on dedicated threads, ensuring smooth animation of particle effects and monster movements even during complex game operations. This creates a more immersive, believable world.

```kotlin
// PhysicsSystem.kt
class PhysicsSystem {
    // Physics constants
    private var gravity = 9.8f * 50f // Scaled gravity
    
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
    
    private fun updateSprings(deltaTime: Float) {
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
            
            // Calculate spring force
            val springForce = spring.stiffness * (distance - spring.restLength)
            
            // Apply forces to objects based on spring physics
            // ...
        }
    }
}

// Physics system runs on a separate thread
private val physicsThread = Thread {
    var lastUpdateTime = System.nanoTime()
    
    while (!Thread.currentThread().isInterrupted) {
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastUpdateTime) / 1_000_000_000f
        lastUpdateTime = currentTime
        
        // Update physics
        updatePhysics(deltaTime)
        
        try {
            Thread.sleep(16) // ~60 FPS
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            break
        }
    }
}
```

## How to Play

### Game Overview
Deadlock Puzzle is a puzzle game that demonstrates the concept of deadlocks in computing systems. Your goal is to arrange monsters on a grid in a way that prevents deadlocks from occurring when they request and hold resources.

### Gameplay Mechanics
- **Monsters**: Colorful creatures that need resources to complete their tasks. Each monster can hold one resource while requesting another.
- **Resources**: Objects that monsters need to acquire. A monster must hold its current resource until it gets the one it's requesting.
- **Deadlocks**: A deadlock occurs when monsters form a circular waiting pattern where each monster holds a resource needed by another monster, creating a standstill.
- **Grid**: The game board where you initially position the monsters before running the simulation.

### Controls
1. **Initial Setup**: Position monsters on the grid by dragging them to strategic locations
2. **Run Button**: Start the simulation to see if your arrangement prevents deadlocks
3. **Another Round**: Reset the level with the same difficulty if you fail
4. **Return Home**: Go back to difficulty selection screen

### Difficulty Levels
- **Easy**: 3 monsters - simpler resource dependencies and more time
- **Medium**: 5 monsters - more complex resource interaction patterns and less time
- **Hard**: 8 monsters - challenging resource dependencies creating multiple potential deadlock scenarios with strict time limits

### Deadlock Visualization
When a deadlock occurs:
1. The game highlights the monsters involved in the circular wait
2. Lines are drawn between monsters showing their resource dependencies
3. The simulation stops, demonstrating how a deadlock freezes a system
4. Vibration feedback and sound effects indicate the deadlock condition

### Tips for Success
- Analyze which monster is requesting which resource before positioning them
- Avoid arrangements where monsters with dependent resources are close to each other
- Consider the order of resource acquisition to prevent circular waits
- Break potential circular dependencies by strategic monster placement

### Educational Value
This game teaches fundamental concepts in operating systems and concurrent programming:
- Resource allocation and the problems it can cause
- Deadlock conditions and their detection
- The four necessary conditions for deadlock (mutual exclusion, hold and wait, no preemption, circular wait)
- Strategies for deadlock prevention and avoidance

## Running the Project

1. Clone this repository
2. Open in Android Studio
3. Build and run on an Android device or emulator

## Credits

Developed as part of CS205 course requirements.