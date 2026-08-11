# Recherche — Gestion des éditions Kai

Date : 28 juillet 2026

## Contexte

Episort utilise TVDB comme source principale pour identifier les séries, récupérer
les ordres aired, DVD et absolute, puis construire les noms et destinations des
épisodes.

Le besoin étudié est la prise en charge d'éditions « Kai », notamment les
montages raccourcis ou sans fillers déjà présents dans la bibliothèque de
l'utilisateur.

Deux catégories doivent être distinguées :

1. les éditions officielles, par exemple *Dragon Ball Kai* ;
2. les fan-edits, par exemple certaines éditions de *Naruto Kai* ou
   *One Piece Kai*.

Plusieurs fan-edits d'un même anime peuvent avoir des auteurs, versions,
découpages et nombres de parties différents. Le mot « Kai » dans un titre ne
constitue donc jamais une preuve suffisante pour sélectionner automatiquement
un profil.

## Décision actuelle

La solution la plus simple et la plus fiable est :

```text
TVDB comme source principale
+ profils Kai locaux et versionnés
+ aucune seconde API obligatoire
```

TVDB continue de fournir l'identité de la série originale et toutes les
métadonnées qu'il connaît. Un profil Kai décrit uniquement la transformation
propre à une édition : nom, auteur, version, parties et correspondances avec les
épisodes TVDB.

AniList et AniDB ne sont pas retenus comme dépendances obligatoires à ce stade.

## Pourquoi AniList ne suffit pas

AniList fournit de bonnes métadonnées au niveau de l'œuvre :

- titre anglais ;
- titre romaji ;
- titre natif ;
- synonymes ;
- année, format et nombre d'épisodes.

En revanche, AniList ne fournit pas un catalogue canonique complet des épisodes
avec saisons, titres et ordres alternatifs comparable à TVDB. Les fan-edits Kai
ne disposent généralement pas d'une fiche autonome et leur découpage n'y est
pas décrit.

AniList pourrait reconnaître une édition officielle comme *Dragon Ball Kai*,
mais pas exprimer de manière fiable qu'une partie de *Naruto Kai* fusionne, par
exemple, six épisodes de la série originale.

## Pourquoi AniDB n'est pas retenu immédiatement

AniDB possède des titres de séries et d'épisodes plus détaillés et
multilingues. Son intégration reste possible, mais elle est plus contraignante :

- enregistrement du client AniDB ;
- téléchargement quotidien d'un catalogue de titres compressé ;
- recherche locale dans ce catalogue pour obtenir un identifiant AniDB ;
- API HTTP en XML compressé plutôt qu'en JSON ;
- cache local important ;
- limitations de trafic strictes ;
- différences de numérotation entre AniDB et TVDB.

L'API UDP AniDB ajouterait une authentification, des sessions, un protocole de
paquets et une gestion stricte du débit. Elle n'est pas nécessaire pour le
besoin actuel.

Surtout, AniDB ne résout pas davantage le découpage des fan-edits non officiels.
Il pourrait être ajouté plus tard comme source facultative pour enrichir les
titres officiels.

## Alternatives examinées

### TVmaze

TVmaze propose une API REST simple avec épisodes, alias et listes alternatives.
Sa couverture des animes et des éditions Kai est toutefois variable. Les
fan-edits ne sont normalement pas décrits.

### TMDB Episode Groups

TMDB permet plusieurs regroupements : aired, absolute, DVD, digital, story arc,
production et TV. Ces groupes organisent principalement des épisodes officiels
existants et ne décrivent pas nécessairement un montage qui coupe et fusionne
plusieurs épisodes.

### Fanedit.org / IFDB

IFDB est conceptuellement proche du besoin et peut servir de source documentaire
pour le nom, l'auteur, la version ou la durée d'un fan-edit. Aucune API publique
officielle et structurée n'a été identifiée pour obtenir les correspondances
d'épisodes. Le site ne doit pas être aspiré sans autorisation.

### Anime-Lists et mappings communautaires

Ces projets savent représenter des plages, offsets et ratios entre différentes
numérotations. Leur format constitue une bonne inspiration pour les profils
Kai, mais ils ne forment pas un catalogue exhaustif des fan-edits.

### Shoko Server

Shoko peut identifier des fichiers et exploiter AniDB. Il serait pertinent pour
un utilisateur qui l'emploie déjà, mais trop lourd comme dépendance obligatoire
d'une application desktop autonome.

## Format proposé pour un profil Kai

Exemple indicatif :

```json
{
  "schemaVersion": 1,
  "profileId": "naruto-kai-mixouille",
  "displayTitle": "Naruto Kai",
  "editionAuthor": "Mixouille",
  "editionVersion": "inconnue",
  "tvdbSeriesId": "78857",
  "language": "fr",
  "parts": [
    {
      "fileMatcher": "(?i)^Naruto Kai 0?1$",
      "season": 1,
      "episode": 1,
      "title": "Le Pays des vagues",
      "sourceEpisodes": [
        {
          "season": 1,
          "from": 1,
          "to": 6
        }
      ]
    }
  ]
}
```

Le schéma définitif devra gérer :

