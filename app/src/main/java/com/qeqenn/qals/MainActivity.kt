package com.qeqenn.qals

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ClipData
import android.content.ClipboardManager
import com.qeqenn.qals.tty.*
import com.qeqenn.qals.ui.theme.QALSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ---------- 数据类 ----------
data class VmConfig(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "",
    val os: String = "",
    val cpu: Int = 4,
    val memory: Float = 2f,
    val cdrom: String? = null,
    val disk: String? = null,
    val network: Boolean = false,
    val audio: Boolean = false,
    val bootMode: String = "UEFI",
    val kernel: String? = null,
    val cmdline: String? = null,
    val displayEnabled: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("os", os)
        put("cpu", cpu)
        put("memory", memory)
        put("cdrom", cdrom)
        put("disk", disk)
        put("network", network)
        put("audio", audio)
        put("bootMode", bootMode)
        put("kernel", kernel)
        put("cmdline", cmdline)
        put("displayEnabled", displayEnabled)
    }

    companion object {
        fun fromJson(json: JSONObject): VmConfig = VmConfig(
            id = json.getString("id"),
            name = if (json.has("name")) json.getString("name") else "",
            os = json.getString("os"),
            cpu = json.getInt("cpu"),
            memory = json.getDouble("memory").toFloat(),
            cdrom = if (json.has("cdrom") && !json.isNull("cdrom")) json.getString("cdrom") else null,
            disk = if (json.has("disk") && !json.isNull("disk")) json.getString("disk") else null,
            network = json.getBoolean("network"),
            audio = json.getBoolean("audio"),
            bootMode = json.getString("bootMode"),
            kernel = if (json.has("kernel") && !json.isNull("kernel")) json.getString("kernel") else null,
            cmdline = if (json.has("cmdline") && !json.isNull("cmdline")) json.getString("cmdline") else null,
            displayEnabled = if (json.has("displayEnabled")) json.getBoolean("displayEnabled") else false
        )
    }
}

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
    val activity = context as? ComponentActivity
    val prefs = context.getSharedPreferences("qals_prefs", Context.MODE_PRIVATE)

    var currentScreen by remember { mutableStateOf("main") }

    // ---------- 配置状态 ----------
    var vmName by remember { mutableStateOf("") }
    var osExpanded by remember { mutableStateOf(false) }
    var selectedOs by remember { mutableStateOf("") }
    val osOptions = listOf("Windows", "Ubuntu")
    var cpuCores by remember { mutableStateOf(4f) }
    var memorySize by remember { mutableStateOf(2f) }
    var cdromEnabled by remember { mutableStateOf(false) }
    var cdromPath by remember { mutableStateOf("") }
    var diskEnabled by remember { mutableStateOf(false) }
    var diskPath by remember { mutableStateOf("") }
    var networkEnabled by remember { mutableStateOf(false) }
    var audioEnabled by remember { mutableStateOf(false) }
    var bootMode by remember { mutableStateOf("UEFI") }
    var kernelPath by remember { mutableStateOf("") }
    var kernelCmdline by remember { mutableStateOf("") }
    var displayEnabled by remember { mutableStateOf(false) }

    var editingVmId by remember { mutableStateOf<String?>(null) }

    // ---------- 开关状态 ----------
    var isGunyahEnabled by remember {
        mutableStateOf(prefs.getBoolean("gunyah_support", false))
    }
    var isGzvmEnabled by remember {
        mutableStateOf(prefs.getBoolean("gzvm_support", false))
    }
    var generateCommandEnabled by remember {
        mutableStateOf(prefs.getBoolean("generate_command_als", false))
    }

    // ---------- 虚拟机列表 ----------
    var vmList by remember { mutableStateOf<List<VmConfig>>(emptyList()) }

    // ---------- 删除对话框 ----------
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    // ---------- 命令对话框 ----------
    var showCommandDialog by remember { mutableStateOf(false) }
    var dialogCommand by remember { mutableStateOf("") }

    // ---------- 日志 ----------
    var logMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    fun addLog(msg: String) {
        logMessages = logMessages + msg
    }

    // ---------- 状态变量 ----------
    var rootStatus by remember { mutableStateOf("检测中...") }
    var gunyahStatus by remember { mutableStateOf("检测中...") }
    var gzvmStatus by remember { mutableStateOf("检测中...") }
    var qemuStatus by remember { mutableStateOf("检测中...") }

    // ---------- 刷新触发器 ----------
    var refreshTrigger by remember { mutableStateOf(0) }

    // ---------- TTY 相关 ----------
    var currentTTYInstance by remember { mutableStateOf<TTYInstance?>(null) }
    var currentRunningVm by remember { mutableStateOf<VmConfig?>(null) }

    // ---------- 名称校验 ----------
    var nameError by remember { mutableStateOf<String?>(null) }

    // ---------- 下载对话框状态 ----------
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadCompleted by remember { mutableStateOf(false) }

    // ---------- 加载 ----------
    LaunchedEffect(Unit) {
        vmList = loadVmList(prefs)
        addLog("--- 应用启动 ---")
        val (root, gunyah, gzvm) = checkAllStatus(onLog = ::addLog)
        rootStatus = root
        gunyahStatus = gunyah
        gzvmStatus = gzvm
        qemuStatus = checkQemuStatus(onLog = ::addLog)
        addLog("检测完成: Root=$root, Gunyah=$gunyah, GZVM=$gzvm, QEMU=$qemuStatus")
    }

    // ---------- 表单操作 ----------
    fun resetForm() {
        vmName = ""
        selectedOs = ""
        cpuCores = 4f
        memorySize = 2f
        cdromEnabled = false
        cdromPath = ""
        diskEnabled = false
        diskPath = ""
        networkEnabled = false
        audioEnabled = false
        bootMode = "UEFI"
        kernelPath = ""
        kernelCmdline = ""
        displayEnabled = false
        editingVmId = null
        nameError = null
    }

    fun loadVmForEdit(vm: VmConfig) {
        vmName = vm.name
        selectedOs = vm.os
        cpuCores = vm.cpu.toFloat()
        memorySize = vm.memory
        cdromEnabled = vm.cdrom != null
        cdromPath = vm.cdrom ?: ""
        diskEnabled = vm.disk != null
        diskPath = vm.disk ?: ""
        networkEnabled = vm.network
        audioEnabled = vm.audio
        bootMode = vm.bootMode
        kernelPath = vm.kernel ?: ""
        kernelCmdline = vm.cmdline ?: ""
        displayEnabled = vm.displayEnabled
        editingVmId = vm.id
        nameError = null
    }

    fun saveVm(): Boolean {
        if (vmName.isBlank()) {
            nameError = "请输入虚拟机名称"
            return false
        }
        val isDuplicate = vmList.any {
            it.name.equals(vmName, ignoreCase = true) && it.id != editingVmId
        }
        if (isDuplicate) {
            nameError = "虚拟机名称已存在"
            return false
        }
        nameError = null
        val config = VmConfig(
            id = editingVmId ?: System.currentTimeMillis().toString(),
            name = vmName,
            os = selectedOs,
            cpu = cpuCores.toInt(),
            memory = memorySize,
            cdrom = if (cdromEnabled) cdromPath else null,
            disk = if (diskEnabled) diskPath else null,
            network = networkEnabled,
            audio = audioEnabled,
            bootMode = bootMode,
            kernel = if (bootMode == "内核") kernelPath else null,
            cmdline = if (bootMode == "内核") kernelCmdline else null,
            displayEnabled = displayEnabled
        )
        val newList = if (editingVmId != null) {
            vmList.map { if (it.id == editingVmId) config else it }
        } else {
            vmList + config
        }
        saveVmList(prefs, newList)
        vmList = newList
        currentScreen = "main"
        resetForm()
        return true
    }

    fun deleteVm(vmId: String) {
        val newList = vmList.filter { it.id != vmId }
        saveVmList(prefs, newList)
        vmList = newList
    }

    fun stopVmInTerminal() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall qemu-system-aarch64"))
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    addLog("已执行 killall qemu-system-aarch64，成功终止 QEMU 进程")
                } else {
                    val error = process.errorStream.bufferedReader().readText().trim()
                    addLog("killall 执行失败，退出码: $exitCode, 错误: $error")
                }
            } catch (e: Exception) {
                addLog("执行 killall 异常: ${e.message}")
            }
        }.start()

        currentTTYInstance = null
        currentRunningVm = null
        refreshTrigger++
        addLog("虚拟机会话已标记为终止")
    }

    // ---------- 运行/终端逻辑 ----------
    fun startVmInTerminal(vm: VmConfig) {
        if (currentTTYInstance != null) {
            stopVmInTerminal()
        }

        addLog("准备在终端中启动虚拟机: ${vm.name}")
        if (vm.displayEnabled) {
            try {
                X11.prepare(context)
                addLog("X11 键盘配置已准备")
            } catch (e: Exception) {
                addLog("X11 准备失败: ${e.message}")
                return
            }
        }
        val cmdStr = QemuCommandBuilder.buildCommand(
            vm = vm,
            gunyahEnabled = isGunyahEnabled,
            gzvmEnabled = isGzvmEnabled,
            onLog = ::addLog
        )
        if (cmdStr == null) {
            addLog("命令构建失败")
            return
        }
        addLog("完整命令: $cmdStr")

        val sessionClient = TTYSessionStub()
        val viewClient = TTYViewStub()
        val instance = createTTYInstance(context, sessionClient, viewClient)
        currentTTYInstance = instance
        currentRunningVm = vm

        Handler(Looper.getMainLooper()).postDelayed({
            cmd(instance.session, "su")
            if (vm.displayEnabled) {
                val base = X11.qemuBase(isGunyahEnabled, isGzvmEnabled)
                val parts = X11.wrapCommandAsList(base, cmdStr)
                for (part in parts) {
                    cmd(instance.session, part)
                }
            } else {
                cmd(instance.session, cmdStr)
            }
            addLog("命令已发送到终端")
        }, 500)

        currentScreen = "tty"
    }

    fun openTerminal(vm: VmConfig) {
        if (currentTTYInstance == null || currentRunningVm?.id != vm.id) {
            startVmInTerminal(vm)
            return
        }
        currentScreen = "tty"
    }

    fun showCommandDialog(vm: VmConfig) {
        val fullCmd = VmManager.buildCommand(
            vm = vm,
            gunyahEnabled = isGunyahEnabled,
            gzvmEnabled = isGzvmEnabled,
            onLog = ::addLog
        )
        if (fullCmd == null) {
            addLog("生成命令失败")
            return
        }
        val qemuIndex = fullCmd.indexOfFirst { it.contains("qemu-system-aarch64") }
        if (qemuIndex == -1) {
            addLog("未找到 qemu-system-aarch64")
            return
        }
        val qemuArgs = fullCmd.drop(qemuIndex)
        dialogCommand = "./${qemuArgs.first().substringAfterLast("/")} ${qemuArgs.drop(1).joinToString(" ")}"
        showCommandDialog = true
    }

    fun startDownloadQemu() {
        showDownloadDialog = true
        downloadStatus = "正在下载..."
        downloadCompleted = false
        downloadProgress = 0f
        addLog("开始下载 QEMU...")

        var progressJob: Job? = null
        progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (downloadProgress < 40f && !downloadCompleted) {
                delay(10)
                downloadProgress += 0.2f
            }
            while (downloadProgress < 60f && !downloadCompleted) {
                delay(20)
                downloadProgress += 0.2f
            }
            while (downloadProgress < 80f && !downloadCompleted) {
                delay(40)
                downloadProgress += 0.2f
            }
            while (downloadProgress < 95f && !downloadCompleted) {
                delay(50)
                downloadProgress += 0.1f
                if (downloadProgress > 95f) downloadProgress = 90f
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cmd = "wget https://qemu-bin.down.yuyewan.dpdns.org/bin.zip -P /data/local/tmp && " +
                        "unzip /data/local/tmp/bin.zip -d /data/local/tmp -o && " +
                        "rm -f /data/local/tmp/bin.zip"
                addLog("执行命令: su -c '$cmd'")
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                if (output.isNotEmpty()) addLog("输出: $output")
                if (error.isNotEmpty()) addLog("错误: $error")

                progressJob?.cancel()

                withContext(Dispatchers.Main) {
                    if (exitCode == 0) {
                        downloadStatus = "下载并解压完成！"
                        downloadCompleted = true
                        downloadProgress = 100f
                        addLog("下载并解压完成！")
                        // 刷新 QEMU 状态
                        qemuStatus = checkQemuStatus(onLog = ::addLog)
                        addLog("QEMU 状态已刷新: $qemuStatus")
                    } else {
                        downloadStatus = "执行失败，退出码: $exitCode"
                        downloadProgress = 100f
                        addLog("执行失败，退出码: $exitCode")
                    }
                }
            } catch (e: Exception) {
                progressJob?.cancel()
                withContext(Dispatchers.Main) {
                    downloadStatus = "错误: ${e.message}"
                    downloadProgress = 100f
                    addLog("下载异常: ${e.message}")
                }
            }
        }
    }

    // ---------- 返回键 ----------
    BackHandler {
        if (currentScreen != "main") {
            if (currentScreen == "tty") {
                refreshTrigger++
                addLog("从终端返回主页，刷新状态")
            }
            if (currentScreen == "add_vm" || currentScreen == "edit_vm") {
                resetForm()
            }
            currentScreen = "main"
        } else {
            activity?.finish()
        }
    }

    // ---------- UI ----------
    QALSTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (currentScreen == "tty") {
                    null
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentScreen) {
                                    "main" -> "QALS"
                                    "settings" -> "设置"
                                    "add_vm" -> "添加虚拟机"
                                    "edit_vm" -> "修改虚拟机"
                                    "logs" -> "日志"
                                    else -> "QALS"
                                },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            when (currentScreen) {
                                "main" -> {
                                    TextButton(onClick = { currentScreen = "logs" }) {
                                        Text("日志", color = Color(0xFF2196F3), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                    TextButton(onClick = { currentScreen = "settings" }) {
                                        Text("设置", color = Color.Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                "settings" -> {
                                    TextButton(
                                        onClick = {
                                            prefs.edit().apply {
                                                putBoolean("gunyah_support", isGunyahEnabled)
                                                putBoolean("gzvm_support", isGzvmEnabled)
                                                putBoolean("generate_command_als", generateCommandEnabled)
                                            }.apply()
                                            currentScreen = "main"
                                        }
                                    ) {
                                        Text("保存", color = Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                "add_vm", "edit_vm" -> {
                                    TextButton(
                                        onClick = {
                                            if (saveVm()) { /* 自动跳转 */ }
                                        }
                                    ) {
                                        Text("保存", color = Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                    TextButton(onClick = {
                                        resetForm()
                                        currentScreen = "main"
                                    }) {
                                        Text("返回", color = Color.Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                "logs" -> {
                                    TextButton(onClick = { currentScreen = "main" }) {
                                        Text("返回", color = Color.Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Crossfade(targetState = currentScreen) { screen ->
                when (screen) {
                    "main" -> {
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 60.dp)
                            ) {
                                items(vmList) { vm ->
                                    val isRunning = currentTTYInstance != null && currentRunningVm?.id == vm.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .combinedClickable(
                                                onClick = {
                                                    loadVmForEdit(vm)
                                                    currentScreen = "edit_vm"
                                                },
                                                onLongClick = {
                                                    deleteTargetId = vm.id
                                                    showDeleteDialog = true
                                                }
                                            ),
                                        elevation = CardDefaults.cardElevation(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = vm.name.ifEmpty { vm.os },
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = vm.os,
                                                    fontSize = 14.sp,
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    text = "CPU: ${vm.cpu}核  内存: ${vm.memory}G",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                if (isRunning) {
                                                    Button(
                                                        onClick = { openTerminal(vm) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFF4CAF50)
                                                        ),
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    ) {
                                                        Text("终端")
                                                    }
                                                }
                                                Button(
                                                    onClick = {
                                                        if (isRunning) {
                                                            stopVmInTerminal()
                                                        } else {
                                                            if (generateCommandEnabled) {
                                                                showCommandDialog(vm)
                                                            } else {
                                                                startVmInTerminal(vm)
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isRunning) Color.Red else Color(0xFF1976D2)
                                                    )
                                                ) {
                                                    Text(if (isRunning) "结束" else "运行")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    resetForm()
                                    currentScreen = "add_vm"
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                Text("添加虚拟机", color = Color.Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            }

                            if (showDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteDialog = false },
                                    title = { Text("确认删除") },
                                    text = { Text("确定要删除此虚拟机吗？") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                deleteTargetId?.let { deleteVm(it) }
                                                showDeleteDialog = false
                                                deleteTargetId = null
                                            }
                                        ) {
                                            Text("删除", color = Color.Red)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showDeleteDialog = false
                                            deleteTargetId = null
                                        }) {
                                            Text("取消")
                                        }
                                    }
                                )
                            }

                            if (showCommandDialog) {
                                AlertDialog(
                                    onDismissRequest = { showCommandDialog = false },
                                    title = { Text("QEMU 启动命令") },
                                    text = {
                                        Text(
                                            text = dialogCommand,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("QEMU Command", dialogCommand)
                                            clipboard.setPrimaryClip(clip)
                                            addLog("命令已复制到剪贴板")
                                            showCommandDialog = false
                                        }) {
                                            Text("复制")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCommandDialog = false }) {
                                            Text("确定")
                                        }
                                    }
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
                            generateCommandEnabled = generateCommandEnabled,
                            onGenerateCommandChange = { newValue ->
                                generateCommandEnabled = newValue
                                prefs.edit().putBoolean("generate_command_als", newValue).apply()
                            },
                            onDownloadQemu = { startDownloadQemu() },
                            qemuStatus = qemuStatus,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    "add_vm", "edit_vm" -> {
                        AddVmScreen(
                            vmName = vmName,
                            onVmNameChange = {
                                vmName = it
                                nameError = null
                            },
                            osExpanded = osExpanded,
                            onOsExpandedChange = { osExpanded = it },
                            selectedOs = selectedOs,
                            onSelectedOsChange = { selectedOs = it },
                            osOptions = osOptions,
                            cpuCores = cpuCores,
                            onCpuCoresChange = { cpuCores = it },
                            memorySize = memorySize,
                            onMemorySizeChange = { memorySize = it },
                            cdromEnabled = cdromEnabled,
                            onCdromEnabledChange = { cdromEnabled = it },
                            cdromPath = cdromPath,
                            onCdromPathChange = { cdromPath = it },
                            diskEnabled = diskEnabled,
                            onDiskEnabledChange = { diskEnabled = it },
                            diskPath = diskPath,
                            onDiskPathChange = { diskPath = it },
                            networkEnabled = networkEnabled,
                            onNetworkEnabledChange = { networkEnabled = it },
                            audioEnabled = audioEnabled,
                            onAudioEnabledChange = { audioEnabled = it },
                            bootMode = bootMode,
                            onBootModeChange = { bootMode = it },
                            kernelPath = kernelPath,
                            onKernelPathChange = { kernelPath = it },
                            kernelCmdline = kernelCmdline,
                            onKernelCmdlineChange = { kernelCmdline = it },
                            displayEnabled = displayEnabled,
                            onDisplayEnabledChange = { displayEnabled = it },
                            nameError = nameError,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    "logs" -> {
                        LogsScreen(
                            logMessages = logMessages,
                            onClear = {
                                logMessages = emptyList()
                            }
                        )
                    }
                    "tty" -> {
                        currentTTYInstance?.let { instance ->
                            TTYScreen(
                                instance = instance,
                                content = { TTYIME() }
                            )
                        } ?: run {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("终端已关闭", color = Color.White)
                            }
                        }
                    }
                }
                // 下载 QEMU 对话框
                if (showDownloadDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            if (downloadCompleted || downloadStatus.contains("失败") || downloadStatus.contains("错误")) {
                                showDownloadDialog = false
                                downloadCompleted = false
                            }
                        },
                        title = { Text("下载 QEMU") },
                        text = {
                            Column {
                                Text(downloadStatus)
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = downloadProgress / 100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDownloadDialog = false
                                    downloadCompleted = false
                                },
                                enabled = downloadCompleted || downloadStatus.contains("失败") || downloadStatus.contains("错误")
                            ) {
                                Text("确定")
                            }
                        },
                        dismissButton = null
                    )
                }
            }
        }
    }
}

// ---------- 工具函数 ----------
fun loadVmList(prefs: android.content.SharedPreferences): List<VmConfig> {
    val jsonArray = prefs.getString("vm_list", "[]") ?: "[]"
    return try {
        val array = JSONArray(jsonArray)
        (0 until array.length()).map { index ->
            VmConfig.fromJson(array.getJSONObject(index))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveVmList(prefs: android.content.SharedPreferences, list: List<VmConfig>) {
    val array = JSONArray()
    list.forEach { array.put(it.toJson()) }
    prefs.edit().putString("vm_list", array.toString()).apply()
}

// ---------- QEMU 状态检测 ----------
private suspend fun checkQemuStatus(onLog: (String) -> Unit): String = withContext(Dispatchers.IO) {
    onLog("执行命令: su -c ls /data/local/tmp/qemu-gunyah/qemu-system-aarch64")
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /data/local/tmp/qemu-gunyah/qemu-system-aarch64"))
        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().readText().trim()
        val error = process.errorStream.bufferedReader().readText().trim()
        onLog("退出码: $exitCode, 输出: $output, 错误: $error")
        if (exitCode == 0 && output == "/data/local/tmp/qemu-gunyah/qemu-system-aarch64") {
            "可以使用"
        } else {
            "请先下载"
        }
    } catch (e: Exception) {
        onLog("检测 QEMU 异常: ${e.message}")
        "请先下载"
    }
}

// ---------- 状态检测（支持 Permission denied） ----------
suspend fun checkAllStatus(onLog: (String) -> Unit): Triple<String, String, String> =
    withContext(Dispatchers.IO) {
        onLog("执行命令: su -c exit")
        val root = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "exit"))
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            val error = process.errorStream.bufferedReader().readText().trim()
            onLog("退出码: $exitCode, 输出: $output, 错误: $error")
            if (exitCode == 0) "已获取" else "未获取"
        } catch (e: Exception) {
            onLog("异常: ${e.message}")
            "未获取"
        }

        onLog("执行命令: su -c ls /dev/gunyah")
        val gunyah = if (root == "已获取") {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /dev/gunyah"))
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                onLog("退出码: $exitCode, 输出: $output, 错误: $error")
                when {
                    output.contains("Permission denied") || error.contains("Permission denied") -> "检测到，但不可用"
                    exitCode == 0 && output == "/dev/gunyah" -> "可以使用"
                    else -> "不可使用"
                }
            } catch (e: Exception) {
                onLog("异常: ${e.message}")
                "不可使用"
            }
        } else {
            onLog("跳过（无root）")
            "不可使用"
        }

        onLog("执行命令: su -c ls /dev/gzvm")
        val gzvm = if (root == "已获取") {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /dev/gzvm"))
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                onLog("退出码: $exitCode, 输出: $output, 错误: $error")
                when {
                    output.contains("Permission denied") || error.contains("Permission denied") -> "检测到，但不可用"
                    exitCode == 0 && output == "/dev/gzvm" -> "可以使用"
                    else -> "不可使用"
                }
            } catch (e: Exception) {
                onLog("异常: ${e.message}")
                "不可使用"
            }
        } else {
            onLog("跳过（无root）")
            "不可使用"
        }

        Triple(root, gunyah, gzvm)
    }