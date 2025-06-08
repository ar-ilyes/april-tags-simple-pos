package com.example.apriltags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.apriltags.data.CameraPosition
import com.example.apriltags.data.DetectedTag
import com.example.apriltags.camera.WorkingCameraPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositioningScreen(
    viewModel: PositioningViewModel = viewModel()
) {
    // Observe ViewModel state
    val currentPosition by viewModel.cameraPosition.observeAsState()
    val detectedTags by viewModel.detectedTags.observeAsState(emptyList())
    val isProcessing by viewModel.isProcessing.observeAsState(false)
    val isOpenCVReady by viewModel.isOpenCVReady.observeAsState(false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isOpenCVReady) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Indoor Positioning System",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isOpenCVReady) "System Ready" else "Initializing...",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }

        // Camera Feed
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isOpenCVReady) {
                    WorkingCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameProcessed = { frame ->
                            viewModel.processFrame(frame)
                        },
                        isOpenCVReady = isOpenCVReady
                    )

                    // Processing overlay
                    if (isProcessing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Black.copy(alpha = 0.7f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Processing...",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Initialization placeholder
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Initializing Camera...",
                            color = Color.Gray
                        )
                        Text(
                            text = "Please wait",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Current Position Display
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Current Position",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (currentPosition != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("X: ${String.format("%.2f", currentPosition!!.position.x)}m")
                        Text("Y: ${String.format("%.2f", currentPosition!!.position.y)}m")
                        Text("Z: ${String.format("%.2f", currentPosition!!.position.z)}m")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Orientation: ${String.format("%.1f", Math.toDegrees(currentPosition!!.orientation.toDouble()))}°")

                        val accuracyColor = when {
                            currentPosition!!.accuracy > 0.8f -> Color(0xFF4CAF50)
                            currentPosition!!.accuracy > 0.6f -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }

                        Text(
                            text = "Accuracy: ${String.format("%.1f", currentPosition!!.accuracy * 100)}%",
                            color = accuracyColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No position data available",
                        color = Color.Gray
                    )
                }
            }
        }

        // Detected Tags
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Detected ArUco Tags (${detectedTags.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (detectedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detectedTags) { tag ->
                            TagInfoCard(tag = tag)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No tags detected",
                        color = Color.Gray
                    )
                }
            }
        }

        // System Stats
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "System Information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                SystemInfoRow("OpenCV Status", if (isOpenCVReady) "Ready" else "Loading")
                SystemInfoRow("Detection Engine", "Modern ArUco Detector")
                SystemInfoRow("Position Algorithm", "PnP + Kalman Filter")
                SystemInfoRow("Tags in Database", "10 reference points")
            }
        }
    }
}

@Composable
private fun TagInfoCard(tag: DetectedTag) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tag ID: ${tag.id}",
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Center: (${String.format("%.0f", tag.center.x)}, ${String.format("%.0f", tag.center.y)})",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Corners: ${tag.corners.size} detected",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}