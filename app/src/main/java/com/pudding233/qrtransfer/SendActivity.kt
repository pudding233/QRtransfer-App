package com.pudding233.qrtransfer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.pudding233.qrtransfer.databinding.ActivitySendBinding
import com.pudding233.qrtransfer.model.TransferPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class SendActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySendBinding
    private var selectedFileUri: Uri? = null
    private var transmissionJob: Job? = null
    private val gson = Gson()
    private val blockSize = 256 // 减小块大小以减少数据量
    private val qrCodeWriter = QRCodeWriter()
    private val qrCodeSize = 1500
    private val qrCodeChannel = Channel<Bitmap>(Channel.BUFFERED) // 二维码缓存通道
    private val qrCodeGenerationScope = CoroutineScope(Dispatchers.Default + Job())
    private var isGeneratingQRCodes = false

    private val selectFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        binding.selectFileButton.setOnClickListener {
            selectFile.launch("*/*")
        }
    }

    private fun handleFileSelection(uri: Uri) {
        selectedFileUri = uri
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val fileName = cursor.getString(nameIndex)
            val fileSize = cursor.getLong(sizeIndex)
            
            binding.selectedFileNameText.text = fileName
            binding.selectedFileSizeText.text = "文件大小: ${formatFileSize(fileSize)}"
            
            startTransmission(uri, fileName, fileSize)
        }
    }

    private fun startTransmission(uri: Uri, fileName: String, fileSize: Long) {
        transmissionJob?.cancel()
        isGeneratingQRCodes = true
        
        // 启动二维码生成协程
        qrCodeGenerationScope.launch {
            try {
                val totalBlocks = (fileSize + blockSize - 1) / blockSize
                val fileData = readFileData(uri)
                val blocks = splitIntoBlocks(fileData)
                
                // 启动显示协程
                transmissionJob = lifecycleScope.launch {
                    var transmittedPackets = 0L
                    val startTime = System.currentTimeMillis()

                    while (isActive) {
                        val qrCode = qrCodeChannel.receive()
                        transmittedPackets++
                        
                        withContext(Dispatchers.Main) {
                            binding.qrCodeImage.setImageBitmap(qrCode)
                            updateTransmissionInfo(transmittedPackets, startTime, blocks.size)
                        }
                    }
                }

                // 持续生成二维码
                while (isGeneratingQRCodes) {
                    val packet = generateFountainPacket(fileName, fileSize, blocks, totalBlocks.toInt())
                    val jsonData = gson.toJson(packet)
                    
                    // 检查数据大小
                    if (jsonData.length > 2000) { // 设置合理的数据大小限制
                        Log.w("SendActivity", "数据包过大: ${jsonData.length} bytes")
                        continue // 跳过过大的数据包
                    }
                    
                    val qrCode = generateQRCode(jsonData)
                    qrCodeChannel.send(qrCode)
                }
            } catch (e: Exception) {
                Log.e("SendActivity", "传输错误", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SendActivity, "传输出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readFileData(uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { input ->
            ByteArrayOutputStream().use { output ->
                input.copyTo(output)
                output.toByteArray()
            }
        } ?: throw IllegalStateException("无法读取文件")
    }

    private fun splitIntoBlocks(data: ByteArray): List<ByteArray> {
        val blocks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val remainingBytes = data.size - offset
            val currentBlockSize = min(blockSize, remainingBytes)
            blocks.add(data.copyOfRange(offset, offset + currentBlockSize))
            offset += currentBlockSize
        }
        return blocks
    }

    private fun generateFountainPacket(
        fileName: String,
        fileSize: Long,
        blocks: List<ByteArray>,
        totalBlocks: Int
    ): TransferPacket {
        val seed = Random.nextLong()
        val random = Random(seed)
        
        // 调整编码策略，减小数据量
        val degree = when {
            blocks.size == 1 -> 1
            blocks.size <= 4 -> random.nextInt(1, blocks.size + 1)
            else -> {
                // 限制最大度数以控制数据大小
                val maxAllowedDegree = min(blocks.size, 8)
                generateRobustSolitonDegree(random, blocks.size, maxAllowedDegree)
            }
        }

        val selectedBlocks = selectRandomBlocks(random, blocks.size, degree)
        val combinedData = xorBlocks(blocks, selectedBlocks)
        val encodedData = Base64.getEncoder().encodeToString(combinedData)
        
        return TransferPacket(
            header = TransferPacket.Header(
                magic = "FLQR",
                fileSize = fileSize,
                fileName = fileName,
                blockIndex = seed.toInt(),
                totalBlocks = totalBlocks,
                checksum = calculateChecksum(combinedData)
            ),
            encoding = TransferPacket.Encoding(
                degree = degree,
                sourceBlocks = selectedBlocks,
                checksum = calculateChecksum(selectedBlocks.toString().toByteArray())
            ),
            payload = encodedData
        )
    }

    private fun generateRobustSolitonDegree(random: Random, k: Int, maxDegree: Int): Int {
        // 确保参数有效
        if (k <= 1 || maxDegree <= 1) return 1
        
        val c = 0.05
        val delta = 0.05
        
        // 计算R值并确保至少为2
        val R = max(2, min((c * ln(k.toDouble() / delta) * kotlin.math.sqrt(k.toDouble())).toInt(), maxDegree))
        
        return when (val p = random.nextDouble()) {
            in 0.0..0.4 -> 1  // 40%概率选择度数1
            in 0.4..0.8 -> {  // 40%概率选择2-4之间的小度数
                if (maxDegree <= 2) 2
                else random.nextInt(2, min(5, maxDegree + 1))
            }
            else -> {  // 20%概率选择更大的度数
                if (R <= 2) 2
                else random.nextInt(2, R + 1)
            }
        }
    }

    private fun selectRandomBlocks(random: Random, totalBlocks: Int, degree: Int): List<Int> {
        // 确保不会选择超过总块数的块
        val actualDegree = min(degree, totalBlocks)
        val indices = (0 until totalBlocks).toMutableList()
        indices.shuffle(random)
        return indices.take(actualDegree).sorted()
    }

    private fun xorBlocks(blocks: List<ByteArray>, selectedIndices: List<Int>): ByteArray {
        if (selectedIndices.isEmpty()) return ByteArray(0)
        
        // 找出选中块中的最大长度
        val maxLength = selectedIndices.maxOf { blocks[it].size }
        val result = ByteArray(maxLength)
        
        // 复制第一个块
        blocks[selectedIndices[0]].copyInto(result)
        
        // XOR其余的块
        for (i in 1 until selectedIndices.size) {
            val block = blocks[selectedIndices[i]]
            for (j in block.indices) {
                result[j] = (result[j].toInt() xor block[j].toInt()).toByte()
            }
        }
        
        return result
    }

    private fun generateQRCode(content: String): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L // 使用L级别减小数据量
        hints[EncodeHintType.MARGIN] = 0

        val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        
        // 优化像素设置过程
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun calculateChecksum(data: ByteArray): Int {
        return data.fold(0) { acc, byte -> 
            (acc * 31 + byte.toInt()) and 0x7FFFFFFF  // 使用更好的散列函数
        }
    }

    private fun updateTransmissionInfo(transmittedPackets: Long, startTime: Long, totalBlocks: Int) {
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (elapsedSeconds > 0) transmittedPackets / elapsedSeconds else 0.0
        
        binding.transmissionSpeedText.text = "传输速度: %.1f 包/秒".format(speed)
        binding.blockCountText.text = "数据块总数: $totalBlocks"
    }

    private fun formatFileSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }

    override fun onDestroy() {
        isGeneratingQRCodes = false
        transmissionJob?.cancel()
        qrCodeGenerationScope.cancel()
        qrCodeChannel.close()
        super.onDestroy()
    }
} 