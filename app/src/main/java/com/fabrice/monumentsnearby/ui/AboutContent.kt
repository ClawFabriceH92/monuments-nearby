package com.fabrice.monumentsnearby.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fabrice.monumentsnearby.BuildConfig

/** Une source de données ou une bibliothèque à créditer. */
private data class Credit(val name: String, val role: String, val url: String)

private val CREDITS = listOf(
    Credit("OpenStreetMap", "Données cartographiques et monuments (Overpass) — © les contributeurs OSM, ODbL", "https://www.openstreetmap.org/copyright"),
    Credit("CARTO", "Fonds de carte Positron et Dark Matter", "https://carto.com/attributions"),
    Credit("Wikidata", "Fiches, dates, architectes, classements — CC0", "https://www.wikidata.org"),
    Credit("Wikipédia", "Résumés et articles — CC BY-SA 4.0", "https://fr.wikipedia.org"),
    Credit("Wikimedia Commons", "Photos (licence indiquée sur chaque image)", "https://commons.wikimedia.org"),
    Credit("Valhalla (FOSSGIS)", "Itinéraires piétons", "https://valhalla.openstreetmap.de"),
    Credit("Photon (komoot)", "Suggestions de villes", "https://photon.komoot.io"),
    Credit("Nominatim", "Géocodage des villes", "https://nominatim.org"),
    Credit("POP — data.culture.gouv.fr", "Notices Mérimée et Muséofile", "https://www.pop.culture.gouv.fr"),
    Credit("osmdroid", "Affichage de la carte (Apache 2.0)", "https://github.com/osmdroid/osmdroid"),
    Credit("Exo 2", "Police de caractères — SIL Open Font License 1.1", "https://fonts.google.com/specimen/Exo+2")
)

/**
 * Crédits et mentions : sources de données, services et bibliothèques dont
 * l'application dépend, avec leurs licences. Un tap ouvre le site.
 */
@Composable
fun AboutContent(onOpenUrl: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Monuments à proximité ${BuildConfig.VERSION_NAME} — application libre, sans compte ni " +
                "publicité. Les données viennent de services ouverts que voici.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CREDITS.forEach { credit ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUrl(credit.url) }
                    .padding(vertical = 6.dp)
            ) {
                Text(credit.name, style = MaterialTheme.typography.labelLarge)
                Text(
                    credit.role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
