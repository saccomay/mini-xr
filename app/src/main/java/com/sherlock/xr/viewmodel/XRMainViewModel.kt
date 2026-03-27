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

// Represents an anchored device label in space
data class AnchoredDevice(
    val id: String,
    val info: DeviceInfo,
    val isDetailOpen: Boolean = false
)

class XRMainViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<AnchoredDevice>>(emptyList())
    val devices: StateFlow<List<AnchoredDevice>> = _devices.asStateFlow()

    // Flow to emit 2D coordinates that need to be raycasted by the UI
    private val _pendingRaycasts = MutableSharedFlow<Pair<Float, Float>>()
    val pendingRaycasts = _pendingRaycasts.asSharedFlow()

    // The ML Kit Detector (to be initialized by the Activity/UI Layer)
    var objectDetector: ObjectDetector? = null

    // Hàm này được CameraX (File MainActivity) gọi liên tục mỗi khi có frame mới từ Camera thực.
    fun processCameraFrame(imageProxy: androidx.camera.core.ImageProxy) {
        val image = imageProxy.image
        // Nếu không thu được ảnh, phải release ngay imageProxy để hệ thống camera nhả frame tiếp theo.
        if (image == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees

        // CHÚ Ý: Chuyển sang Dispatchers.Default để các tác vụ nặng (Convert YUV sang RGB, 
        // ML Kit process) không làm block Main Thread (gây đơ UI XR).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val detector = objectDetector ?: return@launch
                
                // Bước 1: Gọi model ML Kit để khoanh vùng tem/thiết bị trong khung hình
                val results: List<DetectedObjectResult> = detector.analyze(image, rotation)
                
                // Bước 2: Duyệt qua các vật thể detect được
                for (result in results) {
                    // Bước 3: Lấy toạ độ tâm 2D (trên frame ảnh) của vật thể
                    val (x, y) = result.centerCoordinate
                    
                    // Bước 4: Đẩy toạ độ này lên SharedFlow để tầng UI (androidx.xr.compose) 
                    // nhận và thực hiện việc SceneCore Raycast (phóng tia 3D) từ đó.
                    _pendingRaycasts.emit(Pair(x.toFloat(), y.toFloat()))
                }
            } finally {
                // LUÔN LUÔN close imageProxy sau khi phân tích xong để CameraX feed frame mới.
                imageProxy.close()
            }
        }
    }

    // Called by the UI after SceneCore successfully raycasts the 2D point
    fun onRaycastSuccessMockDataset() {
        val id = "Device-${UUID.randomUUID().toString().take(4)}"
        
        viewModelScope.launch {
            val info = DeviceRepository.fetchInfoMock(id)
            val newDevice = AnchoredDevice(id = id, info = info)
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
