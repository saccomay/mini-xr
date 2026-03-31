package com.sherlock.xr

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.scenecore.scene
import com.sherlock.xr.ui.DetailedPopup
import com.sherlock.xr.ui.LabelBadge
import com.sherlock.xr.viewmodel.XRMainViewModel

@Suppress("OPT_IN_USAGE")
@Composable
fun XRApp(viewModel: XRMainViewModel = viewModel()) {
    val devices = viewModel.devices.collectAsState().value
    val session = LocalSession.current
    val spatialCapabilities = LocalSpatialCapabilities.current
    val isSpatialUiEnabled = spatialCapabilities.isSpatialUiEnabled

    val isDebugMode = viewModel.isDebugMode.collectAsState().value
    val debugDetections = viewModel.debugDetections.collectAsState().value

    // Chỉ đặt opacity khi bật Spatial UI
    LaunchedEffect(isSpatialUiEnabled) {
        if (isSpatialUiEnabled) {
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
                    val targetPos =
                            com.sherlock.xr.viewmodel.MyVector3(
                                    dirX * distance,
                                    dirY * distance,
                                    dirZ * distance
                            )

                    viewModel.onRaycastSuccessMockDataset(targetPos)
                }
            }
        }
    }

    // 2D UI (Chỉ hiện rõ khi ở Home Space / Debug Mode)
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {

        // --- Camera Preview (Live feed qua PreviewView của CameraX) ---
        if (isDebugMode) {
            CameraPreview(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- Bounding-box overlay + debug text ---
        if (isDebugMode && debugDetections.isNotEmpty()) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                debugDetections.forEach { detection ->
                    val rect = detection.boundingBox
                    val imgW = detection.imageWidth.toFloat().coerceAtLeast(1f)
                    val imgH = detection.imageHeight.toFloat().coerceAtLeast(1f)

                    // Tính scale để fit ảnh vào canvas (Scale theo chiều nhỏ hơn – giống ContentScale.Fit)
                    val scaleX = size.width / imgW
                    val scaleY = size.height / imgH
                    // Dùng min scale để không bị méo (ảnh đã rotate nên fit là đúng)
                    val scale = minOf(scaleX, scaleY)
                    val offsetX = (size.width - imgW * scale) / 2f
                    val offsetY = (size.height - imgH * scale) / 2f

                    drawRect(
                        color = androidx.compose.ui.graphics.Color.Red,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            rect.left * scale + offsetX,
                            rect.top * scale + offsetY
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            rect.width() * scale,
                            rect.height() * scale
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    "DEBUG MODE",
                    color = androidx.compose.ui.graphics.Color.Red,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                debugDetections.forEach {
                    Text("Label: ${it.label}", color = androidx.compose.ui.graphics.Color.White)
                    Text("Rect: ${it.boundingBox}", color = androidx.compose.ui.graphics.Color.Yellow)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // --- Control Panel (ở Home Space hoặc Debug Mode) ---
        if (isDebugMode || !isSpatialUiEnabled) {
            ControlPanel(
                viewModel = viewModel,
                devices = devices,
                isDebugMode = isDebugMode,
                onToggleDebug = {
                    viewModel.toggleDebugMode()
                    if (!isDebugMode) {
                        session?.scene?.requestHomeSpaceMode()
                    } else {
                        session?.scene?.requestFullSpaceMode()
                        session?.scene?.spatialEnvironment?.preferredPassthroughOpacity = 1.0f
                    }
                }
            )
        }
    }

    Subspace {
        if (!isDebugMode && isSpatialUiEnabled) {
            // Bảng điều khiển (Control Panel) đặt cố định trước mặt người dùng (khoảng cách 800mm)
            SpatialPanel(modifier = SubspaceModifier.offset(x = 0.dp, y = (-200).dp, z = (-800).dp)) {
                ControlPanel(
                    viewModel = viewModel,
                    devices = devices,
                    isDebugMode = false,
                    onToggleDebug = {
                        viewModel.toggleDebugMode()
                        session?.scene?.requestHomeSpaceMode()
                    }
                )
            }
        }

        devices.forEach { device ->
            SpatialPanel(
                modifier = SubspaceModifier.offset(
                    x = (device.worldPosition.x * 1000).dp,
                    y = (device.worldPosition.y * 1000).dp,
                    z = (device.worldPosition.z * 1000).dp
                )
            ) { LabelBadge(info = device.info) { viewModel.toggleDetail(device.id) } }

            if (device.isDetailOpen) {
                SpatialPanel(
                    modifier = SubspaceModifier.offset(
                        x = (device.worldPosition.x * 1000).dp,
                        y = (device.worldPosition.y * 1000 + 350).dp,
                        z = (device.worldPosition.z * 1000).dp
                    )
                ) { DetailedPopup(info = device.info) }
            }
        }
    }
}

@Composable
fun ControlPanel(
    viewModel: XRMainViewModel,
    devices: List<com.sherlock.xr.viewmodel.AnchoredDevice>,
    isDebugMode: Boolean,
    onToggleDebug: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                androidx.compose.ui.graphics.Color(0xFF2C2C2E).copy(alpha = 0.8f),
                androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { viewModel.triggerScan() }) {
            Icon(Icons.Filled.Search, contentDescription = "Scan")
            Spacer(Modifier.width(8.dp))
            Text("Scan")
        }
        Spacer(Modifier.width(16.dp))
        Button(onClick = { viewModel.clearData() }, enabled = devices.isNotEmpty()) {
            Icon(Icons.Filled.Delete, contentDescription = "Clear")
            Spacer(Modifier.width(8.dp))
            Text("Clear")
        }
        Spacer(Modifier.width(16.dp))
        Button(onClick = onToggleDebug) {
            Text(if (isDebugMode) "Exit Debug" else "Debug Mode")
        }
    }
}

/**
 * Hiển thị live camera feed bằng CameraX PreviewView.
 * PreviewView là một Android View thực nên cần AndroidView để nhúng vào Compose.
 * Kết nối với Preview use case qua setSurfaceProvider.
 */
@Composable
fun CameraPreview(viewModel: XRMainViewModel, modifier: Modifier = Modifier) {
    val previewUseCase by viewModel.previewUseCase.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            // Mỗi khi previewUseCase thay đổi, kết nối SurfaceProvider mới vào PreviewView
            previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
        },
        modifier = modifier
    )
}
