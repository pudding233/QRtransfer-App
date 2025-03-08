package com.pudding233.qrtransfer.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

/**
 * QR码工具类，处理QR码的生成和扫描
 */
object QRCodeUtil {

    private const val TAG = "QRCodeUtil"
    private const val QR_CODE_SIZE = 800 // 生成的QR码尺寸
    const val MAX_QR_CONTENT_LENGTH = 2000 // QR码内容的最大长度，导出常量供其他类访问
    
    /**
     * 生成QR码图片
     * @param content 需要生成二维码的内容
     * @return 生成的二维码位图
     */
    fun generateQRCode(content: String): Bitmap? {
        try {
            Log.d(TAG, "生成QR码，内容长度: ${content.length}")
            
            // 检查内容长度，如果过长则使用更适合的参数
            if (content.length > MAX_QR_CONTENT_LENGTH) {
                Log.w(TAG, "QR码内容过长(${content.length}字符)，将使用更高版本，可能影响扫描速度")
            }
            
            val hints = EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java)
            hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M // 使用M级别纠错
            hints[com.google.zxing.EncodeHintType.MARGIN] = 1 // 设置最小边距
            
            val qrCodeWriter = QRCodeWriter()
            
            try {
                val bitMatrix: BitMatrix = qrCodeWriter.encode(
                    content, 
                    BarcodeFormat.QR_CODE, 
                    QR_CODE_SIZE, 
                    QR_CODE_SIZE,
                    hints
                )
                
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)
                
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        pixels[y * width + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                    }
                }
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                
                return bitmap
            } catch (e: Exception) {
                // 如果默认设置生成失败，尝试使用更高版本QR码
                Log.w(TAG, "使用标准设置生成QR码失败，尝试增加版本号: ${e.message}")
                
                // 明确指定版本号为40(最大)来处理大数据量
                hints[com.google.zxing.EncodeHintType.QR_VERSION] = 40
                
                val bitMatrix: BitMatrix = qrCodeWriter.encode(
                    content, 
                    BarcodeFormat.QR_CODE, 
                    QR_CODE_SIZE, 
                    QR_CODE_SIZE,
                    hints
                )
                
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)
                
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        pixels[y * width + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                    }
                }
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                
                return bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成QR码失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 从位图中解析QR码
     * @param bitmap 包含QR码的位图
     * @return 解析出的字符串，如果解析失败返回null
     */
    fun decodeQRCode(bitmap: Bitmap): String? {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
            hints[DecodeHintType.TRY_HARDER] = true
            
            val reader = MultiFormatReader()
            reader.setHints(hints)
            
            val result = reader.decode(binaryBitmap)
            return result.text
        } catch (e: Exception) {
            Log.e(TAG, "解析QR码失败: ${e.message}")
            return null
        }
    }
} 