package com.qeqenn.qals

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qeqenn.qals.ui.theme.QALSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qals_prefs", Context.MODE_PRIVATE)

    var currentScreen by remember { mutableStateOf("main") }

    var isGunyahEnabled by remember {
        mutableStateOf(prefs.getBoolean("gunyah_support", false))
    }
    var isGzvmEnabled by remember {
        mutableStateOf(prefs.getBoolean("gzvm_support", false))
    }

    // 状态变量：root、gunyah可用性、gzvm可用性
    var rootStatus by remember { mutableStateOf("检测中...") }
    var gunyahStatus by remember { mutableStateOf("检测中...") }
    var gzvmStatus by remember { mutableStateOf("检测中...") }

    // 启动时检测所有状态
    LaunchedEffect(Unit) {
        val (root, gunyah, gzvm) = checkAllStatus()
        rootStatus = root
        gunyahStatus = gunyah
        gzvmStatus = gzvm
    }

    QALSTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                "main" -> "QALS"
                                "settings" -> "设置"
                                "add_vm" -> "添加虚拟机"
                                else -> "QALS"
                            },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        when (currentScreen) {
                            "main" -> {
                                TextButton(onClick = { currentScreen = "settings" }) {
                                    Text(
                                        text = "设置",
                                        color = Color.Blue,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            "settings" -> {
                                TextButton(
                                    onClick = {
                                        prefs.edit().apply {
                                            putBoolean("gunyah_support", isGunyahEnabled)
                                            putBoolean("gzvm_support", isGzvmEnabled)
                                        }.apply()
                                        currentScreen = "main"
                                    }
                                ) {
                                    Text(
                                        text = "保存",
                                        color = Color.Green,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            "add_vm" -> {
                                TextButton(
                                    onClick = { currentScreen = "main" }
                                ) {
                                    Text(
                                        text = "返回",
                                        color = Color.Blue,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (currentScreen) {
                "main" -> {
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        TextButton(
                            onClick = { currentScreen = "add_vm" },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "添加虚拟机",
                                color = Color.Blue,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                "settings" -> {
                    SettingsScreen(
                        isGunyahEnabled = isGunyahEnabled,
                        onGunyahEnabledChange = { newValue ->
                            if (newValue) isGzvmEnabled = false
                            isGunyahEnabled = newValue
                        },
                        isGzvmEnabled = isGzvmEnabled,
                        onGzvmEnabledChange = { newValue ->
                            if (newValue) isGunyahEnabled = false
                            isGzvmEnabled = newValue
                        },
                        rootStatus = rootStatus,
                        gunyahStatus = gunyahStatus,
                        gzvmStatus = gzvmStatus,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                "add_vm" -> {
                    AddVmScreen(
                        onBackClick = { currentScreen = "main" },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * 检测所有状态：root、gunyah设备、gzvm设备
 * 返回 Triple(root状态, gunyah状态, gzvm状态)
 */
private suspend fun checkAllStatus(): Triple<String, String, String> = withContext(Dispatchers.IO) {
    // 1. 检测 root
    val root = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "exit"))
        val exitCode = process.waitFor()
        if (exitCode == 0) "已获取" else "未获取"
    } catch (e: Exception) {
        Log.e("StatusCheck", "检测 root 失败: ${e.message}")
        "未获取"
    }

    // 2. 检测 Gunyah 设备（仅当 root 已获取时执行）
    val gunyah = if (root == "已获取") {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /dev/gunyah"))
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (exitCode == 0 && output == "/dev/gunyah") "可以使用" else "不可使用"
        } catch (e: Exception) {
            Log.e("StatusCheck", "检测 Gunyah 失败: ${e.message}")
            "不可使用"
        }
    } else {
        "不可使用"
    }

    // 3. 检测 GZVM 设备
    val gzvm = if (root == "已获取") {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /dev/gzvm"))
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (exitCode == 0 && output == "/dev/gzvm") "可以使用" else "不可使用"
        } catch (e: Exception) {
            Log.e("StatusCheck", "检测 GZVM 失败: ${e.message}")
            "不可使用"
        }
    } else {
        "不可使用"
    }

    Triple(root, gunyah, gzvm)
}