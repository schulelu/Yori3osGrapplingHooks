import java.security.MessageDigest

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.yori3o.yo_hooks"
version = providers.gradleProperty("plugin_version").get()

val pluginVersion = version.toString()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val githubRepo = providers.gradleProperty("github_repo").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paper_api").get()}")
    // The pure logic tests touch enums such as Material, which load without a server
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("paper_api").get()}")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.test {
    useJUnitPlatform()
}

// --- resource pack: the mod's own textures, models, sounds and names (MIT, see LICENSE) ---

val modAssets = layout.projectDirectory.dir("../src/main/resources/assets/yo_hooks")
val modernAssets = layout.projectDirectory.dir("../src/modern/resources/assets/yo_hooks")
val packFileName = "YoHooks-resourcepack-$pluginVersion.zip"

val resourcePack = tasks.register<Zip>("resourcePack") {
    description = "Builds the optional client resource pack."
    archiveFileName = packFileName
    destinationDirectory = layout.buildDirectory.dir("pack")
    // Reproducible: the same sources always give the same sha1, locally and on CI
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.projectDirectory.dir("pack"))
    from(modAssets) {
        into("assets/yo_hooks")
    }
    // The mod's item definitions use its client-side "yo_hooks:hook_active" property, which a vanilla client
    // can't read; the pack instead overrides the vanilla fishing rod and tripwire hook (see pack/assets/minecraft).
    from(modernAssets) {
        into("assets/yo_hooks")
        exclude("items/**")
    }
    from(layout.projectDirectory.file("../src/main/resources/logo.png")) {
        rename { "pack.png" }
    }
    from(layout.projectDirectory.file("../LICENSE"))
}

val packInfo = tasks.register("packInfo") {
    description = "Writes the pack's download URL and sha1 for the plugin to offer."
    val zip = resourcePack.flatMap { it.archiveFile }
    val out = layout.buildDirectory.file("generated/packinfo/yohooks-pack.properties")
    val url = "https://github.com/$githubRepo/releases/download/paper-v$pluginVersion/$packFileName"
    inputs.file(zip)
    inputs.property("url", url)
    outputs.file(out)
    doLast {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(zip.get().asFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        out.get().asFile.apply {
            parentFile.mkdirs()
            writeText("url=$url\nsha1=$sha1\n")
        }
    }
}

tasks.processResources {
    val props = mapOf("version" to pluginVersion, "minecraft" to minecraftVersion)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
    from(packInfo)
    from(layout.projectDirectory.file("../LICENSE"))
}

tasks.jar {
    archiveFileName = "YoHooks-Paper-$pluginVersion.jar"
}

tasks.assemble {
    dependsOn(resourcePack)
}

tasks.runServer {
    minecraftVersion(minecraftVersion)
}
