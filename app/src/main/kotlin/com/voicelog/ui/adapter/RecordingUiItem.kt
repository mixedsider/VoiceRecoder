package com.voicelog.ui.adapter

import com.voicelog.db.entity.Recording
import com.voicelog.db.entity.Summary
import com.voicelog.db.entity.Transcript

sealed class RecordingUiItem {
    data class Header(val dateLabel: String) : RecordingUiItem()
    data class RecordingItem(
        val recording: Recording,
        val transcript: Transcript?
    ) : RecordingUiItem()
    data class SummaryItem(val summary: Summary) : RecordingUiItem()
}
