package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import java.time.Instant

class HeartRateSensorManager(private val context: Context) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    init {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        allSensors.forEach {
            Log.d("SensorCheck", "Sensor: ${it.name} / Type: ${it.type}")
        }
    }

    fun streamHeartRate() = callbackFlow<Pair<Int, String>> {
        val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val hr = it.values.firstOrNull()?.toInt() ?: return
                    val timestamp = Instant.now().toString()
                    trySend(hr to timestamp)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Simulated HRV for now — real HRV needs raw PPG or complex API
    fun streamHeartRateVariability() = flow {
        while (true) {
            val hrv = (30..80).random() + (0..99).random() / 100.0 // Random 30.0–80.99ms
            val timestamp = Instant.now().toString()
            emit(hrv to timestamp)
            delay(2000) // Slow down to fake it
        }
    }.onCompletion {
        // Optional cleanup
    }
}