package com.qeqenn.qals

import android.content.Context
import android.content.Intent
import java.io.File
import java.util.zip.ZipInputStream

object X11 {
    const val appPackage = "com.qeqenn.qals"

    fun prepare(context: Context): File {
        val root = File(context.cacheDir, "x11/xkb")
        if (File(root, "rules/evdev").exists() && File(root, "symbols/us").exists()) return root
        root.deleteRecursively()
        root.mkdirs()
        ZipInputStream(context.assets.open("xkb.zip")).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val file = File(root, entry.name.replace('\\', '/'))
                if (entry.isDirectory) file.mkdirs() else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return root
    }

    fun qemuBase(gunyahEnabled: Boolean, gzvmEnabled: Boolean): String = when {
        !gunyahEnabled && !gzvmEnabled -> "/data/local/tmp/qemu-gzvm"
        gzvmEnabled -> "/data/local/tmp/qemu-gzvm"
        else -> "/data/local/tmp/qemu-gunyah"
    }

    fun wrapCommand(qemuDir: String, command: String): String {
        return "${startScript(qemuDir)}; ${environmentScript()}; $command"
    }

    fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

    private fun startScript(qemuDir: String): String {
        val x11Dir = "/data/data/$appPackage/cache/x11"
        return listOf(
            """[ "$(id -u)" = 0 ] || exit 1""",
            "X11_DIR=${quote(x11Dir)}",
            "QEMU_DIR=${quote(qemuDir)}",
            "APK=\$(pm path $appPackage | sed 's/^package://' | tr '\\n' ':' | sed 's/:\$//')",
            "[ -n \"\$APK\" ] || exit 1",
            """pkill -9 -f 'com.termux.x11.CmdEntryPoint' 2>/dev/null || true""",
            """pkill -9 -f '^x11$' 2>/dev/null || true""",
            "rm -rf \"\$X11_DIR/tmp/.X11-unix\" \"\$X11_DIR/tmp/.X1-lock\" \"\$X11_DIR/tmp/.tX1-lock\"",
            "mkdir -p \"\$X11_DIR/tmp/.X11-unix\" \"\$X11_DIR/home\"",
            ": > \"\$X11_DIR/home/.Xauthority\"",
            "chmod -R 777 \"\$X11_DIR\" 2>/dev/null || true",
            "(unset LD_LIBRARY_PATH LD_PRELOAD TERMUX_X11_DEBUG; CLASSPATH=\"\$APK\" TERMUX_X11_TMPDIR=\"\$X11_DIR/tmp\" TMPDIR=\"\$X11_DIR/tmp\" XDG_RUNTIME_DIR=\"\$X11_DIR/tmp\" HOME=\"\$X11_DIR/home\" XKB_CONFIG_ROOT=\"\$X11_DIR/xkb\" TERMUX_X11_OVERRIDE_PACKAGE=$appPackage /system/bin/app_process / --nice-name=x11 com.termux.x11.CmdEntryPoint :1 -nolock -ac >\"\$X11_DIR/x11.log\" 2>&1) &",
            "am start -n $appPackage/com.termux.x11.MainActivity >/dev/null 2>&1 || true",
            "for i in \$(seq 1 200); do [ -S \"\$X11_DIR/tmp/.X11-unix/X1\" ] && break; sleep 0.05; done",
            "[ -S \"\$X11_DIR/tmp/.X11-unix/X1\" ] || { cat \"\$X11_DIR/x11.log\" 2>/dev/null; exit 1; }",
            "for p in X11:6 X11-xcb:1 Xext:6 Xcursor:1 Xi:6 Xfixes:3 Xrandr:2 Xss:1 Xinerama:1 Xrender:1 Xau:6 Xdmcp:6 xcb:1; do n=\${p%:*}; v=\${p#*:}; [ -e \"\$QEMU_DIR/lib/lib\$n.so\" ] && ln -sf \"lib\$n.so\" \"\$QEMU_DIR/lib/lib\$n.so.\$v\"; done"
        ).joinToString("; ")
    }

    private fun environmentScript(): String {
        return "export DISPLAY=:1 XAUTHORITY=\"\$X11_DIR/home/.Xauthority\" HOME=\"\$X11_DIR/home\" TMPDIR=\"\$X11_DIR/tmp\" XDG_RUNTIME_DIR=\"\$X11_DIR/tmp\" XKB_CONFIG_ROOT=\"\$X11_DIR/xkb\" SDL_VIDEODRIVER=x11 SDL_AUDIODRIVER=aaudio SDL_VIDEO_X11_XRANDR=0 SDL_VIDEO_X11_XINERAMA=0 SDL_VIDEO_X11_XVIDMODE=0 SDL_VIDEO_X11_XCURSOR=0 LANG=C LC_ALL=C"
    }

    private fun quote(value: String) = shellQuote(value)
}

fun Context.openX11() {
    startActivity(Intent().setClassName(X11.appPackage, "com.termux.x11.MainActivity"))
}
