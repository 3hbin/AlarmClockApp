package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Chọn giọng đọc: theo hệ thống · giọng có sẵn · ứng dụng giọng đọc khác · cài đặt hệ thống. */
object TtsVoicePicker {

    fun show(activity: Activity, onChanged: (String) -> Unit) {
        val enginePkg = AppSettings.getTtsEngine(activity).ifBlank { null }
        var eng: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val e = eng
            if (activity.isFinishing || activity.isDestroyed) {
                try { e?.shutdown() } catch (_: Exception) {}
                return@OnInitListener
            }
            if (e == null || status != TextToSpeech.SUCCESS) {
                try { e?.shutdown() } catch (_: Exception) {}
                if (enginePkg != null) {
                    // Ứng dụng giọng đọc đã chọn không còn -> quay về mặc định của hệ thống
                    AppSettings.setTtsEngine(activity, "")
                    TtsVoiceCatalog.apply(activity, TtsVoiceCatalog.system())
                    show(activity, onChanged)
                } else {
                    Toast.makeText(
                        activity,
                        Lang.t(
                            "Máy chưa có giọng đọc. Hãy cài/bật một ứng dụng giọng đọc trong cài đặt.",
                            "No speech engine found. Install or enable one in settings."
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    openSystemSettings(activity)
                }
                return@OnInitListener
            }
            showList(activity, e, enginePkg, onChanged)
        }
        eng = if (enginePkg != null) TextToSpeech(activity, listener, enginePkg)
        else TextToSpeech(activity, listener)
    }

    private fun showList(
        activity: Activity, e: TextToSpeech, enginePkg: String?, onChanged: (String) -> Unit
    ) {
        val lang = if (AppSettings.isEnglishUi(activity)) "en" else "vi"
        val voices = try { e.voices } catch (_: Exception) { null }
        val real = TtsVoiceCatalog.fromEngine(voices, lang)
        val engines = try { e.engines } catch (_: Exception) { emptyList<TextToSpeech.EngineInfo>() }
        val savedName = AppSettings.getTtsVoiceName(activity)
        val engineToSave = enginePkg ?: ""

        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun mark(sel: Boolean) = if (sel) "  ✓" else ""

        val sys = TtsVoiceCatalog.system()
        labels.add(sys.label + mark(savedName.isBlank()))
        actions.add { choose(activity, sys, "", onChanged) }

        real.forEach { opt ->
            labels.add(opt.label + mark(opt.voiceName == savedName))
            actions.add { choose(activity, opt, engineToSave, onChanged) }
        }
        if (engines.size > 1) {
            labels.add(Lang.t("Đổi ứng dụng giọng đọc…", "Change speech app…"))
            actions.add { showEngines(activity, engines, onChanged) }
        }
        labels.add(Lang.t("Mở cài đặt giọng nói của hệ thống…", "Open system speech settings…"))
        actions.add { openSystemSettings(activity) }

        MaterialAlertDialogBuilder(activity)
            .setTitle(Lang.t("Chọn giọng AI", "Choose AI voice"))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(Lang.t("Đóng", "Close"), null)
            .setOnDismissListener { try { e.shutdown() } catch (_: Exception) {} }
            .show()
    }

    private fun showEngines(
        activity: Activity, engines: List<TextToSpeech.EngineInfo>, onChanged: (String) -> Unit
    ) {
        val names = engines.map { (it.label ?: it.name) }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(Lang.t("Ứng dụng giọng đọc", "Speech app"))
            .setItems(names) { _, which ->
                val info = engines[which]
                AppSettings.setTtsEngine(activity, info.name)
                TtsVoiceCatalog.apply(activity, TtsVoiceCatalog.system())
                onChanged(TtsVoiceCatalog.system().label)
                show(activity, onChanged) // mở lại danh sách giọng của ứng dụng vừa chọn
            }
            .setNegativeButton(Lang.t("Đóng", "Close"), null)
            .show()
    }

    private fun choose(
        activity: Activity, opt: TtsVoiceOption, engine: String, onChanged: (String) -> Unit
    ) {
        AppSettings.setTtsEngine(activity, engine)
        TtsVoiceCatalog.apply(activity, opt)
        onChanged(opt.label)
        // Đọc thử bằng đúng giọng vừa chọn
        val preview = TtsHelper(activity).also { it.useMediaStream = true }
        preview.onDone = { preview.shutdown() }
        preview.speak(
            Lang.t(
                "Xin chào, đây là giọng đọc của trợ lý AI.",
                "Hello, this is the AI assistant voice."
            )
        )
    }

    fun openSystemSettings(activity: Activity) {
        try {
            activity.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }
}
