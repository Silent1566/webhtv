# 他人 fork 可借鉴/可合入改动评估（2026-11-26）

**性质：** 只读调研评估。本文不授权任何代码改动；主仓生产代码、构建脚本、锁文件、补丁目录均为 0 修改。
**基线：** 本仓 `dev2` = `787ea36b48207c1f7f1a487d1087924d81af027d`（2026-11-26，工作树干净）。
**上级校验：** `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`；播放器/native 项须走 `.codex/skills/upstream-integration-governor/SKILL.md` 的门槛再定。

## Recovery anchor

- 目标：给出"别人 fork 里哪些提交值得借鉴/合入"的分级清单与最小实施路径，不实施。
- 状态：调研完成，结论如下；未创建任何工作区代码改动，未提交、未打 tag。
- 回滚：删除本文件即可，无副作用；`F:\Workspace\webtv2\_scan\*` 为分析用 bare 克隆（alternates 指向主仓），可随时删除。
- 下一步：等用户对第 1–3 项点名授权后，按本文"最小验证"执行。

## 1. 筛选漏斗与方法

| 阶段 | 数量 | 口径 |
|---|---|---|
| 两个网络的 fork 全集 | 572 | `Silent1566/webhtv` 147 + `fish2018/webhtv` 562，按 `full_name` 去重（`fish2018/webhtv` 现重定向到 `webhtv/webhtv`，二者同库） |
| 近期仍活跃 | 351 | `pushed_at >= 2026-08-01` |
| 确有自有改动 | 128 | 同时相对 `dev2` 与 `fish2018/main` 都领先，取 `min(ahead)` > 0 |
| 深读分析 | **32** | 见 §2 名单（其余 96 个按 fork 星数/最后推送降序抽样后判定为镜像或无源码增量） |

**"领先数"不等于"别人的成果"。** 本次调研复现了三种系统性假象，评审任何 fork 前必须先扣减：

1. **共享补丁**：11 个 fork 的唯一"自有"提交都是同一个 `de4e1f59535b64bcb8db2dc8c7267b86d19f62fe`（WebHome fm.vod 续播修复），且本仓已等价（`HomeWebBridge.java:284/300`，来源 `62fd7f12e58`）。
2. **上游滞后计成本人**：`4e30ffaf219b1db15fe5662aeff6f9820e2b32bf`、`e519fdefefa46bccc6f7a86b97062f9d8e2b03e8`、`c5b447b3a55fbc74ce483c2b9cc7dd64238f5ea7`、`f3f0eddf06df698979b857fb1fd79bee78e766cf`、`78634fefd4faf1852b414b6ec9e309144de8b655`、`f69db83746312b0ad98ea4c38eb907bb024a6fd0`、`3f3031748882713455a47ded88c58ce6956fcb1c` 经 `git branch -a --contains` 证实只在 `remotes/fish2018/main`/`remotes/webhtv-upstream/main` —— 属**上游未合入**，不是 fork 作品（ShuttleSpace 的自有改动因此为 0）。
3. **rebase/重放使 patch-id 失效**：`git cherry` 报 `+` 不等于新增。llb0 的 424 个非 merge 提交中，patch-id 只覆盖 38 个（其余因 blob 按需拉取中断未算），因此本文只对"已逐条下钻并用本仓符号/行号核验"的候选负责。

**深读名单（32 个）**：klcb2010、llb0、st7833232、GamePCStudio、Helios030、lisa3456、rongyigg、wyicibo1-netizen、ipet520、shxjshxj123、x315600、IsayIsee、syzsky、jammy912、qznet、muxd93、zhuyinan、ShuttleSpace、1663736404a、kuby007、AZHUZHITAN、YaChengMu、keket0、DodgeZhang、LeelandoAi、sayxiaoyan、zou1314250、hjdhnx、ordinary521、wzg66、cnmake、personal-backup2026。

## 2. 合入候选（独立、低耦合、缺口已复核）

### 2.1 历史片名稳定化 —— 最便宜且收益明确

