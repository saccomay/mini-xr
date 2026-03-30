package com.sherlock.xr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import com.sherlock.xr.ui.DetailedPopup
import com.sherlock.xr.ui.LabelBadge
import com.sherlock.xr.viewmodel.XRMainViewModel

import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.scenecore.scene
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.movable
import androidx.compose.ui.unit.dp

@Composable
fun XRApp(
    viewModel: XRMainViewModel = viewModel()
) {
    val devices = viewModel.devices.collectAsState().value
    val session = LocalSession.current
    val spatialCapabilities = LocalSpatialCapabilities.current
    val isSpatialUiEnabled = spatialCapabilities.isSpatialUiEnabled

    LaunchedEffect(isSpatialUiEnabled) {
        if (isSpatialUiEnabled) {
            session?.scene?.requestFullSpaceMode()
            // Đặt mức độ nhìn xuyên thấu (Camera Passthrough) là 100% để hiển thị trọn vẹn không gian thực
            session?.scene?.spatialEnvironment?.preferredPassthroughOpacity = 1.0f
        }
    }

    // Phân tích toạ độ 2D từ CameraX và ánh xạ thành toạ độ Không gian 3D (Raycast Approximation)
    LaunchedEffect(viewModel.pendingRaycasts) {
        viewModel.pendingRaycasts.collect { data ->
            session?.let { xrSession ->
                if (data.size >= 4) {
                    val screenX = data[0]
                    val screenY = data[1]
                    val width = data[2]
                    val height = data[3]
                    
                    // Giả định góc nhìn (FOV) của Camera thiết bị XR khoảng 70 ngang, 50 dọc
                    val fovX = 70f
                    val fovY = 50f
                    
                    // Chuyển đổi toạ độ pixel (0 -> width) thành khoảng (-1 -> 1)
                    val normalizedX = (screenX / width) * 2f - 1f
                    // Y trong ảnh thì 0 nằm ở trên cùng, Hệ trục toạ độ 3D thì Y dương nằm phía trên
                    val normalizedY = -(screenY / height) * 2f + 1f
                    
                    // Đổi sang góc quay
                    val angleX = normalizedX * (fovX / 2f)
                    val angleY = normalizedY * (fovY / 2f) 
                    
                    // Tính toạ độ Vector chỉ hướng (Direction Vector)
                    val dirX = kotlin.math.sin(Math.toRadians(angleX.toDouble())).toFloat()
                    val dirY = kotlin.math.sin(Math.toRadians(angleY.toDouble())).toFloat()
                    val dirZ = -kotlin.math.cos(Math.toRadians(angleX.toDouble())).toFloat()
                    
                    // Đặt Label cách mắt người dùng (tâm 0,0) khoảng 1.2 mét về phía tia nhìn
                    val distance = 1.2f
                    val targetPos = com.sherlock.xr.viewmodel.MyVector3(
                        dirX * distance,
                        dirY * distance,
                        dirZ * distance
                    )
                    
                    viewModel.onRaycastSuccessMockDataset(targetPos)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.triggerScan() }) {
                Icon(Icons.Filled.Search, contentDescription = "Scan")
                Spacer(Modifier.width(8.dp))
                Text("Scan")
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = { viewModel.clearData() }) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear")
                Spacer(Modifier.width(8.dp))
                Text("Clear")
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = { session?.scene?.requestHomeSpaceMode() }) {
                Icon(Icons.Filled.Close, contentDescription = "Escape")
                Spacer(Modifier.width(8.dp))
                Text("Exit")
            }
        }
    }

    Subspace {
        devices.forEach { device ->
            // Neo LabelBadge tại vị trí worldPosition (ví dụ: mock random ra)
            // Cân nhắc nhân với scale dp nếu cần, hiện tại giả định x,y,z đang ánh xạ 1-1 với dp.
            SpatialPanel(
                modifier = SubspaceModifier
                    .offset(
                        x = (device.worldPosition.x * 1000).dp, 
                        y = (device.worldPosition.y * 1000).dp, 
                        z = (device.worldPosition.z * 1000).dp
                    )
                    .movable()
            ) {
                LabelBadge(info = device.info) {
                    viewModel.toggleDetail(device.id)
                }
            }

            if (device.isDetailOpen) {
                // Đặt DetailedPopup ngay trên LabelBadge
                SpatialPanel(
                    modifier = SubspaceModifier
                        .offset(
                            x = (device.worldPosition.x * 1000).dp, 
                            y = (device.worldPosition.y * 1000 + 200).dp, // offset lên trên 200dp
                            z = (device.worldPosition.z * 1000).dp
                        )
                        .movable()
                ) {
                    DetailedPopup(info = device.info)
                }
            }
        }
    }
}
