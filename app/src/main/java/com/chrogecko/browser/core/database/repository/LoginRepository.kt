package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.LoginDao
import com.chrogecko.browser.core.database.entity.LoginEntity
import com.chrogecko.browser.core.security.SecretBox
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A login as the UI may see it: no decrypted password until it is actually filled in. */
data class SavedLogin(
    val id: Long,
    val host: String,
    val username: String,
)

@Singleton
class LoginRepository @Inject constructor(private val dao: LoginDao) {

    val logins: Flow<List<SavedLogin>> = dao.observeAll().map { rows ->
        rows.map { SavedLogin(it.id, it.host, it.username) }
    }

    suspend fun forHost(host: String): List<SavedLogin> =
        dao.forHost(host).map { SavedLogin(it.id, it.host, it.username) }

    /** The decrypted password, or null when the keystore entry is gone. */
    suspend fun password(host: String, username: String): String? =
        dao.find(host, username)?.let { entity ->
            withContext(Dispatchers.IO) { SecretBox.decrypt(entity.secret) }
        }

    suspend fun password(id: Long): String? =
        dao.byId(id)?.let { entity ->
            withContext(Dispatchers.IO) { SecretBox.decrypt(entity.secret) }
        }

    suspend fun save(host: String, username: String, password: String) {
        if (host.isBlank() || username.isBlank() || password.isEmpty()) return
        val existing = dao.find(host, username)
        val encrypted = withContext(Dispatchers.IO) { SecretBox.encrypt(password) }
        dao.upsert(
            LoginEntity(
                id = existing?.id ?: 0,
                host = host,
                username = username,
                secret = encrypted,
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteHost(host: String) = dao.forHost(host).forEach { dao.delete(it.id) }

    suspend fun clear() = dao.clear()

    suspend fun count(): Int = dao.count()
}
