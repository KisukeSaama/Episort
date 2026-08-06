EPISORT 0.1.0 - PORTABLE APPLICATION
====================================

No Java installation is required. Keep the extracted Episort folder intact:
the launcher uses the bundled runtime and libraries beside it.

WINDOWS
-------
Double-click Episort.exe.

LINUX
-----
Run this command from a terminal:

    ./bin/Episort

The bundle targets x64 glibc-based desktop Linux systems with GTK 3 available.

If needed, restore its executable permission first:

    chmod +x ./bin/Episort

TVDB
----
Episort does not contain an API key. Define TVDB_API_KEY in the environment
before launching the application. Never place a private key in a media folder.

Windows PowerShell:

    $env:TVDB_API_KEY = "your-key"
    .\Episort.exe

Linux:

    TVDB_API_KEY="your-key" ./bin/Episort

FRANCAIS
--------
Aucune installation de Java n'est necessaire. Conservez tout le dossier
Episort extrait. Sous Windows, double-cliquez sur Episort.exe. Sous Linux,
executez ./bin/Episort. La variable TVDB_API_KEY doit etre definie avant le
lancement si vous souhaitez utiliser les metadonnees TVDB.
