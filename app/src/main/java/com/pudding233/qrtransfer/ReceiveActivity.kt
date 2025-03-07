package com.pudding233.qrtransfer

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pudding233.qrtransfer.databinding.ActivityReceiveBinding
import com.pudding233.qrtransfer.decoder.FountainDecoder
import com.pudding233.qrtransfer.model.TransferPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ReceiveActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReceiveBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private var decoder: FountainDecoder? = null
    private val gson = Gson()
    private var receivedPackets = 0L
    private var startTime = 0L
    private var currentFileName = ""
    private var isProcessingFrame = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var totalBytesReceived = 0L
    private val blockSize = 1024
    
    // 用于并行处理的协程作用域和通道
    private val decodeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val frameChannel = Channel<ImageProxy>(Channel.BUFFERED)
    private val resultChannel = Channel<String>(Channel.BUFFERED)
    private val lastProcessedValue = MutableStateFlow<String?>(null)
    
    // 缓存最近处理过的二维码内容，避免重复处理
    private val processedCache = LinkedHashSet<String>()
    private val maxCacheSize = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCamera()
        setupProcessing()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAllPotentialBarcodes()
                .build()
        )
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupProcessing() {
        // 启动图像处理协程
        decodeScope.launch {
            for (frame in frameChannel) {
                try {
                    processFrame(frame)
                } finally {
                    frame.close()
                }
            }
        }

        // 启动结果处理协程
        decodeScope.launch {
            for (result in resultChannel) {
                processQRContent(result)
            }
        }

        // 监听最新处理的值
        lifecycleScope.launch {
            lastProcessedValue.collect { value ->
                value?.let { processQRContent(it) }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(1920, 1080))
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setImageQueueDepth(1) // 减少图像队列深度
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer())
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA.let { selector ->
                        CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()
                    },
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "相机绑定失败", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "停止相机预览失败", e)
        }
    }

    private inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            if (!isProcessingFrame) {
                isProcessingFrame = true
                decodeScope.launch {
                    frameChannel.send(image)
                }
            } else {
                image.close()
            }
        }
    }

    private suspend fun processFrame(image: ImageProxy) {
        try {
            val mediaImage = image.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                
                withContext(Dispatchers.Default) {
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.let { barcode ->
                                val rawValue = barcode.rawValue ?: return@let
                                if (!processedCache.contains(rawValue)) {
                                    processedCache.add(rawValue)
                                    if (processedCache.size > maxCacheSize) {
                                        processedCache.iterator().next()?.let {
                                            processedCache.remove(it)
                                        }
                                    }
                                    decodeScope.launch {
                                        resultChannel.send(rawValue)
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "扫描QR码失败: ${e.message}")
                        }
                }
            }
        } finally {
            isProcessingFrame = false
        }
    }

    private suspend fun processQRContent(rawValue: String) {
        withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "二维码数据大小: ${rawValue.length} 字节")
                val packet = gson.fromJson(rawValue, TransferPacket::class.java)
                processPacket(packet)
            } catch (e: Exception) {
                Log.e(TAG, "解析数据包失败: ${e.message}")
            }
        }
    }

    private fun processPacket(packet: TransferPacket) {
        if (decoder == null) {
            // 新文件传输开始
            startTime = System.currentTimeMillis()
            receivedPackets = 0
            totalBytesReceived = 0
            currentFileName = packet.header.fileName
            decoder = FountainDecoder(
                fileSize = packet.header.fileSize,
                totalBlocks = packet.header.totalBlocks
            )
            updateUI(packet.header.fileName, 0f)
        }

        decoder?.let { decoder ->
            receivedPackets++
            totalBytesReceived += blockSize // 累计接收的字节数
            if (decoder.addPacket(packet)) {
                val progress = decoder.getProgress()
                updateUI(currentFileName, progress)

                if (decoder.isComplete()) {
                    saveFile(decoder)
                }
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> "%.2f MB/s".format(bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> "%.2f KB/s".format(bytesPerSecond / 1024)
            else -> "%.0f B/s".format(bytesPerSecond)
        }
    }

    private fun updateUI(fileName: String, progress: Float) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.fileNameText.text = fileName
            
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            val bytesPerSecond = if (elapsedSeconds > 0) totalBytesReceived / elapsedSeconds else 0.0
            binding.speedText.text = "传输速度: ${formatSpeed(bytesPerSecond)}"
            
            binding.blockCountText.text = "已接收数据包: $receivedPackets"
            binding.progressText.text = "解码进度: ${(progress * 100).roundToInt()}%"
        }
    }

    private fun saveFile(decoder: FountainDecoder) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = decoder.getDecodedData() ?: return@launch
                
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/qrtransfer"
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, currentFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                }

                val uri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@launch

                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(data)
                }

                withContext(Dispatchers.Main) {
                    stopCamera() // 停止相机预览
                    
                    // 显示带有存储路径的完成对话框
                    AlertDialog.Builder(this@ReceiveActivity)
                        .setTitle("传输完成")
                        .setMessage("文件已保存到：\n${relativePath}/${currentFileName}")
                        .setPositiveButton("确定") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                    
                    // 在日志中也记录保存位置
                    Log.i(TAG, "文件已保存到: ${relativePath}/${currentFileName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存文件失败", e)
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ReceiveActivity)
                        .setTitle("保存失败")
                        .setMessage("保存文件时发生错误：${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } finally {
                this@ReceiveActivity.decoder = null
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
        barcodeScanner.close()
        decodeScope.cancel()
        frameChannel.close()
        resultChannel.close()
    }

    companion object {
        private const val TAG = "ReceiveActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
} 