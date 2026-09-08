# 音频去广告升级设计：频谱指纹社区闭环与语音广告复合规则

> 任务：`AD-AUDIO-RULES-V2`
>
> 状态：设计评审稿；本文件先于代码实施，后续实现、验证、回滚记录继续追加在本文件。
>
> 编写日期：2026-09-08（Asia/Shanghai）
>
> 适用版本：WebHTV 当前 `dev2`，基线 `1806e4647bab28f2b257fe87f3b4567b4ff65b36`

## 1. 目标与结论

用户提供的资料实际上包含两条不同的去广告能力，不能合并成一个解析器：

1. **音频频谱指纹规则**：在线 `rules.json`，由采集器上传、云端校验合并，匹配已知广告的解码后 PCM。
2. **语音广告规则**：人可读的“广告之后*>马上回来，30”语法，通过 Sherpa-ONNX 识别文本，再按多句顺序和时间窗口命中。

本项目当前已经具备第一条能力的本地运行时和 Probe v1 兼容解析器，但第二条仍是“平面关键词包含匹配”。本次升级推荐：

- 保留当前 `spectral-sequence-v2` 运行时，不复制采集器 APK，不引入第二条 Exo 音频处理器；
- 继续兼容上游 `ad-audio-probe-rules` v1，使用社区 `rules.json` 作为**独立的指纹来源**；
- 新增独立的语音规则文档/解析/匹配层，把用户提供的 `*`、`>`、`,`、`[pre,post]` 语法转换为有界的语音广告候选；
- 把语音识别计算从播放实时性保护中单独治理：不得在音频回调中执行识别，限制 Sherpa 线程数和后台预算，队列过载时丢弃旧识别输入而不是拖慢 EXO；
- 默认继续关闭语音广告识别和自动跳过；指纹通道、语音通道、HLS/URL 规则互不改变策略和生命周期；
- 第一阶段只使用已验证的本地/内置语音规则，暂不假设在线 `rules.json` 能承载语音文本规则。

**推荐决策：实施“WebHTV 适配版 V2”，而不是盲目接入上游 SDK 或把两种规则强行拼接。**

## 2. 证据基线

### 2.1 本地 WebHTV 基线

| 证据 | 当前事实 | 设计影响 |
|---|---|---|
| `app/src/main/java/com/fongmi/android/tv/ad/audio/ProbeRuleCodec.java` | 严格解析 `ad-audio-probe-rules`、schema 1、`spectral-sequence-v1`，再映射到本地指纹 v2 | 远程指纹规则无需另造格式；必须保持整包拒绝和 revision 单调 |
| `ProbeRuleDownloader.java` | 后台 HTTPS 下载，4 MiB 限制，解析成功后交给 `ProbeRuleStore` 原子替换 | 继续 fail-open；网络更新不能阻塞播放 |
| `ProbeRuleStore.java` | 本地缓存、原子写入、revision 防回滚 | V2 不覆盖现有缓存；新来源必须复用同一安全边界 |
| `PlaybackMediaAudioPipeline.java` / `PlaybackMediaSignalHub.java` | 单一 Exo PCM 管线、多消费者、session/generation 生命周期 | 指纹和语音共享 PCM，不再安装第二个 AudioProcessor |
| `PcmAdAudioSignalProvider.java` | 指纹匹配运行在 Hub consumer 与后台 matcher 中 | 保持指纹通道隔离，规则规模增长前先测量再做索引 |
| `SpeechAdKeywordSet.java` | 逗号/分号分隔的平面关键词，文本包含即命中 | 不能表达顺序句、跨段 wildcard、前后跳转窗口，需要新规则模型 |
| `SpeechAdSignalProvider.java` | Sherpa 会话、PCM 邮箱、recognition callback、候选生成和代际校验已存在 | 新 matcher 应嵌入 Provider，不应绕开现有 Coordinator |
| `RealtimeSubtitleRecognizer.java` | 在线模型最多使用 `min(4, CPU/2)` 个线程，CPU provider；离线模型另有识别线程 | 必须增加 TV 性能预算，否则模型就绪后可能抢占 EXO 资源 |
| `AdAudioRuntimeController.java` | PCM、Probe、Speech 三个 Provider 组合，Coordinator 是唯一 seek authority | 语音候选只能通过现有 policy/coordinator 跳转 |

