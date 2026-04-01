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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.xr.compose.subspace.layout.rotateToLookAtUser
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

    // Phân tích toạ độ 2D từ CameraX và ánh xạ thành toạ độ Không gian 3D (Raycast Approximation)
    LaunchedEffect(viewModel.pendingRaycasts) {
        viewModel.pendingRaycasts.collect { data ->
            session?.let { _ ->
                if (data.size >= 4) {
                    val pixelX = data[0]   // tâm bbox theo X trong rotatedImage space
                    val pixelY = data[1]   // tâm bbox theo Y trong rotatedImage space
                    val imgW   = data[2]   // chiều rộng rotatedImage (portrait)
                    val imgH   = data[3]   // chiều cao rotatedImage (portrait)

                    // FOV của camera thường dung trên thiết bị XR (gần với passthrough camera)
                    // 63° ngang (theo trục ngắn của ảnh portrait = chiều X)
                    // 48° dọc (theo trục dài của ảnh portrait = chiều Y)
                    val halfFovX = 63f / 2f  // °31.5°
                    val halfFovY = 48f / 2f  // °24°

                    // Chuẩn hoá tọa độ pixel vào khoảng (-1..+1)
                    // X: 0 = trái, imgW = phải → -1 = trái, +1 = phải
                    // Y: 0 = trên, imgH = dưới → +1 = trên, -1 = dưới (y-flip do hệ trục 3D y-up)
                    val ndcX = (pixelX / imgW) * 2f - 1f
                    val ndcY = -((pixelY / imgH) * 2f - 1f)  // y-flip

                    // Tính góc quay tương ứng (theo đơn vị radian)
                    val yawRad   = Math.toRadians((ndcX * halfFovX).toDouble()).toFloat()  // quay trái/phải
                    val pitchRad = Math.toRadians((ndcY * halfFovY).toDouble()).toFloat()  // quay lên/xuống

                    // FIX: Công thức spherical -> cartesian đúng:
                    // dirX = sin(yaw)  * cos(pitch)  ← FIX: nhân cos(pitch) để trục X chính xác
                    // dirY = sin(pitch)              ← Đúng
                    // dirZ = -cos(yaw) * cos(pitch)  ← FIX: nhân cos(pitch) để trục Z chính xác
                    // Thiếu cos(pitch) khiến đối tượng ở góc ngắn nhìn sẽ bị đặt sai vị trí
                    val cosPitch = kotlin.math.cos(pitchRad.toDouble()).toFloat()
                    val dirX =  kotlin.math.sin(yawRad.toDouble()).toFloat()   * cosPitch
                    val dirY =  kotlin.math.sin(pitchRad.toDouble()).toFloat()
                    val dirZ = -kotlin.math.cos(yawRad.toDouble()).toFloat()   * cosPitch

                    // Đặt label cách mắt người dùng 1.5 mét (1 mét = 1000 dp trong XR space)
                    val distanceMeters = 1.5f
                    val targetPos = com.sherlock.xr.viewmodel.MyVector3(
                        dirX * distanceMeters,
                        dirY * distanceMeters,
                        dirZ * distanceMeters
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
                    // Billboard: tự xoay về phía user, giữ thẳng đứng theo trục Y
                    .rotateToLookAtUser()
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
                    .rotateToLookAtUser()
                    .gravityAligned()
            ) { LabelBadge(info = device.info) { viewModel.toggleDetail(device.id) } }

            if (device.isDetailOpen) {
                val popupYDp = Meter(device.worldPosition.y + 0.3f).toDp()
                SpatialPanel(
                    modifier = SubspaceModifier
                        .offset(x = xDp, y = popupYDp, z = zDp)
                        .rotateToLookAtUser()
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
