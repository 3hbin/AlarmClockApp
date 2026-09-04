package com.example.alarmclock

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.alarmclock.databinding.ActivityTabHostBinding

class MainTabActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivityTabHostBinding
    private var suppressCallback = false
    private val red = 0xFFEA4335.toInt()
    private val gray = 0xFF757575.toInt()

    private val menuIds = intArrayOf(
        R.id.nav_alarm, R.id.nav_world, R.id.nav_stopwatch,
        R.id.nav_timer, R.id.nav_gallery, R.id.nav_settings
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { overridePendingTransition(0, 0) } catch (_: Exception) {}

        try {
            binding = ActivityTabHostBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            // Layout lỗi → về MainActivity
            startActivity(android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION))
            finish()
            return
        }

        try { setupViewPager() } catch (_: Exception) {}
        try { setupNavBars() } catch (_: Exception) {}
        try { applyNavStyle(AppSettings.getBottomNavStyle(this)) } catch (_: Exception) {
            try {
                binding.curvedNav.visibility = View.VISIBLE
                binding.googleNavBar.visibility = View.GONE
                binding.glassNavBar.visibility = View.GONE
            } catch (_: Exception) {}
        }

        val start = intent.getIntExtra(EXTRA_TAB, 0).coerceIn(0, 5)
        try { goToPage(start) } catch (_: Exception) {}
        try { DynamicIconHelper.applySafe(this) } catch (_: Exception) {}
        try {
            window.setBackgroundDrawableResource(R.color.surface)
            window.decorView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.surface))
        } catch (_: Exception) {}
    }

    private fun setupViewPager() {
        val pager = binding.viewPager
        pager.isUserInputEnabled = false
        // Chỉ preload tab kề → tránh tạo 6 Fragment cùng lúc (dễ crash)
        pager.offscreenPageLimit = 5
        pager.setPageTransformer { _, _ -> }
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 6
            override fun createFragment(position: Int): Fragment = try {
                when (position) {
                    0 -> AlarmListFragment()
                    1 -> WorldClockFragment()
                    2 -> StopwatchFragment()
                    3 -> TimerFragment()
                    4 -> GalleryFragment()
                    else -> SettingsFragment()
                }
            } catch (_: Exception) {
                AlarmListFragment()
            }
        }
        pager.post {
            try {
                (pager.getChildAt(0) as? RecyclerView)?.apply {
                    itemAnimator = null
                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                }
            } catch (_: Exception) {}
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (suppressCallback) return
                suppressCallback = true
                try { syncNavSelection(position) } catch (_: Exception) {}
                suppressCallback = false
            }
        })
    }

    private fun glassIcons(): List<ImageView> = listOf(
        binding.glass0, binding.glass1, binding.glass2,
        binding.glass3, binding.glass4, binding.glass5
    )

    private fun setupNavBars() {
        try {
            binding.googleNavBar.setOnItemSelectedListener { item ->
                if (suppressCallback) return@setOnItemSelectedListener true
                val idx = menuIds.indexOf(item.itemId)
                if (idx >= 0) {
                    if (idx == 5) SettingsLockHelper.requireUnlock(this) { goToPage(idx) }
                    else goToPage(idx)
                }
                true
            }
        } catch (_: Exception) {}

        try {
            glassIcons().forEachIndexed { index, iv ->
                iv.setOnClickListener {
                    if (index == 5) SettingsLockHelper.requireUnlock(this) { goToPage(index) }
                    else goToPage(index)
                }
            }
        } catch (_: Exception) {}

        try {
            binding.curvedNav.navStyle = CurvedBottomNavView.Style.CURVED
            binding.curvedNav.setItems(BottomNavHelper.items(this), 0)
            binding.curvedNav.setOnItemSelectedListener { index, _ ->
                if (suppressCallback) return@setOnItemSelectedListener
                if (index == 5) SettingsLockHelper.requireUnlock(this) { goToPage(index) }
                else goToPage(index)
            }
        } catch (_: Exception) {}
    }

    fun applyNavStyle(style: Int) {
        binding.googleNavBar.visibility = View.GONE
        binding.glassNavBar.visibility = View.GONE
        binding.curvedNav.visibility = View.GONE
        when (style) {
            AppSettings.NAV_GOOGLE -> binding.googleNavBar.visibility = View.VISIBLE
            AppSettings.NAV_GLASS, AppSettings.NAV_PERSISTENT -> binding.glassNavBar.visibility = View.VISIBLE
            else -> {
                binding.curvedNav.visibility = View.VISIBLE
                binding.curvedNav.navStyle = CurvedBottomNavView.Style.CURVED
                binding.curvedNav.setItems(BottomNavHelper.items(this), binding.viewPager.currentItem)
            }
        }
        syncNavSelection(binding.viewPager.currentItem)
    }

    private fun goToPage(index: Int) {
        val i = index.coerceIn(0, 5)
        try {
            if (binding.viewPager.currentItem != i) {
                binding.viewPager.setCurrentItem(i, false)
            }
        } catch (_: Exception) {}
        try { syncNavSelection(i) } catch (_: Exception) {}
    }

    private fun syncNavSelection(position: Int) {
        try {
            if (binding.googleNavBar.visibility == View.VISIBLE) {
                val id = menuIds.getOrNull(position) ?: return
                if (binding.googleNavBar.selectedItemId != id) {
                    binding.googleNavBar.selectedItemId = id
                }
            }
        } catch (_: Exception) {}
        try {
            if (binding.glassNavBar.visibility == View.VISIBLE) {
                glassIcons().forEachIndexed { i, iv ->
                    iv.setColorFilter(if (i == position) red else gray, PorterDuff.Mode.SRC_IN)
                    val s = if (i == position) 1.12f else 1f
                    iv.scaleX = s
                    iv.scaleY = s
                }
            }
        } catch (_: Exception) {}
        try {
            if (binding.curvedNav.visibility == View.VISIBLE) {
                binding.curvedNav.selectIndex(position, animate = false)
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try { applyNavStyle(AppSettings.getBottomNavStyle(this)) } catch (_: Exception) {}
        try { DynamicIconHelper.applySafe(this) } catch (_: Exception) {}
        try {
            window.setBackgroundDrawableResource(R.color.surface)
            window.decorView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.surface))
        } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        try {
            if (binding.viewPager.currentItem != 0) {
                goToPage(0)
                return
            }
        } catch (_: Exception) {}
        super.onBackPressed()
    }


    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra(EXTRA_TAB, -1)
        if (tab in 0..5) {
            try { goToPage(tab) } catch (_: Exception) {}
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
