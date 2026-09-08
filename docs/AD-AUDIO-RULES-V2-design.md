# 音频去广告升级设计：频谱指纹社区闭环与语音广告复合规则

> 任务：`AD-AUDIO-RULES-V2`
>
> 状态：设计评审稿已交付；Phase 1 已提交并通过 34 项 JVM 测试，尚未接线。Phase 2 的时间/锁/线程评审已补齐，建议先实施 2A 执行隔离，再实施 2B 规则与精确度验收；仍待明确实施确认。Phase 3～4 未实施，不代表整项升级完成。
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
| `https://m3u8-ad-audio-rules-sync.ccfork.workers.dev/rules.json` | 2026-09-08 网页读取工具返回 `revision=3` | 实际响应头字段为 `ad-audio-probe-rules`、schema 1、`spectral-sequence-v1` | A：直接响应证据；早先本机 curl 的 TLS 失败仍保留为环境问题。网页工具可读取不代表本机或 Android 下载器已通过联网验收 |
| `https://github.com/0o755/m3u8-ad-audio-collector/releases` | 当前 API 未发现正式 latest release | 用户另提供了采集器 APK/Demo 地址 | 不把未验证 APK 当作生产依赖；优先依赖公开源码、合同和规则文件 |

用户提供的 `/run/user/1000/gvfs/smb-share:server=192.168.50.3,share=users/Maple/共享/星落6.0.1.apk` 已于 2026-09-08 做只读文件/ZIP 目录检查：81,613,887 字节、1,722 个 ZIP 条目、存在 `AndroidManifest.xml`，原生库目录仅见 `arm64-v8a`。文件名检索未出现 Sherpa/ONNX/Vosk/Whisper/fingerprint 等显式名称；这**不能证明没有这些能力**，也不能推断广告算法、识别精度或性能。未安装、执行、提取或反编译 APK；其交互行为对照仍未完成，不把它作为实现来源或生产依赖。

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
3. 动作 `,post` 映射为 `preRoll=0`、`postRoll=post`；动作 `,[pre,post]` 映射为对应窗口，也接受省略外侧逗号的 `[pre,post]`。规范输出统一使用 ASCII 逗号；多重动作后缀整行拒绝。
4. 主体按 `>` 分成 1～8 个有序句段；每个句段长度限制 128 个 Unicode code point。
5. `*` 只表示通配文本，不表示正则；连续 `*` 合并；主体去除不可识别控制/格式字符，空白折叠。128 code point 上限适用于规范化后的整个句段，而不是每个 wildcard 分隔的字面量。
6. 句段必须包含至少一个字母或数字；全是 `*`、空句段和只有标点的规则拒绝。
7. `preRoll`/`postRoll` 限制在 0～120 秒；首期默认规则最多 30 秒，超过上限拒绝。
8. 单文档最多 256 条规则、总输入 64 KiB、每条最多 8 个句段；超限整份拒绝并保留旧快照。

编译结果使用字面量片段和通配状态机，不调用用户可控正则，避免灾难性回溯。中文匹配使用 NFKC 规范化；拉丁文本保留大小写无关匹配；不保存完整识别文本到日志。

### 5.2 语音匹配状态机

`SpeechAdMatcher` 由单一后台 worker 调用，维护当前 timeline 的有界识别窗口；session/generation 的旧 callback 必须由现有 Provider 先行拒绝，不能由一个 timeline token 替代全部代际校验：

1. 每个识别结果只保留规范化文本、起止时间、timeline token；每次输入最多 4,096 code point，共享窗口最多 128 个结果、8,192 UTF-16 code unit，超限按完整旧结果淘汰，不截断代理字符对；
2. 规则按 `>` 顺序匹配，首字面量即使跨结果也保留原结果的 `firstStartUs`，末字面量保留所属结果的 `lastEndUs`；
3. `*` 可跨增量识别片段，包括空串和换行；窗口从最早结果起点到最新结果终点最长 30 秒，未完成的首句也不得无限保留。Phase 1 只接受已定稿、非重叠、按时间递增的增量结果；重复/重叠结果拒绝，超长或无效输入中断文本连续性。累计 partial/final 去重与修订属于 Phase 2 适配职责，不能把两个累计全文直接拼接；
4. 同一规则在 30 秒冷却窗口内不重复发候选；
5. 命中后输出 `ruleId`、`preRollMs`、`postRollMs`、`firstStartUs`、`lastEndUs` 和 timeline token，不在 Match 中输出原文；
6. seek、切源、音频 flush、引擎重建和规则热更新提升 generation，清空所有状态；旧 callback 丢弃；
7. Provider 的候选保持原始 capture 时间；**只能由现有 `AdSkipCoordinator.targetFor` 使用 `PlaybackMediaClock` 转为媒体时间并做 duration 钳制**。Provider 不能预先加 media anchor，也不能拿媒体总时长钳制 capture 坐标，避免重复转换。无法取得新鲜时钟或目标已过期时不得 seek。

