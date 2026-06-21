package com.qeqenn.qals

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qeqenn.qals.ui.theme.QALSTheme
import kotlinx.coroutines.Dispatchers
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
    val cmdline: String? = null
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
            cmdline = if (json.has("cmdline") && !json.isNull("cmdline")) json.getString("cmdline") else null
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

    var editingVmId by remember { mutableStateOf<String?>(null) }

    // ---------- 开关状态 ----------
    var isGunyahEnabled by remember {
        mutableStateOf(prefs.getBoolean("gunyah_support", false))
    }
    var isGzvmEnabled by remember {
        mutableStateOf(prefs.getBoolean("gzvm_support", false))
    }

    // ---------- 虚拟机列表 ----------
    var vmList by remember { mutableStateOf<List<VmConfig>>(emptyList()) }

    // ---------- 删除确认对话框状态 ----------
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vmList = loadVmList(prefs)
    }

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
        editingVmId = null
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
        editingVmId = vm.id
    }

    fun saveVm() {
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
            cmdline = if (bootMode == "内核") kernelCmdline else null
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
    }

    fun deleteVm(vmId: String) {
        val newList = vmList.filter { it.id != vmId }
        saveVmList(prefs, newList)
        vmList = newList
    }

    // ---------- 状态检测 ----------
    var rootStatus by remember { mutableStateOf("检测中...") }
    var gunyahStatus by remember { mutableStateOf("检测中...") }
    var gzvmStatus by remember { mutableStateOf("检测中...") }

    LaunchedEffect(Unit) {
        val (root, gunyah, gzvm) = checkAllStatus()
        rootStatus = root
        gunyahStatus = gunyah
        gzvmStatus = gzvm
    }

    // ---------- UI ----------
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
                                "edit_vm" -> "修改虚拟机"
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
                                    Text("设置", color = Color.Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
                                    Text("保存", color = Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            "add_vm", "edit_vm" -> {
                                TextButton(onClick = { saveVm() }) {
                                    Text("保存", color = Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                                TextButton(onClick = {
                                    resetForm()
                                    currentScreen = "main"
                                }) {
                                    Text("返回", color = Color.Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                )
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
                                            Button(
                                                onClick = {
                                                    // TODO: 运行虚拟机逻辑（后续实现）
                                                },
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Text("运行")
                                            }
                                        }
                                    }
                                }
                            }

                            // 添加虚拟机按钮
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

                            // ---------- 删除确认对话框 ----------
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
                    "add_vm", "edit_vm" -> {
                        AddVmScreen(
                            vmName = vmName,
                            onVmNameChange = { vmName = it },
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
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

// ---------- 状态检测 ----------
suspend fun checkAllStatus(): Triple<String, String, String> = withContext(Dispatchers.IO) {
    val root = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "exit"))
        val exitCode = process.waitFor()
        if (exitCode == 0) "已获取" else "未获取"
    } catch (e: Exception) {
        Log.e("StatusCheck", "检测 root 失败: ${e.message}")
        "未获取"
    }

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
    } else "不可使用"

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
    } else "不可使用"

    Triple(root, gunyah, gzvm)
}