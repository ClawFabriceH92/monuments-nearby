package com.fabrice.monumentsnearby.data

import kotlin.random.Random

/**
 * Quiz de fin de balade : questions générées depuis les données des étapes
 * (année, architecte, style, classement…). Les mauvaises réponses sont les
 * autres monuments de la balade — et une question n'est posée que si son
 * indice est unique parmi les étapes (pas d'ambiguïté possible).
 */
object WalkQuiz {

    data class Question(
        val text: String,
        val options: List<String>,
        val correctIndex: Int
    )

    private const val MAX_QUESTIONS = 5

    fun build(monuments: List<Monument>, random: Random = Random.Default): List<Question> {
        val names = monuments.map { it.name }.distinct()
        if (names.size < 3) return emptyList()

        // (indice, monument) pour chaque attribut dont la valeur est unique
        data class Candidate(val clue: String, val answer: String)

        fun <T> uniques(selector: (Monument) -> T?): List<Pair<Monument, T>> {
            val values = monuments.mapNotNull { m -> selector(m)?.let { m to it } }
            val counts = values.groupingBy { it.second }.eachCount()
            return values.filter { counts[it.second] == 1 }
        }

        val candidates = buildList {
            uniques { it.inception }.forEach { (m, year) ->
                add(Candidate("Quel monument a été construit en $year ?", m.name))
            }
            uniques { it.architect }.forEach { (m, architect) ->
                add(Candidate("Quel monument est l'œuvre de l'architecte $architect ?", m.name))
            }
            uniques { it.style }.forEach { (m, style) ->
                add(Candidate("Quel monument est de style $style ?", m.name))
            }
            uniques { it.heritageYear }.forEach { (m, year) ->
                add(Candidate("Quel monument a été protégé au titre des monuments historiques en $year ?", m.name))
            }
            uniques { it.namedAfter }.forEach { (m, named) ->
                add(Candidate("Quel monument est nommé d'après $named ?", m.name))
            }
            uniques { it.founder }.forEach { (m, founder) ->
                add(Candidate("Quel monument a été fondé par $founder ?", m.name))
            }
        }

        return candidates
            .shuffled(random)
            .distinctBy { it.answer } // une seule question par monument
            .take(MAX_QUESTIONS)
            .mapNotNull { candidate ->
                val distractors = names.filter { it != candidate.answer }
                    .shuffled(random)
                    .take(2)
                if (distractors.size < 2) return@mapNotNull null
                val options = (distractors + candidate.answer).shuffled(random)
                Question(
                    text = candidate.clue,
                    options = options,
                    correctIndex = options.indexOf(candidate.answer)
                )
            }
    }
}
