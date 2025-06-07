package com.example.apriltags.data


data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float = 0f
) {
    fun distanceTo(other: Point3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class AprilTag(
    val id: Int,
    val worldPosition: Point3D,
    val size: Float // in meters
)

data class DetectedTag(
    val id: Int,
    val corners: List<Point2D>,
    val center: Point2D
)

data class Point2D(
    val x: Float,
    val y: Float
)

data class CameraPosition(
    val position: Point3D,
    val orientation: Float = 0.0f, // Default orientation in radians
    val accuracy: Float
)