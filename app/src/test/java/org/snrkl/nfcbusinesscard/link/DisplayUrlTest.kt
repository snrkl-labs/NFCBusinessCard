package org.snrkl.nfcbusinesscard.link

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The link text shown beside a QR code.
 *
 * Both the card face and the widget render this, so the rule lives in one place
 * and is pinned here — the failure it guards against is the two drifting apart
 * and the same link reading differently in the app and on the home screen.
 */
class DisplayUrlTest {

    @Test
    fun theSchemeIsDropped() {
        assertEquals("example.com/card", displayUrl("https://example.com/card"))
        assertEquals("example.com/card", displayUrl("http://example.com/card"))
    }

    @Test
    fun aTrailingSlashIsDropped() {
        assertEquals("example.com", displayUrl("https://example.com/"))
    }

    @Test
    fun thePathSurvivesIntact() {
        // The path is the only thing distinguishing one of the operator's links
        // from another, so it is never shortened or elided.
        assertEquals(
            "www.linkedin.com/in/some-quite-long-profile-name",
            displayUrl("https://www.linkedin.com/in/some-quite-long-profile-name"),
        )
    }

    @Test
    fun aQueryStringSurvives() {
        assertEquals("example.com/c?id=7", displayUrl("https://example.com/c?id=7"))
    }

    @Test
    fun wwwIsKept() {
        // Not stripped: it is part of how a host is written and dropping it
        // could name a different host from the one the code actually opens.
        assertEquals("www.example.com", displayUrl("https://www.example.com"))
    }

    @Test
    fun aLinkWithNoSchemeIsLeftAlone() {
        assertEquals("example.com", displayUrl("example.com"))
    }
}
