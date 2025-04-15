package com.example.deadlockpuzzle.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deadlockpuzzle.R
import com.example.deadlockpuzzle.adapters.HighScoreAdapter
import com.example.deadlockpuzzle.data.GameDatabase
import com.example.deadlockpuzzle.models.GameDifficulty
import com.google.android.material.tabs.TabLayout

/**
 * Activity for displaying high scores.
 */
class HighScoresActivity : AppCompatActivity() {
    // Views
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: LinearLayout
    private lateinit var backButton: Button
    
    // Database and adapter
    private lateinit var gameDatabase: GameDatabase
    private lateinit var adapter: HighScoreAdapter
    
    // Current difficulty to display
    private var currentDifficulty = GameDifficulty.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_high_scores)
        
        // Initialize database using singleton pattern
        gameDatabase = GameDatabase.getInstance(this)
        
        // Initialize views
        tabLayout = findViewById(R.id.difficulty_tabs)
        recyclerView = findViewById(R.id.high_scores_list)
        emptyStateView = findViewById(R.id.empty_state)
        backButton = findViewById(R.id.back_button)
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HighScoreAdapter()
        recyclerView.adapter = adapter
        
        // Set up tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // Update difficulty based on selected tab
                currentDifficulty = when (tab.position) {
                    0 -> GameDifficulty.EASY
                    1 -> GameDifficulty.MEDIUM
                    2 -> GameDifficulty.HARD
                    else -> GameDifficulty.EASY
                }
                
                // Reload high scores for the selected difficulty
                loadHighScores()
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {
                // Not needed
            }
            
            override fun onTabReselected(tab: TabLayout.Tab) {
                // Not needed
            }
        })
        
        // Set up back button
        backButton.setOnClickListener {
            finish() // Just finish the activity to go back
        }
        
        // Initially load high scores for EASY difficulty
        loadHighScores()
    }
    
    /**
     * Loads high scores for the current difficulty
     */
    private fun loadHighScores() {
        // Get high scores from database
        val highScores = gameDatabase.getHighScores(currentDifficulty)
        
        // Update adapter with new data
        adapter.submitList(highScores)
        
        // Show empty state if no scores
        if (highScores.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
        }
    }
} 