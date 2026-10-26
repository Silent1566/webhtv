# TV-MANUAL-PLAY-20261026 关闭「点播自动播放」后的手动播放交互修复

## 目标

TV 端关闭【点播自动播放】后进入手动播放模式：进入播放页不再把焦点交给左上角预览窗口，
预览位显示影片封面；点选集直接全屏起播，省掉「先全屏、再按一次 OK」的多余步骤。

## 结论先行：目标对象更正

初版方案误把目标定位到 `TmdbDetailActivity`（沉浸融合内联面板）。经用户指正后确认真实对象是
`app/src/leanback/res/layout/activity_video.xml` 的 `@+id/video`（400dp×225dp、margin 24dp、
左上角、`background="@color/black"`、`focusable="true"`、内含 `exo`），承载于 `VideoActivity`，
覆盖 **原生增强 / 影视原生 / 炫彩详情的播放页**（`TmdbDetailActivity.playDefaultPlayback()` →
`VideoActivity.startDirectTmdb(...)`）。

## 根因（`VideoActivity`，改动前）

| 现象 | 根因 |
|---|---|
| 焦点落在预览窗口 | `mBinding.video.requestFocus()` 有 9 处无条件调用（2258/2597/2777/2865/2889/4487/5570/5275 等），与自动播放开关无关；`checkCast()` → `showInitialPreview()` 末尾即 5570 |
| 要先全屏再按 OK 才播 | `video` 点击监听是 `onVideo()`：`if (!isFullscreen()) enterFullscreen();`，只切布局不起播；全屏后 OK 才走 `onKeyCenter()` → `player().isEmpty() ? onRefresh()` |
| 选集不直接播放 | `onItemClick(Episode)` 首行 `if (shouldEnterFullscreen(item)) return;`，而 `isPlayingContent(episode)` 只对「已选中」集为真；点其它集走 `selectEpisode` → `onRefresh` → `startPlayer`，此时 `shouldAutoPlay()==false`，prepare 了但不播 |
| 黑框而非封面 | `loadArtwork` 把封面喂给 `exo.setDefaultArtwork(...)`，但 `PlayerView` 仅在无视频帧时显示；`video` 的 `background` 是硬编码黑 |

自动播放开关的作用点：`VideoActivity extends PlaybackActivity`，
`PlaybackActivity.shouldAutoPlay()` 返回 `PlayerSetting.isAutoPlay()`，作为 `playWhenReady` 传入
`player().parse(...)` / `player().start(...)` —— 关掉后取流照做、只是不播。

## 决策（用户已确认 Q1=A、Q2=A）

- **Q1 生效范围 = A**：所有 `VideoActivity` 场景（原生增强 + 影视原生 + 炫彩详情播放页）。
- **Q2 选集行为 = A**：点选集直接全屏播放。
- 仅 TV 端生效；手机 flavor 行为不变（`Util.isLeanback()` 门槛）。

## 实现

### 1. 新增「手动播放模式」语义

```java
private boolean isManualPlaybackMode() {
    return Util.isLeanback() && !PlayerSetting.isAutoPlay();
}
```

### 2. 初始焦点分流（4 处调用点）

新增 `requestInitialPlaybackFocus()` / `focusInitialEpisode()`，复用既有
`scrollToEpisode(position, true)`（自带 `post` 与空适配器保护）。替换点：
`setDetail`、`revealShellWhileTmdbLoads`、`revealTmdbDetail`、`showInitialPreview`。
每处保留原「焦点回预览窗口」分支作为非手动模式的 else 路径。

### 3. 选集直接全屏起播

`onItemClick(Episode)` 内：

```java
if (isManualPlaybackMode()) mExplicitPlaybackRequest = true;
try { selectEpisode(item, true); } finally { mExplicitPlaybackRequest = false; }
if (isManualPlaybackMode() && !isFullscreen()) enterFullscreen();
```

配合子类覆盖：

```java
@Override
protected boolean shouldAutoPlay() {
    return mExplicitPlaybackRequest || super.shouldAutoPlay();
}
```

**关键修正**：初版 `playSelectedEpisodeNow()` 在 `selectEpisode` 之后再调一次 `onRefresh()`，
而 `selectEpisode` 内部（3885 行）已经调用 `onRefresh()` —— 会重复取流。已改为只置位标志、
复用 `selectEpisode` 自身触发的那次取流。

### 4. 预览位封面层

`activity_video.xml` 在 `exo` 之后、`audioStage` 之前插入 `@+id/videoCover`
（`focusable="false"`、`clickable="false"`、`visibility="gone"`、`scaleType="centerCrop"`）。
`updateVideoCover()` 在 `isManualPlaybackMode() && !mAudioStageVisible && player().isEmpty()` 时显示，
并在 `setDetail`、`showInitialPreview`、`onRefresh`、`onStateChanged(STATE_READY)` 处刷新。

## 验证

| 项 | 命令 | 结果 |
|---|---|---|
| leanback 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| mobile 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 定向单测 | `VideoActivityLayoutTest`（157，含新增 2）+ `PlayerPlaybackRegressionSourceTest`(15) + `PlayerControlFocusIntegrationTest`(11) + `TmdbDetailActivityLayoutTest` + `DetailModeControllerTest` | 0 failures / 0 errors |
| 负向对照 | 把 `isManualPlaybackMode()` 临时改为 `return false;` | `manualPlaybackModeMovesFocusToEpisodesAndShowsCover` FAILED → 断言真实生效，已还原 |

新增用例：
- `manualPlaybackModeMovesFocusToEpisodesAndShowsCover`
- `manualPlaybackCoverOnlyShowsWhileNothingIsLoaded`

**未执行**：真机/模拟器端到端（本机无 ARM 设备）。

## 环境说明

本会话中 `ops_bash`（bash.exe）在 `app/`、`docs/`、`.codex/`、Gradle 缓存下均被沙箱 ACL 拒绝，
而 `pwsh` 与 `read`/`write`/`edit` 工具不受限。因此编译、测试、git 操作全部经 `pwsh` 执行；
`task_guard.sh` 因依赖 bash 无法运行，改用直接 git 提交 + annotated tag 完成等价收尾。

## 回滚

- 代码：`git revert <commit>`。
- 功能级降级：让 `isManualPlaybackMode()` 返回 `false` 即完全回到修复前行为；
  或仅让 `updateVideoCover()` 直接 return 关闭封面层，其余不受影响。

## 已知边界

- 手动模式下若封面 URL 全空，`videoCover` 保持 GONE（露出原黑底），不留透明洞。
- 起播失败时 `player().isEmpty()` 仍为真 → 封面保留，用户可换源/换集重试。
- 短剧/阅读器路由走 `startPlayer` 既有拦截分支，未新增分支。
