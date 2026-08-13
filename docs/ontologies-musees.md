# Ontologies culturelles pour le mode Musée — notes

Référence : standards de la profession muséale (recherche Fabrice, 08/2026).

## Les standards

| Standard | Description | Complexité | Usage typique |
|---|---|---|---|
| **EDM** (Europeana Data Model) | Standard européen, fusionne les domaines patrimoniaux, aligné sur Wikidata/SKOS | Moyenne-Haute | API Europeana, agrégation patrimoine |
| **LIDO** | Format XML/Semantic Web léger pour l'échange musée↔app | Faible-Moyenne | Ingestion de métadonnées brutes |
| **CDWA Lite / CDPL** | Focalisé descriptions d'œuvres d'art, vocabulaires riches | Moyenne | Objets d'art |

## Décision d'architecture (notre app)

**On n'implémente PAS EDM/LIDO/CDWA dans l'app.** Raisons :

1. Notre app consomme **Wikidata + Wikimedia Commons + Wikipédia** (zéro clé, zéro infra).
   Wikidata contient déjà des alignements vers EDM/CDWA/LIDO et agrège les données
   muséales — c'est exactement le point de la recommandation EDM ("Wikidata contains
   alignment statements to other ontologies used in the cultural domain").
2. Notre échelle = une requête HTTP depuis un téléphone. Pas de moteur d'inférence
   RDF/OWL, pas de millions de triples, pas d'index SPARQL local.
3. L'ontologie interne reste un simple data class `Monument` — suffisant pour la liste/la carte.

**Quand EDM/LIDO deviendraient utiles (éventuel) :**
- API **Europeana** (données en EDM) comme source complémentaire pour les œuvres/musées
  mal couverts par Wikidata. C'est le seul cas où comprendre EDM servirait concrètement.
- Import d'un dump LIDO d'un musée précis (hors périmètre actuel).

## Pipeline réellement utilisé (mode Musée)

1. GPS → Overpass (`tourism=museum` + tag `wikidata=Q...`)
2. QID du musée → SPARQL Wikidata : œuvres avec `wdt:P195` (collection) = QID du musée
3. Pour chaque œuvre : titre (label FR), artiste (P170), date (P571), image (P18 → Commons),
   type (P31), résumé Wikipédia (sitelink frwiki) si article dédié
