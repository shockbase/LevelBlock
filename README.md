# LevelBlock

[![CI](https://github.com/shockbase/LevelBlock/actions/workflows/ci.yml/badge.svg)](https://github.com/shockbase/LevelBlock/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/shockbase/LevelBlock?display_name=tag&sort=semver&label=release)](https://github.com/shockbase/LevelBlock/releases/latest)
[![Fabric Compatibility](https://github.com/shockbase/LevelBlock/actions/workflows/fabric-update-release.yml/badge.svg?branch=main)](https://github.com/shockbase/LevelBlock/actions/workflows/fabric-update-release.yml)

LevelBlock ist eine Fabric-Mod für gemeinsame Survival-Runden, in denen die
begehbare Welt säulenweise freigeschaltet wird. Jede neue angrenzende
X/Z-Säule kostet ein Erfahrungslevel. Fortschritt und freigeschaltete Säulen
werden für alle Mitglieder einer Session geteilt.

Das Projekt ist von [BastiGHGs **„Level = Block XXL Duo Challenge“**](https://www.youtube.com/watch?v=yahCECwHhzQ) inspiriert:

LevelBlock besteht aus zwei getrennten Dateien:

| Datei | Installationsort | Aufgabe |
| --- | --- | --- |
| `LevelBlock-Server-VERSION.jar` | Fabric-Server | Sessions, Befehle, Fortschritt und serverseitige Validierung |
| `LevelBlock-Client-VERSION.jar` | Jeder Minecraft-Client | Blockähnliche Spielerkollision und Darstellung der Begrenzung |

Die Server-Mod allein reicht nicht aus. Jeder Spieler benötigt die passende
Client-Mod. Ein separates Resourcepack ist nicht erforderlich.

## Download

Die fertigen Dateien befinden sich unter
[GitHub Releases](https://github.com/shockbase/LevelBlock/releases).

Für eine Installation immer beide Dateien aus demselben Release verwenden:

- `LevelBlock-Server-1.0.3+mc.26.2.loader.0.19.3.api.0.159.0.jar`
- `LevelBlock-Client-1.0.3+mc.26.2.loader.0.19.3.api.0.159.0.jar`

Die Versionsnummer enthält direkt die getestete Zielumgebung:

```text
1.0.3+mc.26.2.loader.0.19.3.api.0.159.0
│     │       │             └─ Fabric API
│     │       └─ Fabric Loader
│     └─ Minecraft
└─ LevelBlock
```

Dadurch erkennen Admins und Spieler bereits am Dateinamen, welche Minecraft-,
Loader- und Fabric-API-Version zusammengehören.

## Kompatibilität

| Komponente | Anforderung |
| --- | --- |
| LevelBlock | `1.0.3` |
| Minecraft | `26.2` |
| Java | `25` |
| Getesteter Fabric Loader | `0.19.3` |
| Getestete Fabric API | `0.159.0+26.2` |

Die maßgeblichen Versionen stehen in
[`gradle.properties`](gradle.properties). Automatisch erstellte
Fabric-Kompatibilitätsreleases übernehmen diese Werte in Mod-Version,
Dateinamen und Release Notes.

Server, Clients und Fabric API müssen dieselbe Minecraft-Version unterstützen.
Für Server und Clients sollte außerdem immer dieselbe LevelBlock-Version
verwendet werden. Bei einem inkompatiblen Netzwerkprotokoll trennt der Server
die Verbindung.

## Installation für Server-Admins

### 1. Server sichern und stoppen

Vor Installation oder Update:

1. Server vollständig stoppen.
2. Weltordner sichern.
3. Falls LevelBlock bereits installiert ist, zusätzlich
   `config/levelblock/sessions.json` sichern.

LevelBlock sollte nicht bei laufendem Server ausgetauscht werden.

### 2. Fabric-Server vorbereiten

Einen Fabric-Server für Minecraft `26.2` mit Java `25` installieren. Die
offizielle Fabric-Dokumentation befindet sich unter
[docs.fabricmc.net/players](https://docs.fabricmc.net/players/).

Der Server muss sich einmal erfolgreich starten lassen, bevor LevelBlock
hinzugefügt wird. Dadurch wird unter anderem der Ordner `mods/` angelegt.

### 3. Server-Mod und Fabric API installieren

Folgende Dateien in den serverseitigen Ordner `mods/` legen:

```text
server/
├─ mods/
│  ├─ fabric-api-VERSION+26.2.jar
│  └─ LevelBlock-Server-VERSION.jar
├─ server.properties
└─ ...
```

Nicht auf den Server gehören:

- `LevelBlock-Client-VERSION.jar`
- ein separates LevelBlock-Resourcepack
- alte Paper-/Bukkit-Versionen von LevelBlock

### 4. Server starten und Installation prüfen

Nach dem Start muss im Serverlog folgende Meldung erscheinen:

```text
LevelBlock Server gestartet.
```

Zusätzlich prüfen:

```mcfunction
/levelblock help
```

Der Alias `/lb help` funktioniert ebenfalls. Admin-Befehle benötigen
Minecraft-Operatorrechte auf Gamemaster-Stufe.

### 5. Client-Datei verteilen

Allen Spielern die `LevelBlock-Client-VERSION.jar` aus demselben Release
bereitstellen. Ohne Client-Mod trennt der Server den Spieler nach ungefähr fünf
Sekunden mit folgender Meldung:

```text
Für diesen Server wird die LevelBlock-Client-Mod benötigt.
```

Bei einer inkompatiblen Client-/Server-Kombination erscheint:

```text
LevelBlock Client/Server-Protokoll ist nicht kompatibel.
```

## Installation für Spieler

### 1. Fabric Loader installieren

Minecraft `26.2` im offiziellen Launcher mindestens einmal starten und wieder
schließen. Danach den Fabric Loader für dieselbe Minecraft-Version installieren.
Für Windows gibt es eine
[offizielle Fabric-Anleitung](https://docs.fabricmc.net/players/installing-fabric/windows).

### 2. Benötigte Mods installieren

In den `mods/`-Ordner der verwendeten Minecraft-Instanz gehören:

```text
.minecraft/
└─ mods/
   ├─ fabric-api-VERSION+26.2.jar
   └─ LevelBlock-Client-VERSION.jar
```

Bei Prism Launcher, Modrinth App oder anderen Launchern besitzt jede Instanz
einen eigenen `mods/`-Ordner. Die Datei muss in der tatsächlich gestarteten
Instanz liegen.

Nicht auf den Client gehört `LevelBlock-Server-VERSION.jar`.

### 3. Spiel starten und verbinden

Im Launcher das Fabric-Profil auswählen. Nach dem Beitritt synchronisiert der
Server Lobbys, Session und freigeschaltete Säulen automatisch.

Die Client-Mod enthält bereits:

- den Shader für die animierte Grenzdarstellung,
- die terrainabhängige Darstellung,
- die lokale blockähnliche Kollision.

Ein Resourcepack-Download ist daher nicht erforderlich.

## Spielprinzip

1. Ein Spieler erstellt mit `/levelblock lobby` an seiner aktuellen Position
   einen 5×5-Lobbybereich.
2. `/levelblock start` startet den Countdown.
3. Alle geeigneten Spieler im Lobbybereich werden in eine gemeinsame Session
   aufgenommen und gemeinsam in den Startbereich versetzt.
4. Die Session beginnt mit einem freigeschalteten 3×3-Bereich um den
   Mittelpunkt der Lobby.
5. Beim Betreten einer angrenzenden gesperrten Säule wird dem auslösenden
   Spieler ein Erfahrungslevel abgezogen und die Säule für die gesamte Session
   freigeschaltet.
6. Fortschritt gilt dimensionsbezogen und wird dauerhaft gespeichert.

### Grenzfarben

| Farbe | Bedeutung |
| --- | --- |
| Aqua | Lobbybereich |
| Gold | Standardfarbe der Sessiongrenze |
| Grün | Die unmittelbar angrenzende Säulenseite kann mit einem Erfahrungslevel freigeschaltet werden |
| Rot | Die unmittelbar angrenzende Säulenseite kann aktuell nicht freigeschaltet werden |

Nach einer erfolgreichen Freischaltung blitzt die gesamte Sessiongrenze kurz
grün auf und blendet anschließend wieder zu Gold über.

Die Grenze ist keine Minecraft-WorldBorder und besteht nicht aus
Barriereblöcken. Sie schränkt nur die Bewegung des lokalen Spielers mit
blockähnlicher Kollision ein. Dadurch bleiben unter anderem folgende
Interaktionen nutzbar:

- Angeln,
- Nahkampf und Waffen,
- Projektile,
- Block- und Entity-Raycasts,
- Monsterbewegung.

## Befehle

Alle Spielerbefehle müssen im Spiel ausgeführt werden.

| Befehl | Berechtigung | Bedeutung |
| --- | --- | --- |
| `/levelblock help` | Alle | Kurzübersicht anzeigen |
| `/levelblock lobby` | Alle | 5×5-Lobby an der aktuellen Position erstellen |
| `/levelblock start` | Lobbybesitzer | Countdown der eigenen Lobby starten |
| `/levelblock cancel` | Lobbybesitzer | Eigene Lobby oder laufenden Countdown abbrechen |
| `/levelblock stop` | Sessionbesitzer oder Operator | Eigene Session stoppen |
| `/levelblock invite <spieler>` | Sessionbesitzer | Online-Spieler einladen |
| `/levelblock join [besitzer\|uuid]` | Eingeladener Spieler | Einladung annehmen |
| `/levelblock leave` | Sessionmitglied | Aktive Session verlassen |
| `/levelblock info` | Sessionmitglied | Eigene Session anzeigen |
| `/levelblock list` | Operator | Alle gespeicherten Sessions anzeigen |
| `/levelblock info <uuid>` | Operator | Bestimmte Session anzeigen |
| `/levelblock stop <uuid>` | Operator | Bestimmte Session stoppen |
| `/levelblock delete <uuid>` | Operator | Gespeicherte Session endgültig löschen |

Alle Befehle sind auch über den Alias `/lb` erreichbar.

Hinweise:

- Nur der Lobbybesitzer kann den Countdown starten.
- Der Besitzer muss sich beim Start in seiner eigenen Lobby befinden.
- Bereits aktive Spieler werden nicht in eine andere Session aufgenommen.
- Ein Sessionbesitzer beendet seine Session mit `/levelblock stop`, statt sie
  mit `/levelblock leave` zu verlassen.
- `delete` entfernt den gespeicherten Sessionfortschritt und sollte nur nach
  einem Backup verwendet werden.

## Daten und Backups

Persistente Daten liegen auf dem Server unter:

```text
config/levelblock/sessions.json
```

Die Datei enthält:

- Session-UUID und Besitzer,
- Mitglieder und offene Einladungen,
- Status der Session,
- Ursprungsposition pro Dimension,
- alle freigeschalteten X/Z-Säulen.

LevelBlock schreibt die Datei über eine temporäre Datei und ersetzt sie danach
atomar, sofern das Dateisystem dies unterstützt. Trotzdem sollte
`sessions.json` Bestandteil der normalen Serverbackups sein.

Lobbys und laufende Countdowns sind temporär und werden nach einem Neustart
nicht wiederhergestellt.

## Update

1. Server stoppen.
2. `config/levelblock/sessions.json` sichern.
3. Alte `LevelBlock-Server-...jar` aus dem Serverordner `mods/` entfernen.
4. Neue Server-JAR aus dem Release einfügen.
5. Fabric API bei Bedarf aktualisieren.
6. Auf allen Clients die alte Client-JAR durch die Client-JAR desselben
   Releases ersetzen.
7. Server und Clients neu starten.

Nicht mehrere LevelBlock-Versionen gleichzeitig im jeweiligen `mods/`-Ordner
liegen lassen.

## Fehlerbehebung

### Server startet nicht

- Minecraft-, Loader-, Fabric-API- und Java-Version vergleichen.
- Prüfen, ob versehentlich die Client-JAR auf dem Server liegt.
- Den vollständigen Crash Report und `logs/latest.log` prüfen.

### Spieler wird nach dem Beitritt getrennt

- Client-JAR im richtigen Instanzordner installiert?
- Fabric API auf dem Client vorhanden?
- Server- und Client-JAR aus demselben Release?
- Client wirklich über das Fabric-Profil gestartet?

### Grenze fehlt oder wird falsch dargestellt

- Sicherstellen, dass die Client-Mod geladen wurde.
- Alte LevelBlock-Client-JARs aus `mods/` entfernen.
- Server und Client vollständig neu starten.
- Grafik- oder Shadermods testweise deaktivieren.

### Befehle fehlen oder Admin-Befehl wird verweigert

- Im Serverlog nach `LevelBlock Server gestartet.` suchen.
- `/levelblock help` statt nur `/levelblock` testen.
- Für Admin-Befehle Operatorrechte vergeben.

### Fortschritt fehlt

- `config/levelblock/sessions.json` und Serverlog prüfen.
- Kontrollieren, ob der Server mit demselben Arbeitsverzeichnis gestartet wurde.
- Eine beschädigte Datei nicht überschreiben; zuerst Backup erstellen.

## Entwicklung

Vollständiger Build:

```bash
./gradlew clean build
```

Ausgaben:

```text
build/libs/LevelBlock-Server-VERSION.jar
build/libs/LevelBlock-Client-VERSION.jar
common/build/reports/tests/test/index.html
common/build/reports/jacoco/test/html/index.html
```

Die IntelliJ-Run-Konfigurationen unter `.run/` bauen Server-Mod, Client-Mod oder
das gesamte Projekt.

## GitHub-Releases

### Manuelles Kompatibilitätsrelease

Ein Tag muss exakt aus den Werten in `gradle.properties` gebildet werden.
`.github/workflows/release.yml` validiert den Tag, führt Tests und Build aus und
veröffentlicht anschließend Server- und Client-JAR:

```bash
git tag -a \
  v1.0.3+mc.26.2.loader.0.19.3.api.0.159.0 \
  -m "LevelBlock 1.0.3 for Minecraft 26.2"
git push origin v1.0.3+mc.26.2.loader.0.19.3.api.0.159.0
```

### Automatisches Release für neue Fabric-Versionen

`.github/workflows/fabric-update-release.yml` läuft täglich und kann zusätzlich
manuell gestartet werden. Der Workflow:

1. liest die aktuelle Minecraft-Version aus `gradle.properties`,
2. ermittelt den neuesten stabilen Fabric Loader über Fabric Meta,
3. ermittelt die neueste dazugehörige Fabric API über Fabric Maven,
4. vergleicht beide Werte mit dem Repository,
5. aktualisiert die Abhängigkeiten nur bei einer Änderung,
6. führt den vollständigen Build und alle Tests aus,
7. bildet die vollständige Kompatibilitätsversion,
8. committet geänderte Fabric-Abhängigkeiten auf `main`,
9. erstellt Tag und GitHub-Release,
10. lädt Server- und Client-JAR hoch.

Der manuelle Workflow-Eingang `force_release` erzeugt bei Bedarf auch ohne neue
Fabric-Abhängigkeit das Release für das aktuelle Ziel, sofern dieser Tag noch
nicht existiert.

Die Automatik aktualisiert bewusst nur Fabric Loader und Fabric API innerhalb
der bereits unterstützten Minecraft-Version. Ein Wechsel auf eine neue
Minecraft-Version kann Quellcode- und Mapping-Anpassungen erfordern und wird
nicht ungeprüft veröffentlicht.

Damit automatische Commits und Releases funktionieren, muss das Repository
unter **Settings → Actions → General → Workflow permissions** Schreibzugriff
für `GITHUB_TOKEN` erlauben.

## Lizenz

LevelBlock ist unter der [GNU General Public License Version 3](LICENSE)
(`GPL-3.0-only`) lizenziert.
