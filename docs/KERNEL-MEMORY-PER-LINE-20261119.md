# KERNEL-MEMORY-PER-LINE-20261119 内核记忆按线路存

## Recovery anchor

- 目标：播放内核记忆的粒度从「整剧一个值」改成「每条线路一个值」。线路 1 手动切 EXO 不影响线路 2；线路 2 未命中记忆时用设置页默认；跨源聚合来的记录仍不沿用记忆。
- Lane：standard；分支 `dev1`；base HEAD `0730b05c0a2b74276882e12f68be9083ec1eed23`；受保护既有脏路径 `.scratch-goal/`。
- 已完成：`History` 线路表 + API、v48→v49 迁移、三处读写点、单元/契约测试、schema 49。
- 下一步：设备上验证真实 v48 库的迁移（安装新包 → 查表结构/行数/无崩溃），然后提交。

## 现象与根因

设备实测（cid=4，「今天是太阳雨」）：一条历史记录 `player=0`、`vodFlag=高清线路2`，设置页默认 `player=3`（MPV）。
应用调试日志（`/debug/logs.txt`）里的两条 switch-cause 说明一切：

```
kind=kernel cause=user-choice              from=MPV/hard to=EXO/hard position=2864   ← 用户在线路1 手动切 EXO
kind=kernel cause=history-kernel-before-play from=MPV/hard to=EXO/hard position=0      ← 线路2 起播被历史记忆改成 EXO
```

根因：内核只存了一份，而 `vodFlag` 会随换线路改写，于是线路 1 的选择被线路 2 继承。上一轮修的只是「跨源聚合记录不沿用」，管不到同源内换线路。

## 设计

- 新增列 `History.kernelByFlag`（JSON：线路名 → 内核常量，空串 = 无记录）。存整张表而非拆列的理由同 `subtitleSource`：不进 WHERE/ORDER BY、不参与去重，只在 `copy()`/迁移/备份里多一处。
- 读：`getKernelForFlag(flag)`；播放用 `getPlaybackPlayerOrDefault(flag)` = 跨源记录 → 设置页默认，否则命中线路记忆用它，未命中 → 设置页默认。
- 写：`setKernelForFlag(flag, kernel)`，只有用户手动换内核才调用（`rememberPlayerKernel(String,int)` / `rememberInlinePlayerKernel`）。
- 线路来源：两版 `VideoActivity.kernelFlag()`（`getFlag().getFlag()`）、`TmdbDetailActivity.inlineKernelFlag()`（`selectedFlag.getFlag()`）。

## 迁移（v48 → v49）

- `MIGRATION_48_49`：`ALTER TABLE History ADD COLUMN kernelByFlag TEXT NOT NULL DEFAULT ''`（`addColumnIfMissing` 保证幂等），并 `UPDATE History SET player = -1 WHERE player <> -1`。
- 为什么丢弃旧值：旧值写入时的线路会被之后的换线路覆盖成 `vodFlag`，硬塞回去就是把线路 1 的选择记到线路 2 名下——正是这次要修的 bug。丢弃后每条线路从零开始，下一次换内核即按线路记住。
- `player` 字段保留（Room 列 + 备份 JSON 兼容），但不再参与任何内核判定。

## 验证

- `HistoryTest`：11 例 0 失败（新增 4 例：线路隔离、旧整剧值不泄漏、copy/JSON 往返、脏数据容错）。
- `PlayerManagerSwitchCauseSourceTest`：10 例 0 失败。
- `VideoActivityLayoutTest`：159 例 1 失败，仍是既有失败 `leanbackPlaybackLoadingIgnoresStaleReadyBeforeNewPlayerStarts`（base `8cbf5faa5` 起即失败，与本次无关）。
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：BUILD SUCCESSFUL。
- `app/schemas/com.fongmi.android.tv.db.AppDatabase/49.json` 已生成并包含 `kernelByFlag`。
- 设备迁移验证：见下方「设备验证」段。

## 设备验证（192.168.50.3:5555）

在设备上真实的 v48 数据库上跑了一遍升级（该库是用户自己用出来的数据，98 条历史）：

```
升级前  user_version=48  无 kernelByFlag 列  98 行  player 分布 -1:1 / 0:94 / 3:3
升级后  user_version=49  有 kernelByFlag 列  98 行  player 分布 -1:98
        kernelByFlag TEXT notnull=0 default=''   PRAGMA integrity_check = ok
```

两个真实问题被这一步抓到并修掉：

1. 首次安装直接崩溃：`IllegalStateException: Migration didn't properly handle: History` —— 我给列加了 `NOT NULL`，而实体字段未标 `@NonNull`，Room 的期望表是 `notNull=false`，`validateMigration` 直接拒绝升级（库回滚到 v48，数据未损）。改为 `TEXT DEFAULT ''`（可空）后升级通过。
2. 迁移前 `player` 的 94 个 EXO 值全部清成 `-1`：这是设计选择（见「迁移」），不是数据丢失事故。

升级后应用正常启动（`HomeActivityCurrent`），崩溃缓冲区 FATAL 计数 0。

## 回滚

`git revert` 本提交；数据库会停在 v49（多一个空列、旧整剧值已被清空），如需完全回退到 v48 行为需同时回退迁移并把 `player` 恢复——由于旧值已被清空，这一点无法自动还原，属于一次性可接受的语义变更。