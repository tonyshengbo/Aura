package com.auracode.assistant.provider

import com.auracode.assistant.model.AgentCollaborationMode
import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.model.ContextFile
import com.auracode.assistant.protocol.ItemKind
import com.auracode.assistant.protocol.ItemStatus
import com.auracode.assistant.protocol.UnifiedToolUserInputAnswerDraft
import com.auracode.assistant.protocol.UnifiedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodexAppServerProviderTest {
    @Test
    fun `thread start sandbox mode uses kebab case protocol enum`() {
        assertEquals("workspace-write", APP_SERVER_THREAD_SANDBOX_MODE)
    }

    @Test
    fun `turn start sandbox policy uses camel case protocol enum`() {
        assertEquals("workspaceWrite", APP_SERVER_TURN_SANDBOX_TYPE)
    }

    @Test
    fun `plan collaboration mode serializes required settings payload`() {
        val payload = buildCollaborationModePayloadForTest(
            mode = AgentCollaborationMode.PLAN,
            model = "gpt-5.3-codex",
            reasoningEffort = "medium",
        )

        assertEquals("plan", payload?.get("mode")?.toString()?.trim('"'))
        val settings = payload?.get("settings")?.jsonObject
        assertEquals("gpt-5.3-codex", settings?.get("model")?.toString()?.trim('"'))
        assertEquals("medium", settings?.get("reasoning_effort")?.toString()?.trim('"'))
        assertEquals("null", settings?.get("developer_instructions")?.toString())
    }

    @Test
    fun `file change kind parser accepts object payload`() {
        val json = Json.parseToJsonElement(
            """{"type":"update","move_path":null}""",
        ).jsonObject

        assertEquals("update", extractFileChangeKindForTest(json))
    }

    @Test
    fun `command execution write does not emit additional diff apply item`() {
        val events = parseAppServerNotificationForTest(
            requestId = "req-1",
            method = "item/completed",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "commandExecution")
                        put("id", "call_1")
                        put("status", "completed")
                        put("command", "/bin/zsh -lc \"cat > /tmp/SingletonDemo.java <<'EOF'\nclass A {}\nEOF\"")
                        put("cwd", "/tmp")
                        put("aggregatedOutput", "")
                        put(
                            "commandActions",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "unknown")
                                        put("command", "cat > /tmp/SingletonDemo.java <<'EOF'\nclass A {}\nEOF")
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        assertEquals(1, events.size)
        val commandEvent = assertIs<UnifiedEvent.ItemUpdated>(events.single())
        assertEquals(ItemKind.COMMAND_EXEC, commandEvent.item.kind)
    }

    @Test
    fun `file change completion preserves previously parsed structured changes when completed payload omits changes`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        val started = parser.parseNotification(
            method = "item/started",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "fileChange")
                        put("id", "fc_1")
                        put("status", "started")
                        put(
                            "changes",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("path", "/tmp/hello.kt")
                                        put("kind", "update")
                                        put("oldContent", "fun a() = 1\n")
                                        put("newContent", "fun a() = 2\nfun b() = 3\n")
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        val completed = parser.parseNotification(
            method = "item/completed",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "fileChange")
                        put("id", "fc_1")
                        put("status", "completed")
                        put("output", "update /tmp/hello.kt")
                    },
                )
            },
        )

        val startedItem = assertIs<UnifiedEvent.ItemUpdated>(started.single()).item
        val completedItem = assertIs<UnifiedEvent.ItemUpdated>(completed.single()).item
        assertEquals(ItemKind.DIFF_APPLY, startedItem.kind)
        assertEquals(1, startedItem.fileChanges.size)
        assertEquals(1, completedItem.fileChanges.size)
        val change = completedItem.fileChanges.single()
        assertEquals("/tmp/hello.kt", change.path)
        assertEquals("fun a() = 1\n", change.oldContent)
        assertEquals("fun a() = 2\nfun b() = 3\n", change.newContent)
        assertEquals(2, change.addedLines)
        assertEquals(1, change.deletedLines)
    }

    @Test
    fun `file change parser reads nested payload changes`() {
        val events = parseAppServerNotificationForTest(
            requestId = "req-1",
            method = "item/completed",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "fileChange")
                        put("id", "fc_2")
                        put("status", "completed")
                        put(
                            "payload",
                            buildJsonObject {
                                put(
                                    "changes",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("path", "/tmp/nested.kt")
                                                put("kind", "update")
                                                put("old_content", "a\nb\n")
                                                put("new_content", "a\nc\n")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        val item = assertIs<UnifiedEvent.ItemUpdated>(events.single()).item
        val change = assertNotNull(item.fileChanges.singleOrNull())
        assertEquals("/tmp/nested.kt", change.path)
        assertEquals(1, change.addedLines)
        assertEquals(1, change.deletedLines)
    }

    @Test
    fun `turn plan updated notification maps to running plan event`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        parser.parseNotification(
            method = "turn/started",
            params = buildJsonObject {
                put(
                    "turn",
                    buildJsonObject {
                        put("id", "turn-1")
                        put("threadId", "thread-1")
                    },
                )
            },
        )
        val updated = parser.parseNotification(
            method = "turn/plan/updated",
            params = buildJsonObject {
                put("turnId", "turn-1")
                put("explanation", "Plan updated")
                put(
                    "plan",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("status", "pending")
                                put("step", "First step")
                            },
                        )
                    },
                )
            },
        )

        val runningPlan = assertIs<UnifiedEvent.RunningPlanUpdated>(updated.single())
        assertEquals("thread-1", runningPlan.threadId)
        assertEquals("turn-1", runningPlan.turnId)
        assertEquals("Plan updated", runningPlan.explanation)
        assertEquals(1, runningPlan.steps.size)
        assertEquals("pending", runningPlan.steps.single().status)
        assertEquals("First step", runningPlan.steps.single().step)
        assertTrue(runningPlan.body.contains("- [pending] First step"))
    }

    @Test
    fun `turn diff updated notification maps to unified event`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        val events = parser.parseNotification(
            method = "turn/diff/updated",
            params = buildJsonObject {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put("diff", "diff --git a/foo.kt b/foo.kt")
            },
        )

        val updated = assertIs<UnifiedEvent.TurnDiffUpdated>(events.single())
        assertEquals("thread-1", updated.threadId)
        assertEquals("turn-1", updated.turnId)
        assertEquals("diff --git a/foo.kt b/foo.kt", updated.diff)
    }

    @Test
    fun `context compaction lifecycle notifications map to dedicated item kind`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        parser.parseNotification(
            method = "turn/started",
            params = buildJsonObject {
                put(
                    "turn",
                    buildJsonObject {
                        put("id", "turn-ctx-1")
                        put("threadId", "thread-ctx-1")
                    },
                )
            },
        )

        val started = parser.parseNotification(
            method = "item/started",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "contextCompaction")
                        put("id", "ctx-1")
                    },
                )
            },
        )
        val completed = parser.parseNotification(
            method = "item/completed",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "contextCompaction")
                        put("id", "ctx-1")
                    },
                )
            },
        )

        val startedItem = assertIs<UnifiedEvent.ItemUpdated>(started.single()).item
        val completedItem = assertIs<UnifiedEvent.ItemUpdated>(completed.single()).item
        assertEquals("req-1:ctx-1", startedItem.id)
        assertEquals(ItemKind.CONTEXT_COMPACTION, startedItem.kind)
        assertEquals(ItemKind.CONTEXT_COMPACTION, completedItem.kind)
        assertEquals("Context Compaction", startedItem.name)
        assertEquals("Compacting context", startedItem.text)
        assertEquals("Context compacted", completedItem.text)
    }

    @Test
    fun `thread compacted updates existing context compaction item instead of creating a second node`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        parser.parseNotification(
            method = "turn/started",
            params = buildJsonObject {
                put(
                    "turn",
                    buildJsonObject {
                        put("id", "turn-ctx-2")
                        put("threadId", "thread-ctx-2")
                    },
                )
            },
        )
        val started = parser.parseNotification(
            method = "item/started",
            params = buildJsonObject {
                put(
                    "item",
                    buildJsonObject {
                        put("type", "contextCompaction")
                        put("id", "ctx-2")
                    },
                )
            },
        )
        val compacted = parser.parseNotification(
            method = "thread/compacted",
            params = buildJsonObject {
                put("threadId", "thread-ctx-2")
                put("turnId", "turn-ctx-2")
            },
        )

        val startedItem = assertIs<UnifiedEvent.ItemUpdated>(started.single()).item
        val compactedItem = assertIs<UnifiedEvent.ItemUpdated>(compacted.single()).item
        assertEquals(startedItem.id, compactedItem.id)
        assertEquals(ItemKind.CONTEXT_COMPACTION, compactedItem.kind)
        assertEquals(ItemStatus.SUCCESS, compactedItem.status)
        assertEquals("Context compacted", compactedItem.text)
    }

    @Test
    fun `request user input response preserves numeric json-rpc id type`() {
        val response = buildRequestUserInputResponseForTest(JsonPrimitive(0))

        assertEquals("0", response.getValue("id").jsonPrimitive.content)
        assertFalse(response.getValue("id").jsonPrimitive.isString)
    }

    @Test
    fun `tool user input prompt parser maps request metadata and question fields`() {
        val prompt = buildToolUserInputPromptForTest(
            serverRequestId = JsonPrimitive(0),
            params = buildJsonObject {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put("itemId", "call-1")
                put(
                    "questions",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "builder_demo_target")
                                put("header", "Target")
                                put("question", "How should I handle the builder demo?")
                                put("isOther", true)
                                put("isSecret", false)
                                put(
                                    "options",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("label", "Reuse existing demo")
                                                put("description", "Keep the current file and refine it")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        assertEquals("0", prompt.requestId)
        assertEquals("thread-1", prompt.threadId)
        assertEquals("turn-1", prompt.turnId)
        assertEquals("call-1", prompt.itemId)
        assertEquals(1, prompt.questions.size)
        val question = prompt.questions.single()
        assertEquals("builder_demo_target", question.id)
        assertTrue(question.isOther)
        assertFalse(question.isSecret)
        assertEquals("Reuse existing demo", question.options.single().label)
    }

    @Test
    fun `tool user input response serializes answers map preserving numeric json rpc id type`() {
        val response = buildToolUserInputResponseForTest(
            serverRequestId = JsonPrimitive(0),
            submission = mapOf(
                "builder_demo_target" to UnifiedToolUserInputAnswerDraft(
                    answers = listOf("Reuse existing demo"),
                ),
            ),
        )

        assertEquals("0", response.getValue("id").jsonPrimitive.content)
        assertFalse(response.getValue("id").jsonPrimitive.isString)
        val answers = response.getValue("result").jsonObject.getValue("answers").jsonObject
        val builderAnswer = answers.getValue("builder_demo_target").jsonObject
        assertEquals(listOf("Reuse existing demo"), builderAnswer.getValue("answers").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `historical request user input item restores dedicated user input summary`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        val events = parser.parseHistoricalTurn(
            buildJsonObject {
                put("id", "turn-1")
                put("threadId", "thread-1")
                put("status", "completed")
                put(
                    "items",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "requestUserInput")
                                put("id", "call-1")
                                put("status", "completed")
                                put(
                                    "questions",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", "builder_demo_target")
                                                put("header", "Target")
                                                put("question", "How should I handle the builder demo?")
                                                put("isSecret", false)
                                            },
                                        )
                                    },
                                )
                                put(
                                    "answers",
                                    buildJsonObject {
                                        put(
                                            "builder_demo_target",
                                            buildJsonObject {
                                                put(
                                                    "answers",
                                                    buildJsonArray {
                                                        add(JsonPrimitive("Reuse existing demo"))
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        val restored = events.filterIsInstance<UnifiedEvent.ItemUpdated>()
            .single { it.item.kind == ItemKind.USER_INPUT }
            .item
        assertEquals("req-1:call-1", restored.id)
        assertTrue(restored.text.orEmpty().contains("Target"))
        assertTrue(restored.text.orEmpty().contains("Reuse existing demo"))
    }

    @Test
    fun `thread token usage update parser reads total usage and context window`() {
        val parser = CodexAppServerProvider.AppServerNotificationParser(
            requestId = "req-1",
            diagnosticLogger = {},
        )

        val events = parser.parseNotification(
            method = "thread/tokenUsage/updated",
            params = buildJsonObject {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put(
                    "tokenUsage",
                    buildJsonObject {
                        put("modelContextWindow", 258400)
                        put(
                            "total",
                            buildJsonObject {
                                put("inputTokens", 615513)
                                put("cachedInputTokens", 546304)
                                put("outputTokens", 6617)
                            },
                        )
                        put(
                            "last",
                            buildJsonObject {
                                put("inputTokens", 35869)
                                put("cachedInputTokens", 35584)
                                put("outputTokens", 5)
                            },
                        )
                    }
                )
            }
        )

        val usage = assertIs<UnifiedEvent.ThreadTokenUsageUpdated>(events.single())
        assertEquals("thread-1", usage.threadId)
        assertEquals("turn-1", usage.turnId)
        assertEquals(258400, usage.contextWindow)
        assertEquals(615513, usage.inputTokens)
        assertEquals(546304, usage.cachedInputTokens)
        assertEquals(6617, usage.outputTokens)
    }

    @Test
    fun `prompt groups inline snippets separately from read by path contexts`() {
        val prompt = buildPromptForTest(
            AgentRequest(
                engineId = "codex",
                prompt = "summarize",
                contextFiles = listOf(
                    ContextFile(path = "/tmp/Foo.kt:10-12", content = "fun selected() = true"),
                    ContextFile(path = "/tmp/Bar.kt", content = null),
                ),
                workingDirectory = "/tmp",
            ),
        )

        assertTrue(prompt.contains("Context snippets:"))
        assertTrue(prompt.contains("FILE: /tmp/Foo.kt:10-12\nfun selected() = true"))
        assertTrue(prompt.contains("Context files (read by path):"))
        assertTrue(prompt.contains("- /tmp/Bar.kt"))
    }
}
