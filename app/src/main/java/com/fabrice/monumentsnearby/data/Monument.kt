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
    /** Fondateur (P112). */
    val founder: String? = null,
    /** Propriétaire (P127). */
    val owner: String? = null,
    /** Site web officiel (P856). */
    val website: String? = null,
    /** Horaires d'ouverture (OSM opening_hours). */
    val openingHours: String? = null,
    /** Tarif (OSM fee/charge) : « payant », « gratuit », ou prix. */
    val fee: String? = null,
    /** Année du classement patrimonial (qualificatif P580 de P1435). */
    val heritageYear: String? = null,
    /** Référence Mérimée (P380) → notice POP data.culture.gouv.fr. */
    val merimeeRef: String? = null,
    /** Identifiant Muséofile (P539) → notice POP des musées de France. */
    val museofileRef: String? = null,
    /** Nommé d'après (P138). */
    val namedAfter: String? = null,
    /** Événements marquants (P793) : « incendie (2019) », « restauration »… */
    val events: List<String> = emptyList(),
    /** Année d'ouverture officielle (P1619), si différente de la construction. */
    val openedYear: String? = null,
    /** Adresse postale (P6375). */
    val address: String? = null,
    /** Commune / division administrative (P131). */
    val commune: String? = null,
    /** true si monument majeur (article Wikipédia dédié ou type important). */
    val important: Boolean = false
)

/** Catégorie affichable d'un monument : musée, religieux, château, ruines, monument, autre. */
fun Monument.category(): String {
    val k = kind.lowercase()
    return when {
        k.contains("musée") || k.contains("museum") -> "musée"
        k.contains("église") || k.contains("cathédrale") || k.contains("basilique") ||
            k.contains("chapelle") || k.contains("temple") || k.contains("monastère") ||
            k.contains("abbaye") || k.contains("convent") || k.contains("abbey") -> "religieux"
        k.contains("château") || k.contains("palais") || k.contains("manoir") ||
            k.contains("fort") || k.contains("castle") || k.contains("palace") ||
            k.contains("manor") || k.contains("chateau") -> "château"
        k.contains("ruine") || k.contains("archéologique") || k.contains("archaeological") ||
            k.contains("ruins") -> "ruines"
        k.contains("monument") || k.contains("mémorial") || k.contains("memorial") ||
            k.contains("fontaine") || k.contains("fountain") || k.contains("battlefield") -> "monument"
        else -> "autre"
    }
}
