# Codex And Tools Reference

更新时间：2026-03-14

这份文档只整理和本项目实现直接相关的 OpenAI / Codex 能力，不尝试复写全部官方文档。

## 1. Codex 的产品能力边界

基于 OpenAI 官方 `Introducing Codex`：

- Codex 是面向软件工程任务的 agent
- 每个任务运行在隔离环境中
- 能读文件、改文件、执行命令
- 结果应当带可验证证据，例如终端日志、测试输出、文件修改
- 用户应该能追踪每一步执行过程，再决定是否采纳结果

对本项目的直接含义：

- 时间线不能只显示“最终答案”
- 工具调用和命令执行需要作为一等事件展示
- 文件读取、编辑、搜索、命令执行应该分开建模，而不是混成一种“trace”
- 成功 / 失败状态应该直接挂在对应事件上，而不是埋进正文

来源：
- https://openai.com/index/introducing-codex/

## 2. Shell tool

基于 OpenAI 官方 `Shell` 文档：

- Shell 是 Responses API 可用工具之一
- 适合执行命令、测试、lint、类型检查等
- 命令通常是非交互式
- 需要记录 stdout / stderr / exit status，便于模型判断结果
- 高风险命令应做隔离、审计和限制

对本项目的直接含义：

- `执行命令` 应是独立 UI 类型
- 命令节点至少应具备：
  - 命令标题
  - 成功 / 失败状态
  - 可展开详情
  - stdout / stderr / exit code
  - 重试 / 复制等动作
- 红点、绿点应直接反映该条命令的状态，而不是整轮对话状态

来源：
- https://platform.openai.com/docs/guides/tools-shell

## 3. File search

基于 OpenAI 官方 `File search` 文档：

- File search 是 Responses API 内置托管工具
- 它面向“在知识库 / 文件集合中检索相关内容”
- 底层是 semantic + keyword retrieval
- 它和 shell 不同，不是“跑命令”，而是“检索文件信息”

对本项目的直接含义：

- `搜索文件` 不应显示成 `执行命令`
- 搜索结果也不应显示成 `编辑文件`
- UI 上最好是单独的证据条类型，语义上和 `读取文件` 相邻，但不等同

来源：
- https://platform.openai.com/docs/guides/tools-file-search/

## 4. 建议的上层 UI 分类

结合官方能力与当前插件时间线，建议把底层事件映射成以下几类：

- `读取文件`
  - 典型来源：read / view / open file
  - 展示重点：文件名、路径、成功状态
- `编辑文件`
  - 典型来源：edit / patch / write / apply diff
  - 展示重点：文件名、增删变化、Diff 入口、成功状态
- `搜索文件`
  - 典型来源：grep / search / file search / retrieval
  - 展示重点：搜索目标、命中范围、成功状态
- `执行命令`
  - 典型来源：shell / terminal / command run
  - 展示重点：命令名、exit code、stdout/stderr、成功或失败状态

补充类型：

- `助手说明`
  - 用于承接“我将先读取文件，再执行命令”这类自然语言过程说明
- `结果总结`
  - 用于承接“已修改 / 已失败 / 原因 / 后续建议”这类总结

## 5. 推荐的时间线展示规则

参考官方能力边界和你当前想要的设计方向，推荐统一成下面这套规则：

- 折叠态：
  - 只显示一行标题
  - 标题里体现类别和主体，例如 `编辑文件 LocalSplash.kt`
  - 右侧显示状态点
  - 右侧可带少量操作，如 `Diff` / `复制` / `重试`
- 展开态：
  - 在当前条目底部插入详情，不跳到别处
  - 命令类显示命令、目录、stdout/stderr、exit code
  - 文件编辑类显示路径、diff、工具输入输出
  - 搜索类显示 query、命中文件、摘要
- 失败态：
  - 仍保留原类别，不要退化成普通错误文案块
  - 例如失败的 shell 仍是 `执行命令`
  - 例如失败的 patch 仍是 `编辑文件`

## 6. 对当前项目最有价值的后续扩展点

- 给 `编辑文件` 节点解析增删行数，靠近设计图里的 `+14 -2`
- 给 `搜索文件` 补独立图标、标题和详情布局
- 给 `执行命令` 详情拆分 stdout / stderr，而不是只混合 body
- 给节点加稳定的事件类型枚举，不再完全依赖字符串猜测
- 把底层 provider 事件解析和上层 UI 分类映射拆开，单独维护

## 7. 使用建议

后面做功能扩展时，优先按下面顺序查：

1. 先看本文件，判断能力应该落在哪个 UI 类别
2. 再看官方来源，确认参数、限制和模型支持
3. 最后再改 provider 解析和 timeline 展示
