package com.example.apriltags.postionning

import android.util.Log
import com.example.apriltags.data.*
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import kotlin.math.*

class RobustPositionCalculator {

    companion object {
        private const val TAG = "RobustPositionCalculator"
    }

    private val tagManager = TagManager()

    // Camera intrinsic parameters - YOU SHOULD CALIBRATE THESE FOR YOUR CAMERA
    // These are approximate values for a typical mobile camera at 640x480
    private val cameraMatrix = Mat(3, 3, CvType.CV_64FC1).apply {
        // Format: [fx, 0, cx; 0, fy, cy; 0, 0, 1]
        // fx, fy = focal lengths, cx, cy = principal point
        put(0, 0,
            800.0, 0.0, 320.0,    // fx=800px, cx=320px (center x)
            0.0, 800.0, 240.0,    // fy=800px, cy=240px (center y)
            0.0, 0.0, 1.0         // homogeneous coordinate
        )
    }

    // Distortion coefficients [k1, k2, p1, p2, k3] - Convert to MatOfDouble
    private val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0)

    private val kalmanFilter = PositionKalmanFilter()

    fun calculatePosition(detectedTags: List<DetectedTag>): CameraPosition? {
        if (detectedTags.isEmpty()) {
            Log.w(TAG, "No tags detected")
            return null
        }

        val knownTags = detectedTags.filter {
            tagManager.getTagPosition(it.id) != null
        }

        if (knownTags.isEmpty()) {
            Log.w(TAG, "No known tags detected")
            return null
        }

        Log.d(TAG, "Calculating position from ${knownTags.size} known tags")

        val rawPosition = when (knownTags.size) {
            1 -> calculatePositionFromSingleTag(knownTags[0])
            2 -> calculatePositionFromTwoTags(knownTags[0], knownTags[1])
            else -> calculatePositionFromMultipleTags(knownTags)
        }

        // Apply Kalman filtering for smooth tracking
        return rawPosition?.let { kalmanFilter.update(it) }
    }

    private fun calculatePositionFromSingleTag(tag: DetectedTag): CameraPosition? {
        val knownPos = tagManager.getTagPosition(tag.id) ?: return null
        val tagSize = tagManager.getTagSize()

        try {
            // Method 1: PnP solver for accurate 3D pose
            val pnpResult = solvePnPPose(tag, tagSize)
            if (pnpResult != null) {
                val worldPosition = transformToWorldCoordinates(knownPos, pnpResult)
                return CameraPosition(
                    position = worldPosition.position,
                    orientation = worldPosition.orientation,
                    accuracy = 0.85f
                )
            }

            // Method 2: Fallback to geometric estimation
            Log.d(TAG, "PnP failed, using geometric estimation")
            return calculateGeometricPosition(tag, knownPos)

        } catch (e: Exception) {
            Log.e(TAG, "Error in single tag calculation", e)
            return calculateGeometricPosition(tag, knownPos)
        }
    }

    private fun calculatePositionFromTwoTags(tag1: DetectedTag, tag2: DetectedTag): CameraPosition? {
        val pos1 = calculatePositionFromSingleTag(tag1) ?: return null
        val pos2 = calculatePositionFromSingleTag(tag2) ?: return null

        // Weighted average based on accuracy and tag size
        val weight1 = pos1.accuracy * calculateTagQuality(tag1)
        val weight2 = pos2.accuracy * calculateTagQuality(tag2)
        val totalWeight = weight1 + weight2

        if (totalWeight <= 0) return pos1 // Fallback

        val avgX = (pos1.position.x * weight1 + pos2.position.x * weight2) / totalWeight
        val avgY = (pos1.position.y * weight1 + pos2.position.y * weight2) / totalWeight
        val avgZ = (pos1.position.z * weight1 + pos2.position.z * weight2) / totalWeight

        // Handle orientation averaging (accounting for circular nature)
        val avgOrientation = averageAngles(
            listOf(pos1.orientation to weight1, pos2.orientation to weight2)
        )

        return CameraPosition(
            position = Point3D(avgX, avgY, avgZ),
            orientation = avgOrientation,
            accuracy = min(0.95f, (weight1 + weight2) / 2f + 0.1f)
        )
    }

    private fun calculatePositionFromMultipleTags(tags: List<DetectedTag>): CameraPosition? {
        val positions = mutableListOf<Pair<CameraPosition, Float>>()

        // Calculate position from each tag with quality weighting
        for (tag in tags.take(6)) { // Limit to 6 tags for performance
            val position = calculatePositionFromSingleTag(tag)
            if (position != null) {
                val quality = calculateTagQuality(tag)
                positions.add(position to quality)
            }
        }

        if (positions.isEmpty()) return null

        // Weighted least squares approach
        var totalWeight = 0f
        var weightedX = 0f
        var weightedY = 0f
        var weightedZ = 0f
        val orientations = mutableListOf<Pair<Float, Float>>()

        for ((pos, quality) in positions) {
            val weight = pos.accuracy * quality
            totalWeight += weight
            weightedX += pos.position.x * weight
            weightedY += pos.position.y * weight
            weightedZ += pos.position.z * weight
            orientations.add(pos.orientation to weight)
        }

        val avgOrientation = averageAngles(orientations)

        return CameraPosition(
            position = Point3D(
                weightedX / totalWeight,
                weightedY / totalWeight,
                weightedZ / totalWeight
            ),
            orientation = avgOrientation,
            accuracy = min(0.98f, totalWeight / positions.size + 0.02f)
        )
    }

    private fun solvePnPPose(tag: DetectedTag, tagSize: Float): PnPResult? {
        try {
            // Create 3D object points for the tag corners
            val objectPoints = create3DTagCorners(tagSize)
            val imagePoints = convertToMatOfPoint2f(tag.corners)

            val rvec = Mat()
            val tvec = Mat()

            // Convert camera matrix to MatOfDouble if needed for some OpenCV versions
            val cameraMatrixDouble = MatOfDouble()
            cameraMatrix.convertTo(cameraMatrixDouble, CvType.CV_64FC1)

            val success = Calib3d.solvePnP(
                objectPoints, imagePoints, cameraMatrixDouble, distCoeffs, rvec, tvec
            )

            if (success) {
                val result = PnPResult(rvec.clone(), tvec.clone())

                // Cleanup
                objectPoints.release()
                imagePoints.release()
                cameraMatrixDouble.release()
                rvec.release()
                tvec.release()

                return result
            }

            // Cleanup on failure
            objectPoints.release()
            imagePoints.release()
            cameraMatrixDouble.release()
            rvec.release()
            tvec.release()

        } catch (e: Exception) {
            Log.e(TAG, "PnP solver error", e)
        }

        return null
    }

    private fun create3DTagCorners(tagSize: Float): MatOfPoint3f {
        val half = tagSize / 2f
        return MatOfPoint3f(
            Point3(-half.toDouble(), -half.toDouble(), 0.0),  // Top-left
            Point3(half.toDouble(), -half.toDouble(), 0.0),   // Top-right
            Point3(half.toDouble(), half.toDouble(), 0.0),    // Bottom-right
            Point3(-half.toDouble(), half.toDouble(), 0.0)    // Bottom-left
        )
    }

    private fun convertToMatOfPoint2f(corners: List<Point2D>): MatOfPoint2f {
        val points = corners.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        return MatOfPoint2f(*points)
    }

    private fun transformToWorldCoordinates(tagWorldPos: Point3D, pnpResult: PnPResult): CameraPosition {
        // Extract translation vector (camera position relative to tag)
        val tvecArray = DoubleArray(3)
        pnpResult.tvec.get(0, 0, tvecArray)

        // Extract rotation vector and convert to rotation matrix
        val rvecArray = DoubleArray(3)
        pnpResult.rvec.get(0, 0, rvecArray)

        val rotationMatrix = Mat()
        Calib3d.Rodrigues(pnpResult.rvec, rotationMatrix)

        // Camera position in tag coordinate system
        val camPosTag = Mat(3, 1, CvType.CV_64FC1)
        camPosTag.put(0, 0, -tvecArray[0], -tvecArray[1], -tvecArray[2])

        // Transform to world coordinates
        val rotMatArray = DoubleArray(9)
        rotationMatrix.get(0, 0, rotMatArray)

        // Simple transformation (assuming tag coordinate system aligns with world)
        val worldX = tagWorldPos.x - tvecArray[0].toFloat()
        val worldY = tagWorldPos.y - tvecArray[1].toFloat()
        val worldZ = tagWorldPos.z - tvecArray[2].toFloat()

        // Extract orientation (yaw) from rotation matrix
        val yaw = atan2(rotMatArray[3], rotMatArray[0]).toFloat()

        // Cleanup
        rotationMatrix.release()
        camPosTag.release()

        return CameraPosition(
            position = Point3D(worldX, worldY, worldZ),
            orientation = yaw,
            accuracy = 0.9f
        )
    }

    private fun calculateGeometricPosition(tag: DetectedTag, knownPos: Point3D): CameraPosition {
        // Estimate distance from tag size in image
        val distance = estimateDistanceFromTag(tag)
        val angle = estimateAngleFromTag(tag)

        // Calculate position using polar coordinates
        val x = knownPos.x + distance * cos(angle).toFloat()
        val y = knownPos.y + distance * sin(angle).toFloat()

        return CameraPosition(
            position = Point3D(x, y, knownPos.z),
            orientation = angle.toFloat(),
            accuracy = 0.7f // Lower accuracy for geometric method
        )
    }

    private fun estimateDistanceFromTag(tag: DetectedTag): Float {
        // Calculate average size of tag in image
        val corner1 = tag.corners[0]
        val corner2 = tag.corners[1]
        val corner3 = tag.corners[2]
        val corner4 = tag.corners[3]

        val width1 = sqrt((corner2.x - corner1.x).pow(2) + (corner2.y - corner1.y).pow(2))
        val width2 = sqrt((corner4.x - corner3.x).pow(2) + (corner4.y - corner3.y).pow(2))
        val height1 = sqrt((corner3.x - corner2.x).pow(2) + (corner3.y - corner2.y).pow(2))
        val height2 = sqrt((corner1.x - corner4.x).pow(2) + (corner1.y - corner4.y).pow(2))

        val avgSize = (width1 + width2 + height1 + height2) / 4.0f

        val realTagSize = tagManager.getTagSize() // Physical size in meters
        val focalLength = 800.0f // Should match camera calibration

        // Basic pinhole camera distance estimation
        return (realTagSize * focalLength) / avgSize
    }

    private fun estimateAngleFromTag(tag: DetectedTag): Double {
        val center = tag.center
        val imageCenter = Point2D(320f, 240f) // Image center (should match camera calibration)

        val dx = center.x - imageCenter.x
        val dy = center.y - imageCenter.y

        return atan2(dy.toDouble(), dx.toDouble())
    }

    private fun calculateTagQuality(tag: DetectedTag): Float {
        // Calculate quality score based on tag size and shape
        val corners = tag.corners

        // Size score (larger is better, up to a point)
        val minX = corners.minOf { it.x }
        val maxX = corners.maxOf { it.x }
        val minY = corners.minOf { it.y }
        val maxY = corners.maxOf { it.y }

        val area = (maxX - minX) * (maxY - minY)
        val sizeScore = min(1.0f, area / 10000f) // Normalize to 0-1

        // Shape score (how square-like)
        val sides = listOf(
            distance(corners[0], corners[1]),
            distance(corners[1], corners[2]),
            distance(corners[2], corners[3]),
            distance(corners[3], corners[0])
        )

        val avgSide = sides.average()
        val maxDeviation = sides.maxOf { abs(it - avgSide) }
        val shapeScore = max(0.0f, 1.0f - (maxDeviation / avgSide).toFloat())

        return (sizeScore + shapeScore) / 2f
    }

    private fun distance(p1: Point2D, p2: Point2D): Double {
        val dx = (p1.x - p2.x).toDouble()
        val dy = (p1.y - p2.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    private fun averageAngles(orientations: List<Pair<Float, Float>>): Float {
        // Convert to unit vectors, average, then convert back
        var sumX = 0.0
        var sumY = 0.0
        var totalWeight = 0f

        for ((angle, weight) in orientations) {
            sumX += cos(angle.toDouble()) * weight
            sumY += sin(angle.toDouble()) * weight
            totalWeight += weight
        }

        return atan2(sumY / totalWeight, sumX / totalWeight).toFloat()
    }

    // Helper classes
    private data class PnPResult(val rvec: Mat, val tvec: Mat)

    // Simple Kalman filter for position smoothing
    private class PositionKalmanFilter {
        private var lastPosition: CameraPosition? = null
        private var lastTimestamp = System.currentTimeMillis()

        fun update(newPosition: CameraPosition): CameraPosition {
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastTimestamp) / 1000.0f // seconds
            lastTimestamp = currentTime

            val smoothed = if (lastPosition == null || deltaTime > 1.0f) {
                // First measurement or too much time passed - use as-is
                newPosition
            } else {
                // Apply simple exponential smoothing
                val alpha = min(1.0f, deltaTime * 2.0f) // Adapt based on time

                val smoothX = lerp(lastPosition!!.position.x, newPosition.position.x, alpha)
                val smoothY = lerp(lastPosition!!.position.y, newPosition.position.y, alpha)
                val smoothZ = lerp(lastPosition!!.position.z, newPosition.position.z, alpha)
                val smoothOrientation = lerpAngle(lastPosition!!.orientation, newPosition.orientation, alpha)

                CameraPosition(
                    position = Point3D(smoothX, smoothY, smoothZ),
                    orientation = smoothOrientation,
                    accuracy = max(lastPosition!!.accuracy, newPosition.accuracy)
                )
            }

            lastPosition = smoothed
            return smoothed
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private fun lerpAngle(a: Float, b: Float, t: Float): Float {
            // Handle angle wrapping
            var diff = b - a
            if (diff > PI) diff -= 2 * PI.toFloat()
            if (diff < -PI) diff += 2 * PI.toFloat()
            return a + diff * t
        }
    }
}