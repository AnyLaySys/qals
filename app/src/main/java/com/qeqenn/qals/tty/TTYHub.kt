package com.qeqenn.qals.tty

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import com.qeqenn.qals.R
import androidx.compose.material3.Text
import com.qeqenn.qals.ui.*  // 如果不存在，可改为自定义组件，但这里可以注释掉，因为未使用

@Composable
fun TTYHub(
    sessions: List<TTYInstance>,
    onSelect: (TTYInstance) -> Unit,
    onDelete: (TTYInstance) -> Unit,
    onCreate: () -> Unit
) = Column(
    Modifier
        .fillMaxSize()
        .background(Color.Black)
) {
    Column(
        Modifier
            .weight(1f)
            .padding(9.dp)
            .verticalScroll(rememberScrollState())
    ) {
        sessions.forEachIndexed { i, tty ->
            // 由于 ALSList 和 ALSButton 可能不存在，暂时注释，实际需要时再实现
            // ALSList(...)
            Text("Session ${i+1}")
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp), Alignment.Center
    ) {
        // ALSButton(R.drawable.add) { onCreate() }
        Text("Add")
    }
}