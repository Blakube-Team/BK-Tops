plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":api"))

    implementation("io.github.revxrsal:lamp.common:4.0.0-rc.12")
    implementation("io.github.revxrsal:lamp.bukkit:4.0.0-rc.12")

    implementation("dev.dejvokep:boosted-yaml:1.3.6")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.zaxxer:HikariCP:7.0.2")

    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.1")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")

    compileOnly("com.github.UlrichBR:UClans-API:8.4.0")
    compileOnly("net.sacredlabyrinth.phaed.simpleclans:SimpleClans:2.19.2")
    compileOnly("com.github.booksaw:BetterTeams:4.13.4")
    compileOnly("dev.kitteh:factions:4.0.0")
    compileOnly("com.palmergames.bukkit.towny:towny:0.101.2.0")
    compileOnly("world.bentobox:bentobox:3.7.3-SNAPSHOT")
    compileOnly("com.bgsoftware:SuperiorSkyblockAPI:2025.1")
    compileOnly("com.github.angeschossen:LandsAPI:7.17.2")
    compileOnly(files("../libs/KingdomsX.jar"))
}

tasks {

    withType<JavaCompile> {
        options.compilerArgs.add("-XDstringConcat=inline")
    }

    shadowJar {
        archiveFileName.set("BK-Tops-${project.version}.jar")
        archiveClassifier.set("")

        relocate("org.h2", "com.blakube.bktops.libs.h2")
        relocate("revxrsal.commands", "com.blakube.bktops.libs.lamp")
        relocate("dev.dejvokep.boostedyaml", "com.blakube.bktops.libs.boostedyaml")

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("**/org/jetbrains/**")
        exclude("**/org/intellij/**")
        exclude("META-INF/MANIFEST.MF")
        exclude("**/*.kotlin_metadata")
        exclude("**/*.kotlin_module")
        exclude("**/*.SF")
        exclude("**/*.DSA")
        exclude("**/*.RSA")
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
    build {
        dependsOn(shadowJar)
    }
}