package com.local.passover.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanning.getClient
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.local.passover.viewmodels.QRScannerState
import com.local.passover.viewmodels.QRScannerViewModel
import timber.log.Timber

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onNavigateUp: () -> Unit,
    viewModel: QRScannerViewModel = hiltViewModel()
){
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if(!hasPermission)permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(uiState) {
        if(uiState is QRScannerState.Success){
            Toast.makeText(context, "Connected and saved!", Toast.LENGTH_LONG).show()
            onNavigateUp()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = {Text("Scan QR", fontWeight = FontWeight.Bold, color= Color.White)})
    }) { paddingValues ->
        if(hasPermission){
            when(val state = uiState){
                is QRScannerState.Loading -> {
                    CircularProgressIndicator()
                }
                is QRScannerState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.resetScanner() }) {
                            Text("Retry")
                        }
                    }
                }
                is QRScannerState.Scanning -> {
                    SimpleQrScanner(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        isEnabled = uiState is QRScannerState.Scanning,
                        onQrFound =  { qrValue ->
                            Timber.tag("QRScanner").i( "QR found: $qrValue")
                            viewModel.processQRCode(qrValue)
                        }
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("What's Up!")
                    }
                }
            }

        } else{
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to scan QR codes.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant permission")
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun SimpleQrScanner(
    modifier: Modifier = Modifier,
    isEnabled: Boolean,
    onQrFound: (String) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()
                    val scanner = getClient(options)
                    var hasFoundCode = false

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                if(!isEnabled || hasFoundCode){
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage,
                                        imageProxy.imageInfo.rotationDegrees)

                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            barcodes.firstOrNull()?.rawValue?.let { value ->
                                                hasFoundCode = true
                                                onQrFound(value)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        ctx as LifecycleOwner,
                        cameraSelector,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = modifier) {
            val overlayColor = Color(0f, 0f, 0f, 0.6f)
            val rectWidth = size.width * 0.7f
            val rectHeight = size.height * 0.4f

            val left = (size.width - rectWidth) / 2
            val top = (size.height - rectHeight) / 2
            val rect = Rect(left, top, left + rectWidth, top + rectHeight)

            // dimmed background
            drawRect(color = overlayColor)

            // transparent rectangle
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                blendMode = BlendMode.Clear
            )
        }
        Text(
            "Alight the QR code inside the box",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom=32.dp)
        )
    }
}
