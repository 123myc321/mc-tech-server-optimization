---
name: 生电服优化
description: Optimize a Minecraft Java server (Fabric / NeoForge / Paper) for CPU and memory without changing game mechanics. Use when the user asks to optimize a Minecraft server, 生电服, 技术生存服, tick/TPS, JVM/GC tuning, chunk pregeneration, mod config audit, or redstone/entity parity review.
---

# 生电服优化

## 核心原则

**机制完整性 > 稳定性 > 性能 > 性价比 > 功能。**

优化只允许发生在“同样的计算结果、更少的 CPU/内存开销”这一层。任何改变红石更新顺序、流体流动、随机刻、刷怪、实体 tick、村民行为、结构布局、世界生成结果的改动，默认拒绝，除非用户明确接受代价。

先列计划，再动手。每个改动都要能回滚，并留下可验证的证据。

---

## 0. 确认边界

动手前必须问清或查明：

1. **版本栈**：MC 版本、加载器（Fabric / NeoForge / Forge）、服务端核心（vanilla / Paper / Purpur）、Java 版本。
2. **硬件**：物理核心 / 逻辑线程、内存、磁盘剩余空间。内存是瓶颈时不要建议加内存，先做占用削减。
3. **玩法边界**：是否允许改视距/模拟距离、是否允许预生成、出生点常加载是否必须保留、是否允许改客户端。
4. **世界状态**：世界大小、region 数量、是否已预生成、备份位置、是否有自定义方块/物品。
5. **负载画像**：玩家规模、常驻假人、常加载机器、下界/末地农场、红石时钟。

---

## 1. 红线清单（默认全部禁止）

| 类别 | 典型实现 | 破坏点 |
|---|---|---|
| 视距/模拟距离 | `simulation-distance` 调低、动态视距 | 实体 tick 半径减小，远处农场停摆 |
| 实体 tick | activation-range、lobotomize、AI 节流、实体上限、动态 mobcap | 实体跳过 tick、村民降频、刷怪率变化 |
| 红石 | 红石线充能优化、tick 调度重写、铁轨加速、红石粉更新重排 | 更新顺序/时序改变 |
| 流体 | 流体后处理改期、重复流体刻取消、流体流动优化 | 流动时序/复制行为改变 |
| 随机刻 | 只遍历有随机刻的 section、区块外抑制随机刻 | 作物/树叶/雪/冰生长变化 |
| 刷怪 | 刷怪集合优化、非原版刷怪、刷怪笼 despawn | 刷怪塔产出/边界变化 |
| 结构 | 结构布局优化、结构权重采样优化 | 结构位置/布局变化 |
| 世界生成 | 含水层优化、噪声实现替换（未验证等价） | 新生成区域地形变化 |
| 数据安全 | 异步区块序列化、异步玩家存档、激进区块卸载 | 崩溃时丢存档/区块 |
| 出生点 | 移除出生点常加载区块 | 出生点机器在无人在场时停摆 |

用户说“完整性优先”时，以上全部按原版处理，并明确告知性能代价。

---

## 2. 审计方法

### 2.1 先建基线

在改任何东西之前，记录：

- 启动到 `Done (Xs)` 的时间
- tick 中位数 / p95 / 最大值
- GC 总暂停与单次最大停顿
- TPS（稳态）
- 世界大小与 region 数量

没有基线，后面的“优化”无法证明有效。

### 2.2 JVM

只动不影响游戏逻辑的参数：

| 参数 | 适用场景 | 注意 |
|---|---|---|
| `-Xms` = `-Xmx` | 固定堆，避免动态扩缩 | 按机器内存定；3 GB 不一定比 4 GB 好，实测 |
| `-XX:+UseG1GC` | 默认选择 | 核心少时比 ZGC 少抢 CPU |
| `-XX:+UseZGC` | 追求低停顿 | 并发线程抢核，tick 中位可能略高 |
| `-XX:ReservedCodeCacheSize=512M` | 大量 mod | 代码缓存不足会触发 `CodeCache GC Threshold` |
| `-XX:AutoBoxCacheMax=20000` | 实体/坐标/物品数量多 | 减少 Integer 装箱 |
| `-XX:+ExitOnOutOfMemoryError` | 保护存档 | OOM 立即退出 |
| `-Djava.awt.headless=true` | 专用服 | 不初始化 AWT |
| `-XX:+UseCompactObjectHeaders` | JDK 25+，内存瓶颈 | 对象头 12→8 字节，通常省 10–20% 堆；需实测兼容 |

不要用：`UseLargePages`（笔记本锁页易失败）、`UseTransparentHugePages`（Linux only）、`UseNUMA`（单路无效）。