### 2.2 外部仓库与在线规则

以下版本通过 GitHub API 在 **2026-09-08** 读取，记录完整 revision，避免把网页瞬时内容当作版本基线：

| 仓库/地址 | 读取到的 revision | 观察到的内容 | 可信度与限制 |
|---|---|---|---|
| `https://github.com/0o755/m3u8-ad-audio-probe` | `61c7d2ec57792f8b439254b35cc0c3a9a31acf20` | Probe SDK；公开规则协议为 `ad-audio-probe-rules` schema 1；MIT | 官方仓库 README/公开文档；不直接作为 WebHTV 运行时依赖 |
| `https://github.com/0o755/m3u8-ad-audio-collector` | `070e4ff6f500386d0cfa779c981ba3cb39bf66e7` | 采集器、规则测试、提交 Worker；只调用 Probe 公共 API | 采集端与播放端职责不同；不复制 APK 内部实现 |
| `https://github.com/0o755/m3u8-ad-audio-rules` | `bb0041673883f949045d272b845394a2b518882d` | 云端规则仓库，提交后经 Worker/GitHub Actions 校验、去重、冲突过滤、合并 | 规则仓库无签名；当前只能依靠 HTTPS、严格 schema、大小限制和 revision |
| `https://raw.githubusercontent.com/0o755/m3u8-ad-audio-rules/main/rules.json` | 文件 SHA-256 `d5ab4d42196186221676727b81ddc7474363f76a3dd1360362cf16fc7bea6b6c` | `revision=3`、`schemaVersion=1`、`algorithm=spectral-sequence-v1`、5 条规则；每条有 4 个相位序列 | 通过 GitHub API 获取同一文件内容；不是固定发布包，后续内容会变 |
| `https://m3u8-ad-audio-rules-sync.ccfork.workers.dev/rules.json` | 未能在本机读取 | 用户指定的实时 Worker 地址；协议文档声明原样转发 GitHub `rules.json` | 本机 TLS 连接在 2026-09-08 失败；不能以失败的 Worker 响应为实现依据，保留 GitHub Raw 作为可审计回退 |
| `https://github.com/0o755/m3u8-ad-audio-collector/releases` | 当前 API 未发现正式 latest release | 用户另提供了采集器 APK/Demo 地址 | 不把未验证 APK 当作生产依赖；优先依赖公开源码、合同和规则文件 |

用户提供的 `/run/user/1000/gvfs/.../星落6.0.1.apk` 是外部共享路径，本轮没有读取或反编译；它不作为本设计的必要证据，也不改变源码协议决策。

### 2.3 用户提供的语音语法

语音规则示例包含以下实际需求：

```text
广告之后*>马上回来，30
中场休息*>广告时间，[2,30]
投注*平台*>注册*彩金，30
```

其含义是：

- `*`：同一句中匹配任意文本，可为空，可跨识别片段；
- `>`：多个句段按顺序出现；
- `,30`：以最后一个句段结束为锚点，向后覆盖 30 秒；
- `[2,30]`：从第一个句段开始前 2 秒，到最后一个句段结束后 30 秒；
- 行尾不需要再写 `*`；空行、`//` 注释和中文标点应被容忍。

这不是当前社区 `rules.json` 的字段，不能塞进 `ProbeRuleCodec` 的 `fingerprints` 数组，也不应把全文规则复制到 `SpeechAdKeywordSet` 的逗号关键词中。

## 3. 用户可见能力

实施后，用户可以得到两条相互独立的能力：

1. **已知音频广告自动/确认跳过**：开启音频指纹后，WebHTV 在后台更新社区指纹规则；播放 EXO VOD 时从解码 PCM 匹配，命中后按现有“提示确认”或“自动跳过”策略处理。
2. **语音广告句式识别**：开启语音广告识别并准备好模型后，识别“广告之后……马上回来”“中场休息……广告时间”等顺序句，按规则指定的前后窗口给出候选；支持用户确认或自动跳过。

两种命中同时发生时，Coordinator 仍只允许一次有效 seek；不能因指纹和语音重复命中而连续跳转或延长跳转窗口。

## 4. 方案比较

