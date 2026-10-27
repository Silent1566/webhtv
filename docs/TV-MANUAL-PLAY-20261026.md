# TV-MANUAL-PLAY-20261026 关闭「点播自动播放」后的手动播放交互修复

## 目标

TV 端关闭【点播自动播放】后进入手动播放模式：进入播放页不再把焦点交给左上角预览窗口，
预览位显示影片封面；点选集直接全屏起播，省掉「先全屏、再按一次 OK」的多余步骤。

## 关键更正：目标对象

初版方案误把目标定位到 `TmdbDetailActivity`（沉浸融合内联面板）。经用户指正后确认真实对象是
`app/src/leanback/res/layout/activity_video.xml` 的 `@+id/video`（400dp×225dp、margin 24dp、
左上角、`background="@color/black"`、`focusable="true"`、内含 `exo`），承载于 `VideoActivity`，
覆盖 **原生增强 / 影视原生 / 炫彩详情的播放页**（`TmdbDetailActivity.playDefaultPlayback()` →
`VideoActivity.startDirectTmdb(...)`）。用户确认 Q1=A（所有 VideoActivity 场景）、Q2=A（选集直接全屏播放）。

## 根因

| 现象 | 根因 |
|---|---|
| 焦点落在预览窗口 | `mBinding.video.requestFocus()` 有 9 处无条件调用（2258/2597/2777/2865/2889/4487/5570/5275 等），与自动播放开关无关；`checkCast()` → `showInitialPreview()` 末尾即其一 |
| 要先全屏再按 OK 才播 | `video` 点击监听是 `onVideo()`：`if (!isFullscreen()) enterFullscreen();`，只切布局不起播；全屏后 OK 才走 `onKeyCenter()` → `player().isEmpty() ? onRefresh()` |
| 选集不直接播放 | `onItemClick(Episode)` 首行 `if (shouldEnterFullscreen(item)) return;`，而 `isPlayingContent(episode)` 只对「已选中」集为真；点其它集走 `selectEpisode` → `onRefresh` → `startPlayer`，此时 `shouldAutoPlay()==false`，prepare 了但不播 |
| 黑框而非封面 | `loadArtwork` 把封面喂给 `exo.setDefaultArtwork(...)`，但 `PlayerView` 仅在无视频帧时显示；`video` 的 `background` 是硬编码黑 |

自动播放开关作用点：`VideoActivity extends PlaybackActivity`，
`PlaybackActivity.shouldAutoPlay()` 返回 `PlayerSetting.isAutoPlay()`，作为 `playWhenReady` 传入
`player().parse(...)` / `player().start(...)` —— 关掉后取流照做、只是不播。

## 实现

### 1. 新增「手动播放模式」语义

```java
private boolean isManualPlaybackMode() {
    return Util.isLeanback() && !PlayerSetting.isAutoPlay();
}
```

### 2. 初始焦点分流（含「选集未就绪」挂起）

`requestInitialPlaybackFocus()` / `focusInitialEpisode()` / `flushPendingManualEpisodeFocus()`：
详情刚绑定时选集适配器可能为空，此时记下 `mPendingManualEpisodeFocus`，等
`setEpisodeAdapter` 两条分支（普通路径与 `setUpstreamNativeEpisodeItems`）末尾 flush，
避免在选集为空的那一刻误回退到预览窗口。替换点：`setDetail`、`revealShellWhileTmdbLoads`、
`revealTmdbDetail`、`showInitialPreview`，每处保留原分支作为非手动模式路径。

### 3. 选集直接全屏起播（含「已选中集」分支）

```java
if (shouldEnterFullscreen(item)) {
    // 已选中集：shouldEnterFullscreen 只负责进全屏，自动播放关闭时需补一次显式起播
    if (isManualPlaybackMode() && !player().isPlaying()) { ...; onPlay(); }
    return;
}
if (isManualPlaybackMode()) { mExplicitPlaybackRequest = true; mManualPlaybackStarted = true; }
selectEpisode(item, true);
if (isManualPlaybackMode() && !isFullscreen()) enterFullscreen();
```

`shouldAutoPlay()` 覆盖为**一次性消费**：

```java
@Override
protected boolean shouldAutoPlay() {
    if (mExplicitPlaybackRequest) { mExplicitPlaybackRequest = false; return true; }
    return super.shouldAutoPlay();
}
```

