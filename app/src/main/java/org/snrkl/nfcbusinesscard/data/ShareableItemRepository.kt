package org.snrkl.nfcbusinesscard.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.snrkl.nfcbusinesscard.data.db.ShareableItemDao
import org.snrkl.nfcbusinesscard.data.db.emailEntities
import org.snrkl.nfcbusinesscard.data.db.phoneEntities
import org.snrkl.nfcbusinesscard.data.db.toDomain
import org.snrkl.nfcbusinesscard.data.db.toEntity
import org.snrkl.nfcbusinesscard.data.model.ShareableItem

/**
 * The Shareable Item library.
 *
 * The single source of truth for items. The UI observes it and the HCE service
 * reads through it, so the two can never disagree about what the Active Item is.
 */
internal class ShareableItemRepository(private val dao: ShareableItemDao) {

    val items: Flow<List<ShareableItem>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeItem(id: Long): Flow<ShareableItem?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getItem(id: Long): ShareableItem? = dao.getById(id)?.toDomain()

    suspend fun getFirstItem(): ShareableItem? = dao.getFirst()?.toDomain()

    suspend fun count(): Int = dao.count()

    /**
     * Inserts or updates an item and returns its id.
     *
     * A new item is appended to the end of the library rather than inserted at
     * the top: the operator's existing ordering is a deliberate arrangement and
     * adding a second item should not displace the first one they chose.
     */
    suspend fun save(item: ShareableItem): Long {
        val positioned = if (item.id == 0L) {
            item.copy(position = dao.maxPosition() + 1)
        } else {
            item
        }
        return dao.upsertWithChildren(
            item = positioned.toEntity(),
            phones = positioned.phoneEntities(),
            emails = positioned.emailEntities(),
        )
    }

    suspend fun delete(id: Long) = dao.deleteItemById(id)

    suspend fun applyOrder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)
}
