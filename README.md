# LevelBlock

[![CI](https://github.com/shockbase/LevelBlock/actions/workflows/ci.yml/badge.svg)](https://github.com/shockbase/LevelBlock/actions/workflows/ci.yml)
[![Beta Release](https://github.com/shockbase/LevelBlock/actions/workflows/release.yml/badge.svg)](https://github.com/shockbase/LevelBlock/actions/workflows/release.yml)

Fabric-Mod für gemeinsames, säulenweises Erweitern einer begehbaren Welt.

## Voraussetzungen

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3 oder neuer
- Fabric API 0.158.0+26.2 oder neuer

## Installation

1. LevelBlock-Server-VERSION.jar auf dem Server in mods/ legen.
2. LevelBlock-Client-VERSION.jar bei jedem Spieler in mods/ legen.
3. Fabric API auf Server und Clients installieren.

Ein separates Resourcepack ist nicht nötig. Modelle und Texturen sind in der
Client-Mod enthalten. Der Server lehnt Verbindungen ohne passende Client-Mod ab.

## Spielprinzip

1. /levelblock lobby erstellt einen 5x5-Lobbybereich.
2. /levelblock start startet den Countdown.
3. Alle Spieler im Bereich beginnen eine gemeinsame Session mit 3x3 freien Säulen.
4. Jede neue angrenzende X/Z-Säule kostet den auslösenden Spieler ein XP-Level.
5. Fortschritt wird geteilt und atomar in config/levelblock/sessions.json gespeichert.

Die Grenze ist keine WorldBorder und besteht aus keinen Blöcken. Nur die Bewegung
des lokalen Spielers nutzt Vanillas Blockkollision. Angeln, Waffen, Projektile,
Raycasts und Monster bleiben unbeeinflusst. Blau markiert Lobbys, Rot gesperrte
Grenzen und Grün bezahlbare Erweiterungen.

## Befehle

| Befehl | Bedeutung |
| --- | --- |
| /levelblock lobby | 5x5-Lobby erstellen |
| /levelblock start | Countdown und Session starten |
| /levelblock stop | eigene Session stoppen |
| /levelblock invite SPIELER | Spieler einladen |
| /levelblock join [BESITZER oder UUID] | Einladung annehmen |
| /levelblock leave | Session verlassen |
| /levelblock info | aktuelle Session anzeigen |
| /levelblock list | alle Sessions anzeigen (Operator) |
| /levelblock info UUID | Session anzeigen (Operator) |
| /levelblock stop UUID | Session stoppen (Operator) |
| /levelblock delete UUID | Session löschen (Operator) |

Alias: /lb

## Entwicklung

    ./gradlew clean build

Ausgaben:

- build/libs/LevelBlock-Server-VERSION.jar
- build/libs/LevelBlock-Client-VERSION.jar
- common/build/reports/tests/test/index.html
- common/build/reports/jacoco/test/html/index.html

Ein Tag wie v0.1.0-beta.1 erzeugt ein GitHub-Pre-Release mit beiden Mods.
