package com.example.deadlockpuzzle.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.deadlockpuzzle.R
import com.example.deadlockpuzzle.data.GameDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying high scores in a RecyclerView.
 */
class HighScoreAdapter : ListAdapter<GameDatabase.HighScore, HighScoreAdapter.HighScoreViewHolder>(HighScoreDiffCallback()) {

    /**
     * ViewHolder for high score items.
     */
    class HighScoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rankText: TextView = itemView.findViewById(R.id.rank_text)
        private val playerNameText: TextView = itemView.findViewById(R.id.player_name_text)
        private val timeText: TextView = itemView.findViewById(R.id.time_text)
        
        /**
         * Binds a high score to this view holder.
         */
        fun bind(highScore: GameDatabase.HighScore, position: Int) {
            // Set rank (position + 1 since positions are 0-indexed)
            rankText.text = "#${position + 1}"
            
            // Format play date instead of showing player name
            val date = Date(highScore.date.toLong())
            val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
            val formattedDate = formatter.format(date)
            playerNameText.text = formattedDate
            
            // Format time as "Xs" (X seconds)
            timeText.text = "${highScore.time}s"
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HighScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_high_score, parent, false)
        return HighScoreViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: HighScoreViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}

/**
 * DiffUtil callback for efficient RecyclerView updates.
 */
class HighScoreDiffCallback : DiffUtil.ItemCallback<GameDatabase.HighScore>() {
    override fun areItemsTheSame(oldItem: GameDatabase.HighScore, newItem: GameDatabase.HighScore): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: GameDatabase.HighScore, newItem: GameDatabase.HighScore): Boolean {
        return oldItem == newItem
    }
} 