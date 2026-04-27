package com.voicelog.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voicelog.db.dao.RecordingDao
import com.voicelog.db.entity.Recording
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecordingDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recordingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_andGetById_returnsInserted() = runBlocking {
        val id = dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        val rec = dao.getById(id)

        assertNotNull(rec)
        assertEquals("/a.wav", rec!!.filePath)
        assertEquals("pending", rec.status)
    }

    @Test
    fun getPendingTranscriptionRecordings_returnsPendingAndQueuedOnly() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "pending"))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60, status = "queued"))
        dao.insert(Recording(filePath = "/c.wav", startedAt = 3000L, durationSec = 60, status = "done"))

        val pending = dao.getPendingTranscriptionRecordings()

        assertEquals(2, pending.size)
        assertEquals("/a.wav", pending[0].filePath)
        assertEquals("/b.wav", pending[1].filePath)
    }

    @Test
    fun getPendingSummaryRecordings_returnsTranscriptReadyOnly() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "pending"))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60, status = "transcript_ready"))
        dao.insert(Recording(filePath = "/c.wav", startedAt = 3000L, durationSec = 60, status = "summarizing"))

        val pending = dao.getPendingSummaryRecordings()

        assertEquals(1, pending.size)
        assertEquals("/b.wav", pending[0].filePath)
    }

    @Test
    fun updateStatus_changesStatus() = runBlocking {
        val id = dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.updateStatus(id, "transcribing")

        val rec = dao.getById(id)

        assertEquals("transcribing", rec!!.status)
    }

    @Test
    fun updateSummaryText_changesSummaryText() = runBlocking {
        val id = dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.updateSummaryText(id, "hello summary")

        val rec = dao.getById(id)

        assertEquals("hello summary", rec!!.summaryText)
    }

    @Test
    fun markPendingAsQueued_updatesOnlyPendingRows() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "pending"))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60, status = "queued"))
        dao.insert(Recording(filePath = "/c.wav", startedAt = 3000L, durationSec = 60, status = "done"))

        val updated = dao.markPendingAsQueued()
        val all = dao.getAllRecordings().first()

        assertEquals(1, updated)
        assertEquals(listOf("done", "queued", "queued"), all.map { it.status })
    }

    @Test
    fun resetInFlightStatuses_restoresExpectedStates() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "queued"))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60, status = "transcribing"))
        dao.insert(Recording(filePath = "/c.wav", startedAt = 3000L, durationSec = 60, status = "summarizing"))
        dao.insert(Recording(filePath = "/d.wav", startedAt = 4000L, durationSec = 60, status = "done"))

        dao.resetInFlightStatuses()

        val all = dao.getAllRecordings().first()
        assertEquals(listOf("done", "transcript_ready", "pending", "pending"), all.map { it.status })
    }

    @Test
    fun resetToPending_updatesOnlyRequestedRows() = runBlocking {
        val first = dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "done"))
        val second = dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60, status = "done"))

        dao.resetToPending(listOf(first))

        assertEquals("pending", dao.getById(first)!!.status)
        assertEquals("done", dao.getById(second)!!.status)
    }

    @Test
    fun hasAnyWithStatuses_checksPresence() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60, status = "done"))

        assertTrue(dao.hasAnyWithStatuses(listOf("done", "pending")))
        assertTrue(!dao.hasAnyWithStatuses(listOf("queued", "transcribing")))
    }

    @Test
    fun getAllRecordings_flow_returnsAllOrderedByStartedAtDesc() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 3000L, durationSec = 60))

        val all = dao.getAllRecordings().first()

        assertEquals(2, all.size)
        assertEquals("/b.wav", all[0].filePath)
        assertEquals("/a.wav", all[1].filePath)
    }

    @Test
    fun getExpiredRecordings_returnsOnlyDoneAndExpired() = runBlocking {
        val oldDone = dao.insert(Recording(filePath = "/old.wav", startedAt = 1000L, durationSec = 60, status = "done"))
        dao.insert(Recording(filePath = "/new.wav", startedAt = System.currentTimeMillis(), durationSec = 60, status = "done"))
        dao.insert(Recording(filePath = "/pending.wav", startedAt = 1000L, durationSec = 60, status = "pending"))

        val expired = dao.getExpiredRecordings(System.currentTimeMillis() - 1000L)

        assertEquals(1, expired.size)
        assertEquals(oldDone, expired[0].id)
    }

    @Test
    fun deleteExpiredRecordings_removesOnlyDoneAndExpired() = runBlocking {
        val oldDone = dao.insert(Recording(filePath = "/old.wav", startedAt = 1000L, durationSec = 60, status = "done"))
        val newDone = dao.insert(Recording(filePath = "/new.wav", startedAt = System.currentTimeMillis(), durationSec = 60, status = "done"))
        val pending = dao.insert(Recording(filePath = "/pending.wav", startedAt = 1000L, durationSec = 60, status = "pending"))

        dao.deleteExpiredRecordings(System.currentTimeMillis() - 1000L)

        val all = dao.getAllRecordings().first()
        assertEquals(2, all.size)
        assertNull(dao.getById(oldDone))
        assertNotNull(dao.getById(newDone))
        assertNotNull(dao.getById(pending))
    }

    @Test
    fun insert_emptyList_getAllReturnsEmpty() = runBlocking {
        val all = dao.getAllRecordings().first()
        assertTrue(all.isEmpty())
    }
}
