package com.example.bleexample.screens

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bleexample.models.BLEScanViewModel
import com.example.bleexample.models.ChatServer
import com.example.bleexample.utils.askPermissions
import com.example.bleexample.utils.askSinglePermission
import com.example.bleexample.utils.requiredPermissionsInitialClient

const val TAG = "HomeScreen"
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun Home(viewModel: BLEScanViewModel){
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted:Boolean ->
        if(isGranted){
            viewModel.startScan()
        }
        else{
            Log.i(TAG, "Location permission not granted")
            Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
        }
    }
    fun extraLocationPermissionRequest(){
        askSinglePermission(locationPermissionLauncher,android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,context){
            viewModel.startScan()
        }
    }
    val multiplePermissionLauncher =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                Log.i(TAG, "Launcher result: $permissions")
                if (permissions.containsValue(false)) {
                    Log.i(TAG, "At least one of the permissions was not granted.")
                    Toast.makeText(
                        context,
                        "At least one of the permissions was not granted. Please do so manually",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //do something
                    viewModel.startScan()
                }
            }
        } else {
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                Log.i(TAG, "Launcher result: $permissions")
                if(permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false)){
                    Toast.makeText(
                        context,
                        "Please select 'Allow all the time'",
                        Toast.LENGTH_SHORT
                    ).show()
                    extraLocationPermissionRequest()
                }
                else if (permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    //permission for location was granted.
                    //we direct the user to select "Allow all the time option"
                    Toast.makeText(
                        context,
                        "Please select 'Allow all the time'",
                        Toast.LENGTH_SHORT
                    ).show()
                    extraLocationPermissionRequest()
                } else {
                    Toast.makeText(
                        context,
                        "Location permission was not granted. Please do so manually",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    val scanResults = viewModel.scanResults
    val isScanning = viewModel.scanningForDevice
    val isScanFail = viewModel.isScanFailed
    val selectedDevice = viewModel.selectedDevice


    Column (
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ){
        Text("Current text to send: ${viewModel.textValue}")
        Button(onClick={
            askPermissions(multiplePermissionLauncher, requiredPermissionsInitialClient,context){
                Log.d(TAG, "Permission granted")
            }
        }){
            Text(text = "Ask for permissions")
        }
        Text(viewModel.isDeviceConnected.toString())
        if(viewModel.isDeviceConnected.value.not()){
            Button(onClick = {
                Log.d(TAG, "Start Scan button clicked")
                askPermissions(multiplePermissionLauncher, requiredPermissionsInitialClient,context){
                    viewModel.startScan()
                }
            }){
                Text(text = "Start Scan")
            }
            if(isScanning.value){
                Text(text = "Scanning...")
            }
            if(scanResults.isNotEmpty()){
                Text(text = "Scan Results:")
                scanResults.forEach {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.clickable {
                            viewModel.selectDevice(it.value)
                        }.background(color = Color.LightGray)
                            .padding(15.dp)
                    ){
                        Text(text = "${it.key} - ${it.value.name?: "Unnamed"}", modifier = Modifier.background(color = if (selectedDevice == it.value) Color.Green else Color.Black))
                    }
                }
            }
        }
        else{
            Text(text = "Connected")

            viewModel.messages.value.forEach { message ->
                Text(text = message)
            }
            TextField(value = viewModel.textValue, onValueChange = {newValue->
                viewModel.updateTextValue(newValue)
            })
            Button(onClick = {
                ChatServer.sendMessage(viewModel.textValue)
                viewModel.updateTextValue("")
            }){
                Text(text = "Send")
            }
        }

    }
}