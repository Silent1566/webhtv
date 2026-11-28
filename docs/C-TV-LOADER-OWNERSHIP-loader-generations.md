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

## QuickJS 子项：模块缓存归属

本地起点：`Silent1566/webhtv dev2@749806c50d9aed55d278a1fcbc0b20bc6af6cd40`，工作树干净。按上一轮明确的建议先隔离缓存，本单元不交换 BaseLoader 的 JS owner，也不改 Python、Cat、原生桥或依赖。

| 等级 | 核对证据（2026-11-28，UTC+8） | 取舍 |
| --- | --- | --- |
| A | 本仓 `JsLoader.clear`、`quickjs.crawler.Loader.spider`、`Spider.createCtx/createObj`、`Module.get/fetch/clear`；临时 JVM 屏障探针两次均观察新模块源读取 1→2，已有 Spider 和独立缓存不变 | 问题是清理的缓存归属；不是 JNI 销毁或初始化对象被旧 loader 覆盖。当前 BaseLoader 未切换 JS owner，探针模拟未来的实例交叠，不是真实设备故障证据。 |
| A | [FongMi/TV 8b499e0972b93f6173eac0e0414485c6b894294a](https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a.patch)、[冻结树 JsLoader](https://github.com/FongMi/TV/blob/4512b4c9f82534ff66acfde06ccdd68f35b074f0/app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java) | 最终上游具有每实例 QuickJsRuntime.Session，clear 只清该 session；借用归属不变量，不移植 runtime、整个退役协议或其生命周期后续（见 Jar 研究记录）。 |
| A | [Android LruCache](https://developer.android.com/reference/android/util/LruCache) 与本机 Android SDK 中 `android.util.LruCache` 实现；本仓 `Module` 每对象一个 LRU，容量 50 | 保留现有缓存实现、容量、查找/取源规则和 JS 路径。没有新增并发算法、原生、性能或设备主张，不需独立论文或设备 benchmark；成熟实现的按会话隔离可直接对照上游。 |

**取舍与实施：** 保留全局缓存会使旧清理驱逐新资源；原样移植会引入本仓未使用的 runtime/session。选择让现有 `quickjs.crawler.Loader` 持有一个 Module，明确传给由它创建的所有 Spider；入口脚本与 import 使用同一个 Module，`JsLoader.clear` 只清自己的 Module。保留公共双参 Spider 构造入口及其旧兼容行为。此阶段不承诺阻止旧初始化回写旧缓存。正常单 owner 的 LRU 容量仍为 50，不新增依赖/ABI/制品，不声称速度改善。

**验收/回滚：** Leanback Robolectric 屏障驱动真实 Loader/Spider 读路径与 JsLoader 清理，仅替换原生桥和资源 I/O，要求旧清理前后新模块只读一次、入口和 import 共享、新实例已建 Spider 不受影响、旧缓存仍被清理。双端 ARM64 Debug 定向验证、旧 JS/Python 日志测试及 QuickJS 代理兼容护栏；一个本地守卫提交，可 `git revert`，无数据迁移。

**红灯：** 新增 `QuickJsModuleCacheTest`，由真实 `JsLoader.clear`、`Loader.spider`、`Spider.createCtx/createObj` 与 `Module` 执行。修改前 Leanback 本次 Gradle exit 1，XML 1 项 1 失败，旧清理放行后新资源读次数 `expected 1, actual 2`；此前入口与 import 共用命中，旧清理确实完成。

**验证：** 同一屏障用例从红转绿：放行旧清理后，新 loader 的入口与 import 仍命中同一缓存，源读取保持 1 次；旧缓存被清除，旧实例清理完成，新实例的 Spider 槽保留。测试使用真实 Java 读路径和清理，仅替换 QuickJS JNI 与资源 I/O，不是设备播放/真实站源测试。

- ARM64 Debug：Leanback **81/81**（15 类），Mobile **49/49**（11 类），构建 exit 0，共 130 项无失败或跳过，XML 为本次运行（2026-11-28T01:43:53Z）生成。
- 两端筛选 `com.fongmi.android.tv.api.loader.*`；Leanback 另跑代理响应兼容 14 项与代理消费者 6 项。既有 JS/Python 失败诊断 11 项通过；测试夹具只替换 JNI 初始化，由真实 Loader 构造初始化缓存。Windows JVM 临时设置 `-Drobolectric.conscryptMode=OFF`，不更改运行时配置。
- Lead 复核：`BaseLoader`、`Module`、Python/Cat、依赖、JNI 均未修改；现有 50 项 LRU 与查找规则保持；独立双参 Spider 仍使用兼容的共享缓存路径，而经 Loader 创建的 Spider 不再访问它。

**QuickJS recovery anchor：** 缓存归属子项达成双端验收；以守卫提交和本地恢复标签为交付点。唯一后续候选：增加 JS owner 退役与迟到发布门控，再让 BaseLoader 交换实例；本阶段不包含该行为。

## QuickJS 子项：JS loader 退役与换代

基线：`dev2@e8f941a8b52b4b58b32b2b186cbd6add40978fdb`，工作树干净；用户继续上一阶段明确列出的 JS 换代。生产范围：`BaseLoader`、`JsLoader`；`SpiderInit` 仅提取已有失败回收，Python 的 map/重试路径保持。保留 App 公开调用参数、命名空间、recent 和代理语义；不改变 Python、Cat、QuickJS/JNI、依赖和播放器。不开 PR，不推送。

| 等级 | 核对证据（2026-11-28，UTC+8） | 取舍与局限 |
| --- | --- | --- |
| A | 本仓 `JsLoader.clear/getSpider/proxy`、`SpiderInit.load`、`BaseLoader.clear/awaitClear/dex/activePluginKeys`；[OpenJDK 21 ConcurrentHashMap](https://github.com/openjdk/jdk/blob/jdk-21%2B35/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java) 的 `clear` 与 `computeIfAbsent` | 当前同一 map 上的清理与初始化可能争用 bin 监视器；clear 前遍历销毁还看不到未发布的实例。JS 换代不能把旧 Jar 查找接到新的全局指针；缓存整理应保留队列中尚未销毁的活动脚本。 |
| A | [FongMi/TV 8b499e0972b93f6173eac0e0414485c6b894294a](https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a.patch) 与[冻结树 SpiderLoader](https://github.com/FongMi/TV/blob/4512b4c9f82534ff66acfde06ccdd68f35b074f0/app/src/main/java/com/fongmi/android/tv/api/loader/SpiderLoader.java#L42) | 借鉴先标记关闭、按 key 协调初始化、短锁发布和关闭后销毁未采用实例。上游 FutureTask.cancel 的中断/失败共享不直接照搬，保留本地失败重试和串行清理。相关后续、成熟实现和取消语义见前两子项；无适用的独立算法、设备性能或二进制升级研究。 |
| A | 本机锁定 `wrapper-android 3.2.3` 的 `javap`：`QuickJSLoader.init` 只调用 `System.loadLibrary`；`quickjs.crawler.Loader` 本地 Java 代码 | 新 loader 构造在清理队列锁外；不新增 native 上下文或线程。Java/JNI 行为改动以实际设备验收为准，本阶段不宣称测过设备。 |

**取舍：** 不改会留下清理阻塞与漏回收；直接移植上游会更改运行时构造、等待/失败合同。窄适配：BaseLoader 在入队时一起退役并替换 Jar/JS 指针，旧 JS 仍走原串行队列；每个生产 JS owner 固定自身的 Jar 解引用。JS 初始化在同槽互斥下、但不持 owner 或缓存清理锁执行，经同一短锁检查退役/发布；退役后才完成的实例只销毁一次。销毁期间仍保留旧活动 key，namespaced recent/代理只接受当前实例。不会强行中断脚本或已经进入代理的调用。

**验收/回滚：** 屏障暂停旧 init，旧队列释毕、新世代同 key 可用，放行 A 得 SpiderNull 且只销毁一次；另阻塞已缓存实例的销毁，验证切换立即可见、旧 key 在清理期间被保护、清理结束后新实例与代理不被误删。保留同槽成功复用、失败重试、旧 QuickJS 缓存回归及双端相关测试；一次守卫提交/标签，`git revert` 可回滚，无数据迁移。

**红灯：** Leanback 本次报告（Gradle exit 1）新增屏障 2/2 失败，分别是旧初始化仍悬挂时 clear Future 超时，以及旧 destroy 悬挂时 owner 没有换代；两条均为业务断言。

**验证与复核：** 两条屏障由红转绿：旧 JS 初始化未释放时，原队列已经完成该次清理，新世代同槽可以加载；旧初始化随后完成，只销毁一次并返回 SpiderNull。旧缓存实例的 destroy 未释放时，新 owner 也已可用，旧 owner 不再提供 cached Spider/recent 代理，活跃文件键仍由 BaseLoader 保护直到旧清理结束。新键、代理及缓存保持可用。

- ARM64 Debug 定向测试 Gradle exit 0，Leanback **83/83**（16 类），Mobile **49/49**（11 类），XML 为本次运行（2026-11-28T02:15:07Z），无失败或跳过。Leanback 含两条新屏障、QuickJS 缓存隔离、JS/Python 失败诊断 11 项与代理兼容/消费者 20 项。Windows JVM 临时关闭 Conscrypt，不改运行时或断言。
- 锁与范围：同 key 初始化互斥，临时槽在最后一个调用退出时释放，不持 owner 状态锁运行脚本或销毁；销毁/发布所有权随退役检查互斥。生产创建的 JS loader 只引用同代 Jar；串行队列、awaitClear、Py/Cat 清理顺序及失败回收行为不变。无真实站源/真机 JNI 验证；已进入插件的调用不会被强制中断。

**JS 换代 recovery anchor：** JS 子项达成双端验收，以守卫提交和本地恢复标签为交付点。唯一后续候选：先勘察 Python 进程级运行时、模块和取消的归属，再决定是否单独隔离，不复用 JS 换代假设。
