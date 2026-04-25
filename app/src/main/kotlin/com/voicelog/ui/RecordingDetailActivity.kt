package com.voicelog.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.voicelog.R
import com.voicelog.databinding.ActivityRecordingDetailBinding
import com.voicelog.db.AppDatabase
import com.voicelog.db.entity.Recording
import com.voicelog.db.entity.Summary
import com.voicelog.db.entity.Transcript
import com.voicelog.util.SummaryTextFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingDetailActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val EXTRA_RECORDING_ID = "recording_id"
        private const val TTS_UTTERANCE_ID = "transcript_utterance"

        fun createIntent(context: Context, recordingId: Long): Intent {
            return Intent(context, RecordingDetailActivity::class.java)
                .putExtra(EXTRA_RECORDING_ID, recordingId)
        }
    }

    private lateinit var binding: ActivityRecordingDetailBinding

    private val handler = Handler(Looper.getMainLooper())
    private val displayDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
    private val summaryDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isSeeking = false
    private var isTtsReady = false
    private var isSpeakingTranscript = false
    private var transcriptText = ""
    private var recordingFilePath: String? = null
    private var recordingDurationMs = 0

    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            if (!isSeeking) {
                binding.seekBarRecording.progress = player.currentPosition
                binding.tvPlaybackPosition.text = formatPlaybackText(
                    player.currentPosition,
                    player.duration
                )
            }
            if (player.isPlaying) {
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupTabs()
        setupAudioControls()
        setupTranscriptControls()
        tts = TextToSpeech(this, this)

        val recordingId = intent.getLongExtra(EXTRA_RECORDING_ID, -1L)
        if (recordingId <= 0L) {
            finish()
            return
        }

        loadRecordingDetail(recordingId)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isTtsReady = false
            updateTranscriptButton()
            return
        }

        val textToSpeech = tts ?: return
        val languageResult = textToSpeech.setLanguage(Locale.KOREA)
        isTtsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    runOnUiThread {
                        isSpeakingTranscript = true
                        updateTranscriptButton()
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    runOnUiThread {
                        isSpeakingTranscript = false
                        updateTranscriptButton()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    runOnUiThread {
                        isSpeakingTranscript = false
                        updateTranscriptButton()
                    }
                }
            }
        })

        updateTranscriptButton()
    }

    override fun onStop() {
        super.onStop()
        if (mediaPlayer?.isPlaying == true) {
            pauseRecordingPlayback()
        }
        stopTranscriptPlayback()
        handler.removeCallbacks(progressUpdater)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressUpdater)
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_recording_audio))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_summary))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_full_text))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.tabLayout.getTabAt(1)?.select()
    }

    private fun setupAudioControls() {
        binding.btnPlayRecording.setOnClickListener {
            toggleRecordingPlayback()
        }

        binding.seekBarRecording.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val total = mediaPlayer?.duration ?: recordingDurationMs
                    binding.tvPlaybackPosition.text = formatPlaybackText(progress, total)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val target = seekBar?.progress ?: return
                mediaPlayer?.seekTo(target)
                isSeeking = false
            }
        })
    }

    private fun setupTranscriptControls() {
        binding.btnReadTranscript.setOnClickListener {
            toggleTranscriptPlayback()
        }
        updateTranscriptButton()
    }

    private fun loadRecordingDetail(recordingId: Long) {
        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@RecordingDetailActivity)
                val recording = db.recordingDao().getById(recordingId) ?: return@withContext null
                val transcript = db.transcriptDao().getByRecordingId(recordingId)
                val dateKey = summaryDateFormat.format(Date(recording.startedAt))
                val summary = db.summaryDao().getByDate(dateKey)
                RecordingDetailState(recording, transcript, summary)
            }

            if (detail == null) {
                finish()
                return@launch
            }

            bindDetail(detail)
        }
    }

    private fun bindDetail(detail: RecordingDetailState) {
        val recording = detail.recording
        transcriptText = detail.transcript?.text.orEmpty()
        recordingFilePath = recording.filePath
        recordingDurationMs = recording.durationSec * 1000

        binding.toolbar.title = displayDateTimeFormat.format(Date(recording.startedAt))
        binding.toolbar.subtitle = statusLabel(recording.status)

        binding.tvRecordingMeta.text = buildString {
            appendLine("${getString(R.string.detail_time_label)}: ${displayDateTimeFormat.format(Date(recording.startedAt))}")
            appendLine("${getString(R.string.detail_duration_label)}: ${formatDuration(recording.durationSec)}")
            append("${getString(R.string.detail_status_label)}: ${statusLabel(recording.status)}")
        }

        binding.tvSummaryContent.text = detail.summary?.let {
            SummaryTextFormatter.normalize(it.summaryText)
        } ?: missingSummaryText(recording.status)

        binding.tvTranscriptContent.text = transcriptText.ifBlank {
            getString(R.string.transcript_empty)
        }

        val audioFileExists = File(recording.filePath).exists()
        binding.btnPlayRecording.isEnabled = audioFileExists
        if (!audioFileExists) {
            binding.tvPlaybackPosition.text = getString(R.string.recording_missing)
            binding.seekBarRecording.isEnabled = false
        } else {
            binding.seekBarRecording.isEnabled = true
            binding.seekBarRecording.max = recordingDurationMs
            binding.seekBarRecording.progress = 0
            binding.tvPlaybackPosition.text = "00:00 / ${formatMillis(recordingDurationMs)}"
        }

        updateTranscriptButton()
    }

    private fun toggleRecordingPlayback() {
        val player = mediaPlayer
        if (player?.isPlaying == true) {
            pauseRecordingPlayback()
            return
        }

        stopTranscriptPlayback()

        val readyPlayer = player ?: createMediaPlayer()
        if (readyPlayer == null) {
            Toast.makeText(this, getString(R.string.recording_loading_failed), Toast.LENGTH_SHORT).show()
            return
        }

        readyPlayer.start()
        binding.btnPlayRecording.text = getString(R.string.recording_pause)
        binding.btnPlayRecording.setIconResource(android.R.drawable.ic_media_pause)
        handler.post(progressUpdater)
    }

    private fun pauseRecordingPlayback() {
        mediaPlayer?.pause()
        binding.btnPlayRecording.text = getString(R.string.recording_play)
        binding.btnPlayRecording.setIconResource(android.R.drawable.ic_media_play)
        handler.removeCallbacks(progressUpdater)
    }

    private fun createMediaPlayer(): MediaPlayer? {
        val source = recordingFilePath
        if (source.isNullOrBlank()) {
            return null
        }

        val player = MediaPlayer()
        return try {
            player.setDataSource(source)
            player.prepare()
            player.setOnCompletionListener {
                binding.seekBarRecording.progress = 0
                binding.tvPlaybackPosition.text = formatPlaybackText(0, it.duration)
                binding.btnPlayRecording.text = getString(R.string.recording_play)
                binding.btnPlayRecording.setIconResource(android.R.drawable.ic_media_play)
                handler.removeCallbacks(progressUpdater)
            }
            mediaPlayer = player
            binding.seekBarRecording.max = player.duration
            binding.tvPlaybackPosition.text = formatPlaybackText(0, player.duration)
            player
        } catch (_: Exception) {
            player.release()
            null
        }
    }

    private fun toggleTranscriptPlayback() {
        if (isSpeakingTranscript) {
            stopTranscriptPlayback()
            return
        }

        if (transcriptText.isBlank()) {
            Toast.makeText(this, getString(R.string.transcript_empty), Toast.LENGTH_SHORT).show()
            return
        }

        if (!isTtsReady) {
            Toast.makeText(this, getString(R.string.tts_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        pauseRecordingPlayback()

        tts?.speak(transcriptText, TextToSpeech.QUEUE_FLUSH, null, TTS_UTTERANCE_ID)
    }

    private fun stopTranscriptPlayback() {
        if (isSpeakingTranscript) {
            tts?.stop()
        }
        isSpeakingTranscript = false
        updateTranscriptButton()
    }

    private fun updateTranscriptButton() {
        binding.btnReadTranscript.isEnabled = transcriptText.isNotBlank()
        binding.btnReadTranscript.text = if (isSpeakingTranscript) {
            getString(R.string.transcript_stop_reading)
        } else {
            getString(R.string.transcript_read_aloud)
        }
        binding.btnReadTranscript.setIconResource(
            if (isSpeakingTranscript) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun showTab(position: Int) {
        binding.audioContainer.isVisible = position == 0
        binding.summaryContainer.isVisible = position == 1
        binding.transcriptContainer.isVisible = position == 2
    }

    private fun statusLabel(status: String): String {
        return when (status) {
            "pending" -> "충전 대기중"
            "queued" -> "처리 대기중"
            "transcribing" -> "텍스트 변환중"
            "summarizing" -> "요약 생성중"
            "failed" -> "처리 실패"
            "done" -> "완료"
            else -> status
        }
    }

    private fun missingSummaryText(status: String): String {
        return when (status) {
            "failed" -> getString(R.string.summary_failed)
            "transcribing", "summarizing", "queued", "pending" -> getString(R.string.summary_pending)
            else -> getString(R.string.summary_pending)
        }
    }

    private fun formatDuration(durationSec: Int): String {
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        return if (minutes > 0) {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        } else {
            String.format(Locale.US, "0:%02d", seconds)
        }
    }

    private fun formatPlaybackText(currentMs: Int, totalMs: Int): String {
        return "${formatMillis(currentMs)} / ${formatMillis(totalMs)}"
    }

    private fun formatMillis(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private data class RecordingDetailState(
        val recording: Recording,
        val transcript: Transcript?,
        val summary: Summary?
    )
}
