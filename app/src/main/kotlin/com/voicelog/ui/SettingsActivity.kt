package com.voicelog.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.voicelog.databinding.ActivitySettingsBinding
import com.voicelog.util.InferenceBackend
import com.voicelog.util.InferencePipeline
import com.voicelog.util.InferencePreferences
import com.voicelog.util.InferenceRuntime
import com.voicelog.util.ModelSpec
import com.voicelog.util.ModelUtils
import com.voicelog.util.ProcessingPreferences
import com.voicelog.worker.ModelDownloadWorker
import com.voicelog.worker.ProcessingScheduler

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
        loadExecutionPolicies()
        loadInferenceSettings()
        setupRetentionListeners()
        setupExecutionPolicyListeners()
        setupInferenceListeners()
        setupModelActionButtons()
        renderModelStatus()
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

    private fun loadExecutionPolicies() {
        when (ProcessingPreferences.getSttPolicy(this)) {
            ProcessingPreferences.ExecutionPolicy.IMMEDIATE -> binding.radioSttImmediate.isChecked = true
            ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE -> binding.radioSttBatteryAware.isChecked = true
            ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY -> binding.radioSttChargingOnly.isChecked = true
            ProcessingPreferences.ExecutionPolicy.MANUAL -> binding.radioSttManual.isChecked = true
        }

        when (ProcessingPreferences.getSummaryPolicy(this)) {
            ProcessingPreferences.ExecutionPolicy.IMMEDIATE -> binding.radioSummaryImmediate.isChecked = true
            ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE -> binding.radioSummaryBatteryAware.isChecked = true
            ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY -> binding.radioSummaryChargingOnly.isChecked = true
            ProcessingPreferences.ExecutionPolicy.MANUAL -> binding.radioSummaryManual.isChecked = true
        }
    }

    private fun loadInferenceSettings() {
        applyBackendSelection(binding.radioGroupSttBackend, InferencePreferences.getSttBackend(this))
        applyBackendSelection(binding.radioGroupLlmBackend, InferencePreferences.getLlmBackend(this))
        bindModelSpinner(
            pipeline = InferencePipeline.STT,
            spinnerAdapter = binding.spinnerSttModel,
            selectedModelId = InferencePreferences.getSttModelId(this),
        )
        bindModelSpinner(
            pipeline = InferencePipeline.LLM,
            spinnerAdapter = binding.spinnerLlmModel,
            selectedModelId = InferencePreferences.getLlmModelId(this),
        )
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

    private fun setupExecutionPolicyListeners() {
        binding.radioGroupSttPolicy.setOnCheckedChangeListener { _, checkedId ->
            val policy = when (checkedId) {
                binding.radioSttImmediate.id -> ProcessingPreferences.ExecutionPolicy.IMMEDIATE
                binding.radioSttBatteryAware.id -> ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE
                binding.radioSttManual.id -> ProcessingPreferences.ExecutionPolicy.MANUAL
                else -> ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY
            }
            ProcessingPreferences.setSttPolicy(this, policy)
            ProcessingScheduler.maybeEnqueue(this)
        }

        binding.radioGroupSummaryPolicy.setOnCheckedChangeListener { _, checkedId ->
            val policy = when (checkedId) {
                binding.radioSummaryImmediate.id -> ProcessingPreferences.ExecutionPolicy.IMMEDIATE
                binding.radioSummaryBatteryAware.id -> ProcessingPreferences.ExecutionPolicy.BATTERY_AWARE
                binding.radioSummaryManual.id -> ProcessingPreferences.ExecutionPolicy.MANUAL
                else -> ProcessingPreferences.ExecutionPolicy.CHARGING_ONLY
            }
            ProcessingPreferences.setSummaryPolicy(this, policy)
            ProcessingScheduler.maybeEnqueue(this)
        }
    }

    private fun setupInferenceListeners() {
        binding.radioGroupSttBackend.setOnCheckedChangeListener { _, _ ->
            InferencePreferences.setSttBackend(this, readBackendSelection(binding.radioGroupSttBackend))
            renderModelStatus()
        }

        binding.radioGroupLlmBackend.setOnCheckedChangeListener { _, _ ->
            InferencePreferences.setLlmBackend(this, readBackendSelection(binding.radioGroupLlmBackend))
            renderModelStatus()
        }

        binding.spinnerSttModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position) as? ModelSpec ?: return
                InferencePreferences.setSttModelId(this@SettingsActivity, selected.id)
                renderModelStatus()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.spinnerLlmModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position) as? ModelSpec ?: return
                InferencePreferences.setLlmModelId(this@SettingsActivity, selected.id)
                renderModelStatus()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupModelActionButtons() {
        binding.btnDownloadSelectedModels.setOnClickListener {
            val selectedIds = InferencePreferences.getSelectedModelIds(this).toTypedArray()
            val request = ModelDownloadWorker.createRequest(selectedIds)
            WorkManager.getInstance(this).enqueueUniqueWork(
                ModelDownloadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            Toast.makeText(this, "Started download for selected models.", Toast.LENGTH_SHORT).show()
        }

        binding.btnRefreshModelStatus.setOnClickListener {
            renderModelStatus()
            Toast.makeText(this, "Model status refreshed.", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteSelectedModels.setOnClickListener {
            val specs = InferencePreferences.getRequiredModelSpecs(this)
            var deleted = 0
            specs.forEach { spec ->
                val file = ModelUtils.getModelFile(this, spec)
                if (file.exists() && file.delete()) {
                    deleted++
                }
            }
            renderModelStatus()
            Toast.makeText(this, "Deleted $deleted selected model file(s).", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRetention(days: Int) {
        prefs.edit().putInt("retention_days", days).apply()
    }

    private fun renderModelStatus() {
        val sttModel = InferencePreferences.getSelectedSttModel(this)
        val llmModel = InferencePreferences.getSelectedLlmModel(this)
        val sttResolution = InferenceRuntime.resolveStt(this)
        val llmResolution = InferenceRuntime.resolveLlm(this)
        applyBackendAvailabilityState(sttResolution.availabilities, isStt = true)
        applyBackendAvailabilityState(llmResolution.availabilities, isStt = false)
        val sttDownloadSpecs = ModelUtils.expandModelSelection(listOf(sttModel.id))
        val llmDownloadSpecs = ModelUtils.expandModelSelection(listOf(llmModel.id))
        val sttReady = sttDownloadSpecs.all { ModelUtils.isModelReady(this, it) }
        val llmReady = llmDownloadSpecs.all { ModelUtils.isModelReady(this, it) }
        val missingNames = InferencePreferences.getMissingModelSpecs(this)
            .joinToString(separator = "\n") { "- ${it.displayName}" }
            .ifBlank { "None" }

        binding.tvInferenceSummary.text =
            "STT: requested ${sttResolution.requestedBackend.displayName}, " +
                "effective ${sttResolution.effectiveBackend.displayName}, " +
                "engine ${sttResolution.engineKind}\n" +
                "LLM: requested ${llmResolution.requestedBackend.displayName}, " +
                "effective ${llmResolution.effectiveBackend.displayName}, " +
                "engine ${llmResolution.engineKind}\n" +
                buildString {
                    if (sttResolution.fallbackReason != null) {
                        append("STT fallback: ${sttResolution.fallbackReason}\n")
                    }
                    if (llmResolution.fallbackReason != null) {
                        append("LLM fallback: ${llmResolution.fallbackReason}\n")
                    }
                    append("Actual runtime fallback is also logged after engine initialization.")
                }.trim()

        binding.tvWhisperPath.text = buildString {
            append("STT model: ${sttModel.displayName}\n")
            append("Status: ${if (sttReady) "Ready" else "Missing"}\n")
            append("Requested backend: ${sttResolution.requestedBackend.displayName}\n")
            append("Effective backend: ${sttResolution.effectiveBackend.displayName}\n")
            append("Engine: ${sttModel.engineKind}\n")
            append("Supported backends: ${sttModel.supportedBackends.joinToString { it.displayName }}\n")
            append("Path: ${ModelUtils.getModelFile(this@SettingsActivity, sttModel).absolutePath}\n")
            if (sttDownloadSpecs.size > 1) {
                append("Auxiliary files:\n")
                sttDownloadSpecs.drop(1).forEach { spec ->
                    append("- ${spec.displayName}: ${if (ModelUtils.isModelReady(this@SettingsActivity, spec)) "Ready" else "Missing"}\n")
                }
            }
            append("Backend availability:\n")
            sttResolution.availabilities
                .filter { it.backend != InferenceBackend.AUTO }
                .forEach { availability ->
                    append("- ${availability.backend.displayName}: ${if (availability.available) "Available" else "Unavailable"}")
                    if (!availability.reason.isNullOrBlank()) {
                        append(" (${availability.reason})")
                    }
                    append('\n')
                }
            append("Notes: ${sttModel.description}")
        }.trim()

        binding.tvLlamaPath.text = buildString {
            append("LLM model: ${llmModel.displayName}\n")
            append("Status: ${if (llmReady) "Ready" else "Missing"}\n")
            append("Requested backend: ${llmResolution.requestedBackend.displayName}\n")
            append("Effective backend: ${llmResolution.effectiveBackend.displayName}\n")
            append("Engine: ${llmModel.engineKind}\n")
            append("Supported backends: ${llmModel.supportedBackends.joinToString { it.displayName }}\n")
            append("Experimental: ${if (llmModel.experimental) "Yes" else "No"}\n")
            append("Path: ${ModelUtils.getModelFile(this@SettingsActivity, llmModel).absolutePath}\n")
            append("Backend availability:\n")
            llmResolution.availabilities
                .filter { it.backend != InferenceBackend.AUTO }
                .forEach { availability ->
                    append("- ${availability.backend.displayName}: ${if (availability.available) "Available" else "Unavailable"}")
                    if (!availability.reason.isNullOrBlank()) {
                        append(" (${availability.reason})")
                    }
                    append('\n')
                }
            append("Notes: ${llmModel.description}\n")
            append("Missing selected files:\n$missingNames")
        }.trim()
    }

    private fun bindModelSpinner(
        pipeline: InferencePipeline,
        spinnerAdapter: android.widget.Spinner,
        selectedModelId: String,
    ) {
        val models = ModelUtils.getSelectableModels(pipeline)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAdapter.adapter = adapter
        val selectedIndex = models.indexOfFirst { it.id == selectedModelId }.coerceAtLeast(0)
        spinnerAdapter.setSelection(selectedIndex, false)
    }

    private fun applyBackendSelection(
        group: android.widget.RadioGroup,
        backend: InferenceBackend,
    ) {
        group.check(
            when (backend) {
                InferenceBackend.AUTO -> if (group === binding.radioGroupSttBackend) binding.radioSttBackendAuto.id else binding.radioLlmBackendAuto.id
                InferenceBackend.CPU -> if (group === binding.radioGroupSttBackend) binding.radioSttBackendCpu.id else binding.radioLlmBackendCpu.id
                InferenceBackend.GPU -> if (group === binding.radioGroupSttBackend) binding.radioSttBackendGpu.id else binding.radioLlmBackendGpu.id
                InferenceBackend.NPU -> if (group === binding.radioGroupSttBackend) binding.radioSttBackendNpu.id else binding.radioLlmBackendCpu.id
            }
        )
    }

    private fun readBackendSelection(group: android.widget.RadioGroup): InferenceBackend {
        return when (group.checkedRadioButtonId) {
            binding.radioSttBackendCpu.id,
            binding.radioLlmBackendCpu.id -> InferenceBackend.CPU

            binding.radioSttBackendGpu.id,
            binding.radioLlmBackendGpu.id -> InferenceBackend.GPU

            binding.radioSttBackendNpu.id -> InferenceBackend.NPU

            else -> InferenceBackend.AUTO
        }
    }

    private fun applyBackendAvailabilityState(
        availabilities: List<com.voicelog.util.BackendAvailability>,
        isStt: Boolean,
    ) {
        val availabilityByBackend = availabilities.associateBy { it.backend }
        fun isAvailable(backend: InferenceBackend): Boolean =
            availabilityByBackend[backend]?.available == true

        if (isStt) {
            binding.radioSttBackendCpu.isEnabled = isAvailable(InferenceBackend.CPU)
            binding.radioSttBackendGpu.isEnabled = isAvailable(InferenceBackend.GPU)
            binding.radioSttBackendNpu.isEnabled = isAvailable(InferenceBackend.NPU)
        } else {
            binding.radioLlmBackendCpu.isEnabled = isAvailable(InferenceBackend.CPU)
            binding.radioLlmBackendGpu.isEnabled = isAvailable(InferenceBackend.GPU)
        }
    }
}
