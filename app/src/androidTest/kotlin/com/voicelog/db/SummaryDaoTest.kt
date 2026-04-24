package com.voicelog.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voicelog.db.dao.SummaryDao
import com.voicelog.db.entity.Summary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SummaryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SummaryDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.summaryDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertOrReplace_insertsNew() = runBlocking {
        dao.insertOrReplace(Summary(date = "2026-04-24", summaryText = "첫 요약", createdAt = 1000L))
        val s = dao.getByDate("2026-04-24")
        assertNotNull(s)
        assertEquals("첫 요약", s!!.summaryText)
    }

    @Test
    fun insertOrReplace_replacesSameDate() = runBlocking {
        dao.insertOrReplace(Summary(date = "2026-04-24", summaryText = "이전 요약", createdAt = 1000L))
        dao.insertOrReplace(Summary(date = "2026-04-24", summaryText = "새 요약", createdAt = 2000L))
        val all = dao.getAllSummaries().first()
        assertEquals(1, all.size)
        assertEquals("새 요약", all[0].summaryText)
    }

    @Test
    fun getByDate_nonExistent_returnsNull() = runBlocking {
        assertNull(dao.getByDate("1900-01-01"))
    }

    @Test
    fun getAllSummaries_orderedByDateDesc() = runBlocking {
        dao.insertOrReplace(Summary(date = "2026-04-22", summaryText = "A", createdAt = 1000L))
        dao.insertOrReplace(Summary(date = "2026-04-24", summaryText = "C", createdAt = 3000L))
        dao.insertOrReplace(Summary(date = "2026-04-23", summaryText = "B", createdAt = 2000L))
        val all = dao.getAllSummaries().first()
        assertEquals(3, all.size)
        assertEquals("2026-04-24", all[0].date)
        assertEquals("2026-04-23", all[1].date)
        assertEquals("2026-04-22", all[2].date)
    }

    @Test
    fun deleteByDate_removesCorrectEntry() = runBlocking {
        dao.insertOrReplace(Summary(date = "2026-04-24", summaryText = "삭제대상", createdAt = 1000L))
        dao.insertOrReplace(Summary(date = "2026-04-23", summaryText = "유지", createdAt = 2000L))
        dao.deleteByDate("2026-04-24")
        assertNull(dao.getByDate("2026-04-24"))
        assertNotNull(dao.getByDate("2026-04-23"))
    }

    @Test
    fun getAllSummaries_empty_returnsEmptyList() = runBlocking {
        val all = dao.getAllSummaries().first()
        assertTrue(all.isEmpty())
    }
}
