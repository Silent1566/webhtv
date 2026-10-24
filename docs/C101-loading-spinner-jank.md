# C101 — 详情页/播放页「加载中圆圈」卡顿修复

任务 ID：`C101`（C10x 已被 C10-beta-conflict-resolution / C10-binary-upstream-alignment / C100-beta-merge-review-dev3 占用，故取 C101）
分支：`dev2`　基线 HEAD：`dcf9828cba616f07ea7b21ebc4053b691522a208`
开始时间：2026-10-24（Asia/Shanghai）
状态：**实施中**

## Recovery anchor

- 目标：让「进入详情页/播放页时加载中动画（含播放器加载圈）」不再卡顿（转圈中途停住再恢复）。
- 验收：同一探针脚本 `scripts/perf/loading_jank_probe.sh`、debug_log 开/关两态各 3 次采样，进入卡片窗口 **Janky ≤20%、p99 ≤120ms、无 `Skipped ≥60 frames!`**；功能不变（详情渲染、内联自动播放、详情直放、换源/重匹配）；回归门禁全绿。
- 当前文件/符号：见 §5 分阶段。
- 已完成：探针脚本打通并采到基线；根因全部有证据（§3）。
- 未验证的工作区改动：无（代码尚未改）。
- 未决风险：设备是 interpret-only debug 构建，绝对帧时被放大（故用相对基线）；模拟器 uiautomator 不可用（已绕开）。
- 下一步（唯一）：实施 S1（两 flavor `VideoActivity` 覆写 `deferPlaybackServiceBinding()`）。

## 1. 问题陈述（用户可见现象）

用户报告：进入详情页与播放页时，加载中动画（包含播放器加载圈）都会卡顿，**转圈动画中途停住再恢复**，体验很差。

机制：加载动画是 `CircularProgressIndicator` / `ProgressBar` 的 indeterminate 动画，由 RenderThread/动画时钟驱动，本身不吃主线程。**一旦主线程在某一帧被长段 Java 工作占住，动画就停帧**——「停住再恢复」就是主线程单帧超时。

## 2. 复现与量化基线

设备：`emulator-5556`（SM-N9700 / marlin / Android 9 / SDK 28），安装的是**本仓库 mobile flavor debug 构建**（base.apk 203,989,727 字节，与 `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` 字节数一致）。

**重要导航事实**：mobile flavor 上点击首页卡片**直接打开 `VideoActivity`，不经过 `TmdbDetailActivity`**（`mCurrentFocus=...VideoActivity`）——详情壳、TMDB 详情加载、内嵌播放器全在同一个 Activity 内。因此探针按「一次点击、两个窗口」测量：`load-early`（tap+3s，圈在位）与 `load-full`（等到 `canSave=true`）。

探针：`scripts/perf/loading_jank_probe.sh`（`--tag/--runs/--serial/--debug-log on|off|keep/--launch-wait/--early-wait/--ready-timeout/--item-xy`），落盘 `.wh_diag/perf/<tag>/run<N>/`（gfxinfo、framestats、logcat、截屏、markers、summary）。

### 基线实测（2026-10-24 10:00，debug_log=off）

| run | total | janky | janky% | p99 | maxSkipped | davey |
|---|---|---|---|---|---|---|
| 1 | 172 | 99 | 57.56% | 650ms | 47 | 1 |
| 2 | 150 | 85 | 56.67% | 550ms | 55 | 1 |
| 3 | 151 | 43 | 28.48% | 450ms | 49 | 1 |

中位 **≈56.7%**，p99 450–650ms，均远超验收线（≤20% / ≤120ms）。
另一轮独立基线（`.wh_diag/perf/off_gfx.txt`）：Total 168 / Janky 79 (47.02%) / 99th 350ms / Missed Vsync 4 / Slow UI thread 4。logcat 另有 `Skipped 189/109/87/70/34 frames!` 与 `Davey! duration=723–1825ms`（`DrawStart→FrameCompleted` 跨 800–1400ms，而 `PerformTraversalsStart` 紧跟 Vsync → 卡在 traversal 内部）。

## 3. 根因（全部有直接证据）

### F1 详情页 onCreate/initView/initPage 同步段过重
`TmdbDetailActivity.initView`（主线程）顺序执行 `inflate(activity_tmdb_detail.xml)`（1590 行、78,955 字节，`getBinding()` 每次 inflate）→ `initPage()`（20+ setOnClickListener、4 个 setOnKeyListener、6 个 nextFocus、7 个 adapter 注册）→ `applyDetailTemplate()`（逐条 setLayoutParams/setPadding/setHeight/setRadius/setTextSize）。
调用图证据：`BaseActivity.onCreate` inclusive **99.71%**、`CustomRecyclerView.onMeasure` **99.46%**、`EpisodeAdapter.onCreateViewHolder` 93.10%。
XML 事实：`@+id/loading`（148dp×112dp，含 `ProgressBar indeterminate` + `loadingText`）**没有 android:visibility 属性 → 默认 VISIBLE**，即 inflate 完成瞬间圈就在屏上，而紧接着的主线程工作会立刻把它冻住。

### F2 结果应用整段塞进同一主线程 tick
`applyLoaded(...)`（`TmdbDetailActivity.java:2803-2833`）在**同一主线程 tick** 内连跑：`binding.loading.setVisibility(GONE)`（首行）→ `TmdbEpisodeSorter.sort` → `clearEpisodeRenderCaches` → `applyTmdbBundle` → `enrichVod` → `initHistory` → `bindPage()`（内含 bindBackdrop/Header/Meta/Ratings/Overview/Source/Flags/loadRelatedVideosForCurrentContext/bindTmdbSection/updateKeepState/updateFollowingState/requestDetailPlayFocus）→ `focusInlinePlayerPanel` → `maybeAutoPlayInline` → `loadTmdbMediaBlocks`/bindTmdbSection → 可能 `showTmdbMatchDialog`。
`applyTmdbResultNow(...)`（:2905-2927）同构（再叠加 `renderEpisodes()`）。
`runOnAliveUi(Runnable)`（:2750-2755）只是 `runOnUiThread`，**没有任何帧对齐/分片能力** → 整段落在单帧里，圈停帧。

