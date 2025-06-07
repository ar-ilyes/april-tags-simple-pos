package com.example.apriltags.vision


import android.content.Context
import org.opencv.android.OpenCVLoader

class OpenCVManager(private val context: Context) {
    private var isInitialized = false
    private val callbacks = mutableListOf<() -> Unit>()

    fun initialize(onReady: () -> Unit) {
        if (isInitialized) {
            onReady()
        } else {
            callbacks.add(onReady)

            // Modern OpenCV initialization
            if (OpenCVLoader.initLocal()) {
                isInitialized = true
                callbacks.forEach { it.invoke() }
                callbacks.clear()
            }
        }
    }
}