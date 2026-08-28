package com.example.alarmclock

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityGalleryBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Bộ sưu tập ảnh khi nhập PIN sai / quét mặt thất bại.
 * Bảo vệ bằng mật khẩu. Quên MK → nhập đúng email khôi phục (hoặc Google nếu cấu hình SHA-1).
 */
class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private var unlocked = false

    private val googleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            // 12501 = user cancelled
            Toast.makeText(this, "Đã hủy đăng nhập Google", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email?.lowercase().orEmpty()
            unlockByEmail(email, source = "Google")
        } catch (e: ApiException) {
            val msg = when (e.statusCode) {
                10 -> "Lỗi 10 (DEVELOPER_ERROR): thiếu SHA-1 trên Firebase. Dùng \"Nhập email khôi phục\" bên dưới."
                12500 -> "Lỗi Google Sign-In. Thử \"Nhập email khôi phục\"."
                12501 -> "Đã hủy đăng nhập Google"
                else -> "Google thất bại (${e.statusCode}). Dùng nhập email khôi phục."
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Google lỗi: ${e.message}. Dùng nhập email khôi phục.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Bộ sưu tập"

        val hasPass = AppSettings.hasGalleryPassword(this)
        if (!hasPass) {
            unlocked = true
            showGallery()
        } else {
            binding.lockLayout.visibility = View.VISIBLE
            binding.galleryContent.visibility = View.GONE
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

        binding.btnForgotGoogle.setOnClickListener { recoverWithGoogle() }
        binding.btnForgotEmail.setOnClickListener { recoverWithEmailInput() }
        binding.btnRefresh.setOnClickListener { if (unlocked) loadPhotos() }
    }

    private fun unlockByEmail(email: String, source: String) {
        val recovery = AppSettings.getRecoveryEmail(this).lowercase()
        if (recovery.isBlank()) {
            Toast.makeText(this, "Chưa đặt email khôi phục trong Cài đặt", Toast.LENGTH_LONG).show()
            return
        }
        if (email.lowercase() != recovery) {
            Toast.makeText(
                this,
                "Email ($email) không khớp email khôi phục đã lưu",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        AppSettings.clearGalleryPassword(this)
        unlocked = true
        Toast.makeText(
            this,
            "Đã xác minh bằng $source — mật khẩu đã xóa. Vào Cài đặt đặt lại nếu cần.",
            Toast.LENGTH_LONG
        ).show()
        showGallery()
    }

    /** Khôi phục không cần Google API: gõ đúng email đã lưu trong Cài đặt */
    private fun recoverWithEmailInput() {
        val recovery = AppSettings.getRecoveryEmail(this)
        if (recovery.isBlank()) {
            Toast.makeText(this, "Vào Cài đặt → lưu Email Google khôi phục trước", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Nhập đúng email khôi phục"
            setText("")
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Khôi phục bằng email")
            .setMessage("Nhập đúng email đã lưu trong Cài đặt:\n${maskEmail(recovery)}")
            .setView(input)
            .setPositiveButton("Xác nhận") { _, _ ->
                val typed = input.text?.toString()?.trim().orEmpty()
                unlockByEmail(typed, source = "email")
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 1) return "***"
        return email.take(2) + "***" + email.substring(at)
    }

    private fun recoverWithGoogle() {
        val recovery = AppSettings.getRecoveryEmail(this)
        if (recovery.isBlank()) {
            Toast.makeText(this, "Vào Cài đặt → lưu Email Google khôi phục trước", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            // Đăng xuất trước để hiện lại picker
            client.signOut().addOnCompleteListener {
                googleLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được Google. Dùng nhập email.", Toast.LENGTH_LONG).show()
            recoverWithEmailInput()
        }
    }

    private fun showGallery() {
        binding.lockLayout.visibility = View.GONE
        binding.galleryContent.visibility = View.VISIBLE
        loadPhotos()
    }

    private fun loadPhotos() {
        val dir = File(filesDir, "intruder_photos")
        val files = dir.listFiles()?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        binding.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.layoutManager = GridLayoutManager(this, 2)
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
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
            holder.img.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
            holder.itemView.setOnClickListener { onClick(f) }
        }
    }
}
