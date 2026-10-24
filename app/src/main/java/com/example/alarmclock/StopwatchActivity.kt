package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityStopwatchBinding

class StopwatchActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    private lateinit var binding: ActivityStopwatchBinding
    private var timeInMillis = 0L
    private var isRunning = false
    private val laps = mutableListOf<Long>()
    private lateinit var lapAdapter: LapAdapter

    /** Vẽ mượt 10ms khi màn hình đang mở — không phụ thuộc broadcast 1s của service. */
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            timeInMillis = StopwatchService.currentElapsed()
            updateDisplay()
            uiHandler.postDelayed(this, 10)
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StopwatchService.ACTION_UPDATE) return
            isRunning = intent.getBooleanExtra(StopwatchService.EXTRA_RUNNING, false)
            if (intent.getBooleanExtra(StopwatchService.EXTRA_RESET, false)) {
                timeInMillis = 0L
                laps.clear()
                saveLaps()
                lapAdapter.notifyDataSetChanged()
                stopUiTick()
            } else {
                timeInMillis = if (isRunning) StopwatchService.currentElapsed()
                else intent.getLongExtra(StopwatchService.EXTRA_MS, 0L)
            }
            updateDisplay()
            binding.btnStartPause.text =
                if (isRunning) "Tạm dừng" else if (timeInMillis > 0) "Tiếp tục" else "Bắt đầu"
            binding.btnLapReset.text = if (isRunning) "Lượt" else "Đặt lại"
            if (isRunning) startUiTick() else stopUiTick()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityStopwatchBinding.inflate(layoutInflater)
            setContentView(binding.root)
        try { ThemeFix.apply(this, findViewById(android.R.id.content)) } catch (_: Exception) {}
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        try { DynamicIconHelper.ensureMainEnabled(this) } catch (_: Exception) {}
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Lỗi Bấm giờ: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
            return
        }
        try { BottomNavHelper.bind(this, binding.curvedNav, 2) } catch (_: Exception) {}
        try {
            binding.viewDial.accent = EventManager.activeColor(this)
            binding.viewDial.night = ThemeFix.isNight(this)
        } catch (_: Exception) {}

        try { AlarmNotificationHelper.ensureChannels(this) } catch (_: Exception) {}

        binding.toolbar.setNavigationOnClickListener { Motion.finishFade(this) }

        lapAdapter = LapAdapter(laps)
        binding.recyclerLaps.layoutManager = LinearLayoutManager(this)
        binding.recyclerLaps.adapter = lapAdapter
        loadLaps()

        // Nếu app bị đóng / máy khởi động lại: chạy lại bấm giờ đã lưu
        if (!StopwatchService.isActive) {
            try { ClockRecovery.restoreStopwatch(this) } catch (_: Exception) {}
        }
        // Đồng bộ nếu service đang chạy
        if (StopwatchService.isActive) {
            isRunning = StopwatchService.isRunningFlag
            timeInMillis = StopwatchService.currentElapsed()
            updateDisplay()
            binding.btnStartPause.text = if (isRunning) "Tạm dừng" else if (timeInMillis > 0) "Tiếp tục" else "Bắt đầu"
            binding.btnLapReset.text = if (isRunning) "Lượt" else "Đặt lại"
            if (isRunning) startUiTick()
        }

        binding.btnStartPause.setOnClickListener {
            SoundHelper.animatePress(it)
            if (isRunning) {
                SoundHelper.playPause(this)
                pause()
            } else {
                SoundHelper.playStart(this)
                start()
            }
        }
        binding.btnLapReset.setOnClickListener {
            SoundHelper.animatePress(it)
            SoundHelper.playClick(this)
            if (isRunning) addLap() else reset()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(StopwatchService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }
        // Vào lại màn hình: đồng bộ + vẽ mượt
        if (StopwatchService.isActive) {
            isRunning = StopwatchService.isRunningFlag
            timeInMillis = StopwatchService.currentElapsed()
            updateDisplay()
            if (isRunning) startUiTick()
        }
    }

    override fun onStop() {
        stopUiTick()
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    private fun startUiTick() {
        uiHandler.removeCallbacks(uiTick)
        uiHandler.post(uiTick)
    }

    private fun stopUiTick() {
        uiHandler.removeCallbacks(uiTick)
    }

    private fun start() {
        val intent = Intent(this, StopwatchService::class.java).apply {
            action = if (timeInMillis > 0 && !isRunning) StopwatchService.ACTION_RESUME
            else StopwatchService.ACTION_START
            putExtra(StopwatchService.EXTRA_MS, timeInMillis)
        }
        ContextCompat.startForegroundService(this, intent)
        isRunning = true
        binding.btnStartPause.text = "Tạm dừng"
        binding.btnLapReset.text = "Lượt"
        startUiTick()
        Toast.makeText(this, "Bấm giờ chạy nền — thoát app vẫn chạy (xem thông báo)", Toast.LENGTH_SHORT).show()
    }

    private fun pause() {
        // Lấy thời gian chính xác trước khi pause
        timeInMillis = StopwatchService.currentElapsed()
        startService(Intent(this, StopwatchService::class.java).setAction(StopwatchService.ACTION_PAUSE))
        isRunning = false
        stopUiTick()
        updateDisplay()
        binding.btnStartPause.text = "Tiếp tục"
        binding.btnLapReset.text = "Đặt lại"
    }

    private fun reset() {
        startService(Intent(this, StopwatchService::class.java).setAction(StopwatchService.ACTION_STOP))
        isRunning = false
        stopUiTick()
        timeInMillis = 0L
        laps.clear()
        saveLaps()
        lapAdapter.notifyDataSetChanged()
        updateDisplay()
        binding.btnStartPause.text = "Bắt đầu"
        binding.btnLapReset.text = "Đặt lại"
    }

    private fun addLap() {
        // Thời điểm bấm Lượt — đọc trực tiếp clock, không đợi UI tick
        val t = StopwatchService.currentElapsed()
        timeInMillis = t
        laps.add(0, t)
        saveLaps()
        lapAdapter.notifyDataSetChanged()
        binding.recyclerLaps.scrollToPosition(0)
        updateDisplay()
    }


    private fun lapsPrefs() = getSharedPreferences("stopwatch_laps", MODE_PRIVATE)

    private fun saveLaps() {
        val arr = org.json.JSONArray()
        for (t in laps) arr.put(t)
        lapsPrefs().edit().putString("laps", arr.toString()).apply()
    }

    private fun loadLaps() {
        try {
            val raw = lapsPrefs().getString("laps", "[]") ?: "[]"
            val arr = org.json.JSONArray(raw)
            laps.clear()
            for (i in 0 until arr.length()) {
                laps.add(arr.getLong(i))
            }
            lapAdapter.notifyDataSetChanged()
        } catch (_: Exception) {}
    }

    private fun updateDisplay() {
        binding.tvTime.text = StopwatchFmt.big(timeInMillis)
        binding.viewDial.setState(timeInMillis, isRunning)
        binding.tvState.text = when {
            isRunning -> "ĐANG CHẠY"
            timeInMillis > 0 -> "TẠM DỪNG"
            else -> "SẴN SÀNG"
        }
        if (laps.isEmpty()) {
            binding.tvCurLap.visibility = View.INVISIBLE
        } else {
            binding.tvCurLap.visibility = View.VISIBLE
            val cur = (timeInMillis - laps[0]).coerceAtLeast(0L)
            binding.tvCurLap.text = "Lượt ${laps.size + 1}  •  ${StopwatchFmt.plain(cur)}"
        }
    }

    override fun onResume() {
        super.onResume()
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        try { binding.root.alpha = 1f } catch (_: Exception) {}
        try { Motion.playTabEnter(this) } catch (_: Exception) {}
        try { binding.curvedNav.selectIndex(2, animate = false) } catch (_: Exception) {}
    }
}

