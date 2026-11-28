# C-TV-LIVE-AUTH — 来源限定的 HLS 认证上下文（Exo 子项）

## 目标与范围

基线：`dev2@91c2221683ddcbca804345c9120c12c6416afece`，工作树干净。用户批准上一轮建议的窄单元：只改 `catvod` 共享 `AuthInterceptor` 的凭据作用域与 401 重试目标，不引入 `Auth.Context`、`PlaySpec.auth`、DataSourceFactory 逐源标签或 MPV 路径。产出本地提交，不推送。

要解决的实际能力：401 认证重试只作用于**发起挑战的那个来源**（协议 + 主机 + 有效端口）。同主机不同端口、HTTP→HTTPS、跨域重定向都不再继承凭据；调用方显式提供的 `Authorization` 不被挑战后回填的凭据覆盖。

## 设计依据（2026-11-28，UTC+8）

| 等级 | 证据 | 决策影响 |
| --- | --- | --- |
| A | 本仓 `AuthInterceptor.java:18`（`host → userInfo` 全局映射）、`:31-41`（401 重试用原始 `request`）、`OkHttp.java:243` 应用拦截器链、`Auth.java:24-30` | 缺口在键与重试目标，不在 Basic/Digest 计算。`AuthInterceptor` 属 `catvod` 共享模块，其它调用方行为必须保持。 |
| A | [RFC 7617 §2.2](https://www.rfc-editor.org/rfc/rfc7617#section-2.2)（保护空间由认证方案与 realm 定义，凭据不应跨保护空间复用）、[RFC 7235 §4.1](https://www.rfc-editor.org/rfc/rfc7235#section-4.1)（challenge 与 `WWW-Authenticate`） | 主机名不是保护空间边界；不同端口/协议属不同来源。以 origin 作键符合规范意图。 |
| A | [OkHttp 5.4.0 `RetryAndFollowUpInterceptor`](https://github.com/square/okhttp/blob/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt) 与 `Response.request()` 语义 | 重定向在 `chain.proceed` 内部完成，应用拦截器看到的 `chain.request()` 不等于最终响应请求；必须用 `response.request()` 才能对正确的 URL 重试与计算 digest URI。 |
| B | FongMi/TV [`16b1f3e10c3e9ca0877461b26c21308626fac77c`](https://github.com/FongMi/TV/commit/16b1f3e10c3e9ca0877461b26c21308626fac77c)（台账登记） | 上游以 sameOrigin 捕获并拒绝覆盖已有凭据；本仓按该不变量做最小适配，不移植其 Context/PlaySpec 结构。 |

**未采用：** 维持现状会保留跨来源复用；整移上游需新增 auth 上下文并触及 DataSourceFactory、直播源与 MPV，超出本次批准范围。本单元不含 MPV、代理选路、缓存键与诊断输出变化，也不承诺实测泄漏结论或性能收益。

## 验收

Leanback JVM（Robolectric + MockWebServer，`127.0.0.1` 不同端口天然构成不同来源）：

1. 带 userinfo 的请求成功后，同主机**另一个端口**的请求不得携带 `Authorization`。
2. 同一来源已有记住的凭据时，调用方显式 `Authorization` 遇 401 不被替换，且不产生第二次请求。
3. 跨端口 302 后的 401 不重试到原 URL，也不把凭据发往重定向目标。

保留：`clear()`、userinfo → Basic 注入、Digest 分支、其它 `catvod` 调用方行为。回滚：单提交 `git revert`。

**Recovery anchor：** 守卫已开，生产代码未改；唯一下一步是写三条回归并取得红灯。

## 实施与验收（2026-11-28）

- 红灯（`app/build/auth-origin-red.log`，本次 XML 3 项 3 失败）：跨端口挑战被另一来源的记住凭据应答（期望 401 实得 200）；调用方显式 `Authorization` 在 401 后被记住凭据替换（期望 401 实得 200）；跨端口 302 后的 401 被重试到原 URL 并成功（期望 401 实得 200）。
- 改动只在 `AuthInterceptor`：记住的凭据键从主机名改为来源（协议 + 主机 + 有效端口）；401 重试改用 `response.request()`，并对该请求的来源查表；调用方自带 `Authorization`（非 userinfo 注入）遇 401 不再覆盖；digest 的 URI 也改用挑战请求。userinfo → Basic 注入、`clear()`、Digest 分支与其它 `catvod` 调用方行为不变。
- 绿灯：`AuthInterceptorOriginTest` **3/3**（XML 2026-11-28T03:48:14Z）；Leanback 相邻 loader 组 **69/69**，Mobile loader 组 **49/49**，均本次生成、无失败或跳过。Windows JVM 以 `-Drobolectric.conscryptMode=OFF` 绕过缺失的 `conscrypt_jni`，测试类另有 `@ConscryptMode(OFF)`，不改产品配置。
- 边界：`AuthInterceptor` 属共享模块，本次只收紧跨来源复用；未做真实局域网/外部站源的 HLS 认证端到端验证，也未接入 `Auth.Context`/`PlaySpec.auth`/MPV 路径。无依赖、ABI、制品或性能主张。

**Recovery anchor：** 源码与回归已完成，待 Mobile 结果与守卫提交；后续若需要 MPV 侧，需另立阶段。
