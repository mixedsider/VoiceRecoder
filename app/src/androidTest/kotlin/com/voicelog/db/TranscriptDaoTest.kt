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
import org.junit.Assert.*
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
        val tId = transcriptDao.insert(Transcript(recordingId = recId, text = "안녕하세요", createdAt = 1000L))
        val t = transcriptDao.getById(tId)
        assertNotNull(t)
        assertEquals("안녕하세요", t!!.text)
        assertEquals(recId, t.recordingId)
    }

    @Test
    fun getByRecordingId_returnsCorrectTranscript() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = recId, text = "테스트", createdAt = 2000L))
        val t = transcriptDao.getByRecordingId(recId)
        assertNotNull(t)
        assertEquals("테스트", t!!.text)
    }

    @Test
    fun getByRecordingId_noTranscript_returnsNull() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        assertNull(transcriptDao.getByRecordingId(recId))
    }

    @Test
    fun cascadeDelete_whenRecordingDeleted() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "done"))
        val tId = transcriptDao.insert(Transcript(recordingId = recId, text = "삭제될 텍스트", createdAt = 1000L))

        // 만료 삭제로 recording 제거 → transcript도 CASCADE 삭제
        recordingDao.deleteExpiredRecordings(System.currentTimeMillis() + 1000L)

        assertNull(transcriptDao.getById(tId))
    }

    @Test
    fun getTranscriptsByDate_returnsCorrectDate() = runBlocking {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val today = System.currentTimeMillis()
        val todayStr = dateFmt.format(Date(today))

        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = today, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = recId, text = "오늘 내용", createdAt = today))

        val results = transcriptDao.getTranscriptsByDate(todayStr)
        assertEquals(1, results.size)
        assertEquals("오늘 내용", results[0].text)
    }

    @Test
    fun getTranscriptsByDate_wrongDate_returnsEmpty() = runBlocking {
        val recId = recordingDao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        transcriptDao.insert(Transcript(recordingId = recId, text = "내용", createdAt = 1000L))

        val results = transcriptDao.getTranscriptsByDate("9999-12-31")
        assertTrue(results.isEmpty())
    }
}
