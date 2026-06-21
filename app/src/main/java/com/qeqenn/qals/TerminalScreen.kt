package com.qeqenn.qals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalScreen(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // 自动滚动到底部（新行出现时）
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp),
        state = listState
    ) {
        items(lines) { line ->
            Text(
                text = line,
                color = Color(0xFF00FF00), // 绿色终端风格
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}