# C-TV-SEEK-OWNERSHIP — TV 方向键延迟 seek 所有权

## 决策与授权记录

- 2026-11-08（Asia/Shanghai，研究开始 14:35:57，前置研究目标结束 14:43:57）：**推荐窄适配；已批准的局部合同内可进入实施，但必须先取得下面的真实调用 Red，不能把研究当作修复完成。** 本轮只研究，生产编辑、构建、提交、标签均未执行。
- 原 root `F:\Workspace\fish2018\webhtv` HEAD `47454687f504955775b398905ba09d73aeb9f20c`；最终集成 tree `.codex/task-state/worktrees/tv-small-fixes` 已核实相同 HEAD，只读且父正在 baseline，禁止写入。
- 保护原 `app/.cxx/`、未提交 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md` 及其他已有 dirty 文件。本轮唯一写入本文件和另一项已指定设计文件；未来复制本文件为实施 tree 的唯一 `docs/C-TV-SEEK-OWNERSHIP-tv-keys.md`，不另开 plan/评估副本。
- 与 C-TV-ASYNC-RESULT-OWNERSHIP 独立，二者都碰 leanback `VideoActivity`，未来串行，各自 guard/验证/原子提交/恢复标签，不合并任务。

## 冻结来源、证据与分级

访问日期均为 **2026-11-08**。A=精确代码/平台规范/可复现测试，B=维护者解释/成熟项目实现；来源描述不是本地测试通过声明。

| 来源/完整身份 | 级别 | 支持的事实、消费者与影响 | 限制 |
|---|---|---|---|
| `https://github.com/FongMi/TV/commit/7bf115759881e27df819bb01a357f4d7094d295c`；parent `0a4e0f93641d82b205ab5a5dd40a18e249a666a9`，cache `.git/upstream-tv-assessment.git` | A | 新 SeekInput 的稳定 Runnable、keyCode/deviceId/downTime；VodInput/LiveInput，TV Cast/Live/Video 在替换、预载、全屏离开、销毁取消；输入是实际三个 Activity 的生产消费者 | 不直接 cherry-pick：本地仍是 CustomKeyDown*，没有上游 BaseInput/controller 体系 |
| `https://github.com/FongMi/TV/commit/51813cb611386ab3fc3fe388480cfb12d2670f67`；parent `9febe402bb951c92856e26b95deae36c34248071` | A/B | SeekInput:非方向 DOWN 取消、零偏移不 seek；三个 Activity 控制接管/暂停取消。维护者提交正文称 84 focused Leanback tests 和 Java compilation | 该提交没带这些测试；84 是上游自述，非本地复现 |
| 上游最终 HEAD `4512b4c9f82534ff66acfde06ccdd68f35b074f0` 的 `app/src/leanback/java/com/fongmi/android/tv/ui/custom/SeekInput.java`；与 `51813cb611386ab3fc3fe388480cfb12d2670f67` 的该文件 diff 为空 | A | 最终仍是 250ms、固定 Runnable、键所有权、canSeek 二次检查、零偏移取消 | 它本身没有 WebHTV 的 immutable playback trace/session 校验，必须补足 |
| 精确 patch `.git/tv-review-core.patch`:59-762，SHA256 `53a7fd7995ae5acdb393d09c36e8724fcb58635f45d4070a7bbda14716d2b9f1` | A | 已核对这两个 seek commit 的活动取消 hunk 与输入字段/行为；只采用 seek 两条，不带其他 patch | patch 并非本地可直接 apply 的承诺 |
| `https://github.com/FongMi/TV/pull/772`，贡献者源 `ba2d0b8dc11a7b658ee5c5e7c490acf573c4d8bb`；FongMi 在页面引用并关闭 PR | B | 解释每个匿名 Runnable 无法合并，第二次按住前应移除上一提交；保留普通直播即时切线路；贡献者 28 场景是模拟 JVM fixture | 非设备/Android 端到端，贡献者的零偏移完成回调已被第二条上游提交修正为取消；不以最初 PR 覆盖批准合同 |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/view/KeyEvent.java`:47-52,86-91,2621,3124-3129,3215,3233-3245（官方 AOSP tag） | A | repeat 是同一 DOWN 序列，取消 UP 带 FLAG_CANCELED，异常丢 UP 必须容忍；deviceId 与 downTime（uptime）用于隔离所有者 | 多键 downTime 文档有“最近键按下时间”语义，不能把它单独当全局唯一 ID；组合 key/device/downTime 与会话 |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/os/Handler.java`:442-456,621-635,849-855 | A | 时间基于 uptime，removeCallbacks 只移除指定 Runnable 的 pending 消息；禁止 removeCallbacksAndMessages(null) 清全局队列 | 不撤销已经运行的回调，执行前 owner 校验仍必要；不宣称 O(1) 或性能实测 |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/app/Activity.java`:285-292,434-441 | A | onStop 为不可见，状态仍保留并可 restart；不等于释放播放服务 | 输入取消不等于停止后台音频/PiP |
| `https://robolectric.org/blog/2019/06/04/paused-looper/`，2019-06-04，维护者 Brett Chabot/Jonathan Gerrish | A/B | PAUSED 不 inline 执行主 Looper post，显式 idle/idleFor；维护者解释 legacy Scheduler 与 Looper 两份队列可能不同步 | 应使用现有 4.16，不升级至文章最初 4.3 |
| `https://raw.githubusercontent.com/robolectric/robolectric/master/robolectric/src/test/java/org/robolectric/shadows/ShadowPausedLooperTest.java`:44-46,103-121（访问时 master）与本地 `app/src/testLeanback/.../FlagSelectionListenerTest.java`:43-85、`player/exo/ExoStartupRecoveryIntegrationTest.java`:67-71 | A | 成熟 PAUSED 测试控制真实 Handler；本地已有真实组件+Looper 用法 | 外部 master 未冻结 commit，作测试方式佐证；本地 fullhash 基线为决定性锚点 |
| `https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/ui/src/main/java/androidx/media3/ui/PlayerView.java`:650-665 | B | 成熟播放器 UI 在主线程 setPlayer，先把旧 Player listener 与 surface 脱钩；解释 owner 生命周期边界 | 不能推导它已解决任何已排队回调；只是局部 ownership 的旁证，非 WebHTV 替代代码 |

官方 `developer.android.com/reference/android/view/KeyEvent`、`.../os/Handler`、`.../guide/components/activities/activity-lifecycle` 读取因网络引擎失败；改读对应官方 AOSP 精确 tag 源与 Javadoc，A级平台语义已覆盖，不需要再泛搜。Robolectric 错误的初始 tag 路径 404 后换到真实测试路径，不再重试同假设。

