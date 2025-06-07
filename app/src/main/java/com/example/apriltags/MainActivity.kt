package com.example.apriltags

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.apriltags.ui.theme.AprilTagsTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    companion object {
        init {
            try {
                if (OpenCVLoader.initLocal()) {
                    Log.d("OpenCV", "OpenCV initialization succeeded")
                } else {
                    Log.d("OpenCV", "OpenCV initialization failed")
                }
            } catch (e: Exception) {
                Log.e("OpenCV", "OpenCV initialization error", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprilTagsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IndoorPositioningApp()
                }
            }
        }
    }
}