**关键修正（两轮）**：
1. 初版在 `selectEpisode` 之后又调一次 `onRefresh()`，而 `selectEpisode` 内部已调 —— 重复取流，已改为复用那一次。
2. 初版用 `try/finally` 立即复位标志，但起播是**异步**的（取流结果经 LiveData 回来才 `startPlayer`），
   标志早已变回 false。已改为在 `shouldAutoPlay()` 里一次性消费。`onError` 里清理该标志防止污染后续取流。

### 4. 预览位封面层

`activity_video.xml` 在 `exo` 之后、`audioStage` 之前插入 `@+id/videoCover`（`focusable="false"`、
`clickable="false"`、默认 `gone`）。`updateVideoCover()` 判据用 **`!mManualPlaybackStarted`** 而非
`player().isEmpty()`：手动模式下详情页会先 prepare（播放器非空但没播），用 `isEmpty` 会误判成该隐藏。

## 验证（模拟器 127.0.0.1:5561，HD1910 / x86_64 + libnb.so ARM 转译）

设置：`detail_open_mode=2`（影视原生）、`player_auto_play=false`。

| 项 | 证据 |
|---|---|
| 待播态不自动播放 | `state=2`（PAUSED）、`speed=0.0`、`position=0` |
| 封面层生效 | `app:id/videoCover` 可见性 `V.ED.....`（VISIBLE），尺寸 0,0-800,450 |
| 焦点不在预览框 | 点集前焦点落在下方选集卡片（UI dump 确认） |
| 点选集直接起播 | `state=3`（PLAYING）、`speed=1.0`、position 12093→340212 持续推进 |
| 直接全屏 | `app:id/video` 由 800x450 变为 `0,0-1920,1080` |
| 起播后封面自动隐藏 | `videoCover` 变为 `G.ED.....`（GONE） |
| 已选中集分支 | 同样由暂停转为 `state=3` 全屏播放 |

诊断期内曾打印 `MANUALDBG` 日志实证：`onItemClick episode=第1集 manual=true shouldEnter=false`、
`shouldAutoPlay explicit=true`；**诊断日志已全部移除**，最终产物中 `MANUALDBG` 命中数为 0。

单测（最终）：
- `VideoActivityLayoutTest` 157 项 / 0 失败（含新增 2 项）
- `PlayerPlaybackRegressionSourceTest` 15 / 0、`PlayerControlFocusIntegrationTest` 11 / 0
- `TmdbDetailActivityLayoutTest` 130 / 0、`DetailModeControllerTest` 13 / 0

**负向对照**：把 `isManualPlaybackMode()` 临时改为 `return false` →
新增用例如实失败，证明断言有效（已还原）。

**非本次引入的观察项**：验证中遇到一次 ANR（`executing service PlaybackService`）。
ANR 主线程堆栈为 `CustomSeekView.updateProgress` → `MediaController.isPlaying()`（IPC），
我的方法在堆栈中命中数为 0，属模拟器 ARM 转译性能问题，与本次改动无关。

## 环境说明（重要）

本会话 `ops_bash`（bash.exe）在 `app/`、`docs/`、`.codex/`、`G:\GradleCache` 下均被沙箱 ACL 拒绝，
而 `pwsh` 与 `read`/`write`/`edit` 不受限 —— 编译、测试、adb、git 全部改走 `pwsh` 完成。
`task_guard.sh` 因依赖 bash 服务无法运行，改用直接 git 提交 + annotated tag 做等价收尾。

另：F 盘曾剩余 0GB 导致 `磁盘空间不足` 编译失败，清理 `app/build/intermediates`、`app/build/tmp` 后恢复。

## 回归修复：切线路后集数分段按钮 LEFT/RIGHT 失效

用户在手动播放功能上线后报告（m01153、m01160）：「集数分组按钮向左和向下无法选到对应的按钮」，
「播放一段时候后能正常但是刚切换线路后就不行」。

### 现场与取证

- 设备：模拟器 `emulator-5562`（HD1910 / 1920x1080；5561、5560、5554 在验证期间陆续 `mCurrentFocus=null` 死机）。
- 视图树取证：`adb shell dumpsys activity top`，按缩进维护祖先栈解析 flags
  （第 3 组首字符为可见性，第 4 组含 `F` 才是 hasFocus，第 5 组为 bounds），
  脚本 `out/fp.ps1` / `out/st.ps1`。`uiautomator dump` 在该模拟器上经常返回空 focused 节点，不可靠。
- 稳定复现序列（seq34 / seq36）：进入详情 → UP 到分段行 → UP 到线路行 → RIGHT → OK 切线路 → DOWN 回分段行
  → LEFT/LEFT/RIGHT 全部纹丝不动（分段行已重建：bounds 宽度由 365 变 383）。
