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

    // Process real camera frames using ML Kit
    fun processCameraFrame(image: Image, rotation: Int) {
        viewModelScope.launch {
            val detector = objectDetector ?: return@launch
            
            // 1. Run ML Kit detection
            val results: List<DetectedObjectResult> = detector.analyze(image, rotation)
            
            // 2. Mock Device Data onto whatever ML Kit detected
            for (result in results) {
                // Ignore background/unknowns if necessary, but here we process any detected object
                val (x, y) = result.centerCoordinate
                
                // 3. Emit the 2D coordinate to the UI for SceneCore Raycasting
                _pendingRaycasts.emit(Pair(x.toFloat(), y.toFloat()))
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
