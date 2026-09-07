# C13：dev4 合并 beta 最新代码并复评全部未推送改动

## Recovery anchor

- **目标：** 将 `origin/beta` 最新代码合入 `dev4`，复评本地已提交未推送的手机版“外观与语言”入口，以及 beta 在 `dev4` 上次 PR 后新增的播放器、站点主题和冲突修复；发现问题即最小修复并重新验证，随后提交、创建恢复标签、推送 `dev4`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
- **任务守卫：** `beta-sync-review-20260907-dev4`，模式 `standard`，范围 `app/**`、`docs/**`、`gradle/**`、`gradlew`；开始时工作树干净，无受保护脏路径。
- **本地基线：** `dev4@c344741692b3cb4e7dda473a8dbebb433ea50e65`；唯一未推送提交为手机版设置页复用既有 `AppearanceDialog` 的 C13 变更。
- **beta 目标：** `origin/beta@cc88e278a8ddc2088a82a68dbf1671e419606a29`；共同祖先为 `03f600acc5672c858814e4eab720987b3980e027`。
- **合并状态：** `git merge --no-commit --no-ff origin/beta` 自动完成，零未合并路径；本地 C13 两个文件与 beta 新增路径零重叠，因此未发生内容冲突。
- **回滚：** 未提交时可 `git merge --abort`；提交后按本任务恢复标签整体回退。合并前已验证 C13 恢复点为 `recovery/C13-mobile-appearance-language/20260907020029-c344741692b3`。

## 评审范围和结果

### 本地已提交未推送改动

- `c344741692b3cb4e7dda473a8dbebb433ea50e65` 将手机版设置页分散的界面缩放、主题色、图片尺寸和语言四个入口合并为一个“外观与语言”入口。
- 该变更只改 `SettingFragment.java` 与 `fragment_setting.xml`，调用仓库中已经存在并由 TV 端共用的 `AppearanceDialog`；选择后的语言/主题事件、界面重建和图片尺寸刷新仍由该对话框负责。
- 与 beta 的新增文件列表无交集，合并结果相对 beta 仍只有上述两个功能文件和本轮修复/记录，未发现行为回退。

### beta 增量

- `dev4` 上次已合入 beta 的 PR #220 对应 `c78b7cb11550209b2c0f884441e4a0fb65784e05`。其后的播放器首播续播、MPV 时长恢复、进度绑定、站点主题及 PR #218 冲突处理均已有独立提交/恢复标签和验证记录；本轮复核最终树、对应测试和合并血缘，不重复已成功的设备场景。
- 广告规则批量导入/启停来自已合入的 PR #219，最终实现和测试未被后续合并改写；本轮聚焦测试重新覆盖导入存储、规则来源、批量操作和焦点保持。
- PR #218 的冲突修复 `016edd1d50fede43e119613737fe2288c99e45ba` 已用双端 Arm64 Java 编译验证主题事件语义；最终树保留 mobile 的 `LANGUAGE/THEME` 和 Leanback 的 `LANGUAGE/UI_SCALE/THEME`。

## 评审发现与修复

1. **`gradlew` 可执行位回退。** beta 历史把包装器模式带回 `100644`，会让标准 `./gradlew` 调用失败；恢复为 `100755`，脚本内容和镜像配置不变。
2. **两项源码测试使用过窄字符串断言。** 实现已正确：Leanback 为 `LANGUAGE/UI_SCALE/THEME` 三态重建，HLS 详情先重新解析最新条目再显示 `current.detail()`；旧测试分别只接受相邻 `LANGUAGE/THEME` 和 `item.detail()`，造成假失败。断言改为验证必要行为而非局部变量名/条件顺序。

## 验证与最终复评

- 第一轮聚焦 Mobile Arm64 单测共 39 项：37 通过、2 项上述陈旧断言失败；同时 Mobile Java/资源编译通过。
- 修复后以同一组目标重新运行：39/39 通过，`BUILD SUCCESSFUL in 38s`。覆盖 `SiteDialogThemeSourceTest`、`PlayerPlaybackRegressionSourceTest`、`AdRuleManageDialogLayoutTest`、`MobileAdRuleManageDialogTest`、`ImportedAdRuleCandidateStoreTest`、`UserAdRuleSourceTest`、`MobileSiteAdapterStyleTest`、`MpvMainThreadPropertySourceTest`。
- Leanback Arm64 Java 编译通过，`BUILD SUCCESSFUL in 31s`。
- `git diff --cached --check` 通过；无未合并路径；C13 与 beta 增量零路径重叠；`gradlew` 为可执行模式。
- 修复后再次审查最终树：未发现剩余阻断问题。未重复已有的 SDK 28 站点配色探针、MPV/EXO 真机播放和核心切换，因为相关提交已有当前代码覆盖的成功设备证据，且本轮没有修改这些生产实现。

