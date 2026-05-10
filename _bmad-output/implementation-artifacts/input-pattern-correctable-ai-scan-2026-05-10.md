# Pattern d'entrée corrigeable et Scan IA

## Implémentation

- Ajout d'un modèle structuré de parse d'entrée (`ScanInputParse`, `ScanInputToken`, `ScanInputRole`, `ScanInputParseSource`).
- Ajout de `ScanInputPatternParser`, logique pure couvrant `SxxExx`, `NxNN`, épisode absolu, série avant marqueur, titre après marqueur, extension et positions.
- `ScanRowFactory` alimente maintenant `inputParse`, le résumé lisible `inputPattern`, `order` et `confidence`.
- `ScanPatternFormatter` priorise les valeurs de `inputParse` avant les fallbacks regex historiques.
- La colonne `Pattern d'entrée` affiche le résumé structuré avec tooltip de positions.
- La colonne `Type` est éditable via ComboBox (`SERIES`, `MOVIE`, `UNKNOWN`, `IGNORED`).
- Le bouton IA est renommé `Proposer` et envoie exactement `Propose un renommage pour tous les fichiers sélectionnés.`
- Le contexte du chat inclut le pattern structuré complet et ses positions.
- Les étapes workflow 1 et 2 sont renommées.
- Le plafond de sortie du chat IA passe à 2048 tokens et le plafond JSON de détection de patterns à 1024 tokens pour éviter les réponses tronquées.
- Le pattern d'entrée est visible en entier dans le tooltip de colonne et dans le panneau de détail, avec édition manuelle et application aux lignes sélectionnées.
- Le sélecteur de type est limité à Série/Film, localisé selon la langue de l'interface, et applique le changement à toutes les lignes sélectionnées quand la ligne active fait partie de la sélection.

## Vérification

- `.\gradlew.bat test`
- `.\gradlew.bat build`
