/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.mamaProductiesBV.wearbiofeedbackclient.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import androidx.wear.tooling.preview.devices.WearDevices
import com.mamaProductiesBV.wearbiofeedbackclient.presentation.theme.WearBiofeedbackClientTheme
import kotlinx.coroutines.*
class MainActivity : ComponentActivity() {
    private var socketClient: SocketClient? = null
    private var discoveryJob: Job? = null
    private var permissionState: MutableState<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            val context = LocalContext.current
            permissionState = remember { mutableStateOf("unknown") }
            val state = permissionState!!

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                state.value = when {
                    isGranted -> "granted"
                    !shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS) -> "permanently_denied"
                    else -> "denied"
                }
            }

            // Initial permission check
            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BODY_SENSORS
                ) == PackageManager.PERMISSION_GRANTED

                state.value = when {
                    granted -> "granted"
                    !shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS) -> "permanently_denied"
                    else -> "denied"
                }

                if (!granted) {
                    permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                }
            }

            when (state.value) {
                "granted" -> {
                    WearApp(
                        onClientReady = { socketClient = it },
                        registerDiscoveryJob = { discoveryJob = it }
                    )
                }

                "denied" -> {
                    PermissionRequiredScreen(
                        message = "This app needs access to your heart rate sensor to work.",
                        buttonText = "Try Again",
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                        }
                    )
                }

                "permanently_denied" -> {
                    PermissionRequiredScreen(
                        message = "Permission is permanently denied.\nPlease enable it in system settings.",
                        buttonText = "Open Settings",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketClient?.stop()
        discoveryJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        permissionState?.let { state ->
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED

            state.value = when {
                granted -> "granted"
                !shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS) -> "permanently_denied"
                else -> "denied"
            }
        }
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
            val context = LocalContext.current.applicationContext

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
                            deviceId = "Watch_001",
                            context = context,
                            onStateChange = { state ->
                                connectionState.value = state
                            }
                        )
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

@Composable
fun PermissionRequiredScreen(message: String, buttonText: String, onClick: () -> Unit) {
    WearBiofeedbackClientTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = onClick) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(onClientReady = {}, registerDiscoveryJob = {})
}