- 关键教训：截图里白色焦点环会滞后/残留，不能据此判断焦点；必须用 dumpsys。
  设备启动慢会让整轮按键变成空操作，脚本必须有「等应用拿到窗口焦点」的门禁。

### 根因（探针日志实证）

不是 nextFocus 声明错误，也不是按键没到达分段行。`out/seq35.log` 显示 LEFT 的 ACTION_DOWN 已把选中位改到 1，
但 25~35ms 后又被复位。`selectEpisodeSegmentPosition` 的调用栈有三条来源、交替约 20Hz：

1. `setUpstreamNativeEpisodeItems → setEpisodeAdapter → onItemClick(Flag) → FlagSelectionListener.run`（线路网格布局驱动）；
2. `selectEpisodeSegment → onSegmentFocus → ArrayAdapter 的 text OnFocusChangeListener`（分段行子项获焦驱动）；
3. `selectEpisodeSegment → onChildViewHolderSelected`（分段行 BaseGridView 的 child-selected 监听，注册在 `setRecyclerView`）。

即：线路网格布局 → 重建选集 → 分段行 `setSelectedPosition(2)`；分段行布局时 `BaseGridView.focusToViewInLayout`
把焦点交回选中子项 2 → 触发 `onSegmentFocus(2)` → `selectEpisodeSegment` → `showEpisodeSegment` →
`addAll`(`notifyDataSetChanged`) → 整页再布局 → 回到第一步。刚用左右键移动的选中位在这个自激循环里被反复弹回。

### 补丁（app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java）

1. `showEpisodeSegment`：内容相同就直接返回（`isSegmentDisplayed(items)`），切断无意义重建。
2. `selectEpisodeSegment`：非请求焦点且分段未变时 no-op（`isSegmentContentDisplayed(position)`）。
3. `selectEpisodeSegmentPosition`：用户正把焦点放在分段行上时，焦点位是权威，重建回调不得改写选中位。
4. 分段行左右键改为显式接管：`onArrayKey` 的 LEFT/RIGHT 走 `moveArraySelection(±1)`，
   以真实焦点位（`focusedArrayPosition()`）为准，并用一次性 `OnGlobalLayoutListener` 在布局结束后复核补偿。
5. 集数网格的焦点校正从「只等一帧」改为持久监听（`mEpisodeGridFocusTarget`）：
   异步取流/图片加载后的第二次布局会把焦点留给预览框，只等一帧会漏。
6. 补齐 `episodeHeader` 整条 GONE 时的可达性判断：`isEpisodeFocusTarget` 改用 `isEffectivelyVisible`
   （逐级检查自身与全部祖先），否则 `findFocusUp/Down` 会把已隐藏的表头工具当成有效邻居。
7. 手动模式焦点安全网：系统在退出触摸模式（`leaveTouchMode → restoreDefaultFocus`）或 RecyclerView 回收
   持有焦点的子项时会自行把焦点交回最上方的预览框，声明式 nextFocus 覆盖不到，故在根视图焦点变化时把焦点交回当前选集。
8. `tryStartFastTmdbPlayback`、`resetDetailForNewIntent` 两处无守卫的 `mBinding.video.requestFocus()` 改为
   `requestInitialPlaybackFocus()`，换条目/炫彩详情直放路径不再把焦点拽回预览框。
9. `onDestroy` 注销上述监听。

### 验证（模拟器 emulator-5562）

设置：`detail_open_mode=2`（影视原生）、`player_auto_play=false`。

| 项 | 证据 |
|---|---|
| 待播不自动播、焦点在选集 | `state=2`（PAUSED）`speed=0.0`；焦点 `episodeGrid > duration > text` |
| 预览位显示封面 | `app:id/videoCover` VISIBLE |
| 点选集直接全屏起播 | 按 OK 后 `state=3` `speed=1.0`，position 258560 → 264562 递增 |
| 起播后直接全屏、封面隐藏 | `app:id/video` bounds 变 `0,0-1920,1080`，`videoCover` 变 GONE |
| 切线路后分段行左右可用 | LEFT 272,0-383 → 176,0-256 → 48,0-160；RIGHT 逐格回退 |
| 切线路后分段行向下可用 | DOWN 落到 `episodeGrid > duration > text` |

单测与构建：`./gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --offline --tests VideoActivityLayoutTest --tests PlayerPlaybackRegressionSourceTest --tests PlayerControlFocusIntegrationTest --tests TmdbDetailActivityLayoutTest :app:assembleLeanbackArm64_v8aDebug -q`
→ exit 0，无 FAILED。`VideoActivityLayoutTest` 同步新增断言覆盖上述守卫与左右键接管。

