package com.roadar.app.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.min
import kotlin.math.roundToInt

internal const val YOLO26_INPUT_SIZE = 640
private const val PIXEL_COUNT = YOLO26_INPUT_SIZE * YOLO26_INPUT_SIZE

internal data class ModelTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val scale: Float,
    val padLeft: Int,
    val padTop: Int
)

internal data class PreparedModelInput(
    val nchwRgb: FloatArray,
    val transform: ModelTransform
)

internal object Yolo26Preprocessor {
    fun fromImageProxy(image: ImageProxy): PreparedModelInput {
        val bitmap = imageToBitmap(image)
        return try {
            fromBitmap(bitmap, image.imageInfo.rotationDegrees)
        } finally {
            bitmap.recycle()
        }
    }

    fun fromBitmap(source: Bitmap, rotationDegrees: Int = 0): PreparedModelInput {
        val upright = if (rotationDegrees == 0) {
            source
        } else {
            val rotation = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotation, true)
        }

        try {
            val scale = min(
                YOLO26_INPUT_SIZE.toFloat() / upright.width,
                YOLO26_INPUT_SIZE.toFloat() / upright.height
            )
            val resizedWidth = (upright.width * scale).roundToInt()
            val resizedHeight = (upright.height * scale).roundToInt()
            val horizontalPadding = YOLO26_INPUT_SIZE - resizedWidth
            val verticalPadding = YOLO26_INPUT_SIZE - resizedHeight
            val padLeft = (horizontalPadding / 2f - 0.1f).roundToInt()
            val padTop = (verticalPadding / 2f - 0.1f).roundToInt()

            val letterboxed = Bitmap.createBitmap(
                YOLO26_INPUT_SIZE,
                YOLO26_INPUT_SIZE,
                Bitmap.Config.ARGB_8888
            )
            try {
                val canvas = Canvas(letterboxed)
                canvas.drawColor(Color.rgb(114, 114, 114))
                canvas.drawBitmap(
                    upright,
                    null,
                    Rect(padLeft, padTop, padLeft + resizedWidth, padTop + resizedHeight),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )

                val pixels = IntArray(PIXEL_COUNT)
                letterboxed.getPixels(
                    pixels,
                    0,
                    YOLO26_INPUT_SIZE,
                    0,
                    0,
                    YOLO26_INPUT_SIZE,
                    YOLO26_INPUT_SIZE
                )
                val input = FloatArray(PIXEL_COUNT * 3)
                for (index in pixels.indices) {
                    val pixel = pixels[index]
                    input[index] = ((pixel shr 16) and 0xff) / 255f
                    input[PIXEL_COUNT + index] = ((pixel shr 8) and 0xff) / 255f
                    input[PIXEL_COUNT * 2 + index] = (pixel and 0xff) / 255f
                }

                return PreparedModelInput(
                    nchwRgb = input,
                    transform = ModelTransform(
                        sourceWidth = upright.width,
                        sourceHeight = upright.height,
                        scale = scale,
                        padLeft = padLeft,
                        padTop = padTop
                    )
                )
            } finally {
                letterboxed.recycle()
            }
        } finally {
            if (upright !== source) upright.recycle()
        }
    }

    private fun imageToBitmap(image: ImageProxy): Bitmap {
        require(image.planes.size == 3) { "Expected a YUV_420_888 camera frame." }

        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val pixels = IntArray(width * height)
        val yStart = yBuffer.position()
        val uStart = uBuffer.position()
        val vStart = vBuffer.position()

        for (row in 0 until height) {
            val sourceY = crop.top + row
            val yRow = yStart + sourceY * yPlane.rowStride + crop.left * yPlane.pixelStride
            val uvRow = (sourceY / 2) * uPlane.rowStride
            for (column in 0 until width) {
                val sourceX = crop.left + column
                val y = yBuffer.get(yRow + column * yPlane.pixelStride).toInt() and 0xff
                val uvColumn = (sourceX / 2) * uPlane.pixelStride
                val u = (uBuffer.get(uStart + uvRow + uvColumn).toInt() and 0xff) - 128
                val v = (vBuffer.get(vStart + (sourceY / 2) * vPlane.rowStride +
                    (sourceX / 2) * vPlane.pixelStride).toInt() and 0xff) - 128
                val luma = (y - 16).coerceAtLeast(0)
                val red = ((298 * luma + 409 * v + 128) shr 8).coerceIn(0, 255)
                val green = ((298 * luma - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val blue = ((298 * luma + 516 * u + 128) shr 8).coerceIn(0, 255)
                pixels[row * width + column] =
                    (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
