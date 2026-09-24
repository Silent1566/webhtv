# fix-auto-line-audio-only

## 目标与验收

- 播放失败后自动切换到下一条线路时，不再沿用失败播放器实例的解码/Surface 状态。
- 保留本剧用户记住的播放核心；自动线路回退不得自动改选另一个核心。
- Mobile 与 Leanback 保持一致处理；手动切换核心的既有行为不变。

## 原因

`onError()` 原来只执行 `reset/stop` 后进入 `startFlow()`。自动切线路会重新取址并复用同一个引擎实例；若失败现场留下视频 renderer/Surface 异常，音频仍可能继续，形成“有声音没画面”。手动切核心会重建引擎，因此能恢复。

## 修复

在自动回退取址前调用 `preparePlayer(applyHistoryPlayerKernel())`。`applyHistoryPlayerKernel()` 恢复本剧记忆的核心；`preparePlayer()` 仅在需要时释放并重建同核心引擎，并重新绑定进度条、渲染面和 UI 状态。这样不会改变用户选择的核心，也不影响正常自动回退的地址流程。

## 验证

- `PlayerPlaybackRegressionSourceTest.autoLineFallbackRebuildsRememberedPlayerBeforeFetchingNextLine`
- Mobile/Leanback ARM64 debug 编译或按需要覆盖安装后，用首线路失败且第二线路可播的资源验证画面恢复。

## 回滚

- 撤销本提交即可恢复旧行为；改动只在 Mobile/Leanback `VideoActivity.onError`。
