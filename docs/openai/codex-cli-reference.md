# Codex CLI Reference (Local Working Notes)

更新时间：2026-03-15

本文件是面向本项目重构的本地摘要，不替代官方文档。字段与行为以官方文档为准。

## 1) 非交互模式：`codex exec --json`

- `--json` 输出为逐行 JSON（JSONL）事件流。
- 事件语义可归约为：
  - `thread.*`：会话级
  - `turn.*`：轮次级
  - `item.*`：步骤级（工具、文本、计划更新等）
  - `error`：错误级
- 用于插件侧流式渲染时，推荐按事件时间顺序做单向 reduce，不在 UI 层做反向修复。

## 2) 会话续跑：`exec resume`

- `resume` 用于在同一 thread 上续跑，不需要重新构建完整历史提示。
- 对插件架构意义：
  - session store 至少要保存 thread id；
  - UI 应显式区分“新会话执行”与“续跑执行”；
  - 数据层不应把 thread 与 turn 概念混淆。

## 3) 计划模式与执行模式衔接

- 交互 CLI 存在 `/plan` 命令。
- 非交互 `exec --json` 事件中存在计划更新类 item（可统一建模为 `plan_update`）。
- 建议在插件中做两阶段：
  - `PLAN_ONLY`：只采集计划节点，不触发危险执行；
  - `EXECUTE_APPROVED_PLAN`：用户批准后进入执行轮次（可 resume）。

## 4) 对本项目的统一协议建议

- 统一协议保持 `thread / turn / item` 结构。
- `item` 细分：
  - `narrative`
  - `tool_call`
  - `command_exec`
  - `diff_apply`
  - `approval_request`
  - `plan_update`
- 失败信息保持原节点语义，不退化为通用文本块。

## 5) 官方来源

- Non-interactive mode:
  - https://developers.openai.com/codex/noninteractive
- CLI reference:
  - https://developers.openai.com/codex/cli/reference
- Slash commands:
  - https://developers.openai.com/codex/cli/slash-commands
