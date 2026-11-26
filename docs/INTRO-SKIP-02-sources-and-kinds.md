# INTRO-SKIP-02 · 片段跳过：新增数据源与片段种类

状态：三源/七类已提交；Android 9 TODB TLS 续修中　分支：dev2　起始 HEAD：144d06b722fcfdd6eeb1e5ba7fe28624aab70304
日期：2026-11-26

## 1. 目标与验收

对齐 PC 版（`webhtv-pc/dev2` @ `ce522cd4c13a2afa689c524d11041280c91d6f10`）的片段跳过能力，在 Android 版上：

1. 新增第三数据源 **TODB PlayBack**（`POST https://playback.theotherdb.org/api/metadata`），与 IntroDB、TheIntroDB 并行查询，沿用现有 5s 总预算与三态缓存语义。
2. TheIntroDB 端点从 `v3/media` 升级到 **v4**（v2/v3 已被上游标记弃用/冻结）。
3. 片段种类从 4 类扩到 **7 类**：回顾、片头、片尾、预告、**彩蛋（片尾之后）**、**片内广告**、**中场休息**；位宽与 PC 的 `SkipKindBits` 一致（1/2/4/8/16/32/64），出厂默认仍是回顾+片头+片尾（=7）。
4. IntroDB 的 `post_credits` 字段按 PC 实测契约映射成「彩蛋」，不再猜 `preview/next_episode/trailer`。

验收：

- 单元测试覆盖：新 kinds 解析、TODB 解析（秒→毫秒、`chapter` 丢弃、`version_runtime` 作参考时长）、彩蛋按「距结尾」折算、片中段与开头段不互相去重、旧位掩码偏好不变。
- 位掩码兼容：已存的 `intro_skip_kinds` 值（0..15）语义不变。
- 播放层行为不变：只有「尾部段」能按「本集看完」处理；片中段永远是有界 seek。

## 2. 现状（Android）

- `IntroSkipService`：两家 provider、5s 总预算、`isCacheableResponse` 三态缓存、尾部段按「距结尾距离」折算（容差 2s / 漂移上限 5min / 开放结束 5s）。
- `IntroSkipPlan`：只有 `openings` / `endings` 两个桶，`fromResolved` 把「非开头段」一律塞进 `endings`。
- `Segment.Kind`：INTRO/RECAP/OUTRO/PREVIEW；`isOpening()` 即判定开头，`trailing = OUTRO || PREVIEW`。
- `Setting`：`INTRO_SKIP_KIND_RECAP|INTRO|OUTRO|PREVIEW = 1|2|4|8`，`DEFAULT = 7`。
- 设置入口：TV `SettingAdActivity`、手机 `SettingAdFragment` → `IntroSkipKinds.show()` 多选框。

## 3. 最佳实践证据

| 证据 | 位置/地址 | 结论 | 采用 |
| --- | --- | --- | --- |
| PC 类型与位宽 | `webhtv-pc/dev2:apps/desktop-flutter/lib/core/skip_plan.dart:19-138` | 7 类；位 1..64；`dedupeGroup` 分 0/1/2 三组；尾部段才走「距结尾」 | 采用 |
| PC 尾部段折算 | 同上 `:466-517` | 与 Android 现有 `createTrailing` 同构（2s/5min/5s 三个常量一致） | 复用现有实现 |
| PC 三源并行 + 三态缓存 | `.../lib/services/intro_skip/registry.dart:104-179` | 半份结果不缓存；空查询可缓存 | 复用现有 `isCacheableResponse` |
| IntroDB 契约 | `.../introdb_provider.dart:1-105`；`docs/reference/intro-skip-data-sources.md:22-48` | 4 个顶层对象 `recap/intro/outro/post_credits`，无预告字段；参考时长取 `duration_ms/runtime_*` | 采用 |
| TheIntroDB 契约 | `.../theintrodb_provider.dart:1-95`；PC 文档 `:52-76` | v4；各类型为数组；`start_ms:null`=从 0，`end_ms:null`=到结尾；不下发参考时长 | 采用（端点升级 v3→v4） |
| TheIntroDB 官方 OpenAPI | `https://theintrodb.org/openapi.yaml`（HTTP 200，本日读取） | `v4/media` 仍在；v2/v3 弃用（维护者 issue `varunsalian/debrify#108`，2026-10-17：v2/v3 冻结并计划移除） | 升 v4，不留 v3 回退 |
| TODB 契约 | `.../todb_provider.dart:1-107`；PC 文档 `:79-128` | POST JSON；`items[].marker[]`；`time_*`/`version_runtime` 单位是**秒**；`chapter` 必须丢弃；免鉴权实测可用 | 采用 |
| 不做 SponsorBlock 分类 | PC 文档 `:174-179` | YouTube 插播位分类法，VOD 无数据源，加了是一排永远空的开关 | 明确不做 |

