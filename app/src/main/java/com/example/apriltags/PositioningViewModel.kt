package com.example.apriltags

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apriltags.data.CameraPosition
import com.example.apriltags.data.DetectedTag
import com.example.apriltags.postionning.PositionCalculator
import com.example.apriltags.vision.AprilTagDetector
import com.example.apriltags.vision.OpenCVManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencv.core.Mat

class PositioningViewModel(application: Application) : AndroidViewModel(application) {

    private val openCVManager = OpenCVManager(application)
    private val aprilTagDetector = AprilTagDetector()
    private val positionCalculator = PositionCalculator()

    private val _currentPosition = MutableStateFlow<CameraPosition?>(null)
    val currentPosition: StateFlow<CameraPosition?> = _currentPosition.asStateFlow()

    private val _detectedTags = MutableStateFlow<List<DetectedTag>>(emptyList())
    val detectedTags: StateFlow<List<DetectedTag>> = _detectedTags.asStateFlow()

    private val _isOpenCVReady = MutableStateFlow(false)
    val isOpenCVReady: StateFlow<Boolean> = _isOpenCVReady.asStateFlow()

    init {
        initializeOpenCV()
    }

    private fun initializeOpenCV() {
        viewModelScope.launch {
            openCVManager.initialize {
                _isOpenCVReady.value = true
            }
        }
    }

    fun processFrame(mat: Mat) {
        android.util.Log.d("PositioningViewModel", "processFrame called - Mat: ${mat.width()}x${mat.height()}")
        android.util.Log.d("PositioningViewModel", "OpenCV ready: ${_isOpenCVReady.value}")

        if (!_isOpenCVReady.value) {
            android.util.Log.w("PositioningViewModel", "OpenCV not ready, releasing Mat")
            mat.release() // Clean up
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("PositioningViewModel", "Starting tag detection...")
                val detectedTags = aprilTagDetector.detectTags(mat)
                android.util.Log.d("PositioningViewModel", "Detection completed - found ${detectedTags.size} tags")
                _detectedTags.value = detectedTags

                val position = positionCalculator.calculatePosition(detectedTags)
                _currentPosition.value = position
                android.util.Log.d("PositioningViewModel", "Position calculated: $position")
            } catch (e: Exception) {
                android.util.Log.e("PositioningViewModel", "Error in processFrame", e)
                e.printStackTrace()
            } finally {
                // FIX: Always release the Mat when done
                mat.release()
            }
        }
    }
}
