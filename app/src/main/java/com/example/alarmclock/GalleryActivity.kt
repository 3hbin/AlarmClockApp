package com.example.alarmclock

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityGalleryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Bộ sưu tập ảnh khi nhập PIN sai / quét mặt thất bại.
 * Mở khóa bằng mật khẩu HOẶC email khôi phục (không dùng Google Sign-In — lỗi 10).
 */
class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { BottomNavHelper.bind(this, binding.curvedNav, 4) } catch (_: Exception) {}
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Bộ sưu tập"

        if (!AppSettings.hasGalleryPassword(this)) {
            unlocked = true
            showGallery()
        } else {
            binding.lockLayout.visibility = View.VISIBLE
            binding.galleryContent.visibility = View.GONE
            val saved = AppSettings.getRecoveryEmail(this)
            if (saved.isNotBlank()) {
                binding.tvRecoveryHint.text =
                    "Quên mật khẩu? Gõ đúng email đã lưu (${maskEmail(saved)}):"
                // Gợi ý domain, không prefill full email (bảo mật)
            } else {
                binding.tvRecoveryHint.text =
                    "Chưa lưu email khôi phục. Vào Cài đặt → nhập Email + bấm Lưu email, hoặc Xóa mật khẩu."
                binding.etRecoveryEmail.isEnabled = false
                binding.btnForgotEmail.isEnabled = false
                binding.btnForgotEmail.alpha = 0.45f
            }
        }

        binding.btnUnlock.setOnClickListener {
            val pass = binding.etPassword.text?.toString().orEmpty()
            if (AppSettings.checkGalleryPassword(this, pass)) {
                unlocked = true
                showGallery()
            } else {
                Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnForgotEmail.setOnClickListener {
            val typed = binding.etRecoveryEmail.text?.toString()?.trim().orEmpty()
            unlockByEmail(typed)
        }

        binding.btnRefresh.setOnClickListener { if (unlocked) loadPhotos() }
        binding.swipeRefresh.setColorSchemeColors(0xFF3F51B5.toInt(), 0xFF7E57C2.toInt())
        binding.swipeRefresh.setOnRefreshListener {
            if (unlocked) loadPhotos() else binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun unlockByEmail(email: String) {
        val recovery = AppSettings.getRecoveryEmail(this).trim().lowercase()
        if (recovery.isBlank()) {
            Toast.makeText(
                this,
                "Chưa lưu email khôi phục trong Cài đặt. Vào Cài đặt → Lưu email, hoặc Xóa mật khẩu bộ sưu tập.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (email.isBlank()) {
            Toast.makeText(this, "Nhập email khôi phục đã lưu", Toast.LENGTH_SHORT).show()
            return
        }
        if (email.trim().lowercase() != recovery) {
            Toast.makeText(this, "Email không khớp (${maskEmail(recovery)})", Toast.LENGTH_LONG).show()
            return
        }
        AppSettings.clearGalleryPassword(this)
        unlocked = true
        Toast.makeText(this, "Đã xác minh email — mật khẩu đã xóa", Toast.LENGTH_LONG).show()
        showGallery()
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 1) return "***"
        return email.take(2) + "***" + email.substring(at)
    }

    private fun showGallery() {
        binding.lockLayout.visibility = View.GONE
        binding.galleryContent.visibility = View.VISIBLE
        loadPhotos()
    }

    private fun loadPhotos() {
        binding.shimmer.show()
        binding.loadingAnim.applyBrandDefault()
        binding.loadingAnim.visibility = android.view.View.VISIBLE
        binding.loadingAnim.start()
        Thread {
            val dir = File(filesDir, "intruder_photos")
            val files = dir.listFiles()?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
            runOnUiThread {
                binding.shimmer.hide()
                binding.loadingAnim.stop()
                binding.loadingAnim.visibility = android.view.View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                binding.recycler.layoutManager = GridLayoutManager(this, 2)
                binding.recycler.setHasFixedSize(true)
                binding.recycler.adapter = PhotoAdapter(files) { file ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle(file.name)
                        .setItems(arrayOf("Xóa ảnh")) { _, _ ->
                            if (!unlocked) return@setItems
                            file.delete()
                            loadPhotos()
                        }
                        .setNegativeButton("Đóng", null)
                        .show()
                }
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        Motion.finishFade(this)
        return true
    }

    private class PhotoAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<PhotoAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgPhoto)
            val name: TextView = v.findViewById(R.id.tvName)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_photo, parent, false)
            return VH(v)
        }
        override fun getItemCount() = files.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = files[position]
            holder.name.text = f.name
            holder.img.setImageBitmap(decodeSampled(f.absolutePath, 360, 360))
            holder.itemView.setOnClickListener { onClick(f) }
        }

        private fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val h = bounds.outHeight
            val w = bounds.outWidth
            while (h / sample > reqH && w / sample > reqW) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeFile(path, opts)
        }
    }
}
