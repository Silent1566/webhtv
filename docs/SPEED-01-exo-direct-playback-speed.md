# SPEED-01 · EXO 压缩直通导致倍速完全失效

## 1. 现象

真机诊断包（用户导出，只读）：

`/run/user/1000/gvfs/smb-share:server=192.168.50.3,share=users/Maple/共享/webhtv-debug-log (3) (6).txt`

- `play.clock`：player 上报 `speed=5.0/3.0/2.5`，但 `positionMs` 每 5000 ms 墙钟只推进约 5000 ms（1.00×）。
  倍速完全没生效，0.5× 同样失效。
- `audio.output.configure`：`encoding=10`（AAC-LC，压缩而非 PCM）、`offload=false`、`tunneling=false`。
- `audio.candidates`：`support=2`，即 `AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY`，判定由
  `ExoCompressedAudioDirectPolicy` 的厂商压缩直通路径给出。
- 时序：`audio.processing stage=requested-parameters speed=5.0` 在 11:34:57.398，
  `audio.output.configure` 在 11:34:57.499 —— 输出配置时 sink 的 speed 已经是 5.0，
  所以在选择/配置阶段加门禁即可覆盖用户报告的场景。

## 2. 根因链

1. media3 的倍速只有两条实现路径：PCM 域的 `SonicAudioProcessor`，或
   `AudioOutput.setPlaybackParameters`（`AudioTrack.setPlaybackParams`）。
2. 后者由 `DefaultAudioSink.useAudioOutputPlaybackParams()` 决定，读
   `OutputConfig.usePlaybackParameters`；`ExoCompressedAudioDirectPolicy.java:323-324` 写的是
   `setUsePlaybackParameters(config.enablePlaybackParameters)`，该值来自
   `DefaultRenderersFactory.enableAudioOutputPlaybackParameters`，media3 默认 false，
   本仓库除 `ExoUtil.java:1060` 的转发外没有任何地方打开它。
3. 压缩直通输出不经过处理器链，于是 Sonic 分支对该输出等于什么都不做 → 速率恒为 1×。
4. media3 自带的兜底 `DefaultAudioSink.maybeDisableOffload()` 开头即 `if (!config.isOffload) return;`，
   而 `ExoCompressedAudioDirectPolicy.java:319` 显式 `setIsOffload(false)`，
   所以"要变速就退回 PCM"的保护永远不会触发。
5. 直通准入发生在**格式/输出选择与配置**阶段（`getFormatSupport` → `getOutputConfig` →
   `getAudioOutput`），此时策略对象并不知道播放速率，所以它照样给出直通配置。

结论：只要在非 1.0× 时不让这套策略认领压缩直通格式，media3 就会走解码器 + PCM，
倍速由 Sonic 真正实现；1.0× 仍走直通，保留 E11 的收益。

## 3. 改动点

| 文件 | 改动 |
| --- | --- |
| `app/.../exo/ExoCompressedAudioDirectPolicy.java` | 新增 `volatile float playbackSpeed = 1f`、`public void setPlaybackSpeed(float)`、`speedRequiresPcm()`；`getFormatSupport` 与 `getOutputConfig` 在既有 `audioPassthroughEnabled` 检查之后、直通准入判断之内加门禁：`Math.abs(speed-1f) > 0.001f` 时 `vendorDirectConfigs.remove(directKey)` 并 `logDecision(..., "vendor-direct-speed-requires-pcm")`，前者返回 `standard`，后者回落 `super.getOutputConfig(config)`。 |
| `app/.../engine/ExoPlayerEngine.java` | 新增 `public void setPlaybackSpeed(float)`，转发到已持有的 `compressedAudioDirectPolicy`。 |
| `app/.../PlayerManager.java` | `applyEffectiveSpeed` 在 `player.setPlaybackParameters(...)` **之前**调用新增的 `prepareExoAudioForSpeed`：先 `exo.setPlaybackSpeed(speed)`，再在 `isExo()` + 速率离开 1× + 上一步仍是 1× + 播放已 READY + `getAudioPlaybackDiagnostics().outputMode()` 仍是压缩输出（`PASSTHROUGH`/`COMPRESSED_DIRECT`）时，用 `speedChangeAudioRebuildInProgress` 一次性标志调用一次 `rebuildAudioPipeline()`，防止 `rebuildAudioPipeline` 内部 `setSpeed` 再触发重建。 |

不改动：`setEnableAudioOutputPlaybackParameters` 的取值（全仓仍为 false）、AC3/EAC3/DTS 的 offload 语义、
E11 的成功路径判定。

## 4. 验证

- `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "*ExoCompressedAudioDirectPolicySpeedTest" --console=plain`
  → `BUILD SUCCESSFUL in 1m 53s`；新增 6 例全部通过（`tests="6" failures="0" errors="0"`），
  覆盖：1.0× 仍返回 `FORMAT_SUPPORTED_DIRECTLY` 且仍是厂商直通配置；3.0× / 2.5× / 0.5× 返回不含直通的
  standard 结果并回落到 `super.getOutputConfig`；回到 1.0× 后重新可认领直通；1.0005× 在容差内不触发门禁；
  NaN 回落为 1.0×。
- 编译由同一条测试任务覆盖（`:app:compileMobileArm64_v8aDebugJavaWithJavac` / `...UnitTestJavaWithJavac` 均为该任务的依赖）。

## 5. 残余风险

- 1×→非 1× 的首次切换会触发一次 `rebuildAudioPipeline()`：表现为一次短暂重载（重新 prepare 并回到原位置）。
  非 1×→非 1× 的后续改速不再重建；1× 从不重建。
- 该重建依赖播放处于 `STATE_READY` 且诊断输出的 `OutputMode` 已经可见（`getAudioPlaybackDiagnostics()`
  需要当前播放会话的 trace 一致）。若此刻诊断仍为 `UNKNOWN`，本次不会重建，仅靠选择期门禁在下次
  输出配置生效。
- 回落 PCM 后，高码率多声道音轨（如 5.1 真 Dolby/DTS 的压缩帧）需要设备以 PCM 承载同样通道数；
  设备 PCM 通道/采样率能力不足时会出现下混或失败，此时由既有 PCM 失败/回退链路接管，
  不再享受 E11 直通收益。
- 回到 1.0× 时只重新打开直通准入，不会主动重建回直通输出；E11 收益在下一次输出配置/管线重建时恢复。

## 6. 回滚

`git revert <本任务提交 SHA>`（等价于还原上面三处改动 + 删除对应单测与本文档）。
恢复后行为立即回到"压缩直通 + 倍速无效"，与改动前一致。

## Recovery anchor

- 目标：非 1.0× 时 EXO 退出厂商压缩直通改走 PCM+Sonic，使倍速真实生效；1.0× 保留 E11 直通。
- 状态：实现 + 定向单测通过（待提交）。
- 回滚锚点：本任务提交（`fix(exo): 倍速非 1.0 时退出压缩直通并回落 PCM`）及其 `recovery/SPEED-01/<timestamp>` 标签。
- 下一步：真机复测 5.0×/3.0×/0.5× 的 `play.clock` 是否与墙钟同比，并确认 1.0× 仍报 `support=2` 直通。
