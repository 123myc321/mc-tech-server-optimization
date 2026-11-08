# 案例：生电服“完整性优先”优化

> 这是一个脱敏后的真实案例，用来说明“性能优化”和“机制完整性”冲突时该怎么取舍。
> 所有路径、种子、玩家名、地址都已移除。

## 背景

- 机器：4 核 8 线程笔记本 / 8 GB 内存
- 服务端：Minecraft Java + Fabric，数十个 mod
- 玩法：生电/技术生存，红石、刷怪塔、村民工程是核心
- 用户最终要求：**机制完整性优先**，性能可以让步

## 基线

| 指标（固定假人，1 分钟窗口） | 值 |
|---|---|
| tick 中位数 | 16.3 ms |
| tick p95 | 20.4 ms |
| tick 最大值 | 65.4 ms |
| GC 总暂停 | 1152 ms / 3 min |

## 审计发现

| 项 | 问题 | 处理 |
|---|---|---|
| Lithium | `config/lithium.properties` 是空文件 → 全部默认开启，包括红石线充能、流体、tick 调度、随机刻、刷怪、爆炸、游戏事件、温度缓存 | 关闭 12 项 |
| C2ME | `asyncSerialization=true`、`allowPOIUnloading=true`、流体后处理改期、含水层优化 | 改为原版行为 |
| letmedespawn | 刷怪笼生成的怪会自然消失 | 移出 |
| LeavesBeGone | 树叶固定 5–20 tick 衰减 | 移出 |
| faster-happy-ghasts | 改快乐恶魂移动 | 移出 |
| FastItemFrames | 已把展示框写成自定义方块，移出会导致世界缺方块 | **保留**（数据不可逆） |
| Ksyxis | 出生点 19×19 区块不加载 | 保留，但明确告知“出生点机器会停” |
| VMP | `use_async_portals=true` | 关闭 |
| 自制 mod | 异步玩家存档默认开启 | 关闭，改同步落盘 |
| TT20 | 所有加速项 | 关闭 |
| ServerCore | activation-range、lobotomize、动态调整、刷怪上限 | 关闭 |

关键教训：**空配置文件等于“全部默认开启”**。只看 mod 名字或“我装的是优化 mod”会漏掉真正的机制改动。

## 改动分类

### 零机制风险

- JVM：固定堆、G1、代码缓存 512M、`AutoBoxCacheMax`、`ExitOnOutOfMemoryError`、headless
- `server.properties`：`sync-chunk-writes=false`、`max-auto-save-chunks-per-tick=8`、`network-compression-threshold=512`
- 预生成：Chunky 半径 3000 方块，约 14 万区块

### 机制相关，按完整性处理

- Lithium 12 项关闭
- C2ME 6 项回原版
- zfastnoise 的 `end` / `fixed` / `trivial` 关闭
- VMP 异步传送门关闭
- 移出 3 个行为向 mod

### 不可逆数据

- FastItemFrames 保留；移出前必须备份并确认世界中没有 `fastitemframes:item_frame`

## 结果

| 配置 | tick 中位 | p95 | max | 说明 |
|---|---|---|---|---|
| 基线 | 16.3 ms | 20.4 ms | 65.4 ms | 未优化 |
| 纯计算优化 | 9.2–9.5 ms | 12.0 ms | 43.5 ms | 关闭机制优化前 |
| **完整性优先** | **16.7 ms** | **24.9 ms** | **52.4 ms** | 关闭机制相关优化后 |

结论：

- 机制相关的优化一关，性能基本回到原版。
- 这是“完整性”的真实价格，不是配置没生效。
- 剩余的优化空间只在 JVM 和系统层（不改游戏逻辑）。
- 启动后几秒的 `Can't keep up` 是假人同时登录/区块加载的尖峰，不算稳态问题。

## 可复用经验

1. 先审计配置，不要先加 mod。空配置 = 全开。
2. 改动必须看启动日志：`N override(s) found`、`Config X changed from A to B`。
3. 数据不可逆的 mod 不能直接移出（自定义方块/物品/实体）。
4. tick 中位数差 1–2 ms 是噪声，只有可复现差异才算结论。
5. 固定负载、固定脚本、至少两轮。
6. 完整性优先时，明确告诉用户代价，不要偷偷保留性能项。
7. 自制 mod 也要过同一套红线审计；异步功能必须有 drain 和失败回退。
