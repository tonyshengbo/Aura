plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    // Includes Skiko 0.9.4.2, which avoids changing NSWindow shadows from the AWT thread.
    id("org.jetbrains.compose") version "1.8.2"
    kotlin("plugin.serialization") version "2.1.21"
}

group = "com.auracode.assistant"
// 插件发布版本：Marketplace 上传包、GitHub Release 与插件描述版本均以此为准
version = "1.2.0"

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
    // Reuse the IDE's coroutine runtime: packaging a second copy breaks its exception-handler SPI
    // when Compose starts/cancels pointer-input coroutines (for example, while zooming a diagram).
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
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
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 平台测试会启动 IntelliJ Application；单独 JVM 执行，避免污染纯 Kotlin 测试的调度环境。
tasks.test {
    exclude("**/ComposeTextInputUiTest.class")
    exclude("**/ConversationTitleUiTest.class")
    exclude("**/ComposePopupUiTest.class")
    exclude("**/ComposePopupVisualTest.class")
}

// 需要桌面会话：挂载生产 ComposePanel，验证 IME、草稿同步、焦点及菜单交互。
val composeInputUiTest by intellijPlatformTesting.testIde.registering {
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    task {
        include("**/ComposeTextInputUiTest.class")
        include("**/ConversationTitleUiTest.class")
        include("**/ComposePopupUiTest.class")
        systemProperty("java.awt.headless", "false")
    }
}

// 使用生产环境的 Skia 画布检查 Compose 菜单的主题与边缘绘制。
val windowPopupVisualTest by intellijPlatformTesting.testIde.registering {
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    task {
        include("**/ComposePopupVisualTest.class")
        systemProperty("java.awt.headless", "false")
        systemProperty("compose.swing.render.on.graphics", "false")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
        }
        changeNotes = """
            <p>Aura Code 1.2.0 adds Mermaid flowcharts, Compose text input, and follow-up messages during execution.</p>
            <ul>
              <li>Renders Mermaid flowcharts locally with zoom, pan, fullscreen, and source fallback.</li>
              <li>Supports sending follow-up messages to running Codex and Claude sessions, plus persistent Codex asynchronous questions.</li>
              <li>Uses Compose text fields and menus with isolated editing state and IME submission protection.</li>
              <li>Improves Markdown tables, conversation titles, scrollable session tabs, and Compose menus.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            create("IU", "2024.3.4.1")
        }
    }
}
