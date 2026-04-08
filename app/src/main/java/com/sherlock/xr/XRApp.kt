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
import androidx.xr.compose.subspace.layout.gravityAligned
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.unit.Meter
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

    // [ĐÃ XÓA] LaunchedEffect pendingRaycasts:
    // Việc tính tọa độ 3D đã được chuyển hoàn toàn vào ViewModel.processCameraFrame()
    // thông qua CoordinateMapper.pixelToWorld(). Không cần UI layer can thiệp nữa.

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
        // Scale theo FILL_CENTER của PreviewView: dùng maxOf để fill toàn màn hình (crop phần thừa)
        if (isDebugMode && debugDetections.isNotEmpty()) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                debugDetections.forEach { detection ->
                    val rect = detection.boundingBox
                    val imgW = detection.imageWidth.toFloat().coerceAtLeast(1f)
                    val imgH = detection.imageHeight.toFloat().coerceAtLeast(1f)

                    // FILL_CENTER: scale theo chiều lớn hơn để fill toàn canvas
                    val scale = maxOf(size.width / imgW, size.height / imgH)

                    // Offset để căn giữa (giống PreviewView căn giữa sau khi crop)
                    val offsetX = (size.width  - imgW * scale) / 2f
                    val offsetY = (size.height - imgH * scale) / 2f

                    // Vẽ bounding box màu đỏ
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.Red,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            rect.left  * scale + offsetX,
                            rect.top   * scale + offsetY
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            rect.width()  * scale,
                            rect.height() * scale
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )

                    // Vẽ crosshair (+) tại tâm bbox để kiểm tra độ chính xác
                    val cx = rect.exactCenterX() * scale + offsetX
                    val cy = rect.exactCenterY() * scale + offsetY
                    val arm = 20f  // độ dài cánh crosshair (px)
                    // Crosshair năm ngang
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Green,
                        start = androidx.compose.ui.geometry.Offset(cx - arm, cy),
                        end   = androidx.compose.ui.geometry.Offset(cx + arm, cy),
                        strokeWidth = 4f
                    )
                    // Crosshair thẳng đứng
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Green,
                        start = androidx.compose.ui.geometry.Offset(cx, cy - arm),
                        end   = androidx.compose.ui.geometry.Offset(cx, cy + arm),
                        strokeWidth = 4f
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
            SpatialPanel(
                modifier = SubspaceModifier
                    .offset(
                        x = 0.dp,
                        y = Meter(-0.2f).toDp(),
                        z = Meter(-0.8f).toDp()
                    )
                    .gravityAligned()
            ) {
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
            val xDp = Meter(device.worldPosition.x).toDp()
            val yDp = Meter(device.worldPosition.y).toDp()
            val zDp = Meter(device.worldPosition.z).toDp()

            SpatialPanel(
                modifier = SubspaceModifier
                    .offset(x = xDp, y = yDp, z = zDp)
                    .gravityAligned()
            ) { LabelBadge(info = device.info) { viewModel.toggleDetail(device.id) } }

            if (device.isDetailOpen) {
                val popupYDp = Meter(device.worldPosition.y + 0.3f).toDp()
                SpatialPanel(
                    modifier = SubspaceModifier
                        .offset(x = xDp, y = popupYDp, z = zDp)
                        .gravityAligned()
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
    val depth by viewModel.scanDepthMeters.collectAsState()

    Column(
        modifier = Modifier
            .background(
                androidx.compose.ui.graphics.Color(0xFF2C2C2E).copy(alpha = 0.8f),
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hàng 1: Scan / Clear / Debug buttons ───────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(if (isDebugMode) "Exit Debug" else "Debug")
            }
        }

        // ── Hàng 2: Depth slider ────────────────────────────────────────────
        // Cho phép kỹ thuật viên điều chỉnh khoảng cách (depth) đến thiết bị
        // mà không cần biên dịch lại. Range 0.3m–3.0m phù hợp cho môi trường lab rack.
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Depth:",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(52.dp)
            )
            Slider(
                value  = depth,
                onValueChange = { viewModel.setDepth(it) },
                valueRange = 0.3f..3.0f,
                steps = 26,   // bước 0.1m: (3.0 - 0.3) / 0.1 - 1 = 26
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format("%.1fm", depth),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )
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
