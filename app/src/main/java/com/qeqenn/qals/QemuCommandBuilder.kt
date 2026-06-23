package com.qeqenn.qals

object QemuCommandBuilder {
    fun buildCommand(
        vm: VmConfig,
        gunyahEnabled: Boolean,
        gzvmEnabled: Boolean,
        onLog: (String) -> Unit
    ): String? {
        val base = X11.qemuBase(gunyahEnabled, gzvmEnabled)

        val libs = X11.qemuLibraryPath(base)
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
            // ---------- GZVM 专用 ----------
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
                    cmd.add("\"${vm.cmdline}\"")
                }
            } else {
                cmd.add("-bios")
                cmd.add(bios)
            }

            if (vm.disk != null) {
                cmd.add("-drive")
                cmd.add("if=none,file=${vm.disk},format=raw,id=hd")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=hd")
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

            if (vm.audio) {
                cmd.add("-audiodev")
                cmd.add("aaudio,id=aa")
                cmd.add("-device")
                cmd.add("virtio-snd-pci,audiodev=aa")
            }

            if (!vm.displayEnabled) {
                cmd.add("-nographic")
            }
        } else {
            // ---------- Gunyah / TCG 通用 ----------
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

            if (vm.audio) {
                cmd.add("-audiodev")
                cmd.add("aaudio,id=aa")
                cmd.add("-device")
                cmd.add("virtio-snd-pci,audiodev=aa")
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
        val command = cmd.joinToString(" ")
        return command
    }
}
