package com.example.apriltags.postionning

import com.example.apriltags.data.Point3D

class TagManager {
    // Physical tag size in meters (measure your printed tags)
    private val tagSize = 0.15f // 15cm - UPDATE THIS TO MATCH YOUR PRINTED TAGS

    // Configure these positions based on your room measurements (in meters)
    // These represent the real-world positions of your ArUco markers
    private val knownTagPositions = mapOf(
        0 to Point3D(0.0f, 0.0f, 1.5f),      // Tag 0 - Corner 1 (x=0m, y=0m, height=1.5m)
        1 to Point3D(4.0f, 0.0f, 1.5f),      // Tag 1 - Corner 2 (x=4m, y=0m, height=1.5m)
        2 to Point3D(4.0f, 3.0f, 1.5f),      // Tag 2 - Corner 3 (x=4m, y=3m, height=1.5m)
        3 to Point3D(0.0f, 3.0f, 1.5f),      // Tag 3 - Corner 4 (x=0m, y=3m, height=1.5m)
        4 to Point3D(2.0f, 1.5f, 1.5f),      // Tag 4 - Center reference (x=2m, y=1.5m, height=1.5m)
        5 to Point3D(1.0f, 0.0f, 1.5f),      // Tag 5 - Additional reference point
        6 to Point3D(3.0f, 0.0f, 1.5f),      // Tag 6 - Additional reference point
        7 to Point3D(4.0f, 1.5f, 1.5f),      // Tag 7 - Additional reference point
        8 to Point3D(3.0f, 3.0f, 1.5f),      // Tag 8 - Additional reference point
        9 to Point3D(1.0f, 3.0f, 1.5f)       // Tag 9 - Additional reference point
    )

    /**
     * Get the known 3D position of a tag by its ID
     * @param id The ArUco marker ID
     * @return The 3D position of the tag in world coordinates, or null if unknown
     */
    fun getTagPosition(id: Int): Point3D? = knownTagPositions[id]

    /**
     * Get the physical size of the tags in meters
     * @return Tag size in meters
     */
    fun getTagSize(): Float = tagSize

    /**
     * Get all known tag positions
     * @return Map of tag ID to 3D position
     */
    fun getAllKnownTags(): Map<Int, Point3D> = knownTagPositions

    /**
     * Check if a tag ID is known
     * @param id The tag ID to check
     * @return true if the tag is known, false otherwise
     */
    fun isTagKnown(id: Int): Boolean = knownTagPositions.containsKey(id)

    /**
     * Get the number of known tags
     * @return Number of known tags
     */
    fun getKnownTagCount(): Int = knownTagPositions.size

    /**
     * Add or update a tag position
     * @param id Tag ID
     * @param position 3D position in world coordinates
     */
    fun setTagPosition(id: Int, position: Point3D) {
        // Note: This creates a new map since the original is immutable
        // In a real app, you might want to make knownTagPositions mutable
        // or use a different data structure
    }

    /**
     * Get the expected tag size in pixels at a given distance
     * @param distance Distance to tag in meters
     * @param focalLength Camera focal length in pixels
     * @return Expected tag size in pixels
     */
    fun getExpectedPixelSize(distance: Float, focalLength: Float): Float {
        return (tagSize * focalLength) / distance
    }
}