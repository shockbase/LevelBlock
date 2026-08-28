import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()
evaluationDependsOn(":common")
val commonSourceSets = project(":common").extensions.getByType<SourceSetContainer>()

sourceSets.main {
    java.srcDir(rootProject.file("fabric-common/src/main/java"))
    resources.srcDir(rootProject.file("resourcepack"))
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation(project(":common"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile", "-parameters"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
    exclude("pack.mcmeta")
}

tasks.named<Jar>("jar") {
    dependsOn(":common:classes")
    archiveBaseName.set("LevelBlock-Client")
    from(commonSourceSets.named("main").map { it.output })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
