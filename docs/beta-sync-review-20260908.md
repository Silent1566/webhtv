# dev1 合并 beta 最新代码与复评（2026-09-08）

## 恢复锚点

- **目标**：在保留 dev1 已提交未推送主题阶段 A-E 改动的前提下，合并 `origin/beta` 最新代码，审查合并后的全部本地改动与 beta 增量，修复问题、验证、复评通过后提交并推送 dev1，创建中文 PR 到 beta，最后重新拉取远端。
- **任务守卫**：`beta-sync-review-dev1-20260908`，模式 `standard`；开始时工作树干净，无受保护脏路径；范围 `app`、`docs`。
- **时间与基线**：2026-09-08 16:09 CST；开始时 `dev1@32ace88636f90f507288df859c325afd493b3e89`，远端 `origin/beta@253ffaa4f7908d0b90fa092f020cb20611aaadb5`；已执行 `git fetch --prune origin beta`。
- **合并证据**：`git merge --no-ff --no-commit origin/beta` 自动完成，无冲突；`git diff --cached --check` 通过；合并结果相对 beta 为本地主题阶段 A-E 和既有审查记录。
- **回滚**：合并提交前可 `git merge --abort`；完成后使用本任务 guard 创建的 recovery annotated tag 回退原子提交。

## 首轮审查结论

- beta 带入的 TV 触摸、TMDB 人物/详情和播放器自定义按钮改动已存在于 beta 历史中的独立评审记录（`docs/beta-sync-review-20260907-dev3-round2.md`、`docs/beta-sync-review-dev2-20260907-round3.md`）；本次按最终合并树复核文件边界、合并树无冲突和现有测试覆盖，未发现新的阻断问题。
- 本地主题阶段 A-E 覆盖 profile codec/validator、TweakCN 导入、传输边界、resolver、controller、mobile editor、TV selector/catalog/cache。首轮发现两个安全阻断：`ThemeTweakCnAdapter.parse` 直接解析输入，绕过 `ThemeProfileCodec` 的大小/嵌套上限；`ThemeTransfer.lookupPublic` 未拒绝 RFC 4193 IPv6 ULA（以及映射 IPv4/保留地址），分别可能导致导入解析资源耗尽和 DNS 解析到内网 IPv6 服务的 SSRF。

## 修复与验证

- 修复 `ThemeProfileCodec.validateJsonBounds`：在 JSON 树构造前统一限制 UTF-8 大小和括号嵌套深度；`ThemeTweakCnAdapter` 复用该边界检查。
- 修复 `ThemeTransfer.isPublicAddress`：拒绝 RFC 4193 ULA、IPv4-mapped 私网、CGNAT、保留/文档地址，并继续拒绝本地、链路本地、站点本地和组播地址。
- 新增 `ThemeImportExportTest` 覆盖超大/深嵌套导入、ULA、映射 IPv4、CGNAT、保留 IPv4/IPv6 与公网地址。
- 定向验证（修复后最终一遍）：
  - `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.theme.ThemeImportExportTest --tests com.fongmi.android.tv.theme.ThemeProfileCodecTest --tests com.fongmi.android.tv.theme.ThemeProfileMigrationTest --tests com.fongmi.android.tv.theme.ThemeImportSourceTest --no-daemon --console=plain`：通过（`BUILD SUCCESSFUL`）。
  - 修复前合并索引主题相关测试及 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：通过；修复仅影响共享主题安全边界，移动端最终测试已重新编译共享 Java。

## 修复后复评

- 修复后复评逐行覆盖 codec/TweakCN 两条输入路径、大小/深度限制、DNS 地址过滤、主题 profile 解析，以及合并后 beta 改动的无冲突最终树；两项首轮阻断均已闭环，未发现剩余阻断问题。
- 当前索引已暂存本次修复与本文档，待执行 guard finish 原子提交。

## 下一步

执行 `bash .codex/scripts/task_guard.sh finish`，随后推送 `dev1`、创建中文 PR 到 `beta`，最后 fetch 远端并核验 PR/分支状态。

## 关闭证据

- `task_guard.sh finish` 已创建提交：`e4b6e9e169ac155bb55540ed6de551c71d9287a0`。
- 已创建并推送恢复标签：`recovery/beta-sync-review-dev1-20260908/20260908173812-e4b6e9e169ac`。
- 已推送 `dev1` 至 `origin`；最终 `HEAD == origin/dev1 == e4b6e9e169ac155bb55540ed6de551c71d9287a0`。
- 已创建中文 PR：[#238](https://github.com/Silent1566/webhtv/pull/238)，目标 `beta`，当前状态 `OPEN`，GitHub `mergeStateStatus=CLEAN`。
- 已执行 `git fetch --prune origin` 和 `git pull --ff-only origin dev1`，结果为 `Already up to date`；工作树干净。
- **当前状态：** 本任务代码与审查交付已完成；无后续动作。
