package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

object DiscoveryHelper {
    suspend fun listenForServerBroadcast(port: Int = 8888, timeoutMs: Int = 5000): Pair<String, Int>? =
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = timeoutMs
                    bind(java.net.InetSocketAddress(port))
                }

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val msg = String(packet.data, 0, packet.length).trim()
                Log.d("DiscoveryHelper", "Received: $msg")

                val json = JSONObject(msg)
                if (json.getString("type") == "discovery") {
                    val ip = json.getString("ip")
                    val unityPort = json.getInt("port")
                    return@withContext Pair(ip, unityPort)
                }
            } catch (e: Exception) {
                if (e is java.net.SocketTimeoutException) {
                    Log.d("DiscoveryHelper", "No discovery response received — retrying...")
                } else {
                    Log.e("DiscoveryHelper", "Unexpected error: ${e.message}", e)
                }
            } finally {
                socket?.close()
            }
            null
        }
}