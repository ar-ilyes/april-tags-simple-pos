package com.example.apriltags

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.apriltags.opencv.OpenCVManager
import com.example.apriltags.ui.theme.AprilTagsTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var openCVManager: OpenCVManager
    private val viewModel: PositioningViewModel by viewModels()

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "✅ All permissions granted - initializing OpenCV")
            initializeOpenCV()
        } else {
            Log.e(TAG, "❌ Camera permission denied")
            // Handle permission denial
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity onCreate")

        // Initialize OpenCV Manager
        openCVManager = OpenCVManager(this)

        // Check permissions and initialize
        if (allPermissionsGranted()) {
            initializeOpenCV()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        // Set up Compose UI
        setContent {
            AprilTagsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PositioningScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 MainActivity onResume")

        // Enable OpenCV view functionality
        openCVManager.enableView()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Camera permission is granted")

            // Initialize OpenCV if not already done
            if (!openCVManager.isOpenCVReady()) {
                Log.d(TAG, "🔄 OpenCV not ready, initializing...")
                initializeOpenCV()
            } else {
                Log.d(TAG, "✅ OpenCV already ready")
            }
        } else {
            Log.e(TAG, "❌ Camera permission is NOT granted - requesting...")
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity onPause")

        // Disable OpenCV view functionality
        openCVManager.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy")
    }

    private fun initializeOpenCV() {
        Log.d(TAG, "Initializing OpenCV...")

        openCVManager.initializeOpenCV { success ->
            if (success) {
                Log.d(TAG, "OpenCV initialized successfully - Version: ${openCVManager.getOpenCVVersion()}")
                runOnUiThread {
                    // OpenCV is ready - you can now start camera or other CV operations
                    onOpenCVReady()
                }
            } else {
                Log.e(TAG, "OpenCV initialization failed")
                runOnUiThread {
                    // Handle initialization failure
                    onOpenCVInitializationFailed()
                }
            }
        }
    }

    private fun onOpenCVReady() {
        Log.d(TAG, "OpenCV is ready for use")

        // Force a small delay to ensure everything is ready
        handler.postDelayed({
            Log.d(TAG, "OpenCV ready callback complete")
        }, 100)
    }

    private fun onOpenCVInitializationFailed() {
        Log.e(TAG, "Failed to initialize OpenCV")

        // Show error or retry
        handler.postDelayed({
            Log.w(TAG, "Retrying OpenCV initialization...")
            initializeOpenCV()
        }, 1000)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}