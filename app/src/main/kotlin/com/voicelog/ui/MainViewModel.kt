package com.voicelog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicelog.db.AppDatabase
import com.voicelog.ui.adapter.RecordingUiItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dateFmt = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA)
    private val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    val uiItems = db.recordingDao().getAllRecordings()
        .map { recordings ->
            val grouped = recordings.groupBy { dateKeyFmt.format(Date(it.startedAt)) }
            val result = mutableListOf<RecordingUiItem>()
            for ((_, recs) in grouped) {
                val label = dateFmt.format(Date(recs.first().startedAt))
                result.add(RecordingUiItem.Header(label))
                for (rec in recs) {
                    val transcript = db.transcriptDao().getByRecordingId(rec.id)
                    result.add(RecordingUiItem.RecordingItem(rec, transcript))
                }
            }
            result.toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
