package com.local.passover.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.local.passover.classes.DataStoreHandler
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Preferences(navController: NavController) {
    BackHandler(enabled = true) {
        navController.popBackStack()
    }
    Scaffold (
        topBar = {
                 TopBar(navController, "Preferences")
        },
        backgroundColor = Color.Black,
    ) { paddingValues ->
        Column (modifier = Modifier
            .padding(paddingValues)
            .padding(10.dp)){
            ListItem (text = { Text("Configure Webhook URL") }, modifier = Modifier.clickable { navController.navigate("ConfigureWebhook") })
            ListItem (text = { Text("Manage Contacts") }, modifier = Modifier.clickable { navController.navigate("ManageContacts") })
            ListItem (text = { Text("Configure Notifications") }, modifier = Modifier.clickable { navController.navigate("ConfigureNotifications") })
        }
    }
}

@Composable
fun TopBar(navController: NavController, title: String){
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)){
        IconButton(onClick = { navController.popBackStack() }){
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
fun ConfigureWebhook(navController: NavController){
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val dataStoreHelper = remember { DataStoreHandler(context) }
    val coroutineScope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var isTextFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = Unit) {
        url = dataStoreHelper.getUrlDirect()
    }
    Scaffold (
        topBar = {
            TopBar(navController, "Configure Webhook")
        },
        backgroundColor = Color.Black,
    ) { paddingValues ->
        Column (modifier = Modifier
            .padding(paddingValues)
            .padding(10.dp)){
            OutlinedTextField(
                value = url,
                singleLine = true,
                label = {
                        Text("Webhook URL")
                },
                placeholder = { Text(text = "Enter Webhook URL here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {focusState->
                        isTextFieldFocused = focusState.hasFocus
                },
                shape = RoundedCornerShape(10.dp),
                onValueChange = {
                  url = it
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    disabledTextColor = Color.DarkGray,
                    disabledLabelColor = Color.DarkGray,
                    disabledBorderColor = Color.White,
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.White,
                    placeholderColor = Color.DarkGray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    coroutineScope.launch {
                        dataStoreHelper.saveUrl(url)
                    }
                    focusManager.clearFocus()
                })
            )
            if (!isTextFieldFocused){
                Button(onClick = {
                    coroutineScope.launch {
                        dataStoreHelper.saveUrl("")
                    }
                    url = ""
                }) {
                    Text(text = "Reset", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ManageContacts(navController: NavController){
    Scaffold (
        topBar = {
                 TopBar(navController, "Manage Contacts")
        },
        backgroundColor = Color.Black,
    ) { paddingValues ->
        Column (modifier = Modifier
            .padding(paddingValues)
            .padding(10.dp)){
            Text(text = "Total Contacts: 0", color = Color.White)
            Text(text = "Last Updated: Never", color = Color.White)
            Button(onClick = { /*TODO*/ }) {
                Text(text = "Update Contact")
            }
            Button(onClick = { /*TODO*/ }) {
                Text(text = "Clear Contacts")
            }
        }
    }
}

@Composable
fun ConfigureNotifications(navController: NavController){
    val context = LocalContext.current
    val dataStoreHelper = remember { DataStoreHandler(context) }
//    val savedUrl by dataStoreHelper.url.collectAsState(initial = "")
    val coroutineScope = rememberCoroutineScope()
    var allowedNotificationMapping by remember { mutableStateOf(mapOf<String, List<Boolean>>()) }
    LaunchedEffect(key1 = Unit) {
        allowedNotificationMapping = dataStoreHelper.getAppInfoMap()
    }
    Scaffold (
        topBar = {
                 TopBar(navController, "Configure Notifications")
        },
        backgroundColor = Color.Black,
    ) { paddingValues ->
        Column (modifier = Modifier
            .padding(paddingValues)
            .padding(10.dp)){
            allowedNotificationMapping.forEach { (appName, allowedNotifications) ->
                NotificationListItem(
                    appName,
                    notificationEnabled = allowedNotifications[0],
                    mediaNotificationEnabled = allowedNotifications[1],
                    onNotificationToggle = { newNotificationEnabled ->
                        val updatedMap = allowedNotificationMapping.toMutableMap().apply {
                            this[appName] = listOf(newNotificationEnabled, allowedNotifications[1])
                        }

                        allowedNotificationMapping = updatedMap
                        coroutineScope.launch {
                            dataStoreHelper.saveAppInfo(appName, newNotificationEnabled, allowedNotifications[1])  // Save updated state in DataStore
                        }

                    },
                    onMediaNotificationToggle = { newMediaNotificationEnabled ->
                        val updatedMap = allowedNotificationMapping.toMutableMap().apply {
                            this[appName] = listOf(allowedNotifications[0], newMediaNotificationEnabled)
                        }
                        allowedNotificationMapping = updatedMap
                        coroutineScope.launch {
                            dataStoreHelper.saveAppInfo(appName, allowedNotifications[0], newMediaNotificationEnabled)  // Save updated state in DataStore
                        }
                    }
                )
            }

        }
    }
}

@Composable
fun NotificationListItem(
    appName: String,
    notificationEnabled: Boolean,
    mediaNotificationEnabled: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    onMediaNotificationToggle: (Boolean) -> Unit
){
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 20.dp)
    ){
        Text(appName, color = Color.White)

        Row{
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = if (notificationEnabled) "Notification Enabled" else "Notification Disabled",
                tint = if (notificationEnabled) Color.White else Color.DarkGray,
                modifier = Modifier.clickable { onNotificationToggle(!notificationEnabled) } // Toggle notification state
            )

            Spacer(modifier = Modifier.padding(horizontal = 10.dp))

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = if (mediaNotificationEnabled) "Media Notification Enabled" else "Media Notification Disabled",
                tint = if (mediaNotificationEnabled) Color.White else Color.DarkGray,
                modifier = Modifier.clickable { onMediaNotificationToggle(!mediaNotificationEnabled) } // Toggle media notification state
            )
        }
    }
}
