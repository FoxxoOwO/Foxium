package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.TextSnippetDao
import com.chrogecko.browser.core.database.entity.TextSnippetEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TextSnippetRepository @Inject constructor(private val dao: TextSnippetDao) {

    val snippets: Flow<List<TextSnippetEntity>> = dao.observeAll()

    /**
     * Triggers are unique. When a different snippet already owns the trigger, update that row in
     * place instead of REPLACE-ing it: Room's REPLACE would delete the old row (and its enabled
     * state and id) and insert a new one, which silently destroys the other snippet.
     */
    suspend fun save(id: Long, trigger: String, expansion: String): Long {
        val clean = trigger.trim()
        if (clean.isEmpty() || clean.any { it.isWhitespace() }) return 0
        if (expansion.isEmpty()) return 0
        val conflict = dao.findByTrigger(clean)
        if (conflict != null && conflict.id != id) {
            // Reject the edit: overwriting the *other* row's expansion made the user's change
            // vanish while corrupting an unrelated snippet. -1 distinguishes this from validation.
            return -1
        }
        val existingEnabled = if (id != 0L) dao.findById(id)?.enabled ?: true else true
        return dao.upsert(
            TextSnippetEntity(
                id = id,
                trigger = clean,
                expansion = expansion,
                enabled = existingEnabled,
            ),
        )
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) =
        dao.setEnabled(id, enabled, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)
}