### F3 播放服务绑定在主线程同步构造整个播放器
`PlaybackActivity.initView`（:1105）→ `bindPlaybackService()`（:608-616）→ `startService+bindService` → `PlaybackService.onCreate`（:140-161）全同步在主线程：`new PlayerManager(this)` → `buildEngine` → `ExoPlayerEngine.<init>` → `ExoUtil.buildPlayer` → `ExoPlayer.Builder.build()`（含 renderers/trackSelector/analytics），再 `new MediaLibrarySession.Builder(...).build()`、`setupNotification()`。
调用图证据：`PlaybackService.onCreate` inclusive **99.52%** → `PlayerManager.<init>` 96.45% → `PlayerManager.buildEngine` 96.77% → `ExoPlayerEngine.<init>` 98.00% → `ExoPlayerImpl.<init>` 98.84%。

### A1 诊断日志在调用线程做多轮正则清洗（仅在 debug_log 打开时）
`catvod` 是**仓库内 Gradle 子模块**（`settings.gradle:28-32`），`DebugLogStore/SpiderDebug/DiagnosticText/DiagnosticLogBuffer` 都是**被跟踪的源码**（可改）。
一次 `SpiderDebug.log(tag, msg, args)` = acceptsTag + String.format + `DiagnosticText.clean(message)` ×2 + `clean(tag)` ×1；`DiagnosticLogBuffer.offer` 再 clean(线程名) + 整串 UTF-8 计量。
`DiagnosticText.clean`（:27-49）**顺序跑 6 个 Pattern.matcher(value).replaceAll(...)**（HEADER→SECRET→BEARER→URI_TEXT→LOCAL_PATH→ADDRESS），短 ASCII 也全跑；`origins` 再跑一遍 URI_TEXT。
调用图（决定性）：`Choreographer.doFrame → ViewRootImpl traversal → SpiderDebug.log → DebugLogStore.add → DiagnosticLogBuffer.add → DiagnosticText.clean (83.33%) → Matcher.replaceAll (80%) → icu_60::RegexMatcher::MatchChunkAt`；主线程自占比第一 **4.39%**。全仓 `SpiderDebug.log(` 调用点 **1079 个**；[main] 线程 10 分钟约 **2900 条**诊断写入。
注：设备 prefs 里 debug_log 本次实测为 true（用户此前手动开启），是量级放大器；关掉后 5s 内日志不再增长（6KB/s → 0）。**但关日志只降量级不消除卡顿**（关后仍 47% janky）——F1/F2/F3 才是主因。

### A3 ProgressLayout 每次 CONTENT 都新建动画
`ProgressLayout.showView(View)`（:331-335）= setAlpha(0f); setVisibility(VISIBLE); `view.animate().alpha(1f).setDuration(100);`——每次 showContent() 都新建 ViewPropertyAnimator 并触发额外 invalidate/重绘。`hideView` = setVisibility(INVISIBLE)（无 animate().cancel()）。

### A4 ImageHeaderPolicy 每次调用重编译正则
`ImageHeaderPolicy.java:25-27`：`host.matches("img\\d+\\.doubanio\\.com")` 每次调用都重新编译 Pattern（列表/详情 item 绑定高频路径，调用图 `ImageHeaderPolicy.doubanImageReferer` 出现占比 80.00%）。

### 已排除（避免重复试做）
- `ThemeController.bindTheme` 在 leanback/mobile BaseActivity 各调两次：`ThemeBinder.bind` 在 `baseline.equals(active)` 时**直接 return**（未自定义主题零成本）；且 `ThemeBinderContractTest` 主动断言这两次调用存在，**不可删**。
- `PlaybackPerformanceSetting.ensureInitialized()` 每次串行 13 个 migrate：每个 migrate 首行都是 Prefers 读 → 稳态就是 13 次 SP 读，轻；调用频度高但非瓶颈（无自占比证据）。
- `ResUtil.getScreenWidth/Height` 无缓存、`History.findPlayback` 主线程 Room、`AppDatabase.allowMainThreadQueries()`：只有 inclusive 占比，缺自占比证据，**本轮不授权**。
- 首页 WebView JNI（CheckJNI::CallMethodV）ANR 栈属于 HomeActivity 路径，**不是详情/播放页原因**。
- `grep cost= .wh_diag/perf` 无命中 → 设备日志已被滚动覆盖，需重采。

### 度量设施现状
全仓 `grep Choreographer|FrameMetrics|getFrameMetrics|jank app/src/main/java` **零命中** → 项目没有任何掉帧度量设施；也没有 baseline profile（全 gradle 零命中）。设备 `Dexopt state: x86_64: [status=verify] [reason=boot]`（interpret-only，主线程 simpleperf 全是 art::interpreter::DoCall / Class::FindClassMethod / FindFieldFast）→ **绝对帧时被放大**，验收用相对基线。

## 4. 最佳实践调研（设计门禁）

