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
        holder.binding.tvLabel.text = alarm.label
        holder.binding.tvRepeat.text = alarm.getRepeatText()

        holder.binding.switchEnabled.setOnCheckedChangeListener(null)
        holder.binding.switchEnabled.setCheckedSilent(alarm.isEnabled)
        holder.binding.switchEnabled.setLoading(false)

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

        Motion.fadeScaleIn(holder.binding.root, delay = (position % 6) * 30L)
    }

    override fun getItemCount() = alarms.size
}
