package org.snrkl.nfcbusinesscard.ndef

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The URI abbreviation table.
 *
 * The prefix byte is the classic reason a tag reads back as garbage, and it is
 * completely invisible until someone taps a phone against it. There is no
 * emulator path to catch a mistake here, so it is pinned down exhaustively.
 */
class UriPrefixTest {

    @Test
    fun `https is abbreviated to 0x04`() {
        val result = abbreviateUri("https://example.com/card")
        assertThat(result.prefixByte).isEqualTo(0x04.toByte())
        assertThat(result.remainder).isEqualTo("example.com/card")
    }

    @Test
    fun `https with www prefers the longer 0x02 over 0x04`() {
        // Both prefixes match. Taking the first hit in table order would give
        // 0x02 here only by luck of the ordering, so longest-match is asserted
        // rather than assumed.
        val result = abbreviateUri("https://www.example.com")
        assertThat(result.prefixByte).isEqualTo(0x02.toByte())
        assertThat(result.remainder).isEqualTo("example.com")
    }

    @Test
    fun `http is abbreviated to 0x03 and http www to 0x01`() {
        assertThat(abbreviateUri("http://example.com").prefixByte).isEqualTo(0x03.toByte())
        assertThat(abbreviateUri("http://www.example.com").prefixByte).isEqualTo(0x01.toByte())
    }

    @Test
    fun `tel and mailto get their own prefixes`() {
        assertThat(abbreviateUri("tel:+61400000000").prefixByte).isEqualTo(0x05.toByte())
        assertThat(abbreviateUri("mailto:someone@example.com").prefixByte).isEqualTo(0x06.toByte())
    }

    @Test
    fun `an unrecognised scheme falls back to 0x00 and is written in full`() {
        val result = abbreviateUri("geo:-33.8688,151.2093")
        assertThat(result.prefixByte).isEqualTo(0x00.toByte())
        assertThat(result.remainder).isEqualTo("geo:-33.8688,151.2093")
    }

    @Test
    fun `ftp variants pick the longest match, not the first in table order`() {
        // ftp:// is 0x0D but sits *after* ftp://ftp. (0x08) and
        // ftp://anonymous:anonymous@ (0x07) in the table, so this catches an
        // implementation that scans in index order.
        assertThat(abbreviateUri("ftp://ftp.example.com").prefixByte).isEqualTo(0x08.toByte())
        assertThat(abbreviateUri("ftp://example.com").prefixByte).isEqualTo(0x0D.toByte())
        assertThat(abbreviateUri("ftp://anonymous:anonymous@example.com").prefixByte)
            .isEqualTo(0x07.toByte())
    }

    @Test
    fun `urn variants pick the longest match`() {
        // urn: is 0x13; the urn:epc: family is 0x1E-0x22 and urn:nfc: is 0x23,
        // all of which start with "urn:".
        assertThat(abbreviateUri("urn:epc:id:sgtin:1").prefixByte).isEqualTo(0x1E.toByte())
        assertThat(abbreviateUri("urn:epc:raw:1").prefixByte).isEqualTo(0x21.toByte())
        assertThat(abbreviateUri("urn:epc:something").prefixByte).isEqualTo(0x22.toByte())
        assertThat(abbreviateUri("urn:nfc:ext:example").prefixByte).isEqualTo(0x23.toByte())
        assertThat(abbreviateUri("urn:isbn:0000000000").prefixByte).isEqualTo(0x13.toByte())
    }

    @Test
    fun `every prefix in the table round-trips`() {
        // Abbreviating a URI then expanding it must return the original for
        // every entry, which catches an off-by-one in the table indexing.
        URI_PREFIXES.forEachIndexed { index, prefix ->
            if (index == 0) return@forEachIndexed
            val uri = prefix + "round-trip-probe"
            val abbreviated = abbreviateUri(uri)
            assertThat(abbreviated.expand()).isEqualTo(uri)
        }
    }

    @Test
    fun `the table has exactly the 36 entries the NFC Forum defines`() {
        // 0x00 through 0x23. A row inserted or dropped silently shifts every
        // byte after it, so the count is pinned.
        assertThat(URI_PREFIXES).hasSize(36)
        assertThat(URI_PREFIXES[0x04]).isEqualTo("https://")
        assertThat(URI_PREFIXES[0x02]).isEqualTo("https://www.")
        assertThat(URI_PREFIXES[0x23]).isEqualTo("urn:nfc:")
    }

    @Test
    fun `a URI that is exactly a prefix leaves an empty remainder`() {
        val result = abbreviateUri("https://")
        assertThat(result.prefixByte).isEqualTo(0x04.toByte())
        assertThat(result.remainder).isEmpty()
    }
}
