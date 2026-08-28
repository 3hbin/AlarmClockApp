package com.example.alarmclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityStopwatchBinding

class StopwatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStopwatchBinding
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var timeInMillis = 0L
    private var isRunning = false
    private val laps = mutableListOf<Long>()
    private lateinit var lapAdapter: LapAdapter

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                timeInMillis = SystemClock.elapsedRealtime() - startTime
                updateDisplay()
                handler.postDelayed(this, 10)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStopwatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        lapAdapter = LapAdapter(laps)
        binding.recyclerLaps.layoutManager = LinearLayoutManager(this)
        binding.recyclerLaps.adapter = lapAdapter

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

    private fun start() {
        startTime = SystemClock.elapsedRealtime() - timeInMillis
        isRunning = true
        handler.post(updateRunnable)
        binding.btnStartPause.text = "Tạm dừng"
        binding.btnLapReset.text = "Lượt"
    }

    private fun pause() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        binding.btnStartPause.text = "Tiếp tục"
        binding.btnLapReset.text = "Đặt lại"
    }

    private fun reset() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
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
