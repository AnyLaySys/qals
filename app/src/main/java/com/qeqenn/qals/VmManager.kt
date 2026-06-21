package com.qeqenn.qals

import java.io.File
import java.util.concurrent.ConcurrentHashMap

object VmManager {
    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val runningPids = ConcurrentHashMap<String, Int>()

    fun isRunning(vmId: String): Boolean = runningProcesses.containsKey(vmId)

    fun startVm(
        vm: VmConfig,
        gunyahEnabled: Boolean,
        gzvmEnabled: Boolean,
        onLog: (String) -> Unit,
        onOutput: (String) -> Unit,
        onExit: (Int) -> Unit
    ): Boolean {
        if (isRunning(vm.id)) {
            stopVm(vm.id)
        }

        val cmd = buildCommand(vm, gunyahEnabled, gzvmEnabled, onLog)
        if (cmd == null) {
            onLog("构建命令行失败")
            return false
        }

        onLog("启动虚拟机: ${vm.name}")
        onLog("命令: ${cmd.joinToString(" ")}")

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd.joinToString(" ")))
            runningProcesses[vm.id] = process
            try {
                val pidField = process.javaClass.getDeclaredField("pid")
                pidField.isAccessible = true
                val pid = pidField.getInt(process)
                runningPids[vm.id] = pid
                onLog("进程 PID: $pid")
            } catch (e: Exception) {
                onLog("无法获取 PID")
            }

            // 读取 stdout
            Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            onOutput(line)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }.start()

            // 读取 stderr
            Thread {
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            onOutput(line)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }.start()

            // 监控进程退出
            Thread {
                try {
                    val exitCode = process.waitFor()
                    runningProcesses.remove(vm.id)
                    runningPids.remove(vm.id)
                    onLog("进程退出，退出码: $exitCode")
                    onExit(exitCode)
                } catch (e: Exception) {
                    onLog("监控进程退出异常: ${e.message}")
                }
            }.start()

            true
        } catch (e: Exception) {
            onLog("启动失败: ${e.message}")
            false
        }
    }

    fun stopVm(vmId: String) {
        runningProcesses[vmId]?.let { process ->
            try {
                process.destroy()
                process.waitFor()
                runningProcesses.remove(vmId)
                runningPids.remove(vmId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun stopAll() {
        runningProcesses.keys.forEach { stopVm(it) }
    }

    private fun buildCommand(
        vm: VmConfig,
        gunyahEnabled: Boolean,
        gzvmEnabled: Boolean,
        onLog: (String) -> Unit
    ): Array<String>? {
        // 确定基础路径
        val base = when {
            // TCG 加速：使用 gzvm 的二进制
            !gunyahEnabled && !gzvmEnabled -> "/data/local/tmp/qemu-gzvm"
            gzvmEnabled -> "/data/local/tmp/qemu-gzvm"
            else -> "/data/local/tmp/qemu-gunyah"
        }

        val libs = base      // LD_LIBRARY_PATH 直接指向 base
        val bios = "$base/QEMU_EFI.fd"

        val cpuCount = vm.cpu
        val memSize = vm.memory.toInt()

        val cmd = mutableListOf<String>()
        // 新增 DISPLAY 环境变量
        cmd.add("DISPLAY=:1")
        cmd.add("LD_LIBRARY_PATH=$libs")
        cmd.add("nice")
        cmd.add("-n")
        cmd.add("-20")
        cmd.add("taskset")
        val mask = ((1L shl cpuCount) - 1).toString(16)
        cmd.add(mask)
        cmd.add("$base/qemu-system-aarch64")
        cmd.add("-L")
        cmd.add("$base/pc-bios")

        // 确定加速器
        val accel = when {
            gunyahEnabled && gzvmEnabled -> {
                onLog("警告：同时启用 Gunyah 和 GZVM，使用 Gunyah")
                "gunyah"
            }
            gunyahEnabled -> "gunyah"
            gzvmEnabled -> "gzvm"
            else -> "tcg,thread=multi"
        }

        // ---------- 根据加速器选择不同参数 ----------
        if (accel == "gzvm") {
            // ---------- GZVM 专用参数（依据成功命令） ----------
            cmd.add("-M")
            cmd.add("virt,gic-version=3")

            cmd.add("-cpu")
            cmd.add("host")

            cmd.add("-accel")
            cmd.add("gzvm")

            cmd.add("-smp")
            cmd.add("$cpuCount,sockets=1,cores=$cpuCount,threads=1")
            cmd.add("-m")
            cmd.add("${memSize}G")

            // 内核或 bios
            if (vm.bootMode == "内核") {
                if (vm.kernel.isNullOrEmpty()) {
                    onLog("内核文件未选择")
                    return null
                }
                cmd.add("-kernel")
                cmd.add(vm.kernel)
                if (!vm.cmdline.isNullOrEmpty()) {
                    cmd.add("-append")
                    cmd.add(vm.cmdline)
                }
            } else {
                cmd.add("-bios")
                cmd.add(bios)
            }

            // 硬盘（使用 ioeventfd=off 模式）
            if (vm.disk != null) {
                cmd.add("-drive")
                cmd.add("if=none,file=${vm.disk},format=raw,id=hd")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=hd,ioeventfd=off")
            }

            // 光盘（如果配置了）
            if (vm.cdrom != null) {
                cmd.add("-drive")
                cmd.add("if=none,file=${vm.cdrom},format=raw,id=cd")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=cd")
            }

            // 网络
            if (vm.network) {
                cmd.add("-netdev")
                cmd.add("user,id=usernet,hostfwd=tcp::2222-:22")
                cmd.add("-device")
                cmd.add("virtio-net-pci,netdev=usernet")
            }

            // GZVM 使用 -nographic
            cmd.add("-nographic")

            // 注意：不添加音频、显卡、USB 等

        } else {
            // ---------- Gunyah 或 TCG 的通用参数 ----------
            cmd.add("-M")
            cmd.add("virt,confidential-guest-support=prot0")

            cmd.add("--accel")
            cmd.add(accel)

            if (accel == "tcg,thread=multi" || accel == "tcg") {
                cmd.add("-cpu")
                cmd.add("cortex-a76")
            } else {
                cmd.add("-cpu")
                cmd.add("host")
            }

            cmd.add("-smp")
            cmd.add("$cpuCount,sockets=1,cores=$cpuCount,threads=1")
            cmd.add("-m")
            cmd.add("${memSize}G")

            // Gunyah 需要机密对象
            if (accel == "gunyah" || (gunyahEnabled && !gzvmEnabled)) {
                cmd.add("-object")
                cmd.add("arm-confidential-guest,id=prot0,swiotlb-size=64M")
            }

            if (vm.bootMode == "内核") {
                if (vm.kernel.isNullOrEmpty()) {
                    onLog("内核文件未选择")
                    return null
                }
                cmd.add("-kernel")
                cmd.add(vm.kernel)
                if (!vm.cmdline.isNullOrEmpty()) {
                    cmd.add("-append")
                    cmd.add(vm.cmdline)
                }
            } else {
                cmd.add("-bios")
                cmd.add(bios)
            }

            // iothread
            cmd.add("-object")
            cmd.add("iothread,id=io0")

            // 光盘
            if (vm.cdrom != null) {
                cmd.add("-drive")
                cmd.add("file=${vm.cdrom},if=none,id=dr1,format=raw,aio=threads,media=cdrom")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=dr1,bootindex=1")
            }

            // 硬盘
            if (vm.disk != null) {
                cmd.add("-drive")
                cmd.add("file=${vm.disk},if=none,id=dr0,cache=unsafe,aio=threads,discard=unmap")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=dr0,num-queues=$cpuCount,iothread=io0,disable-legacy=on,disable-modern=off,bootindex=2")
            }

            // 网络
            if (vm.network) {
                cmd.add("-netdev")
                cmd.add("user,id=usernet,hostfwd=tcp::2222-:22")
                cmd.add("-device")
                cmd.add("virtio-net-pci,netdev=usernet")
            }

            // 图形设备
            cmd.add("-device")
            cmd.add("virtio-gpu-pci,xres=2376,yres=1080")
            cmd.add("-display")
            cmd.add("sdl")

            // 键盘鼠标
            cmd.add("-device")
            cmd.add("virtio-tablet-pci")
            cmd.add("-device")
            cmd.add("virtio-keyboard-pci")

            // 音频
            cmd.add("-audiodev")
            cmd.add("aaudio,id=aa")
            cmd.add("-device")
            cmd.add("virtio-snd-pci,audiodev=aa")

            // 串口
            cmd.add("-serial")
            cmd.add("mon:stdio")
        }

        onLog("命令构建完成，共 ${cmd.size} 个参数")
        return cmd.toTypedArray()
    }
}