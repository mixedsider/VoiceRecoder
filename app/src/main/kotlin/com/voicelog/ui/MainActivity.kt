package com.voicelog.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voicelog.R
import com.voicelog.databinding.ActivityMainBinding
import com.voicelog.service.RecordingService
import com.voicelog.ui.adapter.RecordingAdapter
import com.voicelog.util.ModelUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = RecordingAdapter()
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.uiItems.collect { items ->
                adapter.submitList(items)
            }
        }

        binding.fabRecord.setOnClickListener {
            val action = if (isRecording) RecordingService.ACTION_STOP else RecordingService.ACTION_START
            val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
            startForegroundService(intent)
            isRecording = !isRecording
            binding.fabRecord.setImageResource(
                if (isRecording) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_btn_speak_now
            )
        }

        checkModelsAndShowGuide()
        requestMicrophonePermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkModelsAndShowGuide() {
        val whisperReady = ModelUtils.isWhisperModelReady(this)
        val llamaReady = ModelUtils.isLlamaModelReady(this)
        if (!whisperReady || !llamaReady) {
            val modelsDir = ModelUtils.getModelsDir(this).absolutePath
            val missing = buildString {
                if (!whisperReady) appendLine("• ${ModelUtils.WHISPER_MODEL_NAME}")
                if (!llamaReady) appendLine("• ${ModelUtils.LLAMA_MODEL_NAME}")
            }
            AlertDialog.Builder(this)
                .setTitle("모델 파일 필요")
                .setMessage(
                    "다음 모델 파일을 아래 경로에 복사해 주세요:\n\n$modelsDir\n\n필요한 파일:\n$missing"
                )
                .setPositiveButton("확인", null)
                .show()
        }
    }

    private fun requestMicrophonePermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("마이크 권한 필요")
                .setMessage("음성 녹음을 위해 마이크 권한이 필요합니다. 설정에서 권한을 허용해 주세요.")
                .setPositiveButton("확인", null)
                .show()
        }
    }
}
