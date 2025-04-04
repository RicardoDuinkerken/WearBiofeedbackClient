/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.mamaProductiesBV.wearbiofeedbackclient.R
import com.mamaProductiesBV.wearbiofeedbackclient.presentation.theme.WearBiofeedbackClientTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var socketClient: SocketClient? = null
    private var discoveryJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(
                onClientReady = { socketClient = it },
                registerDiscoveryJob = { discoveryJob = it }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy() called — stopping socket")
        socketClient?.stop()
        discoveryJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun WearApp(
    onClientReady: (SocketClient) -> Unit,
    registerDiscoveryJob: (Job) -> Unit
) {
    WearBiofeedbackClientTheme {
        val connectionState = remember { mutableStateOf(ConnectionState.CONNECTING) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            MessageText(state = connectionState.value)

            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val job = coroutineScope.launch(Dispatchers.IO) {
                    var serverInfo: Pair<String, Int>? = null

                    while (isActive && serverInfo == null) {
                        serverInfo = DiscoveryHelper.listenForServerBroadcast()
                        if (serverInfo == null) {
                            Log.d("WearApp", "No server found, retrying in 5 seconds...")
                            delay(5000)
                        }
                    }

                    if (serverInfo != null && isActive) {
                        val (host, port) = serverInfo
                        val client = SocketClient(
                            unityHost = host,
                            unityPort = port,
                            deviceId = "Watch_001"
                        ) { state ->
                            connectionState.value = state
                        }
                        onClientReady(client)
                        client.start()
                    }
                }

                registerDiscoveryJob(job)
            }
        }
    }
}

@Composable
fun MessageText(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTING -> MaterialTheme.colors.secondary
        ConnectionState.CONNECTED -> MaterialTheme.colors.primary
        ConnectionState.RECONNECTING -> MaterialTheme.colors.error
    }

    val text = when (state) {
        ConnectionState.CONNECTING -> "Connecting..."
        ConnectionState.CONNECTED -> "Connected!"
        ConnectionState.RECONNECTING -> "Reconnecting..."
    }

    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = color,
        text = text
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(onClientReady = {}, registerDiscoveryJob = {})
}
