package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.SiteRuleDao
import com.chrogecko.browser.core.database.entity.SiteRuleEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SiteRuleRepository @Inject constructor(private val dao: SiteRuleDao) {

    val rules: Flow<List<SiteRuleEntity>> = dao.observeAll()

    suspend fun set(
        host: String,
        blockJavascript: Boolean,
        desktopSite: Boolean,
        readerMode: Boolean,
    ) {
        if (host.isBlank()) return
        dao.upsert(SiteRuleEntity(host, blockJavascript, desktopSite, readerMode))
    }

    suspend fun delete(host: String) = dao.delete(host)
}
