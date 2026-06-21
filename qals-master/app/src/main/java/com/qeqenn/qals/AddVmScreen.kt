package com.qeqenn.qals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun AddVmScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "这里是添加虚拟机界面",
            fontSize = 20.sp,
            color = Color.Gray
        )
        // onBackClick 供后续扩展，目前右上角已有返回按钮
    }
}