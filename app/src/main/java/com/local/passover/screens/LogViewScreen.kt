package com.local.passover.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.passover.viewmodels.LogViewerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateUp: () -> Unit,
    viewModel: LogViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val logLines by viewModel.logContent.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()

    LaunchedEffect(key1 = true) {
        viewModel.loadLogs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.zoomIn() }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Zoom In")
                    }
                    IconButton(onClick = { viewModel.zoomOut() }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Zoom Out")
                    }
                    IconButton(onClick = { viewModel.saveLogsToDownloads(context) }) {
                        Icon(Icons.Default.Done, contentDescription = "Save Logs")
                    }
                    IconButton(onClick = { viewModel.clearLogs(context) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                    }
                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.primary,
//                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
//                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
//                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
//                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalScroll(rememberScrollState()) // For long lines
        ) {
            itemsIndexed(logLines) { index, line ->
                LogLine(
                    lineNumber = index + 1,
                    logText = line,
                    fontSize = fontSize
                )
            }
        }
    }
}

@Composable
private fun LogLine(lineNumber: Int, logText: String, fontSize: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (lineNumber % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 2.dp)
    ) {
        // Line Number
        Text(
            text = lineNumber.toString().padStart(4, ' '),
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp
        )

        // Log Content
        Text(
            text = highlightLogLine(logText),
            modifier = Modifier.padding(end = 8.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun highlightLogLine(logLine: String): AnnotatedString {
    return buildAnnotatedString {
        withStyle(style = SpanStyle(color = Color.Unspecified)) {
            append(logLine)
        }

        addStyleForLogLevel(" V/", logLine, Color.Gray)
        addStyleForLogLevel(" D/", logLine, Color(0xFF6495ED)) // Cornflower Blue
        addStyleForLogLevel(" I/", logLine, Color(0xFF3CB371)) // Medium Sea Green
        addStyleForLogLevel(" W/", logLine, Color(0xFFFFA500)) // Orange
        addStyleForLogLevel(" E/", logLine, Color(0xFFDC143C)) // Crimson
        addStyleForLogLevel(" A/", logLine, Color.Magenta)
    }
}


private fun AnnotatedString.Builder.addStyleForLogLevel(
    level: String,
    text: String,
    color: Color
) {
    val startIndex = text.indexOf(level)
    if (startIndex != -1) {
        addStyle(
            style = SpanStyle(color = color),
            start = startIndex,
            end = startIndex + level.length
        )
    }
}
