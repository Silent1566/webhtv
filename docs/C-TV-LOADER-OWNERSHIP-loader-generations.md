# C-TV-LOADER-OWNERSHIP：共享 loader 世代隔离

## Jar 子项：实施计划

对应 [FongMi/TV 合入台账](upstream-player-dependency-merge-assessment-2026-11-08.md#c-tv-loader-ownership--共享loader-owner世代取消和清理隔离优先)。本轮只做 App 层 Jar；调用方配置字段发布、JS 模块缓存、Python、Cat 及运行时升级不属于本单元。

- 本地起点：`Silent1566/webhtv dev2@43144f1d8f7150f2c05441ef3bd0d445312353fa`，工作树干净。用户继续上一轮明确推荐的 Jar 红绿单元；不推送。守卫：`C-TV-LOADER-OWNERSHIP-JAR`（upstream）。
- 问题：`BaseLoader.clear` 排队清理同一个 `JarLoader`；`JarLoader.getOrCreateSpider` 在初始化后直接向共享缓存发布，避不开与 clear 的交错。Jar 的 dex、代理方法及 recent 也需使用同一 owner，不能混读写。
- 验收：用屏障阻塞旧初始化 A，清理后让 B 加载同 key，再放 A。A 不返回旧实例、不发布且只销毁一次，B 保持可取。单调用中断但 owner 未退役时，保留成功缓存、不给中断调用返回实例的现有行为。保留同源下载互斥、串行清理/awaitClear、命名空间、recent、两次初始化尝试、调试日志与转译环境 dex 保留。

### 设计依据（2026-11-28，UTC+8）

| 等级 | 源码或资料 | 决策影响与局限 |
| --- | --- | --- |
| A | [FongMi/TV 8b499e0972b93f6173eac0e0414485c6b894294a](https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a.patch)，5 文件，尤其 BaseLoader / JarLoader；[冻结树 SpiderLoader](https://github.com/FongMi/TV/blob/4512b4c9f82534ff66acfde06ccdd68f35b074f0/app/src/main/java/com/fongmi/android/tv/api/loader/SpiderLoader.java) | 先交换 owner、短锁门控发布、锁外清理。上游提交说明称 22 项回归，不能当成 WebHTV 结果。冻结树保留 retired/closed 设计。台账后续 `c1c470b84e8111b0660316e842a9e4cb18fd83af` 转向新 runtime 的退出清理，暂缓，不移植；按 hash/标题查公开问题未取得足以改变本单元的复现或反转证据。 |
| A | [OpenJDK 21 FutureTask.cancel](https://github.com/openjdk/jdk/blob/jdk-21%2B35/src/java.base/share/classes/java/util/concurrent/FutureTask.java#L164-L185) | cancel(true) 通过中断请求停止，不保证已运行的初始化不再完成或产生副作用。发布必须独立判定 owner；本轮不强杀脚本、不换线程池。 |
| B | [Glide v4.16.0 Jobs.removeIfCurrent](https://github.com/bumptech/glide/blob/v4.16.0/library/src/main/java/com/bumptech/glide/load/engine/Jobs.java#L26-L31) | 异步完成处按当前对象身份变更状态，避免旧任务覆盖/移除同 key 新任务；不复用其缓存实现。 |
| A | 本仓 `BaseLoader.clear/awaitClear/parseJar`、`JarLoader.clear/load/parseJar/getOrCreateSpider`、`Download.download`、`Path.jar`、`JarLoaderTest.completedInitAfterCancellationIsCachedButNotReturned`、死锁修复 `4821c047149` | 维持锁外等待与串行清理；本地下载直接写目标文件，owner 切换不等于磁盘隔离。目标文件锁跨 owner 保持，避免同一缓存被并发截断、删除或构造 dex 时被重写。 |

没有新增并发算法、播放器性能或设备能力主张；论文/基准及设备论坛不比实际源码和确定性屏障更能决定本阶段。无依赖、ABI、制品、授权或播放器契约变化，不声称性能提升。

**取舍：** 维持原样则留下清理后的旧发布；原样移植上游会改 Node/JS/Python runtime，并丢失本地防死锁、网盘插件下载和转译 dex 的约束；选择仅适配 Jar：清理请求同步退役并换 Jar owner，旧实例仍经原队列异步清理。发布/退役使用短状态锁，jar 文件下载继续按目标互斥，插件初始化、销毁和等待不持状态锁。

**实施顺序：** JarLoaderTest 单独红灯；BaseLoader/JarLoader 最小实现；mobile/leanback ARM64 Debug 定向测试（Jar、清理队列、命名空间及 recent），并复核清理与读写顺序。风险护栏通过后由守卫单提交并创建本地 annotated 恢复标签。回滚：对该单提交 `git revert`，无配置迁移或文件格式迁移。

**红灯：** Leanback ARM64 屏障单测用原实现运行，Gradle exit 1、本次 XML 1 项 1 失败。释放旧初始化后旧实例销毁计数为 0（期望 1），不是超时/编译/旧报告。

## Jar 子项：验证记录

- 清理请求同步退役并交换 Jar owner，旧 owner 仍按原队列销毁；缓存整理在旧清理结束前仍可看见旧 owner 的活跃 JAR 键。`parseJar` 和 recent 更新使用同一个快照。退役后禁止 dex、代理方法、recent 和 Spider 的新发布；迟到 Spider 只销毁一次。对转译设备继续保留先前装载以及退休交错中新打开的 dex，避免 ART 卸载崩溃。
- 按实际缓存文件互斥下载、校验及打开 dex；插件初始化和销毁不持该锁。复核保留无 MD5 的 HTTP 下载、本地文件、校验命中优先级，以及空/非 JAR 地址不触碰缓存路径。没有实际联网下载或设备 Dex 装载基准；此处依据源代码和已有路径约束。
- ARM64 Debug 双端最终验收：`testLeanbackArm64_v8aDebugUnitTest` / `testMobileArm64_v8aDebugUnitTest`，各 `--tests com.fongmi.android.tv.api.loader.*`，`--offline`。最终构建 exit 0，Leanback **60/60**（12 类）、Mobile **49/49**（11 类），各含 Jar **9/9**；无失败或跳过，XML 晚于本次开始时间（2026-11-27T17:05:22Z）。
- Windows 初跑时 Leanback 的 11 条 JS/Python 测试在 Robolectric 初始化阶段缺少 `conscrypt_jni`。最终 JVM 命令临时使用 `JAVA_TOOL_OPTIONS=-Drobolectric.conscryptMode=OFF`，未改 fixture 或断言，11 条均实际执行通过。非设备/真实站源结论。
- 此阶段只隔离进入旧 Jar owner 后才完成的初始化；不处理尚未进入 loader 的旧配置任务，不承诺强行中止旧插件。JS/Python/Cat 的 owner 与共享 QuickJS 缓存还未调整。

**Recovery anchor：** Jar 子项达成双端验收，以此逻辑单元的守卫提交和恢复标签为交付点。唯一后续候选：单独验证 QuickJS 旧清理清掉新模块缓存的竞态，先确定缓存归属再交换 JS loader。
