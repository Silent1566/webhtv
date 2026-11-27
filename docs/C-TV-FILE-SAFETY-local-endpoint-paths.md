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
