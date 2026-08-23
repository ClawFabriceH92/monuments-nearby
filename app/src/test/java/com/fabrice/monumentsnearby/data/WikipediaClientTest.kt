package com.fabrice.monumentsnearby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaClientTest {

    @Test
    fun `les titres de section perdent leurs signes egal`() {
        val raw = """
            La cathédrale est un édifice gothique.

            == Histoire ==
            Elle fut construite au XIIe siècle.
        """.trimIndent()

        val clean = WikipediaClient.cleanArticleText(raw)

        assertFalse("aucun '=' ne doit rester à lire", clean.contains("="))
        assertTrue(clean.contains("Histoire."))
        assertTrue(clean.contains("Elle fut construite au XIIe siècle."))
    }

    @Test
    fun `les sous-sections sont traitees comme des titres`() {
        // Le titre garde sa propre ligne, le texte de la section suit dessous
        val clean = WikipediaClient.cleanArticleText("=== La nef ===\nElle mesure 30 mètres.")

        assertEquals("La nef.\nElle mesure 30 mètres.", clean)
    }

    @Test
    fun `une ligne vide separe deux sections`() {
        val raw = "Texte d'introduction.\n\n== Histoire ==\nUn siècle plus tard."

        val clean = WikipediaClient.cleanArticleText(raw)

        assertEquals("Texte d'introduction.\n\nHistoire.\nUn siècle plus tard.", clean)
    }

    @Test
    fun `les sections de renvois sont coupees`() {
        val raw = """
            == Architecture ==
            Le portail est sculpté.

            == Notes et références ==
            ↑ Voir la notice.

            == Liens externes ==
            Site officiel
        """.trimIndent()

        val clean = WikipediaClient.cleanArticleText(raw)

        assertTrue(clean.contains("Le portail est sculpté."))
        assertFalse(clean.contains("Voir la notice"))
        assertFalse(clean.contains("Site officiel"))
    }

    @Test
    fun `le texte reprend apres une section ignoree`() {
        val raw = """
            == Bibliographie ==
            Ouvrage de référence, 1920.

            == Postérité ==
            Le monument inspire des peintres.
        """.trimIndent()

        val clean = WikipediaClient.cleanArticleText(raw)

        assertFalse(clean.contains("Ouvrage de référence"))
        assertTrue(clean.contains("Postérité."))
        assertTrue(clean.contains("Le monument inspire des peintres."))
    }

    @Test
    fun `un article sans titre reste intact`() {
        val clean = WikipediaClient.cleanArticleText("Un simple paragraphe.")

        assertEquals("Un simple paragraphe.", clean)
    }
}
