package com.example.apriltags.opencv

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Modern OpenCV Manager for Android using OpenCVLoader.initLocal()
 */
class OpenCVManager(private val context: Context) {

    companion object {
        private const val TAG = "OpenCVManager"
        private var isInitialized = false
    }

    /**
     * Initialize OpenCV using the modern initLocal() method
     * @param callback Called when initialization completes (true = success, false = failure)
     */
    fun initializeOpenCV(callback: (Boolean) -> Unit) {
        if (isInitialized) {
            Log.d(TAG, "OpenCV already initialized")
            callback(true)
            return
        }

        try {
            Log.d(TAG, "Initializing OpenCV with initLocal()...")

            val success = OpenCVLoader.initLocal()

            if (success) {
                Log.d(TAG, "OpenCV initialized successfully - Version: ${getOpenCVVersion()}")
                isInitialized = true
                callback(true)
            } else {
                Log.e(TAG, "OpenCV initialization failed")
                isInitialized = false
                callback(false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during OpenCV initialization", e)
            isInitialized = false
            callback(false)
        }
    }

    /**
     * Check if OpenCV is ready for use
     */
    fun isOpenCVReady(): Boolean = isInitialized

    /**
     * Get OpenCV version information
     */
    fun getOpenCVVersion(): String {
        return try {
            if (isInitialized) {
                org.opencv.core.Core.VERSION
            } else {
                "Not initialized"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting OpenCV version", e)
            "Unknown"
        }
    }

    /**
     * Enable OpenCV functionality (call in onResume)
     */
    fun enableView() {
        Log.d(TAG, "OpenCV view enabled")
        // Modern OpenCV doesn't require explicit view enabling
        // This method is kept for compatibility and future extensions
    }

    /**
     * Disable OpenCV functionality (call in onPause)
     */
    fun disableView() {
        Log.d(TAG, "OpenCV view disabled")
        // Modern OpenCV doesn't require explicit view disabling
        // This method is kept for compatibility and future extensions
    }

    /**
     * Reset OpenCV state (for troubleshooting)
     */
    fun reset() {
        Log.d(TAG, "Resetting OpenCV state")
        isInitialized = false
    }
}