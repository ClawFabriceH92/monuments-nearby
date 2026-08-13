package com.fabrice.monumentsnearby.data

/**
 * Un monument historique / lieu d'intérêt.
 * Les champs wikidataId / wikipediaTitle / inception / imageUrl sont
 * enrichis par Wikidata/Wikimedia après la détection Overpass.
 */
data class Monument(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Double,
    /** Type : tag OSM (monument, castle, ruins...) ou label ontologique Wikidata (P31). */
    val kind: String,
    val description: String? = null,
    val wikipedia: String? = null,
    val imageUrl: String? = null,
    val wikidataId: String? = null,
    val wikipediaTitle: String? = null,
    val inception: String? = null,
    /** Artiste (œuvres de musée, P170 Wikidata). */
    val artist: String? = null,
    /** Catégorie Wikimedia Commons (P373) — pour la galerie d'images. */
    val commonsCategory: String? = null,
    /** Architecte (P84). */
    val architect: String? = null,
    /** Style architectural (P149). */
    val style: String? = null,
    /** Matériau principal (P186). */
    val material: String? = null,
    /** Classement patrimonial (P1435), ex: « Monument historique classé ». */
    val heritage: String? = null,
    /** true si monument majeur (article Wikipédia dédié ou type important). */
    val important: Boolean = false
)
