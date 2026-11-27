# FORK-5 局域网遥控（决策 + 实施记录）

## 要实现的用户能力

手机/网页当遥控器控制电视端：方向、确定、返回、播放/暂停、上一集/下一集、进度快进快退、音量。
`llb0/webhtv` 里这一块最大（`e1ed088310f9e47538eefee3b01e867a6a155bfa` 局域网遥控 + `c39948defe1eb18aee5878289e0cefd4274f5920` 语音遥控压缩 24 提交）。

## 当前项目已有实现

| 事实 | 证据 |
|---|---|
| **已有带鉴权的远程指令通道** | `remote/RemoteCommandExecutor.execute(profile, command)`：`device.status`、`config.list/upsert/use/delete/sites/home`、`remoteSync.export/restore`、`action.search`、`action.push`、`log.recent` 已是分支式命令表 |
| 指令来自中转服务、非直连 | `RemoteAgent`/`RemoteAgentService` 轮询中转；GroupToken/BindGrant 校验（`RemoteStore.consumeBindGrant`、`validateGroup`） |
| 令牌模型齐全 | `RemoteTokens.randomCapability(prefix)` = 32 字节随机 capability 令牌；`RemoteTrustSetup` 负责绑定中转 |
| 局域网发现可用 | `utils/NsdDeviceDiscovery.java`（含 `NsdDeviceDiscoveryTest`） |
| **没有**按键注入/遥控端点 | `server/process/` 无 Voice/Key 类；全仓无 `Instrumentation`/`dispatchInputEvent` |

**结论：本仓的"遥控"通道已经存在（中转 + 能力令牌 + 命令表），缺的是"播放器/导航类命令"这一类命令。**

## 三个方案

### A. 不改
遥控能力靠既有 `action.search`/`action.push` 变通，用户无法从手机换集或暂停。

### B. 搬 llb0 的 `/voice` + 反射注入（拒绝）
- 组成：`server/process/Voice.java`(1034) + `voice/` 8 个类（`VoiceControlDialog` 1508 行等）+ `ServerToken` + 三语 strings + 两个 Manifest 改动。
- 注入方式：反射 `ViewRootImpl.dispatchInputEvent`（`73eca60c5a994c363f65d500c3f476e723e632e2` 的根因结论：Activity.dispatchKeyEvent 只到 View 层，焦点移动在输入管道里）。
- **拒绝理由 1（平台风险）**：依赖非公开 API 注入按键，属会被平台策略限制的做法（本决策不把它当基础，故未联网取证；风险本身足以否决"作为骨架"）。
- **拒绝理由 2（重复建设）**：等于再造一套 HTTP 端点 + 一套 token，与已有中转 capability 模型并存，鉴权面翻倍。
- **拒绝理由 3（体量）**：2600+ 行、三语资源、Manifest 改动，且语音链路还牵第三方 STT。

### C. 在既有命令表上新增 `player.command`（推荐）
1. 新增命令类型 `player.command`，payload：`{action, value}`，action ∈ `play_pause | next | prev | seek_forward | seek_back | back | up | down | left | right | enter | volume_up | volume_down`。鉴权完全沿用现有 GroupToken/BindGrant 与 `validateGroup`。
2. 执行端在 `RemoteCommandExecutor` 加一个分支，转给新的 `RemotePlayerOps`：
   - 播放类：直接调用 Activity/播放器既有 API（`checkNext/checkPrev`、`player().pause/resume`、`seekTo`），**不注入按键**。
   - 导航类：在当前 Activity 上做显式焦点移动——先 `View.focusSearch(FOCUS_DOWN/…)` 找候选，找到就 `requestFocus()`，找不到再退回 `Activity.dispatchKeyEvent`。这样不依赖输入管道私有行为，也不做双通道注入（llb0 自己踩过双触发，靠返回值短路）。
3. 手机端入口：复用现有"远程托管/设备"页面，加一个遥控面板（方向键 + 播放/换集），走同一条中转通道。**本轮不做**语音识别。
4. 直连（同局域网不经中转）**不在本决策内**：若将来要做，单独评估一个 `/key` 端点，并复用 `RemoteTokens` 的 capability 令牌，不新造 token 体系。

## 推荐与理由

**推荐 C，且只做"命令表 + 遥控面板"两件事。**
- 鉴权、设备绑定、传输、日志全部复用，新增面只有 1 个命令类型 + 1 个执行类 + 1 个面板。
- 避开反射注入：命令语义化（next/prev/seek）比"合成按键"更稳，也不受焦点实现改动影响。
- 代价：直连（无中转）场景要等后续单独决策；纯离线局域网用不了遥控。

## 验收标准

- 绑定设备后，从遥控面板发出 `play_pause/next/prev/seek_forward/seek_back/back`，电视端 1 秒内响应，播放页状态与遥控一致。
- 方向键在 TV 首页、详情页、设置页能移动焦点；在播放页不误触发控件（只有明确导航命令才移动）。
- 未绑定/令牌失效时返回明确错误，不执行任何动作；日志里不出现令牌明文。
- 播放页无浮层时，方向键行为与实体遥控器一致（不允许出现"双触发"跳两格）。

