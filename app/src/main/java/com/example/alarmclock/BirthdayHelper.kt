package com.example.alarmclock

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BirthdayHelper {

    const val ALARM_ID = 910001
    const val SCOPE = "https://www.googleapis.com/auth/user.birthday.read"
    val birthdayScope: Scope = Scope(SCOPE)

    fun formatStored(context: Context): String {
        val m = AppSettings.getBirthdayMonth(context)
        val d = AppSettings.getBirthdayDay(context)
        if (m !in 1..12 || d !in 1..31) return ""
        val y = AppSettings.getBirthdayYear(context)
        return if (y > 1900) "%02d/%02d/%d".format(d, m, y) else "%02d/%02d".format(d, m)
    }

    fun pickManual(activity: Activity, onSaved: () -> Unit = {}) {
        val cal = Calendar.getInstance()
        val sm = AppSettings.getBirthdayMonth(activity)
        val sd = AppSettings.getBirthdayDay(activity)
        val sy = AppSettings.getBirthdayYear(activity)
        if (sm in 1..12) cal.set(Calendar.MONTH, sm - 1)
        if (sd in 1..31) cal.set(Calendar.DAY_OF_MONTH, sd)
        if (sy > 1900) cal.set(Calendar.YEAR, sy)
        DatePickerDialog(
            activity,
            { _, year, month, day ->
                saveAndSchedule(activity, year, month + 1, day, "manual")
                Toast.makeText(activity, activity.getString(R.string.birthday_saved, formatStored(activity)), Toast.LENGTH_LONG).show()
                onSaved()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun syncFromSignedInAccount(activity: Activity, account: GoogleSignInAccount?, onDone: () -> Unit = {}) {
        if (account?.account == null) {
            Toast.makeText(activity, activity.getString(R.string.birthday_need_google), Toast.LENGTH_LONG).show()
            pickManual(activity, onDone)
            return
        }
        Thread {
            val main = Handler(Looper.getMainLooper())
            try {
                val token = GoogleAuthUtil.getToken(
                    activity,
                    account.account!!,
                    "oauth2:$SCOPE"
                )
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url("https://people.googleapis.com/v1/people/me?personFields=birthdays")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        main.post {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.birthday_google_fail),
                                Toast.LENGTH_LONG
                            ).show()
                            pickManual(activity, onDone)
                        }
                        return@use
                    }
                    val parsed = parseBirthdays(body)
                    main.post {
                        if (parsed == null) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.birthday_google_empty),
                                Toast.LENGTH_LONG
                            ).show()
                            pickManual(activity, onDone)
                        } else {
                            saveAndSchedule(activity, parsed.first, parsed.second, parsed.third, "google")
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.birthday_from_google, formatStored(activity)),
                                Toast.LENGTH_LONG
                            ).show()
                            onDone()
                        }
                    }
                }
            } catch (e: UserRecoverableAuthException) {
                main.post {
                    try {
                        activity.startActivity(e.intent)
                    } catch (_: Exception) {}
                    Toast.makeText(activity, activity.getString(R.string.birthday_need_permission), Toast.LENGTH_LONG).show()
                    pickManual(activity, onDone)
                }
            } catch (_: Exception) {
                main.post {
                    Toast.makeText(activity, activity.getString(R.string.birthday_google_fail), Toast.LENGTH_LONG).show()
                    pickManual(activity, onDone)
                }
            }
        }.start()
    }

    fun syncNow(activity: Activity, onDone: () -> Unit = {}) {
        val acc = try { GoogleSignIn.getLastSignedInAccount(activity) } catch (_: Exception) { null }
        syncFromSignedInAccount(activity, acc, onDone)
    }

    fun saveAndSchedule(context: Context, year: Int, month: Int, day: Int, source: String) {
        AppSettings.setBirthday(context, year, month, day, source)
        upsertBirthdayAlarm(context)
    }

    fun upsertBirthdayAlarm(context: Context) {
        val month = AppSettings.getBirthdayMonth(context)
        val day = AppSettings.getBirthdayDay(context)
        if (month !in 1..12 || day !in 1..31) return
        val repo = AlarmRepository(context)
        val list = repo.getAlarms()
        val name = AppSettings.getGoogleDisplayName(context).ifBlank { "bạn" }
        val label = context.getString(R.string.birthday_alarm_label, name)
        val existing = list.firstOrNull { it.id == ALARM_ID }
        val alarm = Alarm(
            id = ALARM_ID,
            hour = existing?.hour ?: 8,
            minute = existing?.minute ?: 0,
            isEnabled = existing?.isEnabled ?: true,
            label = label,
            repeatMode = Alarm.REPEAT_YEARLY,
            snoozeMinutes = existing?.snoozeMinutes ?: 5,
            ringtoneUri = existing?.ringtoneUri ?: repo.getGlobalRingtone() ?: "app:soft_chime",
            challengeType = existing?.challengeType ?: Alarm.CHALLENGE_NONE,
            useCrescendo = existing?.useCrescendo ?: true
        )
        val next = list.filter { it.id != ALARM_ID }.toMutableList()
        next.add(0, alarm)
        repo.saveAlarms(next)
        if (alarm.isEnabled) AlarmScheduler.schedule(context, alarm)
    }

    fun nextOccurrenceMillis(month: Int, day: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val max = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, day.coerceAtMost(max))
        }
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.YEAR, 1)
            val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(max))
        }
        return cal.timeInMillis
    }

    private fun parseBirthdays(json: String): Triple<Int, Int, Int>? {
        return try {
            val arr = JSONObject(json).optJSONArray("birthdays") ?: return null
            var best: Triple<Int, Int, Int>? = null
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val date = item.optJSONObject("date") ?: continue
                val month = date.optInt("month", 0)
                val day = date.optInt("day", 0)
                val year = date.optInt("year", 0)
                if (month in 1..12 && day in 1..31) {
                    val triple = Triple(year, month, day)
                    val primary = item.optJSONObject("metadata")?.optBoolean("primary") == true
                    if (primary) return triple
                    if (best == null) best = triple
                }
            }
            best
        } catch (_: Exception) {
            null
        }
    }
}