/** Định dạng thời gian bấm giờ. */
object StopwatchFmt {
    private fun parts(ms: Long): Triple<Long, Long, Long> {
        val t = ms.coerceAtLeast(0L)
        return Triple(t / 3600000, (t / 60000) % 60, (t / 1000) % 60)
    }

    /** 12:34.56 hoặc 1:02:34.56 — phần trăm giây nhỏ hơn. */
    fun big(ms: Long): CharSequence {
        val (h, m, sec) = parts(ms)
        val centis = (ms.coerceAtLeast(0L) / 10) % 100
        val main = if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
        val sp = android.text.SpannableString(String.format("%s.%02d", main, centis))
        sp.setSpan(
            android.text.style.RelativeSizeSpan(0.58f),
            main.length, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return sp
    }

    fun plain(ms: Long): String {
        val (h, m, sec) = parts(ms)
        val centis = (ms.coerceAtLeast(0L) / 10) % 100
        return if (h > 0) String.format("%d:%02d:%02d.%02d", h, m, sec, centis)
        else String.format("%02d:%02d.%02d", m, sec, centis)
    }
}

class LapAdapter(private val laps: List<Long>) : RecyclerView.Adapter<LapAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLap: TextView = v.findViewById(R.id.tvLapNumber)
        val tvTime: TextView = v.findViewById(R.id.tvLapTime)
        val tvTotal: TextView = v.findViewById(R.id.tvLapTotal)
        val tvTag: TextView = v.findViewById(R.id.tvLapTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lap, parent, false)
        return VH(v)
    }

    private fun lapDuration(position: Int): Long {
        val older = laps.getOrNull(position + 1) ?: 0L
        return (laps[position] - older).coerceAtLeast(0L)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val dur = lapDuration(position)
        holder.tvLap.text = (laps.size - position).toString()
        holder.tvTime.text = StopwatchFmt.plain(dur)
        holder.tvTotal.text = "Tổng " + StopwatchFmt.plain(laps[position])
        // Nhanh nhất / chậm nhất khi có từ 2 lượt trở lên
        var tag = ""
        if (laps.size >= 2) {
            var min = Long.MAX_VALUE
            var max = Long.MIN_VALUE
            for (i in laps.indices) {
                val x = lapDuration(i)
                if (x < min) min = x
                if (x > max) max = x
            }
            if (min != max) {
                if (dur == min) tag = "fast" else if (dur == max) tag = "slow"
            }
        }
        val tv = holder.tvTag
        if (tag.isEmpty()) {
            tv.visibility = View.GONE
        } else {
            val fast = tag == "fast"
            tv.visibility = View.VISIBLE
            tv.text = if (fast) "Nhanh nhất" else "Chậm nhất"
            tv.setTextColor(if (fast) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())
            tv.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40f * holder.itemView.resources.displayMetrics.density
                setColor(if (fast) 0x2216A34A else 0x22DC2626)
            }
        }
    }

    override fun getItemCount() = laps.size
}
