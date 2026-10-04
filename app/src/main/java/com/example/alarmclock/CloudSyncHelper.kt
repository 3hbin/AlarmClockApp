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

    /** Cài lại app: đăng nhập Google im lặng rồi kéo báo thức + chat cũ. */
    fun restoreSilently(context: Context) {
        init(context)
        try {
            val existing = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (existing?.email.isNullOrBlank().not()) {
                saveAccount(context, existing!!)
                pullThenMerge(context)
                return
            }
        } catch (_: Exception) {}
        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).requestEmail().requestProfile().requestIdToken(
                "297353017052-lkqrj6s8a1ube2c8quhvk9ebkhodedbq.apps.googleusercontent.com"
            ).build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            client.silentSignIn()
                .addOnSuccessListener { account ->
                    if (account.email.isNullOrBlank()) return@addOnSuccessListener
                    saveAccount(context, account)
                    pullThenMerge(context)
                }
        } catch (_: Exception) {}
    }

    private fun saveAccount(context: Context, account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val email = account.email ?: return
        AppSettings.setRecoveryEmail(context, email)
        AppSettings.setGoogleDisplayName(context, account.displayName?.takeIf { it.isNotBlank() } ?: email)
        AppSettings.setGooglePhotoUrl(context, account.photoUrl?.toString().orEmpty())
        try {
            val token = account.idToken
            if (!token.isNullOrBlank()) {
                val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null)
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(cred)
            }
        } catch (_: Exception) {}
    }

    fun pushAlarmsQuiet(context: Context) {
        try {
            init(context)
            if (uid(context) == null) return
            val alarms = AlarmRepository(context).getAlarms()
            val payload = hashMapOf(
                "alarms" to alarms.map { a ->
                    mapOf(
                        "id" to a.id,
                        "hour" to a.hour,
                        "minute" to a.minute,
                        "isEnabled" to a.isEnabled,
                        "label" to a.label,
                        "repeatMode" to a.repeatMode,
                        "snoozeMinutes" to a.snoozeMinutes,
                        "ringtoneUri" to (a.ringtoneUri ?: ""),
                        "challengeType" to a.challengeType,
                        "shakeTargetCount" to a.shakeTargetCount,
                        "skipHolidays" to a.skipHolidays,
                        "isStrictAntiSnooze" to a.isStrictAntiSnooze,
                        "voiceNote" to (a.voiceNote ?: ""),
                        "useCrescendo" to a.useCrescendo,
                        "group" to a.group,
                        "updatedAt" to System.currentTimeMillis()
                    )
                },
                "updatedAt" to System.currentTimeMillis()
            )
            doc(context).set(payload, com.google.firebase.firestore.SetOptions.merge())
        } catch (_: Exception) {}
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
            val email = AppSettings.getRecoveryEmail(context)
            // Máy Huawei hay kẹt SDK offline. Ghi HTTPS trước để document hiện trên console.
            val okRest = restWrite(context, alarms)
            doc(context).set(payload, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(context, "Đã sao lưu ${alarms.size} báo lên $email", Toast.LENGTH_SHORT).show()
                    onDone(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "push sdk failed", e)
                    Toast.makeText(
                        context,
                        if (okRest) "Đã ghi cloud ${alarms.size} báo • $email"
                        else "Lỗi sao lưu: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    onDone(okRest)
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
                    val viaRest = restReadAlarms(context)
                    if (viaRest != null) {
                        onResult(viaRest)
                    } else {
                        Toast.makeText(context, "Tải cloud lỗi: ${it.message}\nThử HTTPS cũng không vào được.", Toast.LENGTH_LONG).show()
                        onResult(emptyList())
                    }
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
            val histNow = ChatCloudStore.historyJson(context)
            if (histNow.isBlank() || histNow == "[]" || !histNow.contains("\"t\"")) {
                onDone(false); return
            }
            val payload = hashMapOf<String, Any>(
                "email" to AppSettings.getRecoveryEmail(context),
                "updatedAt" to System.currentTimeMillis(),
                "chatHistory" to histNow,
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
                .addOnFailureListener {
                    val ok = restWrite(context, AlarmRepository(context).getAlarms())
                    Toast.makeText(context, if (ok) "Đã ghi cloud cho ${uid(context)}" else "Cloud lỗi: ${it.message}", Toast.LENGTH_LONG).show()
                    onResult(null, null)
                }
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


    private const val FS_PROJECT = "alarmclockapp-8984a"
    private const val FS_KEY = "AIzaSyCB3aOGpk79YcAGR07Cr6g5aq7a3JVK-Vo"
    private const val FS_CERT = "B3F629F3003145CA241A470A1BD998115CD85239"

    private fun restUrl(context: Context): String {
        val id = uid(context) ?: "anon"
        return "https://firestore.googleapis.com/v1/projects/$FS_PROJECT/databases/(default)/documents/users/$id/data/backup?key=$FS_KEY"
    }

    private fun restWrite(context: Context, alarms: List<Alarm>): Boolean {
        return try {
            val email = AppSettings.getRecoveryEmail(context)
            val arr = org.json.JSONArray()
            alarms.forEach { a ->
                arr.put(org.json.JSONObject()
                    .put("id", a.id).put("hour", a.hour).put("minute", a.minute)
                    .put("label", a.label).put("isEnabled", a.isEnabled)
                    .put("repeatMode", a.repeatMode).put("snoozeMinutes", a.snoozeMinutes)
                    .put("challengeType", a.challengeType).put("shakeTargetCount", a.shakeTargetCount)
                    .put("skipHolidays", a.skipHolidays).put("isStrictAntiSnooze", a.isStrictAntiSnooze)
                    .put("voiceNote", a.voiceNote ?: "")
                    .put("useCrescendo", a.useCrescendo).put("group", a.group)
                    .put("useWeekendSchedule", a.useWeekendSchedule)
                    .put("weekendHour", a.weekendHour).put("weekendMinute", a.weekendMinute)
                    .put("ringtoneUri", a.ringtoneUri ?: ""))
            }
            val fields = org.json.JSONObject()
                .put("email", org.json.JSONObject().put("stringValue", email))
                .put("alarmsJson", org.json.JSONObject().put("stringValue", arr.toString()))
                .put("chatHistory", org.json.JSONObject().put("stringValue", ChatCloudStore.historyJson(context)))
                .put("geminiKey", org.json.JSONObject().put("stringValue", ChatCloudStore.geminiKey(context)))
                .put("updatedAt", org.json.JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            val body = org.json.JSONObject().put("fields", fields).toString()
            val conn = java.net.URL(restUrl(context)).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Android-Package", "com.alarmclock.dongho")
            conn.setRequestProperty("X-Android-Cert", FS_CERT)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            Log.i(TAG, "rest write $code")
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "rest write", e)
            false
        }
    }

    private fun restReadAlarms(context: Context): List<Alarm>? {
        return try {
            val conn = java.net.URL(restUrl(context)).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("X-Android-Package", "com.alarmclock.dongho")
            conn.setRequestProperty("X-Android-Cert", FS_CERT)
            val code = conn.responseCode
            if (code == 404) return emptyList()
            if (code !in 200..299) return null
            val raw = conn.inputStream.bufferedReader().readText()
            val fields = org.json.JSONObject(raw).optJSONObject("fields") ?: return emptyList()
            val hist = fields.optJSONObject("chatHistory")?.optString("stringValue")
            val key = fields.optJSONObject("geminiKey")?.optString("stringValue")
            if (!key.isNullOrBlank()) ChatCloudStore.saveKey(context, key)
            if (!hist.isNullOrBlank() && hist != "[]") ChatCloudStore.saveHistory(context, hist)
            val json = fields.optJSONObject("alarmsJson")?.optString("stringValue").orEmpty()
            if (json.isBlank()) return emptyList()
            val arr = org.json.JSONArray(json)
            val out = ArrayList<Alarm>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                out.add(Alarm(
                    id = m.optInt("id"),
                    hour = m.optInt("hour"),
                    minute = m.optInt("minute"),
                    isEnabled = m.optBoolean("isEnabled", true),
                    label = m.optString("label", "Báo thức"),
                    repeatMode = m.optInt("repeatMode", 1),
                    snoozeMinutes = m.optInt("snoozeMinutes", 5),
                    ringtoneUri = m.optString("ringtoneUri").takeIf { it.isNotBlank() },
                    challengeType = m.optInt("challengeType"),
                    shakeTargetCount = m.optInt("shakeTargetCount", 10),
                    skipHolidays = m.optBoolean("skipHolidays"),
                    isStrictAntiSnooze = m.optBoolean("isStrictAntiSnooze"),
                    voiceNote = m.optString("voiceNote").takeIf { it.isNotBlank() },
                    useCrescendo = m.optBoolean("useCrescendo", true),
                    group = m.optString("group", "Chung"),
                    useWeekendSchedule = m.optBoolean("useWeekendSchedule"),
                    weekendHour = m.optInt("weekendHour", -1),
                    weekendMinute = m.optInt("weekendMinute", -1)
                ))
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "rest read", e)
            null
        }
    }

    private fun pullThenMerge(context: Context) {
        pullChatBackup(context) { _, _ -> }
        pullAlarms(context) { cloud ->
            val repo = AlarmRepository(context)
            val local = repo.getAlarms()
            val toSave = if (cloud.isNotEmpty()) cloud else local
            if (cloud.isNotEmpty()) {
                repo.saveAlarms(cloud)
                AlarmScheduler.rescheduleAll(context)
            }
            val ok = restWrite(context, toSave)
            Toast.makeText(
                context,
                if (ok) "Đã ghi cloud ${toSave.size} báo • ${uid(context)}"
                else "Đăng nhập rồi nhưng cloud không ghi được",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
