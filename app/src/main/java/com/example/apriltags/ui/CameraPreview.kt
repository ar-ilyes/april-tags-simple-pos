package com.example.apriltags.ui

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.CvType
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onFrameAnalyzed: (Mat) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(executor) { imageProxy ->
                            processImageProxy(imageProxy, onFrameAnalyzed)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

private fun processImageProxy(imageProxy: ImageProxy, onFrameAnalyzed: (Mat) -> Unit) {
    try {
        // Add logging to see if this function is called
        android.util.Log.d("CameraPreview", "Processing frame: ${imageProxy.width}x${imageProxy.height}")

        // Convert ImageProxy to OpenCV Mat
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        android.util.Log.d("CameraPreview", "Buffer sizes - Y: $ySize, U: $uSize, V: $vSize")

        // Validate buffer sizes
        if (ySize == 0 || uSize == 0 || vSize == 0) {
            android.util.Log.w("CameraPreview", "Empty buffers detected")
            return
        }

        // FIXED: Correct NV21 conversion
        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // FIXED: For NV21, V and U should be interleaved after Y
        // Copy U and V planes in the correct order for NV21
        val uvPixelStride = imageProxy.planes[1].pixelStride
        if (uvPixelStride == 1) {
            // Planes are already interleaved (this is rare)
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)
        } else {
            // Need to interleave U and V manually
            val uvStart = ySize
            val uBytes = ByteArray(uSize)
            val vBytes = ByteArray(vSize)
            uBuffer.get(uBytes)
            vBuffer.get(vBytes)

            // Interleave V and U for NV21 format
            for (i in 0 until minOf(uSize, vSize)) {
                nv21[uvStart + i * 2] = vBytes[i]     // V comes first in NV21
                nv21[uvStart + i * 2 + 1] = uBytes[i] // U comes second
            }
        }

        // Create Mat from NV21 data
        val yuvMat = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        android.util.Log.d("CameraPreview", "YUV Mat created: ${yuvMat.width()}x${yuvMat.height()}, empty: ${yuvMat.empty()}")

        // Validate the Mat before processing
        if (!yuvMat.empty()) {
            val rgbMat = Mat()
            org.opencv.imgproc.Imgproc.cvtColor(yuvMat, rgbMat, org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_NV21)

            android.util.Log.d("CameraPreview", "RGB Mat created: ${rgbMat.width()}x${rgbMat.height()}, empty: ${rgbMat.empty()}")

            if (!rgbMat.empty()) {
                android.util.Log.d("CameraPreview", "Calling onFrameAnalyzed")

                // FIX: Clone the Mat to avoid thread issues
                val clonedMat = rgbMat.clone()
                android.util.Log.d("CameraPreview", "Cloned Mat: ${clonedMat.width()}x${clonedMat.height()}, empty: ${clonedMat.empty()}")

                onFrameAnalyzed(clonedMat)

                // Note: Don't release clonedMat here - let the ViewModel handle it
            } else {
                android.util.Log.e("CameraPreview", "RGB Mat is empty after conversion")
            }

            rgbMat.release()
        } else {
            android.util.Log.e("CameraPreview", "YUV Mat is empty")
        }

        yuvMat.release()

    } catch (e: Exception) {
        android.util.Log.e("CameraPreview", "Error processing frame", e)
        e.printStackTrace()
    } finally {
        imageProxy.close()
    }
}

