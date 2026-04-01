/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sherlock.xr.classification

import android.app.Activity
import android.media.Image
import com.sherlock.xr.classification.utils.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.asDeferred

/**
 * Analyzes an image using ML Kit.
 */
class MLKitObjectDetector(context: Activity) : ObjectDetector(context) {
  // To use a custom model, follow steps on https://developers.google.com/ml-kit/vision/object-detection/custom-models/android.
  // val model = LocalModel.Builder().setAssetFilePath("inception_v4_1_metadata_1.tflite").build()
  // val builder = CustomObjectDetectorOptions.Builder(model)

  // For the ML Kit default model, use the following:
  val builder = ObjectDetectorOptions.Builder()

  private val options = builder
    .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
    .enableClassification()
    .enableMultipleObjects()
    .build()
  private val detector = ObjectDetection.getClient(options)

  override suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult> {
    // `image` is in YUV (https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireCameraImage()),
    val convertYuv = convertYuv(image)

    // Rotate ảnh cho thẳng đứng để model hoạt động chính xác nhất.
    // Sau khi rotate, toàn bộ tọa độ (bounding box, center) đều ở trong không gian ảnh đã-rotate.
    val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)

    // Đưa ảnh vào ML Kit với rotation = 0 vì ảnh đã được rotate thủ công ở bước trên.
    // Điều này đảm bảo ML Kit trả về bounding box ĐÃ đúng chiều – KHÔNG cần rotate lại.
    val inputImage = InputImage.fromBitmap(rotatedImage, 0)

    val mlKitDetectedObjects = detector.process(inputImage).asDeferred().await()
    return mlKitDetectedObjects.mapNotNull { obj ->
      val bestLabel = obj.labels.maxByOrNull { label -> label.confidence } ?: return@mapNotNull null

      // BUG FIX: Không gọi rotateCoordinates() ở đây!
      // Tọa độ center của bbox ĐÃ nằm trong không gian ảnh rotatedImage (đúng chiều).
      // Nếu gọi thêm rotateCoordinates() sẽ bị "xoay 2 lần" → toạ độ sai.
      val centerX = obj.boundingBox.exactCenterX().toInt()
      val centerY = obj.boundingBox.exactCenterY().toInt()

      DetectedObjectResult(
        confidence = bestLabel.confidence,
        label = bestLabel.text,
        centerCoordinate = centerX to centerY,  // Tọa độ đúng trong rotatedImage space
        rect = obj.boundingBox,                  // Bounding box đúng trong rotatedImage space
        bitMap = rotatedImage
      )
    }
  }

  @Suppress("USELESS_IS_CHECK")
  fun hasCustomModel() = builder is CustomObjectDetectorOptions.Builder
}