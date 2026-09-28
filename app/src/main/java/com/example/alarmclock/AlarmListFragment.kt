package com.example.alarmclock

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarmclock.databinding.FragmentAlarmBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmListFragment : Fragment() {
    private var _binding: FragmentAlarmBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: AlarmRepository
    private val alarms = mutableListOf<Alarm>()
    private lateinit var adapter: AlarmAdapter
    private val timeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeTick = object : Runnable {
        override fun run() {
            updateCurrentTime()
            timeHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        repo = AlarmRepository(ctx)
        adapter = AlarmAdapter(
            alarms = alarms,
            onToggle = { alarm ->
                repo.saveAlarms(alarms)
                if (alarm.isEnabled) AlarmScheduler.schedule(ctx, alarm)
                else AlarmScheduler.cancel(ctx, alarm.id)
                updateNextAlarmBanner()
                Toast.makeText(ctx, if (alarm.isEnabled) "Đã bật" else "Đã tắt", Toast.LENGTH_SHORT).show()
            },
            onDelete = { alarm ->
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Xóa báo thức?")
                    .setMessage(alarm.label.ifBlank { "%02d:%02d".format(alarm.hour, alarm.minute) })
                    .setPositiveButton("Xóa") { _, _ ->
                        AlarmScheduler.cancel(ctx, alarm.id)
                        alarms.removeAll { it.id == alarm.id }
                        repo.saveAlarms(alarms)
                        adapter.notifyDataSetChanged()
                        updateNextAlarmBanner()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            },
            onEdit = { alarm ->
                // Sửa qua dialog đơn giản: mở lại editor bằng Features / dialog giờ
                Toast.makeText(ctx, "Sửa: %02d:%02d".format(alarm.hour, alarm.minute), Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(ctx)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null

        try {
            binding.fabAdd.setOnClickListener {
                SoundHelper.animatePress(it)
                showFabMenu()
            }
        } catch (_: Exception) {}

        try {
            binding.swipeRefresh.setOnRefreshListener {
                reload()
                binding.swipeRefresh.isRefreshing = false
            }
        } catch (_: Exception) {}

        try {
            binding.tvNextAlarm.setOnClickListener { showHistory() }
        } catch (_: Exception) {}

        updateCurrentTime()
        updateDailyTip()
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
        timeHandler.removeCallbacks(timeTick)
        timeHandler.post(timeTick)
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacks(timeTick)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeHandler.removeCallbacks(timeTick)
        _binding = null
    }

    private fun reload() {
        val ctx = context ?: return
        alarms.clear()
        try { alarms.addAll(repo.getAlarms()) } catch (_: Exception) {}
        adapter.notifyDataSetChanged()
        updateNextAlarmBanner()
        try {
            val enabled = alarms.count { it.isEnabled }
            if (enabled == 0) {
                androidx.core.app.NotificationManagerCompat.from(ctx).cancel(1001)
            }
        } catch (_: Exception) {}
    }

    private fun updateCurrentTime() {
        val b = _binding ?: return
        val ctx = context ?: return
        val now = Calendar.getInstance()
        val timeFormat = SimpleDateFormat(if (AppSettings.isUse24h(ctx)) "HH:mm" else "hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.getDefault())
        try {
            b.tvCurrentTime.text = timeFormat.format(now.time)
            b.tvCurrentDate.text = dateFormat.format(now.time).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } catch (_: Exception) {}
        updateNextAlarmBanner()
    }

    private fun updateNextAlarmBanner() {
        val b = _binding ?: return
        try {
            val next = alarms.filter { it.isEnabled }.minByOrNull { it.hour * 60 + it.minute }
            b.tvNextAlarm.text = if (next == null) {
                "Không có báo thức nào đang bật"
            } else {
                "Tiếp theo: %02d:%02d  %s".format(
                    next.hour, next.minute, next.label.ifBlank { "Báo thức" }
                )
            }
        } catch (_: Exception) {}
    }

    private fun updateDailyTip() {
        val tips = listOf(
            "💡 Thử thách dễ giúp dậy đúng giờ mà không mất ngủ",
            "🌅 Đặt báo thức T2–T6 để cuối tuần ngủ thêm",
            "🔋 Báo thức 1 lần tự tắt — tiết kiệm pin"
        )
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        try { binding.tvDailyTip.text = tips[day % tips.size] } catch (_: Exception) {}
    }

    private fun showHistory() {
        val ctx = context ?: return
        val lines = AlarmHistory.formatLines(ctx)
        val msg = if (lines.isEmpty()) "Chưa có lịch sử." else lines.take(30).joinToString("\n")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Lịch sử báo thức")
            .setMessage(msg)
            .setPositiveButton("Đóng", null)
            .show()
    }

    private fun showFabMenu() {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Bạn muốn làm gì?")
            .setItems(arrayOf("➕ Thêm báo thức", "📷 Test quét mặt", "😊 Test 10 biểu cảm")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(ctx, FeaturesActivity::class.java))
                    1 -> startActivity(
                        Intent(ctx, FaceChallengeActivity::class.java).putExtra(
                            FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_FACE
                        )
                    )
                    2 -> startActivity(
                        Intent(ctx, FaceChallengeActivity::class.java).putExtra(
                            FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_EXPR
                        )
                    )
                }
            }
            .show()
    }
}
