plugins {
    java
    jacoco
}

import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

group = "de.shockbase"
version = providers.environmentVariable("RELEASE_VERSION")
    .orElse(providers.gradleProperty("releaseVersion"))
    .getOrElse("0.1.0-beta.1-SNAPSHOT")

val pluginVersion = version.toString()
val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val junitVersion = providers.gradleProperty("junitVersion").get()

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

val resourcePack = tasks.register<Zip>("resourcePack") {
    group = "build"
    description = "Packt das LevelBlock-Resourcepack."
    archiveFileName.set("LevelBlock-Resourcepack-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.projectDirectory.dir("resourcepack"))
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("LevelBlock")
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to pluginVersion,
            "Implementation-Vendor" to "Shockbase"
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.build {
    dependsOn(resourcePack)
}
