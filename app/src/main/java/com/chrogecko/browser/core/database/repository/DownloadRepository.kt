package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.DownloadDao
import com.chrogecko.browser.core.database.entity.DownloadEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DownloadRepository @Inject constructor(private val dao: DownloadDao) {

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun find(id: Long): DownloadEntity? = dao.findById(id)

    suspend fun start(url: String, fileName: String, mimeType: String): Long =
        dao.insert(DownloadEntity(url = url, fileName = fileName, mimeType = mimeType))

    suspend fun completed(id: Long, size: Long, path: String) = dao.markCompleted(id, size, path)

    suspend fun failed(id: Long, message: String) = dao.markFailed(id, message)

    suspend fun progress(id: Long, written: Long, total: Long) = dao.markProgress(id, written, total)

    suspend fun cancelled(id: Long) = dao.markCancelled(id)

    suspend fun all(): List<DownloadEntity> = dao.all()

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.clear()
}
