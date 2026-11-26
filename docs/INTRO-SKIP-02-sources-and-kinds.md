# INTRO-SKIP-02 · 片段跳过：新增数据源与片段种类

状态：实施中　分支：dev2　起始 HEAD：144d06b722fcfdd6eeb1e5ba7fe28624aab70304
日期：2026-11-26

## 1. 目标与验收

对齐 PC 版（`webhtv-pc/dev2` @ `ce522cd4c13a2afa689c524d11041280c91d6f10`）的片段跳过能力，在 Android 版上：

1. 新增第三数据源 **TODB PlayBack**（`POST https://playback.theotherdb.org/api/metadata`），与 IntroDB、TheIntroDB 并行查询，沿用现有 5s 总预算与三态缓存语义。
2. TheIntroDB 端点从 `v3/media` 升级到 **v4**（v2/v3 已被上游标记弃用/冻结）。
3. 片段种类从 4 类扩到 **7 类**：回顾、片头、片尾、预告、**彩蛋（片尾之后）**、**片内广告**、**中场休息**；位宽与 PC 的 `SkipKindBits` 一致（1/2/4/8/16/32/64），出厂默认仍是回顾+片头+片尾（=7）。
4. IntroDB 的 `post_credits` 字段按 PC 实测契约映射成「彩蛋」，不再猜 `preview/next_episode/trailer`。

验收：

- 单元测试覆盖：新 kinds 解析、TODB 解析（秒→毫秒、`chapter` 丢弃、`version_runtime` 作参考时长）、彩蛋按「距结尾」折算、片中段与开头段不互相去重、旧位掩码偏好不变。
- 位掩码兼容：已存的 `intro_skip_kinds` 值（0..15）语义不变。
- 播放层行为不变：只有「尾部段」能按「本集看完」处理；片中段永远是有界 seek。

## 2. 现状（Android）

- `IntroSkipService`：两家 provider、5s 总预算、`isCacheableResponse` 三态缓存、尾部段按「距结尾距离」折算（容差 2s / 漂移上限 5min / 开放结束 5s）。
- `IntroSkipPlan`：只有 `openings` / `endings` 两个桶，`fromResolved` 把「非开头段」一律塞进 `endings`。
- `Segment.Kind`：INTRO/RECAP/OUTRO/PREVIEW；`isOpening()` 即判定开头，`trailing = OUTRO || PREVIEW`。
- `Setting`：`INTRO_SKIP_KIND_RECAP|INTRO|OUTRO|PREVIEW = 1|2|4|8`，`DEFAULT = 7`。
- 设置入口：TV `SettingAdActivity`、手机 `SettingAdFragment` → `IntroSkipKinds.show()` 多选框。

## 3. 最佳实践证据

| 证据 | 位置/地址 | 结论 | 采用 |
| --- | --- | --- | --- |
| PC 类型与位宽 | `webhtv-pc/dev2:apps/desktop-flutter/lib/core/skip_plan.dart:19-138` | 7 类；位 1..64；`dedupeGroup` 分 0/1/2 三组；尾部段才走「距结尾」 | 采用 |
| PC 尾部段折算 | 同上 `:466-517` | 与 Android 现有 `createTrailing` 同构（2s/5min/5s 三个常量一致） | 复用现有实现 |
| PC 三源并行 + 三态缓存 | `.../lib/services/intro_skip/registry.dart:104-179` | 半份结果不缓存；空查询可缓存 | 复用现有 `isCacheableResponse` |
| IntroDB 契约 | `.../introdb_provider.dart:1-105`；`docs/reference/intro-skip-data-sources.md:22-48` | 4 个顶层对象 `recap/intro/outro/post_credits`，无预告字段；参考时长取 `duration_ms/runtime_*` | 采用 |
| TheIntroDB 契约 | `.../theintrodb_provider.dart:1-95`；PC 文档 `:52-76` | v4；各类型为数组；`start_ms:null`=从 0，`end_ms:null`=到结尾；不下发参考时长 | 采用（端点升级 v3→v4） |
| TheIntroDB 官方 OpenAPI | `https://theintrodb.org/openapi.yaml`（HTTP 200，本日读取） | `v4/media` 仍在；v2/v3 弃用（维护者 issue `varunsalian/debrify#108`，2026-10-17：v2/v3 冻结并计划移除） | 升 v4，不留 v3 回退 |
| TODB 契约 | `.../todb_provider.dart:1-107`；PC 文档 `:79-128` | POST JSON；`items[].marker[]`；`time_*`/`version_runtime` 单位是**秒**；`chapter` 必须丢弃；免鉴权实测可用 | 采用 |
| 不做 SponsorBlock 分类 | PC 文档 `:174-179` | YouTube 插播位分类法，VOD 无数据源，加了是一排永远空的开关 | 明确不做 |

