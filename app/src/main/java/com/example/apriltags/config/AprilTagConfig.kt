package com.example.apriltags.config

import com.example.apriltags.data.AprilTag
import com.example.apriltags.data.Point3D

object AprilTagConfig {
    // Define your room setup here
    // Coordinates are in meters, with origin at one corner of the room
    val TAGS = mapOf(
        0 to AprilTag(0, Point3D(0f, 0f, 1.5f), 0.1f), // Top-left corner, 1.5m high
        1 to AprilTag(1, Point3D(3f, 0f, 1.5f), 0.1f), // Top-right corner
        2 to AprilTag(2, Point3D(3f, 4f, 1.5f), 0.1f), // Bottom-right corner
        3 to AprilTag(3, Point3D(0f, 4f, 1.5f), 0.1f), // Bottom-left corner
        4 to AprilTag(4, Point3D(1.5f, 2f, 1.5f), 0.1f) // Center of room
    )

    // Camera intrinsic parameters (you should calibrate these for your specific camera)
    const val FOCAL_LENGTH_X = 800f
    const val FOCAL_LENGTH_Y = 800f
    const val PRINCIPAL_POINT_X = 320f
    const val PRINCIPAL_POINT_Y = 240f
}