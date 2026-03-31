# Aura Code (English)

For Chinese documentation, see: [README.zh.md](README.zh.md)

## Overview

Aura Code is an IntelliJ IDEA plugin powered by the local Codex runtime. It provides a native chat tool window inside IDEA, keeps project-local conversation history, and reuses the stored session `thread_id` for multi-turn continuation.

## Current Features

- `Aura Code` tool window for project-scoped conversations
- Streaming responses with cancel support
- Native multi-turn continuation through the stored session `thread_id`
- Context support for current file, manual file context, mentions, and attachments
- Project-local session persistence and restore
- Composer controls for model, reasoning, mode, and pending queue
- Local MCP JSON management
- Local Skills management and slash entry support
- Edited file aggregation and diff entry points
- Cmd/Ctrl+K quick-open shortcut

## Requirements

- IntelliJ IDEA 2023.3+
- JDK 17
- Local `codex` CLI available on `PATH`, or configured in `Settings -> Tools -> Aura Code`

## Build and Install

1. Build the plugin:
```bash
./gradlew buildPlugin
```
2. Find the ZIP in `build/distributions/`.
3. Install ZIP in IntelliJ IDEA.
4. Configure the Codex runtime path in `Settings -> Tools -> Aura Code` if the executable is not already on `PATH`.

## Usage

1. Open `View -> Tool Windows -> Aura Code`
2. Configure the local Codex runtime path if needed
3. Enter a prompt and send
4. Optionally add context files or attachments
5. Continue in the same session to reuse native conversation state

## Debugging Guide

### 1) Local plugin debugging (recommended)

```bash
./gradlew runIde
```

- This launches a sandbox IntelliJ instance.
- Open any project in sandbox and test the Tool Window flows.
- Best for UI behavior, streaming, command confirmation, and diff apply debugging.

### 2) Build failure diagnostics

```bash
./gradlew buildPlugin --stacktrace
./gradlew buildPlugin --info
```

Check:
- IntelliJ platform/plugin compatibility in `build.gradle.kts`
- Local JDK version (must be 17)
- Network access to JetBrains Maven repositories

### 3) Codex runtime diagnostics

Verify in plugin settings:
- Codex runtime path is correct
- `codex exec --help` works in your local environment
- The CLI account/session is already authenticated outside the plugin

### 4) Runtime issue diagnostics

In sandbox IntelliJ, open logs:
- `Help -> Show Log in Finder/Explorer`
- Focus on stack traces, `Codex cli raw:` lines, and `Codex cli summary:` lines

### 5) Session/state checks

- Reopen project and verify session restore.
- Cancel during streaming and verify request termination.
- Ask a follow-up in the same session and verify the plugin reuses the stored CLI `thread_id`.
- Restart IDEA and verify composer model / reasoning selections are restored.

## Safety Model

- Session history is scoped to the current saved session only.
- Native continuation is driven by the stored session `thread_id` in that session.

## Current Scope

- This release targets IntelliJ IDEA.
- Marketplace signing and publishing are out of scope for this ZIP-only release.
