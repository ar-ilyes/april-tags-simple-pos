package com.example.apriltags.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WORKING CameraX implementation that actually shows video and processes frames
 * This is much more reliable than OpenCV's camera
 */
@Composable
fun WorkingCameraPreview(
    modifier: Modifier = Modifier,
    onFrameProcessed: (Mat) -> Unit,
    isOpenCVReady: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Log.d("WorkingCamera", "🎥 Starting WorkingCameraPreview - OpenCV ready: $isOpenCVReady")

    // Check permissions
    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission required")
        }
        return
    }

    if (!isOpenCVReady) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Initializing OpenCV...")
        }
        return
    }

    // Camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            Log.d("WorkingCamera", "🏭 Creating camera preview")

            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            // Start camera
            startCamera(ctx, lifecycleOwner, previewView, onFrameProcessed, cameraExecutor)

            previewView
        }
    )
}

private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFrameProcessed: (Mat) -> Unit,
    cameraExecutor: ExecutorService
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            Log.d("WorkingCamera", "🚀 Setting up camera...")

            val cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .build()

            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Image analysis for OpenCV processing
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var frameCount = 0
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                frameCount++

                if (frameCount % 30 == 0) {
                    Log.d("WorkingCamera", "✅ Processing frame #$frameCount")
                }

                try {
                    val mat = imageProxyToMat(imageProxy)
                    if (mat != null) {
                        onFrameProcessed(mat)
                    }
                } catch (e: Exception) {
                    Log.e("WorkingCamera", "❌ Error processing frame", e)
                } finally {
                    imageProxy.close()
                }
            }

            // Camera selector (back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind use cases to lifecycle
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d("WorkingCamera", "✅ Camera started successfully!")

        } catch (e: Exception) {
            Log.e("WorkingCamera", "❌ Error starting camera", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Convert CameraX ImageProxy to OpenCV Mat
 */
private fun imageProxyToMat(imageProxy: ImageProxy): Mat? {
    return try {
        // Get the YUV_420_888 image
        val planes = imageProxy.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // Copy UV planes
        val uvPixelStride = planes[1].pixelStride
        if (uvPixelStride == 1) {
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)
        } else {
            // Handle interleaved UV
            val uvBuffer = ByteArray(uSize + vSize)
            uBuffer.get(uvBuffer, 0, uSize)
            vBuffer.get(uvBuffer, uSize, vSize)

            // Deinterleave
            for (i in 0 until uSize step uvPixelStride) {
                nv21[ySize + i / uvPixelStride] = uvBuffer[i]
            }
            for (i in 0 until vSize step uvPixelStride) {
                nv21[ySize + uSize / uvPixelStride + i / uvPixelStride] = uvBuffer[uSize + i]
            }
        }

        // Create OpenCV Mat from YUV data
        val yuvMat = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        // Convert YUV to RGB
        val rgbMat = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(yuvMat, rgbMat, org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_I420)

        yuvMat.release()
        rgbMat

    } catch (e: Exception) {
        Log.e("WorkingCamera", "Error converting ImageProxy to Mat", e)
        null
    }
}