### 4.1 方案 A：不改现有实现

保留现有指纹 v1/v2 兼容和语音平面关键词。

- 优点：风险最低、无需新增解析器。
- 缺点：用户提供的顺序语法无法使用；误报和漏报都只能靠增加孤立关键词；模型就绪后的 EXO 性能风险不解决。
- 结论：不能满足本次升级目标，拒绝作为最终方案。

### 4.2 方案 B：原样接入 Probe/采集器 SDK

在 WebHTV 中直接引入 `ad-audio-probe`，同时照搬采集器或 APK 的规则/匹配器。

- 优点：上游更新快，采集器与播放端名义上统一。
- 缺点：Probe 默认 Media3 适配器与 WebHTV 现有 Media3/FFmpeg/Exo 链路有版本和所有权边界；新增一套解码或 PCM 处理会增加包体、网络、生命周期和 CPU；上游指纹协议并不包含语音句法；当前规则仓库无签名；还会引入不可审计的二进制/反编译依赖。
- 结论：拒绝原样接入；只兼容其公开 rules-v1 数据合同和算法黄金样本。

### 4.3 方案 C：WebHTV 适配版（采用）

- 指纹：复用现有 `PlaybackMediaSignalHub`、`PcmAdAudioSignalProvider`、`ProbeRuleCodec/Store/Downloader`；仅增加来源回退、规则统计和规模化前的指标。
- 语音：新增独立 `SpeechAdRuleCodec`、不可变 `SpeechAdRuleSet`、有界 `SpeechAdMatcher`；`SpeechAdSignalProvider` 只负责 PCM/识别会话/代际，候选仍流向现有 multiplexer/policy/coordinator。
- 性能：语音计算使用低优先级、受限线程数和有界邮箱；模型未就绪、过载、识别错误均自动降级，不影响主播放。
- 安全：不把远程文本规则当作可信代码；限制行数、字符数、段数和窗口；禁止任意正则；规则源整包解析，失败保留旧缓存。
- 结论：满足能力目标，改动范围可逆，兼容当前行为和默认关闭契约。

## 5. 规则协议设计

### 5.1 语音规则文档

首期采用纯文本本地文档，后续可封装成 JSON，但不和 Probe JSON 混用：

```text
# speech-ad-rules v1
广告之后*>马上回来，30
中场休息*>广告时间，[2,30]
```

解析规则：

1. 去除 UTF-8 BOM、首尾空白、空行和 `//` 行尾注释；`#` 仅作为文档头或整行注释。
2. 只接受 ASCII/中文逗号 `,`/`，` 的一个动作后缀；没有动作后缀的行拒绝，不静默猜测。
3. 动作 `,post` 映射为 `preRoll=0`、`postRoll=post`；动作 `[pre,post]` 映射为对应窗口。
4. 主体按 `>` 分成 1～8 个有序句段；每个句段长度限制 128 个 Unicode code point。
5. `*` 只表示通配文本，不表示正则；连续 `*` 合并；主体去除不可识别控制字符。
6. 句段必须包含至少一个字母或数字；全是 `*`、空句段和只有标点的规则拒绝。
7. `preRoll`/`postRoll` 限制在 0～120 秒；首期默认规则最多 30 秒，超过上限拒绝。
8. 单文档最多 256 条规则、总输入 64 KiB、每条最多 8 个句段；超限整份拒绝并保留旧快照。

编译结果使用字面量片段和通配状态机，不调用用户可控正则，避免灾难性回溯。中文匹配使用 NFKC 规范化；拉丁文本保留大小写无关匹配；不保存完整识别文本到日志。

### 5.2 语音匹配状态机

`SpeechAdMatcher` 维护当前 session/generation/timeline 的有界识别窗口：

1. 每个识别结果只保留规范化文本、起止时间、timeline token 和有限长度；
2. 对每条规则按 `>` 依次推进状态，第一句记录 `firstStartUs`，末句记录 `lastEndUs`；
3. `*` 可跨 partial/final 片段，但窗口最长 30 秒；超过窗口重置该规则状态；
4. 同一规则在 30 秒冷却窗口内不重复发候选；
5. 命中后输出 `ruleId`、`preRollMs`、`postRollMs`、`firstStartMs`、`lastEndMs`，不输出原文；
6. seek、切源、音频 flush、引擎重建和规则热更新提升 generation，清空所有状态；旧 callback 丢弃；
7. 语音识别 callback 的时间可能早于当前播放位置，候选生成时必须通过 `PlaybackMediaClock` 校准，并钳制到 `[0,duration]`；无法校准时只提示，不自动 seek。