- 来源：`llb0/webhtv` `a720d702583ec4b4cd7e46bd4142f75f13414593`（3 文件 52+/4-）
- 作用：历史里已落库的真实片名，不再被后续播放/远端同步写入的空串、URL、纯集号覆盖。
- 本仓现状：**缺失**。`History.java` 与 `PlaybackProgressWriter.java` 中 `isRealVodName`/`preferStableVodName` 均无命中（`git grep` 空）；`History.java:694 setVodName` 是唯一入口。
- 窄适配（不整搬）：只取两个纯函数（`EPISODE_ONLY_NAME` 正则 + `isRealVodName` + `preferStableVodName`）与 `PlaybackProgressWriter.java:211` 一处调用；规则改为 `isRealVodName(incoming) ? incoming : (isRealVodName(existing) ? existing : incoming)`（真名之间仍允许纠正，只禁止占位回写）。**不要**移植其 `save()`/`save(int cid)` 两处 hunk 与封面分支（本仓无 `isMissingArtwork`）。
- 风险：若个别源刻意用后刷纠正错名，会被旧名钉住；上述放宽规则已覆盖该场景。
- 最小验证：`HistoryTest` 补 3 例（占位不覆盖真名 / 真名可纠正 / 空值不写入）；单条播放后历史名不变。

### 2.2 站源日志判级（只取一半）

- 来源：`llb0/webhtv` `0a062077b47b5c84d7bc5a4968c6245773ddc354`
- 作用：调试日志页不再把接口插件（XBPQ）正常 INFO 染色为错误。
- 本仓现状：**部分缺失**。`DebugLogs.java:319` 仍是 `low.includes('成功')||low.includes('通过')?'ok':'raw'`，`:309` 仍有 `error` 兜底 → 误判现场存在；补丁新增的 `spiderLevel()` 正则（`^\[(DEBUG|INFO|WARN|WARNING|ERROR|FATAL|STDERR)\]`）本仓没有。
- 窄适配：只替换 SpiderDebug 分支判级 + 新增 `spiderLevel`；**保留** `DebugLogStore.addCleaned` 与 `800945de7c` 的行级去重，不移植其 `emit/emitChunks` 分段重写（那半与本仓性能契约冲突）。
- 最小验证：接一个 XBPQ 源，INFO/WARN 不再红条；原有行级合并不回退。

### 2.3 竖屏短剧「上下滑切集」

- 来源：`wyicibo1-netizen/webhtv`
  - `dc1cc9d1f96cde9fcff3a1dcd6230b260571be33` 新增 `ui/custom/VerticalPageSwitcher.java`(113) + mobile `VideoActivity`(29)
  - `05e7c88b1cb7e97c3a5c7d56c46af6becbfe7852` 双层竖向翻页去闪（+84/-29）
  - `dc580f7b1c33551213dbfb8b182638c4b7f484da` 黑场过渡（+11/-10）
- 作用：竖屏短剧在播放页上下滑直接切集；两轮后续修复说明经过真机迭代。
- 本仓现状：**缺失**。`VerticalPager|VerticalPageSwitcher` 在 `app/src` 零命中；现有切集是横向/网格。
- 风险：与现有横滑切集、返回手势、音量/亮度手势冲突；仅 mobile 竖屏。
- 最小验证：竖屏短剧上下滑切集、黑场过渡无残影、返回手势不误触、横屏不受影响。

## 3. 借鉴候选（取设计/规则，不整搬代码）

