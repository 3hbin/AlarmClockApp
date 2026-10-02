package com.example.alarmclock

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Sao lưu theo tài khoản Google: users/{uid}/data/backup
 * Gồm danh sách báo thức, khóa API Gemini và lịch sử chat.
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
        val email = AppSettings.getRecoveryEmail(context).trim().lowercase()
        if (email.isNotBlank()) return email.replace(".", "_").replace("@", "_at_")
        return try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
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
            val payload = hashMapOf<String, Any>(
                "email" to AppSettings.getRecoveryEmail(context),
                "updatedAt" to System.currentTimeMillis(),
                "geminiKey" to ChatCloudStore.geminiKey(context),
                "chatHistory" to ChatCloudStore.historyJson(context),
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
                        "group" to a.group,
                        "useWeekendSchedule" to a.useWeekendSchedule,
                        "weekendHour" to a.weekendHour,
                        "weekendMinute" to a.weekendMinute,
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
                                useCrescendo = m["useCrescendo"] as? Boolean ?: true, group = m["group"] as? String ?: "Chung", useWeekendSchedule = m["useWeekendSchedule"] as? Boolean ?: false, weekendHour = (m["weekendHour"] as? Number)?.toInt() ?: -1, weekendMinute = (m["weekendMinute"] as? Number)?.toInt() ?: -1
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

    fun pushChatBackup(context: Context, onDone: (Boolean) -> Unit = {}) {
        try {
            init(context)
            if (uid(context) == null) {
                onDone(false); return
            }
            val payload = hashMapOf<String, Any>(
                "email" to AppSettings.getRecoveryEmail(context),
                "updatedAt" to System.currentTimeMillis(),
                "chatHistory" to ChatCloudStore.historyJson(context),
                "sessions" to ChatCloudStore.sessions(context).toString()
            )
            val key = ChatCloudStore.geminiKey(context)
            if (key.isNotBlank()) payload["geminiKey"] = key
            doc(context).set(payload, SetOptions.merge())
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "push chat failed", e)
                    onDone(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "push chat exception", e)
            onDone(false)
        }
    }

    fun pullChatBackup(context: Context, onResult: (String?, String?) -> Unit) {
        try {
            init(context)
            migrateLegacyChatPrefs(context)
            if (uid(context) == null) {
                onResult(null, null); return
            }
            doc(context).get()
                .addOnSuccessListener { snap ->
                    val key = snap.getString("geminiKey")
                    val hist = snap.getString("chatHistory")
                    val sessions = snap.getString("sessions")
                    if (!key.isNullOrBlank() && ChatCloudStore.geminiKey(context).isBlank()) {
                        ChatCloudStore.saveKey(context, key)
                    }
                    val local = ChatCloudStore.historyJson(context)
                    val localEmpty = local == "[]" || local.isBlank() || !local.contains("\"t\"")
                    if (!hist.isNullOrBlank() && hist != "[]" && (localEmpty || local.length < hist.length)) {
                        ChatCloudStore.saveHistory(context, hist)
                    }
                    if (!sessions.isNullOrBlank() && sessions != "[]") {
                        ChatCloudStore.prefs(context).edit().putString("sessions", sessions).apply()
                    }
                    onResult(
                        ChatCloudStore.geminiKey(context).ifBlank { key },
                        ChatCloudStore.historyJson(context).ifBlank { hist }
                    )
                }
                .addOnFailureListener { onResult(null, null) }
        } catch (_: Exception) {
            onResult(null, null)
        }
    }

    private fun migrateLegacyChatPrefs(context: Context) {
        try {
            val dest = ChatCloudStore.prefs(context)
            if (!dest.getString("key", "").isNullOrBlank() || dest.getString("history", "[]") != "[]") return
            val old = context.getSharedPreferences("chat_ai", Context.MODE_PRIVATE)
            val key = old.getString("key", "") ?: ""
            val hist = old.getString("history", "[]") ?: "[]"
            if (key.isNotBlank() || hist != "[]") {
                dest.edit().putString("key", key).putString("history", hist).apply()
            }
        } catch (_: Exception) {}
    }

    private fun pullThenMerge(context: Context) {
        pullChatBackup(context) { _, _ -> }
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
