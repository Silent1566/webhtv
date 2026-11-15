# player-button-hide-authority：播放器按钮隐藏开关必须成为最终权威

任务 guard id：`player-button-hide-authority`（quick-fix）
分支：`dev1`；基线 HEAD：`99d6d323e7501d43198d6cf5d98e631f13bf635e`
日期：2026-11-15 (+0800)

## 1. 用户提问与结论

问题：「K歌模式、沉浸音频模式、屏显、有广告、解析」是不是在线接口注入的？

结论：**不是**。五项都来自本地目录
`app/src/main/java/com/fongmi/android/tv/setting/PlayerButtonSetting.java` 的 `DEFAULT` 清单，
设置入口是 `SettingPlayerActivity.java:128`（leanback）/ `SettingPlayerFragment.java:133`（mobile）
弹出的 `PlayerButtonConfigDialog`，落盘键 `player_button_hidden`。
唯一与「在线」相关的只有解析**列表内容**来自 `VodConfig.get().getParses()`；按钮与显隐开关都是本地的。

## 2. 根因（按实际证据修正后的版本）

隐藏偏好的唯一消费者是 `PlayerButtonSetting.applyVisibility(Map)`，它**只写 GONE、只覆盖传入 map 里的 view**。
因此只要某个写点(a)不在 map 覆盖范围、(b)按功能态自己写 VISIBLE 且后面没有 `applyActionButtonVisibility()`
兜底，用户的隐藏选择就会被盖掉。

**原生控制栏（mobile / leanback）本身基本是受控的**：每个功能态写点后面都紧跟
`applyActionButtonVisibility()`。真绕过只有两处直调，均已核实：

| 文件:行 | 绕过方式 |
| --- | --- |
| `setupActionButtons()` → `updatePanDiagnosticAction()` | 在 `applyOrder/applyVisibility` 之后重写 panDiagnostic 可见性 |
| `onImmersiveAudioModeChanged()` → `updateImmersiveAudioAction()` | 切换沉浸音频模式即把已隐藏的按钮点亮 |

**弹层与融合内联是真正的失效面**：

| 面 | 文件:行（基线） | 失效 |
| --- | --- | --- |
| 手机 ⚙ 弹层 | `mobile/.../ui/dialog/ControlDialog.java:519-521` | 解析行只按 `isUseParse()` 判断 |
| 手机 ⚙ 弹层 | `:533-537` | 沉浸音频行只按 `isControlAudioContent()` 判断 |
| TV ⚙ 弹层 | `leanback/.../ui/dialog/ControlDialog.java:404-407` | 解析行同上 |
| 两 flavor 弹层 | `dialog_control.xml` 的 timer / panDiagnostic / codecCapability 行 | 无任何隐藏开关判据 |
| 沉浸融合 | `TmdbDetailActivity.java:8458-8462` | 质量/解析/弹幕/有广告按功能态直写 |

## 3. 决策（interview 已确认）

- 范围：手机原生 + TV 原生 + 沉浸融合三个界面，凡在「播放器按钮」目录里的按钮统一「功能可用 && 未隐藏」。
- 弹层：⚙ 底部弹层跟随隐藏开关，隐藏即彻底不可达。
- 保留的既有契约（不改）：融合模式的**悬浮图标按钮**（上/下集、全屏、投屏、弹幕）
  仍只按功能态显示，不受「播放器按钮」控制——`TmdbDetailActivityLayoutTest.fusionOverlayButtonsDoNotFollowPlayerButtonSettings`
  早已钉住这条，本轮先改后按该契约回退。

## 4. 实施

1. 接缝：`PlayerButtonSetting.apply(View view, String id, boolean available)` ——
   `available && isVisible(id) ? VISIBLE : GONE`，成为目录按钮的唯一显示判据（`applyVisibility` 退化为只兜底隐藏）。
2. `mobile/VideoActivity`：选集/上集/下集（`setEpisodeAdapter`、`refreshTmdbEpisodeTitles`）、画质、
   字幕/音轨/视频轨、标题 全部改走 `apply(...)`。