候选区间定义：

```text
captureStart = max(0, firstStartUs / 1000 - preRollMs)
captureEnd   = lastEndUs / 1000 + postRollMs
mediaTarget  = clamp(clock.mapCaptureToMediaMs(captureEnd), 0, mediaDurationMs)
```

若原始区间为空、映射失败、时间轴过期、媒体为直播/不可 seek，或最终 `mediaTarget <= currentPosition`，则丢弃候选并记录固定枚举诊断。Phase 1 的带 duration 辅助方法只在同坐标系下有意义，不能代替既有 Coordinator 的媒体坐标校验。

未知 duration 以负值表示，此时仅计算未钳制上界的候选，不构成自动 seek 授权。Phase 1 的时间来源是**整个识别片段边界**，不是词级对齐结果；不能声称精确定位“关键词后第 30 秒”。Phase 2 必须确认识别器能否提供可靠终点/对齐，不能把回调墙钟或过期播放位置冒充关键词时间。

`[2,30]` 描述候选广告区间，不代表可以撤销已经播放的前 2 秒。在没有预扫描/前瞻缓冲的实时识别中，只能在命中后向有效终点前跳，不得为了“补跳前段”回 seek；本设计不新增预解码或播放延迟。迟到结果、已越过终点或时间不可信的候选不自动执行。

### 5.3 与现有关键词设置的迁移

- 保留 `SpeechAdSetting` 的启用、跳过秒数、模式入口，避免备份和 UI 立即失效。
- 将现有逗号关键词自动转换为单句规则，例如 `赌场` 转为 `赌场，15`；旧用户无需重新配置。
- 新增“语音规则”文本编辑/导入入口；旧“关键词”作为快捷规则区或兼容输入，不再把复杂规则拆成逗号 token。
- 规则来源分为 `builtin`、`user`、`imported`，合并时按稳定 ID 去重；用户规则优先，不自动覆盖用户同 ID 内容。
- 首期不从音频指纹 `rules.json` 推导语音词，也不把识别文本自动写成 URL/HLS/音频指纹规则。

旧关键词的 ASCII 单词边界语义必须继续保留；新规则的字面量包含匹配不能直接替代旧 `SpeechAdKeywordSet`，否则 `ad` 可能误命中 `download`。Phase 1 没有迁移旧设置。

### 5.4 规则误伤、冲突与默认策略

| 用户示例类型 | 设计处置 |
|---|---|
| “广告之后 > 马上回来”等明确过渡句 | 可作为候选模板，但未经误跳样本与时间对齐验收仍默认确认，不直接自动跳 |
| “本片*冠名”“充值*优惠”“品牌*推荐”等单句 | 可能属于正常剧情/讨论，要求用户主动启用 |
| “激情”“私密”“少妇”“美女*主播”“福利*视频”等宽泛词 | 默认不启用；不得把词命中描述成色情内容判断或可靠儿童保护 |
| “下集预告”“精彩花絮” | 属于可选内容跳过，不等同广告，后续 UI 应与广告开关分离 |
| 同一主体配置不同窗口，如 `本片*冠名,25` 与 `本片*冠名,[4,30]` | Phase 1 保留为不同 ID，仅完全相同的规范规则去重；Phase 3 导入需显式展示冲突、由用户选择。不得默默取最长窗口，Provider/Coordinator 接线后仍最多一次有效 seek |

用户提供的完整示例不是经过精度验证的默认库；语法可解析与适合自动跳过是两项独立验收。

## 6. 播放实时性保护

