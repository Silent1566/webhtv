# C-TV-FILE-SAFETY：上传 ZIP 接入加固解压

## Recovery anchor

- 目标／授权：2026-11-27 用户明确要求「把 Local 上传的 ZIP 分支接到已加固的解压器」。
- 基线：dev2@fd0b2fc10360338b572ab77bca638c6e3e1521a0，初始工作树干净。
- 范围：解压器由 subtitle.cache.SubtitleArchive 迁到 utils.SafeZip 并公开、新增 extractInto；Local 上传 ZIP 分支改用它；相关测试与索引。
- 当前状态：红绿闭环完成，待原子提交。

## 决策与证据（2026-11-27）

| 来源 | 等级 | 本地决策 |
| --- | --- | --- |
| 本地 Local.java:69（改前） | A | 上传 ZIP 仍走共享 FileUtil.zipDecompress：条目路径不校验、无条目/字节上限、异常被吞。这是字幕 ZIP 加固后剩下的最后一个入口。 |
| 本地 SubtitleArchive（上一单元，15 项测试覆盖） | A | 已具备路径、限额、CRC/长度、取消与清理能力，但只支持「新建私有目录」且为字幕包私有类，服务端直接复用会造成命名与包归属错误。 |
| 本地 FileUtil.zipDecompress 的其他消费者 | A | 仍被配置导入等路径使用，本单元不改共享函数，避免扩散到未审查的调用方。 |

- 方案：不改（上传仍可写出根外）；就地让服务端调用字幕类（包私有且语义错位）；选择把解压器迁到 utils.SafeZip 并新增 extractInto（解压进已存在目录，失败只删本次新建条目），两处消费者共享同一套校验。
- 行为保持：字幕路径仍是「新建目录后挑选」；上传仍是「解压进用户选择的目录」，目录内已有文件保留；共享 FileUtil.zipDecompress 未改。
- 成本：纯 Java 文件 IO，无依赖、ABI、包体或性能变化；重命名只影响两个消费者，已由测试覆盖。

## 验收与回滚

- 红灯：上传含 ../ 条目的 ZIP 会写到所选目录之外（首次断言写错位置导致假绿，修正为检查共享根后复现）。
- 绿灯：SafeZip 18/18（双端）、上传边界 8/8、字幕入口 5/5、端点状态 3/3、Mobile 服务器 process 12 类 100 项全部通过，合计 152 项，双端 ARM64 Debug 生产与测试 Java 编译成功。
- 未覆盖：真实浏览器管理页手工上传；共享 FileUtil.zipDecompress 的其它消费者；下载读取仍走 Path.local。
- 唯一回滚单元：Task-Guard: C-TV-FILE-SAFETY-LOCAL-UPLOAD-ZIP 定位提交与标签，单独 revert。