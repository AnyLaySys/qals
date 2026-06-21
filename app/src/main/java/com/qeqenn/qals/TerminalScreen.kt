package com.qeqenn.qals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun TerminalScreen(
    lines: List<String>,
    vmId: String?,
    modifier: Modifier = Modifier
) {
    // 强制使用 fillMaxSize 忽略外部 modifier 的尺寸限制
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    // 固定键盘高度（增加为 160dp 以保证可见）
    val keyboardHeight = 160.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 输出区域：填满整个 Box，底部预留键盘高度
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = keyboardHeight)
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

        // 底部键盘悬浮（固定在底部）
        TerminalKeyboard(
            vmId = vmId,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(keyboardHeight)
                .background(Color(0xFF1A1A1A))
                .padding(8.dp)
        )
    }
}

@Composable
fun TerminalKeyboard(vmId: String?, modifier: Modifier = Modifier) {
    val row1 = listOf("ESC", "CTRL", "ALT", "HOME", "END", "PGUP", "PGDN")
    val row2 = listOf("←", "↑", "↓", "→", "ENTER")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row1.forEach { key ->
                KeyButton(
                    text = key,
                    onClick = {
                        if (vmId != null) {
                            val data = when (key) {
                                "ESC" -> "\u001b"
                                "CTRL" -> "\u0011"
                                "ALT" -> "\u001b"
                                "HOME" -> "\u001b[1~"
                                "END" -> "\u001b[4~"
                                "PGUP" -> "\u001b[5~"
                                "PGDN" -> "\u001b[6~"
                                else -> ""
                            }
                            VmManager.sendInput(vmId, data)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row2.forEach { key ->
                KeyButton(
                    text = key,
                    onClick = {
                        if (vmId != null) {
                            val data = when (key) {
                                "←" -> "\u001b[D"
                                "↑" -> "\u001b[A"
                                "↓" -> "\u001b[B"
                                "→" -> "\u001b[C"
                                "ENTER" -> "\n"
                                else -> ""
                            }
                            VmManager.sendInput(vmId, data)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun KeyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF333333), shape = RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 15.sp, color = Color.White)
    }
}

// ---------- ANSI 颜色解析 ----------
private fun parseAnsi(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString(text)
    val pattern = Pattern.compile("\u001B\\[[;\\d]*m")
    val matcher = pattern.matcher(text)
    val colorMap = mapOf(
        "30" to Color.Black,
        "31" to Color.Red,
        "32" to Color(0xFF00FF00),
        "33" to Color(0xFFFFFF00),
        "34" to Color.Blue,
        "35" to Color(0xFFFF00FF),
        "36" to Color(0xFF00FFFF),
        "37" to Color.White,
        "90" to Color.DarkGray,
        "91" to Color.Red,
        "92" to Color.Green,
        "93" to Color.Yellow,
        "94" to Color.Blue,
        "95" to Color.Magenta,
        "96" to Color.Cyan,
        "97" to Color.White
    )
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
            var newColor = currentColor
            var newBold = isBold
            for (code in codes) {
                when (code) {
                    "0" -> { newColor = defaultColor; newBold = false }
                    "1" -> { newBold = true }
                    "22" -> { newBold = false }
                    in colorMap.keys -> { newColor = colorMap[code] ?: currentColor }
                    else -> {}
                }
            }
            currentColor = newColor
            isBold = newBold
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
    withStyle(style = SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)) {
        append(text)
    }
}