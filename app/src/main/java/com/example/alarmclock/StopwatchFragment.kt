
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.alarmclock.databinding.FragmentStopwatchBinding

class StopwatchFragment : Fragment() {
    private var _binding: FragmentStopwatchBinding? = null
    private val binding get() = _binding!!
    private var isRunning = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateDisplay(StopwatchService.currentElapsed())
            uiHandler.postDelayed(this, 16)
        }
    }
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StopwatchService.ACTION_UPDATE) return
            isRunning = intent.getBooleanExtra(StopwatchService.EXTRA_RUNNING, false)
            val ms = if (isRunning) StopwatchService.currentElapsed()
            else intent.getLongExtra(StopwatchService.EXTRA_MS, 0L)
            updateDisplay(ms)
            try {
                binding.btnStartPause.text =
                    if (isRunning) "Tạm dừng" else if (ms > 0) "Tiếp tục" else "Bắt đầu"
            } catch (_: Exception) {}
            if (isRunning) startTick() else stopTick()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStopwatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            binding.btnStartPause.setOnClickListener {
                SoundHelper.animatePress(it)
                val ctx = requireContext()
                if (isRunning) {
                    ctx.startService(Intent(ctx, StopwatchService::class.java).setAction(StopwatchService.ACTION_PAUSE))
                } else {
                    ctx.startService(Intent(ctx, StopwatchService::class.java).setAction(StopwatchService.ACTION_START))
                }
            }
            binding.btnLapReset.setOnClickListener {
                SoundHelper.animatePress(it)
                val ctx = requireContext()
                ctx.startService(Intent(ctx, StopwatchService::class.java).setAction(StopwatchService.ACTION_RESET))
                isRunning = false
                stopTick()
                updateDisplay(0L)
                binding.btnStartPause.text = "Bắt đầu"
            }
        } catch (_: Exception) {}
        updateDisplay(StopwatchService.currentElapsed())
    }

    override fun onStart() {
        super.onStart()
        val ctx = requireContext()
        val f = IntentFilter(StopwatchService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(ctx, updateReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(updateReceiver, f)
        }
    }

    override fun onStop() {
        super.onStop()
        try { requireContext().unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        stopTick()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTick()
        _binding = null
    }

    private fun startTick() {
        uiHandler.removeCallbacks(uiTick)
        uiHandler.post(uiTick)
    }
    private fun stopTick() { uiHandler.removeCallbacks(uiTick) }

    private fun updateDisplay(ms: Long) {
        val b = _binding ?: return
        val total = ms / 10
        val centi = (total % 100).toInt()
        val sec = ((total / 100) % 60).toInt()
        val min = ((total / 100) / 60).toInt()
        try {
            b.tvTime.text = "%02d:%02d.%02d".format(min, sec, centi)
        } catch (_: Exception) {
            try { b.tvStopwatch.text = "%02d:%02d.%02d".format(min, sec, centi) } catch (_: Exception) {}
        }
    }
}
