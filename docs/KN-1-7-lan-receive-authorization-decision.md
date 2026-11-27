# KN-1 #7 · 局域网接收授权（决策文档，未实施）

> 状态：**决策文档，未实施**。本文件不含任何代码改动；实施需另行授权。
> 来源：`docs/KN-1-pc-parity-upgrade-backlog-20261126.md` 第 7 项（局域网同步/推送接收授权）。
> 调研日期：2026-11-27（本地时间 +08:00）。

## 1. 要实现的用户能力

用一句话说：**同一个 WiFi 下的其他人，不应该能随便往你的电视/手机上推视频、装 APK、改设置或拉走你的配置。**

今天这些操作全都只需要知道设备 IP：`curl 'http://192.168.1.20:9978/action?do=push&url=...'` 就能让电视开始播放任意地址；`do=setting` 能写偏好；`do=sync` 能拉取/覆盖配置。用户没有任何手段关掉或限制它。

## 2. 当前实现（本仓实测证据）

| 结论 | 证据 |
|---|---|
| HTTP 服务随应用启动，监听 9978–9998 | `App.java:300`；`server/Server.java:68` |
| `/action` 只按 `do=` 分发，**没有任何授权判断** | `server/process/Action.java:59-70`（`isRequest` 只判断前缀）、`:72-111`（`doJob` 直接 switch） |
| 可从局域网写入偏好 | `Action.java:93-96` → `do=setting` |
| 可从局域网推 URL / 推 APK / 触发同步 | `Action.java:78-88`（`push`/`cast`/`sync`/`apk`/`apk_url`） |
| 未知 `do` 返回 200（失败被伪装成成功） | `Action.java:109` `default -> Nano.ok()` |
| 请求参数全文进日志（含 URL 与参数） | `Action.java:68` |
| **PC 版文档所述"Android 返回 403 本机 API 修改未开启"在本仓并不存在** | 本仓无该开关；那是 KN-1 引用 PC 文档时的目标态描述，不是现状 |

**仓内已有、可复用的先例**

- `server/proxy/ProxyCapabilityToken.java:13-47`：32 字节 `SecureRandom` 生成、`matches(candidate)` 常量时间比较的 bearer token（目前只用于代理链路）。
- `server/process/ConfigImport.java`：`bodyGuard(session.getMethod(), headers)` —— "限制必须先于 parseBody"的守卫先例。
- `following/FollowingSettings.java:44` `isServerImportEnabled()`：端点级功能开关的先例（未开启抛 `IOException("服务端订阅导入未启用")`）。
- `server/process/ApkUrlPolicy.java`：推 APK 前的 URL 白名单式校验。

**会受影响的自家调用点**（都靠"知道对方 IP"直连，不带任何凭据）：

`ui/dialog/OneKeySyncDialog.java:371`、`ui/dialog/SyncDialog.java:161,170`、`ui/dialog/ApkPushDialog.java:233`、`ui/dialog/ApkPushUrlDialog.java:112`、`ui/dialog/PushPlayUrlDialog.java:100`、`ui/dialog/CastDialog.java:196`（mobile 与 leanback 各一份）、`server/process/Manage.java:639`。

## 3. 最佳实践研究

| 证据类别 | 来源与修订 | 等级 | 结论/用途 |
|---|---|---|---|
| 同族实现（主证据） | `Silent1566/webhtv-pc` @ `76a5096f00478d539874d6ac75d53767b067b288`，`apps/desktop-flutter/lib/services/sync_server.dart:56-60,324-336,454,499,566` | A（一手源码） | 服务端不自己判断，注入 `isPeerAuthorized` 回调；"同步未开启"与"对端未授权"**一律 403**；有一个端点刻意不要求 uuid（否则永远 403） |
| 同族协议设计 | 同仓库 `docs/phase5/design/02-android-sync-protocol.md:134-135,352-368` | A | 明确"默认关闭 + 用户授权"：只接受 uuid 白名单内对端的推送，未授权一律 403 |
| 同族测试 | 同仓库 `apps/desktop-flutter/test/phase5_sync_server_test.dart:643-663`（用例 11/12） | A | 403 语义有测试断言：`403 + 同步未开启`、`403 + 对端未授权` |
| 本仓先例 | 见第 2 节 file:line | A | 令牌原语、请求体守卫、端点开关、URL 策略都已存在，不需要新依赖 |
| 平台文档 | NanoHTTPD（`IHTTPSession.getHeaders()/getParms()`）、Android NSD | B（官方文档，仅作实现约束） | 令牌可走自定义头；发现层已由 `NsdDeviceDiscovery` 提供设备标识，不必新增协议 |
| 论文/技术博客 | —— | 不适用 | 本任务是访问控制工程问题，无算法或性能命题；按 AGENTS.md 记录"不适用"而不空跑搜索 |

**可借鉴但需改造的两点**（照搬会踩坑）

1. PC 的 `isPeerAuthorized(peerAddress)` 用的是**调用方 IP**；NAT/多网卡/热点共享下 IP 不稳定，uuid 才是主键。移植时以 uuid（本仓 `Device.getUuid()`）为主、IP 只作辅助提示。
2. PC 协议文档里 `do=setting` 那类"本机 API 修改"端点需要独立的本地开关（默认关）。本仓目前完全没有，必须一并补，否则令牌一发出，写偏好依然是全开的。

## 4. 方案对比