| 来源 | 证据等级 | 支持的结论 | WebHTV 适用性 | 限制 |
|---|---|---|---|---|
| https://developer.android.com/topic/performance/jankstats （JankStats Library，访问 2026-10-24） | 官方文档 | 官方掉帧度量手段是 JankStats/FrameMetrics，可在生产环境采集每帧状态与卡顿原因 | 本项目**完全没有**掉帧度量设施；后续可引入 | 新增依赖，超出本次最小修复范围 → 列为后续建议 |
| https://developer.android.com/topic/performance/vitals/render （Slow rendering，访问 2026-10-24） | 官方文档 | slow rendering 以慢帧占比衡量，建议先定位主线程长任务 | 与本次 gfxinfo janky% + p99 口径一致 | **web_fetch 对 developer.android.com 只返回 ~100KB devsite 外壳 HTML，无法抽取正文**；结论取自 web_search 摘要 |
| https://developer.android.com/reference/androidx/media3/session/MediaSessionService （Media3 服务，访问 2026-10-24） | 官方文档（摘要） | 媒体会话服务创建是重活，官方推荐服务可后台创建、UI 不阻塞等待 | 本项目 PlaybackService.onCreate 全在主线程同步构造 PlayerManager+ExoPlayer | 文档正文未能直接抓取 |
| https://stackoverflow.com/questions/78378101/circularprogressindicator-shows-too-late-when-time-consuming-code-has-finished （访问 2026-10-24） | 社区实证 | CircularProgressIndicator 会因主线程耗时被推迟显示/停滞 | 与用户现象「转圈停住再恢复」完全对应 | 社区证据非权威，仅用于解释机制 |
| https://mintlify.wiki/AmolPardeshi99/android-performance-skills/anr-jank/view-system （访问 2026-10-24） | 社区整理 | view 系统卡顿主因：layout inflation、RecyclerView 绑定、onDraw | 与 F1/F2 证据一致 | 同上 |
| 仓库内 `docs/TMDB-DETAIL-PERF-01-tmdb-detail-load-render.md`（W5 §9.3） | 一手（本项目实测） | **主线程渲染去重（W5）已试做并回滚：中位 164→202ms 无改善** | 直接排除「去重」路线 | 同一设备类 |

### 方案对比

| 方案 | 正确性 | 兼容性 | 性能 | 风险 | 结论 |
|---|---|---|---|---|---|
| **no change** | — | — | 维持 47–57% janky | 无 | 不满足用户诉求 |
| **上游/官方原样**（JankStats + baseline profile + AsyncLayoutInflater 全面异步膨胀） | 高 | 需新增依赖与 profileinstaller | 长期收益最大但短期不可测 | 改动面大、需 ABI/构建配套、超出本问题范围 | **暂缓**，列后续建议 |
| **本仓最窄适配**（本次采纳） | 不改功能，仅让帧/降本 | 零新依赖、零 API 变更 | 直接命中 F1/F2/F3 + A1/A3/A4 | 每阶段可独立回滚 | **采纳** |

**决策**：采纳「本仓最窄适配」，按 S1→S5 分阶段，先做有 inclusive 证据的 F3/F2（让帧），再做 A1/A3/A4（降本）。**不引入新依赖、不改日志格式/保留策略/分类语义、不删调用点、不重构 TmdbDetailActivity 结构、不修 catvod 既有失败测试**（DebugLogStoreTest 引用已删的 trimLines/readTail，merge-base 与 origin/beta 上同样失败）。

**回滚**：每阶段一个独立 guard 会话 + 独立 commit + `recovery/<id>/<ts>` tag；回滚 = `git revert <该阶段 commit>`。S1 单独回滚只需删两处覆写。

## 5. 分阶段实施

### S1（P0）播放页延后绑定服务
- 两 flavor 的 `VideoActivity` 各覆写 `protected boolean deferPlaybackServiceBinding() { return true; }`。
- 基类已有闲置闸门：`PlaybackActivity.java:206`（定义，全 app/src 仅 3 处命中、**无任何子类覆写**）、`:1105`（判定）、`:618-630 bindPlaybackServiceAfterFirstFrame()`（addOnPreDrawListener → root.post）。
- 增加兜底：`bindPlaybackServiceAfterFirstFrame()` 里 `root.postDelayed(this::bindPlaybackServiceIfIdle, 500L)`，新增 `private void bindPlaybackServiceIfIdle() { if (!isFinishing() && !isDestroyed() && !bound) bindPlaybackService(); }`。
- **不得改** `bindPlaybackService()` 内 `if (bound || shouldRejectPlaybackConnection()) return;` 字面量与两个方法签名（`VideoActivityLayoutTest.java:1873` 用它们作 methodBody 锚点）。
- 安全性已核：service()/player()/isServiceReady() null 安全；isOwner() 在 mService==null 返回 false；onResume/onStop 有 mService != null 守卫；syncKeepScreenOn() 仍在 onServiceConnected 内。

### S2（P0）详情页结果应用按帧分片（TmdbDetailActivity.java）
- 新增 `ArrayDeque<Runnable> renderChunks` + `boolean renderChunkScheduled`（:472 字段区）与 enqueueRenderChunk/scheduleRenderChunk/runNextRenderChunk/clearRenderChunks（插在 runOnAliveUi/canTouchUi（:2756）之后，避开测试 methodBody 切片）。
- scheduleRenderChunk 用 `binding.getRoot().postOnAnimation(...)`（未 attach 回落 App.post）；runNextRenderChunk 先查 canTouchUi()，pollFirst()，try/catch 兜底并强制 loading GONE，队列非空续排。
- applyLoaded：保留同步段（早退分支、vod 赋值/sort/clearEpisodeRenderCaches/applyTmdbBundle/saveTmdbMatch/enrichVod/initHistory/history==null 早退），捕获 `final int generation = loadGeneration`，入队四片：①generation 校验 + bindPage() + applySourceOnlyActionVisibility()；②focusInlinePlayerPanel() + maybeAutoPlayInline()；③loadTmdbMediaBlocks 或 bindTmdbSection；④**首行才** binding.loading.setVisibility(GONE) + showTmdbMatchDialog + 原 apply loaded cost=%dms 日志。
- applyTmdbResultNow 同构分片。
- resetDetailState() 与 onDestroy() 首部各加 clearRenderChunks()。
- **不变量**：`applyLoaded(finalVod, initialBundle, new ArrayList<>(), finalError, false);` 五参调用字面量保持（`TmdbDetailSourcePayloadTest.java:18` 断言）。

