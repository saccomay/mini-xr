package com.sherlock.xr.classification

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import com.sherlock.xr.classification.utils.YuvToRgbConverter

/**
 * Describes a common interface for ObjectDetector that can
 * infer object labels in a given [Image] and gives results in a list of [DetectedObjectResult].
 */
abstract class ObjectDetector(val context: Context) {
  val yuvConverter = YuvToRgbConverter(context)

  /**
   * Infers a list of [DetectedObjectResult] given a camera image frame.
   */
  abstract suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult>

  /**
   * Converts a YUV image to a [Bitmap] using [YuvToRgbConverter].
   */
  fun convertYuv(image: Image): Bitmap {
    return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
      yuvConverter.yuvToRgb(image, this)
    }
  }
}