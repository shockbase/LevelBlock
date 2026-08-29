plugins {
    base
}

group = "de.shockbase"
val levelBlockVersion = providers.gradleProperty("levelBlockVersion").get()
val minecraftCompatibility = providers.gradleProperty("minecraftVersion").get()
val loaderCompatibility = providers.gradleProperty("fabricLoaderVersion").get()
val apiCompatibility = providers.gradleProperty("fabricApiVersion").get().substringBefore("+")
version = "$levelBlockVersion+mc.$minecraftCompatibility.loader.$loaderCompatibility.api.$apiCompatibility"

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
