package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MusicLibraryActivity : AppCompatActivity() {

    private lateinit var source: String
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var loginBtn: MaterialButton
    private lateinit var storeBtn: MaterialButton
    private lateinit var loading: WaveView
    private var tab = TAB_SONGS
    private var waitingLink = false
    private val io = Executors.newFixedThreadPool(3)
    private var preview: MediaPlayer? = null
    private var playingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: SRC_SPOTIFY
        val d = resources.displayMetrics.density
        val night = isNight()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (night) 0xFF12141C.toInt() else 0xFFF7F8FC.toInt())
        }
        root.addView(MaterialToolbar(this).apply {
            title = titleOf()
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
            setImageResource(iconOf())
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
            if (night) setColorFilter(Color.WHITE)
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(if (night) Color.WHITE else 0xFF3C4043.toInt())
            setPadding((12 * d).toInt(), 0, 0, 0)
        }
        head.addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head)

        val search = EditText(this).apply {
            hint = "Tìm tên bài hoặc ca sĩ"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { tab = TAB_SONGS; loadSongs(v.text.toString()); true } else false
            }
        }
        root.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), 0) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), 0)
        }
        loginBtn = MaterialButton(this).apply {
            setText("Đăng nhập")
            setOnClickListener { openApp(search.text.toString()) }
        }
        storeBtn = MaterialButton(this).apply {
            setText("Tải app")
            setOnClickListener { openStore() }
        }
        actions.addView(loginBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(storeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (8 * d).toInt()
        })
        actions.addView(MaterialButton(this).apply {
            setText("Tìm")
            setOnClickListener { tab = TAB_SONGS; loadSongs(search.text.toString()) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (8 * d).toInt()
        })
        root.addView(actions)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
        }
        tabs.addView(chip("Bài hát") { tab = TAB_SONGS; loadSongs(search.text.toString()) })
        tabs.addView(chip("Yêu thích") { tab = TAB_FAV; showSaved(MusicAccounts.favorites(this, source), "Chưa có bài yêu thích. Bấm trái tim để lưu.") })
        tabs.addView(chip("Gần đây") { tab = TAB_RECENT; showSaved(MusicAccounts.recent(this, source), "Chưa nghe bài nào gần đây.") })
        root.addView(tabs)

        loading = WaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams((72 * d).toInt(), (36 * d).toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL }
            visibility = View.GONE
        }
        root.addView(loading)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = NestedScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshGate(search)
    }

    override fun onResume() {
        super.onResume()
        if (waitingLink && isInstalled()) {
            waitingLink = false
            MusicAccounts.setLinked(this, source, true)
            Toast.makeText(this, "Đã liên kết ${titleOf()}", Toast.LENGTH_SHORT).show()
        }
        refreshGate(null)
    }

    override fun onDestroy() {
        try { preview?.release() } catch (_: Exception) {}
        loading.stop()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun refreshGate(search: EditText?) {
        val linked = MusicAccounts.isLinked(this, source)
        val installed = isInstalled()
        storeBtn.visibility = if (installed) View.GONE else View.VISIBLE
        loginBtn.visibility = if (linked) View.GONE else View.VISIBLE
        if (!installed) {
            status.text = "Chưa tải ${titleOf()}. Bấm Tải app để vào Google Play."
            showEmpty()
            return
        }
        if (!linked) {
            status.text = "Chưa đăng nhập. Bấm Đăng nhập để mở ${titleOf()}."
            showEmpty()
            return
        }
        if (tab == TAB_FAV) showSaved(MusicAccounts.favorites(this, source), "Chưa có bài yêu thích.")
        else if (tab == TAB_RECENT) showSaved(MusicAccounts.recent(this, source), "Chưa có bài gần đây.")
        else if (list.childCount == 0) loadSongs(search?.text?.toString().orEmpty())
    }

    private fun showEmpty() {
        loading.stop()
        loading.visibility = View.GONE
        list.removeAllViews()
    }

    private fun chip(label: String, click: () -> Unit) = MaterialButton(this).apply {
        setText(label)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = (6 * resources.displayMetrics.density).toInt()
        }
    }

    private fun loadSongs(query: String) {
        if (!isInstalled() || !MusicAccounts.isLinked(this, source)) {
            refreshGate(null)
            return
        }
        if (!online()) {
            status.text = "Không có mạng. Bật mạng rồi bấm Tìm."
            loading.stop()
            loading.visibility = View.GONE
            list.removeAllViews()
            return
        }
        val q = query.trim().ifBlank { defaultQuery() }
        status.text = "Đang tải bài…"
        list.removeAllViews()
        loading.visibility = View.VISIBLE
        loading.start()
        io.execute {
            val songs = fetch(q)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                loading.stop()
                loading.visibility = View.GONE
                if (songs.isEmpty()) status.text = "Không thấy bài. Thử tên khác."
                else {
                    status.text = "${titleOf()} · ${songs.size} bài"
                    showTracks(songs)
                }
            }
        }
    }

    private fun showSaved(songs: List<Song>, empty: String) {
        if (!MusicAccounts.isLinked(this, source)) {
            refreshGate(null)
            return
        }
        loading.stop()
        loading.visibility = View.GONE
        if (songs.isEmpty()) {
            status.text = empty
            list.removeAllViews()
        } else {
            status.text = "${songs.size} bài"
            showTracks(songs)
        }
    }

    private fun fetch(query: String): List<Song> {
        return if (source == SRC_SPOTIFY) tryDeezer(query) else tryItunes(query)
    }

    private fun tryDeezer(query: String): List<Song> = try {
        val url = "https://api.deezer.com/search?limit=25&q=" + URLEncoder.encode(query, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title")
                if (title.isBlank()) continue
                add(Song(o.optLong("id").toString(), title,
                    o.optJSONObject("artist")?.optString("name").orEmpty(),
                    o.optJSONObject("album")?.optString("cover_medium").orEmpty(),
                    o.optString("preview")))
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun tryItunes(query: String): List<Song> = try {
        val url = "https://itunes.apple.com/search?limit=25&entity=song&term=" + URLEncoder.encode(query, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("trackName")
                if (title.isBlank()) continue
                add(Song(o.optLong("trackId").toString(), title, o.optString("artistName"),
                    o.optString("artworkUrl100"), o.optString("previewUrl")))
            }
        }
    } catch (_: Exception) { emptyList() }

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
                setImageResource(iconOf())
                if (night) setColorFilter(Color.WHITE)
            }
            if (song.cover.startsWith("http")) io.execute {
                val bmp = try {
                    val c = URL(song.cover).openConnection() as HttpURLConnection
                    val b = BitmapFactory.decodeStream(c.inputStream)
                    c.disconnect(); b
                } catch (_: Exception) { null }
                if (bmp != null) runOnUiThread { art.clearColorFilter(); art.setImageBitmap(bmp) }
            }
            val wave = WaveView(this).apply {
                layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (28 * d).toInt())
                visibility = if (playingId == song.id) View.VISIBLE else View.GONE
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
            val heart = MaterialButton(this).apply {
                setText(if (MusicAccounts.isFavorite(this@MusicLibraryActivity, source, song.id)) "♥" else "♡")
                setOnClickListener {
                    val on = MusicAccounts.toggleFavorite(this@MusicLibraryActivity, source, song)
                    setText(if (on) "♥" else "♡")
                    Toast.makeText(this@MusicLibraryActivity, if (on) "Đã thêm yêu thích" else "Đã bỏ yêu thích", Toast.LENGTH_SHORT).show()
                }
            }
            val play = MaterialButton(this).apply {
                setText("Nghe thử")
                setOnClickListener {
                    MusicAccounts.addRecent(this@MusicLibraryActivity, source, song)
                    playingId = song.id
                    playPreview(song)
                    showTracks(songs)
                }
            }
            row.addView(art)
            row.addView(wave)
            row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * d).toInt()
            })
            row.addView(heart)
            row.addView(play)
            if (playingId == song.id) wave.start()
            list.addView(row)
        }
    }

    private fun playPreview(song: Song) {
        if (!online()) {
            status.text = "Không có mạng nên không nghe thử được."
            return
        }
        if (song.preview.isBlank()) {
            Toast.makeText(this, "Bài này không có đoạn nghe thử", Toast.LENGTH_SHORT).show()
            return
        }
        try { preview?.release() } catch (_: Exception) {}
        preview = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(song.preview)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { playingId = null }
            prepareAsync()
        }
        Toast.makeText(this, "Đang nghe: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    private fun choose(song: Song) {
        if (song.preview.isBlank()) {
            Toast.makeText(this, "Bài này không lưu được làm chuông", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "Đang lưu bài…"
        loading.visibility = View.VISIBLE
        loading.start()
        io.execute {
            val file = try {
                val dir = File(filesDir, "music_previews").apply { mkdirs() }
                val out = File(dir, song.id + ".mp3")
                val conn = URL(song.preview).openConnection() as HttpURLConnection
                conn.inputStream.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                conn.disconnect(); out
            } catch (_: Exception) { null }
            runOnUiThread {
                loading.stop()
                loading.visibility = View.GONE
                if (file == null) { status.text = "Không lưu được. Kiểm tra mạng."; return@runOnUiThread }
                MusicAccounts.addRecent(this, source, song)
                setResult(RESULT_OK, Intent().apply {
                    putExtra(RingtonePickerActivity.EXTRA_URI, Uri.fromFile(file).toString())
                    putExtra(RingtonePickerActivity.EXTRA_LABEL, "${song.title} — ${song.artist}")
                })
                finish()
            }
        }
    }

    private fun openStore() {
        val id = pkg()
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))) }
        catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))) }
            catch (_: Exception) { Toast.makeText(this, "Không mở được Google Play", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun openApp(query: String) {
        if (!isInstalled()) { openStore(); return }
        waitingLink = true
        val q = query.trim()
        val uri = when (source) {
            SRC_SPOTIFY -> if (q.isEmpty()) null else "spotify:search:${Uri.encode(q)}"
            SRC_YTM -> if (q.isEmpty()) null else "https://music.youtube.com/search?q=${Uri.encode(q)}"
            else -> if (q.isEmpty()) null else "https://www.tiktok.com/search?q=${Uri.encode(q)}"
        }
        if (uri != null) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg())); return } catch (_: Exception) {}
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg())
        if (launch == null) openStore() else startActivity(launch)
    }

    private fun pkg(): String = when (source) {
        SRC_YTM -> PKG_YTM
        SRC_TIKTOK -> tiktokPkg()
        else -> PKG_SPOTIFY
    }

    private fun tiktokPkg(): String {
        val ids = listOf("com.ss.android.ugc.trill", "com.zhiliaoapp.musically")
        return ids.firstOrNull { try { packageManager.getPackageInfo(it, 0); true } catch (_: Exception) { false } }
            ?: "com.ss.android.ugc.trill"
    }

    private fun isInstalled(): Boolean = try { packageManager.getPackageInfo(pkg(), 0); true } catch (_: Exception) { false }

    private fun online(): Boolean = try {
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) { true }

    private fun titleOf() = when (source) { SRC_YTM -> "YouTube Music"; SRC_TIKTOK -> "TikTok"; else -> "Spotify" }
    private fun iconOf() = when (source) { SRC_YTM -> R.drawable.ic_youtube_music; SRC_TIKTOK -> R.drawable.ic_tiktok; else -> R.drawable.ic_spotify }
    private fun defaultQuery() = when (source) { SRC_TIKTOK -> "nhac tiktok"; SRC_YTM -> "nhac tre"; else -> "vietnam pop" }

    private fun isNight(): Boolean {
        val mode = AppSettings.getDarkMode(this)
        if (mode == 1) return true
        if (mode == 2) return false
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String)

    class WaveView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B6CFF.toInt() }
        private var phase = 0f
        private var anim: ValueAnimator? = null
        fun start() {
            if (anim != null) return
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedFraction; invalidate() }
                start()
            }
        }
        fun stop() { anim?.cancel(); anim = null }
        override fun onDraw(canvas: Canvas) {
            val n = 4
            val gap = width / 12f
            val bw = (width - gap * (n + 1)) / n
            for (i in 0 until n) {
                val h = height * (0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin((phase + i * 0.2f) * Math.PI * 2)).toFloat())
                val left = gap + i * (bw + gap)
                canvas.drawRoundRect(left, height - h, left + bw, height.toFloat(), 6f, 6f, paint)
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val SRC_SPOTIFY = "spotify"
        const val SRC_YTM = "ytm"
        const val SRC_TIKTOK = "tiktok"
        const val PKG_SPOTIFY = "com.spotify.music"
        const val PKG_YTM = "com.google.android.apps.youtube.music"
        private const val TAB_SONGS = 0
        private const val TAB_FAV = 1
        private const val TAB_RECENT = 2
    }
}

