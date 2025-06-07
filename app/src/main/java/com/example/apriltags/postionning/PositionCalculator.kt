package com.example.apriltags.postionning


import com.example.apriltags.data.*
import kotlin.math.*

class PositionCalculator {

    private val tagManager = TagManager()

    fun calculatePosition(detectedTags: List<DetectedTag>): CameraPosition? {
        if (detectedTags.isEmpty()) return null

        val knownTags = detectedTags.filter {
            tagManager.getTagPosition(it.id) != null
        }

        if (knownTags.isEmpty()) return null

        return when (knownTags.size) {
            1 -> calculatePositionFromSingleTag(knownTags[0])
            2 -> calculatePositionFromTwoTags(knownTags[0], knownTags[1])
            else -> calculatePositionFromMultipleTags(knownTags)
        }
    }

    private fun calculatePositionFromSingleTag(tag: DetectedTag): CameraPosition {
        val knownPos = tagManager.getTagPosition(tag.id)!!

        // Estimate distance and angle from tag
        val distance = estimateDistanceFromTag(tag)
        val angle = estimateAngleFromTag(tag)

        // Calculate position relative to tag
        val x = knownPos.x + distance * kotlin.math.cos(angle).toFloat()
        val y = knownPos.y + distance * kotlin.math.sin(angle).toFloat()

        // Calculate camera orientation from tag orientation
        val orientation = estimateCameraOrientation(tag)

        return CameraPosition(
            position = Point3D(x, y, knownPos.z),
            orientation = orientation,
            accuracy = 0.6f // Lower accuracy with single tag
        )
    }

    private fun calculatePositionFromTwoTags(tag1: DetectedTag, tag2: DetectedTag): CameraPosition {
        val pos1 = tagManager.getTagPosition(tag1.id)!!
        val pos2 = tagManager.getTagPosition(tag2.id)!!

        val dist1 = estimateDistanceFromTag(tag1)
        val dist2 = estimateDistanceFromTag(tag2)

        // Triangulation using two known points and distances
        val position = triangulate(pos1, dist1, pos2, dist2)

        // Average orientation from both tags
        val orientation = (estimateCameraOrientation(tag1) + estimateCameraOrientation(tag2)) / 2f

        return CameraPosition(
            position = position,
            orientation = orientation,
            accuracy = 0.8f // Better accuracy with two tags
        )
    }

    private fun calculatePositionFromMultipleTags(tags: List<DetectedTag>): CameraPosition {
        // Use least squares for multiple tags
        val positions = mutableListOf<Point3D>()
        val orientations = mutableListOf<Float>()

        for (tag in tags.take(4)) { // Use up to 4 tags for stability
            val singlePos = calculatePositionFromSingleTag(tag)
            positions.add(singlePos.position)
            orientations.add(singlePos.orientation)
        }

        // Average the positions and orientations
        val avgX = positions.map { it.x }.average().toFloat()
        val avgY = positions.map { it.y }.average().toFloat()
        val avgZ = positions.map { it.z }.average().toFloat()
        val avgOrientation = orientations.average().toFloat()

        return CameraPosition(
            position = Point3D(avgX, avgY, avgZ),
            orientation = avgOrientation,
            accuracy = 0.9f // Highest accuracy with multiple tags
        )
    }

    private fun estimateCameraOrientation(tag: DetectedTag): Float {
        // Calculate orientation from tag corner arrangement
        val corner1 = tag.corners[0]
        val corner2 = tag.corners[1]

        val dx = corner2.x - corner1.x
        val dy = corner2.y - corner1.y

        return kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
    }

    private fun estimateDistanceFromTag(tag: DetectedTag): Float {
        // Calculate tag size in pixels
        val corner1 = tag.corners[0]
        val corner2 = tag.corners[1]
        val corner3 = tag.corners[2]

        val width = kotlin.math.sqrt(
            ((corner2.x - corner1.x) * (corner2.x - corner1.x) +
                    (corner2.y - corner1.y) * (corner2.y - corner1.y)).toDouble()
        ).toFloat()

        val height = kotlin.math.sqrt(
            ((corner3.x - corner2.x) * (corner3.x - corner2.x) +
                    (corner3.y - corner2.y) * (corner3.y - corner2.y)).toDouble()
        ).toFloat()

        val avgSize = (width + height) / 2f

        // Known physical tag size (adjust to your printed size)
        val realTagSize = 0.15f // 15cm in meters

        // Camera focal length (approximate - should be calibrated)
        val focalLength = 800f // pixels

        return (realTagSize * focalLength) / avgSize
    }

    private fun estimateAngleFromTag(tag: DetectedTag): Double {
        // Simplified angle estimation based on tag orientation
        val center = tag.center
        val imageCenter = Point2D(320f, 240f) // Assuming 640x480 image

        val dx = center.x - imageCenter.x
        val dy = center.y - imageCenter.y

        return kotlin.math.atan2(dy.toDouble(), dx.toDouble())
    }

    private fun triangulate(pos1: Point3D, dist1: Float, pos2: Point3D, dist2: Float): Point3D {
        // Simple 2D triangulation
        val d = kotlin.math.sqrt(
            ((pos2.x - pos1.x) * (pos2.x - pos1.x) +
                    (pos2.y - pos1.y) * (pos2.y - pos1.y)).toDouble()
        ).toFloat()

        val a = (dist1 * dist1 - dist2 * dist2 + d * d) / (2 * d)
        val h = kotlin.math.sqrt((dist1 * dist1 - a * a).toDouble()).toFloat()

        val x = pos1.x + a * (pos2.x - pos1.x) / d
        val y = pos1.y + a * (pos2.y - pos1.y) / d + h * (pos2.y - pos1.y) / d

        return Point3D(x, y, (pos1.z + pos2.z) / 2f)
    }
}