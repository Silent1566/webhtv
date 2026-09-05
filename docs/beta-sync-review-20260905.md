# Beta 同步与合并复评

## 目标与范围

- 完成已开始的 `origin/dev1`、`origin/beta` 合并，评审包含已提交未推送内容的最终差异，修复并验证后提交、创建恢复标签、推送 `dev1`，创建或更新中文 beta PR，最后同步远端状态。
- 沿用 active guard `beta-sync-review-20260905`，范围为 `app`、`docs`；没有登记的任务外初始脏路径。保留所有既有合并结果，不重置索引或工作树。
- 不改变播放器依赖、二进制、弹幕渲染、搜索顺序或缓存 key 格式。

## 恢复证据

- 2026-09-05 23:36，Asia/Shanghai：工作区分支 `dev1`，HEAD 为 `399023ced875b3bdb298afd7b09219c8e0eef57e`。
- `.git/MERGE_HEAD` 为 `eab79fba9251dd6d07283fe149d7ce4f6874554d`（origin/dev1）和 `db44647432ea1ac094298cebca9f535f73dc8202`（origin/beta）；合并尚未提交。
- 当前索引包括触屏优化与播放菜单遮罩。工作树还保留 `Backup.java` 类头恢复、遮罩偏好备份补齐、弹幕请求生命周期修复及测试。
- 上一会话完成了回调线程和主线程双重请求身份检查，但遗漏手动匹配缓存的关键词接线。
- 当前磁盘 `app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.dialog.DanmakuSearchIntentTest.xml` 记录 4 项测试、1 项失败、0 errors；失败断言为 `DanmakuSearchDialog must remember the submitted keyword`。结果时间为 2026-09-05T15:17:06.793Z，是有效 RED 证据，不重复运行。

## 修复与验收

- 两个搜索对话框将本集、站点系列、TMDB 季级三层缓存统一绑定到 `searchIntent.getResultKeyword()`，避免结果返回前编辑输入框污染匹配记忆。
- `DanmakuSearchDialog.search` 读取一次关键词，并向请求与状态对象传递同一值。
- 保留旧请求在后台回调及主线程更新前的失效检查；销毁对话框或再次搜索后旧请求不能覆盖结果。
- 验收：纯状态测试与两个入口接线测试通过；mobile/leanback arm64 聚焦测试和 Java 编译通过；合并差异复评没有未解决阻断项。

## 验证与已知限制

- GREEN：独立 JUnit `DanmakuSearchIntentTest` 4/4，日志 `build/beta-review-junit.log`。
- GREEN：一次 Gradle 调用分别筛选 mobile/leanback arm64 debug 单测，两端各 40/40，均为 0 failures、0 errors；包含两端 Java 编译，`BUILD SUCCESSFUL in 3m 1s`。日志 `build/beta-sync-review-20260905-tests.log`。
- 每端覆盖：`DanmakuApiSourceTest` 2、`BackupPreferenceFilterTest` 10、`DanmakuMatchCacheTest` 8、`PlaySpecTest` 4、`SettingPlaybackOverlayTest` 2、`DanmakuSearchIntentTest` 4、`TouchOptimizationHelperSourceTest` 10。
- 修复后复评：核对相对原合并 beta `db44647432ea1ac094298cebca9f535f73dc8202` 的全部本地生产差异与对应测试，包括三个播放入口的 TMDB 身份传递、对话框返回父层、缓存兼容/隔离、搜索回退、弹幕选择状态、备份恢复及异步生命周期。没有未解决阻断项；当前会话无子代理评审工具，复评由本代理完成。
- 2026-09-05 本轮 fetch 后 beta 更新至 `bc5f9b42e090dd7fd303a56c052618dcb5506047`，新增 `Backup` 类头修复与 TMDB 横向选集居中。先关闭当前合并单元，再在独立 guard 中合入该增量并仅补验受影响范围。
- 旧全量测试报告的两项 `FfmpegVc1SupportTest` 失败属于未改动的 FFmpeg 运行库测试，本任务不更改或规避这些测试，也不把聚焦验证称为全量通过。
- 本轮不重复触屏优化已有的设备验证；历史记录见 `docs/touch-optimization-mode-design.md`，其中最终 APK 开关闭环的限制仍保留。

## Recovery Anchor

- 目标及验收：修复已提交关键词绑定，验证并闭环当前 beta 合并、提交、恢复标签、推送与中文 PR。
- 当前阶段：关键词接线与当前合并范围的验证/复评已完成，尚未提交；既有合并改动保留在工作区。
- 保护：旧 Orca 会话和 transcript 只读；任务外路径不改动；不要再次启动合并或重建 guard。
- 回滚锚点：合并前 HEAD `399023ced875b3bdb298afd7b09219c8e0eef57e`；提交后可用恢复标签定位合并状态，回退合并须明确主线后另行批准。
- 未决事项：关闭当前合并后还需整合新到的 beta 增量、推送及创建中文 PR；全量 FFmpeg 运行库测试和历史设备补验限制仍如上。
- 下一步：执行当前 guard 的 `finish`，记录 mobile/leanback 各 40/40 通过并创建恢复标签。
