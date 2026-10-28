package org.snrkl.nfcbusinesscard.link

/**
 * A link as it should be read by a person, not parsed by a machine.
 *
 * A QR code is square and the card region it sits in is landscape, so the code
 * is shown with the link beside it — which only helps if the link is legible at
 * a glance. Two things are dropped to that end:
 *
 *  - **the scheme**, which tells a human nothing. Every link stored here is
 *    `https` by validation, so printing it spends the widest characters in the
 *    string on the one part that is always the same;
 *  - **a trailing slash**, which is punctuation with no meaning to a reader.
 *
 * Nothing else is touched. The path is what distinguishes one of the operator's
 * links from another, and a shortened or elided path would defeat the reason
 * the text is there at all.
 *
 * Deliberately pure Kotlin with no Android imports: it is shared by the Compose
 * card face and the RemoteViews widget, which have no other code in common, and
 * it can then be tested on the JVM.
 */
fun displayUrl(url: String): String =
    url.removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
