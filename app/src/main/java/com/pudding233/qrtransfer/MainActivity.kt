package com.pudding233.qrtransfer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.pudding233.qrtransfer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.sendCard.setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }

        binding.receiveCard.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }
    }
}