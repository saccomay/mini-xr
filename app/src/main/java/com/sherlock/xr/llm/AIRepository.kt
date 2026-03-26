package com.sherlock.xr.llm

import kotlinx.coroutines.delay
import kotlin.random.Random

object AIRepository {
    /** giả lập trả lời gợi ý fix lỗi sau 1.2s */
    suspend fun fetchFixSuggestionMock(label: String): String {
        delay(3200)
        val extra = listOf(
            "Kiểm tra nhật ký hệ thống (logcat) để xác định stack trace liên quan.",
            "Đảm bảo quyền (camera/storage) đã được cấp và xử lý khi bị từ chối.",
            "Thử xoá cache/ dữ liệu, hoặc restart session AR khi track mất ổn định.",
            "Xác minh shader có '#version 300 es' và khớp layout thuộc tính VertexBuffer.",
            "Giảm kích thước texture/bitmap nếu bị OOM hoặc upload GL thất bại."
        ).shuffled().take(3).joinToString("\n• ")

        return """
      Nhãn lỗi: $label

      Gợi ý khắc phục:
      • Kiểm tra điều kiện gây lỗi và tái tạo tối thiểu trong môi trường debug.
      • Bật logging chi tiết cho phần render và mạng.
      • $extra

      Nếu lỗi liên quan đến mạng/API, thử thêm retry+backoff và timeouts phù hợp.
    """.trimIndent()
    }
}
