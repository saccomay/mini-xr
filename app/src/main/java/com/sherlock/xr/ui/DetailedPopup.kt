package com.sherlock.xr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sherlock.xr.data.DeviceInfo

@Composable
fun DetailedPopup(info: DeviceInfo) {
    // Trụ chứa bong bóng và hình tam giác chỉ xuống
    Column(
        modifier = Modifier.width(420.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hộp thông báo chính
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C2C2E).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
        // Header
        Text(
            text = "Device Model: ${info.model}",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Serial: ${info.serial}",
            color = Color.LightGray,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Divider(color = Color.Gray, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        // Device Info List
        Column(modifier = Modifier.fillMaxWidth()) {
            InfoRow("OS", info.osName)
            InfoRow("Battery", "${info.battery}%")
            InfoRow("User", info.user)
            InfoRow("Last Active", info.time)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // RAG / Troubleshooting Steps (Mocked via Task)
        Text(
            text = "Troubleshooting / Task Steps",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Mock steps
        val steps = listOf(
            "Step 1: Verify power connection",
            "Step 2: Check active process (${info.task})",
            "Step 3: Run ADB diagnostics on ${info.model}",
            "Step 4: Sync logs to backend database",
            "Step 5: Review RAG results for error codes"
        )

        LazyColumn(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(steps) { step ->
                Text(
                    text = step,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        } // Đóng Hộp thông báo chính

        // Vẽ tam giác chỉ xuống (phần mũi của Speech Bubble)
        Canvas(modifier = Modifier.size(width = 40.dp, height = 24.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(canvasWidth, 0f)
                lineTo(canvasWidth / 2f, canvasHeight)
                close()
            }
            drawPath(path, Color(0xFF2C2C2E).copy(alpha = 0.7f))
        }
    } // Đóng Trụ chứa bong bóng
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 16.sp)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