未决/边界（如实记录）：

- TODB 文档写 `Authorization: Bearer`，实测免鉴权；若将来强制鉴权，该源退化为「没答上来」（不可缓存），另两家不受影响。
- 不新增「按数据源开关」的 UI：Android 端今天没有源选择器，PC 的三源默认位以「全部启用」等价落地，避免把改动扩到两套设置布局。

## 4. 方案比较

| 方案 | 取舍 |
| --- | --- |
| A 不改 | 保持 4 类 2 源；彩蛋/广告/休息永远缺失，且 TheIntroDB v3 随上游移除而失效 ⇒ 否决 |
| B 照搬 PC 实现 | PC 有完整 `SkipPlan` 重写与手动标记叠加、源位掩码 UI；Android 已有同构实现，整段重写会带来无收益回归风险 ⇒ 否决 |
| C 窄适配（采纳） | 只在现有模型上加「第三桶 + 3 个 kind + 1 个 provider + 端点升级」，复用既有折算/缓存/确认框逻辑 |

## 5. 落地改动

- `IntroSkipService`：`Kind` 扩到 7；新增 `isMidRoll()/dedupeGroup()`；`create()` 与 `overlaps()` 改用分组；`IntroSkipPlan` 增加 `middles` 桶与 `getMiddles()`，`getAll()` 三桶混排；新增 `fetchTodb/parseTodbRaw`；IntroDB 加 `post_credits`；TheIntroDB 升 v4 并识别 `post_credits`。
- `Setting`：新增 3 个位常量并纳入 `INTRO_SKIP_KIND_ALL`；`DEFAULT` 保持 7。
- `IntroSkipPlayback`：`isKindEnabled` 覆盖 7 类；「本集看完」判定仍只对尾部段生效。
- `IntroSkipKinds`：多选框、文案、确认提示扩到 7 类。
- 资源：`values/strings.xml` 与 `values-zh-rCN/strings.xml` 新增 6 条文案。

## 6. 回滚

单提交、无数据迁移：`git revert` 该提交即可。旧位掩码值在回滚后仍被原 `ALL` 掩码夹取，不残留非法位。

## 7. 验证记录（2026-11-26）

- 定向单测：`.gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests IntroSkipServiceTest --tests IntroSkipPlaybackStateTest` → **BUILD SUCCESSFUL in 10m 30s**。
- 结果（`app/build/test-results/testMobileArm64_v8aDebugUnitTest`）：`IntroSkipServiceTest` **33 例**、`IntroSkipPlaybackStateTest` **8 例**，**0 失败 / 0 错误 / 0 跳过**。
- 新增用例（8 例，全部通过）：TODB 秒→毫秒与 `chapter` 丢弃、IntroDB `post_credits`→彩蛋、片中段与开头段同起点不合并、彩蛋按 `version_runtime` 折算、三桶混排时序、`kindFromWire` 白名单、片中广告不按「本集看完」、彩蛋贴结尾仍算看完。
- 兼容性：`INTRO_SKIP_KIND_DEFAULT` 仍为 7，旧位掩码 0..15 语义不变（新位只在用户主动勾选时出现）。
- 未做：设备端实测（未改播放器引擎，只改片段数据与判定类别；TODB 为新增网络源，失败按「没答上来」降级，不影响另外两家）。

## 8. Android 9 TODB TLS 兼容续修（2026-11-26）

### 已确认的现场

- 三源/七类基线：`31d844b179a3c6dd144a22d8f19b7e4894aa4db8`，恢复标签 recovery/intro-skip-02/20261126023131-31d844b179a3；5556 已安装并运行。
- 5556：Android 9/API28，arm64-v8a 应用。改用 TMDB「详情直放」后，10:49:04 从 15.650s 自动跳到 97.000s（实际落点一致），10:49:09 命中片尾并进入第3集，证明已有跳过逻辑有效。
- TheIntroDB 同参数在电脑与5556均为 HTTP404，正文 code=no_submissions；对照 TMDB1399 S1E1 为200。官方 OpenAPI（https://theintrodb.org/openapi.yaml，2026-11-26）明确其含义是暂无可返回标记，保留现有空结果语义。
- TODB 同参数在电脑上为200，TMDB307853 S1E2 的标记为 intro0–103s、credits3012–3162s；5556 在 HTTP 前收到 TLSV1_ALERT_PROTOCOL_VERSION。强制 TLS1.2失败、TLS1.3成功且证书验证通过，根因是旧平台 TLS 能力不足。
- 首条真实回归 liveTodbQueryReturnsMarkers 在未改生产代码的5556上如期断言失败（Tests run:1, Failures:1），证据 .wh_diag/todb-tls-red-device-retry.log。首次 instrumentation 因模拟器进程未附着而无结果，终止旧测试进程并 force-stop 后重新运行，不把环境失败当回归结果。

