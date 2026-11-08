# Minecraft Server Optimization Skill

一个给 AI 编码代理用的 OpenCode skill：优化 Minecraft Java 服务端（Fabric / NeoForge / Paper）的 CPU 与内存占用，同时**不改变生电机制**。

核心原则：

> **机制完整性 > 稳定性 > 性能 > 性价比 > 功能。**

## 它解决什么问题

生电服（技术生存服）最怕“为了性能把机制改坏”。这个 skill 把优化拆成可审计的层次：

- 哪些项默认禁止（红石、流体、随机刻、刷怪、实体 tick、结构、世界生成、数据安全）
- JVM / `server.properties` 里哪些参数只影响计算、不影响逻辑
- 怎么读一个优化 mod 的配置和 mixin 列表，判断它实际改了什么
- 怎么固定负载做 A/B，怎么读 tick 与 GC 数据
- 哪些 mod 会往世界写不可逆数据，不能直接移除
- 预生成的命令、半径估算与常见坑
- 交付改动清单与回滚表的要求

## 安装

把 `SKILL.md` 放到 OpenCode 的 skills 目录：

```text
~/.config/opencode/skills/mc-tech-server-optimization/SKILL.md
```

Windows：

```text
C:\Users\<你>\.config\opencode\skills\mc-tech-server-optimization\SKILL.md
```

新开一个 OpenCode 会话后生效。触发词包括：`优化生电服`、`Minecraft 服务器优化`、`tick/TPS`、`JVM/GC 调优`、`预生成`、`mod 配置审计`。

## 适用版本

- Minecraft Java Edition（26.x 及更早的 Fabric/NeoForge/Paper 服务端）
- 配置项名称会随版本变化，skill 里强调“读默认配置 + 看启动日志”，而不是照抄固定值

## 内容结构

1. 核心原则
2. 边界确认
3. 红线清单
4. 审计方法（JVM / `server.properties` / mod 审计框架 / 不可逆数据 / 系统层）
5. 基准方法（固定负载、采集、读法）
6. 预生成
7. 交付物
8. 通用陷阱
9. 详细流程（SOP，阶段 0–8）
10. 自制 mod（边界、mixin、构建、测试、数据安全）
11. 模板

## 模板

| 文件 | 用途 |
|---|---|
| `templates/benchmark.ps1` | 通用基准脚本：启动、预热、假人、spark 采样、保存日志 |
| `templates/change-list.md` | 改动清单骨架：现状、改动、A/B、红线、回滚 |
| `templates/mod-audit.md` | 单个 mod 的审计记录表 |
| `templates/custom-mod.md` | 自制 mod 检查表：边界、mixin、构建、测试、数据安全 |

## 详细流程

`SKILL.md` 的 §7 是一条可执行的 SOP：

| 阶段 | 内容 |
|---|---|
| 0 | 勘查：版本、硬件、世界、负载 |
| 1 | 备份：文件数校验 |
| 2 | 基线：两轮固定负载 |
| 3 | 零风险项：JVM、`server.properties`、系统层 |
| 4 | mod 审计：配置、mixin、日志证据、A/B |
| 5 | 不可逆数据：保留或备份后处理 |
| 6 | 预生成：Chunky、进度、磁盘增量 |
| 7 | 最终验证：干净启动、TPS、落盘、无残留 |
| 8 | 交付：改动清单与回滚表 |

## License

MIT
