package com.example.alarmclock

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Sao lưu theo tài khoản Google: users/{uid}/backup
 * Gồm danh sách báo thức + email.
 */
object CloudSyncHelper {
    private const val TAG = "CloudSync"

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed", e)
        }
    }

    private fun uid(context: Context): String? {
        val fromAuth = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (!fromAuth.isNullOrBlank()) return fromAuth
        val email = AppSettings.getRecoveryEmail(context).ifBlank { null } ?: return null
        return email.replace(".", "_").replace("@", "_at_")
    }

    private fun doc(context: Context) =
        FirebaseFirestore.getInstance().collection("users").document(uid(context) ?: "anon")
            .collection("data").document("backup")

    fun syncOnLogin(context: Context) {
        init(context)
        if (uid(context) == null) {
            Toast.makeText(context, "Chưa có tài khoản Google để sao lưu", Toast.LENGTH_SHORT).show()
            return
        }
        pullThenMerge(context)
    }

    fun pushAlarms(context: Context, alarms: List<Alarm> = AlarmRepository(context).getAlarms(), onDone: (Boolean) -> Unit = {}) {
        try {
            init(context)
            if (uid(context) == null) {
                onDone(false)
                return
            }
            val payload = hashMapOf(
                "email" to AppSettings.getRecoveryEmail(context),
                "updatedAt" to System.currentTimeMillis(),
                "alarms" to alarms.map { a ->
                    hashMapOf(
                        "id" to a.id,
                        "hour" to a.hour,
                        "minute" to a.minute,
                        "label" to a.label,
                        "isEnabled" to a.isEnabled,
                        "repeatMode" to a.repeatMode,
                        "snoozeMinutes" to a.snoozeMinutes,
                        "challengeType" to a.challengeType,
                        "shakeTargetCount" to a.shakeTargetCount,
                        "skipHolidays" to a.skipHolidays,
                        "isStrictAntiSnooze" to a.isStrictAntiSnooze,
                        "voiceNote" to (a.voiceNote ?: ""),
                        "useCrescendo" to a.useCrescendo,
                        "ringtoneUri" to (a.ringtoneUri ?: "")
                    )
                }
            )
            doc(context).set(payload, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(context, "Đã sao lưu ${alarms.size} báo thức lên Google", Toast.LENGTH_SHORT).show()
                    onDone(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "push failed", e)
                    Toast.makeText(context, "Lỗi sao lưu: ${e.message}", Toast.LENGTH_LONG).show()
                    onDone(false)
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Firebase chưa sẵn sàng: ${e.message}", Toast.LENGTH_LONG).show()
            onDone(false)
        }
    }

    fun pullAlarms(context: Context, onResult: (List<Alarm>) -> Unit) {
        try {
            init(context)
            if (uid(context) == null) {
                onResult(emptyList()); return
            }
            doc(context).get()
                .addOnSuccessListener { snap ->
                    val raw = snap.get("alarms") as? List<*>
                    val list = raw?.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        try {
                            Alarm(
                                id = (m["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                                hour = (m["hour"] as? Number)?.toInt() ?: 0,
                                minute = (m["minute"] as? Number)?.toInt() ?: 0,
                                isEnabled = m["isEnabled"] as? Boolean ?: true,
                                label = m["label"] as? String ?: "Báo thức",
                                repeatMode = (m["repeatMode"] as? Number)?.toInt() ?: 1,
                                snoozeMinutes = (m["snoozeMinutes"] as? Number)?.toInt() ?: 5,
                                ringtoneUri = (m["ringtoneUri"] as? String)?.takeIf { it.isNotBlank() },
                                challengeType = (m["challengeType"] as? Number)?.toInt() ?: 0,
                                shakeTargetCount = (m["shakeTargetCount"] as? Number)?.toInt() ?: 10,
                                skipHolidays = m["skipHolidays"] as? Boolean ?: false,
                                isStrictAntiSnooze = m["isStrictAntiSnooze"] as? Boolean ?: false,
                                voiceNote = (m["voiceNote"] as? String)?.takeIf { it.isNotBlank() },
                                useCrescendo = m["useCrescendo"] as? Boolean ?: true
                            )
                        } catch (_: Exception) { null }
                    } ?: emptyList()
                    onResult(list)
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Tải cloud lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
                    onResult(emptyList())
                }
        } catch (_: Exception) {
            onResult(emptyList())
        }
    }

    private fun pullThenMerge(context: Context) {
        pullAlarms(context) { cloud ->
            val repo = AlarmRepository(context)
            val local = repo.getAlarms()
            when {
                cloud.isNotEmpty() -> {
                    repo.saveAlarms(cloud)
                    AlarmScheduler.rescheduleAll(context)
                    Toast.makeText(context, "Đã khôi phục ${cloud.size} báo từ Google", Toast.LENGTH_LONG).show()
                }
                local.isNotEmpty() -> pushAlarms(context, local)
                else -> Toast.makeText(context, "Google đã liên kết — chưa có báo để sao lưu", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
