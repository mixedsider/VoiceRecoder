package com.voicelog.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.voicelog.R
import com.voicelog.databinding.ActivityMainBinding
import com.voicelog.db.AppDatabase
import com.voicelog.service.RecordingService
import com.voicelog.ui.adapter.RecordingAdapter
import com.voicelog.ui.adapter.RecordingUiItem
import com.voicelog.util.InferencePreferences
import com.voicelog.util.SummaryTextFormatter
import com.voicelog.worker.ModelDownloadWorker
import com.voicelog.worker.ProcessingScheduler
import com.voicelog.worker.ProcessingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_REQUIRED_RUNTIME_PERMISSIONS = 100
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = RecordingAdapter(
        onRecordingClick = { openRecordingDetail(it) },
        onSummaryClick = { showSummaryDetail(it) }
    )
    private val workManager by lazy { WorkManager.getInstance(this) }

    private var isRecording = false
    private var modelDownloadDialog: AlertDialog? = null
    private var lastDownloadWorkState: WorkInfo.State? = null
    private var currentDownloadWorkState: WorkInfo.State? = null
    private var lastProcessingWorkState: WorkInfo.State? = null
    private var permissionRequestInFlight = false
    private val processingDateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

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
            if (!hasMicrophonePermission()) {
                requestRequiredRuntimePermissions()
                return@setOnClickListener
            }

            if (!InferencePreferences.areSelectedModelsReady(this)) {
                showMissingModelsDialog(force = true)
                return@setOnClickListener
            }

            val action = if (isRecording) {
                RecordingService.ACTION_STOP
            } else {
                RecordingService.ACTION_START
            }
            val intent = Intent(this, RecordingService::class.java).apply {
                this.action = action
            }
            startForegroundService(intent)
            isRecording = !isRecording
            binding.fabRecord.setImageResource(
                if (isRecording) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_btn_speak_now
            )
        }

        observeModelDownloads()
        observeProcessingWork()
        repairCorruptedTranscripts()
        enqueueEligibleProcessing()
        reconcileStaleProcessingState()
        updateModelAvailabilityUi()
        if (!requestRequiredRuntimePermissions()) {
            showMissingModelsDialog()
        }
    }

    private fun observeProcessingWork() {
        workManager.getWorkInfosForUniqueWorkLiveData(ProcessingWorker.UNIQUE_WORK_NAME)
            .observe(this) { infos ->
                val info = infos.firstOrNull()
                when (info?.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> {
                        if (!isDownloadActive()) {
                            binding.toolbar.subtitle = "Waiting to process recordings..."
                        }
                    }

                    WorkInfo.State.RUNNING -> {
                        if (!isDownloadActive()) {
                            binding.toolbar.subtitle = "Processing recordings..."
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        if (!isDownloadActive()) {
                            binding.toolbar.subtitle = null
                        }
                        if (lastProcessingWorkState != WorkInfo.State.SUCCEEDED) {
                            reconcileStaleProcessingState()
                        }
                    }

                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED,
                    null -> {
                        if (!isDownloadActive()) {
                            binding.toolbar.subtitle = null
                        }
                        if (lastProcessingWorkState != info?.state) {
                            reconcileStaleProcessingState()
                        }
                    }
                }
                lastProcessingWorkState = info?.state
            }
    }

    private fun isDownloadActive(): Boolean {
        return currentDownloadWorkState == WorkInfo.State.ENQUEUED ||
            currentDownloadWorkState == WorkInfo.State.BLOCKED ||
            currentDownloadWorkState == WorkInfo.State.RUNNING
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeModelDownloads() {
        workManager.getWorkInfosForUniqueWorkLiveData(ModelDownloadWorker.UNIQUE_WORK_NAME)
            .observe(this) { infos ->
                val info = infos.firstOrNull()
                currentDownloadWorkState = info?.state

                when (info?.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.RUNNING -> renderDownloadState(info)

                    WorkInfo.State.SUCCEEDED -> {
                        binding.toolbar.subtitle = null
                        updateModelAvailabilityUi()
                        if (lastDownloadWorkState != WorkInfo.State.SUCCEEDED) {
                            Toast.makeText(this, "Model download complete.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> {
                        binding.toolbar.subtitle = null
                        updateModelAvailabilityUi()
                        if (lastDownloadWorkState != info.state) {
                            showDownloadFailedDialog(
                                info.outputData.getString(ModelDownloadWorker.ERROR_MESSAGE)
                            )
                        }
                    }

                    null -> {
                        binding.toolbar.subtitle = null
                        updateModelAvailabilityUi()
                    }
                }

                if (info == null && !InferencePreferences.areSelectedModelsReady(this)) {
                    showMissingModelsDialog()
                }

                lastDownloadWorkState = info?.state
            }
    }

    private fun reconcileStaleProcessingState() {
        lifecycleScope.launch {
            val hasActiveProcessingWork = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(ProcessingWorker.UNIQUE_WORK_NAME)
                    .get()
                    .any { info ->
                        info.state == WorkInfo.State.ENQUEUED ||
                            info.state == WorkInfo.State.BLOCKED ||
                            info.state == WorkInfo.State.RUNNING
                    }
            }

            if (!hasActiveProcessingWork) {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@MainActivity)
                        .recordingDao()
                        .resetInFlightStatuses()
                }
            }
        }
    }

    private fun enqueueEligibleProcessing() {
        lifecycleScope.launch(Dispatchers.IO) {
            ProcessingScheduler.maybeEnqueue(this@MainActivity)
        }
    }

    private fun repairCorruptedTranscripts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val corruptedIds = db.transcriptDao().getCorruptedRecordingIds()
            if (corruptedIds.isEmpty()) {
                return@launch
            }

            val affectedDates = corruptedIds.mapNotNull { recordingId ->
                db.recordingDao()
                    .getById(recordingId)
                    ?.startedAt
                    ?.let { processingDateKeyFmt.format(Date(it)) }
            }.toSet()

            db.transcriptDao().deleteByRecordingIds(corruptedIds)
            db.recordingDao().resetToPending(corruptedIds)
            affectedDates.forEach { date ->
                db.summaryDao().deleteByDate(date)
            }

            ProcessingScheduler.maybeEnqueue(this@MainActivity)
        }
    }

    private fun renderDownloadState(info: WorkInfo) {
        val modelName = info.progress.getString(ModelDownloadWorker.CURRENT_MODEL_NAME) ?: "Model"
        val percent = info.progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, -1)
        binding.toolbar.subtitle = if (percent in 0..100) {
            "$modelName downloading... $percent%"
        } else {
            "$modelName preparing download..."
        }
        binding.fabRecord.isEnabled = false
    }

    private fun updateModelAvailabilityUi() {
        val ready = InferencePreferences.areSelectedModelsReady(this)
        binding.fabRecord.isEnabled = ready
        if (!ready && binding.toolbar.subtitle.isNullOrBlank()) {
            binding.toolbar.subtitle = "Model download required."
        } else if (ready) {
            binding.toolbar.subtitle = null
        }
    }

    private fun showMissingModelsDialog(force: Boolean = false) {
        if (InferencePreferences.areSelectedModelsReady(this)) {
            return
        }
        if (!force && modelDownloadDialog?.isShowing == true) {
            return
        }

        if (currentDownloadWorkState == WorkInfo.State.RUNNING ||
            currentDownloadWorkState == WorkInfo.State.ENQUEUED ||
            currentDownloadWorkState == WorkInfo.State.BLOCKED
        ) {
            if (force) {
                AlertDialog.Builder(this)
                    .setTitle("Model download in progress")
                    .setMessage("The app is downloading required models. Recording will be available after it finishes.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            return
        }

        val missingNames = InferencePreferences.getMissingModelSpecs(this)
            .joinToString(separator = "\n") { "- ${it.displayName}" }

        modelDownloadDialog = AlertDialog.Builder(this)
            .setTitle("Model download required")
            .setMessage(
                "The app needs these models before first use:\n\n$missingNames\n\n" +
                    "They are large files, so Wi-Fi is recommended."
            )
            .setPositiveButton("Download") { _, _ ->
                enqueueModelDownload()
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showDownloadFailedDialog(reason: String? = null) {
        AlertDialog.Builder(this)
            .setTitle("Model download failed")
            .setMessage(
                buildString {
                    append("Please check your network connection and try again.")
                    if (!reason.isNullOrBlank()) {
                        append("\n\nError: ")
                        append(reason)
                    }
                }
            )
            .setPositiveButton("Retry") { _, _ ->
                enqueueModelDownload()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openRecordingDetail(item: RecordingUiItem.RecordingItem) {
        startActivity(RecordingDetailActivity.createIntent(this, item.recording.id))
    }

    private fun showSummaryDetail(item: RecordingUiItem.SummaryItem) {
        AlertDialog.Builder(this)
            .setTitle("Daily Summary")
            .setMessage(SummaryTextFormatter.normalize(item.summary.summaryText))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun enqueueModelDownload() {
        val request = ModelDownloadWorker.createRequest(
            InferencePreferences.getSelectedModelIds(this).toTypedArray()
        )

        workManager.enqueueUniqueWork(
            ModelDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun requestRequiredRuntimePermissions(): Boolean {
        val missingPermissions = buildList {
            if (!hasMicrophonePermission()) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missingPermissions.isEmpty() || permissionRequestInFlight) {
            return false
        }

        permissionRequestInFlight = true
        requestPermissions(
            missingPermissions.toTypedArray(),
            REQUEST_REQUIRED_RUNTIME_PERMISSIONS,
        )
        return true
    }

    private fun hasMicrophonePermission(): Boolean {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_REQUIRED_RUNTIME_PERMISSIONS) {
            return
        }

        permissionRequestInFlight = false
        val requestedPermissions = permissions.toSet()
        val microphoneDenied = requestedPermissions.contains(Manifest.permission.RECORD_AUDIO) &&
            !hasMicrophonePermission()
        val notificationDenied =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS) &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        when {
            microphoneDenied && notificationDenied -> {
                AlertDialog.Builder(this)
                    .setTitle("Permissions needed")
                    .setMessage(
                        "VoiceLog needs microphone permission to record voice. " +
                            "Notification permission is recommended so you can see background recording, download, and processing progress."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }

            microphoneDenied -> {
                AlertDialog.Builder(this)
                    .setTitle("Microphone permission required")
                    .setMessage("Voice recording needs microphone permission. Please allow it in system settings.")
                    .setPositiveButton("OK", null)
                    .show()
            }

            notificationDenied -> {
                AlertDialog.Builder(this)
                    .setTitle("Notification permission recommended")
                    .setMessage("Background processing progress uses notifications. Allow notifications to see transcription progress.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        if (!microphoneDenied) {
            showMissingModelsDialog()
        }
    }
}
