package com.local.passover.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            NewHome(navController = navController, activity)
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
fun NewHome(navController: NavController, activity: Activity){
    var text = "Hello"
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("1️⃣ Go to Settings → Accessibility → ClipCatcher → Enable")
        Button(onClick = {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text("Open Accessibility Settings")
        }
        Button(onClick = {
            val intent = Intent(context, BLEConnectionService::class.java)
            intent.action = BLEConnectionService.ACTIONS.STOP.toString()
            activity.stopService(intent)
        }) {
            Text("STOP Service")
        }

        Spacer(Modifier.height(24.dp))

        Text("2️⃣ Test below:")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Long-press or tap to select some text above, then tap the Copy icon.")
    }
}