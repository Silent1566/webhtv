# C-TV-FILE-SAFETY：本地端点路径边界

## Recovery anchor

- 目标／授权：2026-11-27 用户批准后实施；只加固 `/upload`、`/newFolder`、`/delFolder`、`/delFile` 四个端点的路径参数边界。
- 基线：dev2@f4214f9fc8b5dd37eab4a5a1f7450615f4c27b00，初始工作树干净。
- 范围：server/process/Local.java、新增 Leanback 边界测试、本文件、稳定索引。
- 当前状态：红绿闭环完成，待原子提交。

## 决策与证据（2026-11-27）

| 来源 | 等级 | 本地决策 |
| --- | --- | --- |
| 本地 Local.java:64–85（改前）、Path.java:102–106、Path.java:233–237 | A | 改前 `delete` 直接 `Path.clear(Path.root(path))`；`Path.root("")` 就是共享根，`clear` 递归删除含目录本身，故空串即删根；`upload`/`newFolder` 用 `Path.root(path, fn)` 拼接，`../` 可写出根外；缺失参数直接空指针。 |
| 本地 Manage.java:325–326、501–502、605–606 | A | 较新的管理端点已用 canonical 根包含校验，说明这是老端点遗漏而非设计意图，可直接对齐既有合同。 |
| FongMi/TV `5a905f87404ec803239edd3c3d6abce6a554f6a8`、`2ae956ad57b8f60246192e64e2e2af9b0d389b48`（台账登记） | B | 仅作方向参考；本单元按本仓既有 canonical 校验风格实现，不整提交照搬。 |

- 方案：不改（空路径可删根、可写根外）；照搬上游（会带入原子写/SAF 大改，超出本单元）；选择最小边界：四个端点统一走一个 canonical 包含校验，空值/缺失显式拒绝，拒绝删除根自身。
- 拒绝语义：不安全参数返回 `400` 并带原因，不再静默 `ok`，避免客户端误判成功。
- 保留合同：合法子目录的浏览、新建、上传、删除不变；`Range`/`ETag`/`Content-Disposition`、`/action` 鉴权、播放器与依赖均未改。
- 未纳入（明确残留）：`/file` 读取仍走 `Path.local`，绝对路径可读到根外；上传 ZIP 仍用共享 `FileUtil.zipDecompress`，条目路径未加固。两者都需单独单元，不在本次范围。

## 验收与回滚

- 红灯（改前）：空串路径删掉共享根、`../outside` 删除根外文件、上传 `../escaped.srt` 写到根外、`../` 建目录写到根外、缺失参数空指针；正常子目录操作当时通过。
- 绿灯：边界 6/6、端点状态 3/3、Mobile 服务器 `process` 12 类 100 项全部通过，双端 ARM64 Debug 生产与测试 Java 编译成功。
- 唯一回滚单元：Task-Guard: C-TV-FILE-SAFETY-LOCAL-PATHS 定位提交与标签，单独 revert。


## 读取边界子单元（2026-11-27）

- 授权与范围：用户批准 /file 远端共享范围隔离，保留真实回环内部读取。基线 dev2@1cfbafbe8f0e4795d5b370fe89c3249ee82b1785，起始工作树干净。只修改 Local、既有端点状态测试、本记录与稳定索引；不改公共 Path.local、写入端点、鉴权或播放器。
- 决策：不改会保留远端根外可读；完全禁用根外会破坏内部缓存。选择实际连接对端为回环才保留例外，其余只放行 canonical 根自身或带目录分隔符的子孙路径；根外 403，根内缺失仍 404，其他 I/O 异常维持 500。

