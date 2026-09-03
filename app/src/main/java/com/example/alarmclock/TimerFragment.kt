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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.alarmclock.databinding.FragmentTimerBinding

class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!
    private var timeLeftInMillis = 0L
    private var isRunning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TimerService.ACTION_UPDATE -> {
                    timeLeftInMillis = intent.getLongExtra(TimerService.EXTRA_MS, 0L)
                    isRunning = intent.getBooleanExtra(TimerService.EXTRA_RUNNING, false)
                    updateText()
                    try { binding.btnStartPause.text = if (isRunning) "Pause" else "Start" } catch (_: Exception) {}
                }
                TimerService.ACTION_FINISHED -> {
                    timeLeftInMillis = 0
                    isRunning = false
                    updateText()
                    try { binding.btnStartPause.text = "Start" } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (TimerService.isActive && TimerService.remainingMs > 0) {
            timeLeftInMillis = TimerService.remainingMs
            isRunning = true
            try { binding.btnStartPause.text = "Pause" } catch (_: Exception) {}
        }
        updateText()

        try {
            binding.btnStartPause.setOnClickListener {
                SoundHelper.animatePress(it)
                val ctx = requireContext()
                if (isRunning) {
                    ctx.startService(Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_PAUSE))
                } else {
                    if (timeLeftInMillis <= 0) {
                        Toast.makeText(ctx, "Chọn thời gian trước", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    ctx.startService(
                        Intent(ctx, TimerService::class.java)
                            .setAction(TimerService.ACTION_START)
                            .putExtra(TimerService.EXTRA_MS, timeLeftInMillis)
                    )
                }
            }
            binding.btnReset.setOnClickListener {
                SoundHelper.animatePress(it)
                requireContext().startService(Intent(requireContext(), TimerService::class.java).setAction(TimerService.ACTION_STOP))
                timeLeftInMillis = 0
                isRunning = false
                updateText()
                binding.btnStartPause.text = "Start"
            }
            binding.btn1min.setOnClickListener { setMinutes(1) }
            binding.btn5min.setOnClickListener { setMinutes(5) }
            binding.btn10min.setOnClickListener { setMinutes(10) }
            binding.btn15min.setOnClickListener { setMinutes(15) }
        } catch (_: Exception) {}
    }

    private fun setMinutes(m: Int) {
        if (isRunning) {
            Toast.makeText(requireContext(), "Hãy Pause trước", Toast.LENGTH_SHORT).show()
            return
        }
        timeLeftInMillis = m * 60_000L
        updateText()
    }

    private fun updateText() {
        val b = _binding ?: return
        val totalSec = (timeLeftInMillis / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        try { b.tvTimer.text = "%02d:%02d".format(min, sec) } catch (_: Exception) {}
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