证据类覆盖：精确 upstream/parent/final tree、实际 patch、本地调用链、官方平台、PR/维护者、成熟播放器代码与测试均已列明。论文不适用：无新并发算法、调度器、协议或解码设计；性能 benchmark 不适用：不主张吞吐/起播/功耗收益，仅减少多余 seek 的正确性设计。不得用减少回调推断性能提升。未取得实机 field report，直接以确定性复现和设备门槛代替，不把 PR 的模拟 fixture 当实测。

## 本地实际调用链与已有保护（HEAD 同上）

1. `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`:44-68：真正 `onKeyDown(KeyEvent)` 包含 DOWN/UP；DOWN 更新 `holdTime`，UP:55 新建匿名 `App.post(() -> listener.onSeekEnd(holdTime),250)`，读的是执行时 mutable holdTime。reset:90-92 只清偏移，不移除队列。
2. `app/src/main/java/com/fongmi/android/tv/App.java`:45-49,68-82：真实 Application handler 是主 Looper async Handler；post(delay) 先 remove **同一个** Runnable，匿名实例不能合并。这是源码可达重复/过期路径，不是“已实测复现”。
3. TV Cast `CastActivity`:454-475：控制隐藏时派发 CustomKeyDownVod；onSeekEnd 只检查 player 非空、reset，再调用实际 seek。Video `VideoActivity`:6239-6240 也接实际 seek。
4. Live `CustomKeyDownLive`:47-69：通过 Listener.dispatch(true) 路由输入，左右 DOWN 预览、UP 调 onKeyLeft/onKeyRight。`LiveActivity`:1049-1052,1096-1104,1119-1127：**player.isLive() 为真时立即 prevLine/nextLine；否则 Activity 匿名 post 250ms seek**。因此只有改 Vod 输入不覆盖 Live；不能在一般 Live 左右键统一提交 seek。数字输入:73-78 的 2000ms 查台 Runnable 与本任务无关。
5. `PlaybackActivity`:321-327：实际 `MediaController.seekTo`，且不 seek 到结尾时还调用 play；故零偏移/取消后不得调用该入口，避免暂停被恢复。
6. 现有 owner `PlaybackActivity.isOwner`:162-165 仅 key 相等；同片 key 下换集、同 URL 重播不够。可复用 `PlayerManager.getPlaybackTraceId`:480-481 → `PlaybackTrace.current`:63-64；trace `begin`:42-52 使用累计 SEQUENCE，清空:67-73，初始 NONE。`PlayerManager.beginPlaybackTrace`:6246-6255 在 start:5202、parse:5229、各 switch:1357/1410/1422/1447/1492/1519/1560 更新；不在普通 seek 更新。这是可用当前媒体 session，不新增全局 generation API。
7. 当前 Video onStop:6327-6333 仅 UI/clock；onDestroy:6366-6393 清其他 Runnable，不清输入；父 PlaybackActivity:904-912 保留后台策略。必须局部清输入但不更改 service 后台/PiP 语义。

## 已批准合同与设计

**实施 disposition：补充/修正上游的所有权模型，不复制其 Activity/controller。**

- 固定 250ms；既有 Constant.INTERVAL_SEEK、长按累计、上下键加速、预览/缓冲逻辑保持。
- 每个实际 CustomKeyDown 实例一个稳定 `seekCommit` Runnable，只有 release 后排队；新合法 DOWN 撤销旧 timer、保留该 burst 偏移；commit 一次消费偏移。输入状态含 keyCode/deviceId/downTime + immutable playback session（captured PlayerManager identity + trace string）。初始 repeat>0、非 owner repeat/UP 无效；owner canceled UP 取消并清键状态；cancel 后同组 late repeat/UP 不复活。
- Listener 仅追加局部 seek 能力/当前 session 查询与 cancel-preview 通知（具体名字由实施定）；优先当前 getter，不新增 PlayerManager/PlaybackActivity API。三个 Activity 提供 `service!=null、未 exiting/finishing/destroyed、当前 owner、当前媒体可 seek` 的谓词；session 比较不能只 key/URL。DOWN 捕获，commit 校验 captured manager/trace 与当前；NONE 不作为有效 session。
- `cancelSeek` 幂等，仅 remove 该 Runnable、清键/累计/预览，不调用 seek/play。左右抵消为零也取消预览，不恢复播放。
- **Activity 必须在新请求发出前取消**（不能等 trace 在 start 才变）：换集/换源/quality/parse选择/预载接管、Cast 新媒体、Live fetch/getPlayer/换台或线路、播放替换/播放器切换、控制显示或控件接管、pause/play/menu/back、离全屏、onStop/onDestroy。trace 校验是二道防线，不替代这些取消点。dispatch 拒绝输入时也先 cancel，尤其 Live 控制/列表已显示的路径。
- 普通 Live 保留左右 UP 即时切线路、上下换台、数字查台；只可 seek Live 的左右路径走合并。`isLive()` 本地分支必须保留，能力检查不允许把普通直播改成 seek。canSeek 可基于当前 Player 的命令能力/既有本地谓词验证，不引用本地不存在的 upstream `player().canSeek()`。
- 不改触摸手势、后台音频、PiP、Web 播放、播放器引擎、Native、缓冲阈值。

### 替代方案

| 方案 | 正确性/兼容/维护 | 决策 |
|---|---|---|
| no change | 匿名多任务和 mutable holdTime 路径继续存在；可回滚但不能满足旧会话不 seek 新片 | 不选 |
| unmodified upstream（两条一起） | key ownership 与控制取消成熟；但 BaseInput/VodInput/controller 迁移过大，本地 Live 和后台/自定义 UI 会被覆盖，输入无 trace 校验 | 拒绝盲拷/cherry-pick |
| narrow adaptation | 现有五个生产文件内补稳定任务/key/session、实际取消点；保留本地消费者与语义；少量相同 input 逻辑先允许局部重复，不为此造框架 | **选择** |
| 只加 Activity remove/只加 helper 测试 | 匿名 Runnable 不可可靠移除；遗漏 late repeat、未知 UP 或真实 Live post；helper 通过不代表入口正确 | 拒绝 |

纯 Java/App 局部状态：ABI/JNI/so/锁/播放器版本/依赖/Gradle 均 **no upgrade/no change**；新增比较/少量字段成本有界，但包体/性能没有测量，不宣称零开销。安全不增加网络、权限或 URL 处理。来源追溯留本文件，不引入新 license 内容/外部代码执行。

## 精确未来允许路径（当前不实施）

生产只允许：

- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownLive.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/CastActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`

测试允许（新增任务专用）：

- `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownSeekOwnershipTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvSeekOwnershipIntegrationTest.java`
- 唯一任务记录 `docs/C-TV-SEEK-OWNERSHIP-tv-keys.md`。

不允许改 PlayerManager、PlaybackActivity、App、mobile、controller、Live 网络/弹幕、build.gradle、native/locks/third_party、原 `.cxx`；如真实 fixture 证明缺一个必需接口，不先造 hooks，记录精确缺口交父决定。

## 可执行 TDD 路线与 acceptance

测试基础已在 app/build.gradle:63-79,87-89,204-208 核实：leanback/mobile + arm64_v8a/armeabi_v7a flavors，includeAndroidResources，testLeanback Robolectric **4.16**、JUnit；不需依赖升级。

**确认 seam：真实 `CustomKeyDownVod.create(TestActivity)`/`CustomKeyDownLive.create(TestActivity)` → `onKeyDown(new KeyEvent(...))` → 真实 `App.post` → PAUSED 主 Looper → Listener 的 seek 次数/偏移/所属 session；再通过真实三个 Activity 路由和 PlaybackActivity seek 到 consumer 的集成测试。** TestActivity implements 原 Listener，recording listener 只观察出站结果，不能把输入或 App/Handler mock 掉。测试专用 `TestApp extends App` 可覆盖 onCreate 避免无关初始化；App 构造器:45-49 自动设置 instance 与真实 Handler，无需生产 test hook。

按垂直切片：一个真实失败测试 → 最小生产实现 → 同测试 Green，不先写一堆 helper 测试。

1. **首个 Red**：Vod 实例，RIGHT DOWN/UP（device=1，downTime=10）；advance 100ms，再 RIGHT DOWN/UP（downTime=110）；advance 149ms 应零 seek，advance 1ms 仍零（第二 UP 后才 150ms）；再 advance 100ms 应仅一次 `+2*INTERVAL_SEEK`。旧版第一匿名任务将在第一 UP+250ms 触发，故真实 Red；不要错误断言两次 UP+250 都已经过才测试零。另切片“第二次按住未 UP”，跨第一期限仍不能 commit。
2. 正常 DOWN repeat1/2（同组）累计、最终 release 一次；没有初始 DOWN 的 repeat、错误 device/downTime/key UP、重复 UP 不排队；owner FLAG_CANCELED UP 后 drain 零提交，late repeat 不复活。
3. LEFT→RIGHT 抵消为零不 seek、不 play、预览取消；后续新 burst 干净。非方向 DOWN、控制接管、pause/menu/back 每次待提交清零。
4. **首个 session Red**：真实 CustomKeyDown 排队 → 改测试 host manager/session A→B（或 A→B→A 同 URL）→ idleFor(250ms)，B/A2 均零旧 seek；当前 session 正常回调必须 apply，避免“全取消”伪修复。
5. 三 Activity 集成：通过真实 dispatchKeyEvent/lifecycle、真实 onSeekEnd/seek；可用 test-only subclass 或有限 fixture 注入 controller recording boundary，禁止重写实际取消逻辑、复制源代码回调或 source-text assert。检查换集请求刚发出而尚未返回、源/quality/parse/播放器替换、离全屏/onStop/onDestroy。每个声明取消点必须有可执行 consumer 证据或任务专用设备 smoke；不能只 helper/mocks 当验收。
6. Live 集成：可 seek Live burst 同 Vod；普通 `player.isLive()` 左右 UP 仍立即各一次 prevLine/nextLine，没有 delayed seek；上下换台/数字2000ms不变。Cast/Vod 的暂停状态取消不得恢复播放。

未来实施 tree 环境：JDK21 `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，SDK `C:\Users\Maple\AppData\Local\Android\Sdk`，GitBash `G:\Git\bin\bash.exe`。首个命令（只在该任务隔离实施 tree，不能在 root 或父正在 baseline 的 tree）：

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest' --tests 'com.fongmi.android.tv.ui.activity.TvSeekOwnershipIntegrationTest'
```

先保留 Red 的原始失败输出（必须行为 assertion 失败，不是 classpath/fixture 错误），再实现、同命令 Green。命令未在本研究运行；flavor 名由本地 build 配置推导，若 Gradle task 名不匹配，只做一次 task 精确定位再执行。不得触发 native 全 ABI rebuild；若 unit-test task 拉 native，使用该实施 tree 的现有 prebuilt/任务排除方式，不在 root 动 `.cxx`。

最终 acceptance：250ms/步长/加速不变；同 burst 一次；cancel/repeat/设备与 session 隔离；每个替换/控制/生命周期零旧 seek；三个生产消费者可达；普通 Live 未变 seek；编译/针对测试通过且已有 baseline 非回归；Exo/MPV 分别设备关键 smoke（至少 VOD 快速按键→换集与可 seek/普通 Live），不把一个 player 当另一种实测。无实机时该项明确未通过，不声称全产品完成。

## 剩余 gate、回滚与交接

- **G1 首个 Red/Green 未执行**：实施先完成真实 App/KeyEvent/Looper fixture；Fixture 若需生产 test hook 或触及第六生产文件，停止扩边并回父。
- **G2 actual canSeek/三个 host 取消映射**：不能照抄上游不存在的能力接口；用本地能力与 controller 行为通过集成测试验证。
- **G3 baseline/设备**：父 baseline 完成后在各自隔离任务 tree 实施，完成上列 smoke；本研究不占用它。
- 回滚锚点为 `47454687f504955775b398905ba09d73aeb9f20c`（后续串行实施前记录真正父 HEAD）。独立原子提交包含五生产文件、任务测试与唯一 docs；使用 `git revert <本任务完整commit>`，不 reset、不撤销弹幕后续任务、不改原 dirty 路径。回滚后核对 250ms/普通 Live 左右与原 baseline；恢复标签只指已提交并验证状态。
- 本轮研究简报 run=1/3 累计；同失败假设最多2 attempts累计后换法；本轮文件编辑 cap=2 file累计，生产=0 file累计；只读 commands不限累计；每实质 query改写 cap=3累计；证据 quota每任务重置不结转，当前取决定性 A/B 证据后停止可选搜找。8分钟为前置研究目标而非停工上限。

### Recovery anchor

研究文档完成；未实施、未运行测试。当前允许本 `.git` 文件，未来唯一 docs 路径见上；关键接口是真实 App handler、CustomKeyDown dispatch、已有 PlaybackTrace getter，isOwner(key) 不是会话。下一步：父 baseline 完成后，为本任务开独立 lane/guard，先运行真实 CustomKeyDown 连按首个 Red。

## 实施阶段2 — 2026-11-08（覆盖上方研究期未实施状态）

- 已批准执行；隔离 tree `F:\Workspace\fish2018\webhtv\.codex\task-state\worktrees\tv-small-fixes`，branch `upstream/small-fixes-20261108`，实际 rollback anchor `60f2e86e761b8411e56a86793ee55a2f0b9b67d7`。root 继续只读。
- 父已提交跨任务索引 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md` SHA256 `beec8fbed4434e0c8004b6d66585dd67d2550553ddc087057f8c944c2cc7edf1`，不得编辑；baseline 同 variant 7项PASS为父提供，不重复运行baseline。
- 15:05:18开始；目标15:30完成（定位/RED6分钟，实现增测12分钟，验证收尾7分钟）。超时删可选工作，保留针对测试/两flavor编译/guard/tag，不降低行为gate。
- guard `start --id C-TV-SEEK-OWNERSHIP --mode standard`，scope为上列五TV生产路径、两个任务测试、唯一doc与同前缀ignored报告/log；受控cap8 file累计，生产未改mobile/公共API/资源/Native/弹幕。
- 第一个业务RED：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest --console=plain`；`burstFromPreviousPlaybackDoesNotSeekReplacement`，真实 CustomKeyDownVod.create/onKeyDown + App.async Handler + ShadowLooper PAUSED；两次RIGHT release后真实PlaybackTrace.begin换会话，再drain，1 test / 1 AssertionError。原始证据 `.codex/task-state/reports/C-TV-SEEK-OWNERSHIP-red-behavior.log`。
- 第一个GREEN：同真实test，`BUILD SUCCESSFUL in 1m 4s`，证据 `.codex/task-state/reports/C-TV-SEEK-OWNERSHIP-green-session.log`。旧session不得提交，而非helper/source断言。
- 非业务故障已分类：首次log目录缺失未执行测试，补建ignored目录；首次Shadows.shadowOf重载导致FingerprintManager类缺失，改用既有Shadow.extract，无依赖升级；一次PowerShell `R`命名撞Invoke-History别名，按已确认的任务文件精确从HEAD恢复本次中间写入，改用ReplaceText且ErrorActionPreference=Stop后正确应用。均不冒充业务RED。
- 最小适配为了两个入口共享同一规则，在CustomKeyDownVod中嵌入package-private `SeekSession`、固定commit Runnable和SeekListener；CustomKeyDownLive沿用该局部状态，不新增BaseInput/VodInput/controller/生产文件。mandatory listener getter使用现有PlaybackTrace，NONE禁止；具体生产取消映射与最终测试见下方最终记录。
- 当前运行真实CastActivity的dispatchKeyEvent/预览Back/onSeekEnd consumer测试（仅seekTo输出边界record，真实PlayerManager/Player/trace/控件）；生产hooks=0。

## 最终实施与验收记录 — 2026-11-08

- 已实现且允许路径为：五个leanback生产文件（CustomKeyDownVod/CustomKeyDownLive/VideoActivity/CastActivity/LiveActivity）、两个testLeanback任务测试、唯一doc；累计8受控文件，生产test-only hooks=0。mobile源文件/触摸/后台/PiP/弹幕/Native/资源/Gradle依赖/PlayerManager与PlaybackActivity公共API未修改。
- CustomKeyDownVod内package-private SeekSession复用真实App主Handler，固定commit Runnable，250ms，keyCode/deviceId/downTime + immutable PlaybackTrace session；NONE无效、cancel幂等、late repeat/UP不复活、zero-offset不触发onSeekEnd（故不会经PlaybackActivity.seekTo恢复播放）。CustomKeyDownLive复用同状态，普通Live左右仍走onSeekUnavailable→原onKeyLeft/onKeyRight即时线路分支，数字2000ms与上下换台保留。
- 实际取消映射：Video getDetail/getPlayer/setPlayer、setParse、refresh/replay、quality/核切换的实际替换/stop路径、show/hide/toggle controls、pause/play/seekBack/seekForward、disc/opening-ending/episode/audio-stage接管、exitFullscreen、onReclaim、onStop:6390、onDestroy:6431；Cast start/核/控制/远程seek/pause/play/stop/Back、onStop:565/onDestroy:591；Live fetch(EpgData):829/fetch():839/start/核/controls/showUI/reload/stop/pause/play/Back、onStop:1203/onDestroy:1226。trace仍是未显式通知的替换/预载接管二道防线；没有另迁controller。
- 保留预览Back：Back DOWN移除timer但保持原预览可见，真正onBackInvoked消费预览而不离页；native pause只在isPaused时cancel，不将buffering false当pause。
- 真实业务RED两条：session替换测试1test/1assertion failure（red-behavior.log）；Cast真实dispatchKeyEvent→预览Back→onSeekEnd输出边界测试1test/1assertion failure（red-back.log）。初始化错误不算RED。
- 最终可信验证命令：`gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest --tests com.fongmi.android.tv.ui.activity.TvSeekOwnershipIntegrationTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain`，日志 `.codex/task-state/reports/C-TV-SEEK-OWNERSHIP-final5.log`，exit0，BUILD SUCCESSFUL in 2m55s；XML汇总21 tests/0 failures/0 errors（16真实输入+5真实Cast/Video消费者），两flavor Java编译PASS。
- 16输入测试具体覆盖：250ms最后释放/新按住合并、repeat、key/device/downTime误匹配、canceled UP/repeat、zero offset不提交、NONE/lost capability、A→B→A session/current positive、cancel/full exit、UP长按速度、Live合并/控制取消、普通Live即时左右/上下/2000ms数字、取消Live UP。5实际Activity测试覆盖Cast预览Back/BackDOWN/控制接管+当前positive、Video实际消费+同key重启trace/控制接管。
- 两个额外大fixture（完整Live实际engine判定/三页未create生命周期）因fixture初始化不足失败，不作为业务RED；按照父2026-11-08确认的窄等价路线改为“真实CustomKeyDownLive行为 + 实际Activity取消调用点代码核对/两flavor编译”，不只删失败宣称完整生命周期已验。完整设备lifecycle/Live实际线路网络/MPV/后台/PiP仍**未设备验证**，不得把上述21PASS等同这些场景实测。
- 试验中的COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM能力门会在未prepare fixture/timeline时抑制既有正常输入，已移除；保留本地非空/isOwner/非redirect/trace及Live原isLive分支，不将新能力门改变当前TV语义。编译静态start误插入cancel及KeyUtil缺import已修正。
- `git diff --check` PASS；跨任务索引SHA256仍 `beec8fbed4434e0c8004b6d66585dd67d2550553ddc087057f8c944c2cc7edf1`。所有报告/log在ignored .codex/task-state/reports，不stage/commit。原root和app/.cxx未操作。
- 回滚锚点 `60f2e86e761b8411e56a86793ee55a2f0b9b67d7`；应独立revert本任务完整commit，并重新检查250ms/普通Live原分支；不要reset或撤销后续弹幕单元。
- 代码commit/recovery tag由下一个task_guard finish原子生成；精确ID留ignored report及guard current/committed_head/recovery_tag（commit无法在自身正文写自己的hash，不改写/移动标签）。父如要求唯一doc记录具体post-commit ID，可用不可避免的文档-only closure而不重开测试/生产。

### 最终 Recovery anchor

状态：局部seek实现与21聚焦测试/双flavor编译已PASS，代码审阅/范围/索引保护PASS；完整设备生命周期/Live网络/MPV/后台/PiP未验且已明确路由给父。下一动作：task_guard finish，生成本单元原子commit与recovery tag，报告精确ID，交父独立复核后串行弹幕。

## 最终集成归档 — 2026-11-08

- 用户已批准本任务窄适配，原始代码提交及恢复点在合并历史保留，不重写。
- 代码/修复提交：`dc5f1e60c4f96f88f5183cbf11b50bf97f2c6b65`
- 本地恢复 tag：`recovery/C-TV-SEEK-OWNERSHIP/20261108075050-dc5f1e60c4f9`
- 单元证据：21项真实输入/消费者测试和双端Java编译通过；完整设备生命周期未测
- 联合验证（2026-11-08 集成树 fresh）：9 类 94 项定向测试 0 失败/0 错误/0 跳过；Leanback Mobile arm64 Debug Java、Mobile UnitTestJavaCompile 编译通过；日志 `.codex/task-state/reports/TV-SMALL-FIXES-INTEGRATION-final.log`。整批仍不冒充 decoder 实播/设备/性能验收。
- 回滚：针对本任务按上述代码链逆序git revert；合并历史保留真实来源，不reset原工作区或移动tag。

## 本轮评审修复

改动范围仅 `CustomKeyDownVod.java`、`CustomKeyDownLive.java`、`LiveActivity.java`、`CustomKeyDownSeekOwnershipTest.java` 与本文件；无新增/删除文件。

1. `CustomKeyDownVod.SeekSession.onUp`：原先 `if (!owns(event)) return;` 让"没有配对 DOWN 的左右 UP"被完全丢弃，回归了旧实现（旧 `CustomKeyDownVod`/`CustomKeyDownLive` 的 ACTION_UP 分支无条件调用 `onKeyLeft`/`onKeyRight`）。现改为 `pending == false` 时先 `listener.onSeekUnavailable(event)` 再 return；`pending == true`（有进行中的 seek 却收到错配 UP）仍直接 return，不误切线路。
2. `LiveActivity.canSeek()`：删除 `dispatch(true)` 条件。面板/列表可见性已在 `CustomKeyDownLive.onKeyDown` 的 DOWN 阶段判过；保留它会让 UP 之后 250ms 的合法时移 seek 在面板恰好出现时被静默丢弃。其余条件与顺序保持原样。
3. `CustomKeyDownLive.check()`：`listener.onKeyDown();` 后的多余空行删除，if/else 链连续，无逻辑变化。

影响面：VOD/Cast 不受影响（`onSeekUnavailable` 只被 `CustomKeyDownLive.Listener` 覆盖，VOD/Cast 侧仍是默认 no-op，`unownedRepeatAndReleaseCannotCreateBurst` 语义保持）；普通 Live 恢复左右 UP 立即切线路的旧行为；`canSeek` 不再因控制面板可见性丢弃已提交的 seek。`CustomKeyDownVod.SeekListener`/`CustomKeyDownLive.Listener` 方法集合与实现者保持一致，没有新增抽象方法。

新增测试：`CustomKeyDownSeekOwnershipTest.unpairedLiveReleaseStillSwitchesLineWithoutSeek` —— 只发左右 UP、无配对 DOWN 时 `LiveHost` 的 `lefts`/`rights` 各 1 且 `seeks` 为空，再断言 `VodHost` 同输入下 `seeks` 仍为空（VOD 默认 no-op）。

验证状态（2026-11-09，Asia/Shanghai，父代理执行）：`gradlew.bat --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests <6 类>` → `BUILD SUCCESSFUL in 7m29s`，mobile 与 leanback 主源集及 `compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac` 全部通过；6 类共 80 tests / 0 failures / 0 errors / 0 skipped（`DanmakuApiOwnershipTest` 3、`StrmHttpTest` 19、`TvSeekOwnershipIntegrationTest` 5、`VideoDanmakuOwnershipTest` 22、`CustomKeyDownSeekOwnershipTest` 17、`ProxyResponseCompatibilityTest` 14）。仍未验：设备/真机（实机 seek、直播即时切线路、时移、慢响应弹幕覆盖、mobile 真实消费者）、全量 Gradle 矩阵/`connectedAndroidTest`/APK 打包安装、QuickJS/Python 真桥的 `destroy` 清理与 gzip 端到端播放。

## Issue #582 — HDMI-CEC 输入兼容修复（2026-11-25）

### 授权、基线与边界
- 用户先要求查看 issue，收到只读诊断后明确回复“开始优化吧”；授权修复该 issue 的快进/快退及临时倍速输入兼容，不授权新增设置、改变倍速/步长、安装设备、推送或调整播放器/原生依赖。
- guard：ISSUE-582-CEC，quick-fix；分支 dev1，基线 21c925bb2c131ed50fc58594eb66bec491f00cd3。已有 OriginMediaRelay.java、Proxy.java、两个 ProxyChunkedResponsePolicy 文件及 .scratch-goal/ 均为保护路径。
- 写入范围：CustomKeyDownVod.java、testLeanback 的 CustomKeyDownCecInputTest.java / 必要的 CustomKeyDownSeekOwnershipTest.java、本文件；测试证据限 ignored .codex/task-state/reports/ISSUE-582-CEC/。

### 已确认依据与设计选择
- Issue：https://github.com/Silent1566/webhtv/issues/582；2026-11-25 经 GitHub API 读取，0 评论。报告 202611081126 可 seek、202611211059 不可 seek，以及长期上键长按误调片头。未取得报告者设备事件轨迹，不宣称已实机复现。
- 官方源码（A级，访问 2026-11-25）：https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/services/core/java/com/android/server/hdmi/HdmiCecLocalDevice.java ，固定 tag android-14.0.0_r1（annotated tag object bd9ef6de90a1b726729d939f48dd8e7dbbe27218）。handleUserControlPressed / handleUserControlReleased / injectKeyEvent 为每条事件使用当前 uptime 作为 downTime 和 eventTime，repeat 递增、device=-1、SOURCE_HDMI、FLAG_FROM_SYSTEM，无 FLAG_LONG_PRESS。因此问题模式来自真实平台实现，而非只凭不规范事件猜测。
- 本地 CustomKeyDownVod.java:126-180 严格匹配 key/device/downTime；:62-74 临时加速仅使用 isLongPress；VideoActivity.java:9253-9263 控制栏优先，:9451-9468 普通上键可聚焦片头，:9168-9179 上键增加片头。
- 回归来源：dc5f1e60c4f96f88f5183cbf11b50bf97f2c6b65 在旧发布 852d8428d3ea207c86bc1ce18c999ffce8df67ee 与故障发布 bd54443af1156b1a22cc226e17aa7bb44dcab64e 之间加入 seek 所有权；当前仍有相同约束。
- 方案比较：不改会保留官方 CEC 模式失败；全局删 downTime 校验会退化非 HDMI 输入隔离，拒绝；选择 SOURCE_HDMI 限定的适配，保留同键/设备/初始 DOWN/会话/cancel 和单次延迟提交，普通输入约束不变。
- HDMI 上键通过同次按住的重复事件与系统 long-press timeout 判断，释放恢复原倍速；独立短按不合并。保持原生 FLAG_LONG_PRESS 路径和用户临时倍速设置；不增加菜单/开关/协议。
- 研究范围说明：这是既有 App 输入路径的局部兼容修复，无依赖、ABI、解码器、网络或跨模块契约变更，不启动上游合并评估。问题报告、固定平台源码和既有真实输入测试可决定方案；无需论文、benchmark 或无关项目调研，不将此记录称为全领域最佳实践审查。

### 实施与验收
- [x] 真实 CustomKeyDownVod + App Handler/Looper 的 HDMI downTime 变化和无 LONG_PRESS 重复输入先 RED，再最小实现 GREEN。
- [x] HDMI 普通左右短按/长按累计/250ms 合并成功；取消、孤立 repeat/UP、错误设备/键/来源、播放会话变更不错误提交。
- [x] HDMI 上键短按仍只普通导航；到系统长按阈值后仅一次加速，释放恢复，不再额外普通上键；取消/其他键/已有 releaseSpeed 路径清状态；普通遥控器原行为不变。
- [x] 既有 CustomKeyDownSeekOwnershipTest 与 TvSeekOwnershipIntegrationTest 通过，Leanback Java 编译由测试任务覆盖；不运行 native 或全 ABI 矩阵。
- [ ] Lead 独立审阅最终差异并运行风险相关回归；记录实际输出后 guard finish 原子提交并创建 annotated recovery tag，不推送。
- 实机边界：现仅连接 LIO-AN00 手机，不操作其 UI、不安装 APK。Robolectric 可验证官方事件模式，但无法代替报告者电视/盒子 HDMI-CEC 链路验收；交付明确该事实。

### Recovery anchor（实施前）
目标：修复 #582 的官方 HDMI 输入模式并保留 seek 会话保护；设计/边界已定，TDD 实施待开始。任务计数：implementation run 1/3。当前任务无生产改动。风险：没有问题电视实机轨迹；采用 SOURCE_HDMI 限定适配避免扩大普通输入行为。回滚：完成后 git revert 本任务单一提交，不 reset 或触碰已有 dirty 文件。唯一下一步：在上述源/测试边界执行 TDD 并交回差异和完整证据。

### 实施记录与定向验证（implementation run 1/3）
- 实际代码只改 CustomKeyDownVod.java，新增 CustomKeyDownCecInputTest.java；既有 ownership 测试未改。私有 HeldKey 复用键/设备/来源及有序事件校验，SOURCE_HDMI 允许每事件变化 downTime；普通输入仍严格 downTime，初始 repeat=0 和播放 session 检查不变。HDMI 上键 repeat 累计 eventTime 到平台阈值后一次加速，释放/取消/其他键/releaseSpeed/退出全屏清状态；seek-only cancellation 不清 speed hold。
- RED 命令：bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownCecInputTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest --console=plain --offline。源文件未改前 32 tests / 13 assertion failures，全部来自新增 CEC 测试（15/13），原 ownership 17/0；red.log、red-TEST-*.xml。
- 子代理自查修复了普通 Live 无配对 UP 回退被误抑制，以及 HDMI 持有期间非 HDMI DOWN 混入倍速识别；后者单独 red-source-mix.log 为 1 test / 1 failure。最终同命令 BUILD SUCCESSFUL in 36s；15 CEC + 17 ownership = 32 tests / 0 failures / 0 errors / 0 skipped。证据 green-accepted.log、green-accepted-TEST-*.xml。Lead 已读取真实断言及 XML 核对，不只采信报告。
- Lead 独立审阅最终生产差异和全部新增行为断言；确认未改 Listener 接口、步长、250ms 提交和 session/cancel 保护，没有视觉变更。
- Lead 消费者集成首轮因 SDK27 android-all-instrumented-8.1.0-robolectric-4611349-i7 未缓存，MavenArtifactFetcher 在网络读取阻塞（线程栈 lead-integration-threads.txt），尚未执行用例即停止，不算测试通过或失败。直连 Maven Central 下载 120s 仅 14965414/96651178 bytes，归类环境传输问题；切换镜像下载同一 artifact，并以 Maven Central 原始 SHA512 f6b923d67e9165f35592f1baa007f9c25906c2b81b1474ec4e20e2886f5e8bfe2893abaf08b35b8c9d480599620998c65177ef5d0e3b1b5bae89b55dad18e79b 校验后只填用户缓存，不改 SDK/test 注解或构建配置。
- 所有证据位于 ignored .codex/task-state/reports/ISSUE-582-CEC/；源码差异 final.patch，定向摘要 verification-summary.txt。

### Recovery anchor（Lead 验收中）
基线和保护路径不变；已实现并完成真实输入 RED/GREEN，Lead 最终差异检查完成，消费者集成因缺失 SDK27 已转为校验后缓存路线。生产和测试改动尚未提交，未操作任何设备。唯一下一步：收集 lead-integration-cached.log 的原定集成结果，成功后补最终计数并 guard finish 提交/标签；若失败只处理直接原因，不跳过门槛。

### Lead 最终验收（2026-11-25，提交前）
- 官方 SHA512 校验后的 SDK27 运行库缓存完成；原命令只重跑先前环境阻塞、未执行用例的 TvSeekOwnershipIntegrationTest。BUILD SUCCESSFUL in 49s，14 tests / 0 failures / 0 errors / 0 skipped；lead-integration-cached.log、lead-TEST-*.xml。未改用 SDK28、未跳过测试，未改任何测试期望。
- 最终合计 46 tests（15 HDMI + 17 原输入所有权 + 14 消费者集成），均 0 failures/errors/skipped。Leanback 主源码/测试源码 Java 编译通过，Gradle 既有 deprecation 警告未扩展处理。Lead 审查通过，无未解决的阻塞代码发现。
- 已路由风险：普通 Live 回退、跨来源误识别在子代理 run1 自查中修复并重验；缺 SDK27 的环境阻塞经校验缓存后解决。无电视/盒子硬件验收与非 SOURCE_HDMI 厂商私有事件模式验证，明确保留为交付边界，不宣称实机修复通过。没有播放器内核、Native、依赖版本或视觉布局变更。
- 子代理实现总 runs=1/3，Lead 独立验收通过；未重跑已成功且源码未变的 32 输入测试。guard finish 负责最终 scope/protected/whitespace、原子提交和本地 annotated recovery tag，未经授权不推送。

### Recovery anchor（验收完成、待本地闭环）
目标和自动化验收已完成，3 个任务文件为 CustomKeyDownVod.java、CustomKeyDownCecInputTest.java、本文件。未提交工作已验证；基线 21c925bb2c131ed50fc58594eb66bec491f00cd3，受保护 dirty 路径未归入任务。设备验证未执行。唯一下一步：guard finish 提交并立即标注本地 recovery/ISSUE-582-CEC 标签，随后报告实际提交与标签，不再额外构建或研究。

## Issue #582 — 指定模拟器补充验证（2026-11-25）

- 用户明确指定 192.168.50.3:5555“模拟器验证能验证的场景”，授权该设备调试包更新及测试操作；不授权清数据、卸载、root、刷机、真实 HDMI 链路完成声明或生产行为扩展。
- 本轮 guard ISSUE-582-CEC-DEVICE，quick-fix；分支 dev1，代码基线 a904628207f4e1fe2a28bbe25b030202df5fe0fd，恢复 tag recovery/ISSUE-582-CEC/20261125203432-a904628207f4。现有 OriginMediaRelay、Proxy、两个 ProxyChunkedResponsePolicy 文件及 .scratch-goal/ 仍保护。
- 设备探测：ADB device；model LIO-AN00，API28，x86_64，1920x1080，ABI列表含 arm64-v8a，native bridge libnb.so。设备已安装 debuggable com.silent.android.webhtv 和 androidx test instrumentation，原 APK 更新时间 2026-11-24 23:15:29，早于修复。不把型号字符串误当物理手机判据。
- 允许只新增 app/src/androidTest/java/com/fongmi/android/tv/ui/custom/CustomKeyDownCecDeviceTest.java 并更新本文件；报告、APK备份、日志和截图仅 ignored .codex/task-state/reports/ISSUE-582-CEC-DEVICE/。不改生产代码、manifest、Gradle、依赖或原生库；需要扩界先 hand_back。
- 验证路线：备份已装 APK身份/签名/必要数据状态；只做 adb install -r 更新、不 clear/uninstall。仅构建 LeanbackArm64_v8a Debug 与 androidTest 包；当前本地已有未提交改动会进入普通构建，交付明确“测试 APK 来自当前工作树”，不冒充独立 release artifact。
- instrumentation 必须加载目标 APK 的真实 CustomKeyDownVod 和实际 Android KeyEvent/Handler，不复制状态机；构造 SOURCE_HDMI、变化的 per-event downTime、无 LONG_PRESS 标志的 repeat。直接输入组件验证与真实播放页面端到端需分列，不能把记录 Listener 回调声称为播放器 seek/倍速实播。
- 目标场景：左右短按10s/长按累计/250ms后单次提交；普通遥控输入不回归；上键短按/连续独立短按、无标志长按一次加速及释放恢复、取消/其他键/离全屏/旧session事件清理；视既有页面/本地媒体入口可达性补实际播放页面 smoke，保存同一场景截图或精确数值/日志。
- 测试保留用户数据和设置；任何临时播放/输入/媒体状态在结束恢复到合理首页。备份 APK可用 adb install -r 恢复，仅需要时执行、不无条件降级。
- 边界：模拟器不支持验证物理CEC总线、电视固件转发、遥控器时序、电视待机/风扇、真实设备解码/性能。报告每项 PASS/FAIL/BLOCKED 与实证，不承诺所有场景必能跑通。

### Recovery anchor（设备验证开始）
完成目标：在指定模拟器验证可覆盖的 #582 场景并留可复现 instrumentation 及证据；代码修复已提交。设备实施 runs=1/3，当前测试未写、未安装。唯一下一步：设备执行子代理在上述测试边界完成 APK备份/构建/定向测试及恢复，交回命令、测试计数、截图和未覆盖项。

### 设备验证阻塞与续行（run 1/3，干净 hand_back）
- 第一次同时构建目标包/测试包，目标 Leanback Debug APK package 完成；androidTest Java 编译因既有 OriginIsolationPlaybackTest.java:464 引用不存在的 R.id.errorText 失败，BUILD FAILED in 2m53s，未安装设备、未执行测试。Lead 核对日志和该精确行，未更改无关测试。
- 这是边界外旧测试编译问题；不扩大任务去修它。Lead 续用原子代理，批准 ignored 任务目录内 Gradle init script 只选择 CustomKeyDownCecDeviceTest.java 编译成专用测试 APK，再仅运行此类。不得把该隔离构建表述为整个 androidTest 源集可编译；保留原失败证据。目标包复用此次已完成产物，无需重复完整构建。
- Lead 已审阅测试 lifecycle、真实 KeyEvent/Handler、记录 Listener 和真实 CastActivity/local MP4 两条证据路径，核对 CastAction 实际 class/extra；测试明确区分直接输入回调与页面播放器 seek/speed，不改生产接口。
- 原目标/测试 APK、证书/哈希及偏好和数据库只读快照已经保存；构建日志、备份和本地 MP4 均在本轮 ignored 证据目录。hand_back 无越界修改，不计失败；同一个 implementation run 1/3 续行。
- 第二次隔离测试 APK 构建成功（1m43s），但部署前证书检查发现既有 com.silent.android.webhtv.test signer SHA256=95e4b2e7043f82a052bebfe1bd44a4a6df3eb383f2d01688144d60e27cff7e4d，新测试 APK signer=32d245c5deda22a35c7a0b7cbecfc5d0c9eee62d5b56b53a7473467c4cd8df2e。不覆盖、不卸载旧测试包；未启动 instrumentation，干净 hand_back。
- 主应用旧/新签名相同（32d245c5deda22a35c7a0b7cbecfc5d0c9eee62d5b56b53a7473467c4cd8df2e），install -r 已成功，原数据保留，当前设备保留新 Leanback 测试包版本。原 mobile APK 备份可恢复，不做无授权清数据/降级。
- Lead 决定使用任务专用 instrumentation applicationId com.silent.android.webhtv.cec582test，在 ignored init script 设置 testApplicationId，仍 target 原 com.silent.android.webhtv、仅选本次测试类。不进行 APK 二进制篡改，不搜索用户私钥，不改 tracked Gradle/Manifest；核对最终 package/target/signer 后安装新 test identity，保留原测试包完全不动。此为本次验证授权内的最小部署适配，继续原子代理 run1/3。

### 初次设备执行结果与 Lead 验收发现
- 独立测试包部署后，仅执行 CustomKeyDownCecDeviceTest，instrument-1.log 为 OK (10 tests)，24.317s，10个 STATUS_CODE=0，无失败/忽略；shell exit0。Lead 直接读取原始输出核对，非仅采信子代理摘要。
- 9项直接输入测试加载目标 APK 生产 CustomKeyDownVod、Android KeyEvent/主Looper，以记录 Listener 断言输入回调；不等价于播放器实播。第10项使用未改生产 CastActivity、实际 dispatchKeyEvent、真实本地 MP4/播放器：before=30352ms，preview=31253ms，release后 after=51750ms，speed=1.0/3.0/1.0，short UP currentFocus=MaterialTextView。
- Lead 逐张审阅六张1920x1080截图：烧录时间码 30.400→31.067→51.733 可证明 seek 前后帧变化；倍速/控制截图只有视频没有可见控件，不能声称截图直接证明3x overlay或控制栏焦点。已退回原子代理加强 real-page 控制 root isShown/globalVisibleRect/焦点是其后代和截图时序证据；若确为生产视觉缺陷则 hand_back，不在测试任务中修生产代码。
- 数据检查初报：3个SQLite逻辑行摘要相同；tv/tv-wal 字节相同，tv-shm仅运行时锁/索引变化。11个偏好文件除默认偏好 perf_exo_decoder_runtime_v1 自动遥测键外字节一致；不宣称所有文件字节完全相同。Lead 已路由为测试前后只恢复该一个键，精确保留原存在性/类型/值，不覆盖整文件、DB或用户其他键；必要原值文件只放任务cache，不在日志泄露内容。
- 同一测试文件补 @After 输入释放和 @BeforeClass/@AfterClass 遥测精确恢复，相关改动后仅定向重验受影响场景；初次10/10结果仍保留其对应源版本，不以旧结果替代新改动验证。

### 设备最终验证与 Lead 结论（2026-11-25）

**结果：PASS（可模拟范围）；物理 HDMI-CEC 总线未验。** 本轮不再修改生产代码，仅新增设备测试和本记录。初次完整设备类10/10 PASS（24.317s）；加强真实页面可见性/焦点断言、每测试释放及单键恢复后，仅重跑受影响的真实页面方法1/1 PASS（13.031s）。没有忽略/跳过；不将两次结果说成11个独立场景或最终全类重跑。

| 范围 | 实际覆盖 | 结果/依据 |
|---|---|---|
| 真实Android输入组件、记录Listener（9项） | HDMI左右短按+/-10s、约250ms一次提交；长按累计/快速连按；错误键/设备/来源/旧事件/孤立repeat拒绝；取消/session替换/离全屏/canSeek失效；上键短按/独立短按、无LONG_PRESS重复长按一次加速/释放；普通DPAD严格downTime与FLAG_LONG_PRESS | instrument-1.log 前九项PASS；加载目标APK生产输入和真实Looper，但不冒充播放器实播 |
| 真实播放页面（1项最终加强版） | 原生产CastActivity、本地150s MP4、原播放器和dispatchKeyEvent；HDMI右键累计+20s；上键长按/释放；短UP打开控制栏及焦点 | before=30000ms，preview=30588ms，after=50982ms；speed=1.0→3.0→1.0；focus属于控制栏 |
| UI可见性 | speed/control/focused-child shown/attached/alpha1/windowFocus/global非空；Lead看最终截图 | speed Rect(936,48-984,96)，control Rect(0,854-1920,1080)，focus Rect(48,902-152,972)；最终控制栏可见刷新焦点和1x |
| 物理设备链路 | 电视固件、CEC总线、遥控器重复时序、待机/风扇、非SOURCE_HDMI私有模式 | NOT VERIFIED，模拟器不能覆盖 |

- 定向运行：adb -s 192.168.50.3:5555 shell am instrument -w -r -e class com.fongmi.android.tv.ui.custom.CustomKeyDownCecDeviceTest com.silent.android.webhtv.cec582test/androidx.test.runner.AndroidJUnitRunner。最终仅运行同类#zRealPageLocalPlayerSeekBoostAndControlFocus，并通过cecRuntimeBaseline参数读取任务cache内首次原值以恢复一个遥测键；该文件已删除，日常重跑省略参数由测试自行快照。构建/安装/fixture完整步骤见ignored reproduce.sh。
- 产物：本次工作树LeanbackArm64_v8aDebug基于a904628207f4e1fe2a28bbe25b030202df5fe0fd，含既有受保护本地改动，不是纯净release。target APK SHA256 ec42d34c462363e01eea0a84715c043df51d342e5e09128c9bac7244be55baca；最终专用test APK SHA256 33d2d727309d1f85b73f1af5d29cfc77aed57c2e891521ff9478f4592fa52314。原主包同签名install -r；原test路径/签名/更新时间未变，独立cec582test包用于验证。
- 证据根：.codex/task-state/reports/ISSUE-582-CEC-DEVICE/。完整REPORT.md和CustomKeyDownCecDeviceTest.patch；初次logs/instrument-1.log；最终logs/instrument-real-page-final.log、logs/final-focused-live-logcat.txt；最终六截图screenshots/final/，结束screenshots/final-home.png；原APK和私有快照只留本地backups，不进Git。Lead读取原始instrument输出、真实数值、测试代码并审阅最终截图；最终聚焦日志FATAL EXCEPTION/AndroidRuntime/ANR计数为0。
- 首轮截图只有视频帧：未等UI帧，补100/150ms有界主Looper帧时间并增加shown/bounds/焦点后代断言，定向复验通过。没有弱化期望或改生产；速度恢复由真实播放器getter及GONE断言证明，不从瞬时截图推断。真实页面是CastActivity，不扩称VideoActivity/直播/所有引擎硬件验收。
- 数据精确保留：未clear/uninstall原包、未删库、未覆盖整份偏好。首次播放更新perf_exo_decoder_runtime_v1，已恢复原存在性和值（DATA_RESTORE equality=true）。首页启动后仅site_health与cache_mgmt_inventory_duration_ms诊断键更新；Config42行未增减，仅2条既有行time自动刷新，其余用户列和历史/订阅/收藏/追剧表逻辑内容未变。Lead从前后SQLite快照独立重算确认time-only两行与各表行数相同。shm/wal可有运行字节差异，不宣称全库/偏好字节相同。
- 诊断/时间差异已对照SiteHealthStore.java:27,42（home协议健康记录）、CacheCenter.java:52-60（scan耗时）、Config.java:473-477和BaseConfig.java:123-131（load成功update时间）归类为正常首页活动元数据，明确保留，不覆盖活动用户库。不是未路由缺陷。
- Lead独立ADB确认结束时主包HomeActivity前台，version560/5.6.0、lastUpdate2026-11-25 20:56:45，两个instrumentation identity保留，未push。原APK备份支持需要时install -r回退。
- 发现及处置闭环：旧测试编译错误→隔离构建本类不改无关测试；test签名冲突→独立包名不卸载；截图证据不足→强断言+定向复测；解码遥测漂移→精确恢复；首页诊断/时间漂移→源码确认并保留。无未解决的本任务代码阻塞发现；物理/OEM链路和未实测页面/引擎仍明确未验。

### Recovery anchor（设备验收完成、待本地闭环）
生产无改动；新增CustomKeyDownCecDeviceTest.java及本文件已由Lead审阅。设备10/10及加强页面1/1有原始证据，真实seek/1→3→1通过；用户数据与原测试包保护完成，正常诊断时间元数据按核实约定保留。一个brief run1/3，多次干净hand_back续行。唯一下一步：guard finish提交测试/记录并立即创建recovery/ISSUE-582-CEC-DEVICE标签，不重复构建测试、不推送。