## 最小实施步骤

1. `RemoteModels`/`RemoteCommandExecutor`：加 `player.command` 分支 + `validateGroup` 复用 + 单测（非法 action、缺 payload、越权 group 三种拒绝路径）。
2. `RemotePlayerOps`：实现 action → 既有 API/焦点移动的映射；导航用 `focusSearch` 优先 + `dispatchKeyEvent` 兜底（**单一通道**）。
3. 手机端遥控面板（复用现有设备页布局风格），按钮触发即发命令。
4. 失败态与日志脱敏。

## 回滚

新增 1 个命令分支 + 1 个执行类 + 1 个面板；回滚即删分支与类，服务端不需改（未知命令本来就会被 `Unsupported command` 拒绝）。

## 风险

- `focusSearch` 在某些自定义容器里返回 null：已设计 `dispatchKeyEvent` 兜底，但需在 TV 三端页面各验一遍。
- 遥控面板与实体遥控同时操作可能竞态：命令串行化（同一时刻只执行一条），并在播放页忽略高频 seek。
- 中转服务不可用即失去遥控（直连另议）。

## 待你决策

1. 是否按 C 只做"命令表 + 遥控面板"（不含语音、不含直连 `/key` 端点）？
2. 导航命令范围先只做 `up/down/left/right/enter/back` 六个是否够用？

## 实施记录（2026-11-27）

### 已落地

1. **命令通道**（`9d273a4500`）：`RemoteCommandExecutor` 新增 `player.command` 分支，鉴权沿用 `validateGroup`；`RemotePlayerCommand` 负责解析与校验（13 个动作、未知动作直接拒绝、seek 夹在 `1000..600000`、导航类 value 归零）；`RemotePlayerOps` 单一通道执行（媒体/音量走 `KeyEvent`、返回走 `Activity.onBackPressed`、方向/确定走 DPAD）。单测 4/4。
2. **手机端面板**（本次提交）：`RemotePlayerPanel` 只做两件事——挑目标、发命令。
   - 目标解析抽成纯逻辑 `RemotePlayerTargets`：排除自己、排除空 `deviceId`、同一设备只保留首次出现的分组、在线设备排前。5 个单测覆盖。
   - 目标唯一时直接开动作列表；目标多于一个时先选设备（实测里 `卧室 TV` 在线排第一，`客厅 TV` 第二）。
   - **结果提示放在命令真正发出之后**：拿到 `commandId` 才提示"已发送"，否则提示"中转服务未受理"，异常则回显 `IOException` 原文。上一版在点击时就提示"已发送"再异步发送，会掩盖发送失败，已改掉。
   - seek 步长不在面板里重写常量，交给执行端 `RemotePlayerCommand` 的默认值（10000ms）。

### 设备实测（emulator-5556，1080×1920）

用一份**临时种子 profile**（`serverUrl=http://127.0.0.1:9`，两台假设备）走通面板分支；测完已把 `shared_prefs` 恢复原样（`remote_trust_store` 计数回到 0）。

| 步骤 | 结果 | 证据（uiautomator 抓到的实际控件文本） |
|---|---|---|
| 设置 → 增强功能 → 长按"远程托管" | 面板打开 | `遥控其他设备 | 卧室 TV | 客厅 TV | 取消` |
| 设备选择 | 在线设备排第一 | 同上，`卧室 TV`（online）在 `客厅 TV`（offline）之前 |
| 选设备后 | 标题带目标名 + 13 项动作 | `遥控 卧室 TV | 播放 / 暂停 | 上一集 | 下一集 | 快退 10 秒 …` |
| 点"下一集" | 真发 HTTP，失败原文回显 | Toast：`发送失败： Failed to connect to /127.0.0.1:9` |
| 崩溃 | 无 | `logcat -b crash` 无 FATAL |

截图只用于本轮人工核对，未入库也未留痕；复现按上面的种子 profile 参数（`serverUrl` 指向一个拒绝连接的端口）重跑即可。

### 仍未验

- **真实中转服务 + 真实 TV 的端到端时延**（验收标准第一条"1 秒内响应"）：需要绑定真实中转并有两台在线设备，本轮没有该环境。
- **TV 端焦点行为**（首页/详情页/设置页的方向键、播放页不误触、无双触发）：执行层已实现、解析有单测，真机未验。
- 长按入口没有可见提示：设置页看不到"这里能长按"。后续可加一条可见行或在点击"远程托管"时提示。

### 回滚

删除 `player.command` 分支、`RemotePlayerOps`、`RemotePlayerCommand`、`RemotePlayerPanel`、`RemotePlayerTargets` 与本次接线即可；服务端无需改动（未知命令本来就会被 `Unsupported command` 拒绝）。