### 一手证据与依赖身份

访问日期均为2026-11-26；A=实际官方源码/制品/规范，B=官方说明/维护者讨论，C=本地实测。

| 证据 | 版本/位置 | 支持的结论与本地决策 |
| --- | --- | --- |
| A：Conscrypt 官方制品 | https://repo.maven.apache.org/maven2/org/conscrypt/conscrypt-android/2.10.0/conscrypt-android-2.10.0.module 及同目录 aar/pom/aar.sha256 | 固定 org.conscrypt:conscrypt-android:2.10.0，AAR5079740字节，SHA256 ac0f6c1b6ae6625603b8f68bb1dd5b9120115d97f2428e98679bf97c3e520551；Apache2，minSdk21，四ABI；本项目仅打包所选ARM ABI。 |
| A：确切源码/发布 | https://github.com/google/conscrypt/releases/tag/2.10.0 ，提交1f4f0db6f26c176a83e0671865e800f063abd282 | 2026-11-23稳定发布；独立 Provider/SSLContext API 提供TLS1.3，不需全局注册。该精确版本发布仅3天，长期场景成熟度未建立，不夸称零风险。 |
| A：Conscrypt 源码 | https://raw.githubusercontent.com/google/conscrypt/2.10.0/common/src/main/java/org/conscrypt/Conscrypt.java 与 SSLParametersImpl.java | 使用 newProvider、标准默认 TrustManagerFactory；不采用 ExperimentalApi 的 getDefaultX509TrustManager。 |
| A：OkHttp5.4.0实际发布源码 | https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-android/5.4.0/okhttp-android-5.4.0-sources.jar ，SHA256 d7aca53404544a763b9e0ac356ebf1976f9d313e20734dd738f53ed400dbe378 | AndroidPlatform/ConscryptSocketAdapter 已支持应用独立 Conscrypt Socket，按 Socket识别并设置ALPN；无需全局 Provider 插入或 Socket包装。 |
| B：维护者讨论 | https://github.com/square/okhttp/issues/2827 | swankjesse 推荐双参数 sslSocketFactory(factory,trustManager)，避免反射提取造成不稳；采用显式同一 TrustManager。 |
| A/B：平台 | https://android.googlesource.com/platform/external/conscrypt/+/android-9.0.0_r1/common/src/main/java/org/conscrypt/NativeCrypto.java ; https://developer.android.com/about/versions/10/behavior-changes-all#tls-1.3 | Android9内建协议止于TLS1.2；Android10+平台默认支持TLS1.3。仅API<29使用应用 Provider，现代平台维持平台TLS。 |
| A/C：ABI/16KB | 同版本AAR直接ZIP/ELF检查；https://github.com/google/conscrypt/issues/1413 为旧2.5.3问题背景 | 当前四ABI全部PT_LOAD对齐16384；arm64原生库2830392字节/v7a1819656字节。实际APK仍须验证选中ABI、ZIP对齐和尺寸差；不声称整个应用16KB兼容。 |
| B：安全发布范围 | https://github.com/google/conscrypt/security/advisories/GHSA-h3wp-2gww-26c4 | 本次稳定版的修复针对服务端多租户mTLS恢复，并非TODB客户端本次握手故障。依赖更新由应用承担，不会自动享受系统Provider补丁。 |

成熟相关实现采用 OkHttp 自带 Android Conscrypt adapter，而非再实现 TLS；泛博客、算法论文和性能论文对这一公开API/明确协议缺失决策不适用。研究中的 GitHub API限流、旧标签URL404已用官方release HTML、确切标签源码及Maven metadata补证；没有用搜索摘要替代源码。未运行上游原生测试套件，以本项目设备回归补集成证据。

### 方案、边界与验收

- 不改：Android9仍不能访问TODB，否决。
- 照搬常见全局 Security.insertProviderAt：会改变播放器、站源等所有默认TLS选型，否决。
- **窄适配（采用）**：TodbHttpClient 为TODB独占、按当前共享client实例/超时复用；保留DNS/代理/认证/拦截器，替换TLS工厂与严格默认主机名校验。API<29使用未注册的Conscrypt，API>=29使用平台TLS。只允许MODERN_TLS，不降级HTTP，不在证书/初始化失败时回退trust-all。
- 共享 catvod OkHttp.java 的既存 trust-all 不在此次范围，禁止修改；新路径不继承其宽松信任行为。5s请求/并行总预算、404空结果、失败不可缓存、片段解析与播放逻辑保持。
- 验收：真实回归红→绿；客户端复用、Provider顺序及共享客户端不变、DNS/代理/超时保留、TLS1.3能力、缺失证书与空链拒绝；5556实际POST得到TODB标记且两源完整结果可缓存；检查制品哈希、ARM库ABI/对齐、APK尺寸；不扩全播放器矩阵。
- 回滚：本次兼容单提交整体 revert（客户端、接线、固定依赖、测试与文档），回到上述31d844b基线；不迁移偏好和数据库，不修改模拟器CA/网络设置，不推送。

