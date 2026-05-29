package com.example.data

import kotlinx.coroutines.flow.Flow

class TrackerRepository(private val trackDao: TrackDao) {
    fun getDailyLogFlow(date: String): Flow<DailyLog?> = trackDao.getDailyLogFlow(date)
    
    suspend fun getDailyLog(date: String): DailyLog? = trackDao.getDailyLog(date)
    
    suspend fun saveDailyLog(log: DailyLog) = trackDao.insertDailyLog(log)
    
    fun getFoodItemsFlow(date: String): Flow<List<FoodItem>> = trackDao.getFoodItemsFlow(date)
    
    suspend fun saveFoodItem(item: FoodItem) = trackDao.insertFoodItem(item)
    
    suspend fun removeFoodItem(item: FoodItem) = trackDao.deleteFoodItem(item)

    fun getAllDailyLogsFlow(): Flow<List<DailyLog>> = trackDao.getAllDailyLogsFlow()
    
    fun getDailyLogsInRangeFlow(startDate: String): Flow<List<DailyLog>> = trackDao.getDailyLogsInRangeFlow(startDate)

    fun getAllFoodItemsFlow(): Flow<List<FoodItem>> = trackDao.getAllFoodItemsFlow()

    fun getUserProfileFlow(): Flow<UserProfile?> = trackDao.getUserProfileFlow()

    suspend fun getUserProfile(): UserProfile? = trackDao.getUserProfile()

    suspend fun saveUserProfile(profile: UserProfile) = trackDao.insertUserProfile(profile)
}
