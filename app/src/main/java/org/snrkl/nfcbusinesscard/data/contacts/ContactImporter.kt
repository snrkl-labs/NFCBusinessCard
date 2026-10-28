package org.snrkl.nfcbusinesscard.data.contacts

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.util.Log
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.vcard.VCardParser
import java.io.InputStream

/** Why an import produced nothing, so the UI can say something useful. */
internal sealed interface ImportResult {
    data class Success(val data: VCardData) : ImportResult

    /** The contact was readable but held nothing this app can present. */
    data object Empty : ImportResult

    /** The platform refused the read. See [ContactImporter] for why this happens. */
    data object PermissionDenied : ImportResult

    /** Malformed file, unreadable stream, or anything else. */
    data class Failed(val reason: String) : ImportResult
}

/**
 * Loads a contact card from the device's contacts, or from a `.vcf` file.
 *
 * **On permissions, and a mistake worth recording.**
 *
 * The system contact picker returns a URI carrying a temporary read grant. That
 * grant applies to *the URI it handed back* — and only to that URI.
 *
 * The first implementation read the lookup key from the picked URI and then
 * built a **different** one, `Contacts.CONTENT_VCARD_URI/<lookupKey>`, to fetch
 * the platform's own vCard export. That is a separate URI, outside the grant, so
 * the read was refused and the import failed with no prompt and no explanation.
 * Convenient as the vCard export would have been — the platform already knows
 * how to serialise a contact — it is not reachable without `READ_CONTACTS`.
 *
 * What does work is querying **under the granted URI itself**: appending
 * `Entity.CONTENT_DIRECTORY` to the picked contact URI returns that contact's
 * data rows, and the grant covers it. So the fields are assembled here from the
 * data rows instead.
 *
 * The upshot is unchanged and still worth having: **no `READ_CONTACTS`
 * permission is requested or declared.** Asking for the entire address book in
 * order to import one card the user just pointed at is a far larger request than
 * the feature justifies, and on a privacy-focused OS it is one a user would be
 * right to refuse.
 */
internal object ContactImporter {

    private const val TAG = "ContactImporter"

