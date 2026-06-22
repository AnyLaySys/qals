package com.qeqenn.qals

import android.content.Context
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object VmManager {
    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val runningPids = ConcurrentHashMap<String, Int>()
    private val runningStreams = ConcurrentHashMap<String, OutputStream>()

    fun isRunning(vmId: String): Boolean = runningProcesses.containsKey(vmId)

    fun startVm(
        vm: VmConfig,
        gunyahEnabled: Boolean,
        gzvmEnabled: Boolean,
        onLog: (String) -> Unit,
        onOutput: (String) -> Unit,
        onExit: (Int) -> Unit
    ): Boolean {
        if (vm.displayEnabled) {
            onLog("显示模式需要 Context 初始化 X11")
            return false
        }
        return startVm(null, vm, gunyahEnabled, gzvmEnabled, onLog, onOutput, onExit)
    }

    fun startVm(
        context: Context?,
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

        if (vm.displayEnabled) {
            try {
                X11.prepare(context ?: return false)
            } catch (e: Exception) {
                onLog("X11 准备失败: ${e.message}")
                return false
            }
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
            runningStreams[vm.id] = process.outputStream
            try {
                val pidField = process.javaClass.getDeclaredField("pid")
                pidField.isAccessible = true
                val pid = pidField.getInt(process)
                runningPids[vm.id] = pid
                onLog("进程 PID: $pid")
            } catch (e: Exception) {
                onLog("无法获取 PID")
            }

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

            Thread {
                try {
                    val exitCode = process.waitFor()
                    runningProcesses.remove(vm.id)
                    runningPids.remove(vm.id)
                    runningStreams.remove(vm.id)?.close()
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
            Thread {
                try {
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                    runningProcesses.remove(vmId)
                    runningPids.remove(vmId)
                    runningStreams.remove(vmId)?.close()
                } catch (e: Exception) {
                    // 忽略所有异常，防止闪退
                }
            }.start()
        }
    }

    fun stopAll() {
        runningProcesses.keys.forEach { stopVm(it) }
    }

    fun sendInput(vmId: String, data: String) {
        runningStreams[vmId]?.let { os ->
            try {
                os.write(data.toByteArray())
                os.flush()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun buildCommand(
        vm: VmConfig,
        gunyahEnabled: Boolean,
        gzvmEnabled: Boolean,
        onLog: (String) -> Unit
    ): Array<String>? {
        val base = X11.qemuBase(gunyahEnabled, gzvmEnabled)

        val libs = if (gunyahEnabled && !gzvmEnabled) {
            "$base/lib"
        } else {
            base
        }

        val bios = "$base/edk2-aarch64-gunyah.fd"

        val cpuCount = vm.cpu
        val memSize = vm.memory.toInt()

        val cmd = mutableListOf<String>()
        cmd.add("DISPLAY=:1")
        cmd.add("SDL_AUDIODRIVER=aaudio")
        cmd.add("LANG=C")
        cmd.add("LC_ALL=C")
        cmd.add("LD_LIBRARY_PATH=$libs")
        cmd.add("nice")
        cmd.add("-n")
        cmd.add("-20")
        cmd.add("taskset")
        val mask = ((1L shl cpuCount) - 1).toString(16)
        cmd.add(mask)
        cmd.add("$base/qemu-system-aarch64")
        cmd.add("-L")
        cmd.add("$base/fw")

        val accel = when {
            gunyahEnabled && gzvmEnabled -> {
                onLog("警告：同时启用 Gunyah 和 GZVM，使用 Gunyah")
                "gunyah"
            }
            gunyahEnabled -> "gunyah"
            gzvmEnabled -> "gzvm"
            else -> "tcg,thread=multi"
        }

        if (accel == "gzvm") {
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

            if (vm.disk != null) {
                cmd.add("-drive")
                cmd.add("if=none,file=${vm.disk},format=raw,id=hd")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=hd,ioeventfd=off")
            }

            if (vm.cdrom != null) {
                cmd.add("-drive")
                cmd.add("if=none,file=${vm.cdrom},format=raw,id=cd")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=cd")
            }

            if (vm.network) {
                cmd.add("-netdev")
                cmd.add("user,id=usernet,hostfwd=tcp::2222-:22")
                cmd.add("-device")
                cmd.add("virtio-net-pci,netdev=usernet")
            }

            if (!vm.displayEnabled) {
                cmd.add("-nographic")
            }

        } else {
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

            cmd.add("-object")
            cmd.add("iothread,id=io0")

            if (vm.cdrom != null) {
                cmd.add("-drive")
                cmd.add("file=${vm.cdrom},if=none,id=dr1,format=raw,aio=threads,media=cdrom")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=dr1,bootindex=1")
            }

            if (vm.disk != null) {
                cmd.add("-drive")
                cmd.add("file=${vm.disk},if=none,id=dr0,cache=unsafe,aio=threads,discard=unmap")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=dr0,num-queues=$cpuCount,iothread=io0,disable-legacy=on,disable-modern=off,bootindex=2")
            }

            if (vm.network) {
                cmd.add("-netdev")
                cmd.add("user,id=usernet,hostfwd=tcp::2222-:22")
                cmd.add("-device")
                cmd.add("virtio-net-pci,netdev=usernet")
            }

            cmd.add("-device")
            cmd.add("virtio-gpu-pci,xres=2376,yres=1080")

            if (!vm.displayEnabled) {
                cmd.add("-nographic")
            } else {
                if (accel == "gunyah" || (gunyahEnabled && !gzvmEnabled)) {
                    cmd.add("-display")
                    cmd.add("sdl")
                }
            }

            cmd.add("-device")
            cmd.add("virtio-tablet-pci")
            cmd.add("-device")
            cmd.add("virtio-keyboard-pci")

            cmd.add("-serial")
            cmd.add("mon:stdio")
        }

        onLog("命令构建完成，共 ${cmd.size} 个参数")
        if (vm.displayEnabled) {
            return arrayOf(X11.wrapCommand(base, cmd.joinToString(" ")))
        }
        return cmd.toTypedArray()
    }
}
