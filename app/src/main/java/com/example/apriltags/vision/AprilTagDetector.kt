package com.example.apriltags.vision

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Dictionary
import org.opencv.objdetect.Objdetect
import com.example.apriltags.data.DetectedTag
import com.example.apriltags.data.Point2D

class ModernArucoDetector {

    companion object {
        private const val TAG = "ModernArucoDetector"
    }

    private val dictionary: Dictionary
    private val detectorParams: DetectorParameters
    private val arucoDetector: ArucoDetector

    init {
        try {
            // Create dictionary - DICT_6X6_250 is similar to AprilTag and very reliable
            dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_250)

            // Create and configure detector parameters for optimal mobile performance
            detectorParams = DetectorParameters().apply {
                // Adaptive thresholding parameters
                set_adaptiveThreshWinSizeMin(3)
                set_adaptiveThreshWinSizeMax(23)
                set_adaptiveThreshWinSizeStep(10)
                set_adaptiveThreshConstant(7.0)

                // Corner refinement for sub-pixel accuracy
                set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
                set_cornerRefinementWinSize(5)
                set_cornerRefinementMaxIterations(30)
                set_cornerRefinementMinAccuracy(0.1)

                // Contour filtering parameters
                set_minMarkerPerimeterRate(0.03)
                set_maxMarkerPerimeterRate(4.0)
                set_polygonalApproxAccuracyRate(0.03)
                set_minCornerDistanceRate(0.05)
                set_minDistanceToBorder(3)

                // Perspective removal parameters
                set_markerBorderBits(1)
                set_perspectiveRemovePixelPerCell(4)
                set_perspectiveRemoveIgnoredMarginPerCell(0.13)

                // Error correction parameters
                set_maxErroneousBitsInBorderRate(0.35)
                set_errorCorrectionRate(0.6)

                // Detection reliability
                set_detectInvertedMarker(false) // Set true if you have inverted markers
            }

            // Create the ArUco detector with our parameters
            arucoDetector = ArucoDetector(dictionary, detectorParams)

            Log.d(TAG, "Modern ArUco detector initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ArUco detector", e)
            throw e
        }
    }

    fun detectTags(inputMat: Mat): List<DetectedTag> {
        Log.d(TAG, "Starting ArUco detection on ${inputMat.width()}x${inputMat.height()} image")

        if (inputMat.empty()) {
            Log.e(TAG, "Input Mat is empty!")
            return emptyList()
        }

        val detectedTags = mutableListOf<DetectedTag>()
        val grayMat = Mat()

        try {
            // Step 1: Preprocess image for optimal detection
            preprocessImage(inputMat, grayMat)

            if (grayMat.empty()) {
                Log.e(TAG, "Preprocessed image is empty!")
                return emptyList()
            }

            // Step 2: Detect ArUco markers
            val corners = mutableListOf<Mat>()
            val ids = Mat()
            val rejectedCandidates = mutableListOf<Mat>()

            arucoDetector.detectMarkers(grayMat, corners, ids, rejectedCandidates)

            Log.d(TAG, "ArUco detection found ${corners.size} markers")

            // Step 3: Process detected markers
            if (corners.isNotEmpty() && !ids.empty()) {
                processDetectedMarkers(corners, ids, detectedTags)
            }

            // Step 4: Cleanup
            ids.release()
            corners.forEach { it.release() }
            rejectedCandidates.forEach { it.release() }

        } catch (e: Exception) {
            Log.e(TAG, "Error during ArUco detection", e)
            e.printStackTrace()
        } finally {
            grayMat.release()
        }

        Log.d(TAG, "Detection complete. Found ${detectedTags.size} valid ArUco markers")
        return detectedTags
    }

    private fun preprocessImage(inputMat: Mat, grayMat: Mat) {
        try {
            // Convert to grayscale if needed
            if (inputMat.channels() > 1) {
                Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            } else {
                inputMat.copyTo(grayMat)
            }

            // Apply histogram equalization for better contrast
            val equalizedMat = Mat()
            Imgproc.equalizeHist(grayMat, equalizedMat)

            // Blend original and equalized (70% original, 30% equalized)
            Core.addWeighted(grayMat, 0.7, equalizedMat, 0.3, 0.0, grayMat)

            // Optional: Apply slight Gaussian blur to reduce noise
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(3.0, 3.0), 0.0)
            blurredMat.copyTo(grayMat)

            // Cleanup
            equalizedMat.release()
            blurredMat.release()

        } catch (e: Exception) {
            Log.w(TAG, "Image preprocessing failed, using original", e)
            if (inputMat.channels() > 1) {
                Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            } else {
                inputMat.copyTo(grayMat)
            }
        }
    }

    private fun processDetectedMarkers(
        corners: List<Mat>,
        ids: Mat,
        detectedTags: MutableList<DetectedTag>
    ) {
        try {
            // Extract marker IDs
            val idsArray = IntArray(ids.rows())
            ids.get(0, 0, idsArray)

            for (i in corners.indices) {
                try {
                    val markerId = idsArray[i]
                    val cornerMat = corners[i]

                    // Extract corner points from the Mat
                    val cornerPoints = extractCornerPoints(cornerMat)

                    if (cornerPoints.size == 4) {
                        // Validate marker quality
                        if (isMarkerValid(cornerPoints)) {
                            val center = calculateCenter(cornerPoints)

                            val detectedTag = DetectedTag(
                                id = markerId,
                                corners = cornerPoints,
                                center = center
                            )

                            detectedTags.add(detectedTag)
                            Log.d(TAG, "Detected ArUco marker ID: $markerId at center: $center")
                        } else {
                            Log.d(TAG, "Marker $markerId failed validation")
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Error processing marker $i", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing detected markers", e)
        }
    }

    private fun extractCornerPoints(cornerMat: Mat): List<Point2D> {
        val corners = mutableListOf<Point2D>()

        try {
            // ArUco corners are stored as 1x4x2 Mat (1 marker, 4 corners, x,y coordinates)
            if (cornerMat.rows() == 1 && cornerMat.cols() == 4 && cornerMat.channels() == 2) {
                val data = FloatArray(8) // 4 corners * 2 coordinates
                cornerMat.get(0, 0, data)

                for (i in 0 until 4) {
                    val x = data[i * 2]
                    val y = data[i * 2 + 1]
                    corners.add(Point2D(x, y))
                }
            } else {
                Log.w(TAG, "Unexpected corner matrix format: ${cornerMat.rows()}x${cornerMat.cols()}x${cornerMat.channels()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting corner points", e)
        }

        return corners
    }

    private fun isMarkerValid(corners: List<Point2D>): Boolean {
        if (corners.size != 4) return false

        try {
            // Check 1: Minimum size
            val minX = corners.minOf { it.x }
            val maxX = corners.maxOf { it.x }
            val minY = corners.minOf { it.y }
            val maxY = corners.maxOf { it.y }

            val width = maxX - minX
            val height = maxY - minY
            val area = width * height

            if (area < 400) { // Minimum 20x20 pixels
                Log.d(TAG, "Marker too small: area=$area")
                return false
            }

            // Check 2: Aspect ratio (should be roughly square)
            val aspectRatio = width / height
            if (aspectRatio < 0.5 || aspectRatio > 2.0) {
                Log.d(TAG, "Invalid aspect ratio: $aspectRatio")
                return false
            }

            // Check 3: Convexity
            if (!isConvexQuadrilateral(corners)) {
                Log.d(TAG, "Marker is not convex")
                return false
            }

            return true

        } catch (e: Exception) {
            Log.w(TAG, "Error validating marker", e)
            return false
        }
    }

    private fun isConvexQuadrilateral(corners: List<Point2D>): Boolean {
        // Check if all cross products have the same sign (indicating convexity)
        var previousSign = 0

        for (i in corners.indices) {
            val p1 = corners[i]
            val p2 = corners[(i + 1) % 4]
            val p3 = corners[(i + 2) % 4]

            val cross = crossProduct(p1, p2, p3)
            val currentSign = when {
                cross > 0 -> 1
                cross < 0 -> -1
                else -> 0
            }

            if (previousSign == 0) {
                previousSign = currentSign
            } else if (currentSign != 0 && currentSign != previousSign) {
                return false // Signs differ, not convex
            }
        }

        return true
    }

    private fun crossProduct(p1: Point2D, p2: Point2D, p3: Point2D): Float {
        val v1x = p2.x - p1.x
        val v1y = p2.y - p1.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        return v1x * v2y - v1y * v2x
    }

    private fun calculateCenter(corners: List<Point2D>): Point2D {
        val centerX = corners.map { it.x }.average().toFloat()
        val centerY = corners.map { it.y }.average().toFloat()
        return Point2D(centerX, centerY)
    }

    fun release() {
        // Cleanup resources if needed
        try {
            // Note: Dictionary and DetectorParameters in modern OpenCV don't have release() methods
            // They are managed automatically by the JVM garbage collector
            Log.d(TAG, "ArUco detector resources cleaned up")
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
}