package com.fabrice.monumentsnearby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonsClientTest {

    @Test
    fun `titre de fichier depuis Special FilePath`() {
        assertEquals(
            "File:Château d'Asnières.jpg",
            CommonsClient.fileTitleFromUrl(
                "https://commons.wikimedia.org/wiki/Special:FilePath/Ch%C3%A2teau_d%27Asni%C3%A8res.jpg?width=400"
            )
        )
    }

    @Test
    fun `titre de fichier depuis une vignette upload`() {
        assertEquals(
            "File:Tour Eiffel.jpg",
            CommonsClient.fileTitleFromUrl(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a8/Tour_Eiffel.jpg/400px-Tour_Eiffel.jpg"
            )
        )
    }

    @Test
    fun `url inconnue donne null`() {
        assertNull(CommonsClient.fileTitleFromUrl("https://example.com/photo.jpg"))
    }

    @Test
    fun `parse lit auteur et licence sans balises`() {
        val json = """
            {"query":{"pages":{"123":{"imageinfo":[{"extmetadata":{
              "Artist":{"value":"<a href=\"//commons.wikimedia.org/wiki/User:X\">Jean Photo</a>"},
              "LicenseShortName":{"value":"CC BY-SA 4.0"},
              "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0"}
            }}]}}}}
        """.trimIndent()
        val a = CommonsClient.parse(json, "File:Tour Eiffel.jpg")!!
        assertEquals("Jean Photo", a.author)
        assertEquals("CC BY-SA 4.0", a.license)
        assertEquals("Jean Photo · CC BY-SA 4.0", a.line)
        assertEquals("https://commons.wikimedia.org/wiki/File:Tour_Eiffel.jpg", a.filePage)
    }
}
