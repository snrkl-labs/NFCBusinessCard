package org.snrkl.nfcbusinesscard.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType

/**
 * A Shareable Item row.
 *
 * URL and vCard items share one table. The alternative — a table per type —
 * would buy nothing: the columns a URL item leaves null are cheap, and a single
 * table keeps `position` a total ordering across the whole library, which is
 * what the reorder affordance in settings needs.
 */
@Entity(
    tableName = "shareable_items",
    indices = [Index("position")],
)
data class ShareableItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val label: String,
    val type: ShareableItemType,
    val position: Int,

    /** Set for [ShareableItemType.Url] items. */
    val url: String? = null,

    @Embedded(prefix = "vcard_")
    val vCard: VCardColumns? = null,
)

/**
 * The scalar vCard fields, embedded into the item row.
 *
 * Phones and emails are one-to-many and live in their own tables; everything
 * else on a contact card is single-valued and belongs here.
 */
data class VCardColumns(
    @ColumnInfo(name = "formatted_name") val formattedName: String = "",
    @ColumnInfo(name = "given_name") val givenName: String = "",
    @ColumnInfo(name = "family_name") val familyName: String = "",
    val organisation: String = "",
    @ColumnInfo(name = "job_title") val jobTitle: String = "",
    val url: String = "",
    val address: String = "",
    val note: String = "",
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
)

/**
 * A phone number belonging to a contact card.
 *
 * `onDelete = CASCADE` matters: deleting an item must not leave orphaned numbers
 * behind, and doing it in the schema rather than in repository code means it
 * still holds for any future write path.
 */
@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = ShareableItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_id")],
)
data class PhoneNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val type: ContactFieldType,
    val value: String,
    /** Preserves the order the operator entered them in. */
    val position: Int,
)

/** An email address belonging to a contact card. See [PhoneNumberEntity]. */
@Entity(
    tableName = "email_addresses",
    foreignKeys = [
        ForeignKey(
            entity = ShareableItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_id")],
)
data class EmailAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val type: ContactFieldType,
    val value: String,
    val position: Int,
)

/** An item with its one-to-many children, as Room assembles it. */
data class ShareableItemWithChildren(
    @Embedded val item: ShareableItemEntity,
    @Relation(parentColumn = "id", entityColumn = "item_id")
    val phones: List<PhoneNumberEntity>,
    @Relation(parentColumn = "id", entityColumn = "item_id")
    val emails: List<EmailAddressEntity>,
)
