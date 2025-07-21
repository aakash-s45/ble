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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.local.passover.classes.AppViewModel
import com.local.passover.services.BLEConnectionService

@Composable
fun Home(activity: Activity) {
    val navController = rememberNavController()
    val viewModel: AppViewModel = hiltViewModel()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
//            NewHome(activity = activity, navController = navController, viewModel = viewModel)
            FirstPage(navController = navController, activity)
        }
        composable("Preferences") {
            Preferences(navController = navController)
        }
        composable("ConfigureWebhook") {
            ConfigureWebhook(navController = navController)
        }
        composable("ManageContacts") {
            ManageContacts(navController = navController)
        }
        composable("ConfigureNotifications") {
            ConfigureNotifications(navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstPage(navController: NavController, activity: Activity) {
    val context = LocalContext.current
    val viewModel: AppViewModel = hiltViewModel()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    var text by remember { mutableStateOf("Hello") }

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

                Text(
                    text = "Step 2: Start or Stop the BLE Service",
                    style = MaterialTheme.typography.titleMedium
                )

                if (!isServiceRunning) {
                    Button(
                        onClick = {
                            val intent = Intent(context, BLEConnectionService::class.java).apply {
                                action = BLEConnectionService.ACTIONS.START.toString()
                            }
                            activity.startService(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("START Service")
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(context, BLEConnectionService::class.java).apply {
                                action = BLEConnectionService.ACTIONS.STOP.toString()
                            }
                            activity.stopService(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("STOP Service")
                    }
                }
            }
        }
    )
}
