package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val date: String, // format: "yyyy-MM-dd"
    val weight: Float? = null, // in kg
    val caloriesConsumed: Int = 0, // total calories consumed
    val steps: Int = 0, // total steps taken
    var activeCaloriesBurned: Int = 0, // calculated from steps/workout
    val workoutDurationMinutes: Int = 0, // in minutes
    val waterIntakeMl: Int = 0, // in ml
    val calorieGoal: Int = 2000,
    val waterGoalMl: Int = 2500,
    val stepGoal: Int = 10000,
    val weightGoal: Float? = null,
    val notes: String = ""
)

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // format: "yyyy-MM-dd"
    val name: String,
    val calories: Int,
    val imageUrl: String? = null, // local URI or file path
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: String = "current_user",
    val name: String,
    val email: String,
    val phone: String,
    val gender: String,
    val dob: String, // "yyyy-MM-dd"
    val height: Float, // cm
    val weight: Float, // kg
    val workoutDaysPerWeek: Int, // e.g. 0-7 days
    val isWorkoutDaily: Boolean,
    val avatarResName: String, // e.g. "avatar_1", "avatar_2", etc.
    val customPhotoUri: String? = null,
    val sheetsUrl: String = "https://script.google.com/macros/s/AKfycbwiyxbO6sGZoHtwy5qiKXdqp4JaXWYI_ffWQwIkIiTJ0gc_sK8f5IAMr9fHcjj83v6s/exec",
    val joiningDate: String = "" // format: "yyyy-MM-dd"
)

