package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.alarmclock.databinding.FragmentTimerBinding

/**
 * Đếm ngược kiểu vòng tròn: bàn phím giờ/phút/giây, mốc nhanh, vòng chạy và màn hết giờ.
 * Vẫn dùng TimerService nên thoát app vẫn kêu đúng giờ.
 */
class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private var digits = ""
    private var timeLeftInMillis = 0L
    private var totalMillis = 0L
    private var isRunning = false
    private var finished = false
    private var finishedAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val overtimeTick = object : Runnable {
        override fun run() {
            val b = _binding ?: return
            if (!finished) return
            val sec = ((SystemClock.elapsedRealtime() - finishedAt) / 1000L).coerceAtLeast(0L)
            b.tvOvertime.text = "Đã trễ %02d:%02d".format(sec / 60, sec % 60)
            handler.postDelayed(this, 1000L)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TimerService.ACTION_UPDATE -> {
                    timeLeftInMillis = intent.getLongExtra(TimerService.EXTRA_MS, 0L)
                    isRunning = intent.getBooleanExtra(TimerService.EXTRA_RUNNING, false)
                    if (timeLeftInMillis > 0) finished = false
                    renderRun()
                }
                TimerService.ACTION_FINISHED -> showFinished()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val keys = listOf(
            binding.key0 to "0", binding.key1 to "1", binding.key2 to "2", binding.key3 to "3",
            binding.key4 to "4", binding.key5 to "5", binding.key6 to "6", binding.key7 to "7",
            binding.key8 to "8", binding.key9 to "9", binding.key00 to "00"
        )
        keys.forEach { (v, d) -> v.setOnClickListener { appendDigits(d) } }
        binding.keyDel.setOnClickListener {
            if (digits.isNotEmpty()) {
                digits = digits.dropLast(1)
                renderDigits()
            }
        }
        binding.btn1min.setOnClickListener { startPreset(1) }
        binding.btn5min.setOnClickListener { startPreset(5) }
        binding.btn10min.setOnClickListener { startPreset(10) }
        binding.btn15min.setOnClickListener { startPreset(15) }
        binding.btnStartPause.setOnClickListener {
            val ms = enteredMillis()
            if (ms > 0) startTimer(ms)
        }
        binding.btnPause.setOnClickListener { togglePause() }
        binding.btnAddMinute.setOnClickListener { addMinute() }
        binding.btnAddFromDone.setOnClickListener {
            dismissDone()
            startTimer(60_000L)
        }
        binding.btnReset.setOnClickListener { restartSame() }
        binding.btnDelete.setOnClickListener { stopTimer() }
        binding.btnStopRing.setOnClickListener { dismissDone() }

        if (TimerService.isActive && TimerService.remainingMs > 0) {
            timeLeftInMillis = TimerService.remainingMs
            if (totalMillis < timeLeftInMillis) totalMillis = timeLeftInMillis
            isRunning = true
            renderRun()
        } else {
            renderDigits()
            showSetup()
        }
    }

    private fun appendDigits(d: String) {
        if (digits.length >= 6) return
        val next = (digits + d).takeLast(6).trimStart('0')
        digits = next
        renderDigits()
    }

    private fun enteredMillis(): Long {
        val raw = digits.padStart(6, '0').takeLast(6)
        val h = raw.substring(0, 2).toIntOrNull() ?: 0
        val m = raw.substring(2, 4).toIntOrNull() ?: 0
        val s = raw.substring(4, 6).toIntOrNull() ?: 0
        return (h * 3600L + m * 60L + s) * 1000L
    }

    private fun renderDigits() {
        val b = _binding ?: return
        val raw = digits.padStart(6, '0').takeLast(6)
        val parts = listOf(b.tvHour to raw.substring(0, 2), b.tvMin to raw.substring(2, 4), b.tvSec to raw.substring(4, 6))
        val active = digits.isNotEmpty()
        val on = ContextCompat.getColor(b.root.context, R.color.text_primary)
        val off = ContextCompat.getColor(b.root.context, R.color.text_tertiary)
        parts.forEach { (tv, value) ->
            tv.text = value
            tv.setTextColor(if (active) on else off)
        }
        val canStart = enteredMillis() > 0
        b.btnStartPause.isEnabled = canStart
        b.btnStartPause.setTextColor(if (canStart) Color.WHITE else off)
    }

    private fun startPreset(minutes: Int) {
        digits = (minutes * 60).toString().padStart(2, '0')
        renderDigits()
        startTimer(minutes * 60_000L)
    }

    private fun startTimer(ms: Long) {
        if (ms <= 0) return
        val ctx = context ?: return
        finished = false
        handler.removeCallbacks(overtimeTick)
        totalMillis = ms
        timeLeftInMillis = ms
        isRunning = true
        ctx.startService(
            Intent(ctx, TimerService::class.java)
                .setAction(TimerService.ACTION_START)
                .putExtra(TimerService.EXTRA_MS, ms)
        )
        renderRun()
    }

    private fun togglePause() {
        val ctx = context ?: return
        if (isRunning) {
            ctx.startService(Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_PAUSE))
            isRunning = false
        } else if (timeLeftInMillis > 0) {
            ctx.startService(
                Intent(ctx, TimerService::class.java)
                    .setAction(TimerService.ACTION_RESUME)
                    .putExtra(TimerService.EXTRA_MS, timeLeftInMillis)
            )
            isRunning = true
        }
        renderRun()
    }

    private fun addMinute() {
        val base = if (timeLeftInMillis > 0) timeLeftInMillis else totalMillis
        startTimer(base + 60_000L)
    }

    private fun restartSame() {
        val ms = if (totalMillis > 0) totalMillis else timeLeftInMillis
        if (ms > 0) startTimer(ms)
    }

    private fun stopTimer() {
        val ctx = context ?: return
        ctx.startService(Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_STOP))
        TimerDoneController.dismiss(ctx)
        finished = false
        handler.removeCallbacks(overtimeTick)
        timeLeftInMillis = 0
        isRunning = false
        digits = ""
        renderDigits()
        showSetup()
    }

    private fun dismissDone() {
        val ctx = context ?: return
        TimerDoneController.dismiss(ctx)
        try {
            ctx.startService(Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_STOP))
        } catch (_: Exception) {}
        finished = false
        handler.removeCallbacks(overtimeTick)
        digits = ""
        timeLeftInMillis = 0
        renderDigits()
        showSetup()
    }

    private fun showSetup() {
        val b = _binding ?: return
        b.setupPanel.visibility = View.VISIBLE
        b.runPanel.visibility = View.GONE
        b.ringLayout.visibility = View.GONE
    }

    private fun renderRun() {
        val b = _binding ?: return
        if (finished) return
        b.setupPanel.visibility = View.GONE
        b.ringLayout.visibility = View.GONE
        b.runPanel.visibility = View.VISIBLE
        b.tvTimer.text = formatClock(timeLeftInMillis)
        b.btnPause.text = if (isRunning) "Tạm dừng" else "Tiếp tục"
        b.tvRunLabel.text = if (isRunning) "Đang đếm" else "Tạm dừng"
        val total = if (totalMillis > 0) totalMillis else timeLeftInMillis.coerceAtLeast(1)
        val progress = ((timeLeftInMillis.coerceAtLeast(0) * 1000L) / total).toInt().coerceIn(0, 1000)
        b.progressRing.setProgressCompat(progress, true)
    }

    private fun showFinished() {
        val b = _binding ?: return
        finished = true
        isRunning = false
        timeLeftInMillis = 0
        finishedAt = SystemClock.elapsedRealtime()
        b.setupPanel.visibility = View.GONE
        b.runPanel.visibility = View.GONE
        b.ringLayout.visibility = View.VISIBLE
        b.tvOvertime.text = "Đã trễ 00:00"
        handler.removeCallbacks(overtimeTick)
        handler.post(overtimeTick)
    }

    private fun formatClock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(TimerService.ACTION_UPDATE)
            addAction(TimerService.ACTION_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(requireContext(), receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(receiver, f)
        }
    }

    override fun onStop() {
        super.onStop()
        try { requireContext().unregisterReceiver(receiver) } catch (_: Exception) {}
        handler.removeCallbacks(overtimeTick)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(overtimeTick)
        _binding = null
        super.onDestroyView()
    }
}
