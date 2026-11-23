package com.chrogecko.browser.core.database.repository

import android.content.Context
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.dao.AutofillDao
import com.chrogecko.browser.core.database.entity.AddressEntity
import com.chrogecko.browser.core.database.entity.CardEntity
import com.chrogecko.browser.core.security.SecretBox
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A card as the UI may see it: only the last four digits leave the keystore. */
data class SavedCard(
    val id: Long,
    val label: String,
    val nameOnCard: String,
    val last4: String,
    val expiry: String,
)

/** A filled-in suggestion chip. */
data class AutofillOption(
    val id: Long,
    val kind: String,
    val label: String,
)

@Singleton
class AutofillRepository @Inject constructor(
    private val dao: AutofillDao,
    @param:ApplicationContext private val context: Context,
) {

    val addresses: Flow<List<AddressEntity>> = dao.observeAddresses()

    val cards: Flow<List<SavedCard>> = dao.observeCards().map { rows ->
        rows.map { SavedCard(it.id, it.label, it.nameOnCard, last4(SecretBox.decrypt(it.number)), it.expiry) }
    }.flowOn(Dispatchers.IO)

    suspend fun address(id: Long): AddressEntity? = dao.address(id)

    suspend fun saveAddress(entity: AddressEntity) {
        dao.upsertAddress(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveCard(label: String, nameOnCard: String, number: String, expiry: String, id: Long = 0) {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 12) return
        val encrypted = withContext(Dispatchers.IO) { SecretBox.encrypt(digits) }
        dao.upsertCard(
            CardEntity(
                id = id,
                label = label.ifBlank { context.getString(R.string.default_card_label) },
                nameOnCard = nameOnCard,
                number = encrypted,
                expiry = expiry,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteAddress(id: Long) = dao.deleteAddress(id)

    suspend fun deleteCard(id: Long) = dao.deleteCard(id)

    /** Field token → value, matching the keys the page content script understands. */
    suspend fun addressValues(id: Long): Map<String, String> {
        val address = dao.address(id) ?: return emptyMap()
        return buildMap {
            put("name", address.name)
            put("street", address.street)
            put("city", address.city)
            put("postal", address.postal)
            put("country", address.country)
            put("phone", address.phone)
            put("email", address.email)
        }.filterValues { it.isNotBlank() }
    }

    suspend fun cardValues(id: Long): Map<String, String> {
        val card = dao.card(id) ?: return emptyMap()
        val number = withContext(Dispatchers.IO) { SecretBox.decrypt(card.number) } ?: return emptyMap()
        return mapOf(
            "cc-name" to card.nameOnCard,
            "cc-number" to number,
            "cc-exp" to card.expiry,
        ).filterValues { it.isNotBlank() }
    }

    private fun last4(number: String?): String = number?.takeLast(4).orEmpty()
}