候选区间定义：

```text
candidateStart = max(0, firstStartMs - preRollMs)
candidateEnd   = min(durationMs, lastEndMs + postRollMs)
```

若 `candidateEnd <= candidateStart`、时间轴过期、媒体为直播或不可 seek，则丢弃候选并记录固定枚举诊断。

### 5.3 与现有关键词设置的迁移

- 保留 `SpeechAdSetting` 的启用、跳过秒数、模式入口，避免备份和 UI 立即失效。
- 将现有逗号关键词自动转换为单句规则，例如 `赌场` 转为 `赌场，15`；旧用户无需重新配置。
- 新增“语音规则”文本编辑/导入入口；旧“关键词”作为快捷规则区或兼容输入，不再把复杂规则拆成逗号 token。
- 规则来源分为 `builtin`、`user`、`imported`，合并时按稳定 ID 去重；用户规则优先，不自动覆盖用户同 ID 内容。
- 首期不从音频指纹 `rules.json` 推导语音词，也不把识别文本自动写成 URL/HLS/音频指纹规则。

## 6. 播放实时性保护

用户反馈已经证明：模型未下载时播放正常，模型下载并启用语音识别后 EXO 立即卡顿；因此“功能能识别”不是充分验收条件，播放实时性是硬约束。

### 6.1 当前风险点

- `RealtimeSubtitleRecognizer.threadCount()` 目前最多配置 4 个 CPU 推理线程；电视盒的可用核心数和调度能力差异很大。
- 语音 Provider 会持续接收 PCM；模型就绪后每帧都可能触发重采样、VAD/解码或识别队列工作。
- Hub consumer 的实时入口即使不做识别，也承担 PCM 引用/复制和投递；必须有明确耗时预算。

### 6.2 适配策略

实施阶段加入以下契约：

1. **音频入口零推理**：Hub/AudioProcessor 回调只做格式检查、有限复制或入有界邮箱；不得创建 Sherpa recognizer、执行 ONNX decode、规则匹配或日志 I/O。
2. **低优先级识别线程**：语音 Provider 使用独立的后台 `Executor`，创建线程时设置 Android background 优先级；不与 UI、Exo 控制线程共用执行器。
3. **线程数上限**：TV 默认 Sherpa/ONNX 推理线程为 1，至少先完成 1/2 线程 A/B；不得继续按 `CPU/2` 无上限启用。高性能设备只有在 profile 证明无掉帧后才允许 2。
4. **有界退压**：PCM 和识别任务都有固定上限；过载时丢弃最旧帧并 reset 当前语音识别段，禁止无界积压和反向阻塞音频生产者。
5. **分阶段启用**：模型未验证、播放状态不适合、媒体非 VOD、时钟无效或 Provider backlog 超阈值时，保持 `DEGRADED/IDLE`；不为了“凑完整识别”牺牲播放。
6. **可观测指标**：记录识别耗时桶、队列峰值、丢帧数、recognizer reset 次数和 Provider 状态；禁止记录 PCM、完整文本、URL、Cookie、关键词正文。
7. **用户保护**：TV 设置说明语音识别可能消耗 CPU；如果连续检测到过载或播放 watchdog 风险，本次会话自动停用语音 Provider，但指纹/HLS 去广告继续工作。

### 6.3 性能验收门槛

在至少一台低端 TV 盒和一台高性能 Android TV 上，使用相同 VOD、相同音轨和相同模型完成对照：

- 关闭语音识别为基线，开启“模型未就绪”、开启“模型就绪 + 线程 1”、开启“模型就绪 + 线程 2”四组；
- 比较 `droppedFrames`、video/audio renderer backlog、`ad-audio`/Sherpa 线程 CPU、识别队列峰值和首帧时间；
- 语音开启组不得造成产品允许阈值之外的掉帧或音频 underrun；任一设备不满足时保留“语音默认关闭 + 自动降级”，不得宣称功能完成；
- 先完成性能实测，再决定是否开放语音自动跳过；指纹确认跳过不依赖语音性能门槛。

