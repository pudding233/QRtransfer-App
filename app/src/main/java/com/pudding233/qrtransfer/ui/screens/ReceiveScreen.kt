package com.pudding233.qrtransfer.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.common.util.concurrent.ListenableFuture
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.pudding233.qrtransfer.util.FileUtil
import com.pudding233.qrtransfer.util.QRCodeUtil
import com.pudding233.qrtransfer.viewmodel.ReceiveViewModel
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

/**
 * 接收文件屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel,
    navigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val receiveState by viewModel.receiveState.collectAsState()
    val receiveStats by viewModel.receiveStats.collectAsState()
    val transferSpeed by viewModel.transferSpeed.collectAsState()
    val transferByteSpeed by viewModel.transferByteSpeed.collectAsState()
    
    // 相机权限
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )
    
    // 保存的文件
    var savedFile by remember { mutableStateOf<File?>(null) }
    
    // 显示完成对话框
    var showCompletedDialog by remember { mutableStateOf(false) }
    
    // 处理完成状态
    LaunchedEffect(receiveState) {
        if (receiveState is ReceiveViewModel.ReceiveState.Completed) {
            val file = viewModel.saveReceivedFile(context)
            savedFile = file
            showCompletedDialog = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("接收文件") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (cameraPermissionState.status.isGranted) {
                // 主内容
                Box(modifier = Modifier.fillMaxSize()) {
                    // 相机预览 - 传递receiveState，以便在文件接收完成时停止相机
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        receiveState = receiveState,
                        onQrCodeScanned = { qrContent ->
                            viewModel.processQRCode(qrContent)
                        }
                    )
                    
                    // 接收信息气泡
                    if (receiveState is ReceiveViewModel.ReceiveState.Receiving || 
                        receiveState is ReceiveViewModel.ReceiveState.Completed) {
                        ReceiveStatusBubble(
                            receiveStats = receiveStats,
                            transferSpeed = transferSpeed,
                            transferByteSpeed = transferByteSpeed,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                        )
                    }
                }
            } else {
                // 请求相机权限
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "需要相机权限来扫描QR码",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("授予相机权限")
                    }
                }
            }
        }
    }
    
    // 完成对话框
    if (showCompletedDialog && savedFile != null) {
        FileReceivedDialog(
            fileName = savedFile?.name ?: "",
            fileSize = savedFile?.length() ?: 0,
            onDismiss = { showCompletedDialog = false },
            onOpenFile = {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        savedFile!!
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("ReceiveScreen", "无法打开文件: ${e.message}")
                }
            }
        )
    }
}

/**
 * 创建自定义线程池用于QR码处理
 */
private fun createQrDecodingExecutor(): ExecutorService {
    // 使用有界队列的线程池，避免任务堆积
    return ThreadPoolExecutor(
        1, // 核心线程数
        1, // 最大线程数 - 减少为1，避免多线程竞争导致相机重启
        60L, // 空闲线程存活时间
        TimeUnit.SECONDS, // 时间单位
        LinkedBlockingQueue(3), // 有界队列，减少队列大小避免积压
        Executors.defaultThreadFactory()
    )
}

/**
 * 相机管理器单例类 - 避免频繁创建和销毁相机实例
 */
private object CameraManager {
    private var cameraProvider: ProcessCameraProvider? = null
    private var isInitialized = false
    
    fun getCameraProvider(context: android.content.Context): ListenableFuture<ProcessCameraProvider> {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            isInitialized = true
        }, ContextCompat.getMainExecutor(context))
        return cameraProviderFuture
    }
    
    fun releaseCamera() {
        cameraProvider?.unbindAll()
    }
}

/**
 * 相机预览组件 - 优化版本
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    receiveState: ReceiveViewModel.ReceiveState,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    // 创建预览视图 - 但保持其作为状态，使其不会在重组时重新创建
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    
    // 创建线程池 - 保持单例
    val qrDecodingExecutor = remember { createQrDecodingExecutor() }
    
    // 处理控制标志
    val isProcessing = remember { AtomicBoolean(false) }
    val lastScanResult = remember { mutableStateOf<String?>(null) }
    val isCameraInitialized = remember { mutableStateOf(false) }
    
    // 当接收完成时停止处理
    val shouldStopProcessing = receiveState is ReceiveViewModel.ReceiveState.Completed
    
    // 创建和释放相机资源
    DisposableEffect(lifecycleOwner) {
        // 创建相机实例
        setupCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            qrDecodingExecutor = qrDecodingExecutor,
            isProcessing = isProcessing,
            lastScanResult = lastScanResult,
            onQrCodeScanned = onQrCodeScanned,
            isCameraInitialized = isCameraInitialized
        )
        
        // 清理
        onDispose {
            // 关闭线程池
            coroutineScope.launch(Dispatchers.IO) {
                qrDecodingExecutor.shutdown()
                try {
                    if (!qrDecodingExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                        qrDecodingExecutor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    qrDecodingExecutor.shutdownNow()
                }
            }
            
            // 在主线程释放相机
            coroutineScope.launch(Dispatchers.Main) {
                CameraManager.releaseCamera()
            }
        }
    }
    
    // 当任务完成时，停止相机处理
    LaunchedEffect(shouldStopProcessing) {
        if (shouldStopProcessing && isCameraInitialized.value) {
            CameraManager.releaseCamera()
        }
    }
    
    // 渲染相机预览
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

/**
 * 设置相机和图像分析 - 从Composable中分离出来，避免重组导致相机重启
 */
