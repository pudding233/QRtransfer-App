package com.pudding233.qrtransfer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pudding233.qrtransfer.util.FileUtil
import com.pudding233.qrtransfer.viewmodel.SendViewModel

/**
 * 发送文件屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    viewModel: SendViewModel,
    navigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sendState by viewModel.sendState.collectAsState()
    val currentQrCode by viewModel.currentQrCode.collectAsState()
    val sendStats by viewModel.sendStats.collectAsState()
    
    // 文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.prepareFile(context, it)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发送文件") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 文件信息卡片
                FileInfoCard(
                    fileName = when (sendState) {
                        is SendViewModel.SendState.Prepared -> (sendState as SendViewModel.SendState.Prepared).fileName
                        is SendViewModel.SendState.Sending -> (sendState as SendViewModel.SendState.Sending).fileName
                        else -> null
                    },
                    fileSize = when (sendState) {
                        is SendViewModel.SendState.Prepared -> (sendState as SendViewModel.SendState.Prepared).fileSize
                        is SendViewModel.SendState.Sending -> (sendState as SendViewModel.SendState.Sending).fileSize
                        else -> null
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // QR码区域
                QrCodeDisplay(
                    qrBitmap = currentQrCode?.asImageBitmap(),
                    isActive = sendState is SendViewModel.SendState.Sending
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 控制按钮
                when (sendState) {
                    is SendViewModel.SendState.Idle -> {
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileOpen,
                                contentDescription = "选择文件"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("选择文件")
                        }
                    }
                    is SendViewModel.SendState.Prepared -> {
                        Button(
                            onClick = { viewModel.startSending() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "开始发送"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始发送")
                        }
                    }
                    is SendViewModel.SendState.Sending -> {
                        Button(
                            onClick = { viewModel.stopSending() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "停止发送"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("停止发送")
                        }
                        
                        // 发送统计信息
                        sendStats?.let { stats ->
                            TransferStatsDisplay(
                                sentPackets = stats.sentPackets,
                                packetsPerSecond = stats.packetsPerSecond,
                                elapsedTimeMs = stats.elapsedTimeMs
                            )
                        }
                    }
                    is SendViewModel.SendState.Error -> {
                        Text(
                            text = (sendState as SendViewModel.SendState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileOpen,
                                contentDescription = "重新选择文件"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重新选择文件")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 文件信息卡片
 */
@Composable
fun FileInfoCard(
    fileName: String?,
    fileSize: Long?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (fileName != null && fileSize != null) {
                Text(
                    text = fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "文件大小: ${FileUtil.formatFileSize(fileSize)}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "未选择文件",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * QR码显示区域
 */
@Composable
fun QrCodeDisplay(
    qrBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isActive: Boolean
) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = 2.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap,
                contentDescription = "QR码",
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Text(
                text = "QR码将显示在这里",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 传输统计信息显示
 */
@Composable
fun TransferStatsDisplay(
    sentPackets: Int,
    packetsPerSecond: Float,
    elapsedTimeMs: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "已发送 $sentPackets 个数据包",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "每秒 %.1f 个数据包".format(packetsPerSecond),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "已传输 ${elapsedTimeMs / 1000} 秒",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
    }
} 