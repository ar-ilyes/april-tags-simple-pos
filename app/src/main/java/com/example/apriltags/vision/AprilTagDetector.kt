package com.example.apriltags.vision

import com.example.apriltags.data.DetectedTag
import com.example.apriltags.data.Point2D
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector
import kotlin.math.*

class AprilTagDetector {

    fun detectTags(inputMat: Mat): List<DetectedTag> {
        android.util.Log.d("AprilTagDetector", "detectTags called - input: ${inputMat.width()}x${inputMat.height()}")

        if (inputMat.empty()) {
            android.util.Log.e("AprilTagDetector", "Input Mat is empty!")
            return emptyList()
        }

        val grayMat = Mat()
        val detectedTags = mutableListOf<DetectedTag>()

        try {
            // Convert to grayscale
            if (inputMat.channels() > 1) {
                Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            } else {
                inputMat.copyTo(grayMat)
            }

            if (grayMat.empty()) {
                return emptyList()
            }

            // Detect AprilTag-like square patterns
            detectAprilTagPatterns(grayMat, detectedTags)

        } catch (e: Exception) {
            android.util.Log.e("AprilTagDetector", "Error in detectTags", e)
            e.printStackTrace()
        } finally {
            grayMat.release()
        }

        android.util.Log.d("AprilTagDetector", "Returning ${detectedTags.size} detected tags")
        return detectedTags
    }

