package org.snrkl.nfcbusinesscard.data.db

import androidx.room.TypeConverter
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType

/**
 * Enum persistence.
 *
 * Enums are stored by `name`, not by ordinal. Ordinals are positional, so
 * inserting a case into the middle of an enum silently reinterprets every
 * existing row — a corruption that shows up much later as the wrong item type.
 * Storing the name costs a few bytes and makes reordering harmless.
 *
 * Unknown names fall back to a sensible default rather than throwing: a row
 * written by a newer build should degrade, not crash the app on open.
 */
internal class Converters {

    @TypeConverter
    fun shareableItemTypeToString(value: ShareableItemType): String = value.name

    @TypeConverter
    fun stringToShareableItemType(value: String): ShareableItemType =
        ShareableItemType.entries.firstOrNull { it.name == value } ?: ShareableItemType.Url

    @TypeConverter
    fun contactFieldTypeToString(value: ContactFieldType): String = value.name

    @TypeConverter
    fun stringToContactFieldType(value: String): ContactFieldType =
        ContactFieldType.entries.firstOrNull { it.name == value } ?: ContactFieldType.Other
}
