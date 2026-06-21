package com.qeqenn.qals

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVmScreen(
    nameError: String? = null,
    vmName: String,
    onVmNameChange: (String) -> Unit,
    osExpanded: Boolean,
    onOsExpandedChange: (Boolean) -> Unit,
    selectedOs: String,
    onSelectedOsChange: (String) -> Unit,
    osOptions: List<String>,
    cpuCores: Float,
    onCpuCoresChange: (Float) -> Unit,
    memorySize: Float,
    onMemorySizeChange: (Float) -> Unit,
    cdromEnabled: Boolean,
    onCdromEnabledChange: (Boolean) -> Unit,
    cdromPath: String,
    onCdromPathChange: (String) -> Unit,
    diskEnabled: Boolean,
    onDiskEnabledChange: (Boolean) -> Unit,
    diskPath: String,
    onDiskPathChange: (String) -> Unit,
    networkEnabled: Boolean,
    onNetworkEnabledChange: (Boolean) -> Unit,
    audioEnabled: Boolean,
    onAudioEnabledChange: (Boolean) -> Unit,
    bootMode: String,
    onBootModeChange: (String) -> Unit,
    kernelPath: String,
    onKernelPathChange: (String) -> Unit,
    kernelCmdline: String,
    onKernelCmdlineChange: (String) -> Unit,
    displayEnabled: Boolean,
    onDisplayEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val cdromLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = getAbsolutePath(context, uri)
                onCdromPathChange(path ?: uri.path ?: "无法获取路径")
            }
        }
    }

    val diskLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = getAbsolutePath(context, uri)
                onDiskPathChange(path ?: uri.path ?: "无法获取路径")
            }
        }
    }

    val kernelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = getAbsolutePath(context, uri)
                onKernelPathChange(path ?: uri.path ?: "无法获取路径")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 名称
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = vmName,
                onValueChange = onVmNameChange,
                label = { Text("名称") },
                placeholder = { Text("输入虚拟机名称") },
                isError = nameError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
            if (nameError != null) {
                Text(
                    text = nameError,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }

        // 操作系统
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "操作系统：",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            ExposedDropdownMenuBox(
                expanded = osExpanded,
                onExpandedChange = onOsExpandedChange
            ) {
                TextField(
                    value = selectedOs,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("请选择") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = osExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .weight(1f)
                )
                ExposedDropdownMenu(
                    expanded = osExpanded,
                    onDismissRequest = { onOsExpandedChange(false) }
                ) {
                    osOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onSelectedOsChange(option)
                                onOsExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        // CPU
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU核心数：",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(
                    text = "${cpuCores.toInt()} 核",
                    fontSize = 18.sp,
                    color = Color.Blue
                )
            }
            Slider(
                value = cpuCores,
                onValueChange = onCpuCoresChange,
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 内存
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "内存大小：",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(
                    text = "${String.format("%.1f", memorySize)}G",
                    fontSize = 18.sp,
                    color = Color.Blue
                )
            }
            Slider(
                value = memorySize,
                onValueChange = onMemorySizeChange,
                valueRange = 0.5f..6f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 光盘
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "光盘：",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Switch(
                    checked = cdromEnabled,
                    onCheckedChange = onCdromEnabledChange
                )
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        cdromLauncher.launch(intent)
                    },
                    enabled = cdromEnabled,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("选择文件")
                }
            }
            if (cdromPath.isNotEmpty()) {
                Text(
                    text = cdromPath,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        // 硬盘
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "硬盘：",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Switch(
                    checked = diskEnabled,
                    onCheckedChange = onDiskEnabledChange
                )
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        diskLauncher.launch(intent)
                    },
                    enabled = diskEnabled,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("选择文件")
                }
            }
            if (diskPath.isNotEmpty()) {
                Text(
                    text = diskPath,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        // 网络
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "启用网络",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = networkEnabled,
                onCheckedChange = onNetworkEnabledChange
            )
        }

        // 音频（保留开关但实际已禁用，但仍存储到配置，以备后续使用）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "启用音频",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = audioEnabled,
                onCheckedChange = onAudioEnabledChange
            )
        }

        // 启动方式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "启动方式：",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Button(
                onClick = { onBootModeChange("UEFI") },
                colors = if (bootMode == "UEFI") {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                } else {
                    ButtonDefaults.buttonColors(containerColor = Color.Gray)
                },
                modifier = Modifier.weight(0.45f)
            ) {
                Text("UEFI")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onBootModeChange("内核") },
                colors = if (bootMode == "内核") {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                } else {
                    ButtonDefaults.buttonColors(containerColor = Color.Gray)
                },
                modifier = Modifier.weight(0.45f)
            ) {
                Text("内核")
            }
        }

        // 内核
        if (bootMode == "内核") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "内核：",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            kernelLauncher.launch(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("选择文件")
                    }
                }
                if (kernelPath.isNotEmpty()) {
                    Text(
                        text = kernelPath,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "内核命令行：",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                TextField(
                    value = kernelCmdline,
                    onValueChange = onKernelCmdlineChange,
                    placeholder = { Text("输入命令行参数") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---------- 新增：启用显示 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "启用显示",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = displayEnabled,
                onCheckedChange = onDisplayEnabledChange
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

private fun getAbsolutePath(context: Context, uri: android.net.Uri): String? {
    return try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            if (split.size > 1) {
                if (split[0] == "primary") {
                    "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
                } else {
                    "/storage/${split[0]}/${split[1]}"
                }
            } else {
                uri.path
            }
        } else {
            uri.path
        }
    } catch (e: Exception) {
        uri.path
    }
}