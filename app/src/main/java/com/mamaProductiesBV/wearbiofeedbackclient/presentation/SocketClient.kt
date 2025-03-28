package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.util.Log
import kotlinx.coroutines.*
import java.net.Socket
import java.time.Instant

class SocketClient(
    private val unityHost: String,
    private val unityPort: Int,
    private val deviceId: String
) {
    private var socket: Socket? = null
    private var output = socket?.getOutputStream()
    private var input = socket?.getInputStream()

    private val TAG = "SocketClient"

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connecting to $unityHost:$unityPort")
                socket = Socket(unityHost, unityPort)
                input = socket?.getInputStream()
                output = socket?.getOutputStream()
                Log.d(TAG, "Connected to Unity")

                val buffer = ByteArray(1024)
                val len = input?.read(buffer) ?: 0
                val serverMsg = String(buffer, 0, len).trim()
                Log.d(TAG, "Unity says: $serverMsg")

                if (serverMsg == "UNITY_HEART_RATE_SERVER_MMPRO_RG1fCEaI307MPsMD") {
                    val handshakeJson =
                        """{"type":"handshake","deviceId":"$deviceId"}\n"""
                    output?.write(handshakeJson.toByteArray())
                    Log.d(TAG, "Sent handshake JSON: $handshakeJson")

                    val responseLen = input?.read(buffer) ?: 0
                    val response = String(buffer, 0, responseLen).trim()
                    Log.d(TAG, "Got handshake response: $response")

                    if (response.contains("handshake_success")) {
                        Log.d(TAG, "Handshake success acknowledged by Unity")
                        sendHeartRateOnce()
                    } else {
                        Log.w(TAG, "Handshake failed or was not acknowledged")
                    }
                } else {
                    Log.w(TAG, "Did not receive correct handshake string from Unity")
                }

                socket?.close()
                Log.d(TAG, "Socket closed")
            } catch (e: Exception) {
                Log.e(TAG, "Socket error: ${e.message}", e)
            }
        }
    }

    private suspend fun sendHeartRateOnce() {
        delay(1000)
        val hr = 73
        val timestamp = Instant.now().toString()
        val hrJson = """
            {
                "type":"heartrate",
                "deviceId":"$deviceId",
                "heartRate":$hr,
                "timestamp":"$timestamp"
            }\n
        """.trimIndent()

        Log.d(TAG, "Sending HR JSON: $hrJson")
        output?.write(hrJson.toByteArray())
        Log.d(TAG, "HR message sent")
    }
}