| 证据与来源（2026-11-27） | 等级 | 决策影响 |
| --- | --- | --- |
| [FongMi/TV@0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1 的 Local.java](https://github.com/FongMi/TV/blob/0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1/app/src/main/java/com/fongmi/android/tv/server/process/Local.java#L206)，blob e6208dd39cdf08adb6a994e6c31c84585b784950 | A | 该快照 resolveFile 已使用 canonical 根内或真实 loopback 才允许读取，现有 404 单元只移植了缺失分支。借鉴快照，不将其错误记作 404 提交新增加的代码。 |
| 本仓 Local.java:40–54、Path.java:137–140、Manage.java:909–912 | A | 读取直接 Path.local，后者可绝对路径回退；管理端已有 canonical 根包含惯例。 |
| 本仓 Server.java:61–66、UrlUtil.java:46–52、ExoUtil.java:1109、CastDialog.java:78 | A | getAddress(String) 使用 127.0.0.1，内部 file URL 转换和字幕缓存需保留；对外投屏使用共享根相对路径。 |
| 锁定 nanohttpd 2.3.1 的 HTTPSession 构造器及 getRemoteIpAddress 字节码 | A | remoteIp 来源 InetAddress.getHostAddress 并直接读取该字段，不由请求头赋值。不信任 Forwarded、X-Forwarded-For、http-client-ip、remote-addr、Host。 |

- 兼容与反例：远端绝对／父级／同名前缀兄弟目录／目录列表／符号链接和伪造头／未知地址必须拒绝；真实 IPv4／IPv6 回环保留内部读取。按字面量地址识别避免 hostname/DNS 放行，不修改全局 Path.local。不宣称其他代理或整机文件服务已全面安全。
- 验收：先复现受控根外文件可读，再验 200/206/304/404/416、Range closed/suffix/open、ETag、中文下载头和目录列表；双端 ARM64 Debug 定向编译。不变更依赖/native/ABI，无算法或性能主张，论文／设备基准不改变本决策。
- 回滚：通过 Task-Guard: C-TV-FILE-SAFETY-LOCAL-READ 定位原子提交和恢复标签，单独 revert；下一动作是未改产品代码的红灯验证。
- 观察但不改动：Range 解析不支持后缀区间（bytes=-2 被解析为 0-2），属既有行为，与本单元无关，未纳入断言也未修复。

## 阻塞记录（2026-11-28 00:05）

- 已确认：修复前远端可读取共享根外文件，红灯 6 项成立（FORBIDDEN 期望、实际 OK）；既有上传／路径边界 8 项回归全绿，未受影响。
- 探针结论（本机 JDK21）：目录符号链接的 exists() 为 true，但 getCanonicalPath() 解析到链接自身而非目标；含冒号的子路径 new File(parent, child).getCanonicalPath() 抛 IOException；前导 "/" 拼接后被当作根内相对路径。
- 因此当前实现仍不达标：11 项中 6 项失败，两类原因——绝对 Windows 路径与回环读取都返回 500（期望 403 / 200），符号链接越界仍返回 200（该环境真实路径判定未生效）。
- 状态：Local.java、端点测试、本记录为未提交改动；任务守卫 C-TV-FILE-SAFETY-LOCAL-READ 保持 active；无提交、无标签、无推送。
- 下一步（唯一）：在测试辅助方法中输出 500 响应正文，定位真实异常类型与消息，再据此调整解析顺序；不靠猜测改代码。
## 收口（2026-11-28 00:23）

- 上节阻塞已解决。根因是本人在此前的按行编辑中把 Java 源码拼坏（字符字面量跨行、残留重复尾部），产品代码一度无法编译，我误读旧测试报告。已修正并确认编译通过。
- 最终实现：只在 /file 读取入口做边界判定。共享根内相对解析用 toRealPath 比对（存在时识别符号链接越界，不存在时用 canonical 字面判定），根外路径仅真实回环对端可读；非回环抛 SecurityException 由既有 catch 映射为 403。回环判定只接受 IP 字面量，localhost 等主机名不再放行。
- 绿灯（均为本次生成报告）：Leanback 端点 11/11、上传/路径边界 8/8；Mobile 服务器 process 12 类 100/100；Leanback 与 Mobile 生产及测试 Java 编译成功。
- 已覆盖：远端绝对路径、父级、同名前缀兄弟目录、目录列表、符号链接越界、伪造转发头、未知/无地址对端均拒绝；真实 IPv4/IPv6 回环保留内部读取；200/206/304/404/416、ETag、中文下载头与目录列表保持。
- 已知未改：Range 不支持后缀区间（既有行为）；本单元未在真实局域网客户端手工复验，未覆盖其它公共 ZIP/下载消费者。
- 回滚：Task-Guard: C-TV-FILE-SAFETY-LOCAL-READ 定位本原子提交与恢复标签，单独 revert。
