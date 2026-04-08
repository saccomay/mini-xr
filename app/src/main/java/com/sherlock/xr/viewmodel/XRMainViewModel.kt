package com.sherlock.xr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sherlock.xr.data.DeviceInfo
import com.sherlock.xr.data.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MyVector3(val x: Float, val y: Float, val z: Float)

// Represents an anchored device label in space
data class AnchoredDevice(
        val id: String,
        val info: DeviceInfo,
        val isDetailOpen: Boolean = false,
        val worldPosition: MyVector3
)

class XRMainViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<AnchoredDevice>>(emptyList())
    val devices: StateFlow<List<AnchoredDevice>> = _devices.asStateFlow()

    // Trạng thái bật/tắt Debug Mode (Có thể dùng làm Home Space mode)
    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    fun toggleDebugMode() {
        _isDebugMode.value = !_isDebugMode.value
    }

    fun triggerScan() {
        // Có thể tích hợp với logic JNI để bắt đầu/dừng quét
    }

    fun clearData() {
        _devices.value = emptyList()
    }

    // Called by the C++ NDK OpenXR Extension when a QR tracking matches
    fun onQrCodeScanned(qrId: String, x: Float, y: Float, z: Float) {
        viewModelScope.launch {
            val newPos = MyVector3(x, y, z)
            val existing = _devices.value.find { it.id == qrId }
            if (existing != null) {
                // Update position directly
                _devices.value = _devices.value.map {
                    if (it.id == qrId) it.copy(worldPosition = newPos) else it
                }
            } else {
                val info = DeviceRepository.fetchInfoMock(qrId)
                val newDevice = AnchoredDevice(id = qrId, info = info, worldPosition = newPos)
                _devices.value = _devices.value + newDevice
            }
        }
    }

    fun toggleDetail(id: String) {
        _devices.value =
                _devices.value.map {
                    if (it.id == id) it.copy(isDetailOpen = !it.isDetailOpen) else it
                }
    }

    fun removeDevice(id: String) {
        _devices.value = _devices.value.filter { it.id != id }
    }
}

