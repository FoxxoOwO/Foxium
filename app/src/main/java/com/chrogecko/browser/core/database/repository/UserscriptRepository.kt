package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.UserscriptDao
import com.chrogecko.browser.core.database.entity.UserscriptEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserscriptRepository @Inject constructor(private val dao: UserscriptDao) {

    val scripts: Flow<List<UserscriptEntity>> = dao.observeAll()
    val enabledScripts: Flow<List<UserscriptEntity>> = dao.observeEnabled()

    suspend fun forInjection(): List<UserscriptEntity> = dao.enabledScripts()

    suspend fun find(id: String): UserscriptEntity? = dao.findById(id)

    /** Installs or updates by (namespace, name), keeping the original id and enable state. */
    suspend fun install(script: UserscriptEntity): UserscriptEntity {
        val existing = dao.findByNamespaceAndName(script.namespace, script.name)
        val merged = if (existing == null) {
            script
        } else {
            script.copy(id = existing.id, enabled = existing.enabled, installedAt = existing.installedAt)
        }
        dao.upsert(merged)
        return merged
    }

    /** False when a different script already owns (namespace, name): REPLACE would delete it. */
    suspend fun save(script: UserscriptEntity): Boolean {
        val conflict = dao.findByNamespaceAndName(script.namespace, script.name)
        if (conflict != null && conflict.id != script.id) return false
        dao.upsert(script.copy(updatedAt = System.currentTimeMillis()))
        return true
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
