package com.ali.hafiflet

import android.os.Bundle
import kotlin.system.exitProcess

/**
 * Shizuku tarafından shell (adb) yetkisiyle ayrı bir süreçte çalıştırılır.
 * Burada Context yoktur; yalnızca komut çalıştırır.
 */
class ShellService : IShellService.Stub() {

    override fun exec(command: String): Bundle {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return Bundle().apply {
            putInt("code", code)
            putString("out", out)
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
