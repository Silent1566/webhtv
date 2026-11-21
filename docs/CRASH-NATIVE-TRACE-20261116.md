# CRASH-NATIVE-TRACE-20261116 · native 崩溃取证

## Recovery anchor

**Objective:** 让下次 native/ANR 死亡能从 `ApplicationExitInfo` 取到平台 trace（tombstone 可读串 / ANR 文本前缀）并写入恢复详情；崩溃会话头补设备、ABI、系统、版本与默认内核。不改播放链路。
**Acceptance:** 有 trace 时详情出现 `traceFormat=tombstone-derived-strings` 与可读信号/符号行；无 trace 时明确 `traceFormat=unavailable`/`trace=none`；会话头出现 `# environment:` 与 `# device:`；`:app:testMobileArm64_v8aDebugUnitTest --tests '*Crash*'` 全绿。
**Status:** 已实现并验证，待提交。
**Next action:** `task_guard.sh finish`（提交 + recovery tag）。

## 触发证据

用户导出的 `webhtv-debug-log(1).txt`（2026-11-16 11:31–11:33）显示上一个进程 `reason=CRASH_NATIVE status=6`（status=6 是信号号，即 SIGABRT），但同一份文件里三条取证通道全空：

| 通道 | 文件里的表现 | 代码原因（修复前） |
|---|---|---|
| Java 报告 | `未找到本次崩溃的堆栈记录` | native 崩溃无 Java 报告；`CrashLogSession.discardReport` 拒收旧报告，行为正确 |
| native tombstone | `Native/ANR trace unavailable or binary; no native trace parsing performed.` | `CrashRecoveryState.exitDetails` 硬编码；恢复链路从不读 `getTraceInputStream()` |
| 系统 logcat | `logcat=unavailable` | `CrashLogSession.logcatTail` 用 `logcat -d` 子进程，未持 `READ_LOGS`，现代 Android 上必然失败 |
| 崩溃前事件 | `结构化事件行数：0` | `debugLog=off`，崩溃前无 `av-diag:` 事件 |

结论：**该文件无法定位崩溃**；缺失的是取证能力，不是报告逻辑错误。

## 平台事实

- `ApplicationExitInfo#getStatus()`：“the exit status argument of exit() if the application calls it, or the signal number if the application is signaled”（AOSP `core/java/android/app/ApplicationExitInfo.java`，2026-11-16 读取）。故 `status=6` = SIGABRT。
- `getTraceInputStream()`：API31+ 对 `REASON_CRASH_NATIVE` 返回符合 tombstone proto 的 protobuf，对 ANR 返回文本（developer.android.com `ApplicationExitInfo` 参考页，2026-11-16 读取）。
- 本仓 `targetSdk=28`、`compileSdk=37`、`minSdk=24`；查询侧已按 `api >= 30` 门控。

## 设计决定

采用**窄适配**：不引入 native signal handler、不引入 tombstone 解析库、不改播放链路。

1. `CrashRecoveryState.Exit` 增加 `trace` 分量，并保留 7 参构造器，既有调用点与测试零改动。
2. `PreviousProcessCrashRecovery.trace()` 在既有 worker 线程上按 `TRACE_BYTES=8KiB` 有界读取；任何异常只记日志并返回空串——死亡事实已由 exit 记录证明，取证失败不得撤销恢复决定。
3. `CrashRecoveryState.traceText()` 做**文本归约**而非解析：ANR 走 UTF-8 原文；native protobuf 只保留长度 ≥4 的可打印字节串（protobuf 框架字节是噪声，信号行/线程名/符号是可读证据）。结果截断 4096 字符并压成单行，保持 `key=value` 块可解析。
4. `exitDetails` 用 `traceFormat` 显式声明来源等级：`anr-text-prefix` / `tombstone-derived-strings` / `platform-trace-strings` / `unavailable`，并在缺失时写“失败点未知”，不做任何“已定位”暗示。
5. `CrashLogText.composeSession` 增加可选 `Environment` 重载（旧 5 参签名保留为委托），输出 `# environment:` 与 `# device:`。

为什么不解析 tombstone：遵循 AGENTS.md“不为本需求贸然建立新崩溃框架”，且解析 protobuf 需新依赖与 schema 版本适配，收益是格式化而非可读性——可打印串已足以把死亡关联到具体库（如 `libmpv.so`）。

## 变更文件

| 文件 | 变更 |
|---|---|
| `utils/CrashRecoveryState.java` | `Exit.trace` 分量 + 7 参兼容构造器；`traceText`/`printableRuns`/`flushRun`/`traceFormat`/`oneLine`；`exitDetails` 输出真实 trace 状态 |
| `utils/PreviousProcessCrashRecovery.java` | `TRACE_BYTES`；`trace(ApplicationExitInfo)` 有界读取；查询处填充 `Exit.trace` |
| `utils/CrashLogText.java` | `Environment` record；`composeSession` 6 参重载与环境/设备行 |
| `utils/CrashLogSession.java` | `environment()`/`kernel()`/`process()`/`safe()`；调用 6 参重载 |
| `test/.../CrashRecoveryStateTest.java` | 4 例：trace 进详情、缺失如实声明、可打印串归约、ANR 文本且有界 |
| `test/.../CrashLogTextTest.java` | 2 例：环境头完整输出、无环境时不产生该块 |

## 验证

- `./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac` → BUILD SUCCESSFUL。
- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*Crash*'` → BUILD SUCCESSFUL；**88 测试 0 失败 0 跳过**（含上述 6 例新增）。
- 未做真机验证：本机 `adb devices` 无设备，且真实 native 崩溃不可按需制造。归约逻辑由二进制 fixture 单元测试覆盖（`traceReductionKeepsReadableRunsAndDropsFramingNoise`），读取路径的设备侧行为属未验证项。

## 未处理（已识别，未在本任务扩张）

- `InMemoryDiagnosticFile.export` 恒返回 `partial=true`，导致崩溃会话报告的 `完整性：partial` 与同文件 health 的 `complete-within-declared-window` 矛盾（假警报）。
- `DiagnosticReport` 的 `结构化事件行数：0` 只统计 `av-diag:` JSON 行，与下方 21 行时间线并列时易被读成“采集坏了”。
- `CrashLogSession.logcatTail` 的 `logcat -d` 子进程通道在未持 `READ_LOGS` 时基本无效，`coverage: logcat=own-uid-tail` 会高估覆盖。

以上三项均属报告口径/通道存废，与本任务“抓到 native trace”不同域，未改动。
