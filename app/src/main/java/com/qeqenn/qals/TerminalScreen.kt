package com.qeqenn.qals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

@Composable
fun TerminalScreen(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    SelectionContainer {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp),
            state = listState
        ) {
            items(lines) { line ->
                val annotated = parseAnsi(line)
                Text(
                    text = annotated,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 解析 ANSI 转义序列并返回带样式的 AnnotatedString
 * 支持颜色：黑、红、绿、黄、蓝、紫、青、白
 * 支持加粗（1）
 */
private fun parseAnsi(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString(text)

    // ANSI 转义序列模式：\033[...m
    val pattern = Pattern.compile("\u001B\\[[;\\d]*m")
    val matcher = pattern.matcher(text)

    // 颜色映射
    val colorMap = mapOf(
        "30" to Color.Black,
        "31" to Color.Red,
        "32" to Color(0xFF00FF00), // 绿色
        "33" to Color(0xFFFFFF00), // 黄色
        "34" to Color.Blue,
        "35" to Color(0xFFFF00FF), // 品红
        "36" to Color(0xFF00FFFF), // 青色
        "37" to Color.White
    )

    // 默认颜色改为白色
    val defaultColor = Color.White

    val result = buildAnnotatedString {
        var currentColor = defaultColor
        var isBold = false
        var lastEnd = 0

        while (matcher.find()) {
            val before = text.substring(lastEnd, matcher.start())
            if (before.isNotEmpty()) {
                appendAnnotated(before, currentColor, isBold)
            }

            val seq = matcher.group()
            val codes = seq.replace("\u001B[", "").replace("m", "").split(";")
            var colorChanged = false
            var newColor = currentColor
            var newBold = isBold

            for (code in codes) {
                when (code) {
                    "0" -> {
                        newColor = defaultColor
                        newBold = false
                        colorChanged = true
                    }
                    "1" -> {
                        newBold = true
                    }
                    "22" -> {
                        newBold = false
                    }
                    in colorMap.keys -> {
                        newColor = colorMap[code] ?: currentColor
                        colorChanged = true
                    }
                    // 其他忽略
                }
            }

            if (colorChanged || newBold != isBold) {
                currentColor = newColor
                isBold = newBold
            }

            lastEnd = matcher.end()
        }

        if (lastEnd < text.length) {
            val remaining = text.substring(lastEnd)
            appendAnnotated(remaining, currentColor, isBold)
        }
    }

    return result
}

private fun AnnotatedString.Builder.appendAnnotated(text: String, color: Color, bold: Boolean) {
    if (text.isEmpty()) return
    withStyle(
        style = SpanStyle(
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    ) {
        append(text)
    }
}