Paper/Purpur 还要看 `paper-global.yml`、`paper-world-defaults.yml` 里的 `chunk-loading`、`tick-rates`、`entities` 相关项；NeoForge 看 `config/` 下各 mod 的 `server`/`common` 配置。

### 2.3 `server.properties`

| 键 | 建议 | 说明 |
|---|---|---|
| `view-distance` / `simulation-distance` | 保持原版 | 红线 |
| `sync-chunk-writes` | `false` | 减少存盘 fsync 停顿；崩溃可能丢最后几秒区块 |
| `max-auto-save-chunks-per-tick` | 调低（如 8） | 存档摊薄，降低单 tick 尖峰 |
| `network-compression-threshold` | 视带宽调高（如 512） | 少压缩小包，省 CPU |
| `entity-broadcast-range-percentage` | 保持原版 | 影响实体广播范围 |
| `enable-query` | 不用就 `false` | 只影响第三方查询 |

Paper 系同名配置可能已被 `paper-global.yml` 接管，改前确认优先级。

### 2.4 优化 mod 的审计框架

不要凭 mod 名字判断安全性。按下面顺序读它的实际行为：

1. **列 mod**：按“优化 / 玩法 / 库 / 纯客户端 / 数据记录”分类。
2. **找配置**：`config/<modid>.properties|toml|yml|json`。空文件往往意味着“全部默认开启”。
3. **读默认**：配置注释里会标 `default`、`incompatible`、`may cause behavior change`。带这些词的项优先关。
4. **看 mixin 列表**：解压 jar，读 `*.mixins.json`。按包名判断影响面：
   - `redstone`、`fluid`、`tick`、`scheduler` → 红石/流体/时序
   - `chunk_ticking`、`random_tick`、`spawn`、`mob_spawning` → 随机刻/刷怪
   - `ai`、`villager`、`pathfinding` → 村民/怪物行为
   - `explosion`、`game_event`、`temperature` → 爆炸/事件/温度
   - `serialization`、`chunk_io`、`async` → 数据安全
5. **关掉后看日志**：多数 mod 会打印 `Loaded configuration ... N override(s) found` 或 `Config X changed from A to B`。没有这行就说明配置没生效。
6. **A/B 验证**：同一世界、同一负载、同一脚本，至少两轮。

#### 常见优化 mod 的审计点

版本不同配置项可能变化，以下按“看什么”而不是“照抄值”使用：

| mod | 优化什么 | 机制风险 / 检查点 |
|---|---|---|
| **Lithium / Canary / Radium** | 通用服务端逻辑 | `lithium.properties` 空 = 全开；重点关 redstone_wire、fluid、tick_scheduler、chunk_ticking、mob_spawning、explosions、game_events、temperature_cache、block_pattern_matching、comparator_tracking |
| **C2ME** | 区块系统/生成/序列化 | `asyncSerialization`、`allowPOIUnloading`、`fluidPostProcessingToScheduledTick`、`filterFluidPostProcessing`、`optimizeAquifer`、`autoSave.mode` |
| **VMP** | 实体跟踪/区块发送 | `use_async_portals`（传送门时序）；实体跟踪优化通常可留 |
| **zfastnoise / Noisium** | 世界生成 | `end`、`fixed`、`trivial` 等标注“可能不兼容”的项；世界已生成后收益趋零 |
| **ServerCore** | 综合 | `optimizations.yml` 五项、`activation-range`、`lobotomize-villagers`、`dynamic`、`breeding-cap` |
| **TT20** | tick 加速 | 所有 `*-acceleration`；只留看门狗/原版 lagback |
| **ModernFix / FerriteCore / Krypton** | 内存/网络/启动 | 通常行为等价，可留；注意 mixin 冲突日志 |
| **Chunky** | 预生成 | 不改机制，安全；注意世界名与半径单位 |
| **Clumps** | 经验球合并 | 实体数变少；用户确认才保留 |
| **Ksyxis** | 出生点常加载 | 出生点有机器就不要用 |
| **FastItemFrames** | 展示框转方块 | 已写入世界的数据不可逆，移出会缺方块 |
| **dimparallel / 维度并行类** | 下界/末地并行 tick | 主世界仍单线程；跨维度传送/实体加入延迟；worker 读未加载区块可能返回空气块 |

### 2.5 数据不可逆的 mod

先判断“它有没有往世界写自定义方块/物品/实体”：

- 有 → 不要直接移出；先备份世界，确认世界中没有该 ID，或保留 mod。
- 无 → 可以移出，但记录回滚路径。

典型：把实体替换成自定义方块的渲染/存储类 mod、把方块替换成自定义实体的 mod、改变存档格式的 mod。

### 2.6 系统层

