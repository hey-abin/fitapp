package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Resize first if too large, to keep payloads reasonable and quick
        val maxDimension = 800
        val ratio = Math.min(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetWidth = if (ratio < 1.0) (width * ratio).toInt() else width
        val targetHeight = if (ratio < 1.0) (height * ratio).toInt() else height
        
        val resized = if (ratio < 1.0) {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        } else {
            this
        }
        
        resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Analyses food image to determine food name and calories.
     * Returns a Pair of (FoodName, Calories) or throws an exception.
     */
    suspend fun analyzeFoodImage(bitmap: Bitmap): FoodAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add it via AI Studio Secrets Panel.")
        }

        val base64Image = bitmap.toBase64()
        val promptText = """
            Analyze this image of food. Respond strictly with a JSON object containing the fields:
            "foodName" (string, short descriptive name of the food),
            "calories" (integer estimated calorie intake),
            "description" (string, brief summary description of size and estimated content).
            
            Example output format:
            {
               "foodName": "Avocado Toast with Egg",
               "calories": 350,
               "description": "One slice of sourdough toast topped with half a mashed avocado and a fried egg."
            }
            Do not include any markdown backticks or any other text before or after the JSON.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No response received from Gemini API.")
            
            Log.d(TAG, "Raw Response: $jsonText")
            val cleanJson = jsonText.trim()
            val jsObj = JSONObject(cleanJson)
            val foodName = jsObj.optString("foodName", "Unknown Meal")
            val calories = jsObj.optInt("calories", 250)
            val description = jsObj.optString("description", "")

            FoodAnalysisResult(foodName, calories, description)
        } catch (e: Exception) {
            Log.e(TAG, "Error in image analysis: ${e.message}", e)
            // Fallback or rethrow
            throw e
        }
    }
}

data class FoodAnalysisResult(
    val foodName: String,
    val calories: Int,
    val description: String
)
