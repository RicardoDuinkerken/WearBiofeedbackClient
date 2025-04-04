package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class SocketClient(
    private val unityHost: String,
    private val unityPort: Int,
    private val deviceId: String,
    private val context: Context,
    private val onStateChange: (ConnectionState) -> Unit
) {
    private var socket: Socket? = null
    private var output = socket?.getOutputStream()
    private var input = socket?.getInputStream()
    private val TAG = "SocketClient"

    private val isStreaming = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private var wasEverConnected = false
    private lateinit var heartRateSensorManager: HeartRateSensorManager

    suspend fun start() {
        if (running.get()) return
        running.set(true)

        do {
            try {
                onStateChange(
                    if (wasEverConnected) ConnectionState.RECONNECTING else ConnectionState.CONNECTING
                )

                Log.d(TAG, "Attempting to connect to $unityHost:$unityPort")
                socket = Socket(unityHost, unityPort)
                input = socket?.getInputStream()
                output = socket?.getOutputStream()
                Log.d(TAG, "Connected to Unity")

                val handshakeJson = JSONObject().apply {
                    put("type", "handshake")
                    put("deviceId", deviceId)
                }.toString() + "\n"
                
                output?.write(handshakeJson.toByteArray())
                Log.d(TAG, "Sent handshake JSON")

                val buffer = ByteArray(1024)
                val messageQueue = LinkedList<String>()
                var handshakeComplete = false

                withContext(Dispatchers.IO) {
                    val timeoutMillis = 3000L
                    val startTime = System.currentTimeMillis()
                    while (!handshakeComplete && System.currentTimeMillis() - startTime < timeoutMillis) {
                        val len = input?.read(buffer) ?: break
                        if (len == -1) break

                        val incoming = String(buffer, 0, len).trim()
                        Log.d(TAG, "Got handshake response chunk: $incoming")

                        for (line in incoming.split("\n")) {
                            val msg = line.trim()
                            try {
                                val json = JSONObject(msg)
                                if (json.optString("type", "").lowercase() == "handshake_success") {
                                    handshakeComplete = true
                                } else {
                                    messageQueue.add(msg)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse handshake JSON: $msg", e)
                            }
                        }
                    }
                }

                if (!handshakeComplete) {
                    Log.w(TAG, "Handshake failed or timed out")
                    close()
                    delay(3000)
                    continue
                }

                wasEverConnected = true
                onStateChange(ConnectionState.CONNECTED)
                Log.d(TAG, "Handshake success acknowledged by Unity")

                heartRateSensorManager = HeartRateSensorManager(context)

                coroutineScope {
                    val listenerJob = launch { listenForCommands(messageQueue) }
                    val streamJob = launch { streamData() }

                    try {
                        listenerJob.join()
                    } finally {
                        streamJob.cancelAndJoin()
                    }
                }

            } catch (e: SocketException) {
                Log.w(TAG, "Connection failed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Socket error: ${e.message}", e)
            } finally {
                close()
                Log.d(TAG, "Retrying in 3 seconds...")
                delay(3000)
            }
        } while (running.get())
    }

    fun stop() {
        running.set(false)
        close()
        Log.d(TAG, "Client stopped by user")
    }

    private suspend fun listenForCommands(preloadedMessages: LinkedList<String>? = null) {
        try {
            val buffer = ByteArray(1024)

            preloadedMessages?.forEach { msg -> handleCommand(msg) }
            preloadedMessages?.clear()

            while (true) {
                val len = input?.read(buffer) ?: -1
                if (len == -1) break

                val incoming = String(buffer, 0, len).trim()
                Log.d(TAG, "Received message from Unity: $incoming")

                for (line in incoming.split("\n")) {
                    val msg = line.trim()
                    handleCommand(msg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while listening for commands: ${e.message}", e)
        } finally {
            Log.d(TAG, "Listener ending, closing socket")
            close()
        }
    }

    private fun handleCommand(msg: String) {
        try {
            val json = JSONObject(msg)
            when (json.optString("type", "").lowercase()) {
                "start" -> {
                    isStreaming.set(true)
                    Log.d(TAG, "Streaming ENABLED by Unity")
                }
                "stop" -> {
                    isStreaming.set(false)
                    Log.d(TAG, "Streaming DISABLED by Unity")
                }
                else -> Log.d(TAG, "Unknown command: ${json.optString("type")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $msg", e)
        }
    }

    private suspend fun streamData() = coroutineScope {
        launch {
            heartRateSensorManager.streamHeartRate().collect { (hr, ts) ->
                if (isStreaming.get()) {
                    val json = JSONObject().apply {
                        put("type", "heartrate")
                        put("deviceId", deviceId)
                        put("heartRate", hr)
                        put("timestamp", ts)
                    }.toString() + "\n"
                    output?.write(json.toByteArray())
                    Log.d(TAG, "Sent HR: $json")
                }
            }
        }

        launch {
            heartRateSensorManager.streamHeartRateVariability().collect { (hrv, ts) ->
                if (isStreaming.get()) {
                    val json = JSONObject().apply {
                        put("type", "hrv")
                        put("deviceId", deviceId)
                        put("hrv", hrv)
                        put("timestamp", ts)
                    }.toString() + "\n"
                    output?.write(json.toByteArray())
                    Log.d(TAG, "Sent HRV: $json")
                }
            }
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