自动播放模式回归（`player_auto_play=true`）：进入详情页直接 `state=3` 播放、焦点在 video、`videoCover` GONE，行为未受影响。

临时诊断日志（`FOCUSDBG-TEMP` / `VideoFOCUS` / `debugFocusId` / `MANUALDBG`）已全部移除，最终产物命中数为 0。


## 回归修复 2：选集网格内 LEFT 失效、RIGHT 跳出网格落到「简介」

用户后续报告（m01171）：「还是有问题我切换线路后按下到了分组按钮（正常）再按下到了当前集数按钮（正常）
再左遥控无效右遥控就跑到了简介按钮了错误的」。

### 根因

前一轮修的是**分段行（分组按钮）**的左右键，用户这次报的是**集数网格内部**的左右键，是另一处：

- 集数卡片根视图（`adapter_episode_card.xml` 的 `cardContainer` / 文本芯片）**不可聚焦**，
  真正持有焦点的是卡片内部的 `duration`/`text` 子视图。
- `EpisodeAdapter` 只写 `nextFocusUp/Down`（`bindTextView` 316-317、`bindCardView` 364-365），
  **没有任何水平 nextFocus**。
- `onEpisodeKey` 原先只接管 UP/DOWN，LEFT/RIGHT 直接 `return false` 交回系统 FocusFinder。
  FocusFinder 在卡片内部找不到水平邻居，于是：
  - 向左：无可达目标 → 焦点不动（表现为「左遥控无效」）；
  - 向右/向上：几何搜索跳出选集网格，落到网格上方最近的可聚焦控件 —— 即 `@+id/content`「简介」按钮。

这也解释了为什么「向下到当前集数按钮正常、再左/右就错乱」：纵向由 `verticalFocusTarget` 显式接管，
横向没有。

### 补丁

1. `TmdbEpisodeGridPolicy.horizontalFocusTarget(position, spanCount, itemCount, right)`：
   只在本排内移动（`position ± 1`，并夹在本排 `[rowStart, rowEnd]` 内），
   行首向左、行尾向右返回 `NO_FOCUS_TARGET`。与既有 `verticalFocusTarget` 同一策略类的纯函数，
   便于单测。
2. `VideoActivity.onEpisodeKey` 增加 LEFT/RIGHT 分支：算出 `target` 后
   `return focusEpisodeGridPosition(target)`；`NO_FOCUS_TARGET` 时 `return true`（消费按键），
   焦点留在原卡片，不再交给 FocusFinder，因此不会跳出网格。

### 验证

单测（`TmdbEpisodeGridPolicyTest` 新增 2 例，共 9 例全过）：

| 用例 | 断言 |
|---|---|
| `horizontalFocusTargetStaysInsideTheCurrentRow` | 行内推进、行首左边界、行尾右边界、非法 position 全部符合预期 |
| `horizontalFocusTargetUsesTheShortLastRowBoundary` | 末排短行（4 项 / spanCount=3）左右都不越界 |

组合验证：
```
.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug :app:testMobileArm64_v8aDebugUnitTest --offline \
  --tests VideoActivityLayoutTest --tests TmdbEpisodeGridPolicyTest \
  --tests PlayerPlaybackRegressionSourceTest --tests PlayerControlFocusIntegrationTest
```
→ `BUILD SUCCESSFUL in 6m 16s`，EXIT=0。
`VideoActivityLayoutTest` 157/0、`TmdbEpisodeGridPolicyTest` 9/0、
`PlayerPlaybackRegressionSourceTest` 15/0、`PlayerControlFocusIntegrationTest` 11/0。

`VideoActivityLayoutTest` 同步新增断言 `episode grid DPAD left/right must stay inside the current row`，
钉死 `onEpisodeKey` 必须含 LEFT/RIGHT 分支、调用 `horizontalFocusTarget(...)` 且 `NO_FOCUS_TARGET` 时消费按键。

设备实测：模拟器 `127.0.0.1:5561` 在本轮验证期间被宿主机上另外 7 台模拟器挤到
`loadavg 6.0`、`pm install` 长时间不返回，**本轮的端到端按键复测未能完成**；
结论以单测 + 源码契约 + 上一轮同链路（`focusEpisodeGridPosition` 已被 UP/DOWN 验证可用）为准。
未完成部分已明确记录，未做任何「已验证」的虚假声明。

## 回归修复 3：焦点在播放器与集数按钮之间来回跳（撤销过度激进的焦点看门狗）