重点防范并验证“模型未就绪时正常、就绪后开启识别导致 EXO 卡顿”的风险。本轮没有执行真实模型/设备对照，不能把线程数或某个调用点认定为已证实根因；“功能能识别”不是充分验收条件，播放实时性是硬约束。

### 6.1 当前风险点

- `RealtimeSubtitleRecognizer.threadCount()` 目前最多配置 4 个 CPU 推理线程；电视盒的可用核心数和调度能力差异很大。
- 语音 Provider 会持续接收 PCM；模型就绪后每帧都可能触发重采样、VAD/解码或识别队列工作。
- Hub consumer 的实时入口即使不做识别，也承担 PCM 引用/复制和投递；必须有明确耗时预算。

### 6.2 适配策略

实施阶段加入以下契约：

1. **音频入口零推理**：Hub/AudioProcessor 回调只做格式检查、有限复制或入有界邮箱；不得创建 Sherpa recognizer、执行 ONNX decode、规则匹配或日志 I/O。
2. **低优先级识别线程**：语音 Provider 使用独立的后台 `Executor`，创建线程时设置 Android background 优先级；不与 UI、Exo 控制线程共用执行器。
3. **线程数上限**：语音广告专用 profile 先使用 1 个推理线程，并完成 1/2 线程 A/B；现有 `threadCount()` 实际已限制为 1～4，问题不是“没有上限”，而是未按播放共存负载验证。实时字幕的原默认配置不随广告 profile 改变；提高广告上限需要 profile 证明无回归。
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

**唯一下一步**：确认 Phase 2 的 Provider 输入/时间对齐合同、变更范围及真实 TV 性能验收方案后，再启动新的 guard 会话实施；不同时改 UI、下载器和原生依赖。

## 12. Phase 1 实施与验证记录（2026-09-08）

### 范围与完成情况

- 分支 `dev2`；Phase 1 基线为设计提交 `045ae26ab2374264f72dd4c266eca2f1f2dfc5e9`，设计恢复标签为 `recovery/AD-AUDIO-RULES-V2/20260908130147-045ae26ab237`。
- 延续原 `AD-AUDIO-RULES-V2`、`standard` guard 会话；初始受保护脏路径为空，交接时四个未跟踪规则类属于该会话，不重新归属其他任务。
- 新增 `SpeechAdRule`、`SpeechAdRuleCodec`、`SpeechAdRuleSet`、`SpeechAdMatcher` 及两个对应测试类；仅这六个文件与本文档属于本阶段范围。
- 不执行用户正则；规范文本产生稳定 ID，集合不可变；程序构造同样约束动作秒粒度，序列化输出不得突破文档大小上限。
- 未修改 Provider、旧关键词设置、UI、指纹下载/缓存、依赖、JNI 或原生库；没有新增运行时默认开关或实际 seek。回滚不需要清理用户配置。

### 决定性验证

最终使用本机 `javac` 编译全部四个新类与两个测试类，随后运行缓存中的 JUnit 4.13.2：

```text
org.junit.runner.JUnitCore
  com.fongmi.android.tv.ad.audio.SpeechAdRuleCodecTest
  com.fongmi.android.tv.ad.audio.SpeechAdMatcherTest
结果：OK (34 tests)，0 failures
```

- 覆盖 BOM/注释、中英文逗号、两个动作格式、规范化与 round-trip、重复/连续 wildcard、空串/换行与顺序匹配、非法多重后缀、规则数/文档字节/整句长度上限、不可变集合、时间窗/冷却/重叠回调/timeline reset、跨首句时间来源与 duration 钳制。
- 新增边界回归用例先在旧实现复现 9 个失败，再于修正后全部通过；不是只更改断言使旧实现变绿。
- 早先 `:app:testDebugUnitTest` 因缺少 flavor 任务失败，随后 Mobile arm64 定向 Gradle 执行完成主/测试源码编译，但发现两处错误的 duration 断言；它们已修正，最终 JVM 测试覆盖了修订实现。**不把先前 Gradle 失败写成成功，不声称最终源码经过 APK/设备验收**。本阶段没有 Android API/依赖改动，直接 JVM 编译和相同 JUnit 用例是本阶段规则合同的最终门槛。
- 完整最终输出保存在本机 `/tmp/AD-AUDIO-RULES-V2-green.O8b3vf/test.log`；临时日志不是持久验收的唯一来源，测试命令、范围和结论在本文与提交的 Verification 字段中保留。

