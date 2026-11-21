# KERNEL-MEMORY-SCOPE-20261119 内核记忆按线路生效

## Recovery anchor

- 目标：播放内核的记忆（`History.player`）只在「当前线路自己的历史记录」上生效；跨源聚合挑来的记录一律回落设置页默认内核。播放失败自动换核不得写入内核记忆，只有用户手动换核才写。
- Lane：quick-fix；分支 `dev1`；base HEAD `8cbf5faa5a058aec0beaf79ff6f900ad4ce5b5e5`；受保护的既有脏路径：`.scratch-goal/`（未跟踪，不暂存）。
- 已完成：源码 4 处 + 契约测试 1 处（见下）。
- 未验证：Gradle 单项测试与双 flavor 编译（本次执行）。
- 下一个动作：跑 `:app:testMobileArm64_v8aDebugUnitTest --tests '*VideoActivityLayoutTest*'` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`，把结果填进「验证」。

## 现象

设置页默认内核 = MPV，但某些剧起播用 EXO；手动在播放器里切到 MPV 能正常播，退出重进同一资源也仍是 MPV，只有换到同一部剧的另一个源/线路时又变回 EXO。

## 根因

内核记忆存在历史记录的 `player` 列（`bean/History.java`，`-1` = 跟随设置页默认）。播放页起播读的是 `getPlayerOrDefault()`：

- 手机版 `ui/activity/VideoActivity.java` `applyHistoryPlayerKernel()`
- 电视版同方法
- TMDB 内联播放 `TmdbDetailActivity.inlineHistoryPlayerKernel()`

而 `History.findPlayback()` 在开启聚合时会按 TMDB 身份**跨源**挑“最近可续播”的记录（`findPlaybackByTmdb` → `findPlaybackCandidate` → `copyForPlaybackKey`），跨源续播走 `forPlaybackKey()`。两条路径都会复制记录并留下 `playbackSourceKey`（`isCrossSourcePlayback()` 为真），于是源 A 记住的内核被带到了源 B，看起来像设置页默认内核失效。

失败自动回退（`PlayerManager.fallbackPlayer` → `nextFallbackPlayer` → `PlayerSetting.firstUntriedPlayer`，顺序 EXO→IJK→MPV→系统）只改引擎，本来就不写历史；本次以测试钉住这条边界。

## 改动

1. `History.getPlaybackPlayerOrDefault()`：`isCrossSourcePlayback()` 为真时返回 `PlayerSetting.getPlayer()`，否则沿用记录记住的内核。
2. 三个读点改用它（手机版 / 电视版 `applyHistoryPlayerKernel`、TMDB `inlineHistoryPlayerKernel`）。
3. `VideoActivityLayoutTest.playerKernelSwitchStaysScopedToCurrentPlayback` 扩展：钉住按源作用域、以及 `PlayerManager` 不持有 `History` 引用（失败回退不可能写内核记忆）。

不动的东西：写点仍然是 `rememberPlayerKernel()`（两版 Activity）与 `rememberInlinePlayerKernel()`，都只从用户换核回调进入；回退顺序、`putActivePlayer` 的会话状态语义都不改。

## 已知遗留（未在本任务处理）

自动回退走 `switchEngine(..., chosen=false, ...)`，只换引擎、不更新 `PlayerSetting.putActivePlayer()`：回退后 `SiteApi.playerContent()` 仍按 MPV 分叉取址。属独立缺陷，另起任务。

## 回滚

`git revert` 本任务的单个提交即可；不涉及数据迁移、配置键或二进制。

## 验证

- `:app:testMobileArm64_v8aDebugUnitTest --tests 'VideoActivityLayoutTest'`：159 例，1 例失败，且为**既有失败** `leanbackPlaybackLoadingIgnoresStaleReadyBeforeNewPlayerStarts`——该断言要求 `startPlayer(getHistoryKey(), result` 落在电视版 `setPlayer(Result)` 方法体内，在 base `8cbf5faa5` 与本次改动后索引同为 `-1`，与本次无关。本次扩展的 `playerKernelSwitchStaysScopedToCurrentPlayback` 通过。
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：BUILD SUCCESSFUL。
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests VideoActivitySeekProgressSourceTest --tests TmdbDetailSeekProgressSourceTest`：6 例，1 例失败，同为**既有失败** `inlineKernelRebuildRebindsSeekView`（base 与本次的 `startPlayer(getHistoryKey(), result` 索引都为 `-1`，属陈旧期望）。
- `git diff --check`：无输出（无空白错误）。

以上两个既有失败不在本任务范围，未修改。
