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
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object AccountSecurity {
    private const val PREF = "account_security"
    private const val CHANNEL = "account_devices"

    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun modelName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, true)) model else "$maker $model"
    }

    fun attach(activity: android.app.Activity, root: android.view.View) {
        val host = root.findViewById<LinearLayout>(R.id.settingsSecurityHost) ?: findHost(root) ?: return
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
        box.addView(MaterialButton(activity).apply {
            text = Lang.t(activity, "Chuyển tài khoản khác", "Switch account")
            setOnClickListener { switchAccount(activity) }
        })
        box.addView(MaterialButton(activity).apply {
            text = Lang.t(activity, "Đăng xuất thiết bị này", "Sign out this device")
            setOnClickListener {
                removeDevice(activity, deviceId(activity))
                GoogleSignInHelper.signOut(activity)
                Toast.makeText(activity, Lang.t(activity, "Đã đăng xuất thiết bị này", "Signed out this device"), Toast.LENGTH_SHORT).show()
                refreshList(activity, list)
            }
        })
        val sw = SwitchMaterial(activity).apply {
            text = Lang.t(activity, "Xác minh 2 bước", "2-step verification")
            isChecked = isTwoStepOn(activity)
            setOnCheckedChangeListener { _, on ->
                if (on) askCode(activity, true) else setTwoStep(activity, false, "")
            }
        }
        box.addView(sw)
        box.addView(body(activity, Lang.t(activity, "Bật xong, vào lại Cài đặt phải nhập mã 4 số.", "After it is on, opening Settings asks for a 4-digit code.")))
        host.addView(box)
        registerAndWatch(activity, list)
        if (isTwoStepOn(activity)) askCode(activity, false)
    }

    private fun findHost(root: android.view.View): LinearLayout? {
        if (root is LinearLayout && root.childCount > 8) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findHost(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun title(c: Context, text: String) = TextView(c).apply {
        this.text = text
        textSize = 16f
        paint.isFakeBoldText = true
    }

    private fun body(c: Context, text: String) = TextView(c).apply {
        this.text = text
        textSize = 13f
        setPadding(0, 4, 0, 8)
    }

    private fun switchAccount(activity: android.app.Activity) {
        GoogleSignInHelper.signOut(activity)
        try {
            activity.startActivity(GoogleSignInHelper.accountPickerIntent())
        } catch (_: Exception) {
            Toast.makeText(activity, Lang.t(activity, "Hãy đăng nhập lại bằng tài khoản khác", "Sign in again with another account"), Toast.LENGTH_LONG).show()
        }
    }

    private fun registerAndWatch(activity: android.app.Activity, list: TextView) {
        val id = deviceId(activity)
        val now = System.currentTimeMillis()
        val mine = JSONObject()
            .put("id", id)
            .put("model", modelName())
            .put("place", place(activity))
            .put("seen", now)
        val local = load(activity)
        val known = local.map { it.optString("id") }.toSet()
        upsert(local, mine)
        save(activity, local)
        refreshList(activity, list)
        push(activity, local)
        pull(activity) { remote ->
            remote.forEach { item ->
                if (item.optString("id") !in known && item.optString("id") != id) {
                    notifyNew(activity, item)
                }
                upsert(local, item)
            }
            upsert(local, mine)
            save(activity, local)
            activity.runOnUiThread { refreshList(activity, list) }
        }
    }

    private fun refreshList(activity: android.app.Activity, list: TextView) {
        val rows = load(activity)
        if (rows.isEmpty()) {
            list.text = Lang.t(activity, "Chưa có thiết bị.", "No devices yet.")
            return
        }
        val me = deviceId(activity)
        list.text = rows.joinToString("\n\n") { o ->
            val here = if (o.optString("id") == me) Lang.t(activity, " · máy này", " · this phone") else ""
            val whenText = android.text.format.DateFormat.format("dd/MM HH:mm", o.optLong("seen"))
            "${o.optString("model")}$here\n${o.optString("place")}\n$whenText"
        }
    }

    fun removeDevice(context: Context, id: String) {
        val left = load(context).filter { it.optString("id") != id }
        save(context, left)
        push(context, left)
    }

    private fun notifyNew(context: Context, item: JSONObject) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Account devices", NotificationManager.IMPORTANCE_HIGH))
        }
        val text = Lang.t(context, "Thiết bị vào tài khoản: ${item.optString("model")} · ${item.optString("place")}", "A device joined your account: ${item.optString("model")} · ${item.optString("place")}")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(Lang.t(context, "Có thiết bị vào tài khoản của bạn", "A device entered your account"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(item.optString("id").hashCode(), n)
    }

    private fun place(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Lang.t(context, "Chưa có vị trí", "Location unavailable")
        }
        return try {
            val lm = context.getSystemService(LocationManager::class.java)
            val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc == null) Lang.t(context, "Chưa có vị trí", "Location unavailable")
            else {
                val ads = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ads?.firstOrNull()?.getAddressLine(0) ?: "${loc.latitude}, ${loc.longitude}"
            }
        } catch (_: Exception) {
            Lang.t(context, "Chưa có vị trí", "Location unavailable")
        }
    }

    private fun load(context: Context): MutableList<JSONObject> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("devices", "[]").orEmpty()
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        return buildList {
            for (i in 0 until arr.length()) add(arr.getJSONObject(i))
        }.toMutableList()
    }

    private fun save(context: Context, rows: List<JSONObject>) {
        val arr = JSONArray()
        rows.forEach { arr.put(it) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("devices", arr.toString()).apply()
    }

    private fun upsert(rows: MutableList<JSONObject>, item: JSONObject) {
        val id = item.optString("id")
        val i = rows.indexOfFirst { it.optString("id") == id }
        if (i >= 0) rows[i] = item else rows.add(item)
    }

    private fun push(context: Context, rows: List<JSONObject>) {
        val id = CloudSyncHelper.accountId(context) ?: return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            val data = hashMapOf("list" to rows.map { it.toString() }, "updated" to System.currentTimeMillis())
            FirebaseFirestore.getInstance().collection("users").document(id)
                .collection("data").document("devices")
                .set(data, SetOptions.merge())
        } catch (_: Exception) {}
    }

    private fun pull(context: Context, done: (List<JSONObject>) -> Unit) {
        val id = CloudSyncHelper.accountId(context) ?: return done(emptyList())
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            FirebaseFirestore.getInstance().collection("users").document(id)
                .collection("data").document("devices")
                .get()
                .addOnSuccessListener { snap ->
                    val list = snap.get("list") as? List<*> ?: return@addOnSuccessListener done(emptyList())
                    done(list.mapNotNull {
                        try { JSONObject(it.toString()) } catch (_: Exception) { null }
                    })
                }
                .addOnFailureListener { done(emptyList()) }
        } catch (_: Exception) {
            done(emptyList())
        }
    }

    private fun isTwoStepOn(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("two", false)

    private fun setTwoStep(context: Context, on: Boolean, code: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("two", on)
            .putString("code", code)
            .apply()
    }

    private fun askCode(activity: android.app.Activity, creating: Boolean) {
        val input = android.widget.EditText(activity).apply {
            hint = "4 số"
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
