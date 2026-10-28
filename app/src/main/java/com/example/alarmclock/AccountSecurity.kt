package com.example.alarmclock

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

/**
 * Bảo mật tài khoản: danh sách thiết bị đăng nhập.
 * Dữ liệu ở users/{uid}/data/devices gồm:
 *  - "list": các thiết bị đang đăng nhập
 *  - "revoked": id các thiết bị bị đăng xuất từ xa (máy đó tự đăng xuất khi thấy id của mình)
 * Đọc/ghi qua REST (có thời gian chờ, báo lỗi rõ ràng).
 */
object AccountSecurity {
    private const val PREF = "account_security"
    private const val POLL_MS = 12_000L

    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun modelName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, true)) model else "$maker $model"
    }

    // ------------------------------------------------------------------ giao diện
    fun attach(activity: Activity, root: android.view.View) {
        val host = findHost(root) ?: return
        if (host.findViewWithTag<android.view.View>("security_block") != null) return
        val d = activity.resources.displayMetrics.density
        val box = LinearLayout(activity).apply {
            tag = "security_block"
            orientation = LinearLayout.VERTICAL
            setPadding(0, (18 * d).toInt(), 0, 0)
        }
        box.addView(title(activity, Lang.t(activity, "Bảo mật tài khoản", "Account security")))
        box.addView(body(activity, Lang.t(activity,
            "Xem máy nào đang vào tài khoản. Nhấn giữ một máy để đăng xuất máy đó.",
            "See which phone is signed in. Long-press a phone to sign it out.")))

        val devBox = LinearLayout(activity).apply {
            tag = "dev_list"
            orientation = LinearLayout.VERTICAL
        }
        box.addView(devBox)

        val btnSignOut = MaterialButton(activity).apply {
            text = Lang.t(activity, "Đăng xuất thiết bị này", "Sign out this device")
            setOnClickListener { signOutThisDevice(activity) }
        }
        box.addView(btnSignOut, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10 * d).toInt()
        })

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
        registerAndWatch(activity, devBox)
    }

    private fun title(activity: Activity, text: String) = TextView(activity).apply {
        this.text = text
        textSize = 18f
        setTextColor(0xFF1A1A1A.toInt())
        setPadding(0, 0, 0, (4 * activity.resources.displayMetrics.density).toInt())
    }

    private fun body(activity: Activity, text: String) = TextView(activity).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF666666.toInt())
        setPadding(0, 0, 0, (8 * activity.resources.displayMetrics.density).toInt())
    }

    private fun findHost(root: android.view.View): LinearLayout? {
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

    private fun showText(activity: Activity, box: LinearLayout, text: String) {
        box.removeAllViews()
        val d = activity.resources.displayMetrics.density
        box.addView(TextView(activity).apply {
            this.text = text
            textSize = 14f
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        })
    }

    /** Vẽ danh sách thiết bị: mỗi máy một thẻ, nhấn giữ máy khác để đăng xuất từ xa. */
    private fun renderDevices(activity: Activity, box: LinearLayout, devices: List<Map<String, Any?>>) {
        box.removeAllViews()
        if (devices.isEmpty()) {
            showText(activity, box, Lang.t(activity, "Chưa có thiết bị.", "No devices yet."))
            return
        }
        val d = activity.resources.displayMetrics.density
        val me = deviceId(activity)
        for (dev in devices) {
            val id = dev["id"]?.toString().orEmpty()
            val model = dev["model"]?.toString().orEmpty()
            val place = dev["place"]?.toString().orEmpty()
            val seen = (dev["seen"] as? Number)?.toLong() ?: 0L
            val isMe = id == me
            val whenText = if (seen > 0) android.text.format.DateFormat.format("dd/MM HH:mm", seen).toString() else ""
            val lines = StringBuilder()
            lines.append(model)
            if (isMe) lines.append(Lang.t(activity, " · máy này", " · this phone"))
            if (place.isNotBlank()) lines.append("\n").append(place)
            if (whenText.isNotBlank()) lines.append("\n").append(whenText)
            if (!isMe) lines.append("\n").append(Lang.t(activity, "Nhấn giữ để đăng xuất máy này", "Long-press to sign this phone out"))
            val card = TextView(activity).apply {
                text = lines.toString()
                textSize = 14f
                setTextColor(0xFF1A1A1A.toInt())
                setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 16 * d
                    setColor(if (isMe) 0xFFEEF2FF.toInt() else 0xFFFFFFFF.toInt())
                    setStroke((1 * d).toInt(), 0xFFD6DCEB.toInt())
                }
                setOnLongClickListener {
                    if (isMe) {
                        Toast.makeText(activity, Lang.t(activity, "Dùng nút \"Đăng xuất thiết bị này\" bên dưới", "Use the \"Sign out this device\" button below"), Toast.LENGTH_SHORT).show()
                    } else {
                        confirmRemote(activity, id, model)
                    }
                    true
                }
            }
            box.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * d).toInt()
            })
        }
    }

    private fun confirmRemote(activity: Activity, id: String, model: String) {
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(Lang.t(activity, "Đăng xuất $model?", "Sign out $model?"))
            .setMessage(Lang.t(activity,
                "Máy này sẽ bị đăng xuất ngay khi mở app hoặc trong vài giây nếu đang mở. Máy bạn đang dùng không bị ảnh hưởng.",
                "That phone will be signed out as soon as it opens the app, or within seconds if it is open. This phone is not affected."))
            .setPositiveButton(Lang.t(activity, "Đăng xuất ngay", "Sign out now")) { _, _ -> revokeDevice(activity, id, model) }
            .setNegativeButton(Lang.t(activity, "Hủy", "Cancel"), null)
            .show()
    }

    // ------------------------------------------------------------------ đồng bộ
    private fun signedInNow(): Boolean = try {
        FirebaseAuth.getInstance().currentUser != null
    } catch (_: Exception) { false }

    private fun isNotFound(e: Exception?): Boolean {
        val m = e?.message ?: return false
        return m.contains("404") || m.contains("NOT_FOUND", true) || m.contains("not found", true)
    }

    private fun asDevices(v: Any?): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        (v as? List<*>)?.forEach { item ->
            (item as? Map<*, *>)?.let { m -> out.add(m.entries.associate { it.key.toString() to it.value }) }
        }
        return out
    }

    private fun asIds(v: Any?): MutableSet<String> =
        (v as? List<*>)?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()

    /**
     * Ghi máy này vào danh sách, đồng thời kiểm tra xem máy này có bị đăng xuất từ xa không.
     * clearRevoked = true khi vừa đăng nhập lại: xóa dấu "bị đăng xuất" của máy này.
     * then(devices, lỗi): devices = null khi có lỗi hoặc máy này vừa bị đăng xuất.
     */
    private fun sync(context: Context, acc: String, clearRevoked: Boolean,
                     then: (List<Map<String, Any?>>?, String?) -> Unit) {
        val path = "users/$acc/data/devices"
        FirestoreRest.get(path) { data, err ->
            if (data == null && err != null && !isNotFound(err)) {
                then(null, err.message)
                return@get
            }
            val devices = asDevices(data?.get("list"))
            val revoked = asIds(data?.get("revoked"))
            val me = deviceId(context)
            if (clearRevoked) revoked.remove(me)

            if (revoked.contains(me)) {
                // Máy này đã bị đăng xuất từ máy khác: gỡ dấu rồi đăng xuất tại đây
                revoked.remove(me)
                FirestoreRest.set(path, mapOf("revoked" to revoked.toList())) { _ -> }
                handleRevoked(context)
                then(null, null)
                return@get
            }

            val next = devices.toMutableList()
            val idx = next.indexOfFirst { it["id"]?.toString() == me }
            val mine = deviceMap(context)
            if (idx >= 0) next[idx] = mine else next.add(mine)
            val cleaned = next.filter { it["id"]?.toString() !in revoked }
            FirestoreRest.set(path, mapOf(
                "list" to cleaned,
                "revoked" to revoked.toList(),
                "updated" to System.currentTimeMillis()
            )) { e -> then(if (e == null) cleaned else null, e?.message) }
        }
    }

    private fun handleRevoked(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Thiết bị này đã được đăng xuất từ máy khác", Toast.LENGTH_LONG).show()
        }
        localSignOut(context)
    }

    /**
     * Đăng xuất đầy đủ như nút đăng xuất của app: Google, Firebase, và xóa tên / email khôi phục / ảnh
     * đã lưu trong app. Thiếu bước xóa thông tin lưu trong app thì app vẫn coi là đang đăng nhập.
     */
    private fun localSignOut(context: Context) {
        try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                context,
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).signOut()
        } catch (_: Exception) {}
        try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        AppSettings.setGoogleDisplayName(context, "")
        AppSettings.setGooglePhotoUrl(context, "")
        AppSettings.setRecoveryEmail(context, "")
    }

    /** Ghi/cập nhật máy này (gọi khi mở app). */
    fun registerThisDevice(context: Context) {
        CloudSyncHelper.ensureSignedIn(context) { writeThisDevice(context, clearRevoked = false) }
    }

    /** Gọi ngay sau khi đăng nhập Google/Firebase thành công: máy này được phép vào lại. */
    fun signedIn(context: Context) = writeThisDevice(context, clearRevoked = true)

    fun writeThisDevice(context: Context, clearRevoked: Boolean = false) {
        try {
            if (!signedInNow()) return
            val acc = CloudSyncHelper.accountId(context) ?: return
            sync(context, acc, clearRevoked) { _, _ -> }
        } catch (_: Exception) {}
    }

    private fun registerAndWatch(activity: Activity, box: LinearLayout) {
        showText(activity, box, Lang.t(activity, "Đang kết nối…", "Connecting…"))
        CloudSyncHelper.ensureSignedIn(activity) { startWatch(activity, box) }
    }

    /** Tải lại danh sách mỗi vài giây khi màn đang mở. */
    private fun startWatch(activity: Activity, box: LinearLayout) {
        val tick = object : Runnable {
            override fun run() {
                if (!box.isAttachedToWindow) return
                if (!signedInNow()) {
                    showText(activity, box, Lang.t(activity, "Chưa đăng nhập — không xem được thiết bị.", "Not signed in — cannot see devices."))
                } else {
                    val acc = CloudSyncHelper.accountId(activity)
                    if (acc.isNullOrBlank()) {
                        showText(activity, box, Lang.t(activity, "Chưa đăng nhập — không xem được thiết bị.", "Not signed in — cannot see devices."))
                    } else {
                        sync(activity, acc, clearRevoked = false) { devices, err ->
                            activity.runOnUiThread {
                                if (!box.isAttachedToWindow) return@runOnUiThread
                                when {
                                    devices != null -> renderDevices(activity, box, devices)
                                    err != null -> showText(activity, box, Lang.t(activity, "Không tải được danh sách thiết bị.", "Could not load devices."))
                                    else -> showText(activity, box, Lang.t(activity, "Thiết bị này đã đăng xuất.", "This phone has been signed out."))
                                }
                            }
                        }
                    }
                }
                box.postDelayed(this, POLL_MS)
            }
        }
        box.post(tick)
    }

    /** Gửi lệnh đăng xuất từ xa cho một máy khác. Máy đó tự đăng xuất khi nhận được. */
    private fun revokeDevice(activity: Activity, id: String, model: String) {
        val acc = CloudSyncHelper.accountId(activity) ?: return
        val path = "users/$acc/data/devices"
        FirestoreRest.get(path) { data, err ->
            if (data == null && err != null && !isNotFound(err)) {
                activity.runOnUiThread {
                    Toast.makeText(activity, Lang.t(activity, "Không gửi được lệnh: ${err.message}", "Could not send: ${err.message}"), Toast.LENGTH_LONG).show()
                }
                return@get
            }
            val devices = asDevices(data?.get("list")).filter { it["id"]?.toString() != id }
            val revoked = asIds(data?.get("revoked"))
            revoked.add(id)
            FirestoreRest.set(path, mapOf(
                "list" to devices,
                "revoked" to revoked.toList(),
                "updated" to System.currentTimeMillis()
            )) { e ->
                activity.runOnUiThread {
                    val msg = if (e == null)
                        Lang.t(activity, "Đã đăng xuất $model. Máy đó sẽ bị đăng xuất khi mở app.", "Signed out $model. That phone will sign out when it opens the app.")
                    else Lang.t(activity, "Lỗi: ${e.message}", "Error: ${e.message}")
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun signOutThisDevice(activity: Activity) {
        val acc = CloudSyncHelper.accountId(activity)
        val me = deviceId(activity)
        if (acc.isNullOrBlank() || !signedInNow()) {
            localSignOut(activity)
            activity.recreate()
            return
        }
        val path = "users/$acc/data/devices"
        // Gỡ máy này khỏi danh sách trước, rồi mới đăng xuất (cần token để ghi)
        FirestoreRest.get(path) { data, _ ->
            val devices = asDevices(data?.get("list")).filter { it["id"]?.toString() != me }
            val revoked = asIds(data?.get("revoked"))
            FirestoreRest.set(path, mapOf("list" to devices, "revoked" to revoked.toList())) { _ ->
                activity.runOnUiThread {
                    localSignOut(activity)
                    Toast.makeText(activity, Lang.t(activity, "Đã đăng xuất thiết bị này", "Signed out this device"), Toast.LENGTH_SHORT).show()
                    activity.recreate()
                }
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

    private fun place(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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

    // ------------------------------------------------------------------ xác minh 2 bước
    private fun isTwoStepOn(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("two", false)

    private fun setTwoStep(context: Context, on: Boolean, code: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("two", on)
            .putString("code", code)
            .apply()
    }

    private fun askCode(activity: Activity, creating: Boolean) {
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
