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
import org.junit.Assert.*
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
    fun getPendingRecordings_returnsOnlyPending() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        val id2 = dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60))
        dao.updateStatus(id2, "done")

        val pending = dao.getPendingRecordings()
        assertEquals(1, pending.size)
        assertEquals("/a.wav", pending[0].filePath)
    }

    @Test
    fun getPendingRecordings_orderedByStartedAt() = runBlocking {
        dao.insert(Recording(filePath = "/b.wav", startedAt = 3000L, durationSec = 60))
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.insert(Recording(filePath = "/c.wav", startedAt = 2000L, durationSec = 60))

        val pending = dao.getPendingRecordings()
        assertEquals(3, pending.size)
        assertEquals("/a.wav", pending[0].filePath)
        assertEquals("/c.wav", pending[1].filePath)
        assertEquals("/b.wav", pending[2].filePath)
    }

    @Test
    fun updateStatus_changesStatus() = runBlocking {
        val id = dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.updateStatus(id, "transcribing")
        val rec = dao.getById(id)
        assertEquals("transcribing", rec!!.status)
    }

    @Test
    fun getAllRecordings_flow_returnsAll() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 2000L, durationSec = 60))
        val all = dao.getAllRecordings().first()
        assertEquals(2, all.size)
    }

    @Test
    fun getAllRecordings_orderedByStartedAtDesc() = runBlocking {
        dao.insert(Recording(filePath = "/a.wav", startedAt = 1000L, durationSec = 60))
        dao.insert(Recording(filePath = "/b.wav", startedAt = 3000L, durationSec = 60))
        val all = dao.getAllRecordings().first()
        assertEquals("/b.wav", all[0].filePath)
        assertEquals("/a.wav", all[1].filePath)
    }

    @Test
    fun deleteExpiredRecordings_removesOnlyDoneAndExpired() = runBlocking {
        val idOldDone = dao.insert(Recording(filePath = "/old.wav", startedAt = 1000L, durationSec = 60))
        dao.updateStatus(idOldDone, "done")
        val idNewDone = dao.insert(Recording(filePath = "/new.wav", startedAt = System.currentTimeMillis(), durationSec = 60))
        dao.updateStatus(idNewDone, "done")
        val idPending = dao.insert(Recording(filePath = "/pending.wav", startedAt = 1000L, durationSec = 60))

        val cutoff = System.currentTimeMillis() - 1000L
        dao.deleteExpiredRecordings(cutoff)

        val all = dao.getAllRecordings().first()
        // old+done 삭제됨, new+done 및 pending 남아있음
        assertEquals(2, all.size)
        assertNull(dao.getById(idOldDone))
        assertNotNull(dao.getById(idNewDone))
        assertNotNull(dao.getById(idPending))
    }

    @Test
    fun insert_emptyList_getAllReturnsEmpty() = runBlocking {
        val all = dao.getAllRecordings().first()
        assertTrue(all.isEmpty())
    }
}
