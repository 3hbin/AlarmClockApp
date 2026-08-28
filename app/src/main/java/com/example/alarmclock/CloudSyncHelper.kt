package com.example.alarmclock

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Đồng bộ báo thức lên Firestore (collection "alarms").
 * Cần bật Firestore trong Firebase Console (test mode hoặc rules phù hợp).
 */
object CloudSyncHelper {
    private const val TAG = "CloudSync"
    private const val COLLECTION = "alarms"

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed", e)
        }
    }

    fun pushAlarms(context: Context, alarms: List<Alarm>, onDone: (Boolean) -> Unit = {}) {
        try {
            init(context)
            val db = FirebaseFirestore.getInstance()
            val batch = db.batch()
            val col = db.collection(COLLECTION)

            // Xóa cũ rồi ghi mới (đơn giản cho demo)
            col.get().addOnSuccessListener { snap ->
                snap.documents.forEach { batch.delete(it.reference) }
                alarms.forEach { alarm ->
                    val ref = col.document(alarm.id.toString())
                    val data = hashMapOf(
                        "id" to alarm.id,
                        "hour" to alarm.hour,
                        "minute" to alarm.minute,
                        "label" to alarm.label,
                        "isEnabled" to alarm.isEnabled,
                        "repeatMode" to alarm.repeatMode,
                        "snoozeMinutes" to alarm.snoozeMinutes,
                        "challengeType" to alarm.challengeType,
                        "skipHolidays" to alarm.skipHolidays,
                        "isStrictAntiSnooze" to alarm.isStrictAntiSnooze,
                        "voiceNote" to (alarm.voiceNote ?: ""),
                        "useCrescendo" to alarm.useCrescendo,
                        "ringtoneUri" to (alarm.ringtoneUri ?: "")
                    )
                    batch.set(ref, data)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Đã đồng bộ ${alarms.size} báo thức lên Cloud", Toast.LENGTH_SHORT).show()
                        onDone(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "push failed", e)
                        Toast.makeText(context, "Lỗi sync: ${e.message}", Toast.LENGTH_LONG).show()
                        onDone(false)
                    }
            }.addOnFailureListener { e ->
                Log.e(TAG, "read failed", e)
                Toast.makeText(context, "Lỗi Firestore: ${e.message}. Hãy bật Firestore trên Console.", Toast.LENGTH_LONG).show()
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
            FirebaseFirestore.getInstance().collection(COLLECTION).get()
                .addOnSuccessListener { snap ->
                    val list = snap.documents.mapNotNull { doc ->
                        try {
                            Alarm(
                                id = (doc.getLong("id") ?: return@mapNotNull null).toInt(),
                                hour = (doc.getLong("hour") ?: 0).toInt(),
                                minute = (doc.getLong("minute") ?: 0).toInt(),
                                isEnabled = doc.getBoolean("isEnabled") ?: true,
                                label = doc.getString("label") ?: "Báo thức",
                                repeatMode = (doc.getLong("repeatMode") ?: 1).toInt(),
                                snoozeMinutes = (doc.getLong("snoozeMinutes") ?: 5).toInt(),
                                ringtoneUri = doc.getString("ringtoneUri")?.takeIf { it.isNotBlank() },
                                challengeType = (doc.getLong("challengeType") ?: 0).toInt(),
                                skipHolidays = doc.getBoolean("skipHolidays") ?: false,
                                isStrictAntiSnooze = doc.getBoolean("isStrictAntiSnooze") ?: false,
                                voiceNote = doc.getString("voiceNote")?.takeIf { it.isNotBlank() },
                                useCrescendo = doc.getBoolean("useCrescendo") ?: true
                            )
                        } catch (_: Exception) { null }
                    }
                    onResult(list)
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Pull lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
                    onResult(emptyList())
                }
        } catch (e: Exception) {
            onResult(emptyList())
        }
    }
}
