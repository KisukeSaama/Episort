# Episort

Episort est une application desktop JavaFX, pensee pour Windows, qui aide a organiser des episodes de series TV depuis un dossier de travail. Elle scanne les fichiers video, regroupe les contenus probables, s'appuie sur TVDB pour les metadonnees, puis prepare un plan de rangement a valider avant toute operation fichier.

## Fonctionnalites

- Scan des fichiers `.avi`, `.mp4` et `.mkv`.
- Detection de dossiers mixtes pouvant contenir plusieurs series.
- Recherche TVDB et prise en charge des ordres aired, DVD et absolute.
- Validation en deux temps : motifs/groupes detectes, puis plan exact source -> destination.
- Operations bornees au dossier de travail configure.
- Assistant IA local optionnel pour suggerer ou expliquer des motifs, sans pouvoir approuver ni executer.

Format cible :

```text
Series Name in English/
  Season XX/
    Series Name in English - SXXEXX - Episode Title in English.original-extension
```

## Stack

- Java 21
- JavaFX
- Gradle
- JUnit 5

## Commandes

```bash
./gradlew run
./gradlew test
./gradlew build
```

Sous Windows PowerShell :

```powershell
.\gradlew.bat run
.\gradlew.bat test
.\gradlew.bat build
```

## Configuration

Les identifiants TVDB ne doivent pas etre versionnes. Utiliser une variable d'environnement ou un fichier de configuration ignore.

L'IA locale utilise Qwen3 8B via un runtime embarque llama.cpp. Le modele est telecharge une fois dans `%LOCALAPPDATA%\Episort\models\`. Si le runtime ou le modele est indisponible, l'application reste utilisable sans les fonctions IA.

Pour preparer une distribution avec le runtime embarque :

```powershell
.\gradlew.bat fetchLlamaRuntime
.\gradlew.bat installDist
```

Les tests LLM reels sont des smoke tests optionnels :

```powershell
.\gradlew.bat test -PrunLocalLlm=true
```

## Documentation

- Design system : `docs/design-system.md`
- Artefacts BMAD : `_bmad-output/`
