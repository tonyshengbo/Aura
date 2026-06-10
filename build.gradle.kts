plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "com.auracode.assistant"
version = "1.0.1"

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
            <p>Aura Code 1.0.0 is the first stable release of the dual-engine Codex and Claude workspace for IntelliJ IDEA.</p>
            <ul>
              <li>Unifies Codex and Claude sessions in one native tool window with project-scoped history, multi-tab conversations, and background execution awareness.</li>
              <li>Includes plan mode, approval prompts, tool user input, file context, edited-file review, Skills, MCP server management, and token usage views.</li>
              <li>Supports local runtime setup for Codex CLI, Claude CLI, and optional Node paths across stable GitHub Release and Marketplace distribution.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            create("IU", "2024.3.4.1")
        }
    }
}