- l'identifiant et la version du schéma ;
- l'identifiant stable du profil ;
- l'auteur et la version de l'édition ;
- la langue ;
- la série TVDB source ;
- les motifs de reconnaissance des fichiers ;
- l'ordre des parties Kai ;
- les titres explicitement connus ;
- les plages d'épisodes TVDB sources ;
- les cas où une partie utilise plusieurs saisons ou plages disjointes ;
- les champs inconnus sans fabriquer de valeur.

Les profils publiables ne doivent contenir aucun chemin personnel, aucune
métadonnée privée de bibliothèque et aucun secret.

## Récupération des informations depuis les fichiers existants

L'analyse initiale doit être strictement en lecture seule.

Pour chaque fichier, relever :

- nom du dossier et du fichier ;
- extension ;
- taille et durée ;
- titre intégré au conteneur ;
- chapitres ;
- langues audio et sous-titres ;
- auteur ou groupe d'encodage ;
- numéro de version éventuel.

Les sources doivent être consultées dans cet ordre :

1. fichiers `README`, `NFO`, JSON, XML, CSV ou SFV accompagnant la release ;
2. noms de dossiers et fichiers ;
3. métadonnées et chapitres MKV/MP4 ;
4. page d'origine ou documentation du créateur du montage ;
5. comparaison manuelle du début et de la fin de chaque partie avec TVDB.

Une durée ou un nombre de fichiers ne suffit jamais à établir une
correspondance exacte.

Si un titre ou une correspondance reste inconnu, Episort doit afficher `—` ou
produire un nom neutre sans titre, par exemple :

```text
Naruto Kai - S01E01.mkv
```

## Workflow fonctionnel envisagé

```text
Scan en lecture seule
  → détection d'un indice Kai
  → proposition, jamais sélection automatique
  → choix ou import du profil exact
  → association à la série TVDB
  → validation du groupe et du découpage
  → génération du plan source → destination
  → validation explicite de chaque opération
  → exécution dans le répertoire de travail uniquement
```

Les deux validations imposées par Episort restent obligatoires :

1. validation du motif détecté, des groupes, saisons, ordres et ambiguïtés ;
2. validation de toutes les opérations avec leurs chemins source et destination.

## Découpage d'implémentation suggéré

### Version 1

- schéma JSON versionné ;
- import manuel d'un profil ;
- validation structurelle et fonctionnelle ;
- association explicite à TVDB ;
- prévisualisation sans modification des médias.

### Version 2

- assistant en lecture seule pour inventorier les fichiers ;
- lecture des métadonnées et chapitres ;
- génération d'un brouillon de profil ;
- édition et validation manuelles.

### Version 3

- éditeur de profils Kai dans l'interface ;
- gestion de plusieurs versions d'un même montage ;
- export d'un profil nettoyé et partageable.

### Version 4 éventuelle

- dépôt communautaire signé ou versionné ;
- mise à jour contrôlée des profils ;
- AniDB facultatif pour enrichir les titres officiels ;
- intégration optionnelle avec Shoko si un serveur existe déjà.

## Packages envisagés

```text
com.episort.kai
  KaiProfile
  KaiPart
  KaiSourceEpisodeRange
  KaiProfileRepository
  KaiProfileParser
  KaiProfileValidator
  KaiMatchService
```

TVDB reste dans son package actuel. Le package Kai ne doit effectuer aucune
opération de fichiers : il produit uniquement une identité et des propositions
destinées au pipeline d'analyse et de planification existant.

## Tests indispensables

- profil JSON valide et invalide ;
- version de schéma inconnue ;
- identifiant de profil dupliqué ;
- partie Kai dupliquée ;
- saison ou épisode invalide ;
- plage source inversée ou vide ;
- séries ou versions ambiguës ;
- absence de titre sans fabrication de valeur ;
- motif de fichier dangereux ou trop large ;
- correspondance déterministe ;
- aucune opération de média pendant l'import et l'analyse ;
- maintien des deux validations ;
- impossibilité de produire une destination hors du workspace.

## Questions encore ouvertes

1. Quelles éditions Kai précises sont déjà présentes dans la bibliothèque ?
2. Le besoin concerne-t-il uniquement les fan-edits francophones ?
3. Les fichiers possèdent-ils des README, NFO, chapitres ou titres intégrés ?
4. Faut-il nommer les parties par arc, par tome de manga ou par numéro neutre ?
5. Les profils doivent-ils être privés, partageables ou intégrés au projet ?
6. Faut-il produire des fichiers NFO pour Plex/Jellyfin en plus du renommage ?

## Sources consultées

- AniList API — Media :
  https://docs.anilist.co/reference/object/media
- AniList API — conditions :
  https://docs.anilist.co/guide/terms-of-use
- AniDB API :
  https://wiki.anidb.net/API
- AniDB HTTP API :
  https://wiki.anidb.net/HTTP_API_Definition
- AniDB UDP API :
  https://wiki.anidb.net/UDP_API_Definition
- AniDB — titres :
  https://wiki.anidb.net/Content%3ATitles
- TVmaze API :
  https://www.tvmaze.com/api
- TMDB — TV Episode Groups :
  https://developer.themoviedb.org/reference/tv-episode-group-details
- Fanedit.org IFDB :
  https://ifdb.fanedit.org/ifdb/
- PlexAniBridge Mappings :
  https://github.com/eliasbenb/PlexAniBridge-Mappings
- Jellyfin — métadonnées NFO :
  https://jellyfin.org/docs/general/server/metadata/nfo/
