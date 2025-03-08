package com.pudding233.qrtransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pudding233.qrtransfer.ui.screens.MainScreen
import com.pudding233.qrtransfer.ui.screens.ReceiveScreen
import com.pudding233.qrtransfer.ui.screens.SendScreen
import com.pudding233.qrtransfer.ui.theme.QRtransferTheme
import com.pudding233.qrtransfer.viewmodel.ReceiveViewModel
import com.pudding233.qrtransfer.viewmodel.SendViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRtransferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}

/**
 * 应用导航主机
 */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        // 主界面
        composable("main") {
            MainScreen(
                onSendClick = { navController.navigate("send") },
                onReceiveClick = { navController.navigate("receive") }
            )
        }
        
        // 发送文件页面
        composable("send") {
            val sendViewModel: SendViewModel = viewModel()
            SendScreen(
                viewModel = sendViewModel,
                navigateBack = { navController.navigateUp() }
            )
        }
        
        // 接收文件页面
        composable("receive") {
            val receiveViewModel: ReceiveViewModel = viewModel()
            ReceiveScreen(
                viewModel = receiveViewModel,
                navigateBack = { navController.navigateUp() }
            )
        }
    }
}