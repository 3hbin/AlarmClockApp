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

    private var warned = false

    /** Ưu tiên uid Firebase Auth (khớp Firestore Rules); không có thì dùng email. */
    private fun uid(context: Context): String? {
        val authUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (!authUid.isNullOrBlank()) return authUid
        val email = AppSettings.getRecoveryEmail(context).trim().lowercase()
        if (email.isNotBlank()) return email.replace(".", "_").replace("@", "_at_")
        return null
    }

    /** Đảm bảo đã đăng nhập Firebase Auth rồi mới ghi/đọc Firestore. */
    private fun ensureAuth(context: Context, then: () -> Unit) {
        try {
            init(context)
            if (FirebaseAuth.getInstance().currentUser != null) { then(); return }
            val token = com.google.android.gms.auth.api.signin.GoogleSignIn
                .getLastSignedInAccount(context)?.idToken
            if (token.isNullOrBlank()) {
                Log.w(TAG, "No Google idToken -> Firebase Auth skipped")
                then(); return
            }
            val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null)
            FirebaseAuth.getInstance().signInWithCredential(cred).addOnCompleteListener { t ->
                if (!t.isSuccessful) Log.e(TAG, "Firebase sign-in failed", t.exception)
                then()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureAuth", e)
            then()
        }
    }

    private fun reportFailure(context: Context, e: Exception?) {
        Log.e(TAG, "Firestore write failed", e)
        if (!warned) {
            warned = true
            Toast.makeText(context, "Sao lưu cloud lỗi: ${e?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun alarmsToMaps(alarms: List<Alarm>): List<Map<String, Any>> = alarms.map { a ->
        hashMapOf<String, Any>(
            "id" to a.id, "hour" to a.hour, "minute" to a.minute, "label" to a.label,
            "isEnabled" to a.isEnabled, "repeatMode" to a.repeatMode,
            "snoozeMinutes" to a.snoozeMinutes, "challengeType" to a.challengeType,
            "shakeTargetCount" to a.shakeTargetCount, "skipHolidays" to a.skipHolidays,
            "isStrictAntiSnooze" to a.isStrictAntiSnooze, "voiceNote" to (a.voiceNote ?: ""),
            "useCrescendo" to a.useCrescendo, "group" to a.group,
            "useWeekendSchedule" to a.useWeekendSchedule, "weekendHour" to a.weekendHour,
            "weekendMinute" to a.weekendMinute, "ringtoneUri" to (a.ringtoneUri ?: ""),
            "routineOn" to a.routineOn, "routineWeather" to a.routineWeather,
            "routineCalendar" to a.routineCalendar, "routineTasks" to a.routineTasks,
            "routineTomorrow" to a.routineTomorrow, "qrToken" to a.qrToken,
            "note" to a.note, "color" to a.color
        )
    }

    /** Chẩn đoán song song: token, mạng tới Google, ghi đường dẫn uid và đường dẫn email (timeout 15s). */
    fun diagnose(context: Context, onResult: (String) -> Unit) {
        val head = StringBuilder()
        val results = java.util.LinkedHashMap<String, String>()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val labels = listOf("Token", "Mạng firestore", "Mạng securetoken", "Mạng identitytoolkit", "Ghi path UID", "Ghi path EMAIL", "Ghi REST UID")
        var done = false
        fun hint(msg: String?): String = when {
            msg == null -> ""
            msg.contains("PERMISSION_DENIED", true) -> " → Rules chặn"
            msg.contains("UNAVAILABLE", true) -> " → không chạm được server"
            msg.contains("NOT_FOUND", true) -> " → chưa tạo database"
            else -> ""
        }
        fun finish() {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            val sb = StringBuilder(head)
            labels.forEach { l -> sb.append(l).append(": ").append(results[l] ?: "⏱ KHÔNG phản hồi (15s)").append("\n") }
            onResult(sb.toString())
        }
        fun put(label: String, v: String) {
            handler.post {
                if (done) return@post
                results[label] = v
                if (results.size == labels.size) finish()
            }
        }
        init(context)
        handler.postDelayed({ finish() }, 15000)
        try {
            val acct = try {
                com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            } catch (_: Exception) { null }
            val email = AppSettings.getRecoveryEmail(context).trim().lowercase()
            head.append("v").append(try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "?" }).append("\n")
            head.append("Google: ").append(acct?.email ?: "(chưa đăng nhập)")
                .append(" · idToken: ").append(if (acct?.idToken.isNullOrBlank()) "KHÔNG" else "có").append("\n")

            val run = {
                val user = FirebaseAuth.getInstance().currentUser
                head.append("Firebase uid: ").append(user?.uid ?: "CHƯA đăng nhập").append("\n")

                // 1) Token
                if (user == null) put("Token", "bỏ qua (chưa đăng nhập Firebase)")
                else {
                    val t0 = System.currentTimeMillis()
                    user.getIdToken(false)
                        .addOnSuccessListener { put("Token", "✅ OK (" + (System.currentTimeMillis() - t0) + "ms)") }
                        .addOnFailureListener { e -> put("Token", "❌ " + e.message) }
                }

                // 2) Mạng tới các host Google
                listOf(
                    "Mạng firestore" to "https://firestore.googleapis.com/",
                    "Mạng securetoken" to "https://securetoken.googleapis.com/",
                    "Mạng identitytoolkit" to "https://identitytoolkit.googleapis.com/"
                ).forEach { (label, url) ->
                    Thread {
                        val t0 = System.currentTimeMillis()
                        val r = try {
                            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                            c.connectTimeout = 8000; c.readTimeout = 8000
                            val code = c.responseCode
                            c.disconnect()
                            "✅ HTTP $code (" + (System.currentTimeMillis() - t0) + "ms)"
                        } catch (e: Exception) { "❌ " + e.javaClass.simpleName + ": " + e.message }
                        put(label, r)
                    }.start()
                }

                // 3) Ghi hai đường dẫn để so sánh
                val col = FirebaseFirestore.getInstance().collection("users")
                fun testWrite(label: String, id: String?) {
                    if (id.isNullOrBlank()) { put(label, "bỏ qua (không có id)"); return }
                    val ref = col.document(id).collection("data").document("diag")
                    val t0 = System.currentTimeMillis()
                    ref.set(hashMapOf<String, Any>("t" to System.currentTimeMillis()))
                        .addOnSuccessListener { put(label, "✅ OK (" + (System.currentTimeMillis() - t0) + "ms)") }
                        .addOnFailureListener { e -> put(label, "❌ " + e.message + hint(e.message)) }
                }
                if (user != null) {
                    FirestoreRest.set("users/" + user.uid + "/data/diag", hashMapOf<String, Any>("t" to System.currentTimeMillis())) { e ->
                        put("Ghi REST UID", if (e == null) "✅ OK" else "❌ " + e.message)
                    }
                } else put("Ghi REST UID", "bỏ qua (chưa đăng nhập)")
                testWrite("Ghi path UID", user?.uid)
                testWrite("Ghi path EMAIL", if (email.isNotBlank()) email.replace(".", "_").replace("@", "_at_") else null)
            }

            if (FirebaseAuth.getInstance().currentUser != null || acct?.idToken.isNullOrBlank()) {
                run()
            } else {
                val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(acct!!.idToken!!, null)
                FirebaseAuth.getInstance().signInWithCredential(cred).addOnCompleteListener { t ->
                    if (!t.isSuccessful) head.append("❌ Firebase Auth: ").append(t.exception?.message).append("\n")
                    run()
                }
            }
        } catch (e: Exception) {
            head.append("❌ Lỗi: ").append(e.message).append("\n")
            finish()
        }
    }

    /** Đẩy nhật ký báo thức (tắt / báo lại) lên cloud. */
    fun pushHistoryQuiet(context: Context) {
        try {
            if (uid(context) == null) return
            val json = AlarmHistory.exportJson(context)
            ensureAuth(context) {
                try {
                    fsSet(context, hashMapOf<String, Any>("alarmHistory" to json, "historyUpdatedAt" to System.currentTimeMillis())) { e ->
                        if (e != null) reportFailure(context, e)
                    }
                } catch (e: Exception) { reportFailure(context, e) }
            }
        } catch (_: Exception) {}
    }

    private fun pullHistory(context: Context) {
        ensureAuth(context) {
            try {
                fsGet(context, onOk = { snap ->
                    val h = snap.getString("alarmHistory")
                    if (!h.isNullOrBlank()) AlarmHistory.mergeJson(context, h)
                    pushHistoryQuiet(context)
                }, onFail = { Log.e(TAG, "pull history failed", it) })
            } catch (_: Exception) {}
        }
    }

    private class Snap(val data: Map<String, Any?>) {
        fun get(k: String): Any? = data[k]
        fun getString(k: String): String? = data[k] as? String
    }

    /** Ghi: thử HTTPS (REST) trước, lỗi thì quay về SDK gRPC. */
    private fun fsSet(context: Context, payload: Map<String, Any>, cb: (Exception?) -> Unit) {
        val d = doc(context)
        FirestoreRest.set(d.path, payload) { err ->
            if (err == null) {
                cb(null)
            } else {
                Log.w(TAG, "REST set lỗi, thử gRPC: " + err.message)
                try {
                    d.set(payload, SetOptions.merge())
                        .addOnSuccessListener { cb(null) }
                        .addOnFailureListener { cb(it) }
                } catch (e: Exception) { cb(e) }
            }
        }
    }

    /** Đọc: thử HTTPS (REST) trước, lỗi thì quay về SDK gRPC. */
    private fun fsGet(context: Context, onOk: (Snap) -> Unit, onFail: (Exception) -> Unit) {
        val d = doc(context)
        FirestoreRest.get(d.path) { data, err ->
            if (data != null) {
                onOk(Snap(data))
            } else {
                Log.w(TAG, "REST get lỗi, thử gRPC: " + err?.message)
                try {
                    d.get()
                        .addOnSuccessListener { onOk(Snap(it.data ?: emptyMap())) }
                        .addOnFailureListener { onFail(it) }
                } catch (e: Exception) { onFail(e) }
            }
        }
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
                saveAccount(context, existing!!) { pullThenMerge(context) }
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
                    saveAccount(context, account) { pullThenMerge(context) }
                }
        } catch (_: Exception) {}
    }

    private fun saveAccount(
        context: Context,
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        onReady: () -> Unit = {}
    ) {
        val email = account.email ?: return
        AppSettings.setRecoveryEmail(context, email)
        AppSettings.setGoogleDisplayName(context, account.displayName?.takeIf { it.isNotBlank() } ?: email)
        AppSettings.setGooglePhotoUrl(context, account.photoUrl?.toString().orEmpty())
        try {
            val token = account.idToken
            if (!token.isNullOrBlank() && FirebaseAuth.getInstance().currentUser == null) {
                val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null)
                FirebaseAuth.getInstance().signInWithCredential(cred).addOnCompleteListener { t ->
                    if (!t.isSuccessful) Log.e(TAG, "Firebase sign-in failed", t.exception)
                    onReady()
                }
                return
            }
        } catch (e: Exception) { Log.e(TAG, "saveAccount", e) }
        onReady()
    }

    fun pushAlarmsQuiet(context: Context) {
        try {
            init(context)
            if (uid(context) == null) return
            val alarms = AlarmRepository(context).getAlarms()
            ensureAuth(context) {
                try {
                    val payload = hashMapOf<String, Any>(
                        "email" to AppSettings.getRecoveryEmail(context),
                        "updatedAt" to System.currentTimeMillis(),
                        "alarms" to alarmsToMaps(alarms)
                    )
                    fsSet(context, payload) { e -> if (e != null) reportFailure(context, e) }
                } catch (e: Exception) { reportFailure(context, e) }
            }
        } catch (_: Exception) {}
    }

    fun pushAlarms(context: Context, alarms: List<Alarm> = AlarmRepository(context).getAlarms(), onDone: (Boolean) -> Unit = {}) {
        try {
            init(context)
            if (uid(context) == null) {
                onDone(false)
                return
            }
            ensureAuth(context) {
                try {
                    val payload = hashMapOf<String, Any>(
                        "email" to AppSettings.getRecoveryEmail(context),
                        "updatedAt" to System.currentTimeMillis(),
                        "geminiKey" to ChatCloudStore.geminiKey(context),
                        "chatHistory" to ChatCloudStore.historyJson(context),
                        "alarmHistory" to AlarmHistory.exportJson(context),
                        "alarms" to alarmsToMaps(alarms)
                    )
                    fsSet(context, payload) { e ->
                        if (e == null) {
                            Toast.makeText(context, "Đã sao lưu ${alarms.size} báo thức lên Google", Toast.LENGTH_SHORT).show()
                            onDone(true)
                        } else {
                            Log.e(TAG, "push failed", e)
                            Toast.makeText(context, "Lỗi sao lưu: ${e.message}", Toast.LENGTH_LONG).show()
                            onDone(false)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Firebase chưa sẵn sàng: ${e.message}", Toast.LENGTH_LONG).show()
                    onDone(false)
                }
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
            ensureAuth(context) { fsGet(context, onOk = { snap ->
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
                                useCrescendo = m["useCrescendo"] as? Boolean ?: true, group = m["group"] as? String ?: "Chung", useWeekendSchedule = m["useWeekendSchedule"] as? Boolean ?: false, weekendHour = (m["weekendHour"] as? Number)?.toInt() ?: -1, weekendMinute = (m["weekendMinute"] as? Number)?.toInt() ?: -1,
                                routineOn = m["routineOn"] as? Boolean ?: false,
                                routineWeather = m["routineWeather"] as? Boolean ?: true,
                                routineCalendar = m["routineCalendar"] as? Boolean ?: true,
                                routineTasks = m["routineTasks"] as? Boolean ?: true,
                                routineTomorrow = m["routineTomorrow"] as? Boolean ?: true,
                                qrToken = m["qrToken"] as? String ?: "",
                                note = m["note"] as? String ?: "",
                                color = (m["color"] as? Number)?.toInt() ?: 0xFF1A73E8.toInt()
                            )
                        } catch (_: Exception) { null }
                    } ?: emptyList()
                    onResult(list)
                }, onFail = {
                    Toast.makeText(context, "Tải cloud lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
                    onResult(emptyList())
                }) }
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
            ensureAuth(context) {
                fsSet(context, payload) { e ->
                    if (e == null) {
                        onDone(true)
                    } else {
                        Log.e(TAG, "push chat failed", e)
                        onDone(false)
                    }
                }
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
            ensureAuth(context) { fsGet(context, onOk = { snap ->
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
                }, onFail = { onResult(null, null) }) }
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
        pullHistory(context)
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
