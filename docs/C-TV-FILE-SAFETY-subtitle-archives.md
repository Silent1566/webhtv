# C-TV-FILE-SAFETY：字幕 ZIP 安全解压

## Recovery anchor

- 目标／授权：2026-11-27 用户批准只加固字幕 ZIP 入口，不改变公共解压消费者、字幕下载、播放器或依赖。
- 基线：dev2@b5f851b109f78a023b9f61e7d1f52165f216eca1，初始工作树干净，无保护脏路径。
- 范围：SubtitleAssetStore、新增 SubtitleArchive、公共 JVM 与 Leanback 消费者测试、本文件和稳定索引。当前计划：先红灯 → 私有新目录解压 → 双端回归与独立复核 → 原子提交和注释恢复标签；本轮不推送。
- 当前状态：公开字幕入口红灯为 5 项中 4 项预期失败（越界、残留、旧 ASS 和无支持字幕）；候选双端 31/31 已通过，代码仅改字幕入口。下一动作是收集独立安全复核，再关闭发现与原子提交。

## 决策与证据（2026-11-27）

| 来源 | 等级 | 本地决策 |
| --- | --- | --- |
| [Android 官方 Zip Path Traversal](https://developer.android.com/privacy-and-security/risks/zip-path-traversal)，页面更新 2024-09-24，本轮取得完整文本 | A | 每个输出必须是目标目录子孙；目标目录要私有且为空。不能沿用可预测旧目录或将越界条目静默跳过。 |
| [FongMi/TV@b802f538cb25bab7252bc170279101b6e409cec4](https://github.com/FongMi/TV/commit/b802f538cb25bab7252bc170279101b6e409cec4)，父 4573249cd6679c6becca9957f6a011bf6c4a5afd | A | 只借鉴 canonical 输出根检查，不移入设置页／播放器大提交。上游越界 continue 不满足整包拒绝。 |
| [FongMi/TV@179cd159266121932ab570b5188b1f9fccf65461](https://github.com/FongMi/TV/commit/179cd159266121932ab570b5188b1f9fccf65461)，父为上一条 | A | 字幕归档限制 256 条、解压总量 128 MiB。保留本地正常字幕排序；不移入公共解压重载、32 MiB 下载改造或 UI 重构。 |
| 本地 SubtitleAssetStore.java:39–50、FileUtil.java:69–80、Path.java:210–217、AssrtSubtitleProvider.java:139–141、SubtitleMatchService.java:111–125 | A | 未校验条目路径，输出复用旧目录，底层复制吞异常；provider.resolve 已能传递异常给 MatchResult.error。维持 unzipAndPick 签名，用明确的运行时失败传递错误，不产生假成功或 null 后再崩溃。 |

- 不改：继续存在越界、无限解压、半成品和旧字幕误命中；原样上游：公共消费者扩散且错误被静默跳过；选择：字幕独立解压器，所有异常使本次工作目录被清理，成功只在本次目录选字幕。
- 固定技术边界：每 ZIP 至多 256 条（目录也计数）、单文件 32 MiB、实际解压总量 128 MiB、路径最多 32 层／1024 字符；使用实际读取量、CRC 与长度验证，不相信 ZIP 元数据。遇取消／损坏／限额／非法路径明确失败；不改变下载或网络限额。
- 文件安全：每次用内部缓存的新私有目录，不读取／删除旧候选目录；拒绝父目录段、绝对路径、盘符、反斜杠与重复规范目标，检查 canonical 子孙边界。只把普通文件写入新目录，不按归档元数据创建符号链接；失败清理不跟随符号链接，异常不会选取半成品。清理失败同样必须报告，不吞异常。
- 同一候选可并发且各自有独立工作目录；不清除正在播放的旧字幕。成功输出沿用应用缓存清理生命周期；不新增持久化结构或自动清理策略。
- 成本与兼容：仅 Java／文件 IO，minSdk 24，采用 java.io 与 java.util.zip 以避免新增 Android API 门槛；无依赖、ABI、网络或包体重构。正常多文件、中英文目录、ASS 优先及现有四种字幕格式保持。
- 补充资料：已读取官方平台及精确上游实现。OWASP Zip_bomb 链接本轮返回 404，不用于结论；没有新算法或性能主张，论文／设备基准不能改变路径与限额合同。精确安全提交与复现样本替代二手漏洞断言，其他文件安全子项保持未实施。

## 验收与回滚

- 先通过公开入口复现越界、损坏包返回旧文件与重复候选选旧 ASS；修复后检查包外零写入、旧输出不变、失败目录零残留和并发隔离。
- 纯 JVM 验证路径类型／重复、条目／单文件／累计限额、CRC／截断、取消、清理与正常边界；Leanback Robolectric 验证真实资源入口；Mobile／Leanback ARM64 Debug 定向构建，不要求播放器矩阵或网络设备。
- 唯一回滚单元：通过 Task-Guard: C-TV-FILE-SAFETY-SUBTITLE-ZIP 定位提交与标签，单独 revert。本阶段不开启其他本地服务或公共 ZIP 修复。

## Checkpoint 1：2026-11-27 22:18（UTC+8）

- 红灯：F:/temp/webhtv-subtitle-zip-red-48588.log，5项中4项预期失败；正常中文路径／ASS优先通过，确保未以安全为由丢掉功能。
- 候选：F:/temp/webhtv-subtitle-zip-green-90368.log，Leanback实际资源入口5/5，独立解压器13/13；Mobile独立解压器13/13，共31/31，无失败／错误／跳过，双端 ARM64 Debug 生产及测试 Java 编译通过。
- 已覆盖：越界与Windows路径、规范目标重复、条目／文件／总量、元数据伪造、CRC与截断、预取消、并发新目录、正常中文目录、无字幕、旧文件保留和失败清理。公共解压、下载、播放器和依赖保持原样。
- 当前仅有声明的六个路径变更；无提交或推送。独立只读代理 zip-security-review 正在复核；每brief实际运行1次。
- 独立复核（zip-security-review，run 1）判定 hand_back，实测两枚伪造目录包均被接受：空目录伪造 CRC、目录夹带压缩载荷而声明长度为 0。修复：目录条目与文件走同一实际字节／CRC／长度校验，读到首个字节即整包拒绝，校验通过后才建目录；新增两条伪造目录回归（原 13 → 15）。
- 复核另指出索引行被重复插入四处，已删除三处，仅保留主索引第 30 行；取消用例原名超出其实际断言，改为 preCancelledExtractionFailsWithoutCreatingAWorkDirectoryAndKeepsTheFlag。
- 最终验证：F:/temp/webhtv-subtitle-zip-final-96752.log，Leanback 解压器 15/15、真实资源入口 5/5，Mobile 解压器 15/15，共 35/35，无失败／错误／跳过，双端 ARM64 Debug 生产与测试 Java 编译通过。
- 残留边界（不宣称已完成）：取消仅覆盖线程预取消，未覆盖流读取中途中断；ZipFile 在条目限额前已打开并解析中央目录，故条目上限不是解析内存上限；未做 Android API 24 运行时测试；未覆盖其他公共 ZIP 消费者、下载限额或本地服务文件安全子项。
- 下一动作：以 Task-Guard: C-TV-FILE-SAFETY-SUBTITLE-ZIP 原子提交并立即创建本地注释恢复标签；不推送。
