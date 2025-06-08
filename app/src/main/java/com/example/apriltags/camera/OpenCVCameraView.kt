package com.example.apriltags.camera

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.core.Mat

/**
 * Working OpenCV Camera View that actually captures frames
 */
class OpenCVCameraView : JavaCameraView, CameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        private const val TAG = "OpenCVCameraView"
    }

    private var frameProcessor: ((Mat) -> Unit)? = null
    private var isProcessingEnabled = true
    private var frameCount = 0

    // Constructor for Compose (no AttributeSet)
    constructor(context: Context) : super(context, CAMERA_ID_BACK) {
        Log.d(TAG, "Creating OpenCVCameraView with context only")
        initialize()
    }

    // Constructor for XML inflation (with AttributeSet)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        Log.d(TAG, "Creating OpenCVCameraView with context and attrs")
        initialize()
    }

    private fun initialize() {
        try {
            Log.d(TAG, "Initializing OpenCVCameraView...")

            // CRITICAL: Set the listener FIRST
            setCvCameraViewListener(this)

            // Configure camera settings
            setCameraIndex(CAMERA_ID_BACK)
            setMaxFrameSize(640, 480)
            enableFpsMeter()

            Log.d(TAG, "OpenCVCameraView initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenCVCameraView", e)
        }
    }

    /**
     * Set the frame processor callback
     */
    fun setFrameProcessor(processor: (Mat) -> Unit) {
        frameProcessor = processor
        Log.d(TAG, "Frame processor set")
    }

    /**
     * Enable or disable frame processing
     */
    fun setProcessingEnabled(enabled: Boolean) {
        isProcessingEnabled = enabled
        Log.d(TAG, "Processing enabled: $enabled")
    }

    // CameraBridgeViewBase.CvCameraViewListener2 implementation
    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.d(TAG, "✅ Camera view started: ${width}x${height}")
        frameCount = 0

        // Check if camera is really working after a delay
        postDelayed({
            Log.d(TAG, "Camera status check - Frames received: $frameCount")
            if (frameCount == 0) {
                Log.e(TAG, "❌ No frames received! Camera may not be working properly")
            }
        }, 3000)
    }

    override fun onCameraViewStopped() {
        Log.d(TAG, "Camera view stopped - Total frames: $frameCount")
        frameCount = 0
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        frameCount++

        if (frameCount % 30 == 0) { // Log every 30 frames (about 1 second)
            Log.d(TAG, "✅ Camera frame received #$frameCount: ${inputFrame.rgba().width()}x${inputFrame.rgba().height()}")
        }

        val frame = inputFrame.rgba() // Get RGBA frame

        // Process frame for ArUco detection if enabled
        if (isProcessingEnabled && frameProcessor != null) {
            try {
                // Create a copy for processing (don't block camera thread)
                val frameClone = frame.clone()

                // Process asynchronously
                post {
                    frameProcessor?.invoke(frameClone)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing camera frame", e)
            }
        }

        return frame // Return original frame for display
    }

    // Surface lifecycle callbacks
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "🔵 Surface created - camera can start now")
        super.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "🔵 Surface changed: ${width}x${height}, format: $format")
        super.surfaceChanged(holder, format, width, height)

        // Make sure camera starts when surface is ready
        if (!isEnabled) {
            Log.d(TAG, "Surface ready - enabling camera now...")
            enableView()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "🔴 Surface destroyed - stopping camera")
        super.surfaceDestroyed(holder)
    }

    /**
     * Start camera safely - only if surface is ready
     */
    fun startCameraSafely() {
        try {
            Log.d(TAG, "🚀 Starting camera...")

            // Check if surface is ready
            val hasValidSurface = holder?.surface?.isValid ?: false
            Log.d(TAG, "Surface valid: $hasValidSurface")
            Log.d(TAG, "Camera enabled: $isEnabled")

            if (!hasValidSurface) {
                Log.w(TAG, "⚠️ Surface not ready, waiting for surface creation...")
                return
            }

            if (!isEnabled) {
                Log.d(TAG, "Enabling camera view...")
                enableView()
            } else {
                Log.d(TAG, "Camera already enabled")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting camera", e)
        }
    }

    /**
     * Stop camera safely
     */
    fun stopCameraSafely() {
        try {
            Log.d(TAG, "🛑 Stopping camera...")
            if (isEnabled) {
                disableView()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow called")
        stopCameraSafely()
        super.onDetachedFromWindow()
    }
}