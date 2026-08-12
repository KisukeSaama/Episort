EPISORT 0.1.2 - PORTABLE APPLICATION
====================================

No Java installation is required. The public download is one executable. On
first launch, it extracts its verified private runtime into the user application
data directory; nothing is created beside the downloaded executable.

WINDOWS
-------
Double-click Episort-0.1.2-windows-x64.exe. Runtime files are stored under
%LOCALAPPDATA%\Episort.

LINUX
-----
Run this command from a terminal:

    chmod +x Episort-0.1.2-linux-x64
    ./Episort-0.1.2-linux-x64

Runtime files are stored under ${XDG_DATA_HOME:-~/.local/share}/Episort. The
bundle targets x64 glibc-based desktop Linux systems with GTK 3 available.

TMDB
----
TMDB access is provided through the Janus gateway and is configured in this
official distribution. No TMDB or Janus account and no .env file are required.

FRANCAIS
--------
Aucune installation de Java n'est necessaire. Le telechargement public contient
un seul executable. Au premier lancement, le runtime est extrait dans
%LOCALAPPDATA%\Episort sous Windows ou
${XDG_DATA_HOME:-~/.local/share}/Episort sous Linux. L'acces TMDB passe par
Janus et est deja configure. Aucun compte ni fichier .env n'est necessaire.
