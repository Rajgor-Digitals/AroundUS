package com.aroundus.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aroundus.app.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnRegister.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            binding.tilFullName.error = null
            binding.tilEmail.error = null
            binding.tilPassword.error = null
            binding.tilConfirmPassword.error = null

            var isValid = true
            if (name.isEmpty()) { binding.tilFullName.error = "Name required"; isValid = false }
            if (email.isEmpty()) { binding.tilEmail.error = "Email required"; isValid = false }
            if (password.isEmpty()) { binding.tilPassword.error = "Password required"; isValid = false }
            if (password != confirmPass) { binding.tilConfirmPassword.error = "Passwords don't match"; isValid = false }

            if (isValid) {
                startActivity(Intent(this, OtpVerificationActivity::class.java))
            }
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
