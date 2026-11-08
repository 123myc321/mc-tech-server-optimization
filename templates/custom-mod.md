# 自制 mod 检查表

## 基本信息

- **mod id**：
- **版本**：
- **目标 MC / 加载器**：
- **JDK**：
- **一句话功能**：

## 1. 行为边界

- [ ] 改哪个类 / 哪个方法：
- [ ] 输入输出是否与原版一致：
- [ ] 是否改变 tick 顺序 / 随机刻 / 刷怪 / 流体 / 红石：
- [ ] 失败时是否留下坏状态：
- [ ] 是否有开关能完全回退：

## 2. 注入点

| 注入点 | 方式（Inject/Redirect/Overwrite） | 影响面 | 理由 |
|---|---|---|---|
| | | | |

- [ ] 没有使用 `@Overwrite`，或使用理由充分
- [ ] 只注入必要方法
- [ ] 与其他优化 mod 的 mixin 不抢同一注入点

## 3. mixin 配置

```json
{
  "required": true,
  "package": "",
  "compatibilityLevel": "",
  "mixins": [],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] `compatibilityLevel` 覆盖目标类版本
- [ ] `package` 与实际路径一致
- [ ] 启动日志无 mixin 应用失败

## 4. 构建

- [ ] 依赖 jar 列表完整（MC / Loader / Fabric API / 其他）
- [ ] `javac` 或 Gradle 能干净编译
- [ ] `fabric.mod.json` 字段完整
- [ ] jar 内容包含 mixins json / assets
- [ ] 产物放进 `mods/` 能加载

## 5. 测试

| 测试 | 结果 |
|---|---|
| 启动 `Done (Xs)` | |
| 功能触发 | |
| 开关关闭 → 原版路径 | |
| `save-all flush` + `stop` | |
| 模拟 kill -9 / 崩溃 | |
| 与现有优化 mod 同时加载 | |
| 同负载 A/B | |

## 6. 数据安全（异步功能必填）

- [ ] `stop` / `save-all` 会 drain 队列
- [ ] 崩溃时最多丢多少：___ 秒 / ___ 个操作
- [ ] 文档里写明了这个窗口
- [ ] 默认值保守

## 7. 回滚

- 删 jar：
- 或关闭配置：
- 备份位置：

## 8. 已知限制

-
