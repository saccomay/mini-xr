package com.sherlock.xr.viewmodel

import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sherlock.xr.classification.DetectedObjectResult
import com.sherlock.xr.classification.ObjectDetector
import com.sherlock.xr.data.DeviceInfo
import com.sherlock.xr.data.DeviceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MyVector3(val x: Float, val y: Float, val z: Float)

// Represents an anchored device label in space
data class AnchoredDevice(
    val id: String,
    val info: DeviceInfo,
    val isDetailOpen: Boolean = false,
    val worldPosition: MyVector3
)

data class RawDetection(
    val boundingBox: android.graphics.Rect,
    val label: String,
    val imageWidth: Int = 480,
    val imageHeight: Int = 640
)

class XRMainViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<AnchoredDevice>>(emptyList())
    val devices: StateFlow<List<AnchoredDevice>> = _devices.asStateFlow()

    // Danh sách các vùng detect thô cho chế độ Debug
    private val _debugDetections = MutableStateFlow<List<RawDetection>>(emptyList())
    val debugDetections: StateFlow<List<RawDetection>> = _debugDetections.asStateFlow()

    private val _previewUseCase = MutableStateFlow<androidx.camera.core.Preview?>(null)
    val previewUseCase: StateFlow<androidx.camera.core.Preview?> = _previewUseCase.asStateFlow()

    fun setPreviewUseCase(useCase: androidx.camera.core.Preview?) {
        _previewUseCase.value = useCase
    }

    // Flow to emit 2D coordinates that need to be raycasted by the UI
    private val _pendingRaycasts = MutableSharedFlow<FloatArray>()
    val pendingRaycasts = _pendingRaycasts.asSharedFlow()

    // The ML Kit Detector (to be initialized by the Activity/UI Layer)
    var objectDetector: ObjectDetector? = null

    private var isProcessingFrame = false
    private var shouldScan = false
    
    // Trạng thái bật/tắt Debug Mode
    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    fun toggleDebugMode() {
        _isDebugMode.value = !_isDebugMode.value
        if (!_isDebugMode.value) {
            _debugDetections.value = emptyList() // Xoá data debug khi tắt
        }
    }

    fun triggerScan() {
        shouldScan = true
    }

    fun clearData() {
        _devices.value = emptyList()
        _debugDetections.value = emptyList()
    }

    // Hàm này được CameraX (File MainActivity) gọi liên tục mỗi khi có frame mới từ Camera thực.
    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun processCameraFrame(imageProxy: androidx.camera.core.ImageProxy) {
        // BUG FIX: Chỉ xử lý frame khi có lệnh triggerScan (shouldScan = true).
        // Debug mode chỉ điều khiển việc HIỂN THỊ kết quả, KHÔNG tự động scan liên tục.
        // Trước đây logic "!shouldScan && !isDebugMode" khiến debug mode bật thì scan mọi frame.
        if (!shouldScan) {
            imageProxy.close()
            return
        }

        // Nếu đang xử lý một frame chưa xong thì drop frame mới nhất để chống lag thiết bị
        if (isProcessingFrame) {
            imageProxy.close()
            return
        }
        
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees

        // Reset shouldScan ngay lập tức để chỉ xử lý duy nhất 1 frame này
        shouldScan = false
        isProcessingFrame = true

        // CHÚ Ý: Chuyển sang Dispatchers.Default để các tác vụ nặng (Convert YUV sang RGB, 
        // ML Kit process) không làm block Main Thread (gây đơ UI XR).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val detector = objectDetector ?: return@launch
                
                // Bước 1: Gọi model ML Kit để khoanh vùng tem/thiết bị trong khung hình
                val results: List<DetectedObjectResult> = detector.analyze(image, rotation)
                
                // Cập nhật debug overlay (chỉ hiển thị nếu isDebugMode = true ở UI layer)
                val rawDetections = results.map { res ->
                    RawDetection(
                        boundingBox = res.rect,
                        label = res.label,
                        // Dùng kích thước bitMap (ảnh đã rotate) để tọa độ bbox khớp với ảnh preview
                        imageWidth = res.bitMap.width,
                        imageHeight = res.bitMap.height
                    )
                }
                _debugDetections.value = rawDetections

                // Bước 2: Duyệt qua các vật thể detect được để tạo anchor 3D
                for (result in results) {
                    // Bước 3: Lấy toạ độ tâm 2D (trong rotatedImage space)
                    val (x, y) = result.centerCoordinate
                    
                    // Bước 4: Đẩy toạ độ + kích thước ảnh đã rotate sang UI để raycast
                    // QUAN TRỌNG: Phải dùng bitMap.width/height (ảnh đã rotate),
                    // KHÔNG dùng image.width/height (YUV thô chưa rotate) → tọa độ sẽ bị lật.
                    _pendingRaycasts.emit(
                        floatArrayOf(
                            x.toFloat(), y.toFloat(),
                            result.bitMap.width.toFloat(),
                            result.bitMap.height.toFloat()
                        )
                    )
                }
            } finally {
                // LUÔN LUÔN close imageProxy sau khi phân tích xong để CameraX feed frame mới.
                imageProxy.close()
                isProcessingFrame = false
            }
        }
    }

    // Called by the UI after SceneCore successfully raycasts the 2D point
    fun onRaycastSuccessMockDataset(position: MyVector3? = null) {
        val id = "Device-${UUID.randomUUID().toString().take(4)}"
        
        viewModelScope.launch {
            val info = DeviceRepository.fetchInfoMock(id)
            val pos = position ?: MyVector3(
                (Math.random() * 2f - 1f).toFloat(), 
                0f, 
                (-2f + (Math.random() * 2f - 1f)).toFloat()
            )
            val newDevice = AnchoredDevice(id = id, info = info, worldPosition = pos)
            _devices.value = _devices.value + newDevice
        }
    }

    fun toggleDetail(id: String) {
        _devices.value = _devices.value.map {
            if (it.id == id) it.copy(isDetailOpen = !it.isDetailOpen) else it
        }
    }

    fun removeDevice(id: String) {
        _devices.value = _devices.value.filter { it.id != id }
    }
}
