package com.example.apriltags.postionning

import com.example.apriltags.data.Point3D

class TagManager {
    // Configure these based on your room layout
    private val knownTagPositions = mapOf(
        0 to Point3D(0.0f, 0.0f, 1.5f),      // Corner 1 at 1.5m height
        1 to Point3D(4.0f, 0.0f, 1.5f),      // Corner 2
        2 to Point3D(4.0f, 3.0f, 1.5f),      // Corner 3
        3 to Point3D(0.0f, 3.0f, 1.5f),      // Corner 4
        4 to Point3D(2.0f, 1.5f, 1.5f)       // Center reference
    )

    fun getTagPosition(id: Int): Point3D? = knownTagPositions[id]
    fun getAllKnownTags(): Map<Int, Point3D> = knownTagPositions
}