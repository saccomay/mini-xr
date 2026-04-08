package com.sherlock.xr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
fun XRApp(
    viewModel: XRMainViewModel = viewModel(),
    onSessionReady: ((Any) -> Unit)? = null,
    onScanRequested: (() -> Unit)? = null
) {
    val devices = viewModel.devices.collectAsState().value
    val session = LocalSession.current
    val spatialCapabilities = LocalSpatialCapabilities.current
    val isSpatialUiEnabled = spatialCapabilities.isSpatialUiEnabled

    val isDebugMode = viewModel.isDebugMode.collectAsState().value

    LaunchedEffect(session) {
        if (session != null) {
            onSessionReady?.invoke(session)
        }
    }

    // Chỉ đặt opacity khi bật Spatial UI
    LaunchedEffect(isSpatialUiEnabled) {
        if (isSpatialUiEnabled) {
            session?.scene?.spatialEnvironment?.preferredPassthroughOpacity = 1.0f
        }
    }

    // 2D UI (Chỉ hiện rõ khi ở Home Space / Debug Mode)
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {

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
                    },
                    onScanRequested = { onScanRequested?.invoke() }
            )
        }
    }

    Subspace {
        if (!isDebugMode && isSpatialUiEnabled) {
            SpatialPanel(
                    modifier =
                            SubspaceModifier.offset(
                                            x = 0.dp,
                                            y = Meter(-0.2f).toDp(),
                                            z = Meter(-0.8f).toDp()
                                    )
                                    // Billboard: tự xoay về phía user, giữ thẳng đứng theo trục Y
                                    .gravityAligned()
            ) {
                ControlPanel(
                        viewModel = viewModel,
                        devices = devices,
                        isDebugMode = false,
                        onToggleDebug = {
                            viewModel.toggleDebugMode()
                            session?.scene?.requestHomeSpaceMode()
                        },
                        onScanRequested = { onScanRequested?.invoke() }
                )
            }
        }

        devices.forEach { device ->
            val xDp = Meter(device.worldPosition.x).toDp()
            val yDp = Meter(device.worldPosition.y).toDp()
            val zDp = Meter(device.worldPosition.z).toDp()

            SpatialPanel(
                    modifier = SubspaceModifier.offset(x = xDp, y = yDp, z = zDp).gravityAligned()
            ) { LabelBadge(info = device.info) { viewModel.toggleDetail(device.id) } }

            if (device.isDetailOpen) {
                val popupYDp = Meter(device.worldPosition.y + 0.3f).toDp()
                SpatialPanel(
                        modifier =
                                SubspaceModifier.offset(x = xDp, y = popupYDp, z = zDp)
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
        onToggleDebug: () -> Unit,
        onScanRequested: () -> Unit
) {
    Row(
            modifier =
                    Modifier.background(
                                    androidx.compose.ui.graphics.Color(0xFF2C2C2E)
                                            .copy(alpha = 0.8f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = {
            viewModel.triggerScan()
            onScanRequested()
        }) {
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
        Button(onClick = onToggleDebug) { Text(if (isDebugMode) "Exit Debug" else "Debug Mode") }
    }
}
