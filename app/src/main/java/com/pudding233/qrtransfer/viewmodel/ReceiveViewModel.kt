package com.pudding233.qrtransfer.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pudding233.qrtransfer.codec.RaptorQCodec
import com.pudding233.qrtransfer.model.DataPacket
import com.pudding233.qrtransfer.util.FileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 接收文件的视图模型
 */
class ReceiveViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ReceiveViewModel"
    }
    
    // RaptorQ编解码器
    private val codec = RaptorQCodec()
    
    // 接收状态
    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val receiveState: StateFlow<ReceiveState> = _receiveState.asStateFlow()
    
    // 接收统计信息
    private val _receiveStats = MutableStateFlow<ReceiveStats?>(null)
    val receiveStats: StateFlow<ReceiveStats?> = _receiveStats.asStateFlow()
    
    // 统计相关变量
    private val startTime = AtomicLong(0)
    private val packetsReceived = AtomicInteger(0)
    private val lastUpdateTime = AtomicLong(0)
    private val lastPacketsCount = AtomicInteger(0)
    
    // 传输速度 (每秒包数)
    private val _transferSpeed = MutableStateFlow(0f)
    val transferSpeed: StateFlow<Float> = _transferSpeed.asStateFlow()
    
    // 传输速度 (每秒字节数)
    private val _transferByteSpeed = MutableStateFlow(0L)
    val transferByteSpeed: StateFlow<Long> = _transferByteSpeed.asStateFlow()

    /**
     * 处理扫描结果
     */
    fun processQRCode(qrContent: String) {
        viewModelScope.launch {
            try {
                // 记录收到的QR码基本信息
                Log.i(TAG, "【收到QR码】 内容长度：${qrContent.length} 字符")
                Log.d(TAG, "【QR码首字符】 前10个字符：${qrContent.take(10).replace("\n", "\\n")}${if (qrContent.length > 10) "..." else ""}")
                
                // 尝试解析为JSON，但不强制要求内容为JSON格式
                val isJsonFormat = qrContent.trim().startsWith("{") && qrContent.trim().endsWith("}")
                
                if (isJsonFormat) {
                    // 标准JSON格式处理
                    Log.d(TAG, "【处理路径】 检测到标准JSON格式，使用JSON处理路径")
                    processJsonQrCode(qrContent)
                } else {
                    // 尝试其他格式处理或兼容性处理
                    Log.d(TAG, "【处理路径】 非JSON格式，尝试兼容性处理")
                    processNonJsonQrCode(qrContent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "【处理失败】 QR码处理异常：${e.message}", e)
                Log.e(TAG, "【异常堆栈】", e)
                if (_receiveState.value !is ReceiveState.Receiving) {
                    _receiveState.value = ReceiveState.Error("处理QR码失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 处理JSON格式的QR码
     */
    private suspend fun processJsonQrCode(qrContent: String) {
        try {
            // 解析数据包
            val parseStart = System.currentTimeMillis()
            val dataPacket = try {
                DataPacket.fromJson(qrContent)
            } catch (e: JSONException) {
                Log.w(TAG, "【JSON解析错误】 可能是损坏的QR码：${e.message}")
                return
            } catch (e: Exception) {
                Log.e(TAG, "【解析失败】 无法处理QR码数据：${e.message}")
                return
            }
            val parseTime = System.currentTimeMillis() - parseStart
            
            // 记录数据包详细信息
            Log.i(TAG, "【解析成功】 耗时：${parseTime}毫秒，魔数：${dataPacket.header.magic}")
            Log.i(TAG, "【文件信息】 名称：${dataPacket.header.fileName}，大小：${FileUtil.formatFileSize(dataPacket.header.fileSize)}")
            Log.i(TAG, "【数据包信息】 块索引：${dataPacket.header.blockIndex}，总块数：${dataPacket.header.totalBlocks}")
            Log.i(TAG, "【编码信息】 度：${dataPacket.encoding.degree}，源块数：${dataPacket.encoding.sourceBlocks.size}")
            Log.d(TAG, "【源块列表】 ${dataPacket.encoding.sourceBlocks.joinToString(", ")}")
            Log.d(TAG, "【载荷大小】 ${dataPacket.payload.length} 字符")
            
            // 如果是初始状态，需要初始化解码器
            if (_receiveState.value is ReceiveState.Idle) {
                Log.i(TAG, "【开始接收】 首次收到数据包，初始化解码器")
                startReceiving(dataPacket)
            }
            
            // 处理数据包
            if (_receiveState.value is ReceiveState.Receiving) {
                val processStart = System.currentTimeMillis()
                val needMorePackets = codec.processPacket(dataPacket)
                val processTime = System.currentTimeMillis() - processStart
                
                // 更新统计信息
                updateStats(dataPacket)
                
                // 记录处理结果
                val codecStats = codec.getDecodingStats()
                Log.i(TAG, "【数据包处理】 耗时：${processTime}毫秒，块索引：${dataPacket.header.blockIndex}")
                Log.i(TAG, "【解码进度】 已解码：${codecStats.solvedBlocks}/${codecStats.totalBlocks}，完成率：${(codecStats.progress * 100).toInt()}%")
                
                // 如果不需要更多包，说明接收完成
                if (!needMorePackets) {
                    _receiveState.value = ReceiveState.Completed(codecStats.fileName)
                    Log.i(TAG, "【接收完成】 文件名：${codecStats.fileName}")
                    Log.i(TAG, "【接收统计】 解码块：${codecStats.solvedBlocks}/${codecStats.totalBlocks}，" +
                              "接收包数：${codecStats.receivedPackets}，效率：${(codecStats.solvedBlocks * 100 / codecStats.receivedPackets)}%")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "【JSON处理异常】 ${e.message}", e)
        }
    }
    
    /**
     * 处理非JSON格式的QR码（兼容性处理）
     * 这里可以添加对其他格式的处理逻辑
     */
    private suspend fun processNonJsonQrCode(qrContent: String) {
        try {
            Log.i(TAG, "【非JSON格式】 尝试处理非标准格式的QR码")
            
            // 检查是否是被误识别为UPC_E的情况（8位数字）
            if (qrContent.length == 8 && qrContent.all { it.isDigit() }) {
                Log.w(TAG, "【特殊处理】 检测到8位数字，可能是被误识别为UPC_E的QR码数据")
                
                // 这里可以添加一些特殊的处理逻辑
                // 例如，可以将这些数字视为特定命令或状态码
                
                // 不要在这里返回，继续尝试其他处理方式
            }
            
            // 尝试检测是否是手动修改的JSON格式（有时特殊字符可能会被剥离）
            if (qrContent.contains("\"magic\"") && qrContent.contains("\"fileName\"")) {
                Log.d(TAG, "【兼容模式】 检测到类JSON格式，尝试修复")
                
                // 尝试添加缺失的括号
                val fixedContent = if (!qrContent.startsWith("{")) "{$qrContent" else qrContent
                val fullyFixed = if (!fixedContent.endsWith("}")) "$fixedContent}" else fixedContent
                
                processJsonQrCode(fullyFixed)
                return
            }
            
            // 支持Base64编码的JSON（某些QR码生成器可能使用Base64编码内容）
            if (qrContent.matches(Regex("^[A-Za-z0-9+/=]+$")) && qrContent.length % 4 == 0) {
                try {
                    Log.d(TAG, "【兼容模式】 尝试作为Base64编码内容解析")
                    val decodedBytes = android.util.Base64.decode(qrContent, android.util.Base64.DEFAULT)
                    val decodedString = String(decodedBytes)
                    
                    if (decodedString.trim().startsWith("{") && decodedString.trim().endsWith("}")) {
                        Log.i(TAG, "【解码成功】 成功从Base64恢复JSON内容")
                        processJsonQrCode(decodedString)
                        return
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "【解码失败】 内容不是有效的Base64编码：${e.message}")
                }
            }
            
            // 尝试不同的字符编码处理
            val encodings = listOf("UTF-8", "GB2312", "GBK", "GB18030")
            for (encoding in encodings) {
                try {
                    // 尝试将内容按指定编码转换
                    val bytes = qrContent.toByteArray(charset("ISO-8859-1"))
                    val encodedString = String(bytes, charset(encoding))
                    
                    // 检查转换后是否为有效JSON
                    if (encodedString.trim().startsWith("{") && encodedString.trim().endsWith("}")) {
                        Log.i(TAG, "【编码转换成功】 使用 $encoding 编码识别出JSON内容")
                        processJsonQrCode(encodedString)
                        return
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "【编码转换失败】 $encoding：${e.message}")
                }
            }
            
            // 尝试作为整个数据包的特定部分解析
            // 例如，可能是数据包序列号或其他元数据
            if (qrContent.length < 10 && qrContent.matches(Regex("^[0-9]+$"))) {
                try {
                    val index = qrContent.toInt()
                    Log.d(TAG, "【兼容模式】 可能是数据包索引：$index")
                    
                    // 这里可以添加对特定索引或序列号的处理逻辑
                    // 例如，可以触发特定的状态更新或向用户显示反馈
                } catch (e: Exception) {
                    Log.d(TAG, "【解析失败】 无法解析为数字索引：${e.message}")
                }
            }
            
            // 记录这个未知格式的QR码，但不触发错误状态
            Log.i(TAG, "【未知格式】 QR码内容无法识别为应用支持的格式")
            Log.d(TAG, "【原始内容】 $qrContent")
            Log.d(TAG, "【HEX格式】 ${qrContent.toByteArray().joinToString("") { "%02X".format(it) }}")
            
        } catch (e: Exception) {
            Log.e(TAG, "【非JSON处理异常】 ${e.message}", e)
        }
    }

    /**
     * 开始接收文件
     */
    private fun startReceiving(initialPacket: DataPacket) {
        try {
            // 初始化解码器
            codec.initializeDecoder(
                initialPacket.header.fileName,
                initialPacket.header.fileSize,
                initialPacket.header.totalBlocks
            )
            
            // 更新状态
            _receiveState.value = ReceiveState.Receiving(
                fileName = initialPacket.header.fileName,
                fileSize = initialPacket.header.fileSize,
                totalBlocks = initialPacket.header.totalBlocks
            )
            
            // 重置统计信息
            startTime.set(System.currentTimeMillis())
            packetsReceived.set(0)
            lastUpdateTime.set(System.currentTimeMillis())
            lastPacketsCount.set(0)
            
            Log.d(TAG, "开始接收文件: ${initialPacket.header.fileName}, 大小: ${FileUtil.formatFileSize(initialPacket.header.fileSize)}")
        } catch (e: Exception) {
            Log.e(TAG, "开始接收文件失败: ${e.message}", e)
            _receiveState.value = ReceiveState.Error("开始接收文件失败: ${e.message}")
        }
    }

    /**
     * 更新传输统计信息
     */
    private fun updateStats(packet: DataPacket) {
        // 增加包计数
        val packets = packetsReceived.incrementAndGet()
        
        // 获取解码器统计信息
        val codecStats = codec.getDecodingStats()
        
        // 更新接收统计信息
        _receiveStats.value = ReceiveStats(
            fileName = codecStats.fileName,
            fileSize = codecStats.fileSize,
            solvedBlocks = codecStats.solvedBlocks,
            receivedPackets = packets,
            totalBlocks = codecStats.totalBlocks,
            progress = codecStats.progress,
            elapsedTimeMs = System.currentTimeMillis() - startTime.get()
        )
        
        // 每秒更新一次传输速度
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime.get() >= 1000) {
            val timeSpan = (currentTime - lastUpdateTime.get()) / 1000f // 秒
            val packetCount = packets - lastPacketsCount.get()
            
            // 包速度
            _transferSpeed.value = packetCount / timeSpan
            
            // 字节速度 (估算)
            val avgPacketSize = packet.payload.length * 0.75f // Base64解码后约为3/4
            _transferByteSpeed.value = (packetCount * avgPacketSize).toLong()
            
            // 重置计数器
            lastUpdateTime.set(currentTime)
            lastPacketsCount.set(packets)
        }
    }

    /**
     * 保存接收到的文件
     */
    fun saveReceivedFile(context: Context): File? {
        return try {
            if (_receiveState.value !is ReceiveState.Completed) {
                Log.e(TAG, "文件尚未接收完成，无法保存")
                return null
            }
            
            // 获取下载目录
            val downloadDir = FileUtil.getPublicDownloadDir()
            
            // 保存文件
            val savedFile = codec.saveDecodedFile(downloadDir)
            Log.d(TAG, "文件保存成功: ${savedFile.absolutePath}")
            
            savedFile
        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败: ${e.message}", e)
            _receiveState.value = ReceiveState.Error("保存文件失败: ${e.message}")
            null
        }
    }

    /**
     * 重置接收状态
     */
    fun reset() {
        _receiveState.value = ReceiveState.Idle
        _receiveStats.value = null
        _transferSpeed.value = 0f
        _transferByteSpeed.value = 0L
    }

    /**
     * 接收状态
     */
    sealed class ReceiveState {
        object Idle : ReceiveState()
        
        data class Receiving(
            val fileName: String,
            val fileSize: Long,
            val totalBlocks: Int
        ) : ReceiveState()
        
        data class Completed(val fileName: String) : ReceiveState()
        
        data class Error(val message: String) : ReceiveState()
    }

    /**
     * 接收统计信息
     */
    data class ReceiveStats(
        val fileName: String,
        val fileSize: Long,
        val solvedBlocks: Int, // 已解码的源块数
        val receivedPackets: Int = 0, // 接收到的数据包总数
        val totalBlocks: Int,
        val progress: Float, // 0.0 - 1.0
        val elapsedTimeMs: Long
    )
}