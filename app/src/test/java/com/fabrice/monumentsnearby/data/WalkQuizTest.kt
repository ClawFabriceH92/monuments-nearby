package com.fabrice.monumentsnearby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WalkQuizTest {

    private fun monument(
        id: String,
        name: String,
        inception: String? = null,
        architect: String? = null,
        style: String? = null
    ) = Monument(
        id = id, name = name, lat = 0.0, lon = 0.0, distanceM = 0.0, kind = "monument",
        inception = inception, architect = architect, style = style
    )

    @Test
    fun `questions generees avec bonnes reponses coherentes`() {
        val stops = listOf(
            monument("1", "Château d'Asnières", inception = "1750", style = "rococo"),
            monument("2", "Musée Louis-Vuitton", inception = "1859", architect = "Jean Dupont"),
            monument("3", "Église Sainte-Geneviève", inception = "1932")
        )
        val questions = WalkQuiz.build(stops, Random(42))

        assertTrue(questions.isNotEmpty())
        questions.forEach { q ->
            assertEquals(3, q.options.size)
            assertTrue(q.correctIndex in q.options.indices)
            assertTrue(q.options.distinct().size == 3)
        }
        // La question sur 1750 doit avoir le château pour bonne réponse
        val q1750 = questions.firstOrNull { it.text.contains("1750") }
        q1750?.let { assertEquals("Château d'Asnières", it.options[it.correctIndex]) }
    }

    @Test
    fun `pas de question sur un attribut ambigu`() {
        val stops = listOf(
            monument("1", "A", inception = "1900"),
            monument("2", "B", inception = "1900"), // même année : ambigu
            monument("3", "C", inception = "1955")
        )
        val questions = WalkQuiz.build(stops, Random(1))

        assertTrue(questions.none { it.text.contains("1900") })
    }

    @Test
    fun `moins de trois monuments donne un quiz vide`() {
        val stops = listOf(
            monument("1", "A", inception = "1900"),
            monument("2", "B", inception = "1950")
        )
        assertTrue(WalkQuiz.build(stops, Random(1)).isEmpty())
    }

    @Test
    fun `une seule question par monument`() {
        val stops = listOf(
            monument("1", "A", inception = "1900", architect = "X", style = "gothique"),
            monument("2", "B", inception = "1950"),
            monument("3", "C", inception = "1980")
        )
        val questions = WalkQuiz.build(stops, Random(7))
        val answers = questions.map { it.options[it.correctIndex] }

        assertEquals(answers.size, answers.distinct().size)
    }
}
