package com.example.alarmclock

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.alarmclock.databinding.FragmentSettingsBinding

/**
 * Tab Cài đặt trong ViewPager2 — giữ state.
 * Giao diện đầy đủ nằm trong SettingsActivity; mở không animation.
 */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var openedOnce = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Các control trong fragment_settings dùng được trực tiếp (cùng id với activity)
        // Nút mở màn hình đầy đủ nếu cần Google Sign-In phức tạp
        view.setOnLongClickListener {
            openFullSettings()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Lần đầu vào tab → đồng bộ không bắt buộc mở activity
        openedOnce = true
    }

    private fun openFullSettings() {
        val i = Intent(requireContext(), SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(i)
        try { activity?.overridePendingTransition(0, 0) } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
