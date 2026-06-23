package com.qeqenn.qals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isGunyahEnabled: Boolean,
    onGunyahEnabledChange: (Boolean) -> Unit,
    isGzvmEnabled: Boolean,
    onGzvmEnabledChange: (Boolean) -> Unit,
    rootStatus: String,
    gunyahStatus: String,
    gzvmStatus: String,
    generateCommandEnabled: Boolean,
    onGenerateCommandChange: (Boolean) -> Unit,
    onDownloadQemu: () -> Unit,
    qemuStatus: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Gunyah 支持开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gunyah支持",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isGunyahEnabled,
                onCheckedChange = onGunyahEnabledChange
            )
        }

        // GZVM 支持开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GZVM支持",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isGzvmEnabled,
                onCheckedChange = onGzvmEnabledChange
            )
        }

        // Generate command for ALS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Generate command for ALS",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = generateCommandEnabled,
                onCheckedChange = onGenerateCommandChange
            )
        }

        // Root 状态
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Root状态：",
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = rootStatus,
                fontSize = 18.sp,
                color = if (rootStatus == "已获取") Color.Green else Color.Red,
                modifier = Modifier.weight(0.6f)
            )
        }

        // Gunyah 状态（三态颜色）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gunyah状态：",
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = gunyahStatus,
                fontSize = 18.sp,
                color = when (gunyahStatus) {
                    "可以使用" -> Color.Green
                    "检测到，但不可用" -> Color.Gray
                    else -> Color.Red
                },
                modifier = Modifier.weight(0.6f)
            )
        }

        // GZVM 状态（三态颜色）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GZVM状态：",
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = gzvmStatus,
                fontSize = 18.sp,
                color = when (gzvmStatus) {
                    "可以使用" -> Color.Green
                    "检测到，但不可用" -> Color.Gray
                    else -> Color.Red
                },
                modifier = Modifier.weight(0.6f)
            )
        }

        // QEMU 状态
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QEMU状态：",
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = qemuStatus,
                fontSize = 18.sp,
                color = if (qemuStatus == "可以使用") Color.Green else Color.Red,
                modifier = Modifier.weight(0.6f)
            )
        }

        // 下载 QEMU 按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDownloadQemu,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("下载 QEMU", fontSize = 18.sp)
            }
        }
    }
}