package com.qeqenn.qals.tty

import com.termux.terminal.*

object TTYEnv {
    // 使用 /system/bin/sh 作为 shell，工作目录设为 /sdcard 或 /data/local/tmp
    val args = arrayOf("-i")
    val env: Array<String> by lazy {
        val systemEnv = System.getenv().toMutableMap()
        systemEnv["TERM"] = "xterm-direct"
        // 可选：设置 HOME 为可写目录
        systemEnv["HOME"] = "/data/local/tmp"
        systemEnv.map { "${it.key}=${it.value}" }.toTypedArray()
    }
}

// 自定义 TerminalSession 工厂，使用 TTYEnv 配置
fun TerminalSession(env: TTYEnv, rows: Int, client: TerminalSessionClient): TerminalSession =
    // 启动 shell，工作目录设为 /data/local/tmp
    TerminalSession("sh", "/data/local/tmp", env.args, env.env, rows, client)