## 7. 指纹规则接入与社区闭环

### 7.1 保持现有兼容链

当前公开 `rules.json` 已被本地 `ProbeRuleCodec` 兼容：

```text
format       = ad-audio-probe-rules
schemaVersion= 1
revision     = 3
algorithm    = spectral-sequence-v1
rules        = 5
```

因此不实施格式转换、不接入采集器 APK、不安装 Probe 自己的 Media3 播放器。播放侧继续：

```text
PlaybackMediaAudioProcessor
        -> PlaybackMediaSignalHub
        -> PcmAdAudioSignalProvider
        -> AdAudioDetectionMultiplexer
        -> AdSkipPolicyController
        -> AdSkipCoordinator
```

### 7.2 下载与来源策略

短期沿用 Worker 默认地址，增加以下可逆改进：

- Worker 为主，GitHub Raw 仅作为固定官方仓库的显式回退；不对任意用户自定义 URL 自动跨域回退；
- 两个响应都必须通过完全相同的 `ProbeRuleCodec`、大小限制、revision 单调校验和原子 Store；
- 网络失败、TLS 失败、HTTP 非 2xx、schema/算法错误或 revision 回退时保留旧缓存并继续播放；
- 记录当前 source、revision、规则数和错误码，不记录规则正文和媒体请求信息；
- 在签名规则包发布前，不把“HTTPS + schema 合法”描述成真实性证明；自动跳过默认仍关闭。

中期可把远程规则迁移到已有 `SignedRulePackage*`/`SignedProbeRuleSidecar*` 验证链，但必须先完成外部发布者密钥、轮换、撤销和恢复合同；不能只在客户端加一个公钥就宣称闭环安全。

### 7.3 规模与性能

当前 matcher 仍近似全规则扫描。远端当前只有 5 条规则，暂不为了未来数千条规则提前重构。达到以下任一条件后再开独立阶段：

- 规则数超过 256；
- 低端设备每 hop matcher 时间或队列 reset 超过预算；
- 线上规则实测显示全扫描造成可复现的资源竞争。

届时按首帧/前两帧建立候选桶，仍在后台 matcher 线程完成，并保留旧扫描器作为回退实现。

## 8. 分阶段实施计划

### Phase 0：设计与基线（本文件）

- [x] 固定 WebHTV 基线、外部仓库完整 commit ID 和远程规则快照信息。
- [x] 明确指纹 JSON 与语音文本规则不是同一协议。
- [x] 记录“不改、原样接入、WebHTV 适配”三种方案和取舍。
- [x] 记录播放实时性、兼容、安全、回滚和验收门槛。

### Phase 1：语音规则纯 JVM 层

预计变更范围：

```text
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdRule.java
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdRuleCodec.java
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdRuleSet.java
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdMatcher.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdRuleCodecTest.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdMatcherTest.java
```

验收：覆盖注释、中文标点、通配、顺序句、前后窗口、跨识别片段、非法/超限输入、冷却和 timeline reset；不依赖 Android、JNI 或真实模型。

### Phase 2：Provider 接线与性能保护

预计变更：

- `SpeechAdSignalProvider`：使用新 matcher、候选时间窗口和有界退压；
- `SpeechAdConfig/Setting`：兼容旧关键词，加入规则来源/性能 profile；
- `RealtimeSubtitleRecognizer`：线程数配置化、TV 默认 1、低优先级和 reset 合同；
- `AdAudioRuntimeController`：独立语音 executor、状态隔离和过载自动降级；
- 现有端到端测试：验证语音候选、指纹候选不会双重 seek。

验收：单测 + JVM 端到端 + 至少一次 Leanback/TV debug 构建；设备性能验证不以编译通过替代。

### Phase 3：规则来源与 UI

- 内置默认语音规则和用户导入/编辑；
- 规则校验错误显示明确原因，不覆盖旧有效规则；
- 音频指纹设置显示来源、revision、规则数量、最后刷新结果；
- 语音设置显示模型状态、规则数量和性能保护状态；
- Mobile/Leanback 入口保持语义一致，TV 焦点导航单独验证。

