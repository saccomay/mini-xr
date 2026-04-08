package com.sherlock.xr.spatial

import com.sherlock.xr.viewmodel.MyVector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chuyển tọa độ 2D (tâm bbox ML Kit) sang tọa độ 3D trong không gian XR.
 *
 * Thuật toán: FOV-based Spherical Projection
 * ─────────────────────────────────────────────────────────────────────────
 *   pixel(cx, cy)
 *       │
 *       ▼  normalize về NDC [-1, +1] + y-flip (2D y-down → 3D y-up)
 *   ndcX ∈ [-1, +1],  ndcY ∈ [-1, +1]
 *       │
 *       ▼  scale bằng half-FOV (radians)
 *   yawRad   = ndcX * halfFovX   (lệch trái / phải so với trục quang học)
 *   pitchRad = ndcY * halfFovY   (lệch lên / xuống)
 *       │
 *       ▼  Spherical → Cartesian (right-handed, camera forward = -Z)
 *   dirX =  sin(yaw) * cos(pitch)
 *   dirY =  sin(pitch)
 *   dirZ = -cos(yaw) * cos(pitch)   ← -Z là "phía trước" mặt kính
 *       │
 *       ▼  scale theo depthMeters
 *   worldPos = dir * depth          → truyền vào SubspaceModifier.offset
 *
 * ⚠ Giới hạn hiện tại:
 *   Tọa độ được đặt TƯƠNG ĐỐI ActivitySpace origin (không phải head user).
 *   Điều này đúng nếu user đứng gần vị trí khởi tạo app.
 *   Phase 2: kết hợp với ArDevice headPose để bù trừ vị trí head.
 *
 * @param fovXDeg  FOV ngang camera (degrees). Giá trị ước tính cho passthrough
 *                 camera trên thiết bị XR; chỉnh lại nếu biết intrinsics thực.
 * @param fovYDeg  FOV dọc camera (degrees).
 */
object CoordinateMapper {

    /**
     * Tính vị trí 3D của label từ tâm bounding box 2D.
     *
     * @param pixelX   Tọa độ X tâm bbox trong ảnh đã rotate (pixels)
     * @param pixelY   Tọa độ Y tâm bbox trong ảnh đã rotate (pixels)
     * @param imgW     Chiều rộng ảnh đã rotate (pixels)
     * @param imgH     Chiều cao ảnh đã rotate (pixels)
     * @param fovXDeg  FOV ngang camera passthrough (degrees)
     * @param fovYDeg  FOV dọc camera passthrough (degrees)
     * @param depthM   Khoảng cách ước tính từ user đến thiết bị vật lý (meters).
     *                 Thiết bị trên rack → 0.5–1.0m là phù hợp.
     * @return         MyVector3 tọa độ 3D (meters) để set SubspaceModifier.offset
     */
    fun pixelToWorld(
        pixelX: Float,
        pixelY: Float,
        imgW: Float,
        imgH: Float,
        fovXDeg: Float = 63f,
        fovYDeg: Float = 48f,
        depthM: Float = 0.6f,
    ): MyVector3 {
        // ── Bước 1: Normalized Device Coordinates [-1, +1] ─────────────────
        // NDC X:  pixel.left(0)→pixel.right(imgW)  =  NDC -1 → +1
        // NDC Y:  pixel.top(0)→pixel.bottom(imgH)  =  NDC +1 → -1  (y-flip)
        val ndcX =  (pixelX / imgW) * 2f - 1f
        val ndcY = -((pixelY / imgH) * 2f - 1f)

        // ── Bước 2: Góc lệch tính từ optical axis (radians) ────────────────
        val halfFovXRad = Math.toRadians((fovXDeg / 2.0)).toFloat()
        val halfFovYRad = Math.toRadians((fovYDeg / 2.0)).toFloat()
        val yawRad      = ndcX * halfFovXRad   // dương = lệch phải
        val pitchRad    = ndcY * halfFovYRad   // dương = lệch lên

        // ── Bước 3: Spherical → Cartesian ──────────────────────────────────
        // Right-handed coordinate: X right, Y up, Z backward (forward = -Z)
        // Công thức đúng khi cả yaw và pitch khác 0 (nhân cos(pitch) cho X, Z):
        val cosPitch = cos(pitchRad)
        val dirX =  sin(yawRad) * cosPitch   // lệch phải → X+
        val dirY =  sin(pitchRad)             // lệch lên  → Y+
        val dirZ = -cos(yawRad) * cosPitch   // phía trước→ Z-

        // ── Bước 4: Scale theo depth ────────────────────────────────────────
        return MyVector3(
            x = dirX * depthM,
            y = dirY * depthM,
            z = dirZ * depthM,
        )
    }
}
