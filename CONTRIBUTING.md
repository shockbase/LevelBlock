# Mitwirken

Voraussetzungen: Java 25 und der enthaltene Gradle Wrapper.

```bash
./gradlew clean build
```

Der Build muss inklusive Tests erfolgreich sein. Pull Requests sollten eine klar
abgegrenzte Aenderung enthalten und neue Spiellogik mit Tests absichern.

Beta-Releases werden ausschliesslich ueber Tags im Format
`v0.1.0-beta.1` erzeugt. Der GitHub-Workflow baut, testet und veroeffentlicht
Plugin-JAR und Resourcepack als Pre-Release.
