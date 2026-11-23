package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.HistoryDao
import com.chrogecko.browser.core.database.entity.HistoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {

    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    fun search(query: String): Flow<List<HistoryEntity>> = dao.search(query)

    suspend fun record(url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        val now = System.currentTimeMillis()
        dao.recordVisit(url = url, title = title.ifBlank { url }, visitedAt = now)
    }

    suspend fun clear() = dao.clear()

    suspend fun deleteOlderThan(before: Long) = dao.deleteOlderThan(before)
}
