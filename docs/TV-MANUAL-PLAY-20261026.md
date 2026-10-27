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
