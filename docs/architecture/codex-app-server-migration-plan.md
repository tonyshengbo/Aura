# Codex App Server Migration Plan

## Summary

This document describes how this plugin would migrate from the current `codex exec --json` architecture to a `codex app-server` architecture.

The migration target is not just a different transport. The real goal is to move control authority from the CLI subprocess into the plugin runtime so that approvals and Plan Mode become real runtime controls instead of display-only concepts.

## Current Architecture

Today the plugin works like this:

1. Build an `AgentRequest`
2. Launch `codex exec ... --json`
3. Write prompt to stdin
4. Parse stdout lines into `EngineEvent`
5. Convert them into timeline items

Current key files:

- Provider command assembly:
  - [Providers.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/provider/Providers.kt)
- Process transport:
  - [CliAgentProvider.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/provider/CliAgentProvider.kt)
- Event parsing:
  - [CliStructuredEventParser.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/provider/CliStructuredEventParser.kt)
  - [CodexUnifiedEventParser.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/protocol/CodexUnifiedEventParser.kt)
- Runtime:
  - [AgentChatService.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/service/AgentChatService.kt)
  - [TimelineActionRuntime.kt](/Users/tonysheng/StudioProject/Codex-Assistant/src/main/kotlin/com/codex/assistant/service/TimelineActionRuntime.kt)

### Why this architecture blocks strong Plan Mode

The subprocess decides and executes first, then emits events later.

That means the plugin cannot reliably:

- stop command execution before it happens
- stop file changes before they happen
- enforce "plan only" as a hard guarantee

The plugin can only:

- instruct
- observe
- annotate violations afterward

## Migration Target

The target architecture becomes:

1. Plugin opens a long-lived App Server session
2. Plugin explicitly starts threads and turns
3. App Server emits protocol events
4. Plugin responds to approval and user-input requests
5. Plugin decides when a plan is approved and when execution may continue

This changes the plugin from:

- CLI wrapper

into:

- rich client runtime

## Confirmed Plan-Mode Entry Path

The app-server source confirms that Plan Mode entry is protocol-driven, not prompt-only:

- the client still uses `turn/start`
- the turn enters Plan Mode when `collaborationMode.mode = "plan"`
- there is no separate `plan/start` method
- `update_plan` is unrelated to entering Plan Mode

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
        "text": "Help me design this before implementation."
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

Meaning:

- `mode = "plan"` selects the built-in Plan collaboration mode
- `developer_instructions = null` means "use upstream built-in instructions for that mode"
- those built-in instructions currently come from upstream `templates/collaboration_mode/plan.md`

Expected plan-related notifications during such a turn:

- `turn/plan/updated`
- `item/started` with a `plan` item
- `item/plan/delta`
- `item/completed` for the final `plan` item

This means the plugin does not need to invent a fake "plan request" transport. It needs to:

- expose a Plan toggle in the composer
- map that toggle to `collaborationMode.mode = "plan"`
- render plan notifications as first-class timeline state
- switch back to `default` mode explicitly when the user wants execution

## Module-by-Module Changes

### 1. Provider / Transport Layer

Replace the current CLI-exec provider with a protocol-aware provider.

Required changes:

- Add `CodexAppServerProvider`
- Add an App Server transport client
  - process transport at first is acceptable if app-server still runs as a local subprocess
  - but transport must be bidirectional and request/response aware
- Remove direct dependence on stdout-only parsing as the primary contract

Result:

- provider no longer represents "launch a one-shot command"
- provider becomes a sessioned protocol client

### 2. Event Model Layer

Current `EngineEvent` is not sufficient for approval-centric runtime control.

Add events for:

- command approval requested
- file change approval requested
- tool user input requested
- approval resolved
- turn interrupted / cleared pending request

Result:

- event model becomes request/response aware
- timeline and coordinator can distinguish:
  - proposal
  - approval request
  - completion

### 3. Service / Session Runtime

`AgentChatService` needs to own more protocol state.

Add state for:

- app-server thread id
- active turn id
- pending approval requests
- pending tool user input requests
- pending plan session state

Responsibilities move here:

- send protocol messages
- correlate request ids
- restore active session protocol state
- mediate transition from Plan -> Execute

### 4. Timeline Runtime

Current runtime auto-executes proposals:

- command proposals are converted into local execution
- approvals are not real

That must change.

Required changes:

- stop treating `proposal == execute`
- proposals become UI-visible pending items
- actual progression requires explicit approval response
- command and file change completion must come from App Server follow-up events, not local execution shortcuts

### 5. UI Layer

Current plan and approval nodes are mostly display oriented.

They need to become interactive:

- `ApprovalNode`
  - approve
  - reject
- `PlanNode`
  - revise
  - approve plan
  - start execution
- tool user input
  - inline prompt or modal response UI

This does not require a new app shell. It requires a stronger state machine behind the existing timeline/composer.

### 6. Persistence Layer

To survive IDE restart, store:

- active remote thread id
- unresolved approval requests
- active plan draft / plan status

Without this, session recovery will restore chat history but not runtime control state.

## Recommended Migration Phases

### Phase 1: Transport Replacement

Goal:

- introduce `app-server` transport
- keep existing timeline rendering as intact as possible

Deliverables:

- provider can connect to app-server
- assistant/tool/plan/approval events still render
- no true approval UI yet

Success criteria:

- current chat experience still works
- no regression in basic session restore

### Phase 2: Real Approval Runtime

Goal:

- command approvals become real
- file change approvals become real
- tool user input becomes real

Deliverables:

- pending approval queue
- approve/reject actions wired into protocol replies
- runtime no longer auto-executes proposals

Success criteria:

- command/file change do not proceed until user decision

### Phase 3: Strong Plan Mode

Goal:

- add a product-grade Plan -> Revise -> Approve -> Execute flow

Deliverables:

- new `PLAN` mode in composer
- plan session state machine
- plan draft persistence
- controlled transition from confirmed plan into execution turn

Success criteria:

- plan mode can guarantee no execution before user approval

## Risks

### Protocol Complexity

`app-server` is not a drop-in parser swap.

It introduces:

- bidirectional communication
- approval correlation
- lifecycle coordination

Mitigation:

- migrate transport first
- keep UI stable while changing runtime internals

### Session Recovery Complexity

Long-lived protocol state is harder to restore than chat history.

Mitigation:

- persist only minimal active runtime state in the first iteration
- restore pending approvals explicitly or invalidate them cleanly

### Partial Dual-Mode Period

During migration, `exec --json` and `app-server` may coexist.

Mitigation:

- treat them as separate provider implementations
- keep a clear capability flag matrix in `EngineCapabilities`

## Final Recommendation

If the product goal is only "better plan presentation", the current CLI stack is enough.

If the product goal is:

- true approvals
- true plan-before-execute
- real execution control

then migration to `codex app-server` is the correct architectural move.
