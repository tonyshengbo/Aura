# Current Architecture Baseline (Pre-V2 Refactor)

更新时间：2026-03-15

## 1) 当前主链路

`CliAgentProvider -> EngineEvent -> TimelineActionAssembler -> TimelineActionRuntime -> Swing ToolWindow`

说明：
- Provider 负责启动 CLI 进程与读取 stdout。
- `CliStructuredEventParser` 直接把行文本转 `EngineEvent`。
- `TimelineActionAssembler` 把 `EngineEvent` 转成 `TimelineAction`。
- Swing 工具窗（`AgentToolWindowPanel`）同时承担：
  - 发请求；
  - 处理动作；
  - 维护展示状态；
  - 写会话存储。

## 2) 当前主要问题

- 协议层和 UI 层边界不清晰：`EngineEvent` 既像传输协议又像 UI 输入。
- 事件语义不是 `thread/turn/item`，跨引擎统一困难。
- UI 为重量级 Swing 单类，职责过载，重构成本高。
- 计划/审批等语义没有稳定的一等节点。

## 3) V2 目标边界

- 引擎适配层只做解析和规范化。
- 统一协议层输出强类型 `UnifiedEvent(thread/turn/item)`。
- 状态机层是唯一状态真相，UI 仅读状态并发 Intent。
- UI 改为 ComposePanel 承载证据流优先时间线。

## 4) 迁移策略（本次执行）

- 一次性重写主链路，不保留旧链路并行入口。
- 首版仅启用 Codex。
- 会话数据硬切为新结构，不兼容旧 payload。