### 实施与验证结果（2026-11-26）

- 生产改动4文件：新增 `TodbHttpClient`（64行，按 base client 身份+超时复用；严格默认 TrustManager；显式同一 manager 的双参数 socket 工厂；新建安全客户端取 `hostnameVerifier()`；MODERN_TLS；`followSslRedirects(false)`；API<29 才走未注册 Conscrypt）、`IntroSkipService` 仅在 `PROVIDER_TODB` 分支选该客户端并记录实际 TLS/协议、`libs.versions.toml` 固定 `conscrypt=2.10.0` 与 `conscrypt-android` 别名、`app/build.gradle` 引入该 AAR。其余 provider、`execute()` 的 404/坏JSON/超时/缓存语义与播放层未改；`catvod` 全局客户端未改。
- 超时一致性：`catvod` 的 `client(long)` 只设 connect/read/write，全文无 `callTimeout`；新客户端同样只设这三项，故 callTimeout 均为默认0，行为与改动前一致。
- 定向单测（`.gradlew.bat :app:testMobileArm64_v8aDebugUnitTest`）：`IntroSkipServiceTest`33、`IntroSkipPlaybackStateTest`8、`IntroSkipActivityWiringTest`4，**45例 0失败/0跳过**；同批 `assembleMobileArm64_v8aDebug`、`assembleMobileArm64_v8aDebugAndroidTest` 与 `compileLeanbackArm64_v8aDebugJavaWithJavac` 均成功（BUILD SUCCESSFUL in 11m10s）。
- 5556 设备验收（`TodbTlsDeviceTest`，3/3 OK，1.1s）：真实查询在清空静态缓存后仍返回 TODB 片段，日志 `TODB transport tls=TLS_1_3 protocol=h2` + `TODB loaded segments=2 cost=923ms`，TheIntroDB 仍为404且整体可缓存；客户端被复用、Provider 顺序与共享客户端实例未变、DNS/代理/拦截器/超时保留、TLS1.3可用、缺失证书与空证书链被拒绝。
- 打包证据：APK 内 `lib/arm64-v8a/libconscrypt_jni.so` 仅 arm64 一份，2830392字节未压缩存储、ZIP 数据偏移 16384 对齐、ELF 全部 PT_LOAD 对齐 16384，SHA256 `319cf6cbcd7268ac9a1d3b2fcb16cc2b1aa66b525012cee90e835ac9a6dacfa3`。**包体对比不可归因**：本次 APK 205819351 字节 vs 10:39 基线 210940936（−5121585），与该库新增 2830392 字节方向相反，说明两次构建还有无关差异，故不声称包体变化量；库本体增量按 AAR 未压缩尺寸记录。
- 独立审查（run1/3）结论：未发现生产缺陷；提出并已修复1项测试可靠性缺陷——设备用例可能被 `IntroSkipService` 静态缓存直接满足而不再联网，现用例先调用 `clearCacheForTest()`（仅包内可见的测试接缝）再断言，并已用清缓存后的真实 POST 复验。
- 未验证（明确不宣称）：API≥29 路径下 OkHttp 自带 `ConscryptSocketAdapter` 在平台识别时可能仍触发 `Conscrypt.isAvailable()`，因此“29+ 完全不加载该原生库”不成立，仅“TODB 客户端在29+走平台TLS”成立；未构造非空但不可信的证书链用例（仅断言空链拒绝）；未跑 base client 重置后的重新绑定、armeabi-v7a 打包、R8/minify release、16KB整机兼容与整机性能。

## 9. Recovery anchor

- 目标：见§8；用户已要求“开始优化”，不另请求操作审批。
- 已完成：现场定位、一手研究、5556真实查询红灯；guard intro-skip-02-tls；实施 brief run1/3（4生产文件子任务），研究run1/3 ready。
- 当前改动：生产子任务见§8；Lead拥有设备测试、本任务文档与总索引。
- 已验证：45例定向单测、双端编译、5556真实 TLS1.3 取数（清缓存后复验）、APK内 ABI/对齐/ELF、安全断言；独立审查无生产缺陷。
- 未验证：见§8末尾“未验证”清单（29+原生库加载语义、非空不可信证书链、base重置、v7a、R8、16KB整机、包体归因）。
- 下一步：提交本次兼容单提交并创建恢复标签，在交付摘要写明坐标与上述边界。