### 尚未完成的验证与设计门槛

- 真实 ASR 定稿/累计结果行为、词级或片段终点的可信度及媒体时钟换算；未解决前不得用新语音规则自动 seek。
- 目标 TV 的 CPU、视频丢帧、AudioTrack underrun、队列丢弃与模型启动对照；低优先级和单线程只是待测方案，不是性能修复证明。
- 指纹/语音同时命中后的唯一 seek、网络失败缓存保留和所有 UI/迁移交互，仍属于后续接线阶段。
- 现有源码/规则直接证据与本地 JVM 反例测试足以限定 Phase 1 的语法合同，但**不等于整项最佳实践评审已完成**。Phase 2 前还需补齐其官方识别器/平台调度资料、相关上游 issue/回退讨论、成熟项目实现，以及目标设备测量；论文或外部 benchmark 若不适用于本地设备，必须记录不适用原因而不照搬性能结论。
- 星落 APK 仅完成只读包目录对照，实际交互、算法和性能均未验证。

### 提交与回滚

本节与六个新源码/测试文件原子提交，guard 的提交 Verification 字段记录最终测试结果，并立即创建唯一的 `recovery/AD-AUDIO-RULES-V2/<timestamp>-<commit>` 注释标签；不以另一次文档提交追写自身 hash。可用 `git log -1 -- app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdMatcher.java` 定位本阶段实现提交，其恢复标签由该提交的本地 annotated tag 标识。

回滚本阶段实现提交即可移除未接线的规则层，保留设计基线与现有播放行为；后续若已经接线，必须先回滚接线阶段再回滚本阶段，不重写历史或移动已发布标签。

## 13. Phase 2 决策补充：执行隔离、时间合同与验收

### 13.1 本轮权限与冻结基线

- 本轮仅更新本文件，不修改运行时代码或依赖；guard 单元为 `AD-AUDIO-RULES-V2-P2-DESIGN`。接续时工作区干净，分支 `dev2`，HEAD 为 `fa8f8959b17dd923397776fa9492152da232a819`。
- 不重新实施 2026-08-18 的旧关键词 Provider 计划；该计划已存在实现。参考 `docs/superpowers/specs/2026-08-18-speech-ad-keyword-provider-design.md` 时，以当前代码和后续修复为准。
- 必须保留 `532be2d7ed5b1562c36d6d85b824d5072d51b849`（语音链路修复）、`9355ea530e467c27d6fb28413c2cf6cb471ce729`（timeline reset 保留会话）及 `4cf2f76e2dea95f0ba69baeb66258d5b1a04035f`（原 Provider）已形成的行为。它们是本地保护基线，不是本轮 cherry-pick 候选。
- Java 制品为 `app/libs/sherpa-onnx-v1.13.4.jar`，SHA-256 为 `c529915aa0c56213678065ad47f3d19c39564555c3a6d95bdbb79e8af82b88fe`；`javap` 确认 Result 有 text/tokens/timestamps/ysProbs，ModelConfig Builder 有 `setNumThreads`，没有通用 ORT session/spinning 配置入口。此检查不证明配套原生库的构建来源。
- Sherpa `v1.13.4` 官方源码固定为 `142807252687d81b40d6315f23470a1512a00de3`；Media3 保持目录中的 `1.11.0-alpha01-fongmi`。**不升级任何 JAR/AAR/SO，不改 JNI，不引入 Vosk**。

### 13.2 当前调用链的具体事实

以下定位均对应上述本地 HEAD；风险是代码推导，尚未在 TV 上复现根因。

