package com.example.alarmclock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Bảo mật tài khoản — danh sách thiết bị đăng nhập, realtime Firestore.
 * Mỗi tài khoản có document users/{uid}/data/devices với field "list".
 */
object AccountSecurity {
    private const val PREF = "account_security"
    private const val CHANNEL = "account_devices"
    private var listener: ListenerRegistration? = null

    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun modelName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, true)) model else "$maker $model"
    }

    fun attach(activity: android.app.Activity, root: android.view.View) {
        appCtx = activity.applicationContext
        val host = findHost(root) ?: return
        if (host.findViewWithTag<android.view.View>("security_block") != null) return
        val d = activity.resources.displayMetrics.density
        val box = LinearLayout(activity).apply {
            tag = "security_block"
            orientation = LinearLayout.VERTICAL
            setPadding(0, (18 * d).toInt(), 0, 0)
        }
        box.addView(title(activity, Lang.t(activity, "Bảo mật tài khoản", "Account security")))
        box.addView(body(activity, Lang.t(activity, "Xem máy nào đang vào tài khoản. Có máy lạ thì đăng xuất ngay.", "See which phone is in your account. Sign it out right away.")))

        val list = TextView(activity).apply {
            textSize = 14f
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            text = Lang.t(activity, "Đang kiểm tra thiết bị…", "Checking devices…")
        }
        box.addView(list)

        val btnSignOut = MaterialButton(activity).apply {
            text = Lang.t(activity, "Đăng xuất thiết bị này", "Sign out this device")
            setOnClickListener {
                signOutThisDevice(activity)
            }
        }
        box.addView(btnSignOut)

        // Xác minh 2 bước
        val twoRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        val twoLabel = TextView(activity).apply {
            text = Lang.t(activity, "Xác minh 2 bước", "2-step verification")
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val twoSwitch = SwitchMaterial(activity).apply {
            isChecked = isTwoStepOn(activity)
            setOnCheckedChangeListener { _, on ->
                if (on) askCode(activity, creating = true)
                else setTwoStep(activity, false, "")
            }
        }
        twoRow.addView(twoLabel)
        twoRow.addView(twoSwitch)
        box.addView(twoRow)
        box.addView(body(activity, Lang.t(activity, "Bật xong, vào lại Cài đặt phải nhập mã 4 số.", "When on, opening Settings requires the 4-digit code.")))

        host.addView(box)

        // Đăng ký máy này + lắng nghe realtime
        registerAndWatch(activity, list)
    }

    private fun title(activity: android.app.Activity, text: String) = TextView(activity).apply {
        this.text = text
        textSize = 18f
        setTextColor(0xFF1A1A1A.toInt())
        setPadding(0, 0, 0, (4 * activity.resources.displayMetrics.density).toInt())
    }

    private fun body(activity: android.app.Activity, text: String) = TextView(activity).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF666666.toInt())
        setPadding(0, 0, 0, (8 * activity.resources.displayMetrics.density).toInt())
    }

    private fun findHost(root: android.view.View): LinearLayout? {
        // Ưu tiên LinearLayout nằm trong ScrollView (nội dung settings)
        fun walk(v: android.view.View): LinearLayout? {
            if (v is android.widget.ScrollView && v.childCount > 0) {
                val c = v.getChildAt(0)
                if (c is LinearLayout && c.orientation == LinearLayout.VERTICAL) return c
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    val found = walk(v.getChildAt(i))
                    if (found != null) return found
                }
            }
            return null
        }
        return walk(root)
    }

    private fun signOutThisDevice(activity: android.app.Activity) {
        val id = deviceId(activity)
        removeDevice(activity, id)
        try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                activity,
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).signOut()
        } catch (_: Exception) {}
        try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        Toast.makeText(activity, Lang.t(activity, "Đã đăng xuất thiết bị này", "Signed out this device"), Toast.LENGTH_SHORT).show()
        activity.recreate()
    }

    /**
     * Ghi máy này vào danh sách thiết bị trên cloud. Gọi khi mở app và ngay sau khi đăng nhập,
     * để máy mới hiện trong danh sách mà không cần mở màn Bảo mật trước.
     */
    /** Bật để hiện dòng chẩn đoán trên màn Bảo mật. Xong lỗi thì đặt false (hoặc xóa). */
    const val DEBUG_DEVICES = true
    @Volatile private var lastWrite = "chưa ghi"
    @Volatile private var lastRead = "chưa đọc"

    fun registerThisDevice(context: Context) {
        CloudSyncHelper.ensureSignedIn(context) { writeThisDevice(context) }
    }

    /** Ghi máy này ngay (gọi sau khi đã đăng nhập Firebase). */
    fun writeThisDevice(context: Context) {
        try {
            val accountId = CloudSyncHelper.accountId(context)
            if (accountId.isNullOrBlank()) { lastWrite = "không có mã tài khoản"; return }
            writeThisDeviceRest(context, accountId) {}
        } catch (e: Exception) { lastWrite = "lỗi: ${e.message}" }
    }

    private fun registerAndWatch(activity: android.app.Activity, list: TextView) {
        list.text = Lang.t(activity, "Đang kết nối…", "Connecting…")
        CloudSyncHelper.ensureSignedIn(activity) { startWatch(activity, list) }
    }

    private var pollTask: Runnable? = null

    /** Ghi máy này rồi tải danh sách, lặp lại mỗi 8 giây khi màn đang mở. Dùng REST (có báo lỗi, không treo). */
    private fun startWatch(activity: android.app.Activity, list: TextView) {
        pollTask?.let { list.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                if (!list.isAttachedToWindow) return
                val acc = CloudSyncHelper.accountId(activity)
                if (acc.isNullOrBlank()) {
                    list.text = Lang.t(activity, "Chưa đăng nhập — không xem được thiết bị.", "Not signed in — cannot see devices.")
                } else {
                    writeThisDeviceRest(activity, acc) { loadDevicesRest(activity, list, acc) }
                }
                list.postDelayed(this, 8000)
            }
        }
        pollTask = tick
        list.post(tick)
    }

    /** Ghi (hoặc cập nhật) máy này vào danh sách bằng REST. */
    private fun writeThisDeviceRest(context: Context, acc: String, then: () -> Unit) {
        val path = "users/$acc/data/devices"
        FirestoreRest.get(path) { data, err ->
            val current = (data?.get("list") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.toMutableList()
                ?: mutableListOf()
            if (err != null && data == null && err.message?.contains("404") != true && err.message?.contains("NOT_FOUND") != true) {
                lastRead = "lỗi đọc: ${err.message}"
            }
            val me = deviceMap(context)
            val id = me["id"].toString()
            val idx = current.indexOfFirst { it["id"]?.toString() == id }
            if (idx >= 0) current[idx] = me else current.add(me)
            FirestoreRest.set(path, mapOf("list" to current, "updated" to System.currentTimeMillis())) { e ->
                lastWrite = if (e == null) "OK" else "lỗi ghi: ${e.message}"
                then()
            }
        }
    }

    private fun loadDevicesRest(activity: android.app.Activity, list: TextView, acc: String) {
        FirestoreRest.get("users/$acc/data/devices") { data, err ->
            activity.runOnUiThread {
                if (!list.isAttachedToWindow) return@runOnUiThread
                if (err != null && data == null) {
                    lastRead = "lỗi đọc: ${err.message}"
                    list.text = Lang.t(activity, "Không tải được danh sách thiết bị.", "Could not load devices.") + debugLine(err.message)
                    return@runOnUiThread
                }
                lastRead = "OK"
                val devices = (data?.get("list") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
                refreshList(activity, list, devices)
                list.text = list.text.toString() + debugLine(null)
            }
        }
    }

    private fun deviceMap(context: Context): Map<String, Any> {
        return mapOf(
            "id" to deviceId(context),
            "model" to modelName(),
            "place" to place(context),
            "seen" to System.currentTimeMillis()
        )
    }

    /** Dòng chẩn đoán (chỉ khi DEBUG_DEVICES). */
    private fun debugLine(err: String?): String {
        if (!DEBUG_DEVICES) return ""
        val acc = try { CloudSyncHelper.accountId(appCtx ?: return "") } catch (_: Exception) { null }
        return "\n\n— chẩn đoán —\n" + CloudSyncHelper.authStatus() +
            "\nMã tài khoản: " + (acc?.take(12) ?: "không có") +
            "\nGhi thiết bị: " + lastWrite +
            "\nĐọc danh sách: " + lastRead +
            (if (err != null) "\nLỗi: $err" else "")
    }

    @Volatile private var appCtx: Context? = null

    private fun refreshList(activity: android.app.Activity, list: TextView, devices: List<Map<*, *>>) {
        if (devices.isEmpty()) {
            list.text = Lang.t(activity, "Chưa có thiết bị.", "No devices yet.")
            return
        }
        val me = deviceId(activity)
        list.text = devices.joinToString("\n\n") { o ->
            val id = o["id"]?.toString().orEmpty()
            val model = o["model"]?.toString().orEmpty()
            val place = o["place"]?.toString().orEmpty()
            val seen = (o["seen"] as? Number)?.toLong() ?: 0L
            val here = if (id == me) Lang.t(activity, " · máy này", " · this phone") else ""
            val whenText = if (seen > 0) android.text.format.DateFormat.format("dd/MM HH:mm", seen) else ""
            "$model$here\n$place\n$whenText"
        }
    }

    fun removeDevice(context: Context, id: String) {
        val accountId = CloudSyncHelper.accountId(context) ?: return
        val ref = FirebaseFirestore.getInstance()
            .collection("users").document(accountId)
            .collection("data").document("devices")
        ref.get().addOnSuccessListener { snap ->
            val current = (snap.get("list") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.toMutableList() ?: return@addOnSuccessListener
            current.removeAll { it["id"]?.toString() == id }
            ref.set(mapOf("list" to current, "updated" to System.currentTimeMillis()))
        }
    }

    private fun place(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return ""
        }
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc == null) return ""
            val geo = Geocoder(context, Locale.getDefault())
            val addrs = geo.getFromLocation(loc.latitude, loc.longitude, 1)
            addrs?.firstOrNull()?.let { a ->
                listOfNotNull(a.locality, a.adminArea, a.countryName).joinToString(", ")
            }.orEmpty()
        } catch (_: Exception) { "" }
    }

    private fun isTwoStepOn(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("two", false)

    private fun setTwoStep(context: Context, on: Boolean, code: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("two", on)
            .putString("code", code)
            .apply()
    }

    private fun askCode(activity: android.app.Activity, creating: Boolean) {
        val input = android.widget.EditText(activity).apply {
            hint = UiTranslate.tr("4 số")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(Lang.t(activity, "Xác minh 2 bước", "2-step verification"))
            .setMessage(if (creating) Lang.t(activity, "Đặt mã 4 số", "Set a 4-digit code") else Lang.t(activity, "Nhập mã 4 số", "Enter the 4-digit code"))
            .setView(input)
            .setPositiveButton(Lang.t(activity, "Xác nhận", "Confirm")) { _, _ ->
                val code = input.text?.toString().orEmpty()
                val saved = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("code", "").orEmpty()
                if (creating) {
                    if (code.length < 4) {
                        setTwoStep(activity, false, "")
                        Toast.makeText(activity, Lang.t(activity, "Mã phải đủ 4 số", "Code must be 4 digits"), Toast.LENGTH_SHORT).show()
                    } else setTwoStep(activity, true, code)
                } else if (code != saved) {
                    Toast.makeText(activity, Lang.t(activity, "Sai mã", "Wrong code"), Toast.LENGTH_SHORT).show()
                    activity.finish()
                }
            }
            .setNegativeButton(Lang.t(activity, "Hủy", "Cancel")) { _, _ ->
                if (creating) setTwoStep(activity, false, "") else activity.finish()
            }
            .setCancelable(false)
            .show()
    }
}
