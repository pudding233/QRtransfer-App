package com.pudding233.qrtransfer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

/**
 * 文件工具类，提供文件选择和管理功能
 */
object FileUtil {
    private const val TAG = "FileUtil"

    /**
     * 根据URI复制文件到临时目录
     * @param context 上下文
     * @param uri 文件的URI
     * @return 复制后的文件对象，如果复制失败返回null
     */
    fun copyFileToTemp(context: Context, uri: Uri): File? {
        try {
            val tempDir = File(context.cacheDir, "transfer_temp")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            // 获取文件名
            val fileName = getFileName(context, uri)
            val tempFile = File(tempDir, fileName ?: "temp_file_${System.currentTimeMillis()}")

            // 打开输入流
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "无法打开输入流")
                return null
            }

            // 复制文件
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(4 * 1024) // 4K buffer
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.d(TAG, "文件已复制到: ${tempFile.absolutePath}, 大小: ${formatFileSize(tempFile.length())}")
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 获取文件名
     * @param context 上下文
     * @param uri 文件的URI
     * @return 文件名，如果找不到则返回null
     */
    fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = it.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }

    /**
     * 获取公共下载目录
     * @return 下载目录
     */
    fun getPublicDownloadDir(): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 格式化文件大小
     * @param size 文件大小（字节）
     * @return 格式化后的大小字符串（如：1.2 MB）
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        
        val formatter = DecimalFormat("#,##0.##")
        return formatter.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * 计算文件传输速度
     * @param bytesPerSecond 每秒传输的字节数
     * @return 格式化后的速度字符串
     */
    fun formatTransferSpeed(bytesPerSecond: Long): String {
        return formatFileSize(bytesPerSecond) + "/s"
    }
} 