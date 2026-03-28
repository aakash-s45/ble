package com.local.passover.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.local.passover.services.PassoverAccessibilityService
import com.local.passover.core.ConnectionState
import com.local.passover.core.TrustedPeerStore
import com.local.passover.viewmodels.FirstPageViewModel
import com.local.passover.utils.isAccessibilityServiceRunning

@Composable
fun Home(activity: Activity) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            FirstPage(navController = navController, activity)
        }
        composable("logViewer") {
            LogViewerScreen(onNavigateUp = {
                navController.navigateUp()
            })
        }
        composable("pairing") {
            PairingScreen(onNavigateUp = {
                navController.navigateUp()
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstPage(navController: androidx.navigation.NavController, activity: Activity) {
    val context = LocalContext.current
    val viewModel: FirstPageViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val connectionState by viewModel.connectionState.collectAsState()
    val trustedPeers by viewModel.trustedPeers.collectAsState(emptyList())

    var isServiceRunning by remember {
        mutableStateOf(isAccessibilityServiceRunning(context, PassoverAccessibilityService::class.java))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning =
                    isAccessibilityServiceRunning(context, PassoverAccessibilityService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passover Setup") }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Step 1: Enable Accessibility Service",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "To enable the service:\n\n" +
                            "1. Tap the button below.\n" +
                            "2. Scroll to 'Passover'.\n" +
                            "3. Tap it and toggle ON the Accessibility Service.\n\n" +
                            "⚠️ This is necessary for the app to interact with system UI.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isServiceRunning) {
                    Button(
                        onClick = {
                            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Accessibility Settings")
                    }
                }

                Divider()

                // ── Pair New Device ──
                Text(
                    text = "Step 2: Pair a Device",
                    style = MaterialTheme.typography.titleMedium
                )

                if (isServiceRunning && connectionState != ConnectionState.CONNECTED && connectionState != ConnectionState.CONNECTING) {
                    Button(
                        onClick = {
                            navController.navigate("pairing")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pair New Device")
                    }
                }

                // ── View Logs ──
                Button(
                    onClick = {
                        navController.navigate("logViewer")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Logs")
                }
                // TODO: use flag isServiceRunning

                // ── Trusted Devices ──
                if (trustedPeers.isNotEmpty()) {
                    Divider()
                    Text(
                        text = "Trusted Devices",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(trustedPeers) { peer ->
                            TrustedPeerItem(
                                peer = peer,
                                onRemove = { viewModel.removeTrustedPeer(peer.deviceId) }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun TrustedPeerItem(
    peer: TrustedPeerStore.TrustedPeer,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = peer.deviceId.take(8) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove device",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