### S3（P1）诊断降本（catvod）
- DiagnosticText：6 个 Pattern 各加必要条件预判（HEADER/SECRET 需含 : 或 =；URI_TEXT 需含 / 或 :；LOCAL_PATH 需含 /；ADDRESS 需含 . 或 :；BEARER 需含 b/B）；新增 `public static String sanitizeTag(String)`（无正则）。
- DiagnosticLogBuffer.offer：线程名 ThreadLocal 缓存；safeTag 有界 LinkedHashMap(128) 缓存。
- DebugLogStore 新增包内 `addCleaned(String, DiagnosticText.Clean, boolean)`；SpiderDebug.log(tag,msg,args) 改为清洗一次复用同一 Clean，safeTag 换 sanitizeTag。
- PlaybackDiskBufferStore.log（:73-79，无去重）比照 logQuery（:81-89，已有 resultMs+间隔去重）加间隔去重。
- 必须保持通过：DiagnosticTextTest 7 例（含 10 万字符 timeout 用例与 8 个 secret 抹除断言）、DiagnosticLogBufferTest 8 例、CrawlerLogCategoryTest、DebugEventLimiterTest。

### S4（P1）ProgressLayout 幂等
- showView 加 `if (view.getVisibility() == VISIBLE && view.getAlpha() == 1f) return;` + `view.animate().cancel()`；hideView 加 `view.animate().cancel()`。
- 语义不变（CONTENT 仍 100ms 淡入，其余仍 INVISIBLE）。
- 新增 `app/src/test/java/com/fongmi/android/tv/ui/custom/ProgressLayoutContractTest.java`（源码文本契约风格；mobile 侧无 Robolectric，行为型测试只能放 app/src/testLeanback）。

### S5（P2）ImageHeaderPolicy 正则预编译
- 改为 `private static final Pattern DOUBAN_IMAGE_HOST` + `DOUBAN_IMAGE_HOST.matcher(host).matches()`。

### S6（P2，门禁后才决定）
仅当批次一+二后详情页进入仍 Janky>40%，才做最窄 F1 收窄（首屏外区块 ViewStub **或** 最重子块 AsyncLayoutInflater），且先补自占比证据（`simpleperf record --app <pkg> --duration 20`，**禁用 -p**，实测 -p 恒 0 samples）。

## 6. 验证

一次性跑完、不重复：
- 探针：`MSYS_NO_PATHCONV=1 bash scripts/perf/loading_jank_probe.sh --tag <tag> --runs 3 --serial emulator-5556`（debug_log 开/关各一轮）。
- 编译门禁：`bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac`
- 单测：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest`
- S3 追加：`bash ./gradlew :catvod:compileDebugJavaWithJavac` + `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*DiagnosticTextRedactionTest'`。**`:catvod:testDebugUnitTest` 不可用**：`catvod/src/test/java/com/github/catvod/crawler/DebugLogStoreTest.java:22/:34` 仍引用已删除的 `DebugLogStore.trimLines/readTail`，catvod 测试源集整体无法编译（既有缺陷，本任务不修），S3 的契约测试因此改落在 app 侧可运行源集。
- 安装采样：`bash scripts/build_arm64_debug_install.sh --flavor mobile --abi arm64-v8a --serial emulator-5556`。
- leanback 复验 1 次：装 emulator-5558。

## 7. 明确不做

不加依赖（JankStats/profileinstaller/baseline profile 仅作后续建议）；不改日志格式/保留策略/分类/开关，不删 SpiderDebug.log 调用点；不修 catvod 既有失败测试；不重构 TmdbDetailActivity 结构；**不动 ThemeController.bindTheme 双次调用**（ThemeBinderContractTest 断言其存在）；除 S6 授权外不引入 AsyncLayoutInflater；不动 allowMainThreadQueries 与 History.findPlayback 主线程调用；不 push、不 stage .wh_diag/。

## 8. 假设与风险

- debug_log 此前为用户手动开启（设备实测 true，本轮已置 false，还原点 /data/local/tmp/prefs.bak）→ A1/S3 的收益只在开日志时体现；F1/F2/F3 与日志无关。
- 设备为 interpret-only debug 构建 → 绝对帧时被放大，验收以相对基线（同脚本、同设备、3 次采样）判定。
- applyTmdbResultNow 分片可能让剧集列表出现一帧「先空后满」；若不接受，把 chunk② 合并回①。
- S1 若与未知子类时序冲突，以「详情内联起播 + 直进播放页」两条路径各 3 次采样否决。

## 9. 实施记录

（每阶段完成后追加：改动文件、验证命令、结果、commit/tag）

- 2026-10-24 09:5x　探针 scripts/perf/loading_jank_probe.sh 打通（修复：$@ 占位符被替换、set -o pipefail 下 dumpsys|grep -q 的 SIGPIPE 141 假阴性、grep -c 双输出、debug_log sed 引号）。基线 baseline-off 3 次：57.56% / 56.67% / 28.48% janky，p99 450–650ms。

