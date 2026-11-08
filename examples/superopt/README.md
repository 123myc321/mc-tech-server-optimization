# SuperOpt — 自制 mod 示例

这是从一个真实优化任务里抽出的自制 mod 示例，用来演示 `SKILL.md` §9 的做法：

- 用 `@Redirect` 把“压缩写入 + 原子替换”从服务端线程挪到单线程队列
- 有界队列 + 同步回退，保证失败/关闭路径不丢数据
- `SERVER_STOPPING` 与 `stopServer` 两处 drain
- 所有功能都有开关，可完全回退原版路径
- 附带一个只读的 view-distance cap 示例（展示如何在 `requestedViewDistance` 上做 per-player 限制）

> 这是一个**示例/模板**，不是开箱即用的生产 mod。包名、版本、依赖需要按你的服务端调整。

## 目录

```text
superopt/
├── build.ps1
└── src/main/
    ├── java/local/superopt/
    │   ├── AsyncPlayerSave.java
    │   ├── SuperOpt.java
    │   ├── ViewCapCommand.java
    │   ├── ViewCapConfig.java
    │   └── mixin/
    │       ├── MinecraftServerMixin.java
    │       ├── PlayerDataStorageMixin.java
    │       └── ServerPlayerMixin.java
    └── resources/
        ├── fabric.mod.json
        └── superopt.mixins.json
```

## 构建

```powershell
.\build.ps1 -ServerDir "C:\path\to\server" -Version "0.1.0"
```

脚本会：

1. 把 `server.jar`、`libraries\**\*.jar`、`.fabric\remappedJars\**\*.jar` 拼成 classpath
2. `javac` 编译到 `build\classes`
3. 复制 `src/main/resources`
4. 替换 `fabric.mod.json` 里的 `${version}`
5. 打包成 `build\superopt-<version>.jar`

不同加载器/版本的依赖路径可能不同；编译失败时先把报错缺的类所在的 jar 加进 classpath。

## 关键设计点

### 1. 只重定向 IO，不改数据

`PlayerDataStorage.save` 里原本是：

```text
NbtIo.writeCompressed(tag, tempFile)
Util.safeReplaceFile(finalFile, tempFile, backupFile)
```

示例只把这两步交给后台线程，NBT 内容与最终文件路径不变。

### 2. 单线程 + 有界队列

- 单线程保证同一玩家的写入有序。
- 队列满（512）时退回同步写入，不阻塞、不丢弃。
- 任何异常都走同步路径，避免“以为存了其实没存”。

### 3. 关闭时 drain

`SERVER_STOPPING` 和 `stopServer` 都会调用 `drain()`，最多等 60 秒；超时则 `shutdownNow()` 并记录未完成数量。

### 4. 默认值保守

`async-player-save` 默认 `true`，但可以一条命令关掉：

```text
/superopt asyncsave false
```

对生产服，如果“崩溃丢最后几秒玩家数据”不可接受，就默认关闭。

## 测试要点

- 启动：`Done (Xs)`，mixin 无应用失败
- 功能：玩家下线/上线，`playerdata` 文件正常更新
- 关闭：`save-all flush` + `stop`，队列归零
- 异常：模拟写入失败，确认回退到同步路径
- 崩溃：kill -9 后确认最多丢失的范围，并写进文档

## 隐私

示例中的包名、作者、路径都是占位；不包含任何真实服务器地址、玩家名、UUID 或世界种子。
