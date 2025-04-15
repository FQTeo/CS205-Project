package com.example.deadlockpuzzle.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.deadlockpuzzle.models.GameDifficulty
import com.example.deadlockpuzzle.models.Monster
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Database helper for the Deadlock Puzzle game.
 * Handles persistence of high scores and saved game state.
 * This implements requirement #13 (preserving state in SQLite).
 */
class GameDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, // Use application context to prevent memory leaks
    DATABASE_NAME, 
    null, 
    DATABASE_VERSION
) {
    companion object {
        private const val TAG = "GameDatabase"
        
        // Database info
        private const val DATABASE_NAME = "deadlock_puzzle.db"
        private const val DATABASE_VERSION = 1
        
        // Tables
        private const val TABLE_HIGH_SCORES = "high_scores"
        private const val TABLE_SAVED_GAMES = "saved_games"
        
        // High Scores columns
        private const val COLUMN_ID = "id"
        private const val COLUMN_DIFFICULTY = "difficulty"
        private const val COLUMN_TIME = "time"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_PLAYER_NAME = "player_name"
        
        // Saved Games columns
        private const val COLUMN_GAME_ID = "game_id"
        private const val COLUMN_MONSTERS_DATA = "monsters_data"
        private const val COLUMN_REMAINING_TIME = "remaining_time"
        private const val COLUMN_TIMESTAMP = "timestamp"
        
        // Singleton instance
        @Volatile
        private var INSTANCE: GameDatabase? = null
        
        /**
         * Get singleton instance of the database
         */
        fun getInstance(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameDatabase(context).also { 
                    INSTANCE = it
                    // Set isInitialized to true here, not just in onCreate
                    it.isInitialized.set(true)
                    Log.d(TAG, "Database instance created and initialized")
                }
            }
        }
    }
    
    // Track if database has been initialized
    private val isInitialized = AtomicBoolean(false)
    
    // Keep database instances for reuse
    private val dbWritable by lazy { 
        val db = writableDatabase
        // Ensure initialization flag is set when database is accessed
        isInitialized.set(true)
        db
    }
    private val dbReadable by lazy { 
        val db = readableDatabase
        // Ensure initialization flag is set when database is accessed
        isInitialized.set(true)
        db
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        try {
            // Create high scores table
            val createHighScoresTable = """
                CREATE TABLE $TABLE_HIGH_SCORES (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_DIFFICULTY TEXT NOT NULL,
                    $COLUMN_TIME INTEGER NOT NULL,
                    $COLUMN_DATE TEXT NOT NULL,
                    $COLUMN_PLAYER_NAME TEXT
                )
            """.trimIndent()
            
            // Create saved games table
            val createSavedGamesTable = """
                CREATE TABLE $TABLE_SAVED_GAMES (
                    $COLUMN_GAME_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_DIFFICULTY TEXT NOT NULL,
                    $COLUMN_MONSTERS_DATA TEXT NOT NULL,
                    $COLUMN_REMAINING_TIME INTEGER NOT NULL,
                    $COLUMN_TIMESTAMP TEXT NOT NULL
                )
            """.trimIndent()
            
            db.execSQL(createHighScoresTable)
            db.execSQL(createSavedGamesTable)
            
            Log.d(TAG, "Database tables created")
            isInitialized.set(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating database tables", e)
        }
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            // Drop and recreate tables on upgrade
            db.execSQL("DROP TABLE IF EXISTS $TABLE_HIGH_SCORES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SAVED_GAMES")
            onCreate(db)
        } catch (e: Exception) {
            Log.e(TAG, "Error upgrading database", e)
        }
    }
    
    // Verify database is ready to use
    private fun checkInitialization() {
        if (!isInitialized.get()) {
            Log.d(TAG, "Initializing database on first use")
            // Force access to the database to trigger initialization
            dbReadable
            isInitialized.set(true)
        }
    }
    
    /**
     * Saves a high score to the database
     */
    @Synchronized
    fun saveHighScore(difficulty: GameDifficulty, timeInSeconds: Int): Long {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        if (!isInitialized.get()) {
            Log.e(TAG, "Database not initialized")
            return -1
        }
        
        try {
            val values = ContentValues().apply {
                put(COLUMN_DIFFICULTY, difficulty.name)
                put(COLUMN_TIME, timeInSeconds)
                put(COLUMN_DATE, Date().time.toString())
                // Player name is no longer needed since this is single-player
                put(COLUMN_PLAYER_NAME, null as String?)
            }
            
            return dbWritable.insert(TABLE_HIGH_SCORES, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving high score", e)
            return -1
        }
    }
    
    /**
     * Gets the top high scores for a specific difficulty
     */
    @Synchronized
    fun getHighScores(difficulty: GameDifficulty, limit: Int = 10): List<HighScore> {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        if (!isInitialized.get()) {
            Log.e(TAG, "Database not initialized")
            return emptyList()
        }
        
        val highScores = mutableListOf<HighScore>()
        
        try {
            val query = """
                SELECT * FROM $TABLE_HIGH_SCORES 
                WHERE $COLUMN_DIFFICULTY = ? 
                ORDER BY $COLUMN_TIME ASC 
                LIMIT ?
            """.trimIndent()
            
            val cursor = dbReadable.rawQuery(query, arrayOf(difficulty.name, limit.toString()))
            
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    val time = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIME))
                    val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))
                    val playerName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PLAYER_NAME))
                    
                    highScores.add(HighScore(id, difficulty, time, dateStr, playerName))
                } while (cursor.moveToNext())
            }
            
            cursor.close()
            
            return highScores
        } catch (e: Exception) {
            Log.e(TAG, "Error getting high scores", e)
            return emptyList()
        }
    }
    
    /**
     * Checks if a time qualifies as a high score for a specific difficulty
     */
    @Synchronized
    fun isHighScore(difficulty: GameDifficulty, timeInSeconds: Int): Boolean {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        try {
            val scores = getHighScores(difficulty)
            
            // If we have fewer than 10 scores, it's automatically a high score
            if (scores.size < 10) return true
            
            // Otherwise, check if it's better than the worst high score
            return timeInSeconds < scores.last().time
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if high score", e)
            // Default to true to encourage user
            return true
        }
    }
    
    /**
     * Saves the current game state
     */
    @Synchronized
    fun saveGameState(
        difficulty: GameDifficulty,
        monsters: List<Monster>,
        remainingTime: Int
    ): Long {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        if (!isInitialized.get()) {
            Log.e(TAG, "Database not initialized")
            return -1
        }
        
        try {
            // First delete any existing saved game
            clearSavedGame()
            
            val gson = Gson()
            
            // Convert monsters to JSON
            val monstersJson = gson.toJson(monsters)
            
            val values = ContentValues().apply {
                put(COLUMN_DIFFICULTY, difficulty.name)
                put(COLUMN_MONSTERS_DATA, monstersJson)
                put(COLUMN_REMAINING_TIME, remainingTime)
                put(COLUMN_TIMESTAMP, Date().time.toString())
            }
            
            val id = dbWritable.insert(TABLE_SAVED_GAMES, null, values)
            
            Log.d(TAG, "Game state saved with ID: $id")
            return id
        } catch (e: Exception) {
            Log.e(TAG, "Error saving game state", e)
            return -1
        }
    }
    
    /**
     * Loads the saved game state
     */
    @Synchronized
    fun loadSavedGame(): SavedGame? {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        if (!isInitialized.get()) {
            Log.e(TAG, "Database not initialized")
            return null
        }
        
        try {
            val query = "SELECT * FROM $TABLE_SAVED_GAMES ORDER BY $COLUMN_TIMESTAMP DESC LIMIT 1"
            val cursor = dbReadable.rawQuery(query, null)
            
            var savedGame: SavedGame? = null
            
            if (cursor.moveToFirst()) {
                val gameId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GAME_ID))
                val difficultyStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DIFFICULTY))
                val monstersJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MONSTERS_DATA))
                val remainingTime = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REMAINING_TIME))
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                
                // Validate difficulty
                val difficulty = try {
                    GameDifficulty.valueOf(difficultyStr)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid difficulty in saved game: $difficultyStr", e)
                    cursor.close()
                    return null
                }
                
                // Validate monsters JSON data
                if (monstersJson.isNullOrBlank() || monstersJson == "[]") {
                    Log.e(TAG, "Invalid or empty monsters data in saved game")
                    cursor.close()
                    return null
                }
                
                // Validate remaining time (must be positive)
                if (remainingTime <= 0) {
                    Log.e(TAG, "Invalid remaining time in saved game: $remainingTime")
                    cursor.close()
                    return null
                }
                
                // Parse monsters from JSON
                val gson = Gson()
                val type = object : TypeToken<List<Monster>>() {}.type
                val monsters = try {
                    val parsed = gson.fromJson<List<Monster>>(monstersJson, type)
                    
                    // Validate monster count matches difficulty
                    val expectedCount = when (difficulty) {
                        GameDifficulty.EASY -> 3
                        GameDifficulty.MEDIUM -> 5
                        GameDifficulty.HARD -> 8
                    }
                    
                    if (parsed.size != expectedCount) {
                        Log.e(TAG, "Monster count mismatch: expected $expectedCount for $difficulty, got ${parsed.size}")
                        cursor.close()
                        return null
                    }
                    
                    parsed
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing monster data from JSON", e)
                    cursor.close()
                    return null
                }
                
                savedGame = SavedGame(gameId, difficulty, monsters, remainingTime, timestamp)
                Log.d(TAG, "Successfully loaded saved game: difficulty=${difficulty.name}, monsters=${monsters.size}, time=$remainingTime")
            } else {
                Log.d(TAG, "No saved game found in database")
            }
            
            cursor.close()
            
            return savedGame
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved game", e)
            return null
        }
    }
    
    /**
     * Checks if there is a saved game
     */
    @Synchronized
    fun hasSavedGame(): Boolean {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        if (!isInitialized.get()) {
            Log.e(TAG, "Database not initialized")
            return false
        }
        
        try {
            // More thorough check - verify a saved game exists and has valid monsters
            val query = "SELECT COUNT(*) FROM $TABLE_SAVED_GAMES WHERE $COLUMN_MONSTERS_DATA IS NOT NULL AND $COLUMN_MONSTERS_DATA != '[]'"
            val cursor = dbReadable.rawQuery(query, null)
            
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            
            cursor.close()
            
            // Log for debugging
            Log.d(TAG, "hasSavedGame check found $count valid saved games")
            
            return count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if saved game exists", e)
            return false
        }
    }
    
    /**
     * Clears the saved game
     */
    @Synchronized
    fun clearSavedGame() {
        // Check and initialize if needed before proceeding
        checkInitialization()
        
        if (!isInitialized.get()) {
            Log.e(TAG, "Database not initialized")
            return
        }
        
        try {
            dbWritable.delete(TABLE_SAVED_GAMES, null, null)
            Log.d(TAG, "Saved game cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing saved game", e)
        }
    }
    
    /**
     * Data class for high scores
     */
    data class HighScore(
        val id: Long,
        val difficulty: GameDifficulty,
        val time: Int, // Time in seconds
        val date: String,
        val playerName: String?
    )
    
    /**
     * Data class for saved games
     */
    data class SavedGame(
        val id: Long,
        val difficulty: GameDifficulty,
        val monsters: List<Monster>,
        val remainingTime: Int,
        val timestamp: String
    )
    
    /**
     * Close any database resources when the app is shutting down
     */
    override fun close() {
        Log.d(TAG, "Closing database connections")
        super.close()
    }
} 