- 2026-10-24 13:5x　**S1 完成**（播放页延后绑定服务）。改动：`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`（`bindPlaybackServiceAfterFirstFrame()` 内加 `root.postDelayed(this::bindPlaybackServiceIfIdle, 500L)` 兜底 + 新增 `private void bindPlaybackServiceIfIdle()`）、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 与 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（各加 `@Override protected boolean deferPlaybackServiceBinding() { return true; }`）。验证：双 flavor `:app:compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` BUILD SUCCESSFUL（6m25s）；全量单测 5320 tests / 4 failed（4 个均为既有 CRLF 冤案）；设备 emulator-5556 实测 s1-off3 中位 janky 37.3%、Davey 0、maxSkipped 36（基线中位 56.7%）→ 保留。commit `f0976cdb2499e481c421fa3afd00b20b214ed361`，tag `recovery/C101-S1/20261024024932-f0976cdb2499`。
- 2026-10-24 03:5x　**S2 完成**（详情页结果应用按帧分片）。改动：仅 `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`（新增 `import java.util.ArrayDeque;`、字段 `renderChunks`/`renderChunkScheduled`、助手 `enqueueRenderChunk/scheduleRenderChunk/runNextRenderChunk/clearRenderChunks`，applyLoaded 切 4 片、applyTmdbResultNow 切 3 片，每片带 generation 校验；resetDetailState 与 onDestroy 首部 clearRenderChunks）。96 insertions / 25 deletions。验证：双 flavor compile SUCCESSFUL；全量单测与 S1 同一批 4 failed（无新增）。设备实测 3 次：45.45% / 27.27% / 77.87% janky，p99 750–850ms（仍高于 ≤120ms 验收线，未达门禁）。commit `442ee30e9ac3531581d288cc18e521e7d7128478`，tag `recovery/C101-S2/20261024035727-442ee30e9ac3`。
- 2026-10-24 13:0x–14:0x　**S3 完成**（诊断降本）。commit `800945de7c3e83373ca6746532ff5e32e04529b0`（7 files changed, 276 insertions(+), 26 deletions(-)），tag `recovery/C101-S3/20261024060418-800945de7c3e`。改动 5 个源文件：`catvod/src/main/java/com/github/catvod/crawler/diagnostics/DiagnosticText.java`（6 个 Pattern 前置必要条件 gate + `escape()` 先扫描决定是否重建 + 新增 `public static String sanitizeTag(String)`）、`catvod/src/main/java/com/github/catvod/crawler/diagnostics/DiagnosticLogBuffer.java`（`THREAD_LABEL` ThreadLocal 缓存线程标签、新增 `public void addCleaned(String, DiagnosticText.Clean, String raw, boolean)`、`add` 改写为调用它）、`catvod/src/main/java/com/github/catvod/crawler/DebugLogStore.java`（新增包内 `addCleaned`）、`catvod/src/main/java/com/github/catvod/crawler/SpiderDebug.java`（`log` 全路径清洗一次复用，`safeTag` 换 `sanitizeTag`）、`app/src/main/java/com/fongmi/android/tv/player/cache/PlaybackDiskBufferStore.java`（`log` 加 `RANGE_LOG_INTERVAL_MS=2000` 同 resultMs 节流）。共 100 insertions / 25 deletions，另有新增测试 `app/src/test/java/com/fongmi/android/tv/diagnostics/DiagnosticTextRedactionTest.java`（9 例）。
  - 行为等价证据（仓库外 `/f/Workspace/webtv2/whdiag/s3/` harness）：对设备真实诊断日志 4 段共 15,182 行逐行比较 `clean().text()/truncated()` 与 `origins()` → `cleanDiffs=0 originsDiffs=0 truncationDiffs=0`；26 个合成对抗用例同样全 0；`sanitizeTag` 414 例 `diffs=0`。
  - 收益证据（同 harness 按真实调用配方「一次 `SpiderDebug.log(tag,msg,args)`」计时，15,428 行）：原实现 38.9–41.5s（2,521–2,692 us/行）→ 新实现 12.0–14.7s（778–953 us/行），即 2.6–3.2x 加速、每行省 ~1.6–1.8ms 主线程。正则应用次数从恒定 6 次/行降到按字符类 gate（语料实测 84,507 vs 120,366 次应用），真实命中仅 62/1408/1672/1330 次。
  - 验证：`:catvod:compileDebugJavaWithJavac` BUILD SUCCESSFUL（38s）；`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*DiagnosticTextRedactionTest'` BUILD SUCCESSFUL（9 tests, 0 failed）；`:app:testMobileArm64_v8aDebugUnitTest` 全量 5329 tests / 4 failed（全是既有 CRLF 冤案：ReaderPlaybackRoutingSourceTest ×2、TmdbSourceOnlyInteractionTest、WebThemeTokenSourceTest，均与 S3 无关）；`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 4546 tests / 6 failed = mobile 那 4 个 + `SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout` 与 `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`；后两者同样是工作区 CRLF 冤案（`git show <任意版本>` 里该字面量含 LF 且 `grep -Pzo` 能命中，全仓工作区被 CRLF 转换，测试硬编码 LF）→ 6 个失败全部与 S3 无关。
  - 环境备注：本轮误触发了 Python 安装管理器，它把 3.15.1 装进了仓库根 `Python/`（未跟踪），已 `rm -rf` 清除；后续需要脚本时改用 JDK 21 的 `jshell`/`javac` 或仓库外目录。
- 2026-10-24 14:1x–14:2x　**S4 完成**（ProgressLayout 幂等切换）。改动：`app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java` 的 `showView(View)` 加「已 VISIBLE 且 alpha==1f 直接返回」幂等守卫 + `view.animate().cancel()` 后再 `setAlpha(0f)/setVisibility(VISIBLE)/animate().alpha(1f).setDuration(100)`；`hideView(View)` 加「已 INVISIBLE 直接返回」+ `cancel()` 后再 `setVisibility(INVISIBLE)`。淡入本身保留（删掉是另一种回退）。新增测试 `app/src/test/java/com/fongmi/android/tv/ui/custom/ProgressLayoutContractTest.java`（4 例，源码契约风格；helper 先把 CRLF 归一化再切片，避开本仓既有行尾冤案）。
  - 验证：`:app:testMobileArm64_v8aDebugUnitTest --tests '*ProgressLayoutContractTest'` BUILD SUCCESSFUL（4 tests, 0 failed，test-results XML `tests="4" failures="0" errors="0"`）；双 flavor compile SUCCESSFUL；`:app:testMobileArm64_v8aDebugUnitTest` 全量 **5333 tests / 4 failed**、`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 **4550 tests / 6 failed**——与 S3 时同一批既有 CRLF 冤案（mobile: ReaderPlaybackRoutingSourceTest ×2、TmdbSourceOnlyInteractionTest、WebThemeTokenSourceTest；leanback 再加 SearchResultDownFocusTest、NativeEnhancedPlaybackStyleFocusTest），无新增失败。
  - 收益定位：进度圈本身由 RenderThread 驱动，切换时省掉一次 `setAlpha(0)`（会请求一次绘制）+ 避免新建 ViewPropertyAnimator，等于把加载窗口内的无效帧请求去掉；本项单独不足以跨过 ≤120ms p99 门禁，与 F1/F2 是叠加关系。
