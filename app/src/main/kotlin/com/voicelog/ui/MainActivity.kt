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
    private val processingDateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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
                            binding.toolbar.subtitle = getString(R.string.status_waiting_process_recordings)
                        }
                    }

                    WorkInfo.State.RUNNING -> {
                        if (!isDownloadActive()) {
                            binding.toolbar.subtitle = getString(R.string.status_processing_recordings)
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
                            Toast.makeText(this, R.string.toast_model_download_complete, Toast.LENGTH_SHORT).show()
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
        val modelName = info.progress.getString(ModelDownloadWorker.CURRENT_MODEL_NAME)
            ?: getString(R.string.model_label)
        val percent = info.progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, -1)
        binding.toolbar.subtitle = if (percent in 0..100) {
            getString(R.string.model_download_progress, modelName, percent)
        } else {
            getString(R.string.model_download_preparing_named, modelName)
        }
        binding.fabRecord.isEnabled = false
    }

    private fun updateModelAvailabilityUi() {
        val ready = InferencePreferences.areSelectedModelsReady(this)
        binding.fabRecord.isEnabled = ready
        if (!ready && binding.toolbar.subtitle.isNullOrBlank()) {
            binding.toolbar.subtitle = getString(R.string.toolbar_model_download_required)
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
                    .setTitle(R.string.title_model_download_in_progress)
                    .setMessage(R.string.message_model_download_in_progress)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
            }
            return
        }

        val missingNames = InferencePreferences.getMissingModelSpecs(this)
            .joinToString(separator = "\n") { "- ${it.displayName}" }

        modelDownloadDialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_model_download_required)
            .setMessage(getString(R.string.message_model_download_required, missingNames))
            .setPositiveButton(R.string.action_download) { _, _ ->
                enqueueModelDownload()
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    private fun showDownloadFailedDialog(reason: String? = null) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_model_download_failed)
            .setMessage(
                buildString {
                    append(getString(R.string.message_model_download_failed))
                    if (!reason.isNullOrBlank()) {
                        append("\n\n")
                        append(getString(R.string.error_line, reason))
                    }
                }
            )
            .setPositiveButton(R.string.action_retry) { _, _ ->
                enqueueModelDownload()
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun openRecordingDetail(item: RecordingUiItem.RecordingItem) {
        startActivity(RecordingDetailActivity.createIntent(this, item.recording.id))
    }

    private fun showSummaryDetail(item: RecordingUiItem.SummaryItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.daily_summary_title)
            .setMessage(SummaryTextFormatter.normalize(item.summary.summaryText))
            .setPositiveButton(R.string.action_ok, null)
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
                    .setTitle(R.string.title_permissions_needed)
                    .setMessage(R.string.message_permissions_needed)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
            }

            microphoneDenied -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.title_microphone_permission_required)
                    .setMessage(R.string.message_microphone_permission_required)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
            }

            notificationDenied -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.title_notification_permission_recommended)
                    .setMessage(R.string.message_notification_permission_recommended)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
            }
        }

        if (!microphoneDenied) {
            showMissingModelsDialog()
        }
    }
}
