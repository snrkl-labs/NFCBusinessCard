package org.snrkl.nfcbusinesscard.data.db

import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * Translation between the Room rows and the domain model.
 *
 * The domain model is what the UI, the NDEF encoder and the tier selector all
 * speak. Keeping the storage shape out of those layers is what allows the
 * encoder to stay pure Kotlin and therefore unit-testable without a device.
 */

internal fun ShareableItemWithChildren.toDomain(): ShareableItem = ShareableItem(
    id = item.id,
    label = item.label,
    type = item.type,
    position = item.position,
    url = item.url,
    vCard = if (item.type == ShareableItemType.VCard) {
        val columns = item.vCard ?: VCardColumns()
        VCardData(
            formattedName = columns.formattedName,
            givenName = columns.givenName,
            familyName = columns.familyName,
            organisation = columns.organisation,
            jobTitle = columns.jobTitle,
            phones = phones.sortedBy { it.position }
                .map { PhoneNumber(type = it.type, value = it.value) },
            emails = emails.sortedBy { it.position }
                .map { EmailAddress(type = it.type, value = it.value) },
            url = columns.url,
            address = columns.address,
            note = columns.note,
            photoUri = columns.photoUri,
        )
    } else {
        null
    },
)

internal fun ShareableItem.toEntity(): ShareableItemEntity = ShareableItemEntity(
    id = id,
    label = label,
    type = type,
    position = position,
    url = url?.takeIf { type == ShareableItemType.Url },
    vCard = vCard?.let {
        VCardColumns(
            formattedName = it.formattedName,
            givenName = it.givenName,
            familyName = it.familyName,
            organisation = it.organisation,
            jobTitle = it.jobTitle,
            url = it.url,
            address = it.address,
            note = it.note,
            photoUri = it.photoUri,
        )
    },
)

/**
 * Blank entries are dropped on the way to storage.
 *
 * The editor keeps an empty row around so the operator has somewhere to type;
 * persisting those would put empty `TEL:` lines into the vCard output and waste
 * QR modules on nothing.
 */
internal fun ShareableItem.phoneEntities(): List<PhoneNumberEntity> =
    vCard?.phones.orEmpty()
        .filter { it.value.isNotBlank() }
        .mapIndexed { index, phone ->
            PhoneNumberEntity(
                itemId = id,
                type = phone.type,
                value = phone.value.trim(),
                position = index,
            )
        }

internal fun ShareableItem.emailEntities(): List<EmailAddressEntity> =
    vCard?.emails.orEmpty()
        .filter { it.value.isNotBlank() }
        .mapIndexed { index, email ->
            EmailAddressEntity(
                itemId = id,
                type = email.type,
                value = email.value.trim(),
                position = index,
            )
        }
