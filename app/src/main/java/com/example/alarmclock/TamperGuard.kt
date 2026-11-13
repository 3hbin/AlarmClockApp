package com.example.alarmclock

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.io.File
import java.security.MessageDigest

/**
 * Chống sửa APK / debug / hook phổ biến.
 * Phát hiện bất thường → thoát process ngay (văng app).
 */
object TamperGuard {

    private const val EXPECTED_PACKAGE = "com.alarmclock.dongho"

    /** Gọi sớm trong Application.onCreate */
    fun verifyOrDie(context: Context) {
        try {
            if (!checkPackageName(context)) die("pkg")
            if (!checkNotDebuggableRelease(context)) die("debug")
            if (!checkSignaturePresent(context)) die("sig")
            if (!checkNoKnownHooks()) die("hook")
            if (!checkInstallerOk(context)) die("installer")
            if (!checkApkPath(context)) die("path")
        } catch (t: Throwable) {
            // Lỗi bất ngờ khi kiểm tra cũng coi như không an toàn
            die("ex")
        }
    }

    private fun die(reason: String) {
        try {
            android.util.Log.e("TamperGuard", "integrity fail: $reason")
        } catch (_: Exception) {}
        try {
            // Xóa process — app "văng"
            Process.killProcess(Process.myPid())
        } catch (_: Exception) {}
        try {
            System.exit(0)
        } catch (_: Exception) {}
        throw SecurityException("App integrity check failed")
    }

    private fun checkPackageName(context: Context): Boolean {
        return context.packageName == EXPECTED_PACKAGE
    }

    /** Release build không được để FLAG_DEBUGGABLE (thường bị bật khi crack). */
    private fun checkNotDebuggableRelease(context: Context): Boolean {
        // Cho phép debug khi tự build debug; chỉ chặn trên bản release
        if (BuildConfig.DEBUG) return true
        val flags = context.applicationInfo.flags
        return (flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0
    }

    /** APK phải có chữ ký hợp lệ (không unsigned / stripped). */
    private fun checkSignaturePresent(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val sigs: Array<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
                val pi = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val si = pi.signingInfo ?: return false
                val arr = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                arr?.map { it.toByteArray() }?.toTypedArray() ?: return false
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pi.signatures?.map { it.toByteArray() }?.toTypedArray() ?: return false
            }
            if (sigs.isEmpty()) return false
            // Hash không rỗng
            val md = MessageDigest.getInstance("SHA-256")
            val dig = md.digest(sigs[0])
            dig.any { it != 0.toByte() }
        } catch (_: Exception) {
            false
        }
    }

    /** Phát hiện Xposed / Frida / Substrate phổ biến. */
    private fun checkNoKnownHooks(): Boolean {
        val suspiciousClasses = listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "com.saurik.substrate.MS"
        )
        for (name in suspiciousClasses) {
            try {
                Class.forName(name)
                return false // tìm thấy hook framework
            } catch (_: ClassNotFoundException) {
                // OK
            } catch (_: Throwable) {
            }
        }
        // Frida / gadget library
        val libHints = listOf("frida", "gadget", "xposed")
        try {
            val maps = File("/proc/self/maps")
            if (maps.canRead()) {
                val text = maps.readText()
                if (libHints.any { text.contains(it, ignoreCase = true) }) return false
            }
        } catch (_: Exception) {}
        return true
    }

    /**
     * Cài từ nguồn lạ không chặn hoàn toàn (APKPure OK),
     * nhưng nếu package bị đổi tên (clone) thì đã fail ở checkPackageName.
     */
    private fun checkInstallerOk(context: Context): Boolean {
        // Không bắt buộc installer — sideload hợp lệ.
        return true
    }

    /** APK path bất thường (tmp crack) — cảnh báo nhẹ, không chặn cứng path. */
    private fun checkApkPath(context: Context): Boolean {
        return try {
            val src = context.applicationInfo.sourceDir ?: return true
            // sourceDir rỗng / không tồn tại là bất thường
            File(src).exists()
        } catch (_: Exception) {
            true
        }
    }

    /** Gọi thêm từ Activity chính (lần 2) — chống bypass chỉ Application. */
    fun verifyInActivity(context: Context) {
        if (BuildConfig.DEBUG) return
        verifyOrDie(context)
    }
}
