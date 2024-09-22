package com.example.bleexample.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Settings(){
    Column {
        Text("Settings", style = TextStyle(fontSize = 20.sp))
        ListItem {
            Text("Export Contacts")
        }
        ListItem {
            Text("Configure Webhook")
        }
        ListItem {
            Text("Export notification Apps")
        }
    }
}

// on click on configure webhook, a text field should appear with a button to save, save the webhook url to shared preferences

@Composable
fun WebhookField(){
    Text("Webhook URL")
    TextField(value = "https://webhook.site/xxxxxxxxxxxxxxxxx", onValueChange = {})
}

//
//
//
//@Composable
//fun SettingsPage(navController: NavController, context: Context) {
//    val sharedPreferencesHelper = remember { SharedPreferencesHelper(context) }
//    val urlText = remember { mutableStateOf("") }
//    val coroutineScope = rememberCoroutineScope()
//
//    LaunchedEffect(Unit) {
//        urlText.value = sharedPreferencesHelper.getUrl() ?: ""
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Settings") },
//                navigationIcon = {
//                    IconButton(onClick = { navController.popBackStack() }) {
//                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
//                    }
//                }
//            )
//        }
//    ) { paddingValues ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(paddingValues)
//                .padding(16.dp)
//        ) {
//            OutlinedTextField(
//                value = urlText.value,
//                onValueChange = { urlText.value = it },
//                label = { Text("Enter URL") },
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            Button(
//                onClick = {
//                    sharedPreferencesHelper.saveUrl(urlText.value)
//                },
//                modifier = Modifier.padding(top = 16.dp)
//            ) {
//                Text("Save URL")
//            }
//
//            Button(
//                onClick = {
//                    coroutineScope.launch {
//                        importContacts(context, sharedPreferencesHelper)
//                    }
//                },
//                modifier = Modifier.padding(top = 16.dp)
//            ) {
//                Text("Import Contacts")
//            }
//        }
//    }
//}