用户报告（附截图）：切线路后向下移动到集数按钮，焦点会在**播放器和集数按钮之间来回跳**，
并质疑「播放器聚焦后为什么要跳到集数按钮上？顶多是进入播放页默认焦点在当前集数吧？」

这个质疑是对的，是我前一轮修得过头了。

### 根因：两处「焦点看门狗」越权

1. **根视图全局焦点看门狗**（`initView` 里的 `addOnGlobalFocusChangeListener`）：
   只要`newFocus == mBinding.video` 就 `post` 一次 `focusInitialEpisode()` 把焦点拽回选集。
   它**没有区分「进场初始焦点」和「用户主动移到播放器」**，所以用户按 UP 到播放器后
   立刻被抢回选集，形成来回跳。
2. **集数网格布局监听器**（`ensureEpisodeGridFocusListener`）同样过宽：
   原条件 `if (focus != null && !isFocusInside(mBinding.video)) return;`
   —— 焦点在**预览框**时不算「已在其它行」，于是每次布局都重新抢回选集。
   RecyclerView 回收持有焦点的子项时 `rootViewRequestFocus()` 会把焦点交回预览框，
   两者互相拉锯，正好是用户看到的闪烁。

根子在于：用户的需求只是「**进场时**默认焦点落在当前集」，不是「焦点永远不许停在播放器」。
我把一次性行为写成了常驻看门狗。

### 补丁

1. **删除**根视图全局焦点看门狗及其 `mManualFocusRedirectPending` 字段。进场初始焦点由
   `requestInitialPlaybackFocus()` 在 `setDetail` / `revealShellWhileTmdbLoads` / `revealTmdbDetail` /
   `showInitialPreview` 四处一次性决定，不需要常驻监听。
2. **收窄** `ensureEpisodeGridFocusListener`：只在 `getCurrentFocus() == null`（焦点真的丢了）时补偿，
   一旦焦点落在任何控件（含预览框）就 `cancelPendingEpisodeGridFocus()` 交还控制权；
   并加 `EPISODE_GRID_FOCUS_MAX_ATTEMPTS = 3` 上限，彻底杜绝长期看门狗。

### 行为对照

| 场景 | 修复前（本轮 bug） | 修复后 |
|---|---|---|
| 进入播放页（手动模式） | 焦点在选集 | 焦点在选集（不变） |
| 用户 UP 到播放器 | **被拽回选集** | 焦点留在播放器 |
| 切线路后 DOWN 到集数 | **来回跳** | 焦点稳定停在集数按钮 |
| 预览框意外获焦 | 被反复抢走 | 保持系统赋予的焦点，不再拉锯 |

### 验证

单测 `VideoActivityLayoutTest` 新增两条契约：
- `episode focus correction must give up as soon as any view holds focus, so it never fights the player window`
  —— 要求方法体含 `if (getCurrentFocus() != null) {` 且**不含** `isFocusInside(mBinding.video)`；
- `episode focus correction must be bounded instead of acting as a permanent watchdog` —— 要求含尝试次数上限；
- `no root focus watchdog may reclaim focus from the player window` —— 要求全局看门狗与
  `mManualFocusRedirectPending` 都不存在。

组合验证：`assembleLeanbackArm64_v8aDebug` + `testMobileArm64_v8aDebugUnitTest`
（VideoActivityLayoutTest 157/0、TmdbEpisodeGridPolicyTest 9/0）→ `BUILD SUCCESSFUL`，EXIT=0。

**负向对照**：把监听器临时改回旧的 `if (focus != null && !isFocusInside(mBinding.video)) return;`，
`VideoActivityLayoutTest` 立刻 `FAILED`，报错正是新增断言
`episode focus correction must give up as soon as any view holds focus...`，
证明断言真的在管这件事，而不是摆设。随后已还原为正确实现并复跑全绿。

设备端复测同样未完成（5561 仍受宿主机 8 台模拟器争抢，`loadavg` 6~8）。

## 回滚

- 代码：`git revert <commit>`。
- 功能级降级：让 `isManualPlaybackMode()` 返回 `false` 即完全回到修复前行为；
  或仅让 `updateVideoCover()` 直接 return 关闭封面层，其余不受影响。

## 已知边界

- 手动模式下若封面 URL 全空，`videoCover` 保持 GONE（露出原黑底），不留透明洞。
- 起播失败时 `onError` 会清掉标志与已起播标记，封面恢复，用户可换源/换集重试。
- 短剧/阅读器路由走 `startPlayer` 既有拦截分支，未新增分支。
- 本次仅验证 leanback（影视原生模式）；原生增强 / 炫彩详情播放页共用同一 Activity 与同一判定，
  但未逐一实测。