- 2026-10-24 14:3x　**S5 完成**（豆瓣图片 host 正则预编译）。改动：`app/src/main/java/com/fongmi/android/tv/utils/ImageHeaderPolicy.java` 新增 `private static final Pattern DOUBAN_IMAGE_HOST = Pattern.compile("img\\d+\\.doubanio\\.com");`，`isDoubanImageHost` 由 `host.matches(...)`（每次调用重新编译正则）改为 `DOUBAN_IMAGE_HOST.matcher(host).matches()`；`app/src/test/java/com/fongmi/android/tv/utils/ImageHeaderPolicyTest.java` 增 2 例（全名匹配边界：`imgx` 不匹配、`img1.doubanio.com.evil.test` 不匹配、`x.img1.doubanio.com` 不匹配、null/空/非 URL 返回空；以及 100 次重复查询稳定）。
  - 验证：`:app:testMobileArm64_v8aDebugUnitTest --tests '*ImageHeaderPolicyTest'` BUILD SUCCESSFUL（6 tests, 0 failed，test-results XML `tests="6" failures="0" errors="0"`）；模块编译随该任务通过。
  - 收益定位：本项是「主线程固定小开销」类的清理，单次省的是正则编译（数十微秒级），只在图片列表反复走 referer 判定时累积；与 F1/F2/S1 属叠加关系，不单独作为验收判据。

## 10. 验收复测与归因结论（2026-10-24 16:0x–16:3x）

### 10.1 验收数据（emulator-5556，mobile debug ≥196MB，`debug_log=false`，探针 `scripts/perf/loading_jank_probe.sh --runs 3`）

| 路径 | tag | Total | Janky | janky% | p50 | p95 | p99 | Slow UI thread | High input latency | Missed Vsync |
|---|---|---|---|---|---|---|---|---|---|---|
| 详情页 TmdbDetailActivity | s5-off | 615/596/581 | 364/253/373 | 59.19/42.45/64.20 | 18/10/18ms | 23/22/24ms | **53/48/38ms** | 6/5/2 | 579/444/543 | 3/3/3 |
| 播放页 VideoActivity | s5-play2 | 100/131/119 | 36/81/71 | 36.00/61.83/59.66 | 14/17/17ms | 200/150/-ms | **750/500/500ms** | 1/3/- | 99/118/- | 1/3/3 |
| 基线（改动前） | baseline-off | 120/143/145 | 63/81/40 | 52.50/56.64/27.59 | 17/18/7ms | - | **650/550/450ms** | 0–12 | 110/78/122 | - |

- **p99 收益明确**：详情页从基线 450–650ms 降到 **38–53ms**（已优于 ≤120ms 门禁）；播放页仍为 500–750ms。
- 详情页 `Slow UI thread` 只剩 2–6 帧，`Slow issue draw commands` 7–15 帧；播放页 `Slow UI thread` 1–3 帧。**主线程长任务（用户抱怨的「转圈中途停住」）在详情页已实质消除。**

### 10.2 janky% 不能作为本机判据（模拟器底噪实测）

- 空载对照：停在首页只做无点击滑动 12s → `Total 307 / Janky 137 = 44.63%`，p50 10ms、p99 89ms，`High input latency 307`（等于总帧数）、`Slow UI thread 0`、`Missed Vsync 0`、`Frame deadline missed 0`。
- 即 `emulator-5556` 的 input 注入延迟约 18ms/帧 > 16.7ms 帧预算，**任何界面都有 ~44.6% 的 janky 地板**；因此 §2 里「Janky ≤20%」在这台模拟器上物理不可达，本轮判定改用 **p99 + framestats 主线程耗时**。
- 该项判定修正也解释了为什么 s5-off 的 janky%（42–64%）远高于 p99 表现：计数器被 `High input latency` 撑起，与主线程无关。

### 10.3 framestats 三分解（真正的「圈停住」证据）

解析 `gfxinfo framestats` 16 列，定义 `starve = Vsync − IntendedVsync`（主线程没按时申请到 vsync = 被长任务占住）、`trav = DrawStart − PerformTraversalsStart`（主线程真实 measure/layout/draw）：

