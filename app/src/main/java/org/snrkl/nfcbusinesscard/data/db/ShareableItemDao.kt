package org.snrkl.nfcbusinesscard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ShareableItemDao {

    @Transaction
    @Query("SELECT * FROM shareable_items ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<ShareableItemWithChildren>>

    @Transaction
    @Query("SELECT * FROM shareable_items WHERE id = :id")
    fun observeById(id: Long): Flow<ShareableItemWithChildren?>

    /**
     * One-shot read for the HCE service.
     *
     * The card-emulation path is not a UI observer — it needs the current payload
     * once, synchronously with respect to the APDU it is answering, not a stream.
     */
    @Transaction
    @Query("SELECT * FROM shareable_items WHERE id = :id")
    suspend fun getById(id: Long): ShareableItemWithChildren?

    @Transaction
    @Query("SELECT * FROM shareable_items ORDER BY position ASC, id ASC LIMIT 1")
    suspend fun getFirst(): ShareableItemWithChildren?

    @Query("SELECT COUNT(*) FROM shareable_items")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM shareable_items")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: ShareableItemEntity): Long

    @Update
    suspend fun updateItem(item: ShareableItemEntity)

    @Delete
    suspend fun deleteItem(item: ShareableItemEntity)

    @Query("DELETE FROM shareable_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("UPDATE shareable_items SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhones(phones: List<PhoneNumberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailAddressEntity>)

    @Query("DELETE FROM phone_numbers WHERE item_id = :itemId")
    suspend fun deletePhonesForItem(itemId: Long)

    @Query("DELETE FROM email_addresses WHERE item_id = :itemId")
    suspend fun deleteEmailsForItem(itemId: Long)

    /**
     * Replaces an item and its children as one unit.
     *
     * The children are deleted and reinserted rather than diffed. The lists are
     * tiny — a handful of numbers at most — and a diff would be a correctness
     * risk out of all proportion to the saving. Wrapping it in `@Transaction`
     * means a failure part-way through cannot leave a card with the old phone
     * numbers and the new emails.
     */
    @Transaction
    suspend fun upsertWithChildren(
        item: ShareableItemEntity,
        phones: List<PhoneNumberEntity>,
        emails: List<EmailAddressEntity>,
    ): Long {
        val itemId = if (item.id == 0L) {
            insertItem(item)
        } else {
            updateItem(item)
            deletePhonesForItem(item.id)
            deleteEmailsForItem(item.id)
            item.id
        }
        if (phones.isNotEmpty()) {
            insertPhones(phones.map { it.copy(id = 0L, itemId = itemId) })
        }
        if (emails.isNotEmpty()) {
            insertEmails(emails.map { it.copy(id = 0L, itemId = itemId) })
        }
        return itemId
    }

    /**
     * Renumbers the library to match [orderedIds].
     *
     * Positions are rewritten from zero rather than nudged, so a reorder cannot
     * accumulate gaps or collisions over time.
     */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }
}
