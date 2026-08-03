import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.1"
}

val h2OriginalPackagePath = "org/h2/"
val h2RelocatedPackagePath = "com/blakube/bktops/libs/h2/"
val h2DataZipPath = h2RelocatedPackagePath + "util/data.zip"

/**
 * H2 ships its resources (error messages, help.csv, web console assets) inside a nested
 * `org/h2/util/data.zip`. Shadow relocates the classes and the string constants used to look
 * those resources up, but it cannot see inside the nested zip, so every lookup misses and H2
 * reports "(Message XXXXX not found)" instead of the real error. Rewrite the nested entry names
 * with the same relocation so H2 can find them again.
 */
fun relocateNestedZipEntries(bytes: ByteArray): ByteArray {
    val result = ByteArrayOutputStream()
    ZipOutputStream(result).use { out ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = if (entry.name.startsWith(h2OriginalPackagePath)) {
                    h2RelocatedPackagePath + entry.name.substring(h2OriginalPackagePath.length)
                } else {
                    entry.name
                }
                out.putNextEntry(ZipEntry(name))
                input.copyTo(out)
                out.closeEntry()
            }
        }
    }
    return result.toByteArray()
}

fun relocateH2Resources(jar: File) {
    val temp = File(jar.parentFile, jar.name + ".h2fix")
    var found = false

    ZipFile(jar).use { zip ->
        ZipOutputStream(temp.outputStream().buffered()).use { out ->
            for (entry in zip.entries()) {
                out.putNextEntry(ZipEntry(entry.name))
                if (!entry.isDirectory) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name == h2DataZipPath) {
                        found = true
                        out.write(relocateNestedZipEntries(bytes))
                    } else {
                        out.write(bytes)
                    }
                }
                out.closeEntry()
            }
        }
    }

    if (!found) {
        temp.delete()
        throw GradleException("Expected $h2DataZipPath in ${jar.name}; H2 relocation may have changed")
    }

    jar.delete()
    temp.renameTo(jar)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.11.0")
    implementation(project(":api"))

    implementation("io.github.revxrsal:lamp.common:4.0.0-rc.18")
    implementation("io.github.revxrsal:lamp.bukkit:4.0.0-rc.18")

    implementation("dev.dejvokep:boosted-yaml:1.3.6")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.saicone.rtag:rtag:1.5.11")
    implementation("com.saicone.rtag:rtag-item:1.5.11")

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
        relocate("com.saicone.rtag", "com.blakube.bktops.libs.rtag")

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("**/org/jetbrains/**")
        exclude("**/org/intellij/**")
        exclude("META-INF/MANIFEST.MF")
        exclude("**/*.kotlin_metadata")
        exclude("**/*.kotlin_module")
        exclude("**/*.SF")
        exclude("**/*.DSA")
        exclude("**/*.RSA")

        doLast {
            relocateH2Resources(archiveFile.get().asFile)
        }
    }
    processResources {
        // Without this Gradle does not see the version as an input and keeps a stale
        // plugin.yml from a previous version when only the version number changes.
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
    test {
        useJUnitPlatform()
    }
    build {
        dependsOn(shadowJar)
    }
}
