package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_profile")
data class LocalProfile(
    @PrimaryKey val id: Int = 1,
    val uid: String = "",
    val username: String = "",
    val avatarId: Int = 3, // Custom icon index
    val country: String = "US",
    val rankName: String = "Silver II",
    val elo: Int = 1120,
    val level: Int = 1,
    val xp: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val gamesPlayed: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastRewardTimestamp: Long = 0L,
    val consecutiveLogins: Int = 0,
    val selectedTheme: String = "AUTO", // Day, Night, Auto
    val soundEnabled: Boolean = true,
    val volume: Float = 0.8f,
    val language: String = "FA", // "EN" or "FA"
    val gridCellTheme: String = "WHITE" // "WHITE" or "BLACK"
)

@Entity(tableName = "match_history")
data class MatchHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val opponentName: String,
    val opponentRank: String,
    val opponentElo: Int,
    val opponentAvatarId: Int,
    val result: String, // "VICTORY", "DEFEAT"
    val gameMode: String, // "RANKED", "CASUAL", "AI", "PASS_PLAY"
    val movesCount: Int,
    val eloChange: Int,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "friends")
data class FriendItem(
    @PrimaryKey val uid: String,
    val username: String,
    val avatarId: Int,
    val country: String,
    val rankName: String,
    val elo: Int,
    val status: String, // "ONLINE", "ACTIVE_MATCH", "OFFLINE"
    val isFavorite: Boolean = false
)

@Entity(tableName = "saved_achievements")
data class LocalAchievement(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val maxProgress: Int,
    val currentProgress: Int,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long = 0L
)