| 路径 | run | starve>100ms 帧数 | 最大 starve | trav>40ms 帧数 | 最大 trav |
|---|---|---|---|---|---|
| 播放页 baseline | 1/2/3 | 7/6/5 | 916.7/816.7/783.3ms | 3/3/3 | 296.7/295.3/243.9ms |
| 播放页 s5-play2 | 1/2/3 | 9/5/6 | 750/700/616.7ms | 3/6/3 | 238.7/243.7/236.5ms |
| 详情页 s5-off | 1/2/3 | **2/2/2** | 683.3/616.7/516.7ms | 1/1/0 | 107.4/112.8/2.2ms |

- **详情页已把长帧压到每轮只 2 次 starve、trav 基本 <115ms**；播放页仍稳定出现 5–9 次 starve>100ms 与 3–6 帧 trav>40ms。
- 播放页长帧 logcat 精确对位（s5-play2/run2）：Davy `duration=859ms` 的帧其 `IntendedVsync=8344.349s → Vsync=8345.049s`（starve 700ms）、`trav=147.5ms`，落在 `15:15:48.983 VideoActivity: onStart: calling mClock.stop().start()` → `15:15:49.046 Skipped 42 frames!` 之间；其余长帧（trav 47.2/243.7/113.9/43.3/45.7ms）落在 `49.526 ExoPlayerImpl: Init` → `49.629 vulkan searching for layers` → `49.722 MediaSessionImpl: Init` → `49.826/49.867 MediaController: Init` → `49.850 Skipped 31 frames!` 这一段，即 **ExoPlayer/MediaSession/MediaController 初始化 + native bridge 加载 `libexo_dovi_renderer.so` 全压在首帧窗口的主线程**。
- 排除项复核：`ExoDv5Native.java:26 private static final LoadResult LOAD_RESULT = loadLibrary();` 已是类初始化期一次性执行（logcat 里 `libnb: enter native_bridge2_getTrampoline ... nativeProbeCapabilities` 连打 7 行是 native bridge 每个 trampoline 两行的固有形态，不是重复加载）；`setContextWall` 切换背景与图片完成相隔 3ms（缓存命中）→ **两者均非热点，勿再怀疑**。

### 10.4 结论

1. **S1–S5 达成目标主体**：详情页「转圈中途停住」的长任务已从每轮 7 帧级降到 2 次 starve、trav<115ms，p99 450–650ms → 38–53ms。
2. **播放页仍有残留**：首帧窗口内 ExoPlayer/MediaSession/MediaController/vulkan/native bridge 的主线程初始化仍造成 3–6 帧 trav 40–244ms、5–9 次 starve>100ms，p99 500–750ms。
3. **janky% 门禁作废**（见 10.2），后续验收统一看 p99 与 starve/trav。

### 10.5 S6 决策：本轮不做

§5 的 S6 触发条件是「详情页进入仍 Janky>40% 且需补自占比证据」。按 10.2 修正判据后，详情页的真实主线程指标（trav>40ms 仅 1/1/0 帧、p99 38–53ms）已说明 **F1（XML inflate 过重）不是当前瓶颈**，做 ViewStub/AsyncLayoutInflater 收窄属无证据优化，故不启动。若后续仍要压播放页 p99，建议的下一步（需另行授权、独立任务 ID）是「把 `PlaybackService` 的 ExoPlayer/MediaSession 构建从首帧窗口挪到首帧之后」，而不是再收紧详情页 XML。

### 10.6 期间发现但不属于本任务的范围外缺陷（记录，不修）

- `catvod/src/test/java/com/github/catvod/crawler/DebugLogStoreTest.java:22/:34` 仍引用已删除的 `DebugLogStore.trimLines/readTail` → `:catvod:testDebugUnitTest` 整体不可编译（既有缺陷）。
- 工作区 `core.autocrlf=true` 导致 6 个源码契约测试硬编码 LF 断言恒失败（mobile 4、leanback +2），与本任务无关。
- `emulator-5556` 上 `simpleperf record -p <pid>` 恒 0 samples、`-e cpu-cycles` 与 dwarf callchain 均不支持、`atrace` 无 trace folder、`kill -3` 不产 `/data/anr/` → 该模拟器无法做栈级归因，播放页归因改用 logcat 时间线 + framestats。

## 11. S7（播放页首帧窗口内的固定开销）实施记录（2026-10-24 20:0x 起）

在 §10.4 的结论上继续追播放页残留。以下四项全部来自设备实测证据，不含推测性优化。

### 11.1 证据来源

- AOT 后 + `debug_log=false` 的 `s6-aot-off` 采样（emulator-5556）：load-early janky 42.86% / 36.90%，p99 1100 / 950ms；framestats 显示长帧几乎只来自 `starve`（p99 1000 / 350ms）与偶发 `trav` 383 / 434ms，`after`/`sync`/`draw` 三相均 <50ms。
- `trav=383ms` 那一帧（PT 19:44:25.719 → DS 19:44:26.102）的 logcat 覆盖 `19:44:25.523 TV-Shell: Shell command 'getprop ro.serialno'`、`getEpisodeTitles` 三次重算与 `applyResizeMode before/after` 成对刷屏。
- 一次进入播放页实测重复：`applyResizeMode before/after` 16 / 14 组、`MPV_SIZE: video layout new` 17 / 21 行；`exo-audio` 日志步骤独占 354–757ms（`19:24:45.566`→`:46.852` 中最后一段）。
- 进度 tick（`Clock.doJob` 每秒 → `PlaybackEventCollector.onProgress` → `PlaybackRecord.clientKey()` → `Device.get()`）每次都会重扫 `NetworkInterface.getByName(eth0/wlan0)`、`Settings.Secure`、`Build` 字段。

