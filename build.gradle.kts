plugins {
    base
}

group = "de.shockbase"
version = providers.environmentVariable("RELEASE_VERSION")
    .orElse(providers.gradleProperty("releaseVersion"))
    .getOrElse("0.1.0-beta.2-SNAPSHOT")

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

val releaseMods = tasks.register<Sync>("releaseMods") {
    group = "build"
    description = "Sammelt die getrennten Server- und Client-Mods."
    dependsOn(":server:build", ":client:build")
    into(layout.buildDirectory.dir("libs"))
    from(project(":server").layout.buildDirectory.dir("libs")) {
        include("LevelBlock-Server-*.jar")
        exclude("*-sources.jar")
    }
    from(project(":client").layout.buildDirectory.dir("libs")) {
        include("LevelBlock-Client-*.jar")
        exclude("*-sources.jar")
    }
}

tasks.assemble {
    dependsOn(releaseMods)
}

tasks.build {
    dependsOn(releaseMods, ":common:check")
}
