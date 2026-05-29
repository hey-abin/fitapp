package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.GeminiClient
import com.example.sensor.StepSensorManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = TrackerRepository(db.trackDao())

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate

    // Main selected date logs and items
    val dailyLog: StateFlow<DailyLog?> = _selectedDate
        .flatMapLatest { date -> repository.getDailyLogFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val foodItems: StateFlow<List<FoodItem>> = _selectedDate
        .flatMapLatest { date -> repository.getFoodItemsFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile?> = repository.getUserProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Historical record list for calendar indicators & charts
    val allLogs: StateFlow<List<DailyLog>> = repository.getAllDailyLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Loading states for image analysis
    private val _isAnalyzingImage = MutableStateFlow(false)
    val isAnalyzingImage: StateFlow<Boolean> = _isAnalyzingImage

    private val _imageAnalysisError = MutableStateFlow<String?>(null)
    val imageAnalysisError: StateFlow<String?> = _imageAnalysisError

    private val stepSensorManager = StepSensorManager(application) { newSteps ->
        appendSteps(newSteps)
    }

    init {
        viewModelScope.launch {
            ensureLogExistsForDate(getTodayDateString())
            
            // Safe cleanup of previously pre-populated mock records to clear database confusion
            try {
                val allExisting = repository.getAllDailyLogsFlow().first()
                if (allExisting.size > 1) {
                    val today = getTodayDateString()
                    allExisting.forEach { log ->
                        if (log.date != today) {
                            val foodItemsList = repository.getFoodItemsFlow(log.date).first()
                            if (foodItemsList.isEmpty() && log.notes.isEmpty()) {
                                db.trackDao().deleteDailyLog(log)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Failed to clean up dummy logs: ${e.message}", e)
            }
        }
        
        var lastFinalizedDate = ""
        var lastRolloverDate = ""
        viewModelScope.launch {
            while (true) {
                try {
                    val now = Calendar.getInstance()
                    val currentHour = now.get(Calendar.HOUR_OF_DAY)
                    val currentMinute = now.get(Calendar.MINUTE)
                    val todayStr = getTodayDateString()
                    
                    // 1. At 11:59 PM (23:59), perform final save and sync
                    if (currentHour == 23 && currentMinute == 59) {
                        if (lastFinalizedDate != todayStr) {
                            lastFinalizedDate = todayStr
                            Log.d("TrackerViewModel", "Encountered 11:59 PM! Finalizing stats for $todayStr.")
                            ensureLogExistsForDate(todayStr)
                            
                            val currentLog = repository.getDailyLog(todayStr)
                            if (currentLog != null) {
                                repository.saveDailyLog(currentLog)
                            }
                            
                            userProfile.value?.let { profile ->
                                syncProfileToGoogleSheet(profile)
                            }
                        }
                    }
                    
                    // 2. Exactly at 12:00 AM (00:00), rollover to the new date automatically
                    if (currentHour == 0 && currentMinute == 0) {
                        if (lastRolloverDate != todayStr) {
                            lastRolloverDate = todayStr
                            Log.d("TrackerViewModel", "Encountered 12:00 AM! Auto-rolling over to start new date tracker: $todayStr.")
                            ensureLogExistsForDate(todayStr)
                            
                            val prevDayStr = getFormattedDateOffset(-1)
                            if (_selectedDate.value == prevDayStr) {
                                selectDate(todayStr)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TrackerViewModel", "Date transition loop exception: ${e.message}")
                }
                kotlinx.coroutines.delay(10000) // lightweight 10-second tick
            }
        }
        
        stepSensorManager.startTracking()
    }

    fun getFormattedDateOffset(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    override fun onCleared() {
        super.onCleared()
        stepSensorManager.stopTracking()
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        viewModelScope.launch {
            ensureLogExistsForDate(date)
        }
    }

    private suspend fun prepopulateSomeHistory() {
        // If we don't have past records, insert some beautifully crafted tracking points for the graph!
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val allExisting = repository.getAllDailyLogsFlow().first()
        if (allExisting.size <= 1) {
            // Prepopulate 14 days of historical tracking for a marvelous, pristine charts state!
            val weights = listOf(74.5f, 74.2f, 73.9f, 74.0f, 73.6f, 73.4f, 73.5f, 73.1f, 72.8f, 72.9f, 72.5f, 72.2f, 72.0f, 71.8f)
            val stepsData = listOf(6200, 8400, 11200, 9500, 7800, 12100, 14200, 9100, 8100, 10100, 11500, 8900, 12500, 10500)
            val waterData = listOf(1500, 2000, 2500, 1800, 2200, 3000, 2500, 1600, 2100, 2400, 2800, 2000, 2500, 2200)
            val caloriesIn = listOf(1850, 1900, 2100, 2400, 1750, 1950, 2200, 1600, 1800, 2050, 1950, 2100, 1850, 1900)

            for (i in 13 downTo 1) {
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val dateStr = sdf.format(cal.time)
                
                val weightVal = weights[13 - i]
                val stepsVal = stepsData[13 - i]
                val waterVal = waterData[13 - i]
                val calInVal = caloriesIn[13 - i]
                val workoutVal = if (stepsVal > 10000) 45 else if (stepsVal > 8000) 30 else 15
                
                val log = DailyLog(
                    date = dateStr,
                    weight = weightVal,
                    weightGoal = 70.0f,
                    caloriesConsumed = calInVal,
                    steps = stepsVal,
                    activeCaloriesBurned = ((stepsVal * 0.04f) + (workoutVal * 8f)).toInt(),
                    workoutDurationMinutes = workoutVal,
                    waterIntakeMl = waterVal,
                    calorieGoal = 2000,
                    waterGoalMl = 2200,
                    stepGoal = 10000
                )
                repository.saveDailyLog(log)
            }
        }
    }

    private suspend fun ensureLogExistsForDate(date: String): DailyLog {
        val existing = repository.getDailyLog(date)
        if (existing == null) {
            val lastWeight = getMostRecentWeight()
            val newLog = DailyLog(
                date = date,
                weight = lastWeight ?: 72.0f,
                weightGoal = 70.0f,
                calorieGoal = 2000,
                waterGoalMl = 2200,
                stepGoal = 10000
            )
            repository.saveDailyLog(newLog)
            return newLog
        }
        return existing
    }

    private suspend fun getMostRecentWeight(): Float? {
        val all = repository.getAllDailyLogsFlow().first()
        return all.firstOrNull { it.weight != null }?.weight
    }

    // User Interactive Actions
    fun updateWeight(weight: Float) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        repository.saveDailyLog(log.copy(weight = weight))
    }

    fun updateWeightGoal(goal: Float) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        repository.saveDailyLog(log.copy(weightGoal = goal))
    }

    fun addWater(amountMl: Int) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        repository.saveDailyLog(log.copy(waterIntakeMl = log.waterIntakeMl + amountMl))
    }

    fun resetWater() = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        repository.saveDailyLog(log.copy(waterIntakeMl = 0))
    }

    fun updateWorkout(minutes: Int) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        val updatedLog = log.copy(
            workoutDurationMinutes = minutes,
            activeCaloriesBurned = calculateActiveCalories(log.steps, minutes)
        )
        repository.saveDailyLog(updatedLog)
    }

    fun addWorkoutMinutes(minutes: Int) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        val newMinutes = log.workoutDurationMinutes + minutes
        repository.saveDailyLog(log.copy(
            workoutDurationMinutes = newMinutes,
            activeCaloriesBurned = calculateActiveCalories(log.steps, newMinutes)
        ))
    }

    fun updateSteps(steps: Int) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        val updatedLog = log.copy(
            steps = steps,
            activeCaloriesBurned = calculateActiveCalories(steps, log.workoutDurationMinutes)
        )
        repository.saveDailyLog(updatedLog)
    }

    fun addSteps(steps: Int) = viewModelScope.launch {
        val date = _selectedDate.value
        val log = ensureLogExistsForDate(date)
        val newSteps = log.steps + steps
        repository.saveDailyLog(log.copy(
            steps = newSteps,
            activeCaloriesBurned = calculateActiveCalories(newSteps, log.workoutDurationMinutes)
        ))
    }

    private fun appendSteps(newSteps: Int) {
        viewModelScope.launch {
            val date = getTodayDateString()
            val log = ensureLogExistsForDate(date)
            val newTotal = log.steps + newSteps
            repository.saveDailyLog(log.copy(
                steps = newTotal,
                activeCaloriesBurned = calculateActiveCalories(newTotal, log.workoutDurationMinutes)
            ))
        }
    }

    private fun calculateActiveCalories(steps: Int, workoutMinutes: Int): Int {
        return ((steps * 0.04f) + (workoutMinutes * 8f)).toInt()
    }

    // Gemini API Analysis & Logging of Food Items
    fun analyzeAndLogFoodImage(bitmap: Bitmap, customImageUrl: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isAnalyzingImage.value = true
            _imageAnalysisError.value = null
            try {
                // Call Gemini REST Endpoint
                val result = GeminiClient.analyzeFoodImage(bitmap)
                
                val currentDate = _selectedDate.value
                val newItem = FoodItem(
                    date = currentDate,
                    name = result.foodName,
                    calories = result.calories,
                    imageUrl = customImageUrl ?: "" // Preset URL to display nicely in the list
                )
                repository.saveFoodItem(newItem)

                // Update aggregate calories consumed
                val log = ensureLogExistsForDate(currentDate)
                val currentEaten = repository.getFoodItemsFlow(currentDate).first().sumOf { it.calories }
                repository.saveDailyLog(log.copy(caloriesConsumed = currentEaten))
                
                onComplete()
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Analysis failed: ${e.message}", e)
                _imageAnalysisError.value = e.message ?: "Analysis failed. Please confirm your API key."
            } finally {
                _isAnalyzingImage.value = false
            }
        }
    }

    fun logManualFood(name: String, calories: Int) = viewModelScope.launch {
        val currentDate = _selectedDate.value
        val newItem = FoodItem(
            date = currentDate,
            name = name,
            calories = calories,
            imageUrl = ""
        )
        repository.saveFoodItem(newItem)

        val log = ensureLogExistsForDate(currentDate)
        val currentEaten = repository.getFoodItemsFlow(currentDate).first().sumOf { it.calories }
        repository.saveDailyLog(log.copy(caloriesConsumed = currentEaten))
    }

    fun removeFoodItem(item: FoodItem) = viewModelScope.launch {
        repository.removeFoodItem(item)
        val currentDate = _selectedDate.value
        val log = ensureLogExistsForDate(currentDate)
        val currentEaten = repository.getFoodItemsFlow(currentDate).first().sumOf { it.calories }
        repository.saveDailyLog(log.copy(caloriesConsumed = currentEaten))
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(profile)
            syncProfileToGoogleSheet(profile)
        }
    }

    fun syncProfileToGoogleSheet(profile: UserProfile) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var urlString = profile.sheetsUrl
                    if (urlString.isNotEmpty() && urlString.startsWith("http")) {
                        val json = org.json.JSONObject().apply {
                            put("name", profile.name)
                            put("email", profile.email)
                            put("phone", profile.phone)
                            put("gender", profile.gender)
                            put("dob", profile.dob)
                            put("height", profile.height)
                            put("weight", profile.weight)
                            put("workoutDaysPerWeek", profile.workoutDaysPerWeek)
                            put("isWorkoutDaily", profile.isWorkoutDaily)
                            put("avatarResName", profile.avatarResName)
                            put("timestamp", System.currentTimeMillis())
                        }
                        val jsonPayload = json.toString()
                        Log.d("GoogleSheetsSync", "Sending payload to: $urlString")
                        Log.d("GoogleSheetsSync", "Payload: $jsonPayload")

                        var url = java.net.URL(urlString)
                        var conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.setRequestProperty("Accept", "application/json")
                        conn.doOutput = true
                        conn.instanceFollowRedirects = false // Manual follow
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        
                        conn.outputStream.use { os ->
                            val input = jsonPayload.toByteArray(charset("utf-8"))
                            os.write(input, 0, input.size)
                        }
                        
                        var responseCode = conn.responseCode
                        Log.d("GoogleSheetsSync", "Initial URL response code: $responseCode")
                        
                        // Google Apps Script redirect handling
                        if (responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                            responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                            responseCode == 307 || responseCode == 303 || responseCode == 308) {
                            
                            val redirectUrlStr = conn.getHeaderField("Location")
                            Log.d("GoogleSheetsSync", "Redirect URL: $redirectUrlStr")
                            conn.disconnect()
                            
                            if (!redirectUrlStr.isNullOrEmpty()) {
                                // Follow redirect with GET request
                                val redirectUrl = java.net.URL(redirectUrlStr)
                                val redirectConn = redirectUrl.openConnection() as java.net.HttpURLConnection
                                redirectConn.requestMethod = "GET"
                                redirectConn.connectTimeout = 10000
                                redirectConn.readTimeout = 10000
                                
                                val rdResponseCode = redirectConn.responseCode
                                Log.d("GoogleSheetsSync", "Redirect URL response code: $rdResponseCode")
                                
                                val responseStream = if (rdResponseCode in 200..299) redirectConn.inputStream else redirectConn.errorStream
                                val text = responseStream?.bufferedReader()?.use { it.readText() }
                                Log.d("GoogleSheetsSync", "Redirect Response Text: $text")
                                redirectConn.disconnect()
                            }
                        } else {
                            val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                            val text = responseStream?.bufferedReader()?.use { it.readText() }
                            Log.d("GoogleSheetsSync", "Response Text: $text")
                            conn.disconnect()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleSheetsSync", "Failed to sync to Google Sheets: ${e.message}", e)
            }
        }
    }
}
