package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.SiteDarkRuleDao
import com.chrogecko.browser.core.database.entity.DarkModeMode
import com.chrogecko.browser.core.database.entity.SiteDarkRuleEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DarkModeRepository @Inject constructor(private val dao: SiteDarkRuleDao) {

    val rules: Flow<List<SiteDarkRuleEntity>> = dao.observeAll()

    /** Explicit rule for a domain, or null when the site has no override. */
    suspend fun explicitMode(domain: String): DarkModeMode? = dao.findByDomain(domain)?.mode

    /** Engine used for a site when the user has not chosen one yet. */
    suspend fun modeFor(domain: String): DarkModeMode =
        explicitMode(domain) ?: DarkModeMode.INVERT

    suspend fun setMode(domain: String, mode: DarkModeMode) {
        if (domain.isBlank()) return
        dao.upsert(SiteDarkRuleEntity(domain = domain, mode = mode))
    }

    suspend fun toggle(
        domain: String,
        globallyEnabled: Boolean,
        preferredEngine: DarkModeMode = DarkModeMode.INVERT,
    ): DarkModeMode {
        val activeMode = if (preferredEngine == DarkModeMode.DYNAMIC) {
            DarkModeMode.DYNAMIC
        } else {
            DarkModeMode.INVERT
        }
        // With the master switch off a site starts as off; with it on it starts as activeMode.
        val current = explicitMode(domain) ?: if (globallyEnabled) activeMode else DarkModeMode.OFF
        val next = if (current == DarkModeMode.OFF) activeMode else DarkModeMode.OFF
        setMode(domain, next)
        return next
    }

    suspend fun reset(domain: String) = dao.deleteByDomain(domain)
}
