package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    // Profile queries
    @Query("SELECT * FROM local_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<LocalProfile?>

    @Query("SELECT * FROM local_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): LocalProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: LocalProfile)

    // Match history queries
    @Query("SELECT * FROM match_history ORDER BY timestamp DESC")
    fun getMatchHistoryFlow(): Flow<List<MatchHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchHistory(match: MatchHistoryItem)

    @Query("DELETE FROM match_history")
    suspend fun clearMatchHistory()

    // Friends queries
    @Query("SELECT * FROM friends ORDER BY username ASC")
    fun getFriendsFlow(): Flow<List<FriendItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendItem)

    @Query("DELETE FROM friends WHERE uid = :uid")
    suspend fun removeFriend(uid: String)

    @Query("UPDATE friends SET status = :status WHERE uid = :uid")
    suspend fun updateFriendStatus(uid: String, status: String)

    // Achievements queries
    @Query("SELECT * FROM saved_achievements")
    fun getAchievementsFlow(): Flow<List<LocalAchievement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<LocalAchievement>)

    @Update
    suspend fun updateAchievement(achievement: LocalAchievement)

    @Query("DELETE FROM local_profile")
    suspend fun clearProfile()

    @Query("DELETE FROM friends")
    suspend fun clearFriends()

    @Query("DELETE FROM saved_achievements")
    suspend fun clearAchievements()
}
