package com.sherlock.xr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.sherlock.xr.classification.MLKitObjectDetector
import com.sherlock.xr.viewmodel.XRMainViewModel
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: XRMainViewModel
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Log.e("MainActivity", "Camera permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[XRMainViewModel::class.java]
        viewModel.objectDetector = MLKitObjectDetector(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Entry point for XR Compose UI
                    XRApp(viewModel)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // QUAN TRỌNG: Cả Preview và ImageAnalysis phải có cùng target resolution.
            // Nếu khác nhau, bounding box từ ML Kit (tọa độ ImageAnalysis space)
            // sẽ không bao giờ khớp chính xác với những gì PreviewView hiển thị.
            // 960×720 = tỷ lệ 4:3, cân bằng giữa độ chính xác và hiệu năng.
            val targetResolution = Size(960, 720)

            val previewUseCase = androidx.camera.core.Preview.Builder()
                .setTargetResolution(targetResolution)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        viewModel.processCameraFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, imageAnalyzer
                )
                viewModel.setPreviewUseCase(previewUseCase)
            } catch(exc: Exception) {
                Log.e("MainActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // Gán cứng phím Volume (Tăng/Giảm âm lượng) thành nút bấm vật lý để Quét/Scan.
    // Việc này giúp kỹ thuật viên có thể rảnh tay hoàn toàn, không cần phải với tay bấm vào bảng điều khiển ảo trong không gian 3D.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            viewModel.triggerScan()
            return true // Chặn không cho đổi âm lượng thực sự
        }
        return super.onKeyDown(keyCode, event)
    }
}
