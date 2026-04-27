package com.voicelog.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voicelog.db.dao.RecordingDao
import com.voicelog.db.dao.TranscriptDao
import com.voicelog.db.entity.Recording
import com.voicelog.db.entity.Transcript
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TranscriptDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var transcriptDao: TranscriptDao
    private lateinit var recordingDao: RecordingDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transcriptDao = db.transcriptDao()
        recordingDao = db.recordingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_andGetById_returnsInserted() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        val transcriptId = transcriptDao.insert(Transcript(recordingId = recId, text = "hello", createdAt = 1000L))

        val transcript = transcriptDao.getById(transcriptId)

        assertNotNull(transcript)
        assertEquals("hello", transcript!!.text)
        assertEquals(recId, transcript.recordingId)
    }

    @Test
    fun getByRecordingId_returnsCorrectTranscript() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = recId, text = "test", createdAt = 2000L))

        val transcript = transcriptDao.getByRecordingId(recId)

        assertNotNull(transcript)
        assertEquals("test", transcript!!.text)
    }

    @Test
    fun getByRecordingId_noTranscript_returnsNull() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        assertNull(transcriptDao.getByRecordingId(recId))
    }

    @Test
    fun deleteByRecordingIds_removesRequestedRows() = runBlocking {
        val first = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        val second = recordingDao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60))
        val firstTranscript = transcriptDao.insert(Transcript(recordingId = first, text = "first", createdAt = 1000L))
        val secondTranscript = transcriptDao.insert(Transcript(recordingId = second, text = "second", createdAt = 2000L))

        transcriptDao.deleteByRecordingIds(listOf(first))

        assertNull(transcriptDao.getById(firstTranscript))
        assertNotNull(transcriptDao.getById(secondTranscript))
    }

    @Test
    fun cascadeDelete_whenRecordingDeleted() = runBlocking {
        val recId = recordingDao.insert(
            Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "done")
        )
        val transcriptId = transcriptDao.insert(
            Transcript(recordingId = recId, text = "delete with parent", createdAt = 1000L)
        )

        recordingDao.deleteExpiredRecordings(System.currentTimeMillis() + 1000L)

        assertNull(transcriptDao.getById(transcriptId))
    }

    @Test
    fun getTranscriptsByDate_returnsCorrectDate() = runBlocking {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val today = System.currentTimeMillis()
        val todayStr = dateFormat.format(Date(today))

        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = today, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = recId, text = "today text", createdAt = today))

        val results = transcriptDao.getTranscriptsByDate(todayStr)

        assertEquals(1, results.size)
        assertEquals("today text", results[0].text)
    }

    @Test
    fun getTranscriptsByDate_wrongDate_returnsEmpty() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = recId, text = "content", createdAt = 1000L))

        val results = transcriptDao.getTranscriptsByDate("9999-12-31")

        assertTrue(results.isEmpty())
    }

    @Test
    fun getCorruptedRecordingIds_returnsOnlyReplacementCharacterRows() = runBlocking {
        val cleanRecording = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        val corruptedRecording = recordingDao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = cleanRecording, text = "normal text", createdAt = 1000L))
        transcriptDao.insert(Transcript(recordingId = corruptedRecording, text = "bad \uFFFD text", createdAt = 2000L))

        val corruptedIds = transcriptDao.getCorruptedRecordingIds()

        assertEquals(listOf(corruptedRecording), corruptedIds)
    }
}
