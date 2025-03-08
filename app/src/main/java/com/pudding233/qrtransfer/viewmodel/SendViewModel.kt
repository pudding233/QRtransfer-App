package com.pudding233.qrtransfer.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pudding233.qrtransfer.codec.RaptorQCodec
import com.pudding233.qrtransfer.model.DataPacket
import com.pudding233.qrtransfer.util.FileUtil
import com.pudding233.qrtransfer.util.QRCodeUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 发送文件的视图模型
 */
class SendViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "SendViewModel"
        private const val QR_CODE_UPDATE_INTERVAL_MS = 50L // 每秒20张QR码
    }
    
    // RaptorQ编解码器
    private val codec = RaptorQCodec()
    
    // 发送状态
    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()
    
    // 当前显示的QR码
    private val _currentQrCode = MutableStateFlow<Bitmap?>(null)
    val currentQrCode: StateFlow<Bitmap?> = _currentQrCode.asStateFlow()
    
    // 发送统计信息
    private val _sendStats = MutableStateFlow<SendStats?>(null)
    val sendStats: StateFlow<SendStats?> = _sendStats.asStateFlow()
    
    // 发送任务
    private var sendJob: Job? = null
    
    // 发送计数 - 这是一个持续增长的计数器，确保不重复发送相同的数据包
    private var packetCounter = AtomicInteger(0)
    private var startTime = 0L

    /**
     * 选择并准备发送文件
     */
    fun prepareFile(context: Context, uri: Uri): Boolean {
        return try {
            // 如果正在发送，先停止
            stopSending()
            
            // 复制文件到临时目录
            val file = FileUtil.copyFileToTemp(context, uri)
            if (file == null) {
                _sendState.value = SendState.Error("无法复制文件")
                return false
            }
            
            // 初始化编码器
            codec.initializeEncoder(file)
            
            // 更新状态
            _sendState.value = SendState.Prepared(
                file = file,
                fileName = file.name,
                fileSize = file.length()
            )
            
            Log.d(TAG, "文件准备完成: ${file.name}, 大小: ${FileUtil.formatFileSize(file.length())}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "准备文件失败: ${e.message}", e)
            _sendState.value = SendState.Error("准备文件失败: ${e.message}")
            false
        }
    }

    /**
     * 开始发送文件
     */
    fun startSending() {
        val currentState = _sendState.value
        if (currentState !is SendState.Prepared) {
            Log.e(TAG, "无法开始发送: 文件未准备好")
            return
        }
        
        // 更新状态
        _sendState.value = SendState.Sending(
            fileName = currentState.fileName,
            fileSize = currentState.fileSize
        )
        
        // 重置统计信息
        startTime = System.currentTimeMillis()
        
        // 启动发送任务
        sendJob = viewModelScope.launch {
            try {
                // 持续生成和显示不同的QR码数据包
                var lastPacketLength = 0
                
                while (isActive) {
                    // 生成数据包 - 由于改进的RaptorQCodec实现，每次都会生成不同的数据包
                    // 当前计数值仅作为记录用途，实际生成的包索引由RaptorQCodec内部控制
                    val currentCount = packetCounter.getAndIncrement()
                    val packet = codec.generateEncodingPacket(currentCount)
                    
                    // 将数据包转换为JSON字符串
                    val jsonString = packet.toString()
                    
                    // 检查是否超出QR码大小限制，如果超出则记录警告
                    if (jsonString.length > QRCodeUtil.MAX_QR_CONTENT_LENGTH) {
                        Log.w(TAG, "警告：数据包大小(${jsonString.length})超出建议的QR码容量(${QRCodeUtil.MAX_QR_CONTENT_LENGTH})，可能导致扫描困难")
                    }
                    
                    // 记录QR码变化情况
                    if (currentCount % 10 == 0 || lastPacketLength != jsonString.length) {
                        Log.d(TAG, "发送QR码数据包 #$currentCount, 大小: ${jsonString.length}字符, 块索引: ${packet.header.blockIndex}")
                        lastPacketLength = jsonString.length
                    }
                    
                    // 生成QR码
                    val qrBitmap = QRCodeUtil.generateQRCode(jsonString)
                    if (qrBitmap != null) {
                        _currentQrCode.value = qrBitmap
                        
                        // 更新统计信息
                        updateStats()
                    } else {
                        Log.e(TAG, "生成QR码失败，将跳过此数据包: #$currentCount")
                    }
                    
                    // 等待一段时间再显示下一个QR码
                    delay(QR_CODE_UPDATE_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送过程中出错: ${e.message}", e)
                _sendState.value = SendState.Error("发送过程中出错: ${e.message}")
            }
        }
        
        Log.d(TAG, "开始发送文件: ${currentState.fileName}")
    }

    /**
     * 停止发送
     */
    fun stopSending() {
        sendJob?.cancel()
        sendJob = null
        
        if (_sendState.value is SendState.Sending) {
            val current = _sendState.value as SendState.Sending
            _sendState.value = SendState.Prepared(
                file = null,
                fileName = current.fileName,
                fileSize = current.fileSize
            )
        }
        
        _currentQrCode.value = null
        Log.d(TAG, "停止发送文件")
    }

    /**
     * 更新统计信息
     */
    private fun updateStats() {
        val sentPackets = packetCounter.get()
        val elapsedTimeMs = System.currentTimeMillis() - startTime
        val packetsPerSecond = if (elapsedTimeMs > 0) sentPackets * 1000f / elapsedTimeMs else 0f
        
        _sendStats.value = SendStats(
            sentPackets = sentPackets,
            elapsedTimeMs = elapsedTimeMs,
            packetsPerSecond = packetsPerSecond
        )
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        stopSending()
    }

    /**
     * 发送状态
     */
    sealed class SendState {
        object Idle : SendState()
        
        data class Prepared(
            val file: File?,
            val fileName: String,
            val fileSize: Long
        ) : SendState()
        
        data class Sending(
            val fileName: String,
            val fileSize: Long
        ) : SendState()
        
        data class Error(val message: String) : SendState()
    }

    /**
     * 发送统计信息
     */
    data class SendStats(
        val sentPackets: Int,
        val elapsedTimeMs: Long,
        val packetsPerSecond: Float
    )
} 