| 项 | 来源（fork / 完整 SHA） | 本仓现状 | 建议做法 |
|---|---|---|---|
| 局域网遥控器 + 语音遥控 | llb0 `e1ed088310f9e47538eefee3b01e867a6a155bfa`、`c39948defe1eb18aee5878289e0cefd4274f5920` | `server/process/` 无 Voice.java，无 `voice/` 包 | 不可搬运（8 个新类 + 1500 行对话框 + token + 三语）。先以现有 `NsdDeviceDiscovery` 为发现层做一个 `/key` 端点最小闭环 |
| 按键注入必须走 InputEvent 管道 | llb0 `73eca60c5a994c363f65d500c3f476e723e632e2` | 本仓 `VideoActivity.dispatchKeyEvent` 仍是被接管点 | 把"注入按键走窗口 InputEvent，dispatchKeyEvent 仅兜底，二者靠返回值短路"写成设计约束，不搬其 Voice 依赖 |
| 进度条预览条 + 抽帧 | llb0 `3ea92a6d9fe17cb6bc327552644950266d7f34b8` | 无 `SeekPreviewStrip`/`Trickplay`（`ls-files` 零命中） | 新 native 源 + FFmpeg 抽帧 + 1100 行缓存，必须走 E/P 门槛评审，不得当补丁合入 |
| 离线下载/缓存 | muxd93 `28a7a523cacc6cf1f4b1a880195fc5b316bc648c`(53 文件 6586 行)、`5277f1f79c54789100bfe6d7723153aea98176b2`；YaChengMu `1847ebae1ac748590e2751caf9baa4c88dbe7da2`、`9d4a7a81d567b5961052b408cd88feff43e09a35`、`3cfb1dd569fd75adac149b56f43d823886d778fc`、`793359991425aca9a78bfe7e2b1ccea78001a5c6`、`af9542268edd8f9526150d85159f1ba9b3e0f21c` | 仅 `utils/Download.java`、`CatWebDownload`、`DownloadRef`；无 DownloadManager/Service | 若要做：先落"单任务 HLS 下载 + 暂停续传 + 本地起播"，直接借其断点/签名刷新不变量与测试断言 |
| 广告区间反馈 → 规则落地 | YaChengMu `f012214a98b3f3c689596953072e9ea251609839`（+24 提交） | 无 `ad/feedback` 包；已有自家广告栈 | 设计级借鉴，接本仓 `AdBlockStatsStore`/规则模型，代码不可搬；属产品决策 |
| 平铺 HDR10 HEVC 抢回硬解 | YaChengMu `fd888c1ddf147ef3825ee89645b691913256c0aa`（单文件 +74） | `ExoUtil.java:1286` 的归一化只在 `DolbyVisionHdr10FallbackRenderer` 内（`shouldUseDolbyVisionHdr10Fallback` 只认 DV） | 技术点成立但触碰 renderer/色域策略；须按 `webhtv-player-gates.md` 在一台 HDR10 4K 机型上验丢帧与色域后再定 |
| 片头跳过确认框"本集不再弹" | YaChengMu `7677ccff5ef688535a88c67ee68b58f2b58d23c5` | `IntroSkipPlayback.java:170 cancelConfirmation` 只释放租约；3 处调用点（leanback `VideoActivity:1950`、mobile `VideoActivity:1841`、`TmdbDetailActivity:12115`） | **先复现再改**；对方用 `declineConfirmation`（永久 skipped）撞本仓 :168/:314 契约，应改为独立 asked 集合 |
| QuickJS 有界等待 | syzsky `d010432c78906ecaa1ed55a8bb120f6084d526cd`（其 import 缺失由后续 `ccc705d05411622d1e91497b40a57506b6ac9804` 补上） | `quickjs/.../Spider.java:56` 仍是 `submit(...).get().get()` 无限等待 | 只能借意图：`task.cancel` 对已完成 Future 是 no-op、executor 为 final、`releaseJS` 同样无界 → 需"超时即整体废弃 Spider 实例 + Loader 重建"设计，不能 cherry-pick |
| Updater per-ABI 清单取值 | IsayIsee `9af1263c74b22226be3086d2d714adb92426c19c` | `Updater.java:261` 只取顶层 `sha256` | 对方实现有缺陷（缺当前 ABI 时 size→0、sha256→""），须 `has(abi)?per-ABI:顶层` 且判空后再用 |
| ContinueWatching 导出 + 详情深链 | zhuyinan `8266b006965225286389c8b9f444621398742d7b` | 无 `ContinueWatching`/`DeepLink` 类 | 属对外契约：其 `DeepLinkActivity` 为 `exported="true"` 且无 permission/调用方校验，标题海报可被三方伪造；写别的包 `Android/data` 在 Android 11+ 可能静默失败。要做须先定协议与权限 |
| 首页源锁定 | GamePCStudio `287826df5ff63d44ae917e5bbde6b812d892b679` | `grep homeLock|首页锁定` 为空；`AppearanceDialog` 已有 | 只取"锁定首页源"这一交互，别搬其聚合弹窗（我们已有） |
| 读 MP4 `stsd` fourcc 判定"当前引擎能否解此 codec" | st7833232 `6775e562715ff584089d0ad75267d9cde5adc1e2`（67 行纯算法） | 引擎选择只看设置，不看码流；但 Exo 已有 `3dcfca96f0a Add support for VVC in MP4 extraction`、MPV 已带 VVC 解码 | 只借算法（Box 边界、`size==1` largesize、`size==0` 到末尾、`boxes()` 迭代 + `child()` 固定链，无递归炸弹；限 64 顶层 box、moov ≤16 MiB）。判定须改为"当前引擎能否解该 codec"，不能照搬其 `{bvc2,vvc1,vvi1}→MPV`；moov 在尾部与不支持 Range 时只能留前缀（对方亦未解决） |
| 标题 →（剧名, 季）折叠的**残余**规则 | st7833232 `1483030bb0ea0c65c3283785e208f875705c631c` | 已接近等价：`ui/helper/TmdbMatcher.java:32` `SOURCE_SEASON`（第N季\|部\|season N\|sN）+ `:492 compareKey()`（含 s2t 折叠）、`EpisodeSeasonPolicy.java:13 SPECIAL_SEASON`、`EpisodeTitleCompact.java:31/33/48`、`following/FollowingUpdatePolicy.java` | 只借两条残余：**弱季标记**（中文字后裸 2–20，如「慶餘年2」）与 **NFKC→T2S→标签剥离的顺序**；弱季规则必须配前缀长度保护，且复用现有 `compareKey`。优先级低 |
| 换源列表按实测延迟排序 | Helios030 `4537e1768223d07ba2a7c444792752c83a6bf2d1` | 无 `ConfigLatencyProbe` | 做成开关（不改变用户熟悉的顺序），限并发 + 超时 |
| 毛玻璃导航栏 | wyicibo1 `8f8b4fc0f4de4bd0f46996885fb76549b178457a`（+`3e52d855bf342d6691ca8df51e92c0df27fd1666`） | 全库无 `RenderEffect` | 纯视觉；API31+ 生效 + 低版本回退，配前后截图 |
| 多季/下载变体选择规则 | st7833232 `4b7a00b6dcc2331335406216f9bc1d8f7a781136` | 无离线模块 | 只有当决定做离线下载时才有意义；其码率/分辨率启发式是 iPhone 假设 |

