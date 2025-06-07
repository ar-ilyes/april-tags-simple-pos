package com.example.apriltags


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apriltags.ui.CameraPreview

@Composable
fun PositioningScreen(viewModel: PositioningViewModel) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    val detectedTags by viewModel.detectedTags.collectAsState()
    val isOpenCVReady by viewModel.isOpenCVReady.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        CameraPreview(
            onFrameAnalyzed = { mat ->
                viewModel.processFrame(mat)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with position information
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Indoor Positioning",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isOpenCVReady) "OpenCV Ready" else "Initializing OpenCV...",
                        color = if (isOpenCVReady) Color.Green else Color.Yellow,
                        fontSize = 12.sp
                    )

                    Text(
                        text = "Tags Detected: ${detectedTags.size}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Position Card
            currentPosition?.let { position ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Current Position",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "X: ${"%.2f".format(position.position.x)}m",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Y: ${"%.2f".format(position.position.y)}m",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Z: ${"%.2f".format(position.position.z)}m",
                            color = Color.White,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Accuracy: ${(position.accuracy * 100).toInt()}%",
                            color = when {
                                position.accuracy > 0.8f -> Color.Green
                                position.accuracy > 0.5f -> Color.Yellow
                                else -> Color.Red
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}