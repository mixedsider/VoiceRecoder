package com.voicelog.ui

import com.voicelog.db.entity.Recording
import com.voicelog.db.entity.Transcript
import com.voicelog.ui.adapter.RecordingUiItem
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModelTest {

    private val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private val dateFmt = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA)

    private fun buildUiItems(recordings: List<Recording>, transcripts: Map<Long, Transcript?>): List<RecordingUiItem> {
        val grouped = recordings.groupBy { dateKeyFmt.format(Date(it.startedAt)) }
        val result = mutableListOf<RecordingUiItem>()
        for ((_, recs) in grouped) {
            val label = dateFmt.format(Date(recs.first().startedAt))
            result.add(RecordingUiItem.Header(label))
            for (rec in recs) {
                result.add(RecordingUiItem.RecordingItem(rec, transcripts[rec.id]))
            }
        }
        return result
    }

    @Test
    fun grouping_singleRecording_producesHeaderAndItem() {
        val rec = Recording(id = 1, filePath = "/a.wav", startedAt = 1745500800000L, durationSec = 60)
        val items = buildUiItems(listOf(rec), emptyMap())
        assertEquals(2, items.size)
        assertTrue(items[0] is RecordingUiItem.Header)
        assertTrue(items[1] is RecordingUiItem.RecordingItem)
    }

    @Test
    fun grouping_twoRecordingsSameDay_oneHeader() {
        val base = 1745500800000L
        val rec1 = Recording(id = 1, filePath = "/a.wav", startedAt = base, durationSec = 60)
        val rec2 = Recording(id = 2, filePath = "/b.wav", startedAt = base + 3600000L, durationSec = 60)
        val items = buildUiItems(listOf(rec1, rec2), emptyMap())
        val headers = items.filterIsInstance<RecordingUiItem.Header>()
        assertEquals(1, headers.size)
        assertEquals(3, items.size)
    }

    @Test
    fun grouping_twoDifferentDays_twoHeaders() {
        val rec1 = Recording(id = 1, filePath = "/a.wav", startedAt = 1745500800000L, durationSec = 60)
        val rec2 = Recording(id = 2, filePath = "/b.wav", startedAt = 1745500800000L + 86400000L, durationSec = 60)
        val items = buildUiItems(listOf(rec1, rec2), emptyMap())
        val headers = items.filterIsInstance<RecordingUiItem.Header>()
        assertEquals(2, headers.size)
    }

    @Test
    fun recordingItem_withTranscript_hasTranscript() {
        val rec = Recording(id = 1, filePath = "/a.wav", startedAt = 1745500800000L, durationSec = 60)
        val transcript = Transcript(id = 1, recordingId = 1, text = "안녕하세요", createdAt = 1000L)
        val items = buildUiItems(listOf(rec), mapOf(1L to transcript))
        val item = items.filterIsInstance<RecordingUiItem.RecordingItem>().first()
        assertEquals("안녕하세요", item.transcript?.text)
    }

    @Test
    fun recordingItem_withoutTranscript_hasNullTranscript() {
        val rec = Recording(id = 1, filePath = "/a.wav", startedAt = 1745500800000L, durationSec = 60)
        val items = buildUiItems(listOf(rec), emptyMap())
        val item = items.filterIsInstance<RecordingUiItem.RecordingItem>().first()
        assertNull(item.transcript)
    }

    @Test
    fun emptyRecordings_producesEmptyList() {
        val items = buildUiItems(emptyList(), emptyMap())
        assertTrue(items.isEmpty())
    }

    @Test
    fun recordingItem_statusPending_noTranscript() {
        val rec = Recording(id = 1, filePath = "/a.wav", startedAt = 1745500800000L, durationSec = 60, status = "pending")
        val items = buildUiItems(listOf(rec), emptyMap())
        val item = items.filterIsInstance<RecordingUiItem.RecordingItem>().first()
        assertEquals("pending", item.recording.status)
        assertNull(item.transcript)
    }
}
