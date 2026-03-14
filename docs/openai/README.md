# OpenAI Docs Local Reference

本目录用于保存和维护与本项目直接相关的 OpenAI / Codex 官方资料的本地参考。

目标：
- 给后续功能扩展提供一个仓库内可检索的资料入口
- 记录官方能力边界，避免 UI 和底层事件语义脱节
- 给实现层保留一份稳定的本地摘要，而不是每次都临时上网查

## 当前收录

- [codex-tools-reference.md](/Users/tonysheng/StudioProject/Codex-Assistant/docs/openai/codex-tools-reference.md)
  - Codex 能力概览
  - Shell / File Search 官方工具能力
  - 面向本项目的 UI 分类映射建议

## 官方来源

- Codex 产品介绍
  - https://openai.com/index/introducing-codex/
- Shell tool
  - https://platform.openai.com/docs/guides/tools-shell
- File search
  - https://platform.openai.com/docs/guides/tools-file-search/
- Tools 总览
  - https://platform.openai.com/docs/guides/tools

## 更新策略

- 本地文件不是官方镜像，也不是规范文本；它是面向本项目的工作摘要
- 设计或实现新能力前，优先看本目录；涉及字段、参数、支持模型、限制条件时，再回官方文档复核
- 如果后面需要做更完整的离线镜像，可以再补一个专门的抓取脚本和快照目录

## 备注

我尝试过直接把官方页面 HTML 抓到仓库里，但官方站点当前对命令行直抓返回 `403`。
因此这一版先落地为“本地摘要 + 官方链接”的可维护方案，足够支持后续功能扩展时快速查询。
