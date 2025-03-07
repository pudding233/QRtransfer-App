package com.pudding233.qrtransfer

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        if (image.format == ImageFormat.YUV_420_888) {
            val data = image.planes[0].buffer.toByteArray()
            val source = PlanarYUVLuminanceSource(
                data,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )

            try {
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decode(bitmap)
                onQRCodeDetected(result.text)
            } catch (e: NotFoundException) {
                // QR码未找到，忽略
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        image.close()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }
} 