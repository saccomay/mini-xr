package com.sherlock.xr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sherlock.xr.data.DeviceInfo

@Composable
fun DetailedPopup(info: DeviceInfo) {
    Column(
        modifier = Modifier
            .width(400.dp)
            .background(Color.DarkGray, RoundedCornerShape(16.dp))
            .padding(16.dp)
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
    }
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
