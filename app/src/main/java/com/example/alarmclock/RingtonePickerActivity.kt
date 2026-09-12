package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RingtonePickerActivity : AppCompatActivity() {

    private var selectedUri: String = "app:ringtone_huawei"
    private lateinit var adapter: ToneAdapter

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = queryDisplayName(uri) ?: "Nhạc của bạn"
        CustomRingtones.add(this, uri, name)
        selectedUri = uri.toString()
        RingtonePreview.play(this, uri.toString(), name)
        rebuild()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ringtone_picker)

        selectedUri = intent.getStringExtra(EXTRA_CURRENT) ?: AppRingtones.DEFAULT_ALARM

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarPicker)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnAddSound).setOnClickListener {
            try {
                openDoc.launch(arrayOf("audio/*", "application/ogg"))
            } catch (_: Exception) {
                try {
                    val i = Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
                    startActivity(Intent.createChooser(i, "Chọn nhạc"))
                } catch (_: Exception) {
                    Toast.makeText(this, "Không mở được trình chọn tệp", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val rv = findViewById<RecyclerView>(R.id.rvTones)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ToneAdapter { item ->
            selectedUri = item.uri
            RingtonePreview.play(this, item.uri, item.name)
            adapter.selected = item.uri
            adapter.notifyDataSetChanged()
        }
        rv.adapter = adapter
        rebuild()

        findViewById<MaterialButton>(R.id.btnPickOk).setOnClickListener {
            RingtonePreview.stop()
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(EXTRA_URI, selectedUri)
                putExtra(EXTRA_LABEL, labelOf(selectedUri))
            })
            finish()
        }
    }

    private fun rebuild() {
        val rows = mutableListOf<ToneRow>()
        rows += ToneRow.Header("Âm thanh của bạn")
        val custom = CustomRingtones.list(this)
        if (custom.isEmpty()) {
            rows += ToneRow.Hint("Chưa có nhạc tự thêm")
        } else {
            custom.forEach { rows += ToneRow.Tone(it.uri, it.name, true) }
        }
        rows += ToneRow.Header("Chuông trong ứng dụng")
        AppRingtones.alarms.forEach {
            rows += ToneRow.Tone(AppRingtones.uriOf(it.id), it.label, false)
        }
        rows += ToneRow.Header("Âm thanh trên thiết bị")
        rows += ToneRow.Tone("silent:", "Im lặng", false)
        try {
            val rm = RingtoneManager(this)
            rm.setType(RingtoneManager.TYPE_ALARM)
            val c = rm.cursor
            var n = 0
            while (c.moveToNext() && n < 40) {
                val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "Chuông hệ thống"
                val uri = rm.getRingtoneUri(c.position)?.toString() ?: continue
                rows += ToneRow.Tone(uri, title, false)
                n++
            }
        } catch (_: Exception) {}
        adapter.submit(rows, selectedUri)
    }


    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun labelOf(uri: String): String {
        if (uri == "silent:") return "Im lặng"
        CustomRingtones.list(this).find { it.uri == uri }?.let { return it.name }
        if (uri.startsWith("app:")) return AppRingtones.labelOf(uri)
        return "Chuông tùy chọn"
    }

    override fun onDestroy() {
        RingtonePreview.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CURRENT = "current_uri"
        const val EXTRA_URI = "picked_uri"
        const val EXTRA_LABEL = "picked_label"
    }
}

sealed class ToneRow {
    data class Header(val title: String) : ToneRow()
    data class Hint(val text: String) : ToneRow()
    data class Tone(val uri: String, val name: String, val user: Boolean) : ToneRow()
}

class ToneAdapter(private val onPick: (ToneRow.Tone) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ToneRow>()
    var selected: String = ""

    fun submit(list: List<ToneRow>, sel: String) {
        items.clear()
        items.addAll(list)
        selected = sel
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ToneRow.Header -> 0
        is ToneRow.Hint -> 1
        is ToneRow.Tone -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_tone_header, parent, false)) {}
            1 -> object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_tone_hint, parent, false)) {}
            else -> ToneVH(inf.inflate(R.layout.item_tone, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is ToneRow.Header -> holder.itemView.findViewById<TextView>(R.id.tvHeader).text = row.title
            is ToneRow.Hint -> holder.itemView.findViewById<TextView>(R.id.tvHint).text = row.text
            is ToneRow.Tone -> (holder as ToneVH).bind(row)
        }
    }

    inner class ToneVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: ToneRow.Tone) {
            itemView.findViewById<TextView>(R.id.tvToneName).text = row.name
            val mark = itemView.findViewById<TextView>(R.id.tvSelected)
            mark.visibility = if (row.uri == selected) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onPick(row) }
            itemView.findViewById<View>(R.id.btnToneMore).setOnClickListener {
                if (!row.user) {
                    onPick(row)
                    return@setOnClickListener
                }
                MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(row.name)
                    .setItems(arrayOf("Chọn", "Xoá")) { _, which ->
                        if (which == 1) {
                            CustomRingtones.remove(itemView.context, row.uri)
                            (itemView.context as? RingtonePickerActivity)?.recreate()
                        } else onPick(row)
                    }.show()
            }
        }
    }
}
