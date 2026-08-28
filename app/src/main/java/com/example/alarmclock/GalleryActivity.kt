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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.io.File

/**
 * Bộ sưu tập ảnh chụp khi quét mặt thất bại.
 * Có mật khẩu; quên mật khẩu → đăng nhập Google trùng email khôi phục.
 */
class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private var unlocked = false
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val googleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email?.lowercase() ?: ""
            val recovery = AppSettings.getRecoveryEmail(this)
            if (recovery.isBlank()) {
                Toast.makeText(this, "Chưa đặt email khôi phục trong Cài đặt", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            if (email != recovery) {
                Toast.makeText(this, "Email Google ($email) không khớp email khôi phục", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            // Credential optional – đủ so khớp email
            AppSettings.clearGalleryPassword(this)
            unlocked = true
            Toast.makeText(this, "Đã xác minh Google — mật khẩu bộ sưu tập đã xóa, hãy đặt lại", Toast.LENGTH_LONG).show()
            showGallery()
        } catch (e: Exception) {
            Toast.makeText(this, "Đăng nhập Google thất bại: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Bộ sưu tập (quét mặt)"

        if (!AppSettings.hasGalleryPassword(this)) {
            unlocked = true
            showGallery()
        } else {
            binding.lockLayout.visibility = View.VISIBLE
            binding.galleryContent.visibility = View.GONE
        }

        binding.btnUnlock.setOnClickListener {
            val pw = binding.etPassword.text?.toString() ?: ""
            if (AppSettings.checkGalleryPassword(this, pw)) {
                unlocked = true
                showGallery()
            } else {
                Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnForgot.setOnClickListener { startGoogleRecovery() }
        binding.btnRefresh.setOnClickListener { if (unlocked) loadPhotos() }
    }

    private fun startGoogleRecovery() {
        val recovery = AppSettings.getRecoveryEmail(this)
        if (recovery.isBlank()) {
            Toast.makeText(this, "Vào Cài đặt → lưu Email Google khôi phục trước", Toast.LENGTH_LONG).show()
            return
        }
        // Web client id optional for requestIdToken; email match still works with basic sign-in
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleLauncher.launch(client.signInIntent)
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