## 完整 beta 提交台账（相对共同祖先）

| 完整 commit | disposition |
| --- | --- |
| `60fc55e18cf755d25dc9c140908188fb21898c44` | 已由 PR #219 合入；广告规则批量导入/启停，最终树与聚焦测试覆盖 |
| `1a95bf977543bca2de9f62e8005506520ae8608b` | 已合入；主题事件即时重建，后续冲突修复保留语义 |
| `11256015f307d5abfaa28f1b8338f99cb95f1207` | 集成承载，已由 PR #219 合入 |
| `99d0dad351f851bc0dd58ee0de37f3a3e996e6d3` | dev1 合并承载，最终树已复核 |
| `74e60ef606ce6b64f5e3dab21230213e180cc504` | 已合入；移除 Mobile 重复主题订阅，BaseActivity 仍负责重建 |
| `4242a06987b1080a923e14800380a866c95e8f67` | 站点主题中间修复，已被后续窄实现取代 |
| `85217abc610cad7d5c623f365ecde8197bd3beb1` | 构建清理中间提交，最终树保留所需结果 |
| `c1d8b10807693baba69bbcc51177e704d34898df` | Gradle 下载排查中间提交，最终配置由后续提交取代 |
| `8d104591b32732f08ec8dfd871c243592f285155` | Gradle 下载排查中间提交，最终配置由后续提交取代 |
| `3a2d7723666eb2f331bec2a2b22cbcaa3c617aef` | 站点主题中间实现，已被后续修复取代 |
| `1558c90fb1c27cbb0f2325ea845640288986a527` | 站点主题中间修复，已被后续窄实现取代 |
| `66b5505282eb3de95538a1eab39ad9196d27ba22` | 已合入；Mobile EXO 首播续播位置一次注入，聚焦测试和编译覆盖 |
| `33d9ef2393747dc689fb735a9c91de2ca4f6c0cb` | 已合入；通用浅色弹窗 surface 跟随主题，最终资源编译通过 |
| `f627d99e88078ccd8760986cbae65cc4a3eb5adc` | 站点主题中间实现，已由 `7a380654...` 取代 |
| `7a3d815ba566ed5ce3e0a8aa76561ad5e67eaf00` | 已合入；Gradle 镜像和广告规则评审；包装器模式在本轮最终恢复 |
| `e6833fb8e068bc807529f8e98994b29294e5fae6` | 已由 PR #219 合入；Leanback 变量冲突修复 |
| `6b0490907da3e0b09a6563c1572cf283a3ae49d3` | 已合入；MPV 缺失时长恢复，聚焦测试和编译覆盖 |
| `7a380654329fa4800c55799440b5dd0798e22167` | 已合入；SDK 28 站点主题窄实现，历史设备探针与本轮聚焦测试覆盖 |
| `6a0b967dd533250d061e9457a93b4c15a724b5f5` | PR #219 集成承载，已合入 beta |
| `cb386895da22a8836bb5587cafff3ef10f89a4fa` | 已合入；播放器重建后进度绑定，已有模拟器证据，本轮编译覆盖 |
| `912208261e4e342ced009b1a0b71feed4855a01d` | dev2 合并承载，已由 PR #221 合入 |
| `c78b7cb11550209b2c0f884441e4a0fb65784e05` | PR #220 集成承载，是本轮增量复评起点 |
| `7a93ec7336b57dcf418c30b399862193e6991446` | PR #221 集成承载，已合入 beta |
| `016edd1d50fede43e119613737fe2288c99e45ba` | PR #218 冲突修复；双端主题事件语义与编译已验证 |
| `cc88e278a8ddc2088a82a68dbf1671e419606a29` | PR #218 最终集成；本次 beta 合并目标 |

## 下一动作

由任务守卫原子提交当前合并、两项测试修正、包装器模式和本文档并创建恢复标签；随后推送 `dev4`、创建中文 PR 到 `beta`，再拉取远端最新状态。
