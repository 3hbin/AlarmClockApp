package com.example.alarmclock

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.alarmclock.databinding.FragmentWorldClockBinding

class WorldClockFragment : Fragment() {
    private var _binding: FragmentWorldClockBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorldClockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try { binding.shimmer.visibility = View.GONE } catch (_: Exception) {}
        try { binding.loadingAnim.visibility = View.GONE } catch (_: Exception) {}
        try { binding.root.setOnClickListener { openFull() } } catch (_: Exception) {}
    }

    private fun openFull() {
        startActivity(
            Intent(requireContext(), WorldClockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        try { activity?.overridePendingTransition(0, 0) } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
