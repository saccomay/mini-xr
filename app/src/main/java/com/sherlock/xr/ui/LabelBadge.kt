package com.sherlock.xr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sherlock.xr.data.DeviceInfo
import com.sherlock.xr.data.DeviceStatus

@Composable
fun LabelBadge(
    info: DeviceInfo,
    onClick: () -> Unit
) {
    val bgColor = when (info.status) {
        DeviceStatus.OK -> Color(0xFF4CAF50)
        DeviceStatus.WARNING -> Color(0xFFFFC107)
        DeviceStatus.ERROR -> Color(0xFFF44336)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${info.status.name} | ${info.model}",
            color = bgColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}