| 路径 / 符号 | 已观察事实 | 决策影响 |
|---|---|---|
| `subtitle/RealtimeSubtitleRecognizer.java:147-174`，`acceptStreaming` / `emitStreamingResult` | 只在 endpoint 或 4.8 秒自适应边界发结果；随后 reset。不是每个 PCM 帧都发累计 partial。终点有末 token 时间加 0.32 秒的估算 | 不先写一个复杂的通用 partial 去重框架；透传完整区间，但不得将估算宣称词级精确度 |
| 同文件 `acceptOffline` / `recognizeLoop` / `recognize`（177-230） | VAD 片段按采样数计算起止，后台队列容量 3；队列积压时只保留最新片段，省略较旧识别段 | 被省略的时间不能与后续文本静默拼接成跨句命中；广告 profile 需要连续性标识/重置，不能直接改变字幕现有策略 |
| `subtitle/SpeechRecognitionFactory.java` 与 `RealtimeSubtitleSpeechRecognitionFactory.java:46-80` | 门面和适配器均透传 text/startUs/endUs/token；Session 本身没有并发安全保证 | 复用现有门面，不新建另一套识别 SDK；native 会话必须有单一串行所有者 |
| `ad/audio/SpeechAdSignalProvider.java:308-314`，`recognitionListener` | Provider 转发时丢弃 `endUs` | 2B 恢复终点传递，不能用旧 HostPosition 或回调墙钟补齐 |
| 同文件 `activateLocked`（285-289）、`drainMailbox`（429-445）、`closeRecognitionSessionLocked`（634 起） | 初始化、accept、reset/close 进入 Provider 锁域；Hub 以 `DIRECT_EXECUTOR` 调用 consumer | 慢推理可让音频入队等待 Provider 锁；禁止仅“换成低优先级”后宣布阻塞消失 |
| `player/audio/PlaybackMediaSignalHub.java:247-295` | mailbox 内调用 executor；DIRECT_EXECUTOR 会在调用线程执行 drain，consumer 本身虽不在 drain 的局部锁块内，但外侧 offer/schedule 调用尚未退出 | 不把这个注册方式误认为天然异步。关闭注册与 Provider 锁还可能形成反向获取关系，入口和清理需要解耦 |
| `RealtimeSubtitleRecognizer.release`（123-144）及 `recognize`（219-230） | release 等待 `recognitionFuture.get()`；离线结果回调又可进入 Provider 锁 | 持 Provider 锁等待 native worker 可能产生循环等待；不使用超时后直接释放 native 指针作为“修复” |
| `ad/audio/AdAudioRuntimeController.java:387,390-398,498-504,566-573` | PCM 指纹与 Speech 使用同一 worker，路由白名单只加入固定 `speech-keyword` ID | 2A 分离 worker；2B 同步更新规则 ID 白名单与配置版本，否则新 matcher 命中也会被丢弃 |
| `ad/audio/AdSkipCoordinator.targetFor`（288-307）与 `player/audio/PlaybackMediaClock.Snapshot.mapCaptureToMediaMs` | Coordinator 对 captureEnd 加 media anchor、验证 fresh/generation、钳制 duration，并拒绝已经过去的目标 | 唯一坐标转换与 seek authority 保持不动；修正本文件早先可能导致重复转换的描述 |
| `SpeechAdSignalProviderTest.candidateUsesCaptureTimeAndLeavesDurationClampingToTheCoordinator` | 现有测试明确要求 Provider 不用 media duration 钳制 capture 区间 | 这是保护合同，不把该用例当作旧预期删除 |

同时保留缓冲期间 park 而不销毁模型、seek/flush 后同会话 reset 并拒绝旧回调、模型未就绪 fail-open、旧 ASCII 单词边界和指纹独立策略。

### 13.3 最佳实践证据记录

访问日期统一为 **2026-09-08，Asia/Shanghai**。A 为当前接口/源码直接证据；B 为维护者对特定问题的解释；C 为未经本地复现的外部报告。GitHub 内容经 agent-reach 的 `gh api` 路由读取；本机 `agent-reach` 可执行程序不可用。官方网页由网页读取工具取得，没有修改代理配置。

