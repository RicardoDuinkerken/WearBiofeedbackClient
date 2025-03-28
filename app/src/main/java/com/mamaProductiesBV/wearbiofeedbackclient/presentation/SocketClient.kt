package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.Socket
import java.net.SocketException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class SocketClient(
    private val unityHost: String,
    private val unityPort: Int,
    private val deviceId: String
) {
    private var socket: Socket? = null
    private var output = socket?.getOutputStream()
    private var input = socket?.getInputStream()
    private val TAG = "SocketClient"

    private val isStreaming = AtomicBoolean(false)
    private val running = AtomicBoolean(true)

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            while (running.get()) {
                try {
                    Log.d(TAG, "Attempting to connect to $unityHost:$unityPort")
                    socket = Socket(unityHost, unityPort)
                    input = socket?.getInputStream()
                    output = socket?.getOutputStream()
                    Log.d(TAG, "Connected to Unity")

                    val buffer = ByteArray(1024)

                    // Send handshake
                    val handshakeJson = """{"type":"handshake","deviceId":"$deviceId"}""".trim() + "\n"
                    output?.write(handshakeJson.toByteArray())
                    Log.d(TAG, "Sent handshake JSON immediately after connect")

                    // Wait for handshake response
                    val responseLen = input?.read(buffer) ?: 0
                    val response = String(buffer, 0, responseLen).trim()
                    Log.d(TAG, "Got handshake response: $response")

                    if (!response.contains("handshake_success")) {
                        Log.w(TAG, "Handshake failed or not acknowledged")
                        close()
                        delay(3000)
                        continue
                    }

                    Log.d(TAG, "Handshake success acknowledged by Unity")

                    // Start listening for commands and HR loop
                    val listener = launch { listenForCommands() }
                    val streamer = launch { streamHeartRateLoop() }

                    // Wait for both to finish (in case of disconnection)
                    listener.join()
                    streamer.join()

                } catch (e: SocketException) {
                    Log.w(TAG, "Connection failed: ${e.message}")
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "Socket error: ${e.message}", e)
                    close()
                }

                Log.d(TAG, "Retrying in 3 seconds...")
                delay(3000)
            }
        }
    }

    private suspend fun listenForCommands() {
        try {
            val buffer = ByteArray(1024)
            while (running.get()) {
                val len = input?.read(buffer) ?: -1
                if (len == -1) break

                val incoming = String(buffer, 0, len).trim()
                Log.d(TAG, "Received message from Unity: $incoming")

                for (line in incoming.split("\n")) {
                    val msg = line.trim()
                    try {
                        val json = JSONObject(msg)
                        val type = json.optString("type", "").lowercase()

                        when (type) {
                            "start" -> {
                                isStreaming.set(true)
                                Log.d(TAG, "Streaming ENABLED by Unity")
                            }
                            "stop" -> {
                                isStreaming.set(false)
                                Log.d(TAG, "Streaming DISABLED by Unity")
                            }
                            else -> Log.d(TAG, "Unknown command: $type")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message: $msg", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while listening for commands: ${e.message}", e)
        } finally {
            close()
        }
    }

    private suspend fun streamHeartRateLoop() {
        try {
            while (running.get()) {
                if (isStreaming.get()) {
                    val hr = 65 + (0..10).random()
                    val timestamp = Instant.now().toString()
                    val hrJson = JSONObject().apply {
                        put("type", "heartrate")
                        put("deviceId", deviceId)
                        put("heartRate", hr)
                        put("timestamp", timestamp)
                    }.toString() + "\n"

                    Log.d(TAG, "Sending HR JSON: $hrJson")
                    output?.write(hrJson.toByteArray())
                    Log.d(TAG, "HR message sent")
                }
                delay(2000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while streaming HR: ${e.message}", e)
        } finally {
            close()
        }
    }

    private fun close() {
        try {
            isStreaming.set(false)
            input?.close()
            output?.close()
            socket?.close()
            Log.d(TAG, "Socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }
}

