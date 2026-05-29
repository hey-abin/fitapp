package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StepSensorManager(private val context: Context, private val onStepsChanged: (Int) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var lastRecordedSteps: Float = -1f
    private val _isSensorAvailable = MutableStateFlow(stepCounterSensor != null)
    val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable

    fun startTracking() {
        if (stepCounterSensor != null) {
            try {
                sensorManager?.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
                Log.d("StepSensorManager", "Registered step counter sensor listener.")
            } catch (e: SecurityException) {
                Log.w("StepSensorManager", "SecurityException registering step sensor: ${e.message}")
            } catch (e: Exception) {
                Log.e("StepSensorManager", "Error registering step sensor: ${e.message}")
            }
        } else {
            Log.d("StepSensorManager", "Step counter sensor not available on this device.")
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        Log.d("StepSensorManager", "Unregistered step counter sensor listener.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0]
            if (lastRecordedSteps < 0) {
                lastRecordedSteps = totalStepsSinceBoot
                return
            }
            val deltaSteps = (totalStepsSinceBoot - lastRecordedSteps).toInt()
            if (deltaSteps > 0) {
                lastRecordedSteps = totalStepsSinceBoot
                onStepsChanged(deltaSteps)
            }
            Log.d("StepSensorManager", "Sensor changed: boot=$totalStepsSinceBoot, delta=$deltaSteps")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