| 证据类别 | 来源、revision 与已读位置 | 等级 / 支持的判断 / 局限与影响 |
|---|---|---|
| 精确上游源码 | `https://github.com/k2-fsa/sherpa-onnx/blob/142807252687d81b40d6315f23470a1512a00de3/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/OnlineRecognizerResult.java` 与 `OnlineModelConfig.java` | A：Java 公开 token 时间戳和线程数，不公开完整词起止或任意 ORT 配置；采用已有 API，不为本阶段新增 JNI 选项 |
| 精确上游实现 | 同 revision 的 `sherpa-onnx/csrc/online-recognizer-transducer-impl.h`，`Convert` / `GetResult` / `Reset`；`online-transducer-greedy-search-decoder.cc` | A：token 时间来自解码帧，另有 segment/start_time；Java Result 没有 start_time 字段；重置后时间对齐仍须多片段样本验证，不能仅按最新 PCM 末尾推断 |
| 官方运行时文档 | `https://onnxruntime.ai/docs/performance/tune-performance/threading.html`，thread management/intra-op/spinning | A：intra-op=1 不创建额外 intra-op worker；旋转等待、线程池和多 Session 有 CPU/延迟权衡。官方建议不能证明 WebHTV 总线程数为 1，也不能通过未暴露的 Java API“关闭全部 spinning”；只设置可用的线程预算并测量 |
| 官方平台文档 | `https://developer.android.com/topic/performance/threads`，thread priority | A：后台线程仍与渲染线程争 CPU；Android Process 优先级作用于调度，Java Thread 优先级不能作为整套 native 线程的资源隔离证明。仅广告执行入口设 background，音频/视频线程不降级 |
| 上游维护者讨论 | `https://github.com/k2-fsa/sherpa-onnx/issues/982#issuecomment-2160185844`，2024-06-11；关联 PR #989 | B：该维护者说明针对 CTC 的 token 时间性质有限。不能推广为所有模型都有完整词区间；只用其支持“模型相关、必须验收”的边界，不移植该历史 PR |
| 上游现场性能报告 | `https://github.com/k2-fsa/sherpa-onnx/issues/2151` 及 2025-12-16 维护者追问 | C：报告多实例/多线程下的性能下降，但维护者仍要求完整代码；没有可移植 TV 结论。拒绝据此做全局线程池/亲和性/native 重构 |
| 成熟 Android 应用源码 | Sherpa 同 revision 的 `android/SherpaOnnxVadAsr/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt` | A：示例有后台录音/识别与流释放流程，可对照生命周期；它是麦克风应用，不证明 Exo PCM 回调可阻塞，也不授权增加麦克风权限 |
| 独立项目对照 | `https://github.com/alphacep/vosk-android-demo/blob/a5e58ec399135f78325bd3e289849fc3fb57ce96/app/src/main/java/org/vosk/demo/VoskActivity.java`，官方 Android demo | A（边界对照）：其活动/识别服务关系与 WebHTV 播放音轨旁路不同，只作 API/生命周期比较；不照搬 SDK、录音入口或性能结论。旧星落评估只对应 5.8.5，不冒充 6.0.1 证据 |
| 测试/测量与论文适用性 | 当前本地 `SpeechAdSignalProviderTest` / `SpeechAdRuntimeEndToEndTest`；外部现场报告如上 | 本轮阅读旧回归合同而不重复测试。本阶段不改变模型/识别算法，新的算法论文不决定锁所有权设计；没有同款 TV 上可迁移的公开 benchmark，所以实际性能仍是设备门槛，不用论文/博客替代。本项目定向并发测试与实际设备对照才决定是否放行 |

以上覆盖了源码、官方文档、issue/维护者、相关应用和现场报告；不声称已完成真实设备性能证明。临时源码/JSON 保存于 `/tmp/AD-AUDIO-RULES-V2-phase2-evidence/`，持久结论和固定 revision 以本节为准。当前证据已能决定 2A 设计，不再为同一问题扩大搜索。

### 13.4 方案比较与推荐

| 方案 | 正确性/兼容性 | 性能/生命周期 | 结论 |
|---|---|---|---|
| 不改 | 旧关键词可继续使用，但新规则仍无入口 | 共享 worker 与锁等待风险不变 | 保留为回滚基线，不代表满足升级目标 |
| 原样照搬采集器/其他 ASR demo | 引入另一音轨/麦克风、SDK 或模型所有权；不解决 WebHTV capture 坐标与旧修复 | 新增 CPU、内存、包体和 native 生命周期；平台演示不是 TV 播放证明 | 拒绝 |
| 只设 numThreads=1 或只调优先级 | 不解决丢终点/新 ID 路由，也不消除持锁等待 | 可能降低竞争，仍可阻塞或死锁；降线程也可能扩大积压 | 不作为完整修复 |
| WebHTV 适配：先 2A 执行隔离，再 2B 时间与规则接线 | 保留旧配置/字幕路径、capture 合同、单 Coordinator；逐阶段验证 | 单独 native owner、有界消息、不持控制锁推理；可独立回滚，不改二进制 | **推荐** |

