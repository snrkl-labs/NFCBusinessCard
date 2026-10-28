package org.snrkl.nfcbusinesscard.vcard

import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * How much of a contact card the QR code carries.
 *
 * A full vCard with address, note and secondary contacts pushes past QR version
 * 15, and a version-15 code in bad light against a low-end camera simply does
 * not scan. Dropping detail drops the module count, which makes each module
 * physically larger on screen and the code far more forgiving. Swiping between
 * tiers is the recovery mechanism when an exchange is failing.
 *
 * Names follow the project's naming conventions.
 */
enum class DensityTier {
    /** Sparsest. The URL alone. */
    LinkOnly,

    /** Name, one phone, one email. */
    Minimal,

    /** Minimal plus organisation, job title and website. */
    Standard,

    /** Standard plus address, note and any secondary phones and emails. */
    Full,
    ;

    companion object {
        /** Ordered sparse-to-dense, which is the order swiping moves through. */
        val ordered: List<DensityTier> = listOf(LinkOnly, Minimal, Standard, Full)
    }
}

/**
 * A kind of field that can appear in a shared payload.
 *
 * Kept as an enum rather than a display string so this package stays free of
 * Android dependencies and remains unit-testable; the UI maps these to
 * localised labels.
 */
enum class SharedFieldKind {
    Link,
    Name,
    Phone,
    Email,
    Organisation,
    JobTitle,
    Website,
    Address,
    Note,
}

/** One field actually present in the payload at a given tier, with its value. */
data class SharedField(val kind: SharedFieldKind, val value: String)

/**
 * Reduces a contact card to the fields a given tier carries.
 *
 * Pure and Android-free so the tier boundaries can be asserted directly in unit
 * tests. Getting these wrong is invisible until someone in a hotel lobby cannot
 * scan a code, so they are tested rather than eyeballed.
 */
internal object TierFieldSelector {

    /**
     * Returns the subset of [data] that [tier] carries.
     *
     * [DensityTier.LinkOnly] is not expressible as a vCard subset — it is a bare
     * URL, not a contact card — so it is handled by [payloadFor] instead and
     * returns just the URL field here for completeness.
     */
    fun select(data: VCardData, tier: DensityTier): VCardData = when (tier) {
        DensityTier.LinkOnly -> VCardData(url = data.url)

        DensityTier.Minimal -> VCardData(
            formattedName = data.formattedName,
            givenName = data.givenName,
            familyName = data.familyName,
            // "one phone, one email" — the first of each, which is the order the
            // operator entered them in, so the primary number sorts first.
            phones = data.phones.take(1),
            emails = data.emails.take(1),
        )

        DensityTier.Standard -> VCardData(
            formattedName = data.formattedName,
            givenName = data.givenName,
            familyName = data.familyName,
            organisation = data.organisation,
            jobTitle = data.jobTitle,
            phones = data.phones.take(1),
            emails = data.emails.take(1),
            url = data.url,
        )

        DensityTier.Full -> data
    }

    /**
     * The text a QR code should encode for [item] at [tier].
     *
     * Returns null when there is nothing meaningful to encode, which the QR view
     * renders as an explanatory message rather than a broken code.
     *
     * A URL item collapses to its URL at every tier — there are no field subsets
     * of a single URL, which is why the tier control hides itself entirely for
     * URL items rather than offering four identical codes.
     */
    fun payloadFor(item: ShareableItem, tier: DensityTier): String? = when (item.type) {
        ShareableItemType.Url -> item.url?.takeIf { it.isNotBlank() }

        ShareableItemType.VCard -> {
            val data = item.vCard
            when {
                data == null -> null
                // LinkOnly on a contact card means the card's own website field.
                // With no website there is nothing sparser to fall back to, so
                // the view reports that rather than silently showing a denser code.
                tier == DensityTier.LinkOnly -> data.url.takeIf { it.isNotBlank() }
                data.formattedName.isBlank() -> null
                else -> VCardSerializer.serialize(select(data, tier))
            }
        }
    }

    /**
     * Which tiers are worth offering for [item].
     *
     * A URL item has exactly one representation. A contact card without a
     * website has no LinkOnly representation, so offering it would produce a
     * dead tier the operator can swipe into and find empty.
     */
    fun availableTiers(item: ShareableItem): List<DensityTier> = when (item.type) {
        ShareableItemType.Url -> listOf(DensityTier.LinkOnly)
        ShareableItemType.VCard -> DensityTier.ordered.filter { tier ->
            tier != DensityTier.LinkOnly || item.vCard?.url?.isNotBlank() == true
        }
    }

    /**
     * Describes exactly what [item] puts on screen at [tier], field by field.
     *
     * A QR code is opaque — you cannot tell by looking whether it carries your
     * mobile number or your home address. Handing a phone to a stranger without
     * knowing which is a real privacy problem, and it gets worse as the tiers
     * get denser. This drives the on-screen summary so the operator can see what
     * they are about to share before they share it.
     *
     * Derived from the same [select] the encoder uses, not assembled separately,
     * so the summary cannot drift from the payload and claim to be sharing
     * something it is not.
     */
    fun describeShared(item: ShareableItem, tier: DensityTier): List<SharedField> =
        when (item.type) {
            ShareableItemType.Url ->
                item.url?.takeIf { it.isNotBlank() }
                    ?.let { listOf(SharedField(SharedFieldKind.Link, it)) }
                    .orEmpty()

            ShareableItemType.VCard -> {
                val data = item.vCard
                when {
                    data == null -> emptyList()
                    tier == DensityTier.LinkOnly ->
                        data.url.takeIf { it.isNotBlank() }
                            ?.let { listOf(SharedField(SharedFieldKind.Link, it)) }
                            .orEmpty()
                    else -> buildList {
                        val selected = select(data, tier)
                        if (selected.formattedName.isNotBlank()) {
                            add(SharedField(SharedFieldKind.Name, selected.formattedName))
                        }
                        if (selected.organisation.isNotBlank()) {
                            add(SharedField(SharedFieldKind.Organisation, selected.organisation))
                        }
                        if (selected.jobTitle.isNotBlank()) {
                            add(SharedField(SharedFieldKind.JobTitle, selected.jobTitle))
                        }
                        selected.phones.filter { it.value.isNotBlank() }.forEach {
                            add(SharedField(SharedFieldKind.Phone, it.value))
                        }
                        selected.emails.filter { it.value.isNotBlank() }.forEach {
                            add(SharedField(SharedFieldKind.Email, it.value))
                        }
                        if (selected.url.isNotBlank()) {
                            add(SharedField(SharedFieldKind.Website, selected.url))
                        }
                        if (selected.address.isNotBlank()) {
                            add(SharedField(SharedFieldKind.Address, selected.address))
                        }
                        if (selected.note.isNotBlank()) {
                            add(SharedField(SharedFieldKind.Note, selected.note))
                        }
                    }
                }
            }
        }
}
