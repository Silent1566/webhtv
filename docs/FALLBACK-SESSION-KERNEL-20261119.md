# FALLBACK-SESSION-KERNEL-20261119 自动回退必须更新会话内核

## Recovery anchor

- 目标：任何一次引擎切换（含播放失败自动回退、内核不可用回退）都把会话内核更新为刚建起来的引擎，消除「以为在跑 A、实际在跑 B」的分叉；`chosen` 退化为纯归因字段。
- Lane：quick-fix；分支 `dev1`；base HEAD `5aab31bcb6c8c5611278cdffc90b098884c0294f`（上一任务 KERNEL-MEMORY-SCOPE-20261119）。
- 已完成：`PlayerManager` 一处发布逻辑 + 两处文档注释 + 契约测试扩展。
- 下一步：提交（guard finish）。

## 缺陷

`PlayerManager.switchEngine(...)` 里写的是 `if (chosen) PlayerSetting.putActivePlayer(type);`。自动回退（`fallbackPlayer` → `"fallback-kernel:"`、`ensurePlayerAvailableForPlayback` → `"kernel-unavailable"`）都传 `chosen=false`，于是内核已经换成 EXO，而会话内核仍停在用户选定的 MPV。

会话内核的读者都会因此按错误内核分叉：

- `SiteApi.playerContent(key, flag, id)` / `playerContentIsolated(...)` → `Source.fetch(result, playerType)`：MPV 走 MPD/EDL 解析，其他内核不走 → 取到给 MPV 的地址却交给 EXO 播。
- `PlayerManager` 构造器（服务重建时按它建引擎）、IJK 实时运行时判定（`:4123/:4143/:4518`）。
- `LutEligibility`（按内核判 LUT 资格）、`PlaybackActivity:652`（服务未连上时的 isMpv）、`OriginHistoryPlayback`（把内核传给原源会话）。

原注释给出的两个理由都不再成立：「写历史」实际由 `rememberPlayerKernel(type)` 用用户选定的值写，测试还禁止用会话内核覆盖；「用户的选择」由历史记录承载，每次起播 `applyHistoryPlayerKernel()` 都会重新写回。

## 改动

1. `switchEngine(...)`：`if (chosen) PlayerSetting.putActivePlayer(type);` → `PlayerSetting.putActivePlayer(type);`（附原因注释）。
2. `switchEngine` 的方法注释与 `logSwitchCause` 的 `chosen=` 说明改写：`chosen` 只表示「是否由用户选定」，与会话内核无关。
3. `PlayerManagerSwitchCauseSourceTest.chosenFlagSeparatesUserChoiceFromAutomaticFallback` 增加两条断言：会话内核无条件发布；不得再出现 `if (chosen)` 门控。

不动的东西：回退顺序（EXO→IJK→MPV→系统）、`chosen` 参数与日志字段、`preparePlayer`/`switchPlayer` 的既有发布点、历史记录的内核记忆语义。

## 验证

- `:app:testMobileArm64_v8aDebugUnitTest --tests PlayerManagerSwitchCauseSourceTest`：10 例 0 失败。
- `--tests VideoActivityLayoutTest`：159 例 1 失败，仍是既有失败 `leanbackPlaybackLoadingIgnoresStaleReadyBeforeNewPlayerStarts`（base `8cbf5faa5` 起即失败，与本次无关，未修）。
- `git diff --check`：干净。

## 风险与回滚

- 风险：回退后下一次取址改用「实际引擎」分支，取到的地址与之前不同——这正是修复目标；若某站点只提供 MPV 专用地址，回退到 EXO 后会先失败再被下一次回退处理，与本次行为差异只体现在取址分支选择上。
- 回滚：`git revert` 本次提交即可，无数据/配置迁移。
