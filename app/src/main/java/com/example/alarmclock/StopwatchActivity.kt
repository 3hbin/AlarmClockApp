package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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

    private lateinit var binding: ActivityStopwatchBinding
    private var timeInMillis = 0L
    private var isRunning = false
    private val laps = mutableListOf<Long>()
    private lateinit var lapAdapter: LapAdapter

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StopwatchService.ACTION_UPDATE) return
            timeInMillis = intent.getLongExtra(StopwatchService.EXTRA_MS, 0L)
            isRunning = intent.getBooleanExtra(StopwatchService.EXTRA_RUNNING, false)
            if (intent.getBooleanExtra(StopwatchService.EXTRA_RESET, false)) {
                laps.clear()
                lapAdapter.notifyDataSetChanged()
            }
            updateDisplay()
            binding.btnStartPause.text = if (isRunning) "Tạm dừng" else if (timeInMillis > 0) "Tiếp tục" else "Bắt đầu"
            binding.btnLapReset.text = if (isRunning) "Lượt" else "Đặt lại"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStopwatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AlarmNotificationHelper.ensureChannels(this)

        binding.toolbar.setNavigationOnClickListener { Motion.finishFade(this) }

        lapAdapter = LapAdapter(laps)
        binding.recyclerLaps.layoutManager = LinearLayoutManager(this)
        binding.recyclerLaps.adapter = lapAdapter

        // Đồng bộ nếu service đang chạy
        if (StopwatchService.isActive) {
            timeInMillis = StopwatchService.elapsedMs
            isRunning = true
            updateDisplay()
            binding.btnStartPause.text = "Tạm dừng"
            binding.btnLapReset.text = "Lượt"
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
    }

    override fun onStop() {
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
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
        Toast.makeText(this, "Bấm giờ chạy nền — thoát app vẫn chạy (xem thông báo)", Toast.LENGTH_SHORT).show()
    }

    private fun pause() {
        startService(Intent(this, StopwatchService::class.java).setAction(StopwatchService.ACTION_PAUSE))
        isRunning = false
        binding.btnStartPause.text = "Tiếp tục"
        binding.btnLapReset.text = "Đặt lại"
    }

    private fun reset() {
        startService(Intent(this, StopwatchService::class.java).setAction(StopwatchService.ACTION_STOP))
        isRunning = false
        timeInMillis = 0L
        laps.clear()
        lapAdapter.notifyDataSetChanged()
        updateDisplay()
        binding.btnStartPause.text = "Bắt đầu"
        binding.btnLapReset.text = "Đặt lại"
    }

    private fun addLap() {
        laps.add(0, timeInMillis)
        lapAdapter.notifyItemInserted(0)
        binding.recyclerLaps.scrollToPosition(0)
    }

    private fun updateDisplay() {
        val minutes = (timeInMillis / 60000) % 60
        val seconds = (timeInMillis / 1000) % 60
        val centis = (timeInMillis / 10) % 100
        binding.tvTime.text = String.format("%02d:%02d.%02d", minutes, seconds, centis)
    }
}

class LapAdapter(private val laps: List<Long>) : RecyclerView.Adapter<LapAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLap: TextView = v.findViewById(R.id.tvLapNumber)
        val tvTime: TextView = v.findViewById(R.id.tvLapTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lap, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = laps[position]
        val minutes = (t / 60000) % 60
        val seconds = (t / 1000) % 60
        val centis = (t / 10) % 100
        holder.tvLap.text = "Lượt ${laps.size - position}"
        holder.tvTime.text = String.format("%02d:%02d.%02d", minutes, seconds, centis)
    }

    override fun getItemCount() = laps.size
}