    private fun detectAprilTagPatterns(grayMat: Mat, detectedTags: MutableList<DetectedTag>) {
        try {
            // Step 1: Adaptive thresholding for better edge detection
            val threshMat = Mat()
            Imgproc.adaptiveThreshold(
                grayMat, threshMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 21, 5.0
            )

            // Step 2: Find contours - FIXED TYPES
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                threshMat, contours, hierarchy,
                Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE
            )

            android.util.Log.d("AprilTagDetector", "Found ${contours.size} contours")

            // Step 3: Filter and analyze contours
            for (i in contours.indices) {
                val contour = contours[i]
                val area = Imgproc.contourArea(contour)

                // Filter by area (adjust based on expected tag size)
                if (area > 2000 && area < 100000) {

                    // Step 4: Approximate contour to polygon - FIXED
                    val approx = MatOfPoint2f()
                    val contour2f = MatOfPoint2f()
                    contour.convertTo(contour2f, CvType.CV_32FC2)

                    val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

                    // Step 5: Check if it's a quadrilateral
                    val points = approx.toArray()
                    if (points.size == 4) {

                        if (isValidAprilTagShape(points, area)) {
                            val corners = points.map { Point2D(it.x.toFloat(), it.y.toFloat()) }
                            val center = Point2D(
                                corners.map { it.x }.average().toFloat(),
                                corners.map { it.y }.average().toFloat()
                            )

                            // Step 6: Extract and decode the tag ID
                            val tagId = extractTagId(grayMat, corners)

                            if (tagId != -1) {
                                detectedTags.add(DetectedTag(tagId, corners, center))
                                android.util.Log.d("AprilTagDetector", "Detected AprilTag ID: $tagId at center: $center")
                            }
                        }
                    }

                    // Cleanup
                    approx.release()
                    contour2f.release()
                }
            }

            // Cleanup
            threshMat.release()
            hierarchy.release()
            contours.forEach { it.release() }

        } catch (e: Exception) {
            android.util.Log.e("AprilTagDetector", "Error in detectAprilTagPatterns", e)
            e.printStackTrace()
        }
    }

    private fun isValidAprilTagShape(points: Array<org.opencv.core.Point>, area: Double): Boolean {
        if (points.size != 4) return false

        // Check if quadrilateral is roughly square
        val corners = points.toList()

        // Calculate side lengths
        val side1 = distance(corners[0], corners[1])
        val side2 = distance(corners[1], corners[2])
        val side3 = distance(corners[2], corners[3])
        val side4 = distance(corners[3], corners[0])

        val avgSide = (side1 + side2 + side3 + side4) / 4.0
        val maxDeviation = avgSide * 0.3 // Allow 30% deviation

        // Check if all sides are roughly equal (square-like)
        val isSquareLike = listOf(side1, side2, side3, side4).all {
            kotlin.math.abs(it - avgSide) < maxDeviation
        }

        // Check if area matches expected square area
        val expectedArea = avgSide * avgSide
        val areaRatio = area / expectedArea
        val isValidArea = areaRatio > 0.7 && areaRatio < 1.3

        return isSquareLike && isValidArea
    }

    private fun distance(p1: org.opencv.core.Point, p2: org.opencv.core.Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun extractTagId(grayMat: Mat, corners: List<Point2D>): Int {
        try {
            // Step 1: Extract the tag region using perspective transform
            val tagSize = 200 // Target size for analysis

            // Source points (tag corners) - FIXED
            val srcPoints = MatOfPoint2f()
            val srcArray = arrayOf(
                org.opencv.core.Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
                org.opencv.core.Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
                org.opencv.core.Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
                org.opencv.core.Point(corners[3].x.toDouble(), corners[3].y.toDouble())
            )
            srcPoints.fromArray(*srcArray)

            // Destination points (normalized square) - FIXED
            val dstPoints = MatOfPoint2f()
            val dstArray = arrayOf(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(tagSize.toDouble(), 0.0),
                org.opencv.core.Point(tagSize.toDouble(), tagSize.toDouble()),
                org.opencv.core.Point(0.0, tagSize.toDouble())
            )
            dstPoints.fromArray(*dstArray)

            // Get perspective transform
            val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            // Apply transform to get normalized tag image
            val normalizedTag = Mat()
            Imgproc.warpPerspective(grayMat, normalizedTag, transform, Size(tagSize.toDouble(), tagSize.toDouble()))

            // Step 2: Analyze the normalized tag pattern
            val tagId = analyzeTagPattern(normalizedTag)

            // Cleanup
            srcPoints.release()
            dstPoints.release()
            transform.release()
            normalizedTag.release()

            return tagId

        } catch (e: Exception) {
            android.util.Log.e("AprilTagDetector", "Error extracting tag ID", e)
            return -1
        }
    }

    private fun analyzeTagPattern(normalizedTag: Mat): Int {
        try {
            // Threshold the normalized tag
            val binaryTag = Mat()
            Imgproc.threshold(normalizedTag, binaryTag, 127.0, 255.0, Imgproc.THRESH_BINARY)

            val tagSize = normalizedTag.width().toInt()
            val cellSize = tagSize / 8 // Assuming 8x8 grid like AprilTag

            // Sample the inner 6x6 grid (ignoring border)
            val pattern = Array(6) { BooleanArray(6) }

            for (row in 0 until 6) {
                for (col in 0 until 6) {
                    val x = (col + 1) * cellSize + cellSize / 2
                    val y = (row + 1) * cellSize + cellSize / 2

                    val pixel = binaryTag.get(y, x)[0]
                    pattern[row][col] = pixel > 127.0 // White = true, Black = false
                }
            }

            binaryTag.release()

            // Step 3: Decode pattern to ID
            return decodeAprilTagPattern(pattern)

        } catch (e: Exception) {
            android.util.Log.e("AprilTagDetector", "Error analyzing tag pattern", e)
            return -1
        }
    }

    private fun decodeAprilTagPattern(pattern: Array<BooleanArray>): Int {
        // Simple pattern matching for IDs 0-15
        // In real AprilTags, this involves complex error correction

        // For now, use a simple checksum-based approach
        var checksum = 0
        for (row in 1 until 5) { // Use inner 4x4 area
            for (col in 1 until 5) {
                if (pattern[row][col]) {
                    checksum += (1 shl (row * 4 + col))
                }
            }
        }

        // Map checksum to IDs 0-15
        return (checksum and 0xF)
    }
}