package com.fabrice.monumentsnearby.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fabrice.monumentsnearby.data.Monument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests d'interface des composants partagés, exécutés sur la JVM avec
 * Robolectric (pas d'émulateur) : ils attrapent les régressions de rendu et
 * de câblage que la simple compilation ne voit pas.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sectionHeader_affiche_le_titre_en_capitales() {
        compose.setContent { SectionHeader("Photos") }
        compose.onNodeWithText("PHOTOS").assertExists()
    }

    @Test
    fun filterBar_remonte_la_categorie_choisie() {
        var chosen: String? = "init"
        compose.setContent { FilterBar(selected = null, onSelect = { chosen = it }) }
        compose.onNodeWithText("Musée").performClick()
        assertEquals("musée", chosen)
    }

    @Test
    fun filterBar_reclic_sur_le_filtre_actif_le_retire() {
        var chosen: String? = "init"
        compose.setContent { FilterBar(selected = "musée", onSelect = { chosen = it }) }
        compose.onNodeWithText("Musée").performClick()
        assertEquals(null, chosen)
    }

    @Test
    fun monumentCard_affiche_le_nom_et_declenche_l_itineraire() {
        var navigated = false
        val monument = Monument(
            id = "1", name = "Château test", lat = 0.0, lon = 0.0,
            distanceM = 250.0, kind = "castle"
        )
        compose.setContent {
            MonumentCard(
                monument = monument,
                onListen = {},
                onNavigate = { navigated = true },
                onCardClick = {}
            )
        }
        compose.onNodeWithText("Château test").assertExists()
        compose.onNodeWithText("Itinéraire").performClick()
        assertTrue(navigated)
    }
}
