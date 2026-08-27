# LevelBlock

[![CI](https://github.com/shockbase/LevelBlock/actions/workflows/ci.yml/badge.svg)](https://github.com/shockbase/LevelBlock/actions/workflows/ci.yml)
[![Beta Release](https://github.com/shockbase/LevelBlock/actions/workflows/release.yml/badge.svg)](https://github.com/shockbase/LevelBlock/actions/workflows/release.yml)

PaperMC-Plugin fuer gemeinsames, blockweises Erweitern einer begehbaren Welt.
Das Projekt befindet sich in der Beta-Phase.

## Voraussetzungen

- Paper 26.2
- Java 25
- das zum Release gehoerende LevelBlock-Resourcepack

Der Build verwendet absichtlich die offizielle dynamische Paper-Koordinate
`26.2.build.+`. CI und Releases loesen sie mit `--refresh-dependencies` auf und
kompilieren dadurch gegen den aktuellsten verfuegbaren Paper-26.2-API-Build.

## Installation

1. Plugin-JAR und Resourcepack-ZIP aus dem neuesten
   [GitHub Pre-Release](https://github.com/shockbase/LevelBlock/releases) laden.
2. Die JAR in den Ordner `plugins/` des Paper-Servers legen.
3. Das Resourcepack serverseitig verteilen.
4. Server mit Java 25 starten.

## Spielprinzip

1. `/levelblock lobby` erstellt einen 5x5-Lobbybereich.
2. `/levelblock start` startet den Countdown `5 4 3 2 1 LEVELBLOCK`.
3. Alle Spieler im Lobbybereich werden Mitglieder einer gemeinsamen Session.
4. Jede Session startet pro Welt mit einem freigeschalteten 3x3-Bereich.
5. Eine angrenzende gesperrte X/Z-Saeule kostet den ausloesenden Spieler ein Level.
6. Fortschritt wird fuer alle Mitglieder geteilt und dauerhaft gespeichert.
7. Teleports, Respawns und Rejoins kaufen keine Saeulen; ungueltige Ziele werden korrigiert.

Die Display-Grenzen folgen dem Terrain. Blau kennzeichnet Lobbys, Rot eine
gesperrte Grenze und Gruen eine aktuell bezahlbare Erweiterung.

## Befehle

| Befehl | Bedeutung |
| --- | --- |
| `/levelblock lobby` | 5x5-Lobby erstellen |
| `/levelblock start` | Countdown und Session starten |
| `/levelblock stop` | eigene Session stoppen |
| `/levelblock invite <spieler>` | Spieler einladen |
| `/levelblock join [besitzer\|uuid]` | Einladung annehmen |
| `/levelblock leave` | Session verlassen |
| `/levelblock info` | aktuelle Session anzeigen |
| `/levelblock list` | alle Sessions anzeigen (Admin) |
| `/levelblock info <uuid>` | Session anzeigen (Admin) |
| `/levelblock stop <uuid>` | Session stoppen (Admin) |
| `/levelblock delete <uuid>` | Session loeschen (Admin) |

Alias: `/lb`

Permissions:

- `levelblock.use` - standardmaessig fuer alle Spieler
- `levelblock.admin` - standardmaessig nur fuer Operatoren

## Entwicklung

Die Pakete trennen Plugin-Lifecycle, Commands, Listener, Spiellogik,
Grenzdarstellung und Persistenz. Erweiterungsregeln sind als reine Domain-Logik
ohne laufenden Server testbar. `sessions.yml` besitzt eine Schema-Version und wird
atomar ersetzt, um Teilwrites zu vermeiden.

```bash
./gradlew clean build
```

Ausgaben:

- `build/libs/LevelBlock-<version>.jar`
- `build/distributions/LevelBlock-Resourcepack-<version>.zip`
- `build/reports/tests/test/index.html`
- `build/reports/jacoco/test/html/index.html`

## Beta-Release

Ein Tag im Format `vMAJOR.MINOR.PATCH-beta.NUMMER` startet den Release-Workflow:

```bash
git tag v0.1.0-beta.1
git push origin v0.1.0-beta.1
```

GitHub testet und baut selbst gegen die aktuelle Paper-26.2-API und erstellt
anschliessend ein als Pre-Release markiertes Release mit JAR und Resourcepack.
