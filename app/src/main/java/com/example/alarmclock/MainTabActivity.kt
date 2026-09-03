
package com.example.alarmclock

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.alarmclock.databinding.ActivityTabHostBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Host 6 tab — ViewPager2 (không animation) + 3 kiểu thanh dưới:
 * 0 CURVED | 1 GLASS | 2 GOOGLE Material 3
 */
class MainTabActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivityTabHostBinding
    private var suppressCallback = false
    private val red = 0xFFEA4335.toInt()
    private val gray = 0xFF757575.toInt()

    private val glassIcons: List<ImageView> by lazy {
        listOf(
            binding.glass0, binding.glass1, binding.glass2,
            binding.glass3, binding.glass4, binding.glass5
        )
    }

    private val menuIds = intArrayOf(
        R.id.nav_alarm, R.id.nav_world, R.id.nav_stopwatch,
        R.id.nav_timer, R.id.nav_gallery, R.id.nav_settings
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { overridePendingTransition(0, 0) } catch (_: Exception) {}

        binding = ActivityTabHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupNavBars()
        applyNavStyle(AppSettings.getBottomNavStyle(this))

        val start = intent.getIntExtra(EXTRA_TAB, 0).coerceIn(0, 5)
        goToPage(start)

        try { DynamicIconHelper.applySafe(this) } catch (_: Exception) {}
    }

    private fun setupViewPager() {
        val pager = binding.viewPager
        pager.isUserInputEnabled = false
        pager.offscreenPageLimit = 5
        pager.setPageTransformer { _, _ -> }
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 6
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> AlarmListFragment()
                1 -> WorldClockFragment()
                2 -> StopwatchFragment()
                3 -> TimerFragment()
                4 -> GalleryFragment()
                else -> SettingsFragment()
            }
        }
        pager.post {
            (pager.getChildAt(0) as? RecyclerView)?.apply {
                itemAnimator = null
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (suppressCallback) return
                suppressCallback = true
                syncNavSelection(position)
                suppressCallback = false
            }
        })
    }

    private fun setupNavBars() {
        // Google Material 3
        binding.googleNavBar.setOnItemSelectedListener { item ->
            if (suppressCallback) return@setOnItemSelectedListener true
            val idx = menuIds.indexOf(item.itemId)
            if (idx >= 0) {
                if (idx == 5) {
                    SettingsLockHelper.requireUnlock(this) { goToPage(idx) }
                } else goToPage(idx)
            }
            true
        }

        // Glass icons
        glassIcons.forEachIndexed { index, iv ->
            iv.setOnClickListener {
                if (index == 5) {
                    SettingsLockHelper.requireUnlock(this) { goToPage(index) }
                } else goToPage(index)
            }
        }

        // Curved
        binding.curvedNav.navStyle = CurvedBottomNavView.Style.CURVED
        binding.curvedNav.setItems(BottomNavHelper.items(this), 0)
        binding.curvedNav.setOnItemSelectedListener { index, _ ->
            if (suppressCallback) return@setOnItemSelectedListener
            if (index == 5) {
                SettingsLockHelper.requireUnlock(this) { goToPage(index) }
            } else goToPage(index)
        }
    }

    /** Hiện đúng 1 trong 3 thanh theo setting. */
    fun applyNavStyle(style: Int) {
        binding.googleNavBar.visibility = View.GONE
        binding.glassNavBar.visibility = View.GONE
        binding.curvedNav.visibility = View.GONE

        when (style) {
            AppSettings.NAV_GOOGLE -> {
                binding.googleNavBar.visibility = View.VISIBLE
            }
            AppSettings.NAV_GLASS, AppSettings.NAV_PERSISTENT -> {
                binding.glassNavBar.visibility = View.VISIBLE
            }
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
        if (binding.viewPager.currentItem != i) {
            binding.viewPager.setCurrentItem(i, false)
        }
        syncNavSelection(i)
    }

    private fun syncNavSelection(position: Int) {
        // Google
        try {
            if (binding.googleNavBar.visibility == View.VISIBLE) {
                val id = menuIds.getOrNull(position) ?: return
                if (binding.googleNavBar.selectedItemId != id) {
                    binding.googleNavBar.selectedItemId = id
                }
            }
        } catch (_: Exception) {}

        // Glass tint
        try {
            if (binding.glassNavBar.visibility == View.VISIBLE) {
                glassIcons.forEachIndexed { i, iv ->
                    val c = if (i == position) red else gray
                    iv.setColorFilter(c, PorterDuff.Mode.SRC_IN)
                    iv.animate().cancel()
                    val s = if (i == position) 1.15f else 1f
                    iv.scaleX = s
                    iv.scaleY = s
                }
            }
        } catch (_: Exception) {}

        // Curved
        try {
            if (binding.curvedNav.visibility == View.VISIBLE) {
                binding.curvedNav.selectIndex(position, animate = false)
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        applyNavStyle(AppSettings.getBottomNavStyle(this))
        try { DynamicIconHelper.applySafe(this) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.viewPager.currentItem != 0) goToPage(0)
        else super.onBackPressed()
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