| 方案 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| A. 不改 | 维持现状 | 零成本 | 同网段任何人可推流/装包/改设置/拉配置 | 否 |
| B. 只加总开关 | 一个"允许局域网控制"开关，默认关 | 半小时能做完 | 开了就全开，无法区分"我的手机"和"邻居的电脑"；自家 7 条链路每次都要手动开 | 不足 |
| C. 总开关 + 共享令牌（配对码） | 电视显示 6–8 位配对码，手机首次配对后本地保存 32B 令牌（复用 `ProxyCapabilityToken`），之后请求带 `X-WebHTV-Token` | 一次配对长期有效；不依赖 IP；实现面小（1 个校验点 + 1 个存储 + 5 处调用点带头）；可撤销（重新生成即失效） | 需要一条配对 UX；令牌是"全权"，不能按端点/按对端细分 | **推荐：S1** |
| D. 总开关 + 对端白名单（uuid+IP） | 照 PC：维护已授权 uuid 列表，未授权 403 | 粒度到设备；与 PC 行为一致 | 仍需要一次配对动作产出白名单；跨网段/热点时 IP 不可靠 | 推荐作为 **S2** |
| E. C + 端点分级 | 在 C 之上把写端点分层：`setting/file/sync/apk*` 需要令牌**且**本机开关；`push/cast/control` 仅需令牌；`/device`、`/tvbus`、只读查询保持开放 | 风险面收敛最干净；符合 PC 协议文档"settings 单独确认"的思路 | 端点分类表要写死并测 | **推荐：并入 S1** |

## 5. 推荐方案（分两阶段）

**S1（本轮建议实施）**

1. 新增 `LanAccessControl`（纯逻辑 + 一层轻存储）：总开关（默认 **关**）、令牌生成/校验（复用 `ProxyCapabilityToken`）、端点分级表。
2. `Action.doResponse` 在 `doJob` 之前做一次校验：未授权 → `403` + 明确 message（`LAN access disabled` / `unauthorized peer`），**且不再把未知 do 当成功**（改 400）。
3. 配对 UX：设置 → 增强功能 → "局域网授权"，页面显示配对码与"重新生成"（重新生成即让所有旧令牌失效）；手机端首次推送失败时提示"请在电视上查看配对码"。
4. 自家 7 条调用点补齐请求头 + 一条"未配对则引导配对"的错误分支。
5. `/device`、`/tvbus`、只读查询类保持开放（发现层与 EPG 依赖它们，且不含隐私/写操作）。

**S2（后续，需重新授权）**：把单一令牌升级为"已授权设备列表"（uuid 白名单），支持按设备撤销；令牌退化为配对过程中的一次性凭据。

## 6. 验收标准

- 未配对客户端 `curl /action?do=push` → `403`，响应与日志都不含令牌明文。
- 关闭总开关时**任何** `do=` 都 403（含只读的 `search`），仅 `/device`、`/tvbus` 可用。
- 未知 `do=` → `400`（不再 200）。
- 配对后：手机推送 URL、一键同步、推 APK 三条链路在模拟器双端实测通过。
- 令牌只在内存与加密偏好中出现；`Action.java:68` 那行日志必须脱敏（当前会把 params 全量打进日志）。
- 单测覆盖：开关关闭、令牌错误、令牌正确、端点分级、未知 do —— 纯逻辑层（不依赖 Android）先红后绿。

## 7. 最小实施步骤

1. `LanAccessControl`（纯 JVM）+ 单测（分级表 / 403 语义 / 令牌撤销）。
2. `Action.doResponse` 前置校验 + 未知 do 改 400 + 日志脱敏。
3. 设置页配对码页面（显示/重新生成），复用既有设置行样式。
4. 7 条自家调用点补头 + 未配对分支。
5. 模拟器双端实测（5554 发送端、5556 接收端）。

## 8. 回滚

删除 `LanAccessControl` 与 `Action` 的校验分支、移除 7 处请求头即可回到今天的行为；不涉及数据迁移，旧令牌留在偏好里无害。

## 9. 风险

- **兼容性**：老版本 App（没有配对逻辑）将无法再推送到新版设备。这是安全修复的必然代价，必须在更新说明里写清；总开关默认关也会让"升级后一切照旧"的预期落空，需要产品确认。
- **配对可发现性**：配对码藏在设置里，用户第一次推送失败容易懵；错误文案必须直指位置。
- **令牌泄漏面**：令牌走明文 HTTP，同网段抓包可见。可接受（相当于会话凭据），但不能进日志、不能进历史/推送记录。
- **误伤自家功能**：`Manage.java:639` 的远端同步与 `ApkUrlPush` 走的路径不同，改动要逐条实测，不能只看编译通过。

## 10. 待你决策（4 个）

1. **总开关默认值**：默认关（安全，但升级后自家推送立刻失效，需引导配对）还是默认开（升级无感，但"开=任何人可推"只解决一半）？我推荐**默认关 + 首次推送失败时引导配对**。
2. **令牌还是白名单先行**：S1 只做令牌（1 天量级），还是直接做 S2 的 uuid 白名单（2–3 天）？
3. **端点分级边界**：`bookmark/search/refresh` 这类"非写但也不公开"的端点，要求令牌还是继续开放？
4. **是否限制网段**：要不要像 PC 那样只接受与设备同网段的请求？我倾向**不做**——热点共享/多网卡下误伤概率高，令牌已经解决了"谁在用"的问题。