### 11.2 S7-1 `applyResizeMode` 幂等（`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`）

- `applyResizeModeNow` 先算完 `effectiveResizeMode` 与 `contentAspectRatio`，再用 `isResizeModeApplied(...)` 比对「上一次真正应用过的参数」（模式、有效模式、目标比例、内容比例的 `Float.floatToIntBits`、stretch）；全等则直接返回，不再打日志、不再 `setVideoAspect`、不再请求 layout。
- 新增字段 `resizeApplied / resizeAppliedMode / resizeAppliedEffectiveMode / resizeAppliedTargetAspect / resizeAppliedContentAspect / resizeAppliedStretch` 与 `invalidateAppliedResizeMode()`；后者在 `resetVideoSurfaceForDecoderSwitch()`（`render = -1;` 之后）与 `attachSurface(...)`（`setPlayer(...)` 之后）调用，保证换了 surface 一定重算。
- `applyResizeMode(int)` 的 `view.post` 二次调用保留（首帧尺寸可能尚未确定），但第二次通常命中守卫直接返回。
- 新增 `app/src/test/java/com/fongmi/android/tv/ui/activity/PlaybackActivityResizeModeIdempotenceTest.java`（4 例）：守卫先于一切副作用、六个比较项齐全、两类重绑都会失效缓存、二次 post 仍在且不自递归。

### 11.3 S7-2 移除音频能力探测（`app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`）

- `buildAudioSink(...)` 的诊断块原本调用 `AudioCapabilities.getCapabilities(...)` 取通道数/AC3 支持，该路径会在主线程同步创建并释放 `AudioTrack`，设备实测这一行日志独占 354–757ms。
- 改为只记录本方法已算好的配置量：`SpiderDebug.log("exo-audio", "configured passthrough=%s floatOutput=%s playbackParams=%s offloadPolicy=%s", ...)`，不再触碰音频硬件；`AudioCapabilities` 的 import 一并删除。
- offload 策略装配顺序不变（`directPolicy.setAudioPassthroughEnabled(passthrough)` → `AudioTrackAudioOutputProvider.Builder`）。
- 新增 `app/src/test/java/com/fongmi/android/tv/player/exo/ExoUtilAudioCapabilityLogTest.java`（3 例）：日志不再触发硬件探测、日志本身与 `isEnabled()` 门禁保留、装配顺序不变、未用 import 不回流。

### 11.4 S7-3 设备身份进程内缓存（`app/src/main/java/com/fongmi/android/tv/bean/Device.java`、`playback/PlaybackRecord.java`）

- 稳定身份（serial / eth MAC / wlan MAC / Android ID / 型号 / 设备形态）在进程生命周期内不变，改为 `ensureIdentity()` 双检锁一次性探测，字段 `identitySerial/identityEth/identityWlan/identityUuid/identityName/identityType` 缓存。
- `Device.get()` 仍返回新的可变实体（调用方会 setter 后 `save()`），`setTime(App.time())` 与 `setIp(Server.get().getAddress())` 继续每次实时取——地址会随网络变化。
- 新增 `Device.identityUuid()`（空值归一为空串）；播放中的每秒进度 tick 走 `PlaybackRecord.clientKey()` → `Device.identityUuid()`，不再构造整个 `Device`、不再枚举网卡。
- 新增 `app/src/test/java/com/fongmi/android/tv/bean/DeviceIdentityCacheTest.java`（4 例）：`get()` 不再逐次探测、双检锁形态、热调用点用廉价访问器、返回值新鲜度约束仍成立。

### 11.5 S7-4 mobile 播放页日志与 intent 解析降本（`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`）

- `getEpisodeTitles()` 结果缓存（`episodeTitles/episodeTitlesReady`），intent extras 由 `episodeTitleValues()` 只反序列化一次；`onNewIntent` 在 `getIntent().putExtras(intent);` 之后调用 `invalidateEpisodeTitlesCache()` 失效两层缓存。方法名与调用形态未变（多处源码契约测试依赖）。
- 去掉三处每次调用都打的 `Log.d`（`集数标题数量`、`getEpisodeTitles - values`、`isIntentTmdbPlayback`），`applyTmdbEpisodeTitles` 不再逐集打印，改为一行 `applyTmdbEpisodeTitles applied=N/M`；`Setting.isTmdbEpisodeFileSize()` 提到循环外。
- leanback 侧同名方法本来就没有这些日志，本次不动（保持两侧行为一致，仅补齐 mobile 的历史遗留）。
- 新增 `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityEpisodeTitlesCacheTest.java`（4 例）：extras 只读一次、换条目必须失效、不再逐集打日志、对外方法名不变。

### 11.6 验证

- 定向：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*PlaybackActivityResizeModeIdempotenceTest' --tests '*ExoUtilAudioCapabilityLogTest' --tests '*DeviceIdentityCacheTest' --tests '*VideoActivityEpisodeTitlesCacheTest'` → `BUILD SUCCESSFUL`（15 tests, 0 failed）。
- 全量：`:app:testMobileArm64_v8aDebugUnitTest` **5350 tests / 4 failed / 2 skipped**；`:app:testLeanbackArm64_v8aDebugUnitTest` **4563 tests / 6 failed / 2 skipped**。失败集合与 §10.6 记录的既有 CRLF 冤案完全一致（mobile：`ReaderPlaybackRoutingSourceTest` ×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`；leanback 再加 `SearchResultDownFocusTest`、`NativeEnhancedPlaybackStyleFocusTest`），无新增。
- 双 flavor `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` 均随上述任务通过。
