# Codex CLI vs App Server Capability Matrix

## Summary

This document compares the current `codex exec --json` integration used by this plugin with the `codex app-server` integration model documented by OpenAI.

It also separates those two Codex-specific integration modes from the generic OpenAI Responses API capabilities, because they are not the same thing:

- `Responses API` gives model/runtime primitives such as conversation continuity, streaming, reasoning, and background execution.
- `codex exec --json` is a CLI execution wrapper model. The CLI owns execution and streams back events/results.
- `codex app-server` is a rich-client protocol model. The client can participate in approvals and other runtime decisions.

## Official References

- Codex App Server:
  - https://developers.openai.com/codex/app-server
- Responses API create:
  - https://developers.openai.com/api/reference/resources/responses/methods/create
- Responses conversation state:
  - https://developers.openai.com/api/docs/guides/conversation-state
- Responses streaming:
  - https://developers.openai.com/api/docs/guides/streaming-responses
- Responses background mode:
  - https://developers.openai.com/api/docs/guides/background

## Current Plugin Reality

Current plugin integration is process-wrapped CLI execution:

- Provider command assembly:
  - [Providers.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/provider/Providers.kt)
- Provider transport:
  - [CliAgentProvider.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/provider/CliAgentProvider.kt)
- Structured event parsing:
  - [CliStructuredEventParser.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/provider/CliStructuredEventParser.kt)
  - [CodexUnifiedEventParser.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/protocol/CodexUnifiedEventParser.kt)
- Runtime execution behavior:
  - [TimelineActionRuntime.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/service/TimelineActionRuntime.kt)
- Approval behavior today:
  - [ApprovalUiPolicy.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/toolwindow/shared/ApprovalUiPolicy.kt)

Important facts from current code:

- The provider invokes `codex exec`.
- The command includes `--dangerously-bypass-approvals-and-sandbox`.
- The plugin consumes event streams after the CLI has already decided what to do.
- The current approval policy always auto-executes / auto-applies proposals.

## Capability Matrix

| Capability | Responses API | `codex exec --json` | `codex app-server` | Notes for this plugin |
| --- | --- | --- | --- | --- |
| Multi-turn continuity | Yes | Yes | Yes | Responses supports `previous_response_id` and conversation state. Current CLI path uses `exec resume <sessionId>`. |
| Streaming output | Yes | Yes | Yes | Responses supports streaming. CLI does it via stdout. App Server does it via protocol events. |
| Reasoning effort | Yes | Yes | Yes | Responses API exposes reasoning effort. Current CLI path already maps `reasoningEffort` into CLI config. |
| Reasoning summary | Yes | Unknown in current CLI path | Potentially available via upstream runtime | Responses API explicitly documents reasoning summaries. Current plugin does not have a direct summary abstraction. |
| Background execution | Yes | No direct plugin control | Possible depending on server/runtime orchestration | Responses documents `background=true`. Current CLI path is foreground process execution. |
| Structured plan updates | Not documented as a generic standard primitive | Partially observed in Codex JSON events | Better fit for protocol delivery | Plugin already parses `plan_update`, but this is not confirmed as a generic Responses primitive. |
| Approval request events | Not documented as a generic standard primitive | Can be surfaced as observed events, but too late for strong control | Yes | Plugin already parses `approval_request`, but CLI wrapper cannot guarantee true pre-execution control. |
| Command pre-approval | No generic built-in workflow contract | No practical client-side pre-intercept | Yes | App Server docs explicitly include command execution approvals. |
| File change pre-approval | No generic built-in workflow contract | No practical client-side pre-intercept | Yes | App Server docs explicitly include file change approvals. |
| Tool user-input loop | Via tools/functions you own | Weak, depends on CLI behavior | Yes | App Server docs explicitly include `tool/requestUserInput`. |
| Client-owned tool execution | Yes, if you build tools yourself on Responses | No, CLI owns execution | Yes | App Server lets the host participate as a rich client. |
| Strong Plan Mode | Must be implemented by host using prompts + host runtime controls | No, only weak best-effort plan mode | Yes | Strong plan mode requires the client to block execution until approval. |
| Weak Plan Mode | Yes | Yes | Yes | Weak means "prompt says plan first", not a hard runtime guarantee. |

## What OpenAI Docs Confirm

### Responses API

Confirmed by docs:

- Multi-turn continuation with `previous_response_id`
- Conversation state support
- Streaming responses
- Background responses
- Reasoning effort and reasoning summary options

Not confirmed as a generic standard Responses primitive:

- `plan_update`
- `approval_request`
- a universal `plan_mode=true`

Implication:

- Responses API gives enough primitives to build a plan experience.
- It does not give a documented universal "plan mode" protocol by itself.

### Codex App Server

Confirmed by docs:

- Rich-client integration is the intended use case.
- Command execution approvals are supported.
- File change approvals are supported.
- `tool/requestUserInput` is supported.
- The server and client exchange ordered protocol messages around approvals.
- `turn/start` supports `collaborationMode`.
- Built-in collaboration presets include `plan` and `default`.
- Plan-mode turns can emit `turn/plan/updated`, `plan` items, and `item/plan/delta`.

Implication:

- App Server is the first integration mode that gives this plugin real pre-execution control points.

## How Plan Mode Is Entered In App Server

This is now confirmed from the app-server schema and core source:

- Plan Mode is not entered via a dedicated `plan/start` method.
- Plan Mode is not the `update_plan` tool.
- Plan Mode is entered by sending `turn/start` with `collaborationMode.mode = "plan"`.

Confirmed source points:

- `TurnStartParams` includes `collaborationMode`
- `ModeKind` is `"plan" | "default"`
- built-in collaboration presets include a `plan` preset
- core runtime switches into plan behavior when `turn_context.collaboration_mode.mode == ModeKind::Plan`

Minimal request shape:

```json
{
  "method": "turn/start",
  "id": 1,
  "params": {
    "threadId": "thread_123",
    "input": [
      {
        "type": "text",
        "text": "First help me make a plan."
      }
    ],
    "collaborationMode": {
      "mode": "plan",
      "settings": {
        "developer_instructions": null
      }
    }
  }
}
```

Important semantics:

- `mode: "plan"` selects Plan Mode.
- `settings.developer_instructions: null` means "use Codex built-in instructions for the selected mode".
- the built-in `plan` instructions come from the upstream `templates/collaboration_mode/plan.md`.
- when the model emits a `<proposed_plan>` block, the app-server can surface it as a `plan` item and `item/plan/delta` stream.

## Practical Conclusions

### If the plugin stays on `codex exec --json`

The plugin can implement only a weak Plan Mode:

- Instruct the model to produce a plan.
- Render `plan_update` if it appears.
- Detect violations after the fact.
- Show approvals as UI.

The plugin cannot reliably guarantee:

- "do not execute before user confirmation"
- "do not modify files in plan mode"
- "do not run commands in plan mode"

Reason:

- The CLI process owns execution and emits results after it has already acted.

### If the plugin moves to `codex app-server`

The plugin can implement a strong Plan Mode:

- Plan generation before execution
- Explicit command approvals
- Explicit file change approvals
- Tool-driven user confirmation flows
- Client-driven transitions from Plan -> Approve -> Execute

## Recommendation

For this codebase:

- Keep `exec --json` only if the goal is "best effort plan UX" with no hard safety guarantee.
- Move to `app-server` if the goal is a real product-grade plan/approval runtime.

The deciding factor is not model intelligence. The deciding factor is runtime control ownership.
