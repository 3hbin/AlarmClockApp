package com.example.alarmclock

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import android.content.res.Configuration

/**
 * Spotify / YouTube Music trong mục chuông.
 * Chưa cài app → nút Play Store. Đã cài → Đăng nhập mở thẳng app.
 * Sau khi đăng nhập, liệt kê bài: tên, ảnh, nghe thử.
 */
class MusicLibraryActivity : AppCompatActivity() {

    private lateinit var source: String
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: SRC_SPOTIFY
        val d = resources.displayMetrics.density
        val night = isNight()
        val iconTint = if (night) Color.WHITE else Color.TRANSPARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (night) 0xFF12141C.toInt() else 0xFFF7F8FC.toInt())
        }
        val bar = MaterialToolbar(this).apply {
            title = if (source == SRC_YTM) "YouTube Music" else "Spotify"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
            if (night) setTitleTextColor(Color.WHITE)
        }
        root.addView(bar)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
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
        }
        head.addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (8 * d).toInt())
        }
        val login = MaterialButton(this).apply {
            text = "Đăng nhập"
            setOnClickListener { openApp() }
        }
        val store = MaterialButton(this).apply {
            text = "Tải trên Google Play"
            setOnClickListener { openStore() }
        }
        actions.addView(login)
        actions.addView(store)
        root.addView(actions)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = NestedScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        paintInstallState()
    }

    override fun onResume() {
        super.onResume()
        paintInstallState()
        if (isInstalled()) connectLibrary()
    }

    override fun onDestroy() {
        try { browser?.disconnect() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun paintInstallState() {
        if (!isInstalled()) {
            status.text = "Chưa tải ứng dụng. Bấm nút bên dưới để vào Google Play."
        } else {
            status.text = "Bấm Đăng nhập để mở app. Đăng nhập xong quay lại, danh sách bài sẽ hiện."
        }
    }

    private fun pkg() = if (source == SRC_YTM) PKG_YTM else PKG_SPOTIFY

    private fun isInstalled(): Boolean = try {
        packageManager.getPackageInfo(pkg(), 0)
        true
    } catch (_: Exception) { false }

    private fun openStore() {
        val id = pkg()
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
        try {
            startActivity(market)
        } catch (_: Exception) {
            try { startActivity(web) } catch (_: Exception) {
                Toast.makeText(this, "Không mở được Google Play", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openApp() {
        if (!isInstalled()) {
            openStore()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg())
        if (launch == null) {
            openStore()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun connectLibrary() {
        if (browser != null) return
        val service = findBrowserService() ?: return
        status.text = "Đang lấy danh sách bài…"
        browser = MediaBrowserCompat(this, service, object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                val b = browser ?: return
                try {
                    controller = MediaControllerCompat(this@MusicLibraryActivity, b.sessionToken)
                    MediaControllerCompat.setMediaController(this@MusicLibraryActivity, controller)
                } catch (_: Exception) {}
                b.subscribe(b.root, sub)
            }
            override fun onConnectionFailed() {
                status.text = "Chưa đăng nhập. Bấm Đăng nhập để mở app."
                browser = null
            }
        }, null)
        try { browser?.connect() } catch (_: Exception) {
            status.text = "Chưa đăng nhập. Bấm Đăng nhập để mở app."
            browser = null
        }
    }

    private val sub = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
            if (children.isEmpty()) {
                status.text = "Chưa có bài. Hãy đăng nhập trong app rồi quay lại."
                return
            }
            val playable = children.filter { it.flags and MediaBrowserCompat.MediaItem.FLAG_PLAYABLE != 0 }
            if (playable.isEmpty()) {
                val folder = children.firstOrNull()
                if (folder != null) browser?.subscribe(folder.mediaId ?: return, this)
                return
            }
            status.text = "${playable.size} bài"
            showTracks(playable.take(40))
        }
        override fun onError(parentId: String) {
            status.text = "Chưa đăng nhập. Bấm Đăng nhập để mở app."
        }
    }

    private fun showTracks(items: List<MediaBrowserCompat.MediaItem>) {
        list.removeAllViews()
        val d = resources.displayMetrics.density
        val night = isNight()
        items.forEach { item ->
            val desc = item.description
            val title = desc.title?.toString() ?: "Bài hát"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            }
            val art = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt())
                scaleType = ImageView.ScaleType.CENTER_CROP
                val bmp: Bitmap? = desc.iconBitmap
                if (bmp != null) setImageBitmap(bmp) else {
                    setImageResource(if (source == SRC_YTM) R.drawable.ic_youtube_music else R.drawable.ic_spotify)
                    if (night) setColorFilter(Color.WHITE)
                }
            }
            val name = TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(if (night) Color.WHITE else 0xFF1A1C28.toInt())
                setPadding((12 * d).toInt(), 0, (8 * d).toInt(), 0)
            }
            val preview = MaterialButton(this).apply {
                text = "Nghe thử"
                setOnClickListener { preview(item.mediaId, title) }
            }
            row.setOnClickListener { choose(item.mediaId, title) }
            row.addView(art)
            row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(preview)
            list.addView(row)
        }
    }

    private fun preview(mediaId: String?, title: String) {
        if (mediaId.isNullOrBlank()) return
        try {
            controller?.transportControls?.playFromMediaId(mediaId, null)
            Toast.makeText(this, "Đang nghe thử: $title", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            openApp()
        }
    }

    private fun choose(mediaId: String?, title: String) {
        if (mediaId.isNullOrBlank()) return
        val uri = "music:$source:$mediaId"
        setResult(RESULT_OK, Intent().apply {
            putExtra(RingtonePickerActivity.EXTRA_URI, uri)
            putExtra(RingtonePickerActivity.EXTRA_LABEL, title)
        })
        finish()
    }

    private fun findBrowserService(): ComponentName? {
        val intent = Intent("android.media.browse.MediaBrowserService").setPackage(pkg())
        val list = try {
            packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        } catch (_: Exception) { emptyList() }
        val info = list.firstOrNull() ?: return null
        return ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)
    }

    private fun isNight(): Boolean {
        val mode = AppSettings.getDarkMode(this)
        if (mode == 1) return true
        if (mode == 2) return false
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val SRC_SPOTIFY = "spotify"
        const val SRC_YTM = "ytm"
        const val PKG_SPOTIFY = "com.spotify.music"
        const val PKG_YTM = "com.google.android.apps.youtube.music"
    }
}
