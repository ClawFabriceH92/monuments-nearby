package com.fabrice.monumentsnearby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataNearbyTest {

    private val sample = """
        {"results":{"bindings":[
          {"item":{"value":"http://www.wikidata.org/entity/Q1"},
           "itemLabel":{"value":"statue de la liberté"},
           "coord":{"value":"Point(2.2945 48.8584)"},
           "typeLabel":{"value":"sculpture"}},
          {"item":{"value":"http://www.wikidata.org/entity/Q1"},
           "itemLabel":{"value":"statue de la liberté"},
           "coord":{"value":"Point(2.2945 48.8584)"},
           "typeLabel":{"value":"monument"}},
          {"item":{"value":"http://www.wikidata.org/entity/Q2"},
           "itemLabel":{"value":"Q2"},
           "coord":{"value":"Point(2.30 48.86)"}},
          {"item":{"value":"http://www.wikidata.org/entity/Q3"},
           "itemLabel":{"value":"plaque Jean Moulin"},
           "itemDescription":{"value":"plaque commémorative"},
           "coord":{"value":"Point(2.31 48.87)"}},
          {"item":{"value":"http://www.wikidata.org/entity/Q4"},
           "itemLabel":{"value":"déjà connu"},
           "coord":{"value":"Point(2.29 48.85)"}}
        ]}}
    """.trimIndent()

    @Test
    fun `parse dedoublonne, exclut les QID connus et les sans-libelle`() {
        val result = WikidataNearby.parse(sample, 48.8584, 2.2945, setOf("Q4"), 10)

        assertEquals(listOf("wd_Q1", "wd_Q3"), result.map { it.id })
        assertEquals("Statue de la liberté", result[0].name)
        assertEquals("sculpture", result[0].kind)
        assertEquals("Q3", result[1].wikidataId)
        assertEquals("plaque commémorative", result[1].description)
        assertTrue(result[0].distanceM < 1.0)
        assertTrue(result[1].distanceM > 1000.0)
    }

    @Test
    fun `parsePoint lit lon puis lat`() {
        assertEquals(2.2945 to 48.8584, WikidataNearby.parsePoint("Point(2.2945 48.8584)"))
        assertNull(WikidataNearby.parsePoint("Polygon(...)"))
    }
}