## 4. SMB：本仓已有播放层，缺的是入口（口径更正）

初轮把 SMB 记成"整块空白"，复核后修正为：

- **已有**：`third_party/maven/androidx/media3/media3-datasource/1.11.0-alpha01-fongmi/*.aar` 内 `classes.jar` 含 `androidx/media3/datasource/SmbDataSource.class`，且同包 `DefaultDataSource.class` 已引用 `smb` → `smb://` 在当前构建的 Exo 数据源链里**可达**；`gradle/libs.versions.toml` 已声明 `smbj = "0.14.0"`。
- **缺失**：App 层浏览/凭据/入口。`app/` 树内 `Smb|smbj` 零命中，仅一个测试提到 `smb://`（`PushParserTest.java:85`）。
- 因此：`qznet` 的 Exo-only `SmbSchemeDataSource`（`7a0f256ca58df2a22e8c4d5adb7c9f6007555c46`，其后 `03b5e5689c48e4e6166d491a5528605bea1909ad` 加 4MiB 读与连接池）属重复实现；`jammy912` `f0bc1b807705f61e6f7107e000df746d737dfc39` 是另一套。
- 最有价值的是 `klcb2010/webhtv`（60★，全网第二大活跃 fork，HEAD `87e3154a3f22f33fda2d1911c588f2bff7ed8465`，SMB 主体在 `b1e134a8b592912c91ccfe2240c085f46a279615` 及后续 60 个提交）：
  - 组成：`SmbBrowserActivity`(1851) + `SmbClient`(363, smbj) + `SmbNetworkScanner`(102, Socket 扫描) + `SmbPlaybackProxy`(188, NanoHTTPD)；功能含收藏共享、删除（含递归）、复制到 Download、壁纸复用、TV 焦点框。
  - 关键设计：**不改 Exo**，本地 HTTP 代理把 `smb://` 变成 `http://127.0.0.1:...` 再走 `VideoActivity`。对本仓可以更省：直接丢 `smb://user:pass@host/share/x.mkv` 给已有 SmbDataSource，代理只在 MPV 路径需要。
  - 必须改：他把账号密码明文存 `SharedPreferences("smb_credentials")`（JSON username/password/domain）→ 须接本仓加密存储。
  - 入口改动：mobile 底栏菜单/个人设置项 + leanback `HomeButton`（其 hook 依赖 `isSmbEntryEnabled`、`Func`/`FuncPresenter`、`menu_nav.xml`、`activity_setting_personal.xml` 等 14 处标记）。
  - 另有 `inject_youtube_home_fallback.py`（`csp_Youtube` 首页推荐兜底取首个分类，仅 TV）——依赖油管线路维护，价值低，仅记录。
  - 方法论可借鉴：他用 `mod/` overlay + `mod/hooks/inject_*.py` + `mod/apply.sh` 在构建前注入，上游树零改动、变基几乎无冲突；本仓是树内直改 + 文档留痕。二者可并存，外来特性可考虑 overlay 形态。

