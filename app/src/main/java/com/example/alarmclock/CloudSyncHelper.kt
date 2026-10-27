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

    fun accountId(context: Context): String? = uid(context)

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
                else try { AccountSecurity.registerThisDevice(context) } catch (_: Exception) {}
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
                val col = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FirestoreRest.DB_ID).collection("users")
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
                    else try { AccountSecurity.registerThisDevice(context) } catch (_: Exception) {}
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
    private fun fsSet(context: Context, payload: Map<String, Any>, cb: (Exception?) -> Unit) =
        fsSetDoc(context, "backup", payload, cb)

    private fun fsSetDoc(context: Context, name: String, payload: Map<String, Any>, cb: (Exception?) -> Unit) {
        val d = docNamed(context, name)
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
    private fun fsGet(context: Context, onOk: (Snap) -> Unit, onFail: (Exception) -> Unit) =
        fsGetDoc(context, "backup", onOk, onFail)

    private fun fsGetDoc(context: Context, name: String, onOk: (Snap) -> Unit, onFail: (Exception) -> Unit) {
        val d = docNamed(context, name)
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

    private fun doc(context: Context) = docNamed(context, "backup")

    private fun docNamed(context: Context, name: String) =
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FirestoreRest.DB_ID).collection("users").document(uid(context) ?: "anon")
            .collection("data").document(name)

    fun syncOnLogin(context: Context) {
        init(context)
        if (uid(context) == null) {
            Toast.makeText(context, UiTranslate.tr("Chưa có tài khoản Google để sao lưu"), Toast.LENGTH_SHORT).show()
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
                    else try { AccountSecurity.registerThisDevice(context) } catch (_: Exception) {}
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
        // Đồng bộ báo thức giữa thiết bị đã tắt — mỗi máy giữ báo thức riêng
        onDone(true); return
        @Suppress("UNREACHABLE_CODE")
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

    fun pullAlarms(context: Context, onError: (() -> Unit)? = null, onResult: (List<Alarm>) -> Unit) {
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
                    if (onError != null) onError() else onResult(emptyList())
                }) }
        } catch (_: Exception) {
            onResult(emptyList())
        }
    }

    private val chatPushHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var chatPushTask: Runnable? = null

    /** Gộp nhiều lần gọi liên tiếp thành một lần đẩy. */
    fun pushChatBackup(context: Context, onDone: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        chatPushTask?.let { chatPushHandler.removeCallbacks(it) }
        val task = Runnable { doPushChatBackup(app, onDone) }
        chatPushTask = task
        chatPushHandler.postDelayed(task, 1200L)
    }

    private fun doPushChatBackup(context: Context, onDone: (Boolean) -> Unit) {
        try {
            init(context)
            if (uid(context) == null) {
                onDone(false); return
            }
            ensureAuth(context) {
                // Luôn đọc bản trên cloud và GỘP trước khi ghi để không bao giờ ghi đè chat cũ.
                fsGet(context, onOk = { snap ->
                    try {
                        ChatCloudStore.mergeSessions(
                            context, snap.getString("sessions"), snap.getString("deletedSessions")
                        )
                    } catch (_: Exception) {}
                    pushChatNow(context, onDone)
                }, onFail = { e ->
                    Log.e(TAG, "push chat: không đọc được cloud, bỏ qua để khỏi ghi đè", e)
                    onDone(false)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "push chat exception", e)
            onDone(false)
        }
    }

    private fun pushChatNow(context: Context, onDone: (Boolean) -> Unit) {
        val payload = hashMapOf<String, Any>(
            "email" to AppSettings.getRecoveryEmail(context),
            "updatedAt" to System.currentTimeMillis(),
            "chatHistory" to ChatCloudStore.historyJson(context),
            "sessions" to ChatCloudStore.sessionsForCloud(context),
            "deletedSessions" to ChatCloudStore.deletedJson(context)
        )
        val key = ChatCloudStore.geminiKey(context)
        if (key.isNotBlank()) payload["geminiKey"] = key
        fsSet(context, payload) { e ->
            if (e == null) {
                onDone(true)
            } else {
                Log.e(TAG, "push chat failed", e)
                onDone(false)
            }
        }
        // Ảnh để ở tài liệu riêng: ảnh quá nặng không còn làm hỏng việc lưu chat.
        pushMedia(context)
    }

    private fun estimate(m: Map<String, Any>): Int {
        var n = 0
        for (v in m.values) {
            if (v is String) {
                n += v.length
            } else if (v is Map<*, *>) {
                for (x in v.values) n += x.toString().length
            } else {
                n += 16
            }
        }
        return n
    }

    private fun pushMedia(context: Context) {
        try {
            val chatImg = hashMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
            try {
                val imgs = collectChatImages(context)
                if (imgs.isNotEmpty()) chatImg["chatImages"] = imgs
            } catch (_: Exception) {}
            if (chatImg.size > 1) {
                fsSetDoc(context, "chatimg", chatImg) { e ->
                    if (e != null) Log.e(TAG, "push chat images failed", e)
                }
            }
            val extras = hashMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
            try {
                val pp = collectProfilePhoto(context)
                if (pp != null) extras["profilePhoto"] = pp
            } catch (_: Exception) {}
            try {
                val g = collectGalleryImages(context)
                if (g.isNotEmpty()) extras["galleryImages"] = g
            } catch (_: Exception) {}
            // Firestore giới hạn khoảng 1 MiB mỗi tài liệu
            if (estimate(extras) > 900_000) extras.remove("galleryImages")
            if (estimate(extras) > 900_000) extras.remove("profilePhoto")
            if (extras.size > 1) {
                fsSetDoc(context, "extras", extras) { e ->
                    if (e != null) Log.e(TAG, "push extras failed", e)
                }
            }
        } catch (_: Exception) {}
    }

    /** Kéo ảnh chat / ảnh hồ sơ / bộ sưu tập từ các tài liệu phụ, xong thì gọi done. */
    private fun pullMedia(context: Context, done: () -> Unit) {
        fsGetDoc(context, "chatimg", onOk = { m ->
            try { restoreChatImages(context, m.get("chatImages") as? Map<*, *>) } catch (_: Exception) {}
            fsGetDoc(context, "extras", onOk = { x ->
                try { restoreProfilePhoto(context, x.getString("profilePhoto")) } catch (_: Exception) {}
                try { restoreGalleryImages(context, x.get("galleryImages") as? Map<*, *>) } catch (_: Exception) {}
                done()
            }, onFail = { done() })
        }, onFail = { done() })
    }

    /**
     * Kéo bản sao lưu chat. Danh sách chat được GỘP với bản trên máy (không ghi đè).
     * onResult(key, hist): hist chỉ khác null khi vừa khôi phục lịch sử cho một máy trống.
     */
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
                    val fresh = ChatCloudStore.isFresh(context)
                    ChatCloudStore.mergeSessions(context, sessions, snap.getString("deletedSessions"))
                    if (fresh) {
                        // Bản sao lưu cũ chưa có danh sách chat
                        if (sessions.isNullOrBlank() && !hist.isNullOrBlank() && hist != "[]") {
                            ChatCloudStore.saveHistory(context, hist)
                        }
                        ChatCloudStore.restoreCurrentIfEmpty(context)
                    }
                    // Bản sao lưu cũ để ảnh ngay trong tài liệu chính
                    try { restoreChatImages(context, snap.get("chatImages") as? Map<*, *>) } catch (_: Exception) {}
                    try { restoreProfilePhoto(context, snap.getString("profilePhoto")) } catch (_: Exception) {}
                    try { restoreGalleryImages(context, snap.get("galleryImages") as? Map<*, *>) } catch (_: Exception) {}
                    pullMedia(context) {
                        val restored = if (fresh) ChatCloudStore.historyJson(context) else null
                        onResult(ChatCloudStore.geminiKey(context).ifBlank { key }, restored)
                    }
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
        val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
        val me = AppSettings.getRecoveryEmail(context).trim().lowercase()
        val owner = prefs.getString("owner", "").orEmpty()
        // Dữ liệu trên máy đang thuộc tài khoản khác -> không trộn sang tài khoản mới
        val switched = owner.isNotBlank() && me.isNotBlank() && owner != me
        if (switched) AlarmHistory.clearLocalOnly(context)

        pullChatBackup(context) { _, _ -> }
        pullHistory(context)
        // Không kéo báo thức từ cloud — mỗi thiết bị giữ riêng
        if (true) return
        @Suppress("UNREACHABLE_CODE")
        pullAlarms(context, onError = {
            Toast.makeText(context, UiTranslate.tr("Không tải được cloud — giữ nguyên báo thức trên máy"), Toast.LENGTH_LONG).show()
        }) { cloud ->
            val repo = AlarmRepository(context)
            val local = repo.getAlarms()
            if (me.isNotBlank()) prefs.edit().putString("owner", me).apply()
            when {
                cloud.isNotEmpty() && local.isEmpty() -> {
                    // Chỉ khôi phục nếu máy chưa có báo thức nào
                    repo.saveAlarms(cloud)
                    AlarmScheduler.rescheduleAll(context)
                    Toast.makeText(context, "Đã khôi phục ${cloud.size} báo từ Google", Toast.LENGTH_LONG).show()
                }
                cloud.isNotEmpty() && local.isNotEmpty() -> {
                    // Máy đã có báo thức — đẩy lên cloud, không ghi đè
                    pushAlarms(context, local)
                }
                switched -> {
                    local.forEach { AlarmScheduler.cancel(context, it.id) }
                    repo.saveAlarms(emptyList())
                    Toast.makeText(context, UiTranslate.tr("Tài khoản mới chưa có báo thức nào"), Toast.LENGTH_SHORT).show()
                }
                local.isNotEmpty() -> pushAlarms(context, local)
                else -> { /* không hiện toast khi chưa có báo */ }
            }
        }
    }

    /** Thu thập ảnh chat đang dùng (tên file -> base64 bản thu nhỏ), giữ tổng dưới ~650KB. */
    private fun collectChatImages(context: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val dir = java.io.File(context.filesDir, "chat_images")
        if (!dir.exists()) return out
        val names = sortedSetOf<String>(reverseOrder())
        try {
            val arr = org.json.JSONArray(ChatCloudStore.historyJson(context))
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i)?.optString("img").orEmpty()
                if (n.isNotBlank()) names.add(n)
            }
        } catch (_: Exception) {}
        try {
            val sess = ChatCloudStore.sessions(context)
            for (i in 0 until sess.length()) {
                val msgs = sess.optJSONObject(i)?.optJSONArray("messages") ?: continue
                for (j in 0 until msgs.length()) {
                    val n = msgs.optJSONObject(j)?.optString("img").orEmpty()
                    if (n.isNotBlank()) names.add(n)
                }
            }
        } catch (_: Exception) {}
        var total = 0
        for (name in names) {
            if (out.size >= 10) break
            val f = java.io.File(dir, name)
            if (!f.exists()) continue
            val b64 = thumbBase64(f) ?: continue
            if (total + b64.length > 650_000) continue
            total += b64.length
            out[name] = b64
        }
        return out
    }

    /** Ảnh đưa lên cloud: cạnh dài ≤ 720px, JPEG chất lượng 70. */
    private fun thumbBase64(f: java.io.File): String? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 720) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, bos)
            android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Throwable) { null }
    }

    private fun collectProfilePhoto(context: Context): String? {
        val path = AppSettings.getProfilePhotoPath(context)
        if (path.isBlank()) return null
        val f = java.io.File(path)
        if (!f.exists() || f.length() > 300_000) return null
        return try {
            android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private fun restoreChatImages(context: Context, map: Map<*, *>?) {
        if (map.isNullOrEmpty()) return
        val dir = java.io.File(context.filesDir, "chat_images").also { it.mkdirs() }
        for ((k, v) in map) {
            val name = k?.toString() ?: continue
            val b64 = v?.toString() ?: continue
            if (name.isBlank() || b64.isBlank()) continue
            val f = java.io.File(dir, name)
            if (f.exists()) continue
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                f.writeBytes(bytes)
            } catch (_: Exception) {}
        }
    }

    private fun restoreProfilePhoto(context: Context, b64: String?) {
        if (b64.isNullOrBlank()) return
        val path = AppSettings.getProfilePhotoPath(context)
        if (path.isNotBlank() && java.io.File(path).exists()) return
        try {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val f = java.io.File(context.filesDir, "profile_photo.jpg")
            f.writeBytes(bytes)
            AppSettings.setProfilePhotoPath(context, f.absolutePath)
        } catch (_: Exception) {}
    }

    private fun collectGalleryImages(context: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val dir = java.io.File(context.filesDir, "intruder_photos")
        if (!dir.exists()) return out
        val files = dir.listFiles()?.filter { it.extension.lowercase() in listOf("jpg","jpeg","png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        for (f in files) {
            if (out.size >= 8) break
            if (f.length() > 350_000) continue
            try {
                val b64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
                if (b64.length < 500_000) out[f.name] = b64
            } catch (_: Exception) {}
        }
        return out
    }

    private fun restoreGalleryImages(context: Context, map: Map<*, *>?) {
        if (map.isNullOrEmpty()) return
        val dir = java.io.File(context.filesDir, "intruder_photos").also { it.mkdirs() }
        for ((k, v) in map) {
            val name = k?.toString() ?: continue
            val b64 = v?.toString() ?: continue
            if (name.isBlank() || b64.isBlank()) continue
            val f = java.io.File(dir, name)
            if (f.exists()) continue
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                f.writeBytes(bytes)
            } catch (_: Exception) {}
        }
    }
}
