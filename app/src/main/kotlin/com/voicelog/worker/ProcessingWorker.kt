package com.voicelog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Plan 4에서 구현: Whisper STT + LLaMA 요약
        return Result.success()
    }
}
