plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "com.auracode.assistant"
version = "1.1.0"

repositories {
    google()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

configurations.matching { configuration ->
    configuration.isCanBeResolved && configuration.name.contains("runtimeClasspath", ignoreCase = true)
}.configureEach {
    resolutionStrategy.sortArtifacts(org.gradle.api.artifacts.ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
}
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.31.0") {
        exclude(group = "org.jetbrains", module = "markdown")
        exclude(group = "org.jetbrains", module = "markdown-jvm")
    }
    implementation("com.mikepenz:multiplatform-markdown-renderer-m2:0.31.0") {
        exclude(group = "org.jetbrains", module = "markdown")
        exclude(group = "org.jetbrains", module = "markdown-jvm")
    }
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    testRuntimeOnly("net.java.dev.jna:jna:5.14.0")
    testImplementation(kotlin("test"))
    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")
    }
}
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
        }
        changeNotes = """
            <p>Aura Code 1.1.0 improves conversation performance, interaction stability, and runtime compatibility.</p>
            <ul>
              <li>Coalesces high-frequency session updates to reduce unnecessary UI projection and rendering work during streaming responses.</li>
              <li>Improves conversation scrolling, process-card expansion behavior, IME input handling, and tool-window lifecycle management.</li>
              <li>Updates Codex and Claude runtime integration, model catalogs, resource loading, and IntelliJ Platform compatibility.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            create("IU", "2024.3.4.1")
        }
    }
}