未决/边界（如实记录）：

- TODB 文档写 `Authorization: Bearer`，实测免鉴权；若将来强制鉴权，该源退化为「没答上来」（不可缓存），另两家不受影响。
- 不新增「按数据源开关」的 UI：Android 端今天没有源选择器，PC 的三源默认位以「全部启用」等价落地，避免把改动扩到两套设置布局。

## 4. 方案比较

| 方案 | 取舍 |
| --- | --- |
| A 不改 | 保持 4 类 2 源；彩蛋/广告/休息永远缺失，且 TheIntroDB v3 随上游移除而失效 ⇒ 否决 |
| B 照搬 PC 实现 | PC 有完整 `SkipPlan` 重写与手动标记叠加、源位掩码 UI；Android 已有同构实现，整段重写会带来无收益回归风险 ⇒ 否决 |
| C 窄适配（采纳） | 只在现有模型上加「第三桶 + 3 个 kind + 1 个 provider + 端点升级」，复用既有折算/缓存/确认框逻辑 |

## 5. 落地改动

- `IntroSkipService`：`Kind` 扩到 7；新增 `isMidRoll()/dedupeGroup()`；`create()` 与 `overlaps()` 改用分组；`IntroSkipPlan` 增加 `middles` 桶与 `getMiddles()`，`getAll()` 三桶混排；新增 `fetchTodb/parseTodbRaw`；IntroDB 加 `post_credits`；TheIntroDB 升 v4 并识别 `post_credits`。
- `Setting`：新增 3 个位常量并纳入 `INTRO_SKIP_KIND_ALL`；`DEFAULT` 保持 7。
- `IntroSkipPlayback`：`isKindEnabled` 覆盖 7 类；「本集看完」判定仍只对尾部段生效。
- `IntroSkipKinds`：多选框、文案、确认提示扩到 7 类。
- 资源：`values/strings.xml` 与 `values-zh-rCN/strings.xml` 新增 6 条文案。

## 6. 回滚

单提交、无数据迁移：`git revert` 该提交即可。旧位掩码值在回滚后仍被原 `ALL` 掩码夹取，不残留非法位。

## 7. 验证记录（2026-11-26）

- 定向单测：`.gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests IntroSkipServiceTest --tests IntroSkipPlaybackStateTest` → **BUILD SUCCESSFUL in 10m 30s**。
- 结果（`app/build/test-results/testMobileArm64_v8aDebugUnitTest`）：`IntroSkipServiceTest` **33 例**、`IntroSkipPlaybackStateTest` **8 例**，**0 失败 / 0 错误 / 0 跳过**。
- 新增用例（8 例，全部通过）：TODB 秒→毫秒与 `chapter` 丢弃、IntroDB `post_credits`→彩蛋、片中段与开头段同起点不合并、彩蛋按 `version_runtime` 折算、三桶混排时序、`kindFromWire` 白名单、片中广告不按「本集看完」、彩蛋贴结尾仍算看完。
- 兼容性：`INTRO_SKIP_KIND_DEFAULT` 仍为 7，旧位掩码 0..15 语义不变（新位只在用户主动勾选时出现）。
- 未做：设备端实测（未改播放器引擎，只改片段数据与判定类别；TODB 为新增网络源，失败按「没答上来」降级，不影响另外两家）。

## 8. Recovery anchor

- 目标/验收：见 §1。
- 已完成：§5 全部改动 + §7 验证。
- 未验证：真机三源联通（TODB 属实测免鉴权的补充源）。
- 下一步：提交并创建恢复标签，把提交/标签坐标写进交付摘要。
