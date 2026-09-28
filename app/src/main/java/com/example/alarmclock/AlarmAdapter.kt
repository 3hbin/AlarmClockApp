package com.example.alarmclock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ItemAlarmBinding
import java.util.Locale

class AlarmAdapter(
    private val alarms: MutableList<Alarm>,
    private val onToggle: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit,
    private val onEdit: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alarm = alarms[position]
        val timeText = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        holder.binding.tvTime.text = timeText
        holder.binding.tvLabel.text = Lang.displayLabel(alarm.label)
        val extra = buildString {
            append(alarm.getRepeatText())
            if (alarm.challengeType != Alarm.CHALLENGE_NONE) {
                append(" · ")
                append(Alarm.challengeLabel(alarm.challengeType))
            }
            if (alarm.snoozeMinutes > 0) append(" · báo lại ${alarm.snoozeMinutes} phút")
        }
        holder.binding.tvRepeat.text = extra
        holder.binding.ivGemini.visibility =
            if (alarm.routineOn) android.view.View.VISIBLE else android.view.View.GONE

        holder.binding.switchEnabled.setOnCheckedChangeListener(null)
        try {
            val c = EventManager.activeColor(holder.itemView.context)
            holder.binding.switchEnabled.colorOn = c
            holder.binding.switchEnabled.spinColor = c
            holder.binding.switchEnabled.invalidate()
        } catch (_: Exception) {}
        holder.binding.switchEnabled.setCheckedSilent(alarm.isEnabled)
        holder.binding.switchEnabled.setLoading(false)
        if (holder.itemView.getTag(R.id.tvTime) != alarm.id) {
            holder.itemView.setTag(R.id.tvTime, alarm.id)
            Motion.slideFadeIn(holder.itemView, (position.coerceAtMost(6) * 28).toLong())
        }

        holder.binding.switchEnabled.setOnCheckedChangeListener { switch, isChecked ->
            switch.setLoading(true)
            switch.postDelayed({
                alarm.isEnabled = isChecked
                onToggle(alarm)
                switch.setLoading(false)
            }, 350)
        }

        holder.binding.btnEdit.setOnClickListener {
            Motion.press(it) { onEdit(alarm) }
        }
        holder.binding.btnDelete.setOnClickListener {
            Motion.press(it) { onDelete(alarm) }
        }

        holder.binding.root.alpha = 1f
        holder.binding.root.scaleX = 1f
        holder.binding.root.scaleY = 1f
    }

    override fun getItemCount() = alarms.size
}
