package com.local.passover.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.local.passover.services.PassoverAccessibilityService
import com.local.passover.core.ConnectionState
import com.local.passover.viewmodels.FirstPageViewModel
import com.local.passover.utils.isAccessibilityServiceRunning

@Composable
fun Home(activity: Activity) {
    val navController = rememberNavController()
//    val viewModel: AppViewModel = hiltViewModel()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
//            NewHome(activity = activity, navController = navController, viewModel = viewModel)
            FirstPage(navController = navController, activity)
        }
//        composable("Preferences") {
//            Preferences(navController = navController)
//        }
//        composable("ConfigureWebhook") {
//            ConfigureWebhook(navController = navController)
//        }
//        composable("ManageContacts") {
//            ManageContacts(navController = navController)
//        }
//        composable("ConfigureNotifications") {
//            ConfigureNotifications(navController = navController)
//        }
        composable("logViewer"){
            LogViewerScreen(onNavigateUp = {
                navController.navigateUp()
            })
        }
        composable("scanQR"){
            QRScannerScreen(onNavigateUp = {
                navController.navigateUp()
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstPage(navController: NavController, activity: Activity) {
    val context = LocalContext.current
    val viewModel: FirstPageViewModel = hiltViewModel()
//    val viewModel: AppViewModel = hiltViewModel()
//    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
//    val status by BluetoothL2capManager.status.collectAsState()
//    val currentClient by BluetoothL2capManager.currentClient.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val connectionState by viewModel.connectionState.collectAsState()
    val hasCredentials by viewModel.hasCredentials.collectAsState(false)

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
//                Button(onClick = {
//                    NetworkManager.findMacServer()
//                }){
//                    Text("Find mac server")
//                }

//                if (!isServiceRunning) {
//                    Button(
//                        onClick = {
//                            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
//                        },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text("Open Accessibility Settings")
//                    }
//                }

                Divider()

                Text(
                    text = "Step 2: Start or Stop the BLE Service",
                    style = MaterialTheme.typography.titleMedium
                )

//                if (!isServiceRunning) {
//                    Button(
//                        onClick = {
//                            val intent = Intent(context, BLEConnectionService::class.java).apply {
//                                action = BLEConnectionService.ACTIONS.START.toString()
//                            }
//                            activity.startService(intent)
//                        },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text("START Service")
//                    }
//                } else {
//                    Column {
//                        Text(status)
//                        Text(currentClient.toString())
//                        Button(
//                            onClick = {
//                                val intent = Intent(context, BLEConnectionService::class.java).apply {
//                                    action = BLEConnectionService.ACTIONS.STOP.toString()
//                                }
//                                activity.stopService(intent)
//                            },
//                            modifier = Modifier.fillMaxWidth()
//                        ) {
//                            Text("STOP Service")
//                        }
//                    }
//                }

                Button(
                    onClick = {
                        navController.navigate("logViewer")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Logs")
                }
                if(isServiceRunning && connectionState != ConnectionState.CONNECTED && connectionState != ConnectionState.CONNECTING){
                    Button(
                        onClick = {
                            navController.navigate("scanQR")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan QR")
                    }
                }
                if(hasCredentials){
                    Button(
                        onClick = {
                            viewModel.removeSavedCredentials()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove Saved Credentials")
                    }
                }
            }
        }
    )
}
