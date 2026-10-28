package org.snrkl.nfcbusinesscard.data.model

/**
 * The type of payload a [ShareableItem] carries.
 *
 * Names follow the project's naming conventions — `ShareableItemType.Url` and
 * `ShareableItemType.VCard`.
 */
enum class ShareableItemType {
    /** An `https://` URL, encoded as an NDEF URI record. Display name: "Link". */
    Url,

    /**
     * Structured contact fields, encoded as an NDEF `text/vcard` MIME record.
     * Display name: "Contact card".
     */
    VCard,
}

/**
 * How a phone number or email address is labelled on a contact card.
 *
 * The *concept* is fixed by the spec — "phone numbers (typed, multiple), email
 * addresses (typed, multiple)" — but the identifier is not yet in the project's
 * naming conventions and is provisional rather than settled.
 *
 * [vCardTypeParameter] is the vCard 3.0 TYPE value, which is not always the same
 * word: a mobile number is `CELL` on the wire regardless of what the UI calls it.
 */
enum class ContactFieldType(val vCardTypeParameter: String) {
    Work("WORK"),
    Home("HOME"),
    Mobile("CELL"),
    Other("OTHER"),
}

/** A single typed phone number on a contact card. */
data class PhoneNumber(
    val type: ContactFieldType = ContactFieldType.Mobile,
    val value: String = "",
)

/** A single typed email address on a contact card. */
data class EmailAddress(
    val type: ContactFieldType = ContactFieldType.Work,
    val value: String = "",
)

/**
 * The structured contact fields behind a [ShareableItemType.VCard] item.
 *
 * Structured, not an opaque `.vcf` string, and deliberately so: the QR density
 * tiers are field subsets, and a blob cannot be tiered. Anything that flattens
 * this into a single string before storage defeats the whole tier feature.
 *
 * [photoUri] is **display only**. It is shown when the card is viewed in this
 * app and is never serialised into a vCard, over NFC or into a QR code. A
 * base64 image is exactly what destroys QR scannability, and over NFC a photo
 * takes the payload from a few hundred bytes to tens of kilobytes — at roughly
 * 106 kbit/s that turns an instant tap into seconds of held contact, which many
 * readers abandon part way, saving nothing and reporting nothing.
 *
 * Anyone who wants the photo gets it by following the hosted vCard URL, which is
 * the operator's primary item anyway.
 */
data class VCardData(
    val formattedName: String = "",
    val givenName: String = "",
    val familyName: String = "",
    val organisation: String = "",
    val jobTitle: String = "",
    val phones: List<PhoneNumber> = emptyList(),
    val emails: List<EmailAddress> = emptyList(),
    val url: String = "",
    val address: String = "",
    val note: String = "",
    /**
     * A locally displayed portrait, as a persisted content URI.
     * Never leaves the device — see the class comment.
     */
    val photoUri: String? = null,
)

/**
 * One stored payload the operator can present — the central domain object.
 *
 * A single item is either a URL or a contact card, never both. The two payload
 * fields are nullable rather than modelled as a sealed hierarchy because Room
 * stores them in one table and the editor screen needs to hold a half-filled
 * draft of either kind without changing its type mid-edit.
 */
data class ShareableItem(
    val id: Long = 0L,
    val label: String = "",
    val type: ShareableItemType = ShareableItemType.Url,
    /** Manual ordering in the library. Lower sorts first. */
    val position: Int = 0,
    /** Populated when [type] is [ShareableItemType.Url]. */
    val url: String? = null,
    /** Populated when [type] is [ShareableItemType.VCard]. */
    val vCard: VCardData? = null,
) {
    /**
     * Whether this item has enough content to actually present.
     *
     * An item can be saved half-finished; it just cannot be served. The HCE
     * service and the QR view both check this rather than emitting an empty
     * record, because a reader cannot distinguish an empty payload from a fault.
     */
    val isPresentable: Boolean
        get() = when (type) {
            ShareableItemType.Url -> !url.isNullOrBlank()
            ShareableItemType.VCard -> vCard?.formattedName?.isNotBlank() == true
        }
}
