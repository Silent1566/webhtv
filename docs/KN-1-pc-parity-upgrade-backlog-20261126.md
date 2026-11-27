# KN-1 参考 PC 版的可借鉴升级清单（评估快照 2026-11-26）

> 状态：**只读评估结论**，不是实施计划，也没有任何一项在本文件中被实现或验收。
> 评估基线：Android（本仓库）`6fa23d9`；参考 PC 版 `Silent1566/webhtv-pc` 在评估期间为 `7f05770`，日历相关复核时为 `754490e`。
> 评估方法：两端源码静态核对（无运行、无构建、无真机）。源码存在 ≠ 运行验收。

## 0. 这份清单是什么

以 PC 版为参照，逐项判断「PC 已实现、Android 有明显缺口」的能力，并排除 Android 已覆盖的部分，避免把已有能力重复当成升级点。每条都给了两端源码位置，便于复核。

**评估之后的情况变化（必须一起读）**：

- 第 3 项「追剧日历」**已在随后实施并合入**：`9e921d7da7`、`0d6ac40dd8`、`52176f9118`、`dacaa8a283`、`30817a79d6`，文档见 `docs/FOLLOW-4-following-calendar.md`。本条保留在清单里只为说明来龙去脉，**不要再当作待办**。
- 第 7 项[局域网接收授权](KN-1-7-lan-receive-authorization-decision.md#15-最终实现验收与兼容性2026-11-27)已完成 TLS 配对/鉴权、双端 UI、第一方发送接线与双设备验收；其余 9 项仍是候选。

## 1. 优先级与证据（按收益 / 改动风险排序）

### A. 低风险，改动局部，接口兼容

| # | 升级点 | PC 实现 | Android 缺口 | 状态 |
|---|---|---|---|---|
| 1 | 自动跳过片段支持按数据源开关 | `ui/intro_skip_options_page.dart:144-169`＋`services/intro_skip/registry.dart:24-31`（按位掩码筛源） | `service/IntroSkipService.java:161-170` 满足 imdb/tmdb 就查三家；`setting/Setting.java:1575-1609` 只有模式与片段类型 | 候选 |
| 2 | 字幕搜索按源反馈失败原因 | `subtitle_providers/registry.dart:64-100` 保留 `failures`（缺本地视频/超时/异常，已脱敏）；`subtitle_search_dialog.dart:183-192` 按源展示 | `subtitle/provider/SubtitleProviderRegistry.java:138-152` 单源异常只写日志 `continue`；`SubtitleMatchService.java:98-105` 空候选一律 `empty_result` | 候选 |
| 3 | 追剧日历（整周/整月 + 按日分段） | `ui/following_calendar_page.dart`、`core/following_calendar.dart` | 评估时无日历 | **已实施**（见 §0） |
| 4 | 配置列表提示本地来源失效 | `services/config_source_status.dart:40-77`（读 1 字节区分丢失/不可读）＋`ui/config_source_status_view.dart` | `ConfigAdapter.java:105-120`（手机版同理）只绑名称与点击；本地文件 `isFile()` 推迟到 `VodConfig.java:178-188` 加载时 | 候选 |

### B. 中等收益，涉及协议/结果接口，需要兼容旧数据

| # | 升级点 | PC 实现 | Android 缺口 | 状态 |
|---|---|---|---|---|
| 5 | 手动批量站点体检（只查首页/分类） | `services/site_self_check.dart:72-110`（≤100 站、并发 2、单站 30s）、`:258-296` | `ui/dialog/SiteHealthReportDialog.java:105-131` 只读已有记录（筛选/排序/清除/搜索），无主动检查 | 候选 |
| 6 | 同步凭据与普通设置分离授权 | `core/android_sync.dart:765-790`：`tmdb_config`/`ai_config` 未授权时**既不落库也不留存** | `bean/Backup.java:289` 中两者与普通偏好同列 `APP_PREFS`，读取端由对端 `options.isSettings()` 决定（`Action.java:368-402`） | 候选 |
| 7 | 局域网同步/推送接收授权 | `services/sync_server.dart:349-359`（开关＋网段）、`:437-460`（`isPeerAuthorized`） | `server/Nano.java:86-107` 直接进处理器；`Action.java:121-125`、`:186-203` 无设备白名单 | [已实施并双设备验收（TLS 配对）](KN-1-7-lan-receive-authorization-decision.md) |
| 8 | 配置载体解码护栏（`<pre>`／无标记 Base64／层数/大小） | `core/config_carrier_decoder.dart:12-37`（8 MiB、≤3 层） | `api/Decoder.java:34-40,72-80` 只认 `**` 标记 Base64 与 `2423` | 候选 |
| 9 | 更新检查缓存/限流/离线可解释 | `services/app_update_service.dart:272-355`（ETag/304、冷却窗口）、`:365-383`（旧缓存但标 `failed`） | `update/UpdateHttp.java:38-52` 只返回正文，非 2xx 抛 `HTTP <code>`；`Updater.java:175-191` 逐级兜底 | 候选 |
| 10 | 第三方元数据源（Bangumi）与手动匹配 | `core/meta_source.dart:13-58`、`services/bangumi_service.dart:132-180`、`ui/meta_match_dialog.dart:1-10`（入口 `browse_pages.dart:2078`、`push_detail_page.dart:206`） | 豆瓣评分已在用（`TmdbItem.getDoubanRating`、`personalDouban` 行），但 `app/src` 内 `bangumi` 命中 0；手动匹配仅 `TmdbSearchDialog.java` | 候选 |

### C. 高成本，建议单列实验

| # | 升级点 | 说明 | 状态 |
|---|---|---|---|
| 11 | 快进/拖动目标画面预览 | PC：`services/player_thumbnail_service.dart:96-133`＋`ui/player_overlay.dart:407-423`（加载中/不可用降级）。Android：`mobile/VideoActivity.java:10222-10227`、`leanback/VideoActivity.java:9421-9427` 只有方向图标与时间。需要第二解码或帧提取、Surface/生命周期、带鉴权 URL，静态阶段无法承诺收益与成本 | 候选 |

## 2. 已核实「不要重做」的部分

- **站点健康排序/分阶段报告**：`SiteHealthStore.java:42-135`、`SiteHealthReportDialog.java:115-131,298-318` 已覆盖；PC 点阵只是形态差异。
- **日志诊断**：`DebugLogDialog.java:31-55`、`server/process/DebugLogs.java:235-237,391-394` 已有分类/搜索/下载/AI 诊断/trace 筛选。
- **字幕源、AI 字幕翻译、实时字幕、Basic Pitch 音高、双字幕、弹幕调节**：均有 Android 实现，不迁移。
- **一键同步、观看记录增量＋墓碑、远程托管授权、APK 摘要与签名校验、更新前备份、镜像分发**：已覆盖。
- **片段跳过三源七类**（INTRO-SKIP-02）：已落地，只有第 1 项「按源开关」是增量。

## 3. 每项的注意点（实施前必须明确）

- **第 1 项**：不能只加 UI。缓存键只含剧集身份（`IntroSkipService.java:593-595`），关闭源后仍会命中旧聚合缓存，需要缓存隔离/失效策略。
- **第 6/7 项**：PC 的设备授权是 UUID/IP 匹配，**不是密码学身份认证**，对外不能宣传成「安全配对」；旧客户端兼容需显式策略。
- **第 8 项**：8 MiB／3 层／64 深度是 PC 当前策略，Android 应先用现有配置样本核定；既有 `2423` AES 与 `**` Base64 兼容性不得回归。
- **第 9 项**：最容易出的错是按渠道/URL 串用缓存，或把过期缓存当成「检查成功」。
- **第 11 项**：涉及第二解码路径与设备资源竞争，需单独立项、单独验收，不能混在普通 UI 改动里。

## 4. 排除掉的、证据不足的

- **音量归一化**：PC 有 `dynaudnorm` 开关；Android 未找到专用 UI，但 `androidx/media3/mpvplayer/MpvPlayer.java:1853-1854` 会加载 mpv 配置目录，可能已可用 mpv 配置等价实现，**未排除**，暂不列为缺口。
- **「配置→站源→解析」完整诊断链对等性**：未取得足以证明窄缺口的证据，不作为候选。

## 5. 验证边界（这份清单本身的）

- 本清单来自**源码静态核对**：没有运行 Android 或 PC 应用，没有构建，没有真机验收。
- 每条结论都有两端 file:line，复核时以当前代码为准；行号可能随代码漂移。
- 除第 3 项外，其余项都**没有**经过任何实现或验证。

## 6. 建议的推进方式

1. 先做 A 类 3 项（1、2、4）：改动局部、可单测覆盖、不碰协议。
2. B 类按「风险/收益」排序推进：6 → 7（安全相关，先明确威胁模型）→ 5 → 9 → 8 → 10。
3. 第 11 项单独立项，先做可行性 spike（第二解码路径开销、鉴权 URL、TV 遥控场景），再决定是否实施。