#### Phase 2A：执行与生命周期隔离（当前建议批准的最小单元）

1. 创建**语音广告专用串行 owner worker**，不复用 PCM 指纹 worker；所有模型 create、Session accept/reset/close 由该 owner 串行处理。字幕默认创建入口和现有线程策略不变，广告工厂显式传入受限 profile。
2. Host/Hub 回调只更新可见 token/状态或进入有界输入队列；无模型校验、初始化、推理、文件/日志 I/O、Future 等待。不能只把 `recognitionSession.accept` 移出 synchronized 后允许 close 并发释放指针。
3. 控制状态锁不跨 native 调用；识别回调只投递带 instance/session/generation/timeline 的有界结果事件，不等待主线程或 Provider 锁。reset/close 先逻辑失效 token，再由 owner 执行物理 reset/release；释放未完成时不启动第二个替代会话。
4. owner 的唤醒任务合并，PCM/结果均有固定容量；生命周期命令优先于旧 PCM。丢帧/丢识别段标记不连续并 reset 本段，不能跨缺口拼词。对于正在 native decode 的会话，超时只诊断/停用，不并行 free，不伪造“已经释放”。
5. 广告 profile 从 numThreads=1 开始；Android background priority 仅在新广告 worker 内设置。不启用 native affinity、全局 ORT 线程池或新的 JNI 开关。新增固定枚举/计数/耗时诊断，不记录文本、关键词、PCM 或媒体 URL。
6. 保留旧关键词规则、模式、时长与 Prefs；暂不启用新复合规则。新增慢 create/accept/close 和迟到回调的并发测试，先证明音频入口/指纹工作不必等待 ASR，再做设备共存测试。

拟批准路径（不是本轮实际修改授权）：

```text
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProvider.java
app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java
app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioDiagnostics.java
app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactory.java
app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizer.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProviderTest.java
app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdRuntimeEndToEndTest.java
app/src/test/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizerTest.java
docs/AD-AUDIO-RULES-V2-design.md
```

实现若确需另增 owner/配置类或公共接口，先列出最小路径与合同变动，不能悄悄扩大此集合。既有实时字幕回归不可删除或改成永远不验证；只为广告新增 profile，并维持原调用重载的语义。

#### Phase 2B：新规则、时间精度和唯一跳转

- 透传真实识别区间并接入 `SpeechAdMatcher`；保留旧关键词集合，不把 ASCII 单词边界降为任意子串。新规则快照与规则 ID 白名单、路由 version 同步更新，旧 callback 不能使用新快照。
- Phase 1 的整结果起止无法定位结果中间的关键字：后续需加入匹配字符范围和可选 token 对齐数据，保留旧调用重载。NFKC/BPE/字节回退或 tokens/timestamps 长度不匹配时不能逐项硬配；无可靠边界只作明确标识的近似候选，不自动执行。
- `+0.32 秒` 与 VAD 整段终点都不是词边界真值。自动路径必须在合法标注音频上量化“末关键词结束 + post”误差；建议先冻结 **500 ms 最大额外时间误差**作为待批准门槛，超过或无法校准不启用该模型的自动路径。不能把停用自动路径当作整项任务完成，必须完成至少一个目标模型/设备的自动场景验收。
- 只产生 capture 坐标候选；沿用 multiplexer → policy → coordinator 处理最终 seek。至少覆盖从非零 media anchor 开始、seek 后重建时间轴、片尾钳制、迟到结果、指纹/语音同时命中和冲突规则最多一次有效跳转。
- Phase 2B 需要补充批准 matcher/配置/路由的具体范围；UI、导入冲突展示和远程来源仍属于 Phase 3，不并入 2A。

### 13.5 验证、资源与发布门槛

**决定性 JVM 回归**：使用可阻塞的 fake recognizer 和 latch/barrier，而不是 sleep 猜时序。慢 ASR 时 Hub 发布和独立指纹任务可完成；close/reset 立即使 token 失效且 native 不并发释放；积压丢弃后不跨空洞命中；配置替换只接受当前 ID；保留既有 capture/duration、park、同 Session reset、prompt/auto/undo 测试。真实模型不适合用 JVM stub 证明。

