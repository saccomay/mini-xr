package com.sherlock.xr.data

import kotlinx.coroutines.delay
import kotlin.random.Random

enum class DeviceStatus {
    OK, WARNING, ERROR
}

data class DeviceInfo(
    val model: String,
    val serial: String,
    val osName: String,
    val battery: Int,
    val task: String,
    val user: String,
    val time: String,
    val status: DeviceStatus,
)

object DeviceRepository {
    /** Simulate an API call that resolves in ~2 seconds. */
    suspend fun fetchInfoMock(deviceId: String): DeviceInfo {
        delay(2000) // 2s network latency

        // Fake data (randomize a bit so you see changes)
        val battery = Random.nextInt(10, 100)
        val status = when {
            battery < 20 -> DeviceStatus.WARNING
            Random.nextFloat() < 0.1f -> DeviceStatus.ERROR
            else -> DeviceStatus.OK
        }
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

        return DeviceInfo(
            model = "Pixel 7 Pro",
            serial = "SN-${Random.nextInt(100000, 999999)}",
            osName = "Android 14 (U)",
            battery = battery,
            task = listOf("Idle", "Scanning", "Syncing", "Recording").random(),
            user = listOf("Alice", "Bob", "Carol", "Du").random(),
            time = now,
            status = status
        )
    }

    /** One call that returns info for all ids after ~2s. */
    suspend fun fetchInfoBulkMock(ids: List<String>): Map<String, DeviceInfo> {
        delay(2000) // simulate network
        return ids.distinct().associateWith { id ->
            val battery = Random.nextInt(10, 100)
            val status = when {
                battery < 20 -> DeviceStatus.WARNING
                Random.nextFloat() < 0.1f -> DeviceStatus.ERROR
                else -> DeviceStatus.OK
            }
            val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())

            DeviceInfo(
                model = "A536B",
                serial = "SN-${Random.nextInt(100000, 999999)}",
                osName = "Android 14 (U)",
                battery = battery,
                task = listOf("Idle", "Scanning", "Syncing", "Recording").random(),
                user = listOf("Alice", "Bob", "Carol", "Du").random(),
                time = now,
                status = status
            )
        }
    }
}