package com.fabrice.monumentsnearby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikidataClientTest {

    @Test
    fun `annee simple`() {
        assertEquals("1889", WikidataClient.yearOf("+1889-03-31T00:00:00Z"))
    }

    @Test
    fun `annee avec zeros de tete`() {
        assertEquals("850", WikidataClient.yearOf("+0850-00-00T00:00:00Z"))
    }

    @Test
    fun `annee avant JC`() {
        assertEquals("450 av. J.-C.", WikidataClient.yearOf("-0450-00-00T00:00:00Z"))
    }

    @Test
    fun `chaine vide`() {
        assertNull(WikidataClient.yearOf(""))
    }
}
