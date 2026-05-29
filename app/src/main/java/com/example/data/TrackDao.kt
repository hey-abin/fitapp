package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM daily_logs WHERE date = :date")
    fun getDailyLogFlow(date: String): Flow<DailyLog?>

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    suspend fun getDailyLog(date: String): DailyLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyLog(log: DailyLog)

    @Delete
    suspend fun deleteDailyLog(log: DailyLog)

    @Query("SELECT * FROM food_items WHERE date = :date ORDER BY timestamp DESC")
    fun getFoodItemsFlow(date: String): Flow<List<FoodItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItem)

    @Delete
    suspend fun deleteFoodItem(item: FoodItem)

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllDailyLogsFlow(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE date >= :startDate ORDER BY date ASC")
    fun getDailyLogsInRangeFlow(startDate: String): Flow<List<DailyLog>>

    @Query("SELECT * FROM food_items ORDER BY timestamp DESC")
    fun getAllFoodItemsFlow(): Flow<List<FoodItem>>

    @Query("SELECT * FROM user_profile WHERE id = 'current_user'")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 'current_user'")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
}
