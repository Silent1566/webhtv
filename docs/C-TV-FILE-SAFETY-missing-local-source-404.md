# C-TV-FILE-SAFETY（404 子项）：本地文件端点缺失时返回 404

## Recovery anchor

- 目标／授权：2026-11-27 用户批准「继续」，实施台账所列 404 子项的窄适配；只改缺失文件这一分支，不动权限／I-O 异常、路径授权、Range 与下载头。
- 基线：dev2@160af2450e351f3b4863729a5725ac2291e3bceb，初始工作树干净，无保护脏路径。
- 范围：server/process/Local.java、新增 Leanback 端点状态测试、本文件、稳定索引。
- 红灯：F:/temp/webhtv-local404-red-94564.log，3 项中 1 项预期失败——missingLocalFileReportsNotFoundInsteadOfServerError 得到 INTERNAL_ERROR 而非 NOT_FOUND；存在文件与目录列表两项当时已通过，作为回归护栏。
- 实施：Local.java 只改「既非目录也非文件」一条分支为 Nano.error(Status.NOT_FOUND, "File not found")，并移除因此失效的 FileNotFoundException 导入；catch 分支、路径解析、Range、下载头、上传／删除均未改。
- 绿灯：F:/temp/webhtv-local404-green-96620.log，Leanback 端点状态 3/3（缺失 404、存在文件 200 且内容正确、目录仍返回列表）；Mobile 服务器 process 全组 12 类 100 项含 ProxyRangeResponsePolicyTest 全部通过；双端 ARM64 Debug 生产与测试 Java 编译成功。
- 边界：未在真实局域网客户端或浏览器管理页手工复验，未覆盖权限／I-O 失败的实际触发（该路径仍返回 500 未改）；本单元无依赖、ABI、包体或性能变化。
- 下一动作：以 Task-Guard: C-TV-FILE-SAFETY-LOCAL-404 原子提交并立即创建本地注释恢复标签；不推送。

## 决策与证据（2026-11-27）

| 来源 | 等级 | 本地决策 |
| --- | --- | --- |
| [FongMi/TV@0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1](https://github.com/FongMi/TV/commit/0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1) | A | 单文件单分支：`if (!file.isFile()) return Nano.error(Status.NOT_FOUND, "File not found")`。仅移植该分支，不携带同提交的 resolveFile 重构。 |
| 本地 Local.java:47–56、Nano.java:67–81、Nano.java:140–149 | A | 当前缺失文件抛 FileNotFoundException 后被统一 catch 成 INTERNAL_ERROR（500）；静态资源分支已用 NOT_FOUND，证明 404 语义在本仓已有先例。 |
| Android/HTTP 语义 | A | 资源不存在用 404，服务端故障用 5xx；两者不可混用，否则客户端与缺依赖诊断误判。 |

- 方案：不改（缺失文件继续报 500）；照搬上游（会带入未审查的路径解析重构）；选择只改「既非目录也非文件」一条分支。
- 保留合同：存在文件仍 200 且内容／MIME／Range／下载头不变；目录仍返回列表；权限与 I/O 异常仍 500；上传／删除／局域网鉴权不动。
- 影响面：无依赖、ABI、包体或性能变化；单方法可回滚。
- 不适用资料：无算法、并发或性能主张，论文与设备基准不能改变 HTTP 状态码语义。

## 验收与回滚

- 先红灯证明缺失文件当前不是 404；修复后缺失文件 404、存在文件 200 且内容正确、目录仍返回列表。
- 定向 Leanback 单测加双端 ARM64 Debug 生产／测试编译；不需要设备矩阵或网络设备。
- 唯一回滚单元：Task-Guard: C-TV-FILE-SAFETY-LOCAL-404 定位提交与标签，单独 revert。
