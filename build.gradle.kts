plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.codex.assistant"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.atlassian.commonmark:commonmark:0.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    testRuntimeOnly("net.java.dev.jna:jna:5.14.0")
    testImplementation(kotlin("test"))
    intellijPlatform {
        intellijIdea("2024.1.7")
        bundledPlugin("com.intellij.java")
        bundledPlugin("Git4Idea")
    }
}
tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    register<JavaExec>("runTimelinePreview") {
        group = "application"
        description = "Run standalone Swing preview for timeline UI debugging"
        classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
        mainClass.set("com.codex.assistant.toolwindow.timeline.TimelinePreviewMainKt")
        jvmArgs(
            "-Djava.awt.headless=false",
            "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
            "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        )
    }

    register<JavaExec>("runToolWindowPreview") {
        group = "application"
        description = "Run standalone Swing preview for full Codex tool window shell"
        classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
        mainClass.set("com.codex.assistant.toolwindow.StandaloneToolWindowPreviewMainKt")
        jvmArgs(
            "-Djava.awt.headless=false",
            "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
            "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        )
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
            untilBuild = "241.*"
        }
    }
}
