package com.example.alarmclock

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Đọc/ghi Firestore bằng HTTPS thường (REST) với Firebase ID token.
 * Dùng khi kết nối gRPC mặc định của SDK bị treo trên một số mạng.
 * Callback luôn chạy trên luồng chính.
 */
object FirestoreRest {
    private const val TAG = "FirestoreRest"
    private val main = Handler(Looper.getMainLooper())

    /** Tên database Firestore của dự án (database tên "default", không phải "(default)"). */
    const val DB_ID = "default"

    private fun base(): String {
        val pid = FirebaseApp.getInstance().options.projectId
        return "https://firestore.googleapis.com/v1/projects/$pid/databases/$DB_ID/documents/"
    }

    private fun token(cb: (String?, Exception?) -> Unit) {
        val u = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (u == null) {
            cb(null, IllegalStateException("Chưa đăng nhập Firebase"))
            return
        }
        u.getIdToken(false)
            .addOnSuccessListener { cb(it.token, null) }
            .addOnFailureListener { cb(null, it) }
    }

    /** Ghi (merge) các trường vào document `path`, ví dụ users/UID/data/backup. */
    fun set(path: String, fields: Map<String, Any>, cb: (Exception?) -> Unit) {
        val fin = AtomicBoolean(false)
        var timeout: Runnable? = null
        fun done(e: Exception?) {
            if (fin.compareAndSet(false, true)) {
                timeout?.let { main.removeCallbacks(it) }
                cb(e)
            }
        }
        timeout = Runnable { done(TimeoutException("REST quá 15 giây")) }
        main.postDelayed(timeout!!, 15000)
        token { tk, err ->
            if (tk == null) {
                done(err)
            } else {
                Thread {
                    var error: Exception? = null
                    try {
                        val mask = fields.keys.joinToString("&") {
                            "updateMask.fieldPaths=" + URLEncoder.encode(it, "UTF-8")
                        }
                        val c = URL(base() + path + "?" + mask).openConnection() as HttpURLConnection
                        c.requestMethod = "POST"
                        c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                        c.setRequestProperty("Authorization", "Bearer $tk")
                        c.setRequestProperty("Content-Type", "application/json")
                        c.connectTimeout = 10000
                        c.readTimeout = 10000
                        c.doOutput = true
                        val f = JSONObject()
                        fields.forEach { (k, v) -> f.put(k, toValue(v)) }
                        val body = JSONObject().put("fields", f).toString()
                        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        val code = c.responseCode
                        if (code !in 200..299) {
                            val msg = (c.errorStream ?: c.inputStream)?.bufferedReader()?.readText().orEmpty()
                            error = Exception("HTTP $code: " + msg.take(300))
                        }
                        c.disconnect()
                    } catch (x: Exception) {
                        error = x
                    }
                    main.post { done(error) }
                }.start()
            }
        }
    }

    /** Đọc document `path`. Không tồn tại (404) -> map rỗng. */
    fun get(path: String, cb: (Map<String, Any?>?, Exception?) -> Unit) {
        val fin = AtomicBoolean(false)
        var timeout: Runnable? = null
        fun done(d: Map<String, Any?>?, e: Exception?) {
            if (fin.compareAndSet(false, true)) {
                timeout?.let { main.removeCallbacks(it) }
                cb(d, e)
            }
        }
        timeout = Runnable { done(null, TimeoutException("REST quá 15 giây")) }
        main.postDelayed(timeout!!, 15000)
        token { tk, err ->
            if (tk == null) {
                done(null, err)
            } else {
                Thread {
                    var data: Map<String, Any?>? = null
                    var error: Exception? = null
                    try {
                        val c = URL(base() + path).openConnection() as HttpURLConnection
                        c.setRequestProperty("Authorization", "Bearer $tk")
                        c.connectTimeout = 10000
                        c.readTimeout = 10000
                        val code = c.responseCode
                        when {
                            code == 404 -> data = emptyMap()
                            code in 200..299 -> {
                                val text = c.inputStream.bufferedReader().readText()
                                val fields = JSONObject(text).optJSONObject("fields")
                                val m = LinkedHashMap<String, Any?>()
                                if (fields != null) {
                                    val it = fields.keys()
                                    while (it.hasNext()) {
                                        val k = it.next()
                                        m[k] = fromValue(fields.getJSONObject(k))
                                    }
                                }
                                data = m
                            }
                            else -> {
                                val msg = (c.errorStream ?: c.inputStream)?.bufferedReader()?.readText().orEmpty()
                                error = Exception("HTTP $code: " + msg.take(300))
                            }
                        }
                        c.disconnect()
                    } catch (x: Exception) {
                        error = x
                    }
                    Log.d(TAG, "get $path -> ${data?.size} / ${error?.message}")
                    main.post { done(data, error) }
                }.start()
            }
        }
    }

    private fun toValue(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long, is Short, is Byte -> JSONObject().put("integerValue", v.toString())
        is Float, is Double -> JSONObject().put("doubleValue", (v as Number).toDouble())
        is Map<*, *> -> {
            val f = JSONObject()
            v.forEach { (k, x) -> f.put(k.toString(), toValue(x)) }
            JSONObject().put("mapValue", JSONObject().put("fields", f))
        }
        is List<*> -> {
            val arr = JSONArray()
            v.forEach { arr.put(toValue(it)) }
            JSONObject().put("arrayValue", JSONObject().put("values", arr))
        }
        else -> JSONObject().put("stringValue", v.toString())
    }

    private fun fromValue(o: JSONObject): Any? = when {
        o.has("stringValue") -> o.getString("stringValue")
        o.has("integerValue") -> o.getString("integerValue").toLongOrNull() ?: 0L
        o.has("doubleValue") -> o.getDouble("doubleValue")
        o.has("booleanValue") -> o.getBoolean("booleanValue")
        o.has("timestampValue") -> o.getString("timestampValue")
        o.has("mapValue") -> {
            val f = o.getJSONObject("mapValue").optJSONObject("fields")
            val m = LinkedHashMap<String, Any?>()
            if (f != null) {
                val it = f.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    m[k] = fromValue(f.getJSONObject(k))
                }
            }
            m
        }
        o.has("arrayValue") -> {
            val a = o.getJSONObject("arrayValue").optJSONArray("values")
            val l = ArrayList<Any?>()
            if (a != null) for (i in 0 until a.length()) l.add(fromValue(a.getJSONObject(i)))
            l
        }
        else -> null
    }
}