3. `leanback/VideoActivity`：13 处写点改走 `apply(...)`，含 panDiagnostic / immersiveAudio / cast / fullscreen，
   并顺带修掉 §2 的两个直调绕过；`applyActionButtonVisibility()` 的复合顺序未动。
4. `TmdbDetailActivity`：`updateInlineButtons` 的 6 处动作栏写点改走 `apply(...)`。
5. 两个 flavor 的 `ControlDialog`：解析 / 沉浸音频 / 计时 / 网络诊断 / 解码能力 等行跟随隐藏开关；
   TV 弹层的目录行只受隐藏开关控制（全屏行不能抄宿主的「全屏时收起」展示规则，否则会删掉退出全屏入口）。
6. 回归锁：`PlayerPlaybackRegressionSourceTest` 新增 `playerButtonHiddenSwitchOwnsEveryCatalogManagedView`；
   同步修正因重构而失效的字面量锚点（focus 刷新、融合 adFeedback/画质、弹层解码行）。

## 5. 验证

| 验证 | 结果 |
| --- | --- |
| `:app:compileMobileArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 6 个契约测试类（PlayerPlaybackRegression / VideoActivityLayout / FallbackDecodeLabelSync / MultiThreadProxyPlayerUi / TmdbDetailActivityLayout / PlayerControlFocus） | 320 tests，3 failed（**均为预存在**，见下） |
| 运行时设备验证（192.168.50.3） | **未做**（需装 APK 并手动进入播放页，见 §6 风险 1） |

3 个预存在失败与本次改动无关，证据：
- `PlayerPlaybackRegressionSourceTest.mobileExoStartupPasses…`：断言的字面量
  `startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata(), mInitialPlaybackPosition);`
  在基线 HEAD 与工作区里都不存在（`git show HEAD:… | grep -c` = 0），属陈旧期望。
- `VideoActivityLayoutTest.leanbackPlaybackLoading…` 与 `TmdbDetailActivityLayoutTest.colorfulDetailDoesNotKeepPlaybackServiceBound…`：
  `git diff -U0` 中不含 `shouldBindPlaybackService|mPlaybackPlayerStarted|startPlayer(` 任何一行，断言对象未被本次改动触碰。

## 6. 已注意到但未处理（交给用户路由）

1. **未做设备运行时验证**。自动化只到编译 + 源码契约级；真机需装 APK 后手动进入播放页逐项确认。
2. 同类旁路仍在边界外：`CastActivity.java:338-340`、`LiveActivity.java:305-309 / 1140-1143`
   （投屏页 / 直播页控制栏直接写 VISIBLE，不查隐藏开关）。
3. `屏显(DISPLAY)` 在**影视原生模式没有任何对应按钮**（只有沉浸融合 TV 行的 `playerDisplay`），
   该设置在原生模式是空操作；手机弹层的 display chip 组是 OSD 开关，不是 DISPLAY 按钮。
4. 手机原生 `setKaraokeActionState()` 把动作栏 K歌 按钮恒定 GONE（K歌入口在音频页 `audioKaraokeAction`），
   与「隐藏」无关但与目录项显示不一致，未改。
5. 弹层 speed chip、scale 行、两个按钮行在布局里没有容器 id，整行隐藏后 section 标题会残留；改布局超出本单元。
6. 3 个预存在失败测试（§5）未修。

## 7. 回滚

单 commit，改动集中在 6 个源文件 + 3 个测试文件 + 本文件；
回滚执行 `git revert <commit>` 即可，无数据迁移、无偏好键变更（`player_button_hidden` 语义不变）。

## 8. 交付后补充（2026-11-15 21:2x）

1. **直播页无需改动**（子代理的「边界外发现」是误报）：`LiveActivity` 两个 flavor 都有
   `setupActionButtons()` 把点播同名按钮登记进 `mActionButtons`（mobile 449-461 / leanback 282-296），
   且 `applyActionButtonVisibility()` 就是「先恢复无动态条件的 5 个按钮、最后
   `PlayerButtonSetting.applyVisibility(mActionButtons)`」的顺序（mobile 471-479 / leanback 303-310，
   源码里还写了顺序理由的注释）。隐藏项在末尾被 GONE，因此直播页本来就受控。
2. **投屏页不接目录，属新能力而非缺陷**：`app/src/leanback/.../activity/CastActivity.java` 全文件没有
   `PlayerButtonSetting` / `mActionButtons` / `applyActionButtonVisibility`，`setTrackVisible()`（337-341）
   只做功能态门控。让投屏页跟随「播放器按钮」需要先把它纳入目录，是行为扩展，未做。
3. **设备运行时验证受阻（未完成）**：设备 `192.168.50.3` 上的 `com.silent.android.webhtv`
   （debug 版 5.6.0，今日 16:20 安装）与本机 debug keystore 签名不一致，
   `adb install -r` 返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；本仓库 `local.properties` 没有 release 签名配置
   （`hasReleaseSigning` 为 false），因此无法用 release 签名覆盖安装。
   要完成运行时验收必须先卸载（会清掉站点接口 / 历史 / 播放器按钮偏好），需用户明确同意后走
   「备份 shared_prefs+databases+files+base.apk → 卸载 → 装本构建 → 验证 → 还原」流程。

## 9. 真机运行时验收（2026-11-15 21:4x，LIO-AN00 / Android 9 / 192.168.50.3）

流程：备份 `shared_prefs`+`databases`+`files`+ 现有 `base.apk` → 卸载 → 装本构建（mobile debug）→
把备份数据写回 → 逐面验 → 把原始 prefs 原样写回。

| 场面 | 设置 | 实测结果 |
| --- | --- | --- |
| 视频播放的 ⚙ 弹层 | `player_button_hidden=danmaku,multi_thread_proxy` | 弹层无「弹幕」行 |
| 视频播放的 ⚙ 弹层 | 追加 `timer,pan_diagnostic,codec_capability,lut,repeat,title,play_params` | 「LUT / 定时 / 循环 / 硬解能力 / 链路诊断」五行全部消失，未隐藏的「播放 / 片头 / 片尾 / 缩放 / 刷新 / 硬解 / 屏显设置」照常显示 |
| 音频播放的 ⚙ 弹层 | hidden 不含 `immersive_audio` | 有「沉浸音频模式」行 |
| 音频播放的 ⚙ 弹层 | hidden 只含 `immersive_audio` | 该行消失 |
| 播放本身 | — | 本构建可正常起播 http mp4/m4a，无崩溃、无 ANR |

未覆盖：原生全屏动作栏（倍速/比例/画质/字幕-音轨-视频轨/标题那些按钮）没有在真机上逐项点验——
该布局需先进入全屏，adb 盲点不稳定；这一面由源码契约测试
`PlayerPlaybackRegressionSourceTest.playerButtonHiddenSwitchOwnsEveryCatalogManagedView` 覆盖。

设备现状：设备上装的是本构建（debug）；用户原始 prefs 已按备份原样写回
（`player_button_hidden=danmaku,multi_thread_proxy`），`databases`/`files` 也在。
原安装包与完整数据备份：`/home/maple/Workspace/webhtv/device-backup-20261115-2140/`
（`installed-base.apk`、`appdata.tar`），需要换回原包时可用。

截图与 UI dump：`/tmp/htv-shots/09-sheet-before.png`、`10-sheet-after.png`、`12-audio-sheet.png`。

## Recovery anchor

- 目标：控制栏按钮隐藏开关在手机原生 / TV 原生 / 沉浸融合三面成为最终权威，弹层跟随。
- 状态：实现与自动化验证完成；设备运行时验证未做；已 commit + recovery tag。
- 下一步动作（若用户批准）：装 debug APK 到 192.168.50.3，逐面确认隐藏生效，再决定是否把同一判据扩到 Live/Cast。