private fun setupCamera(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    qrDecodingExecutor: ExecutorService,
    isProcessing: AtomicBoolean,
    lastScanResult: androidx.compose.runtime.MutableState<String?>,
    onQrCodeScanned: (String) -> Unit,
    isCameraInitialized: androidx.compose.runtime.MutableState<Boolean>
) {
    try {
        val cameraProviderFuture = CameraManager.getCameraProvider(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // 预览用例 - 高性能模式
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // 图像分析用例 - 使用低分辨率和低帧率模式，减少处理负担
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                    .also {
                        it.setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
                            // 如果正在处理或队列中有任务，跳过当前帧
                            if (isProcessing.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            
                            // 标记为正在处理
                            isProcessing.set(true)
                            
                            // 在线程池中处理QR码识别
                            qrDecodingExecutor.execute {
                                try {
                                    val qrContent = decodeQrCodeFromImage(imageProxy)
                                    if (!qrContent.isNullOrEmpty()) {
                                        // 不再对内容进行JSON格式验证或长度验证
                                        // 只检查是否与前一次扫描结果相同
                                        val isRepeat = qrContent == lastScanResult.value
                                        
                                        if (!isRepeat) {
                                            // 更新上次扫描结果并处理
                                            lastScanResult.value = qrContent
                                            onQrCodeScanned(qrContent)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraPreview", "【QR码识别失败】 ${e.message}")
                                } finally {
                                    imageProxy.close()
                                    isProcessing.set(false)
                                }
                            }
                        }
                    }
                
                try {
                    // 解绑所有用例
                    cameraProvider.unbindAll()
                    
                    // 绑定用例到相机
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    
                    // 设置相机初始化标志
                    isCameraInitialized.value = true
                    
                    // 设置自动对焦模式
                    camera.cameraControl.enableTorch(false)
                    
                } catch (e: Exception) {
                    Log.e("CameraPreview", "绑定相机失败: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "相机初始化失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    } catch (e: Exception) {
        Log.e("CameraPreview", "创建相机失败: ${e.message}")
    }
}

/**
 * 在后台线程中解码QR码 - 增强兼容性版本
 */
private fun decodeQrCodeFromImage(imageProxy: ImageProxy): String? {
    try {
        // 记录图像分析基本信息
        val frameWidth = imageProxy.width
        val frameHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        val timestamp = imageProxy.imageInfo.timestamp
        
        // 确保有可用的图像平面
        if (imageProxy.planes.isEmpty() || imageProxy.planes[0] == null) {
            return null
        }
        
        val buffer = imageProxy.planes[0].buffer
        
        // 验证缓冲区有足够的数据
        if (buffer.remaining() <= 0) {
            return null
        }
        
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        // 检查尺寸有效性
        if (frameWidth <= 0 || frameHeight <= 0) {
            return null
        }
        
        // 尝试不同的图像处理方式，创建多个源以增加识别成功率
        // 1. 标准处理
        val source = PlanarYUVLuminanceSource(
            data,
            frameWidth,
            frameHeight,
            0,
            0,
            frameWidth,
            frameHeight,
            false
        )

        // 创建两组解码提示 - 一组仅用于QR码，一组用于所有格式
        
        // 1. 仅QR码的提示 - 优先使用这个避免误判
        val qrOnlyHints = mapOf(
            com.google.zxing.DecodeHintType.TRY_HARDER to true,
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                com.google.zxing.BarcodeFormat.QR_CODE
            ),
            com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8,GB2312,GBK,GB18030,Shift_JIS,ISO-8859-1",
            com.google.zxing.DecodeHintType.PURE_BARCODE to false
        )
        
        // 2. 所有格式的提示 - 仅在专用QR码提示失败时使用
        val allFormatsHints = mapOf(
            com.google.zxing.DecodeHintType.TRY_HARDER to true,
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                com.google.zxing.BarcodeFormat.QR_CODE,
                com.google.zxing.BarcodeFormat.DATA_MATRIX,
                com.google.zxing.BarcodeFormat.AZTEC,
                com.google.zxing.BarcodeFormat.PDF_417
            ),
            com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8,GB2312,GBK,GB18030,Shift_JIS,ISO-8859-1",
            com.google.zxing.DecodeHintType.PURE_BARCODE to false
        )
        
        // 使用ZXing库解析QR码
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val decodeStart = System.currentTimeMillis()
        
        // 第一阶段：仅尝试识别QR码格式
        reader.setHints(qrOnlyHints)
        
        try {
            // 首先尝试仅识别QR码格式
            val result = reader.decode(bitmap)
            val decodeTime = System.currentTimeMillis() - decodeStart
            
            if (result != null) {
                val format = result.barcodeFormat
                val content = result.text ?: ""
                val contentLength = content.length
                val resultMetadata = result.resultMetadata?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "无"
                val resultPoints = result.resultPoints?.joinToString(", ") { "(${it.x}, ${it.y})" } ?: "无"
                
                // 对于UPC_E/EAN_8等1D条码格式，如果内容长度为8个数字，可能是误判
                if ((format == com.google.zxing.BarcodeFormat.UPC_E || 
                     format == com.google.zxing.BarcodeFormat.EAN_8) && 
                    contentLength == 8 && content.all { it.isDigit() }) {
                    
                    Log.w("CameraPreview", "【潜在误判】 检测到格式为 $format 但内容为8位数字，可能是误判，尝试其他识别策略")
                    // 不要立即返回，继续尝试其他识别策略
                } else {
                    // 识别到任何二维码时输出详细的中文日志，不管内容如何
                    Log.i("CameraPreview", "【二维码识别成功】 格式：$format，内容长度：$contentLength 字符，解码耗时：${decodeTime}毫秒")
                    Log.i("CameraPreview", "【图像信息】 尺寸：${frameWidth}x${frameHeight}，旋转角度：${rotation}°，时间戳：$timestamp")
                    Log.d("CameraPreview", "【元数据】 $resultMetadata")
                    Log.d("CameraPreview", "【坐标点】 $resultPoints")
                    
                    // 记录完整内容，不进行截断
                    Log.i("CameraPreview", "【二维码内容】 $content")
                    
                    // 输出内容的十六进制表示，帮助发现不可见字符
                    try {
                        val hexContent = content.toByteArray().joinToString("") { "%02X".format(it) }
                        Log.d("CameraPreview", "【内容十六进制】 $hexContent")
                    } catch (e: Exception) {
                        Log.w("CameraPreview", "【十六进制转换失败】 ${e.message}")
                    }
                    
                    // 不再对内容进行JSON验证或长度验证，直接返回解码结果
                    return content
                }
            }
        } catch (e: Exception) {
            // 当QR码专用模式失败时，继续尝试其他策略
            Log.d("CameraPreview", "【QR专用模式失败】 ${e.message ?: "未知错误"}，尝试其他策略")
        }
        
        // 第二阶段：尝试所有支持的格式
        try {
            reader.setHints(allFormatsHints)
            val allFormatsResult = reader.decode(bitmap)
            
            if (allFormatsResult != null) {
                val format = allFormatsResult.barcodeFormat
                val content = allFormatsResult.text ?: ""
                val contentLength = content.length
                
                // 检查是否为可能的误判
                if ((format == com.google.zxing.BarcodeFormat.UPC_E || 
                     format == com.google.zxing.BarcodeFormat.EAN_8) && 
                    contentLength == 8 && content.all { it.isDigit() }) {
                    
                    Log.w("CameraPreview", "【忽略误判】 检测到 $format 格式的8位数字，可能是QR码误判，继续尝试其他策略")
                } else {
                    Log.i("CameraPreview", "【多格式模式识别成功】 格式：$format，内容长度：$contentLength 字符")
                    Log.i("CameraPreview", "【二维码内容】 $content")
                    return content
                }
            }
        } catch (e: Exception) {
            // 多格式模式失败，继续尝试其他策略
        }
        
        // 尝试使用GlobalHistogramBinarizer增强识别能力
        try {
            val altBitmap = BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
            reader.setHints(qrOnlyHints)  // 使用仅QR码提示
            val altResult = reader.decode(altBitmap)
            
            if (altResult != null) {
                // 同样检查误判情况
                val format = altResult.barcodeFormat
                val content = altResult.text ?: ""
                val contentLength = content.length
                
                if ((format == com.google.zxing.BarcodeFormat.UPC_E || 
                     format == com.google.zxing.BarcodeFormat.EAN_8) && 
                    contentLength == 8 && content.all { it.isDigit() }) {
                    
                    Log.w("CameraPreview", "【备用模式误判】 检测到 $format 格式的8位数字，可能是QR码误判")
                } else {
                    Log.i("CameraPreview", "【备用方式识别成功】 格式：$format，内容长度：$contentLength 字符")
                    Log.i("CameraPreview", "【二维码内容】 $content")
                    return content
                }
            }
        } catch (e: Exception) {
            // 备用方式也失败
        }
        
        // 尝试中心区域识别
        try {
            // 尝试缩小区域识别 - 有时中心区域更清晰
            val centerSource = PlanarYUVLuminanceSource(
                data,
                frameWidth,
                frameHeight,
                frameWidth / 4,
                frameHeight / 4,
                frameWidth / 2,
                frameHeight / 2,
                false
            )
            
            val centerBitmap = BinaryBitmap(HybridBinarizer(centerSource))
            reader.setHints(qrOnlyHints)  // 使用仅QR码提示
            val centerResult = reader.decode(centerBitmap)
            
            if (centerResult != null) {
                // 同样检查误判情况
                val format = centerResult.barcodeFormat
                val content = centerResult.text ?: ""
                val contentLength = content.length
                
                if ((format == com.google.zxing.BarcodeFormat.UPC_E || 
                     format == com.google.zxing.BarcodeFormat.EAN_8) && 
                    contentLength == 8 && content.all { it.isDigit() }) {
                    
                    Log.w("CameraPreview", "【中心区域误判】 检测到 $format 格式的8位数字，可能是QR码误判")
                } else {
                    Log.i("CameraPreview", "【中心区域识别成功】 格式：$format，内容长度：$contentLength 字符")
                    Log.i("CameraPreview", "【二维码内容】 $content")
                    return content
                }
            }
        } catch (e: Exception) {
            // 中心区域识别也失败
        }
        
        // 尝试不同尺寸的扫描区域（50%，75%）
        for (scaleFactor in listOf(0.5f, 0.75f)) {
            try {
                val offsetX = (frameWidth * (1 - scaleFactor) / 2).toInt()
                val offsetY = (frameHeight * (1 - scaleFactor) / 2).toInt()
                val width = (frameWidth * scaleFactor).toInt()
                val height = (frameHeight * scaleFactor).toInt()
                
                val scaledSource = PlanarYUVLuminanceSource(
                    data,
                    frameWidth,
                    frameHeight,
                    offsetX,
                    offsetY,
                    width,
                    height,
                    false
                )
                
                val scaledBitmap = BinaryBitmap(HybridBinarizer(scaledSource))
                reader.setHints(qrOnlyHints)  // 使用仅QR码提示
                val scaledResult = reader.decode(scaledBitmap)
                
                if (scaledResult != null) {
                    // 检查误判情况
                    val format = scaledResult.barcodeFormat
                    val content = scaledResult.text ?: ""
                    val contentLength = content.length
                    
                    if ((format == com.google.zxing.BarcodeFormat.UPC_E || 
                         format == com.google.zxing.BarcodeFormat.EAN_8) && 
                        contentLength == 8 && content.all { it.isDigit() }) {
                        
                        Log.w("CameraPreview", "【缩放区域误判】 检测到 $format 格式的8位数字，可能是QR码误判")
                    } else {
                        Log.i("CameraPreview", "【${(scaleFactor * 100).toInt()}%区域识别成功】 格式：$format，内容长度：$contentLength 字符")
                        Log.i("CameraPreview", "【二维码内容】 $content")
                        return content
                    }
                }
            } catch (e: Exception) {
                // 此尺寸识别失败，继续尝试
            }
        }
            
        return null
    } catch (e: Exception) {
        // 只在发生非预期异常时记录错误日志
        Log.e("CameraPreview", "【二维码处理异常】 ${e.message}", e)
        return null
    }
}

/**
 * 接收状态气泡
 */
@Composable
fun ReceiveStatusBubble(
    receiveStats: ReceiveViewModel.ReceiveStats?,
    transferSpeed: Float,
    transferByteSpeed: Long,
    modifier: Modifier = Modifier
) {
    if (receiveStats == null) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 文件信息
            Text(
                text = receiveStats.fileName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "文件大小: ${FileUtil.formatFileSize(receiveStats.fileSize)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 进度条
            LinearProgressIndicator(
                progress = { receiveStats.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 统计信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "已接收块",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${receiveStats.solvedBlocks}/${receiveStats.totalBlocks}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "传输速度",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.1f 包/秒".format(transferSpeed),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "数据速率",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FileUtil.formatTransferSpeed(transferByteSpeed),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "已用时间",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${receiveStats.elapsedTimeMs / 1000}秒",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 文件接收完成对话框
 */
@Composable
fun FileReceivedDialog(
    fileName: String,
    fileSize: Long,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "完成",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "文件接收完成！",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "文件已保存到下载目录",
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = fileName,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "大小: ${FileUtil.formatFileSize(fileSize)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenFile) {
                Icon(
                    imageVector = Icons.Filled.FileDownload,
                    contentDescription = "打开文件"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("打开文件")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
} 