- 确认杀软实时监控是否真的在运行；服务停了就没有扫描开销，排除项意义不大。
- 页面文件峰值接近上限时，固定大小避免动态扩展卡顿；改完需重启。
- Windows Search 若启用，把服务器目录排除索引。
- 电源方案设为高性能，关闭交流睡眠。
- 笔记本注意散热/降频；先看是否被功耗墙限制。

---

## 3. 基准方法

### 3.1 备份

世界 + 配置 + 启动脚本，单独目录，记录文件数与大小。预生成/改存档格式前必须备份。

### 3.2 固定负载

用 Carpet 假人（或等价工具）固定数量与位置。注意不同版本 `player <name> move` 可能无效；优先用随启动上线的常驻假人。

### 3.3 采集

自建一个可重复的脚本，至少包含：

1. 删旧日志
2. 启动，等 `Done (`
3. 预热固定秒数
4. 让固定数量假人上线/移动
5. 采集 tick 数据（spark profiler 固定窗口，或服务端自带 `/tick query`）
6. `save-all flush`、`stop`
7. 保存 stdout、GC 日志、summary

不要依赖某台机器上的固定脚本路径；把脚本作为交付物的一部分。

### 3.4 指标与读法

- tick 中位数、p95、最大值（至少 1 分钟窗口）
- GC 次数、总暂停、单次最大停顿
- TPS、`Can't keep up` 次数
- 启动到 `Done` 的时间

读法：

- 中位数差 1–2 ms 属于运行间波动；同一配置重跑就能差这么多。
- 只有可复现的差异才下结论。
- 不要用单次平均值判断；它会混入启动/区块加载。
- spark 在 Windows 首次 attach 会造成十几秒停顿，采样窗口要扣除这个尖峰。
- 启动后几秒内的 `Can't keep up` 通常是假人同时登录/区块加载，不算稳态问题。

---

## 4. 预生成

常用工具：Chunky（Fabric/Paper 均有）。

```
/chunky world <dimension>
/chunky center <x> <z>
/chunky radius <blocks>
/chunky shape square
/chunky start
/chunky progress
```

- 维度名用工具自己的名字（通常是 `overworld`，不是 `level-name`）。
- 半径单位是方块，实际常按方形处理；按面积估算区块数。
- 粗估：半径 3000 约 14 万区块、十几分钟、+500 MB；半径翻倍，区块数约 4 倍。
- `continueOnRestart=true` 可断点续跑；`forceLoadExistingChunks=false` 只补未生成区域。
- 预生成只消除“边走边生成”的卡顿，不提升稳态 tick。

---

## 5. 交付物

每次优化至少产出：

1. **改动清单**：现状速查、每项改动与理由、A/B 数据、红线清单、回滚表。
2. **回滚说明**：每项对应的原文件/备份路径，或一条命令。
3. **启动验证**：`Done (Xs)`、无真实 ERROR、`stop` 干净、无残留 java 进程。
4. **明确取舍**：哪些优化被放弃、代价是多少、还剩哪些需要用户拍板。

验证启动时区分真错误与噪音：加载客户端类的 `ClassNotFoundException`、`sessionserver` 超时、spark `CancellationException` 都不是配置错误。

---

## 6. 通用陷阱

| 现象 | 原因/处理 |
|---|---|
| 优化 mod 配置写了但不生效 | 空配置 = 全默认；确认文件路径、mod id、启动日志 override 数 |
| 改了配置但行为没变 | 看 `Config ... changed from X to Y`；`default` 表示内置默认 |
| 关了优化 tick 反而比原版差 | 负载/世界规模/假人数不同，不能跨实验横比 |
| 移出 mod 后世界缺方块 | 该 mod 写过自定义方块/实体；先备份并确认 |
| 服务端启动日志有 `Mappings not present!` | 新版 MC 使用 Mojang 官方名；自编译 mixin 注意 `compatibilityLevel` |
| spark 数据抖动大 | Windows 动态 agent attach；用固定窗口，或改用 `/tick query` |
| 预生成很快但进游戏还是卡 | 预生成只消除生成卡顿；实体/红石/GC 是另一回事 |
| 维度并行 mod 声称加速 | 只并行非主世界维度；主世界仍单线程；跨维度交互有时序代价 |
| 内存够但 GC 仍长 | 看年轻代比例、存活对象、代码缓存；不是堆越大越好 |

---

## 7. 建议的工作流

1. 备份世界与配置。
2. 记录基线：启动时间、tick 中位/p95/max、GC 停顿、TPS。
3. 先做零机制风险的项：JVM、`server.properties`、系统层。
4. 再审计 mod 配置：按 §2.4 的框架，逐项关闭有行为差异的开关，每轮启动看日志确认生效。
5. 最后处理数据不可逆项，确认世界数据后再动。
6. 每轮用同一负载复测，记录数据。
7. 更新改动清单与回滚表。
8. 最终验证：干净启动、`save-all flush`、`stop`、无残留进程。
