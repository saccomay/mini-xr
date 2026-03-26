package com.sherlock.xr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import com.sherlock.xr.ui.DetailedPopup
import com.sherlock.xr.ui.LabelBadge
import com.sherlock.xr.viewmodel.XRMainViewModel

@Composable
fun XRApp(
    viewModel: XRMainViewModel = viewModel()
) {
    val devices = viewModel.devices.collectAsState().value
    val session = LocalSession.current

    // Observe detections and perform raycasts
    LaunchedEffect(viewModel.pendingRaycasts) {
        viewModel.pendingRaycasts.collect { (screenX, screenY) ->
            session?.let { xrSession ->
                // In alpha12+, hit testing APIs might have changed 
                // We'll use the mock logic to verify the UI build first
                viewModel.onRaycastSuccessMockDataset()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { viewModel.onRaycastSuccessMockDataset() },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text("Detect Device (Mock)")
        }
    }

    Subspace {
        devices.forEach { device ->
            SpatialPanel {
                LabelBadge(info = device.info) {
                    viewModel.toggleDetail(device.id)
                }
            }

            if (device.isDetailOpen) {
                SpatialPanel {
                    DetailedPopup(info = device.info)
                }
            }
        }
    }
}
