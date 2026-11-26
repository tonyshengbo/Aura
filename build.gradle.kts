plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "com.auracode.assistant"
// 插件发布版本：Marketplace 上传包、GitHub Release 与插件描述版本均以此为准
version = "1.1.1"

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
    exclude("**/NativeEditorInputPlatformTest.class")
    exclude("**/NativeTextInputUiTest.class")
    exclude("**/ConversationTitleUiTest.class")
    exclude("**/WindowPopupUiTest.class")
    exclude("**/WindowPopupVisualTest.class")
}

val nativeInputPlatformTest by intellijPlatformTesting.testIde.registering {
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    task {
        include("**/NativeEditorInputPlatformTest.class")
    }
}

// 需要桌面会话：实际挂载 ComposePanel 与原生编辑器，验证渲染、输入和遮罩显隐。
val nativeInputUiTest by intellijPlatformTesting.testIde.registering {
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    task {
        include("**/NativeTextInputUiTest.class")
        include("**/ConversationTitleUiTest.class")
        include("**/WindowPopupUiTest.class")
        systemProperty("java.awt.headless", "false")
    }
}

// 使用生产环境的 Skia 画布检查透明窗口；Swing 离屏渲染会掩盖不透明画布导致的黑边。
val windowPopupVisualTest by intellijPlatformTesting.testIde.registering {
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    task {
        include("**/WindowPopupVisualTest.class")
        systemProperty("java.awt.headless", "false")
        systemProperty("compose.swing.render.on.graphics", "false")
    }
}

tasks.check {
    dependsOn(tasks.named("nativeInputPlatformTest"))
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
        }
        changeNotes = """
            <p>Aura Code 1.1.1 adds configurable global system prompts and file drag-and-drop attachments.</p>
            <ul>
              <li>Adds a global system prompt setting so shared role instructions are applied to both Codex and Claude sessions.</li>
              <li>Supports dropping local files onto the tool window to add them as attachments, with hover feedback and filtering of folders and invalid paths.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            create("IU", "2024.3.4.1")
        }
    }
}