## 5. 暂缓 / 实验（不得据此开工）

- **ByteVC2（bvc2）demux 标签**：st7833232 `6975fabdaf2f0318396824bd6d5857e4c9cdcd09`。
  - 复核后**前提被推翻一半**：本仓 `third_party/mpv-native-overrides/ffmpeg.sh` 的 configure 没有 `--disable-decoders` 白名单，已发布的 `libmvcodec.so`（arm64 + armv7）内含 `libavcodec/vvc/dec.c` 与 `VVC (Versatile Video Coding)` 字符串 → **VVC 解码器本就在册**，对方补丁里"构建开 `--enable-decoder=vvc`"那半对本仓无意义。
  - 真缺口只剩 demux 标签：`libmvformat.so` 中 `vvc1`×3、`vvi1`×1，而 **`bvc2`×0**（两 ABI 一致）。
  - 补丁本体仅 14 行 2 文件：`isom_tags.c` 加 `{AV_CODEC_ID_VVC, MKTAG('b','v','c','2')}`；`mov.c mov_finalize_stsd_codec` 仅在 `codec_tag=='bvc2' && !extradata_size` 时合成 2 字节 `fe 00` extradata。未新增 codec ID，无登记风险；无 in-band 参数集时拿不到 SPS/PPS → 失败而非崩溃。
  - 证据强度不足：**无 FATE、无 in-tree 回归测试**，commit 自述 "CI build not yet run"，等价性证据来自其 `docs/W-254-bvc2-decoder.md`（与 vvdec 5056/5056 帧一致，B 级，未独立复现）。
  - 结论：**暂缓/实验性**。前置条件是拿一段 bvc2 样片做 `framemd5` 对拍，且必须走 `third_party/patches` 的 FFmpeg 补丁车道 + 二进制重建证据链，不得直接合入。
- **iOS 端整栈**：st7833232 `ios-poc`（993 个非 merge 提交，`ios/` 338 文件 14.6 万行，仅 19 个提交碰 Android 路径且限于 `third_party/mpv-ios-lock.json`/补丁）。作为独立工程学习对象（`WebHTVCore` 纯逻辑层 + 105 个测试 + 每任务一篇 `docs/W-*.md` + 补丁编号与 FATE 说明），**不合入**。

## 6. 忽略（含重复与噪声）

- 共享补丁 `de4e1f59535b64bcb8db2dc8c7267b86d19f62fe`（11 个 fork 各一份）→ 本仓已等价。
- 上述 7 个上游滞后 SHA → 走 `fish2018` 上游同步通道，不算 fork 成果。**其中 `c5b447b3a55fbc74ce483c2b9cc7dd64238f5ea7`（ARMv7 libass）暴露真实缺口**：本仓 `third_party/exo-ass-native/prebuilt/` 只有 `arm64-v8a/libexo_ass.so`，armeabi-v7a 无 ASS 渲染（复核确认）——应作为上游同步项排期到 E4-LIBASS 车道，须同时核对 `MANIFEST.sha256`/`build-provenance.json` 与本地可重建性，否则等于引入不可复现二进制（约 1.9 MB）。
- ipet520（dev2/beta 血统改基重放，6 条 patch-id 已在 dev2 + 实现皆在）、lisa3456（277 条上游重放 + 删除 `AdblockProxy/AdRuleMatcher` 等**负向改动**）、shxjshxj123（`NativeAudioLogTap` 已存在）、x315600（净自回滚）、ShuttleSpace（0 自有）、1663736404a（其 TMDB 详情整搬会回退）。
- rongyigg 根级 logos 候选：本仓已 `append_to_response=images`（`TmdbService.java:233/247/259`）并解析 `logos`（`TmdbSourcePayloadParser.java:57`）、已有 w342/w780 图源（`TmdbConfig.java:25-26`）→ 差距仅剩"根级 `/images` 端点 + w500 logo 尺寸"，低价值。
- kuby007/AZHUZHITAN/keket0/DodgeZhang/LeelandoAi/sayxiaoyan/zou1314250/hjdhnx/ordinary521/wzg66/cnmake/personal-backup2026：仅共享补丁或 local.properties/CHANGELOG/README/CI 噪声。
- klcb2010 的 PushPlus 失败通知与 `action-failure-notify.yml`、各处 workflow/版本号/包名改名 → 构建运维噪声。

