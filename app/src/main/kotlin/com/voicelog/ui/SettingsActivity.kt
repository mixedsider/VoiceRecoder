package com.voicelog.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.voicelog.databinding.ActivitySettingsBinding
import com.voicelog.util.ModelUtils

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("voicelog_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadRetentionSetting()
        setupRetentionListeners()
        showModelPaths()
    }

    private fun loadRetentionSetting() {
        val days = prefs.getInt("retention_days", 30)
        when (days) {
            7 -> binding.radio7.isChecked = true
            30 -> binding.radio30.isChecked = true
            90 -> binding.radio90.isChecked = true
            else -> {
                binding.radioCustom.isChecked = true
                binding.tilCustomDays.visibility = View.VISIBLE
                binding.etCustomDays.setText(days.toString())
            }
        }
    }

    private fun setupRetentionListeners() {
        binding.radioGroupRetention.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radio7.id -> {
                    saveRetention(7)
                    binding.tilCustomDays.visibility = View.GONE
                }
                binding.radio30.id -> {
                    saveRetention(30)
                    binding.tilCustomDays.visibility = View.GONE
                }
                binding.radio90.id -> {
                    saveRetention(90)
                    binding.tilCustomDays.visibility = View.GONE
                }
                binding.radioCustom.id -> {
                    binding.tilCustomDays.visibility = View.VISIBLE
                }
            }
        }

        binding.etCustomDays.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val days = binding.etCustomDays.text.toString().toIntOrNull() ?: 30
                saveRetention(days.coerceIn(1, 365))
            }
        }
    }

    private fun saveRetention(days: Int) {
        prefs.edit().putInt("retention_days", days).apply()
    }

    private fun showModelPaths() {
        val whisperPath = ModelUtils.getWhisperModelPath(this)
        val llamaPath = ModelUtils.getLlamaModelPath(this)
        val whisperReady = ModelUtils.isWhisperModelReady(this)
        val llamaReady = ModelUtils.isLlamaModelReady(this)

        binding.tvWhisperPath.text = "Whisper: $whisperPath\n상태: ${if (whisperReady) "✓ 준비됨" else "✗ 파일 없음"}"
        binding.tvLlamaPath.text = "LLaMA: $llamaPath\n상태: ${if (llamaReady) "✓ 준비됨" else "✗ 파일 없음"}"
    }
}