object MusicAccounts {
    private fun prefs(c: Context) = c.getSharedPreferences("music_accounts", Context.MODE_PRIVATE)
    fun isLinked(c: Context, source: String) = prefs(c).getBoolean("link_$source", false)
    fun setLinked(c: Context, source: String, on: Boolean) = prefs(c).edit().putBoolean("link_$source", on).apply()

    fun favorites(c: Context, source: String) = read(c, "fav_$source")
    fun recent(c: Context, source: String) = read(c, "recent_$source")
    fun isFavorite(c: Context, source: String, id: String) = favorites(c, source).any { it.id == id }

    fun toggleFavorite(c: Context, source: String, song: MusicLibraryActivity.Song): Boolean {
        val cur = favorites(c, source).toMutableList()
        val had = cur.removeAll { it.id == song.id }
        if (!had) cur.add(0, song)
        write(c, "fav_$source", cur.take(40))
        return !had
    }

    fun addRecent(c: Context, source: String, song: MusicLibraryActivity.Song) {
        val cur = recent(c, source).filter { it.id != song.id }.toMutableList()
        cur.add(0, song)
        write(c, "recent_$source", cur.take(20))
    }

    private fun read(c: Context, key: String): List<MusicLibraryActivity.Song> = try {
        val arr = JSONArray(prefs(c).getString(key, "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(MusicLibraryActivity.Song(o.optString("id"), o.optString("title"), o.optString("artist"), o.optString("cover"), o.optString("preview")))
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun write(c: Context, key: String, songs: List<MusicLibraryActivity.Song>) {
        val arr = JSONArray()
        songs.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("cover", it.cover).put("preview", it.preview))
        }
        prefs(c).edit().putString(key, arr.toString()).apply()
    }
}
