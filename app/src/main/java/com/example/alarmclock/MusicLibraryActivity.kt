package com.example.alarmclock

import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Spotify / YouTube Music không cho app khác đọc thư viện sau khi đăng nhập.
 * Màn này tìm bài, hiện tên + ảnh + nghe thử, rồi lưu đoạn preview làm chuông.
 * Nút mở app vẫn nhảy thẳng Spotify / YouTube Music.
 */
class MusicLibraryActivity : AppCompatActivity() {

    private lateinit var source: String
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val io = Executors.newFixedThreadPool(3)
    private var preview: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: SRC_SPOTIFY
        val d = resources.displayMetrics.density
        val night = isNight()
        val bg = if (night) 0xFF12141C.toInt() else 0xFFF7F8FC.toInt()
        val ink = if (night) Color.WHITE else 0xFF1A1C28.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.addView(MaterialToolbar(this).apply {
            title = if (source == SRC_YTM) "YouTube Music" else "Spotify"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
            if (night) setTitleTextColor(Color.WHITE)
        })

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
        }
        head.addView(ImageView(this).apply {
            setImageResource(if (source == SRC_YTM) R.drawable.ic_youtube_music else R.drawable.ic_spotify)
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
            if (night) setColorFilter(Color.WHITE)
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(if (night) Color.WHITE else 0xFF3C4043.toInt())
            setPadding((12 * d).toInt(), 0, 0, 0)
            setText(if (isInstalled()) "App đã cài. Tìm bài bên dưới, nghe thử rồi chọn làm chuông."
            else "Chưa tải app. Có thể tìm bài ngay, hoặc tải app để mở đăng nhập.")
        }
        head.addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head)

        val search = EditText(this).apply {
            hint = "Tìm tên bài hoặc ca sĩ"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            setTextColor(ink)
            setHintTextColor(0xFF8A8F98.toInt())
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    loadSongs(v.text.toString())
                    true
                } else false
            }
        }
        root.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), 0) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
        }
        actions.addView(MaterialButton(this).apply {
            setText("Đăng nhập")
            setOnClickListener { openApp(search.text.toString()) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!isInstalled()) {
            actions.addView(MaterialButton(this).apply {
                setText("Tải app")
                setOnClickListener { openStore() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * d).toInt()
            })
        }
        root.addView(actions)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = NestedScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        loadSongs("nhạc trẻ")
    }

    override fun onDestroy() {
        try { preview?.release() } catch (_: Exception) {}
        io.shutdownNow()
        super.onDestroy()
    }

    private fun pkg() = if (source == SRC_YTM) PKG_YTM else PKG_SPOTIFY

    private fun isInstalled(): Boolean = try {
        packageManager.getPackageInfo(pkg(), 0)
        true
    } catch (_: Exception) { false }

    private fun openStore() {
        val id = pkg()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được Google Play", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openApp(query: String) {
        if (!isInstalled()) {
            openStore()
            return
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val uri = if (source == SRC_SPOTIFY) "spotify:search:${Uri.encode(q)}"
            else "https://music.youtube.com/search?q=${Uri.encode(q)}"
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg())
            try {
                startActivity(view)
                return
            } catch (_: Exception) {}
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg())
        if (launch == null) openStore() else startActivity(launch)
    }

    private fun loadSongs(query: String) {
        val q = query.trim().ifBlank { "nhạc trẻ" }
        status.text = "Đang tìm \"$q\"…"
        list.removeAllViews()
        io.execute {
            val songs = fetchDeezer(q)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (songs.isEmpty()) {
                    status.text = "Không thấy bài. Thử tên khác."
                } else {
                    status.text = "${songs.size} bài. Bấm dòng để chọn chuông."
                    showTracks(songs)
                }
            }
        }
    }

    private fun fetchDeezer(query: String): List<Song> {
        return try {
            val url = "https://api.deezer.com/search?limit=25&q=" + URLEncoder.encode(query, "UTF-8")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "AlarmClockApp")
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val title = o.optString("title")
                    if (title.isBlank()) continue
                    add(Song(
                        title = title,
                        artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                        cover = o.optJSONObject("album")?.optString("cover_medium").orEmpty(),
                        preview = o.optString("preview")
                    ))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun showTracks(songs: List<Song>) {
        list.removeAllViews()
        val d = resources.displayMetrics.density
        val night = isNight()
        songs.forEach { song ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
                setOnClickListener { choose(song) }
            }
            val art = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt())
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(if (source == SRC_YTM) R.drawable.ic_youtube_music else R.drawable.ic_spotify)
                if (night) setColorFilter(Color.WHITE)
            }
            if (song.cover.startsWith("http")) {
                io.execute {
                    val bmp = try {
                        val c = URL(song.cover).openConnection() as HttpURLConnection
                        c.connectTimeout = 8000
                        val b = BitmapFactory.decodeStream(c.inputStream)
                        c.disconnect()
                        b
                    } catch (_: Exception) { null }
                    if (bmp != null) runOnUiThread {
                        art.clearColorFilter()
                        art.setImageBitmap(bmp)
                    }
                }
            }
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(TextView(this).apply {
                text = song.title
                textSize = 16f
                setTextColor(if (night) Color.WHITE else 0xFF1A1C28.toInt())
            })
            names.addView(TextView(this).apply {
                text = song.artist
                textSize = 13f
                setTextColor(0xFF8A8F98.toInt())
            })
            val play = MaterialButton(this).apply {
                setText("Nghe thử")
                setOnClickListener { playPreview(song) }
            }
            row.addView(art)
            row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * d).toInt()
            })
            row.addView(play)
            list.addView(row)
        }
    }

    private fun playPreview(song: Song) {
        if (song.preview.isBlank()) {
            Toast.makeText(this, "Bài này không có đoạn nghe thử", Toast.LENGTH_SHORT).show()
            return
        }
        try { preview?.release() } catch (_: Exception) {}
        preview = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            setDataSource(song.preview)
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@MusicLibraryActivity, "Không phát được", Toast.LENGTH_SHORT).show()
                true
            }
            prepareAsync()
        }
        Toast.makeText(this, "Đang nghe: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    private fun choose(song: Song) {
        if (song.preview.isBlank()) {
            Toast.makeText(this, "Bài này không lưu được làm chuông", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "Đang lưu ${song.title}…"
        io.execute {
            val file = try {
                val dir = File(filesDir, "music_previews").apply { mkdirs() }
                val out = File(dir, song.title.hashCode().toString() + ".mp3")
                val conn = URL(song.preview).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.inputStream.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                conn.disconnect()
                out
            } catch (_: Exception) { null }
            runOnUiThread {
                if (file == null || !file.exists()) {
                    status.text = "Không lưu được. Kiểm tra mạng."
                    return@runOnUiThread
                }
                setResult(RESULT_OK, Intent().apply {
                    putExtra(RingtonePickerActivity.EXTRA_URI, Uri.fromFile(file).toString())
                    putExtra(RingtonePickerActivity.EXTRA_LABEL, "${song.title} — ${song.artist}")
                })
                finish()
            }
        }
    }

    private fun isNight(): Boolean {
        val mode = AppSettings.getDarkMode(this)
        if (mode == 1) return true
        if (mode == 2) return false
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private data class Song(val title: String, val artist: String, val cover: String, val preview: String)

    companion object {
        const val EXTRA_SOURCE = "source"
        const val SRC_SPOTIFY = "spotify"
        const val SRC_YTM = "ytm"
        const val PKG_SPOTIFY = "com.spotify.music"
        const val PKG_YTM = "com.google.android.apps.youtube.music"
    }
}