## 7. 推荐顺序（按价值 ÷ 成本 × 证据强度）

1. **历史片名稳定化**（§2.1）：1 个文件 + 1 个测试；约 0.5–1h。
2. **站源日志判级半量**（§2.2）：1 个文件；约 0.5h。
3. **竖屏竖向切集**（§2.3）：3 个文件 + 真机；约 2–4h（含手势冲突处理）。
4. **SMB 浏览入口**（§4）：分期；先"浏览 + 用现有 SmbDataSource 播放"，约 1 天起，凭据必须走加密存储。
5. **遥控最小闭环**（§3）：先 `/key` 端点 + InputEvent 管道约束文档，约 1 天；语音与镜像交互另立阶段。
6. **离线下载**：独立立项，须先过 E/P 最佳实践门槛，不可作为补丁合入。

## 8. 证据边界与未决

- 未做设备/样片验证的项目：§2.3 手势冲突、§3 HDR10 丢帧与色域、§4 SMB 真实 NAS 起播与 seek、§5 bvc2 出画。
- patch-id 覆盖不完整：llb0 仅 38/424（blob 按需拉取中断），故对其"未发现等价"而非"证明不存在"。
- scan-c 两项未在本仓反查（`b782c415fc30db2f8da277f5f4dc6770c59678e9` 偏移弹窗、`5e2db4095ecf9090536119503bd7c2c2b08ee4df` 本地源导入）→ 不作为合入依据。
- 未审查各 fork 的源码许可与再分发条款；合入前须确认作者授权与 LICENSE 兼容（尤其 klcb2010 的 `mod/` 整包、muxd93 的下载模块）。

## 9. 复核记录（首轮结论中被撤回的部分）

| 首轮结论 | 复核结果 |
|---|---|
| llb0 "424 条全部真新增" | 撤回：patch-id 仅覆盖 38/424，改述"未发现等价吸收，亦未证明不存在" |
| QuickJS 超时补丁"可合入" | 撤回：该 commit 缺 `CompletableFuture` import（后续 `ccc705d05411622d1e91497b40a57506b6ac9804` 才补）；双 `get` 最坏 30s；`cancel` 对已完成 Future 为 no-op；executor final 且 `releaseJS` 无界 → 降为"借鉴 + 生命周期设计" |
| Updater per-ABI"可合入" | 撤回：对方最终 HEAD 仍未修，缺 ABI 时 size→0 / sha256→""，降为"借鉴重写" |
| SMB "整块空白能力" | 更正：AAR 内已有 `SmbDataSource` 且 `DefaultDataSource` 认 `smb`，`smbj` 已在 catalog；缺的是 App 浏览入口 |
| `c5b447b3` 属 GamePCStudio 自有 | 更正：属 `fish2018` 上游未合入（但暴露 armv7 libass 真缺口） |
| 5 个 fork 共有的 7 个提交算自有 | 更正：全部归上游滞后，ShuttleSpace 自有改动为 0；`c5b447b3`/`4e30ffaf` 作者实为上游 `www.xueximeng.com` |
| bvc2 补丁"全网唯一可移植修法" | 撤回"全网唯一"（无全网审查证据）；补丁实测仅 14 行、2 文件，且"构建开 vvc 解码器"这半无必要——本仓 `libmvcodec.so` 两 ABI 已含 VVC 解码器 |
| "本仓缺 VVC 解码能力" | 推翻：解码器在册，真缺口只有 demux 标签 `bvc2`（`libmvformat.so` 中 `vvc1`×3 / `vvi1`×1 / `bvc2`×0） |
| stsd fourcc 嗅探"可合入" | 降为借鉴：对方 `moov` 在尾部与不支持 Range 的场景同样只能取前缀；路由规则须按本仓引擎能力重写 |
| 标题折叠"本仓仅部分覆盖" | 更正：本仓已有 `TmdbMatcher.SOURCE_SEASON`/`compareKey`/`EpisodeSeasonPolicy`/`EpisodeTitleCompact` 等更近等价，残余只剩弱季标记与折叠顺序 |
| 真实标题被覆盖修复"整搬 3 文件" | 更正：llb0 补丁实为 3 文件 52+/4-（非单文件）；本仓缺 `isMissingArtwork`，封面分支不可搬，只改 `PlaybackProgressWriter.java:211` 一处 |
