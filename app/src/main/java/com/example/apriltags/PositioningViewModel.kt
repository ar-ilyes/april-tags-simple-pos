package com.example.apriltags

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.Mat
import com.example.apriltags.data.DetectedTag
import com.example.apriltags.data.CameraPosition
import com.example.apriltags.vision.ModernArucoDetector
import com.example.apriltags.postionning.RobustPositionCalculator
import com.example.apriltags.opencv.OpenCVManager

class PositioningViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PositioningViewModel"
    }

    private val openCVManager = OpenCVManager(application)
    private lateinit var arucoDetector: ModernArucoDetector
    private lateinit var positionCalculator: RobustPositionCalculator

    // LiveData for UI state
    private val _detectedTags = MutableLiveData<List<DetectedTag>>(emptyList())
    val detectedTags: LiveData<List<DetectedTag>> = _detectedTags

    private val _cameraPosition = MutableLiveData<CameraPosition?>()
    val cameraPosition: LiveData<CameraPosition?> = _cameraPosition

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _isOpenCVReady = MutableLiveData(false)
    val isOpenCVReady: LiveData<Boolean> = _isOpenCVReady

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        initializeOpenCV()
    }

    private fun initializeOpenCV() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing OpenCV...")

                // Initialize OpenCV using modern method
                openCVManager.initializeOpenCV { isSuccess ->
                    if (isSuccess) {
                        Log.d(TAG, "OpenCV initialized successfully")
                        initializeDetectors()
                        _isOpenCVReady.postValue(true)
                    } else {
                        Log.e(TAG, "Failed to initialize OpenCV")
                        _errorMessage.postValue("Failed to initialize OpenCV")
                        _isOpenCVReady.postValue(false)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during OpenCV initialization", e)
                _errorMessage.postValue("OpenCV initialization error: ${e.message}")
                _isOpenCVReady.postValue(false)
            }
        }
    }

    private fun initializeDetectors() {
        try {
            arucoDetector = ModernArucoDetector()
            positionCalculator = RobustPositionCalculator()
            Log.d(TAG, "ArUco detector and position calculator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing detectors", e)
            _errorMessage.postValue("Failed to initialize detectors: ${e.message}")
        }
    }

    /**
     * Process a camera frame to detect ArUco markers and calculate position
     */
    fun processFrame(frame: Mat) {
        if (!_isOpenCVReady.value!! || _isProcessing.value!!) {
            // Release the frame to prevent memory leak
            frame.release()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.postValue(true)

            try {
                Log.d(TAG, "Processing frame: ${frame.width()}x${frame.height()}")

                // Step 1: Detect ArUco markers
                val detectedTags = if (::arucoDetector.isInitialized) {
                    arucoDetector.detectTags(frame)
                } else {
                    Log.w(TAG, "ArUco detector not initialized")
                    emptyList()
                }

                _detectedTags.postValue(detectedTags)

                // Step 2: Calculate position if tags were detected
                if (detectedTags.isNotEmpty() && ::positionCalculator.isInitialized) {
                    val position = positionCalculator.calculatePosition(detectedTags)
                    _cameraPosition.postValue(position)

                    if (position != null) {
                        Log.d(TAG, "Position calculated: (${position.position.x}, ${position.position.y}, ${position.position.z}) accuracy=${position.accuracy}")
                    }
                } else if (detectedTags.isEmpty()) {
                    // Clear position if no tags detected
                    _cameraPosition.postValue(null)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
                _errorMessage.postValue("Frame processing error: ${e.message}")
            } finally {
                // Always release the frame to prevent memory leaks
                frame.release()
                _isProcessing.postValue(false)
            }
        }
    }

    /**
     * Reset the position tracking
     */
    fun resetPosition() {
        _cameraPosition.postValue(null)
        _detectedTags.postValue(emptyList())
        Log.d(TAG, "Position tracking reset")
    }

    /**
     * Clear any error messages
     */
    fun clearError() {
        _errorMessage.postValue(null)
    }

    /**
     * Get current detection statistics
     */
    fun getDetectionStats(): DetectionStats {
        val tags = _detectedTags.value ?: emptyList()
        val position = _cameraPosition.value

        return DetectionStats(
            tagsDetected = tags.size,
            knownTagsDetected = tags.count { /* check if tag is in TagManager */ true },
            hasPosition = position != null,
            accuracy = position?.accuracy ?: 0f,
            isTracking = _isProcessing.value ?: false
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared - cleaning up resources")

        try {
            if (::arucoDetector.isInitialized) {
                arucoDetector.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up ArUco detector", e)
        }
    }
}

/**
 * Data class for detection statistics
 */
data class DetectionStats(
    val tagsDetected: Int,
    val knownTagsDetected: Int,
    val hasPosition: Boolean,
    val accuracy: Float,
    val isTracking: Boolean
)