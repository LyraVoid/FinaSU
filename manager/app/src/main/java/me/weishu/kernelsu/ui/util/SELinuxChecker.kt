package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.Shell

/**
 * Returns the raw SELinux status string ("Enforcing", "Permissive", "Disabled", or "Unknown").
 * Safe to call from any thread (IO recommended).
 */
fun getSELinuxStatusRaw(): String {
    val shell = runCatching { Shell.Builder.create().build("/system/bin/sh") }.getOrNull()
        ?: return "Unknown"

    val stdoutList = ArrayList<String>()
    val stderrList = ArrayList<String>()
    val result = shell.use {
        it.newJob().add("getenforce").to(stdoutList, stderrList).exec()
    }
    val stdout = stdoutList.joinToString("\n").trim()
    val stderr = stderrList.joinToString("\n").trim()

    if (result.isSuccess) {
        return when (stdout) {
            "Enforcing", "Permissive", "Disabled" -> stdout
            else -> "Unknown"
        }
    }

    return if (stderr.endsWith("Permission denied")) {
        "Enforcing"
    } else {
        "Unknown"
    }
}

/**
 * Switches SELinux between enforcing and permissive via root `setenforce`.
 * Returns true when the command succeeded. Safe to call from any thread (IO recommended).
 */
fun setSELinuxEnforcing(enforcing: Boolean): Boolean {
    // setenforce needs root: `Shell.Builder.create().build("sh")` is a non-root fallback shell, so it
    // can read getenforce but cannot switch the mode. Use the cached root shell (ksud/su) instead.
    return try {
        val stdoutList = ArrayList<String>()
        val stderrList = ArrayList<String>()
        val result = getRootShell().newJob()
            .add("setenforce ${if (enforcing) 1 else 0}")
            .to(stdoutList, stderrList)
            .exec()
        result.isSuccess
    } catch (t: Throwable) {
        false
    }
}