### Phase 4：受控发布

- 默认：指纹确认模式关闭自动跳过；语音识别关闭；
- 灰度：先允许指纹确认跳过，再根据设备 profile 开放指纹自动跳过；
- 语音：性能门槛通过后仍默认关闭，用户明确打开；自动跳过后置；
- 监测误跳、漏检、掉帧、队列溢出和模型启动失败；发现主播放退化立即关闭语音 Provider，不回滚指纹缓存。

## 9. 明确不做的事情

- 不把人类语音规则写进 `rules.json` 的 `fingerprints` 字段；
- 不把完整识别文本、关键词、媒体 URL、Cookie、Authorization 上传到社区服务；
- 不复制采集器 APK、反编译代码或 Probe 内部类；
- 不在音频回调、UI 线程或播放器控制线程执行 ONNX 推理；
- 不让语音 Provider 直接调用 `seekTo`；
- 不因规则下载失败清空已验证缓存或阻塞主播放；
- 不在没有真实设备 profile 的情况下声称“降低线程优先级即可解决 TV 卡顿”；
- 不将当前无签名远程规则描述成防篡改发布物。

## 10. 风险、回滚与接受标准

### 风险

| 风险 | 防护 |
|---|---|
| 语音模型抢占 CPU 导致 EXO 掉帧 | 线程 1、低优先级、独立 executor、有界退压、过载停用、设备对照测试 |
| 语音规则 wildcard 误报 | 复杂规则默认确认；至少 2 句的规则优先；冷却、时间窗、人工撤销 |
| 规则误跳正常内容 | 指纹与语音独立策略；无签名规则不默认自动跳；规则整包校验和可回退 |
| 远程 Worker/Raw 内容不一致 | revision + 内容摘要 + 相同 codec；只切换到完整验证通过的快照 |
| 规则数量增长导致 matcher 变慢 | 当前小库不提前索引；以 profile 触发单独候选桶阶段 |
| 旧用户设置丢失 | 旧关键词转换为单句规则；保留原 Prefs key 和备份字段语义 |

### 回滚路径

1. 语音规则层：关闭新 matcher feature flag，继续使用旧 `SpeechAdKeywordSet`；
2. Provider 层：停止创建新语音 executor，保留指纹 Provider 和旧语音配置；
3. 模型层：恢复原 Sherpa 线程数/工厂实现；
4. 指纹来源层：恢复到最后一个 `ProbeRuleStore` 有效 revision，不删除 current/previous；
5. 规则文件：新格式解析失败不写入主文件，删除临时文件即可恢复；
6. 若修改跨多个阶段，按 Phase 2 → Phase 1 逆序回退，每阶段独立 commit/tag。

### 完成标准

- 公开指纹 `rules.json` revision 3 的 5 条规则可被现有 codec 解析并进入缓存，网络失败不影响播放；
- 用户示例中的 `*`、`>`、`,30`、`[2,30]` 至少各有正例、负例和跨识别片段测试；
- 语音候选经过 session/generation/timeline、duration 和 seekability 校验；
- 指纹与语音同时命中时最多产生一次有效跳转；
- 模型未就绪、识别异常、队列溢出、规则超限均 fail-open；
- 在目标 TV 设备上，模型就绪开启语音识别不得引入超出门槛的掉帧或 AudioTrack underrun；
- 所有新增规则/设置均可通过单独 feature flag 关闭，旧用户设置和旧缓存可继续读取；
- 设计、实现、验证、提交和恢复 tag 均记录在本文件，不以“编译通过”代替运行时验收。

## 11. 用户决策与当前下一步

- **建议**：实施 Phase 1（语音规则纯 JVM 层）和 Phase 2（Provider 性能保护），保持指纹远程规则协议不变。
- **暂缓**：签名远程规则发布、数千条规则索引、语音规则云端同步，直到发布者合同和真实设备数据齐备。
- **忽略**：直接引入采集器 APK、Probe 默认 Media3 播放器或把语音规则伪装成指纹 JSON。

**唯一下一步**：经用户确认后，在任务文档所列范围内先实现 Phase 1 的四个 JVM 规则类和对应单元测试；通过后再进入 Provider 性能保护，不同时改 UI、下载器和原生依赖。