**最小构建**：一次 `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest` 的定向相关测试加 Leanback arm64 debug 构建；除相关编辑/不确定失败外不重跑。不为本阶段重建 FFmpeg/Media3/JNI/所有 ABI。共享字幕 factory 的 Android 调用/生命周期由相关测试和这一构建覆盖，不据此声称所有设备均兼容。

**真实 TV 对照**（批准后先冻结样本/阈值，再看结果）：

- 同一设备、OS、APK hash、模型 hash、媒体与解码器、输出/倍速、网络和温度条件；每组先预热，再至少 3 次同长度观测，报告中位数、p95 与离散范围。包括关闭语音、模型未就绪、模型就绪线程 1/2，以及语音+实时字幕同时开启。
- 复用 `PlaybackAnalyticsListener.onDroppedVideoFrames` 的累计计数，以及已有 `onAudioUnderrun` 调试日志；后者当前受 `SpiderDebug` 开关约束，必须两组使用相同开关。不能用 UI FPS 代替视频解码丢帧；不另建一套播放器统计体系。
- 建议门槛：无新增 ANR/音频 underrun/死锁；音频投递 p99 ≤1 ms 且单次不超过 5 ms；5 分钟样本新增视频丢帧不超过 1 帧；额外启动/seek p95 ≤50 ms；目标设备内 ASR 实时系数 <1、队列不持续增长。以上是**待批准的产品门槛，不是行业标准或已测结果**；方差跨越门槛则继续取证，不只取最快一轮。
- 降级只能作为运行时安全保护。若正常目标负载下持续停用识别，说明功能/性能验收未通过，不能按“主播放不再卡”宣布完成。

**设备事实**：2026-09-08 13:40 左右 `adb devices -l` 有 `192.168.50.3:5555/5557/5559/5561` 四个在线端点，显示型号分别为 LIO_AN00、SM_N9700、V1923A、HD1910；没有指定哪个是目标 TV，也没有测试本轮模型。不能凭“ADB 在线”证明有合格 TV 环境。本轮未安装、改设置或播放这些设备；目标 TV 与合法标注音频需在设备验收前落实，不阻止 2A 的代码/并发验证准备。

**资源/包体/回滚**：只新增广告专用 Java worker/profile 与有界队列，不引入第二条 PCM 管线、麦克风、依赖或 native 制品；精确 APK 增量在构建后记录，不能宣称为零。2A、2B 分别原子提交并打恢复标签；回滚先 2B 后 2A，保留用户关键词、缓存、字幕默认行为。功能 flag 默认关闭不等于通过回归；任何 material regression 未解决时不得发布/宣称整项完成。

### Recovery anchor

- **完整目标**：按本文件完成社区指纹兼容、复合语音规则、播放实时性、配置/UI 与受控自动跳过；不把纯规则层或单纯降级重新定义为最终完成。
- **当前状态**：Phase 1 已闭环；本轮只修订 Phase 2 决策文档，无运行时变更。2A 推荐实施但未取得明确代码阶段确认，2B/3/4 未实施。
- **已完成证据**：Phase 1 的 34 项 JVM 测试；本轮精确本地调用链、后续本地修复、Sherpa v1.13.4 源码与 JAR API、官方线程资料及相关 issue/应用对照；未重复 Phase 1 测试。
- **当前文件**：仅 `docs/AD-AUDIO-RULES-V2-design.md`；本次设计提交/恢复标签由 `AD-AUDIO-RULES-V2-P2-DESIGN` guard 产生。
- **风险/门槛**：锁/线程和坐标设计已选定；真实模型词时间误差、TV 资源共存、设备及音频样本仍待后续验证，不能用编译代替。
- **回滚基线**：`fa8f8959b17dd923397776fa9492152da232a819`，`recovery/AD-AUDIO-RULES-V2/20260908133704-fa8f8959b17d`；本轮可仅回滚文档，不影响代码。
- **唯一下一步**：取得明确的 Phase 2A 实施确认，随后在第 13.4 节所列范围启动代码 guard 会话。