    fun fromPickedContact(context: Context, contactUri: Uri): ImportResult {
        val resolver = context.contentResolver
        Log.i(TAG, "Picked contact URI: $contactUri")

        // Under the granted URI, not derived from it. This is the whole fix.
        val entityUri = Uri.withAppendedPath(
            contactUri,
            ContactsContract.Contacts.Entity.CONTENT_DIRECTORY,
        )

        val cursor: Cursor? = try {
            resolver.query(
                entityUri,
                arrayOf(
                    ContactsContract.Contacts.Entity.MIMETYPE,
                    ContactsContract.Contacts.Entity.DATA1,
                    ContactsContract.Contacts.Entity.DATA2,
                    ContactsContract.Contacts.Entity.DATA3,
                    ContactsContract.Contacts.Entity.DATA4,
                    ContactsContract.Contacts.Entity.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )
        } catch (e: SecurityException) {
            // Logged rather than swallowed. The original version returned null
            // here, which made a permission problem indistinguishable from an
            // empty contact — and left nothing in logcat to diagnose it with.
            Log.w(TAG, "Refused read of picked contact", e)
            return ImportResult.PermissionDenied
        } catch (e: Exception) {
            Log.w(TAG, "Could not query picked contact", e)
            return ImportResult.Failed(e.javaClass.simpleName)
        }

        if (cursor == null) {
            Log.w(TAG, "Null cursor for $entityUri")
            return ImportResult.Failed("no cursor")
        }
        Log.i(TAG, "Entity query returned ${cursor.count} rows for $entityUri")

        var displayName = ""
        var givenName = ""
        var familyName = ""
        var organisation = ""
        var jobTitle = ""
        var website = ""
        var address = ""
        var note = ""
        val phones = mutableListOf<PhoneNumber>()
        val emails = mutableListOf<EmailAddress>()

        cursor.use {
            val mimeIndex = it.getColumnIndex(ContactsContract.Contacts.Entity.MIMETYPE)
            val d1 = it.getColumnIndex(ContactsContract.Contacts.Entity.DATA1)
            val d2 = it.getColumnIndex(ContactsContract.Contacts.Entity.DATA2)
            val d3 = it.getColumnIndex(ContactsContract.Contacts.Entity.DATA3)
            val d4 = it.getColumnIndex(ContactsContract.Contacts.Entity.DATA4)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.Entity.DISPLAY_NAME)

            while (it.moveToNext()) {
                if (displayName.isBlank() && nameIndex >= 0) {
                    displayName = it.getStringOrEmpty(nameIndex)
                }
                when (it.getStringOrEmpty(mimeIndex)) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        if (displayName.isBlank()) displayName = it.getStringOrEmpty(d1)
                        givenName = it.getStringOrEmpty(d2)
                        familyName = it.getStringOrEmpty(d3)
                    }

                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = it.getStringOrEmpty(d1)
                        if (number.isNotBlank()) {
                            phones += PhoneNumber(
                                type = phoneTypeOf(it.getIntOrZero(d2)),
                                value = number,
                            )
                        }
                    }

                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val addressValue = it.getStringOrEmpty(d1)
                        if (addressValue.isNotBlank()) {
                            emails += EmailAddress(
                                type = emailTypeOf(it.getIntOrZero(d2)),
                                value = addressValue,
                            )
                        }
                    }

                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        if (organisation.isBlank()) organisation = it.getStringOrEmpty(d1)
                        if (jobTitle.isBlank()) jobTitle = it.getStringOrEmpty(d4)
                    }

                    CommonDataKinds.Website.CONTENT_ITEM_TYPE ->
                        if (website.isBlank()) website = it.getStringOrEmpty(d1)

                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                        if (address.isBlank()) address = it.getStringOrEmpty(d1).replace("\n", ", ")

                    CommonDataKinds.Note.CONTENT_ITEM_TYPE ->
                        if (note.isBlank()) note = it.getStringOrEmpty(d1)
                }
            }
        }

        val photoUri = readContactPhoto(context, contactUri)

        val data = VCardData(
            formattedName = displayName.ifBlank {
                listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
            },
            givenName = givenName,
            familyName = familyName,
            organisation = organisation,
            jobTitle = jobTitle,
            phones = phones,
            emails = emails,
            url = website,
            address = address,
            note = note,
            photoUri = photoUri,
        )

        // A contact with no name and no way to reach anyone is not worth saving.
        return if (data.formattedName.isBlank() && phones.isEmpty() && emails.isEmpty()) {
            Log.w(TAG, "Contact read but held nothing presentable")
            ImportResult.Empty
        } else {
            Log.i(
                TAG,
                "Imported '${data.formattedName}': " +
                    "${phones.size} phone(s), ${emails.size} email(s)",
            )
            ImportResult.Success(data)
        }
    }

    /**
     * Reads the contact's photo into app storage.
     *
     * `openContactPhotoInputStream` with `preferHighres` gives the full-size
     * picture where one exists and falls back to the thumbnail otherwise. The
     * bytes are copied out because the source needs READ_CONTACTS to read again
     * later, so keeping the original reference would leave a dead image on the
     * next launch.
     */
    private fun readContactPhoto(context: Context, contactUri: Uri): String? = runCatching {
        ContactsContract.Contacts
            .openContactPhotoInputStream(context.contentResolver, contactUri, true)
            ?.use { CardPhotoStore.save(context, it) }
    }.onFailure { Log.w(TAG, "Could not read contact photo", it) }.getOrNull()

    /** Reads a `.vcf` chosen from storage. */
    fun fromVCardFile(context: Context, fileUri: Uri): ImportResult {
        val resolver = context.contentResolver
        val text = try {
            resolver.openInputStream(fileUri)?.use(::readText)
        } catch (e: SecurityException) {
            Log.w(TAG, "Refused read of picked file", e)
            return ImportResult.PermissionDenied
        } catch (e: Exception) {
            Log.w(TAG, "Could not read picked file", e)
            return ImportResult.Failed(e.javaClass.simpleName)
        } ?: return ImportResult.Failed("no stream")

        logPropertiesPresent(text)

        val parsed = VCardParser.parseFirst(text) ?: return ImportResult.Failed("not a vCard")

        // An embedded PHOTO is decoded and stored locally. It stays display-only
        // — a photo never goes back out over NFC or into a QR code.
        val withPhoto = VCardParser.extractPhoto(text)
            ?.let { CardPhotoStore.save(context, it) }
            ?.let { parsed.copy(photoUri = it) }
            ?: parsed

        return if (withPhoto.formattedName.isBlank() &&
            withPhoto.phones.isEmpty() &&
            withPhoto.emails.isEmpty()
        ) {
            ImportResult.Empty
        } else {
            ImportResult.Success(withPhoto)
        }
    }

    /**
     * Logs which vCard properties arrived — names only, never values.
     *
     * When an import comes back thinner than the contact that produced it, the
     * first question is whether the fields were missing from the file or dropped
     * by the parser. This answers that directly.
     *
     * Property *names* only, deliberately: dumping the file would put somebody's
     * phone number and address into logcat, where anything with log access could
     * read them. The names are enough to tell the two cases apart.
     */
    private fun logPropertiesPresent(text: String) {
        val properties = text.split("\r\n", "\n", "\r")
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0 || line.startsWith(" ") || line.startsWith("\t")) {
                    null
                } else {
                    line.substring(0, colon).substringBefore(';').substringAfter('.').uppercase()
                }
            }
            .filter { it.isNotBlank() }
        Log.i(TAG, "vCard properties received: " + properties.groupingBy { it }.eachCount())
    }

    private fun Cursor.getStringOrEmpty(index: Int): String =
        if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""

    private fun Cursor.getIntOrZero(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0

    private fun phoneTypeOf(type: Int): ContactFieldType = when (type) {
        CommonDataKinds.Phone.TYPE_MOBILE -> ContactFieldType.Mobile
        CommonDataKinds.Phone.TYPE_WORK,
        CommonDataKinds.Phone.TYPE_WORK_MOBILE,
        -> ContactFieldType.Work
        CommonDataKinds.Phone.TYPE_HOME -> ContactFieldType.Home
        else -> ContactFieldType.Other
    }

    private fun emailTypeOf(type: Int): ContactFieldType = when (type) {
        CommonDataKinds.Email.TYPE_WORK -> ContactFieldType.Work
        CommonDataKinds.Email.TYPE_HOME -> ContactFieldType.Home
        CommonDataKinds.Email.TYPE_MOBILE -> ContactFieldType.Mobile
        else -> ContactFieldType.Other
    }

    /**
     * Reads a stream as text, capped.
     *
     * A `.vcf` with an embedded photo can run to megabytes, and this app
     * discards photos anyway. 512 KB is far beyond any card's text content.
     */
    private fun readText(stream: InputStream): String {
        val limit = 512 * 1024
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val count = stream.read(buffer, read, limit - read)
            if (count <= 0) break
            read += count
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }
}
