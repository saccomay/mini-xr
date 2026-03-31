package com.sherlock.xr.viewmodel

import android.media.Image
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
    val trackingId: Int?,
    val labels: List<String>
)

class XRMainViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<AnchoredDevice>>(emptyList())
    val devices: StateFlow<List<AnchoredDevice>> = _devices.asStateFlow()

    // Danh sách các vùng detect thô cho chế độ Debug
    private val _debugDetections = MutableStateFlow<List<RawDetection>>(emptyList())
    val debugDetections: StateFlow<List<RawDetection>> = _debugDetections.asStateFlow()

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
    fun processCameraFrame(imageProxy: androidx.camera.core.ImageProxy) {
        if (!shouldScan && !_isDebugMode.value) { 
            // Nếu không có lệnh triggerScan và KHÔNG ở chế độ Debug auto-scan thì bỏ qua frame
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

        isProcessingFrame = true

        // CHÚ Ý: Chuyển sang Dispatchers.Default để các tác vụ nặng (Convert YUV sang RGB, 
        // ML Kit process) không làm block Main Thread (gây đơ UI XR).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val detector = objectDetector ?: return@launch
                
                // Bước 1: Gọi model ML Kit để khoanh vùng tem/thiết bị trong khung hình
                val results: List<DetectedObjectResult> = detector.analyze(image, rotation)
                
                if (_isDebugMode.value) {
                    val rawDetections = results.map { res ->
                        RawDetection(
                            boundingBox = res.boundingBox,
                            trackingId = res.trackingId,
                            labels = res.labels.map { it.text }
                        )
                    }
                    _debugDetections.value = rawDetections
                }

                // Bước 2: Duyệt qua các vật thể detect được
                for (result in results) {
                    // Bước 3: Lấy toạ độ tâm 2D (trên frame ảnh) của vật thể
                    val (x, y) = result.centerCoordinate
                    
                    // Bước 4: Đẩy toạ độ cộng thêm kích thước khung hình sang UI
                    _pendingRaycasts.emit(
                        floatArrayOf(x.toFloat(), y.toFloat(), image.width.toFloat(), image.height.toFloat())
                    )
                }
            } finally {
                // LUÔN LUÔN close imageProxy sau khi phân tích xong để CameraX feed frame mới.
                imageProxy.close()
                isProcessingFrame = false
                // Tắt cờ scan để đảm bảo chỉ quét duy nhất 1 frame mỗi lần bấm nút
                shouldScan = false
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
