package com.sherlock.xr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.sherlock.xr.viewmodel.XRMainViewModel
import androidx.xr.arcore.getNativeData
import androidx.xr.runtime.Session

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: XRMainViewModel

    companion object {
        init {
            System.loadLibrary("qr_tracker_native")
        }
    }

    private external fun startQrTrackerNative(nativeData: Any)

    external fun toggleQrTrackerNative(enable: Boolean)

    @androidx.annotation.Keep
    fun onQrCodeScanned(qrCode: String) {
        Log.i("MainActivity", "Thành công quét được QR: $qrCode")
        // Tạm thời hiển thị nó ở độ sâu -0.5m để test luồng UI
        viewModel.onQrCodeScanned(qrCode.trim(), 0f, 0f, -0.5f)
    }

    @kotlin.OptIn(androidx.xr.arcore.UnstableNativeResourceApi::class)
    fun initNativeTracker(sessionObj: Any?) {
        Log.i("MainActivity", "initNativeTracker invoked. sessionObj=$sessionObj")
        if (sessionObj == null) return
        
        val session = sessionObj as? androidx.xr.runtime.Session
        if (session == null) {
            Log.e("MainActivity", "initNativeTracker failed to cast sessionObj to runtime Session! Class: ${sessionObj.javaClass.name}")
            return
        }
        
        try {
            Log.i("MainActivity", "Calling session.getNativeData()...")
            val nativeData = session.getNativeData()
            Log.i("MainActivity", "Extracted nativeData. Calling startQrTrackerNative...")
            startQrTrackerNative(nativeData)
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi lấy NativeData", e)
        }
    }

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted: Boolean ->
                if (isGranted) {
                    Log.i("MainActivity", "Permission granted")
                } else {
                    Log.e("MainActivity", "Scene Understanding permission denied")
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[XRMainViewModel::class.java]
        // Bypass permission checks for now
        // if (ContextCompat.checkSelfPermission(this, "android.permission.SCENE_UNDERSTANDING") !=
        //                 PackageManager.PERMISSION_GRANTED
        // ) {
        //     requestPermissionLauncher.launch("android.permission.SCENE_UNDERSTANDING")
        // }

        setContent {
            MaterialTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    // Entry point for XR Compose UI
                    XRApp(viewModel, 
                        onSessionReady = { session ->
                            initNativeTracker(session)
                        },
                        onScanRequested = {
                            toggleQrTrackerNative(true)
                        }
                    )
                }
            }
        }
    }

    // Gán cứng phím Volume (Tăng/Giảm âm lượng) thành nút bấm vật lý để test trigger.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
                        keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        ) {
            viewModel.triggerScan()
            toggleQrTrackerNative(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
