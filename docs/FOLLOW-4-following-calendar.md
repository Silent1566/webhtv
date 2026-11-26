# FOLLOW-4 追剧日历：完整当前季逐集排期设计与实施计划

> 状态：实施中（2026-11-26 13:36 +08:00）。用户反馈5556没有日历后，按已定完整当前季方案实施并安装；尚未通过构建/业务/视觉验收。
> 任务 ID：FOLLOW-4；日期：2026-11-26。guard follow-4 标准代码会话，范围见§7及guard清单；既有本任务文档经明确采用。代码完成需定向验证、原子提交与恢复标签，不push。
> For agentic workers: use subagent-driven-development or executing-plans task-by-task. Repository task guard and one atomic feature commit/recovery tag override generic per-step commits.

**Goal:** 保留 Android 原追剧列表，新增整周、整月与按日分段的当前所追季逐集排期，手机和电视均可操作，不改变追更、通知、已读、观看进度及播放契约。

**Architecture:** 纯日期日历投影 + 按季只读仓库 + 独立原生日历页面。复用 TmdbService 分季接口/缓存，每季一次响应包含全部 episodes；不逐集请求，不改 Room schema、备份或同步协议。

**Tech Stack:** Java / Android Views / RecyclerView / java.time.LocalDate（现有 coreLibraryDesugaring）；不加日历库、Compose、播放器依赖或后台任务。

**Spec:** 本文 §1–§6；任务书 §7–§8；验收/回滚 §9–§10。

## 0. 基线与已定范围

- Android：dev2 @ `6fa23d9788d3e8ffd3cc9dba1a942144bfabdaa5`；检查时干净。
- PC：dev2 @ `754490eac288eca6d20bbe0ca42f1b9618e907f3`；较前轮 7f05770 已变化，本轮重新读取日历源码，不依赖旧快照。
- PC 保护路径：apps/desktop-flutter/windows/flutter/generated_plugin_registrant.cc、generated_plugins.cmake 已有改动；参考库全程只读。Android 本轮仅新增 docs/FOLLOW-4-following-calendar.md。
- 已定：完整当前所追季（不是全剧所有季）；周/月 + 按日分段；保留原列表；手机与 TV 均适配。
- 假设：首次本月、选今天、周一为周首，沿用 PC/中文场景惯例，不新增全局偏好。日历返回后保留列表筛选及滚动。
- 排除：全剧全季扫描、电影排期、为无身份条目猜 TMDB、自动播放目标集、改通知/已读/进度/播放、全局换肤、依赖升级。
- 完成条件：一集一条、同日多集保留、未知日期不伪造、日期不跨时区漂移、双端可达、旧追剧行为不回归。

## 1. 当前源码与复用边界

以下 Android 短路径以 app/src/main/java/com/fongmi/android/tv/ 为根；P/ 指 PC apps/desktop-flutter/。

| 当前事实 | 证据 | 决策影响 |
|---|---|---|
| 已请求当前季 JSON，但只留集数统计 | following/FollowingMetadataClient.java:30–44、:91–114 | 复用分季响应/缓存，新增只读逐集投影，不写回 Following 统计 |
| 仅 nextAirAt 无法支撑整季每集 | following/Following.java:89–99 | 不用下一集替代整季 |
| 旧 airTime 先 UTC 解析再设本地20点 | following/FollowingMetadataClient.java:116–130 | 直接解析原 air_date，不显示虚构时刻，不修旧聚合算法 |
| season 参数与缓存可复用，默认三天 TTL | service/TmdbService.java:54、:197–218、:982–987 | 不逐集请求，不以缩减字段覆盖现有缓存 |
| 旧 requestJson 网络失败静默读无限期 stale | service/TmdbService.java:845–875 | 新增 calendar 专用可追溯响应；旧接口保持，日历不能谎称刷新成功 |
| 原列表自行筛选/排序，读数据库 | ui/activity/FollowingActivity.java:140–197 | 默认仍为列表，只增加日历入口 |
| 已读水位不等于看过 | following/FollowingUpdatePolicy.java:69–95 | 日历只读 watched，不改未读/通知聚合 |
| 原详情可检查、已读、换源、播放 | ui/activity/FollowingActivity.java:366–423 | 日历行点击只定位原列表身份，不复制写入/播放逻辑 |
| 已有主题与双端布局 | app/src/mobile/res/layout/activity_following.xml；app/src/leanback/res/layout/activity_following.xml | 沿用 following_* token、壁纸、焦点样式 |
| 原设备测试固定列表语义 | app/src/androidTest/java/com/fongmi/android/tv/following/FollowingActivityDeviceTest.java:96–127 | 保留默认列表与既有断言，不为新 UI 改写旧期望 |
| 已开 java.time 脱糖 | app/build.gradle:137、:216 | LocalDate 可覆盖现有 minSdk，无需升级 |

## 2. 一手研究与证据限制

访问日均为 2026-11-26。A=官方规范/API/平台；B=固定版本源码/测试；C=问题报告，非规范。文档经配置的读取桥取得，部分使用云提取；季接口/LocalDate 全页有50,000字符截断，但引用关键段已取得。无真实性能数字或运行验收主张。

| 来源/版本 | 等级与支持事实 | 应用/限制 |
|---|---|---|
| https://developer.themoviedb.org/reference/tv-season-details.md （访问日版本） | A：/3/tv/{series_id}/season/{season_number} 返回 episodes；air_date 示例 YYYY-MM-DD，无精确时刻承诺 | 逐季/date-only；nullability 未文档化，须防御空/缺失/非法日期，不声称保证非空 |
| https://docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html （Java8规范） | A：LocalDate 不代表时区/时刻/instant，支持纯日期运算 | LocalDate/YearMonth；today 注入 Clock/ZoneId，不用午夜毫秒加24小时 |
| https://developer.themoviedb.org/docs/rate-limiting.md | A：旧40/10s已撤销，上限可变，必须尊重429；无缓存建议 | 并发2是本地建议，不冒称官方额度；429暂停批次，不紧循环重试 |
| https://www.themoviedb.org/documentation/api/terms-of-use | A：不得缓存超过6个月，需归因/免责声明 | calendar stale最多30天；保留已有归因，日历显示TMDB来源与免责声明链接；非整仓合规审计 |
| https://developer.android.com/training/tv/get-started/navigation | A：D-pad全可达、焦点明确、网格横竖导航、Back不循环 | 完整焦点路径，空态仍有控件可操作，不依赖触摸/悬停 |
| https://github.com/material-components/material-components-android/issues/882 | C：UTC+13日期毫秒混用跨日的用户报告 | 仅取得报告开头，未获维护者裁定；只作风险反例，不给Material实现下结论 |
| P/lib/core/following_calendar.dart、services/following_air_time_service.dart、ui/following_calendar_page.dart，固定HEAD见§0 | B：已有逐集/周月/分段UI与测试 | 借鉴交互，拒绝不适合Android的季号/缺日期退化规则，见下节 |

证据类别：源/测试由PC及本地源码覆盖；官方API/平台规范见表；上游问题/维护者讨论已定向查一次，#882局限明确；成熟实现参考PC。论文/性能博客不适用：不是算法创新/性能基准，不用二手资料凑数。没有取得的证据不写成业界最佳实践结论。

## 3. 三种方案与PC差异处理

| 方案 | 利弊与选择 |
|---|---|
| 不改/仅nextAirAt | 风险最低，但不能满足用户选择的完整逐集周月视图，排除 |
| 原样移植PC | 有现成UI/测试；Flutter/Dart、季号与日期退化语义不同，不可照搬，排除 |
| Android窄适配（推荐） | 复用单季请求/缓存/主题/旧列表；新增纯日期投影、只读仓库与独立页面；无schema/sync变化 |

必须适配而非复制：
- PC请求 `max(0, season)+1`（P/services/following_air_time_service.dart:172–176）；Android已将trackedSeason原值传TMDB（following/FollowingMetadataClient.java:37–40）。Android 0=specials，不能+1。
- PC仅按identity+日期+集去重（P/core/following_calendar.dart:490–494）。Android按规范identity/季/集去重；同集跨日期冲突不能变两集，也不按同名合并不同身份。
- PC跳过非法/空日期（P/services/following_air_time_service.dart:178–191），有部分有效排期即返回（P/core/following_calendar.dart:713–730）。Android保留未知日期集。
- PC nextAirAt配released集号及本地检查日退化（P/core/following_calendar.dart:732–775）不采纳；本地记录另列。其5年/1年日期窗口也不用于截断用户所选完整当前季。
- PC日期点击滚动测试被skip（P/test/phase9_calendar_page_test.dart:402–430）；不把源码或跳过测试当运行验收，Android必须独立验证定位。

包体只增加Java/XML/字符串，不加原生库/运行时；未测APK增量，不给数字。可验证性能约束是按季去重、并发有界、切视图零请求，不承诺更快。

## 4. 日期、身份与状态契约

1. 输入为未删除追剧记录快照，每条仅当前trackedSeason；不开通知/暂停自动检查不等于在手动日历隐藏。只对tv、tmdbId>0、trackedSeason>=0请求官方排期，不猜身份。
2. seasonNumber原样传既有season接口；0为specials。每集以identityKey+季号+正集号稳定标识。同身份/季/集只产一条；同日多集分别保留；不同未归并身份不自动合并或迁移。
3. 严格解析完整ISO日期；null/空/缺失/非法归日期待定。不补今天、季首播、最后检查日或nextAirAt。响应季号不符/非正集号丢弃并计数据警告。同集跨日冲突取响应中首个有效日期并警告，不生成重复集。
4. 日期原样表达TMDB排期日，不承诺某平台本地更新时间，不转UTC/本地instant、不附小时。按日期、剧名、身份、季、集稳定排序。
5. 一轮渲染共用today=LocalDate.now(clock.withZone(zone))；周一到周日，月份用YearMonth。跨午夜、时区变化及回前台重新计算today；已解析日期不变。
6. 未来=待播；今天=今日排期（不推断此刻已播）；过去=已到排期日。这些不证明站源可播。未来不增加日历未看；不回写Following聚合数。
7. 已看只读：watchedSeason与条目季相同且episode<=watchedEpisode。readWatermark不是watched。日历浏览不调用markRead/applyMetadata/save，不写lastError/failureCount或触发通知。
8. 无身份/无配置/分季失败/官方空列表分别明示。真实未知日期集入日期待定；只有本地统计的剧单列本地记录/无官方排期，显示原观看/可播摘要，不假造全季历史。别季nextAirAt不能混入当前季。

## 5. 页面与双端交互

新增独立FollowingCalendarActivity；原FollowingActivity只加日历按钮，原默认列表/筛选/批量操作保持。Back回原实例，保留筛选/滚动。日历不提供标已读/通知/探针等写入操作。

结构：标题+周/月切换 → 上一期/当前范围/下一期/今天 → 七列周条带或月网格 → 当前周/月按日分段的集列表 → 日期待定/本地记录区。首次本月选今天；周月切换保持同一selectedDate；相邻月格弱化且可选，选后切到对应月。

- 日格显示日期、排期集数、今天/选择/焦点状态，不塞海报。月行数4–6随月份，不能硬编码5行。
- 分段显示可见周/月所有有排期日；选日定位分段，空日显示当天暂无官方排期但保留其它分段及未定区，不把空日当整月空。
- 集行显示海报、剧名、S/E、可空集名、日期关系、观看信息、TMDB来源。使用Activity Result返回：setResult(RESULT_OK, Intent携带EXTRA_IDENTITY_KEY)后finish；原页通过launcher接收，复用focusIdentity定位，仅高亮、不自动播/标已读、不新建原页实例。若原筛选隐藏该身份，提示条目不在当前筛选，不擅自清除筛选。
- 手机目标至少48dp；窄屏换行、横竖屏可滚动；翻页不依赖手势，不引入同向嵌套滚动。
- TV左右按日/上下按周焦点，OK选择并定位分段；工具条→网格→分段列表及反向均可达。focus与selected分别表达，沿用focus_ring_primary；空态仍有返回/今天可操作。
- 只增刷新排期动作，不触发站源检查或通知。进行中禁重复，显示完成/失败季数与重试。FollowingCalendarViewModel持有仓库与不可变快照，旋转时复用在途；SavedInstanceState保存mode/selectedDate/范围/行身份供进程重建，重建只走普通缓存优先读取、不强刷。onCleared关闭仓库；界面不可见暂停新批次，旧回调不持有Activity。复用项目已有ViewModelProvider模式（ui/activity/TmdbDetailActivity.java:12604）。
- 文案放values、values-zh-rCN、values-zh-rTW及仓库既有对应语言资源，沿用主题/壁纸/翻译策略；不硬编码中文到Java、不改全局色板。

## 6. 新接口与缓存请求决定（均待实现）

Policy为无Android/IO的纯Java，嵌套不可变类型避免无意义文件拆分。拟定接口：

```java
// following/FollowingCalendarPolicy.java
static LocalDate parseAirDate(String isoDate); // 非法/未知返回null
static LocalDate weekStart(LocalDate selected);
static CalendarProjection project(List<Following> items,
        Map<String, SeasonSchedule> schedules, LocalDate today);
// schedules按identityKey索引；SeasonSchedule含该身份当前季episodes与来源状态。
// CalendarProjection: NavigableMap<LocalDate,List<Entry>> days;
// List<Entry> undated; List<LocalRecord> localRecords; List<Warning> warnings。
// Entry含identityKey/seasonNumber/episodeNumber/name/date/source/temporalState/watched。

// service/TmdbService.java，仅新增calendar专用响应，旧season签名/语义不变
public CalendarSeasonResult seasonForCalendar(TmdbItem item, int seasonNumber,
        TmdbConfig config, boolean refresh) throws Exception;
// result含body、NETWORK/FRESH_CACHE/STALE_CACHE、cachedAtMillis、sanitizedError、retryAtMillis。
// retryAtMillis=0表示未限流；429+stale时仍携带冷却时间，仓库先暂停批次再展示缓存。
// 此路径专用RateLimitException含retryAtMillis，无缓存失败抛异常。

// following/FollowingCalendarRepository.java
void load(List<Following> snapshot, boolean refresh, Callback callback);
void cancel(); // 代次递增、停止排队、取消Future、忽略迟到结果
void close();  // 释放执行器/页面引用
// callback为不可变CalendarSnapshot+进度；控制器投递UI线程。
```

- seasonForCalendar复用现有URL/参数/缓存文件键，不以缩减正文覆盖原缓存，显式保留命中时间/来源；旧season/requestJson调用方行为不变。
- calendar默认用现有三天TTL；失败最多用30天旧缓存，明示缓存时间/刷新失败。不得把读取时刻当获取时刻；超期不可用。只约束新日历，不改全局缓存治理。
- 首入按季加载，并发<=2；同会话(tmdbId,season,language,配置代次)合并在途。周月切换/选日/滚动只投影，不联网；不新增持久数据库。
- 配置API/凭据/语言或跟踪季变化使相关会话结果失效；代次由内部快照比较生成，凭据不写键/日志/UI。
- 429暂停本批后续任务，遵守Retry-After；无头则页面冷却60秒，显示稍后重试，不自动紧循环重发。返回旧缓存也必须在result中附retryAtMillis，不能把限流吞成普通缓存成功；仓库读取该字段后停止继续排队。不是全局限流框架，不改其它TMDB消费者。
- cancel/close停止新任务、尽力取消在途Future并以generation挡迟到回调。不能声称Future取消必然终止OkHttp；在途受现有网络超时约束，退出后不得继续排队/写页面。
- 不发单集详情/其它季，不自动搜身份。日历失败不写Following.lastError/failureCount。

## 7. 执行拓扑与文件边界

实施采用Hybrid：先冻结Policy/响应接口，再分离数据读取与UI；不让两个child并行改同一seam。实施前重查HEAD/dirty，声明实际路径并task_guard start。本轮不启动代码会话。

- 核心新增 following/FollowingCalendarPolicy.java、FollowingCalendarRepository.java；service/TmdbService.java仅calendar路径与最小私有复用。Following/DAO/UpdatePolicy业务契约不动。
- 页面新增 ui/activity/FollowingCalendarActivity.java、model/FollowingCalendarViewModel.java、ui/adapter/FollowingCalendarDayAdapter.java、FollowingCalendarEntryAdapter.java；FollowingActivity.java仅Activity Result入口/原身份定位复用。
- main/AndroidManifest.xml注册；mobile/leanback activity_following.xml加入口，各增activity_following_calendar.xml、item_following_calendar_day.xml、item_following_calendar_entry.xml。字符串及必要selector复用既有token。
- 测试新增 following/FollowingCalendarPolicyTest.java、FollowingCalendarRepositoryTest.java、service/TmdbServiceCalendarCacheTest.java；androidTest/following/FollowingCalendarActivityDeviceTest.java。原FollowingActivityDeviceTest保留。
- 文档唯一本文，后续研究/修复/验证/提交/回滚均更新此处，不另建plan/spec/fix文件。

## 8. 任务书（实现均未开始）

### T1 纯日期/逐集投影（runs 0/3）
- [ ] 新增失败测试，以冻结today=2026-11-26验证日期/身份/季号/去重；失败点为Policy不存在。
- [ ] 实现§4/§6的Policy及嵌套不可变类型；不IO、不读系统时钟、不修改输入实体。
- [ ] 针对测试通过，并确认FollowingIdentityTest/FollowingUpdatePolicyTest保持；审查无状态写入。
```java
@Test public void unknownOrInvalidDatesAreNotToday() {
    assertNull(FollowingCalendarPolicy.parseAirDate(null));
    assertNull(FollowingCalendarPolicy.parseAirDate("2026-02-29"));
    assertEquals(LocalDate.of(2028,2,29), FollowingCalendarPolicy.parseAirDate("2028-02-29"));
}
@Test public void mondayBoundaryAcrossYear() {
    assertEquals(LocalDate.of(2026,12,28), FollowingCalendarPolicy.weekStart(LocalDate.of(2027,1,1)));
}
```

### T2 单季读取/来源/取消/限流（runs 0/3，依赖T1类型）
- [ ] fake season reader与Clock测试：同季合并、并发<=2、旧代次不回调、配置变化不串结果。
- [ ] TmdbService缓存fixture覆盖NETWORK/FRESH/STALE<=30天/超期/无缓存失败，固定原season行为。
- [ ] 实现§6；把JSON投影为T1类型，不调applyMetadata/Store.save，错误用TmdbService.redactMessage。
- [ ] 429/Retry-After（含stale成功但必须停止队列）与取消后停止排队测试；成功季可先显示，失败季不吞已有内容。

### T3 双端页面与旧入口（runs 0/3，依赖T1/T2冻结）
- [ ] 先捕获原追剧列表Before，用固定样本验证新增入口/周月操作缺失。
- [ ] 连接§5页面/分段/未定区/错误/恢复状态；原页面只加入口与定位，列表动作不变。
- [ ] TV完整D-pad/空态/Back，手机横竖屏/大字体；点击集行只定位身份，日期选择实际滚动到分段。
- [ ] 同场景/视口/主题After；新页面无可靠Before可After+说明，旧入口变化仍需Before/After。

### T4 Lead接受与原子交付（runs 0/3）
- [ ] 读最终diff逐项核对§9；发现问题返对应brief，不把已知缺陷仅写风险后照常交付。
- [ ] 每个最终检查一次，除非相关编辑/结果不确定。不重建播放器/native，不跑全ABI。
- [ ] 本文追加真实结果和未验收场景；task_guard finish仅提交任务路径并创建注释recovery标签，不push。

## 9. 实施后的最小验证（本轮未执行）

需JDK21，系统PATH的Java8不可用于构建。variant来自app/build.gradle的mode/abi，Windows用gradlew.bat等价调用。
```text
./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*FollowingCalendar*' --tests '*TmdbServiceCalendar*' --tests '*FollowingIdentityTest' --tests '*FollowingUpdatePolicyTest'
./gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug
```

| 场景 | 接受结果 |
|---|---|
| 同日E1/E2、次日E3、重复响应/跨日冲突 | 三个独立集；计数准确；同集不重复，冲突警告 |
| 季0/1/2、别季nextAirAt | 原样季号，specials不变S1，别季不填当前季 |
| 闰日/跨月跨年/月份首周偏移 | 正确7列、4–6行、周一开始、日期定位与分段一致 |
| 上海/洛杉矶/UTC+13/DST | 相同air_date归同日；today随地区，排期不附虚构时刻 |
| 空/非法日期、无身份/凭据 | 分别待定/本地记录/配置原因；不补今天/自动搜索 |
| 过去/今天/未来 | 已到排期日/今日排期/待播；不宣称可播或此刻已播 |
| 标已读未看、已看其它季、墓碑 | 不混read与watched，不串季，删除条目不展示 |
| 离线/旧缓存30天内/超期/部分失败/429 | 来源时间明确，超期拒用，不紧循环重试 |
| 快速切换/旋转/退出/改TMDB配置 | 切视图无请求，状态恢复，晚回调不写页面，配置不串结果 |
| 旧列表检查/已读/继续/通知/备份 | 行为及数据字段不被日历改变，原设备测试保持 |
| 手机/TV代表视口、明/暗主题 | 控件不裁切、焦点可达、空态能返回，有同条件视觉证据 |

无设备时必须说未完成视觉/交互验收，构建成功不等于功能通过；本轮没有执行上述命令。

## 10. 回滚、风险路由与Recovery anchor

- 实现作为一个原子功能提交，revert恢复旧页面/入口；无schema/sync迁移或用户数据回退。
- 已发现PC季号/日期降级/跳过滚动测试风险：不原样迁移，Android独立规则与回归验收，不修改PC参考库。
- 旧airTime/无限stale是相邻现有行为，不在此任务全局修复；新calendar-only读取与date-only投影绕开，并用旧接口回归固定边界。若保持不了，hand_back给Lead重新定界，不暗扩责任。
- 未测包体/速度/内存，不作数值承诺；本文件不是整仓安全/法律/兼容性审计。
- 目标/接受：§4/§9；状态：设计完成、生产未开始，仅本文新增；官方研究和PC契约brief各run 1/3已返回并由Lead复核。构建/测试/设备/截图均未执行，未commit/tag。
- 基线/保护：§0；实施前重查Git/JDK/设备。本轮文档撤销仅删除本文，后续功能提交需记录完整hash与recovery标签。
- 下一动作：按T1开始业务实现前的工作区/工具链与截图基线检查；此描述不代表已获新代码实施授权或已开始实现。

## 实施记录（2026-11-26 13:36 +08:00）

- 用户截图显示缺少日历，当前进入实际功能实现与5556安装交付，不再止于设计文档。
- 工作位置保持当前dev2主工作副本：已有热Gradle/ABI产物与设备数据，避免新工作树重复准备；不创建/切换分支。guard follow-4已声明窄路径，唯一初始dirty本文为任务自有并明确采用。
- Before：F:/temp/webhtv-follow4-evidence/before-5556.png；1920x1080、density420、现有浅色追剧列表；设备原有数据不清除。
- 计划自审：T1输出Policy不可变类型供T2/UI消费；T2只改TmdbService及仓库；UI独占页面/资源，不能改核心API；Lead只改本文与做验收。旧列表动作与新只读日历无写入责任互换。
- core brief run1/3实施中，先冻结接口后UI；ui brief run0/3；Lead review/build/device run0。
- Ruling: 范围决策与研究已收敛，当前用户“没看到日历”按实现并安装的完成目标处理；不重新问界面/技术选项，保留方案中的兼容约束。
- 下一动作：收到core冻结API后派发UI，并对core最终diff做独立验收。

- core seam已冻结并通知UI（Policy条目/投影及Repository snapshot接口），core T1 expected RED已观察为缺少Policy（core-red.log，exit1）；UI brief run1/3已派发，严格独占页面/资源/manifest/UI测试，不改core接口。后续仍须Lead读取最终diff和独立验证。

- 核心初测：Policy6/6、Repository6/6、旧identity/update/cachekey/redaction/route/credential26/26通过；calendar cache fixture出现plain-JVM初始化问题4失败，core正在修。Gradle验证改为串行，UI暂停Gradle避免同variant结果目录竞态；Lead尚未执行构建。

- Lead静态review（13:59–14:03）：TmdbService新增calendar-only来源/有界stale及429短路，旧入口委派保持；Repository generation与<=2调度已检查。UI发现App.post无owner token、onStop无条件取消旋转在途、完整snapshot返回前台不检查配置/季变化3项缺口，已路由UI作者修复并补测试，未接受/未构建。View导入编译错已由UI修复；不计为独立重派。
- 真实5556缓存：当前追剧TMDB307853、S1，共24集，排期2026-11-06至2026-11-17，可用于后续真实After；现有追剧状态快照在F:/temp/webhtv-follow4-evidence/before-db/state.json（未打印私密配置）。

- core run1/3 ready：最终组合50 tests/0 failures/0 errors，含Policy7、Repository6、calendar缓存5、旧回归26及当时UI6；log F:/temp/webhtv-follow4-evidence/core-final-green.log，归档XML core-final-xml/。Lead读实际XML并审查transport/scheduler seam，核心接受；后续UI修改另行目标验证。

- UI收到Lead第二批呈现缺口：FUTURE/UNKNOWN误附未看、旧缓存Entry错误未显示、今日零排期无标记；作者确认同边界修复并追加测试，按其报告记录UI brief run2/3，保留最后1次必要视觉回改。核心最终验证已释放Gradle给UI，Lead尚未assemble。

- UI brief run2/3 ready：最终UI过滤12 tests/0fail/0error/0skip，F:/temp/webhtv-follow4-evidence/ui-run2-green.log（18s）。Lead重读VM epoch与Activity可见/旋转guard、Entry future/unknown与stale错误、today标记后接受代码；尚待视觉验收。
- Lead于14:15启动一次双端assemble（mobile/leanback arm64 debug），job pwsh-4ad6b7af14f54197，日志follow4-assemble.log。最初25–40分钟目标已到，因review发现生命周期/呈现风险修复延迟；停止可选工作，后续只build/install/device/close。
- 下一动作：收集该构建结果，成功后覆盖安装mobile到5556，验证入口/月/周/日期定位，失败则只处理直接阻断。

- Build一次实际执行成功（wrapper首调用转义失误未启动Gradle，改绝对路径后成功）：mobile+leanback arm64 debug BUILD SUCCESSFUL，5m56s，follow4-assemble.log。Mobile新包14:18产物，14:22覆盖装5556 Success。
- 视觉第一轮：入口真实可见；月视图24集排期计数、11/6三集定位验证成功；首次自动滚动将标题与周/月控件挤出屏，已交UI run3/3最后一次窄修（只显式选有排期日期滚动，初入/空日保持顶部）。其余状态/布局不做可选美化。
- 截图：current-5556.png入口；month-top-5556.png手动回顶部；selected-nov6-5556.png真实日分段；新日历页没有改前版本，After-only备注。

- 设备验收（mobile/5556）：追剧页可见“追剧日历”；月视图2026-11，TMDB307853当前季24集在6/7/9/10/11/12/13/14/15/16/17日计数正确；点11/6定位“11月6日 五 · 3集”与E1-E3行；集行只读状态正确（E1-E3已看到，E4未看），来源“缓存排期·缓存于11月26日”。点集行回原追剧列表定位。
- 数据保持验证：安装前后following表1行完整JSON一致（watched_episode=3、read_watermark=0、notify等不变），未清数据。
- 余一项视觉缺陷：初次进入自动滚动隐藏标题/周月控件；UI run3/3最后窄修中（仅用户点有排期日期滚动，posted有可见/代次保护）。之后需增量build+覆盖安装并After确认。

- UI run3/3 ready：首次/翻期/周月/今天不滚动；只有显式选日且有分段才滚，posted任务核对binding/visible/epoch。Lead审查修复代码与实际XML：15tests/0fail/0error/0skip，ui-run3-green.log。第二次实际assemble仅因相关scroll修复而运行，日志follow4-final-assemble.log，job pwsh-63950c1efcb140a1。后续只做最终安装、同场景确认与原子交付。

- 最终实机验收（mobile arm64 debug 覆盖安装5556，未清数据）：初次打开停在顶部，pageTitle/周月切换/上一期·下一期·今天·刷新/2026年11月/状态均可见；点击11月6日仍定位“11月6日 五 · 3集”分段（S1·E1-E3，只读已看到E1-E3）。证据 final-initial-5556.png、final-nov6-5556.png。双端assemble成功（follow4-final-assemble.log，3m52s）。
- 已验收：Policy/Repository/日历缓存/旧回归50通过；UI单元15通过（含3条scroll回归）。未执行：androidTest设备契约（FollowingCalendarActivityDeviceTest 4例）、leanback实机遥控、暗色主题截图、旋转与跨午夜实机复验 —— 均按未验收记录，不宣称通过。
- After-only说明：日历为新页面无改前截图；追剧页入口变化有 before-5556.png 与 current-5556.png 同视口/主题对照。设备为1920x1080/density420/API28/浅色主题。

## 视觉改版（follow-5-visual，2026-11-26 15:00–15:1x）

- 用户反馈：默认应为周视图、样式需要美化。默认由月改周（状态里切到月仍按状态恢复）。
- 顶部：`‹ 11月23日 – 11月29日 ›` 大标题导航 + 今天/刷新改为文字按钮，状态压成一行小字。
- 日格：默认无底无边（去掉整片灰格子），今天实心主色 + on_primary 数字，选中浅主色 + 主色描边，焦点同色环；排期数改为小号主色数字，0 时不占位。
- 集行：改扁平层次——剧名只在当天第一行出现一次，剧集行（S/E·集名）成为主信息，状态只剩「看过/未看」与例外状态；排期来源与缓存时间上提到日期标题（同段同源时）。
- 空态拆分：无任何追更 vs 本周/本月没有排期，文案与指引分开，不再把「本周无排期」说成「还没有追更」。
- 两端布局同构同 id，电视端放大字号/焦点目标；测试同步为「默认周视图」契约。
- 视觉证据（mobile/5556）：final2-week-5556.png（周默认+空态文案）、final2-rows-5556.png（月视图行层次）。电视端仅构建通过，未实机验收。

## GitHub日历视觉再研究（2026-11-26，仅研究，未实施）

### 目标、基线与范围
- 用户明确重点是界面好看，授权GitHub检索追剧/追番日历参考；不再把颜色/圆角/抽象布局选项交给用户猜。当前业务基线0d6ac40dd89f1d6e0cf27ad43459132cf28ef53f；本轮不改业务代码、设备或配置，只追加本文。
- 已定：默认周、保留月、逐集/未知日期/只读观看状态等既有数据契约继续。假设：继承WebHTV蓝色色系及明暗主题，不重新品牌化。
- 排除：凭README功能宣称已看过UI、把发现/详情/Shows列表当日历、照抄iOS玻璃/遮挡浮层、再次只改数字颜色与圆角。完成条件是有真实图/固定来源/实际源码及明确的适配判断，不是功能实施或视觉验收。
- 证据分两路：Android/calendar-android-inspiration run1/3；Web/calendar-web-inspiration run1/3。Lead实际read_image与固定源码独立复核，报告不是自动接受。研究约15分钟，原8–12分钟估计超出因多张官网/README图实际不是日历及读取fallback；不再扩展项目。

### 有效案例与证据边界

访问日2026-11-26（UTC+8）。截图来源时间/版本不等同代码HEAD；以下只声明实际看到的画面。

| 项目 | 固定版本与许可 | 实际证据 | 可用结论/限制 |
|---|---|---|---|
| MoviePilot App（主视觉参考） https://github.com/jxxghp/MoviePilot-App | 67ca9aca583608bb4034fe17f3ee1426032959a3；GPL v3 | [官方日历原图](https://raw.githubusercontent.com/jxxghp/MoviePilot-App/67ca9aca583608bb4034fe17f3ee1426032959a3/screenshots/ios/native-v2-live-calendar.jpg)，screenshots/README.md:61–63明确日历 | 真正周日期条+当天节目列表；窄选中胶囊、小排期点、标题/季、集列表、进度三层；图上多张海报空位、底部导航与机器人遮挡也是事实，不照搬 |
| MoviePilot Frontend（单独代码参考） https://github.com/jxxghp/MoviePilot-Frontend | main 6fdbc8104cabf09441572d6ecd444a59b74b2195；MIT。默认v3为98a71572f98858289459eabd37d543e73f9737d8，本轮未读取v3日历 | [FullCalendarView.vue](https://github.com/jxxghp/MoviePilot-Frontend/blob/6fdbc8104cabf09441572d6ecd444a59b74b2195/src/views/subscribe/FullCalendarView.vue):23–44、138–198、206 | 月格内海报+剧名/集数，今日轻底；没有该版本月格实图，不能拿App图当Frontend图。Android/TV不可依赖hover tooltip |
| Showly（Android列表/组件参考） https://github.com/trakt/showly | master e08cb90ba0205ed9401a11265b912b0302f253b5；GPL-3.0 | [官方Shows Calendar小组件图](https://raw.githubusercontent.com/trakt/showly/e08cb90ba0205ed9401a11265b912b0302f253b5/fastlane/metadata/android/en-US/images/phoneScreenshots/08.jpg)；ui-progress/src/main/res/layout/view_calendar_item.xml:19–38、61–106与view_calendar_header.xml:15–23 | 小组件是真按Today/Next Week分组的海报行，不是应用全屏网格；应用calendar源码是日期分组列表。README详情图/02入口页不能证明calendar正文视觉 |
| SeriesGuide（Android层次参考） https://github.com/UweTrottmann/SeriesGuide | dev 1bdc18b10132689fe4751193c7ee26f316a73649；app GPL-3.0-or-later，API Apache-2.0 | screenshots/phone.png是Shows列表；app/src/main/java/com/battlelancer/seriesguide/shows/calendar/CalendarFragment2.kt:69–78、129–130 | 代码证实按宽度调整列表列数并区分空态；只有Shows图，没有取得calendar实图，不称其为漂亮网格样板 |
| Seanime（仅代码启发） https://github.com/5rahim/seanime | d4d58b22fa690093da572083d163f60cc8725104；GPL v3 | seanime-web/src/app/(main)/schedule/_components/schedule-calendar.tsx:30、304–313、410–454、524–580、760–769 | 最多4事件/格、海报背景、今天小白圆、移动分组列表；官网Discover配图实际是发现页，非日历。未取得真实日历图，不能称亲眼验过其视觉 |

许可说明：以上允许借鉴设计思路；若复用代码需履行对应许可，不能因为开源就默认素材可再分发。本任务不搬运代码/海报/品牌资源。前述子报告“GPL不可复制”的绝对说法不采用，GPL允许遵守许可的复用，具体整合另行核对。

### Lead视觉判断与推荐（不是已实现的新界面）
1. 主方向借MoviePilot App的“紧凑7日导航+马上可见的节目”，而不是大面积七列数字。一格把星期/日期/排期点作为整体，选中只占小胶囊；不铺整日蓝色矩形。
2. 日期是导航、剧集是正文。横屏限制日历区的伸展，把海报/剧名/当日集号同时放在首屏；竖屏保持低高度周条接节目卡片。月模式保留，但作为紧凑日期选择，不再撑满横屏。具体响应式布局是WebHTV适配建议，不冒称参考图里已有横屏方案。
3. 海报尺寸稳定、有真实素材就用；缺图给克制占位。节目排版按“剧名+季 → 当日集号/集名 → 次要观看/排期来源”三层，不让缓存时间和诊断数字抢标题权重；错误及日期待定仍可见。
4. 今天、选中、焦点各自有语义：今天用小圆/文字标识，选中用小范围浅底或描边，TV焦点另有外环。当前DayAdapter今天+选中字色压蓝底的问题必须在后续实现中用状态对验证修复，不能因本轮未改代码就声称已修。
5. 不直接照抄MoviePilot大圆角/玻璃材质/底部遮挡。Showly/SeriesGuide只取日期分组和海报行密度；Seanime没有实图，不据代码给整体美观背书。既有默认周与全部数据语义不变。

### 验证、限制与交接
- Lead实际查看MoviePilot日历原图、Showly日历小组件、SeriesGuide Shows图、Showly详情与Lists排除图；核对MoviePilot截图索引/Frontend源码/两份LICENSE及Showly、SeriesGuide固定源码。外部页面通过read_page原文/云读取桥，关键段已取；不使用搜索摘要作实现证据。
- 原始图片与完整报告保留F:/temp/webhtv-calendar-inspiration/：web-moviepilot-ios-calendar.jpg、android-showly-widget-calendar-08.jpg、android-seriesguide-phone.png、web.md、android.md、lead-review.md。MoviePilot原图SHA256=36c61e5b5a7c5f7c7411623718ff116c9fe9f194931e5ec98b42712c77c2e440。
- 此轮没有构建、测试、安装或源码改动。未取得SeriesGuide/Showly应用内calendar、Seanime/Frontend月格实图是证据限制，不把它们表述为项目没有截图/没有日历能力。
- 下一动作：按MoviePilot紧凑周条为主参考形成WebHTV真实内容的单屏视觉稿，再做有界实现与同场景Before/After；不再直接改Android XML碰运气，也不让用户反复选择抽象风格。

## 日历界面重构执行（2026-11-26 16:12 +08，follow-4-surface-redesign）
- 用户授权开始，玻璃效果允许，核心目标是真正好看；主参考MoviePilot周条/海报排期，不照抄素材。采用既有蓝色色系、兼容浅/深主题，先单屏比例稿，Lead看图后实施。
- Lane standard，guard follow-4-surface-redesign；唯一初始dirty为本文GitHub研究，任务自有明确adopt。Base HEAD0d6ac40dd89f1d6e0cf27ad43459132cf28ef53f；工作目录/分支dev2不变。
- 允许仅日历Activity/两个Adapter/双端和land布局/日历专用drawable+colors+字符串+相关测试，核心数据/VM/DB/通知/已读/播放不变。完整路径在guard，child最多24生产文件。
- 先视觉稿A（最多8min）再代码B（约15–20min），Lead构建/5556明暗BeforeAfter与收尾（约15–20min），目标总35–50min、17:00前收敛；这是预算目标，不削减验证。
- 质感决定：半透明霜玻璃面、细高光边和柔和背景；API28不用持续实时blur，不新增原生/第三方依赖。不是用彩色大卡框替代信息层次。
- 拓扑：一个紧耦合UI执行者calendar-surface-redesign run1/3，公开数据seam冻结；Lead专属文档/图审/review/build/install。设备5556 Before在F:/temp/webhtv-calendar-redesign/before-5556.png（1920x1080/density420）。
- 风险必须覆盖：today+selected前景状态对、横屏731dp实际可用宽、日历与内容同屏、独立滚动后的日期定位、混合来源可见、空日/未来/未知不伪造、TV焦点与大字体、旋转late callback。
- 下一动作：收到mock-landscape.png审比例，回传Lead决定后进入同brief代码实现；不再问用户颜色/布局。

- Stage A（约16:20）Lead已看mock-landscape.png与mock-portrait.png：48dp整合头、116dp霜玻璃日期面、44dp窄选中视觉/48dp点击、独立agenda、真实11/6 E1-E3集名。不是native截图、海报为中性占位。接受该视觉方向，进入同brief B。
- Lead图审修正：横屏agenda在每列>=300dp时自适应双列，日期分段header/empty/local满span，竖屏单列；避免长卡右半留白。追加第8日真实2集event点，production依count而非mock。对应mock仅覆盖一次，不额外审批。
- 时间/任务：surface-redesign run1/3（stageA已接受，stageB实施中），Lead只文档/图审与后续测试安装；不会把mock当native视觉通过。下一动作：收到B final读diff与实际测试后build/install，最多一批native视觉修正。

- 验证路由决定：mobile未声明Robolectric，leanback已用4.16。Lead允许仓库外preview.gradle init-script为mobile定向测试临时注入同版本缓存，以--offline执行真实Activity/adapter测试；不改build/lock/生产依赖、不下载新依赖，失败报告明确缺项而非扩展。对应temp cap不变，run仍1/3。

- 设备验收准备：同app UID(run-as)可启动未导出日历，避免重复首页warmup；旋转原值accelerometer=1/user_rotation=0，夜间系统no。验收前后恢复系统主题/旋转；仅提取theme_mode字段，不输出整份凭据/配置。

- expected RED已观察：26tests/3fails（wide仍linear/today+selected前景/mixed source遗漏），23通过。Robolectric缓存4.16 offline可用，测试禁用Conscrypt仅处理Windows宿主JNI；无生产依赖改变。Lead要求tracked mobile tests不得依赖temp init，真实Robolectric用例移临时独立测试源或Instrumentation等价，裸mobile仍可运行；临时总cap仅为此额外.java扩为9。

- Lead进行中diff复核：wide agenda改GridLayoutManager(2，当每列>=300dp)、分段header满span；agenda独立bounded滚动，日期定位用scrollToPositionWithOffset；保留VM/Activity可见代次保护。
- 窄审查回传作者：来源格式化复用render today不用LocalDate.now；日格48dp与内容实际51dp需measure防裁event点；海报直接ImgUtil.getUrl异常必须降级占位不崩页；本任务XML按标准缩进恢复可维护性。相同brief run1，不新开功能/文件。
- 实机明暗策略：当前theme_mode=-1跟随系统，可用cmd uimode切换并恢复no，设备原rotation 1/0；不改App持久化偏好。下一动作：作者最终验证交接，Lead一次build包含双端与androidTest编译，继而原生截图。

- Surface run1/3返回，裸mobile22(Activity15+Policy7)与临时真实Android5项通过，核心/VM/build未变。Lead发现报告local满span与代码不一致、设备测试旧全holder/scroll假设未更新，已回原作者run2/3窄补：local独立type满span+androidTest语义修正，不再设计/跑并发构建。下一动作收到窄补立即build双端+androidTest，实机明暗/定位。

- Surface run2/3窄补按指示完成3文件：spanLookup type!=1满、local独立type2、androidTest改为bounded agenda/真实月份滚后选/ActivityResult内容校验。Lead审查该diff与type映射，裸15无接口变化；进入唯一一次build（mobile+leanback+androidTest），日志build-both.log。

## 界面重构实施结果（surface-redesign run1–3 + Lead原生收口，2026-11-26 17:3x）
- 交付形态：一体头部（标题+周/月+今天+刷新，48dp）→ 霜玻璃日期条（今天实心小圆、selected窄胶囊、focus外环、事件小圆点）→ 独立bounded agenda；横屏内容宽≥608dp时两列（每列≥300dp），竖屏单列；分段header/日期待定/本地记录满span，逐集一集一条保留各自海报、S/E集名、来源/缓存时间/错误。
- 默认周保持不变，月可切；横屏月条最多2行并可内部滚动，Lead补“月视图：上下滑动日历可查看整月”提示，避免被误读成只有两周；竖屏月最多6行。
- 数据契约未动：Policy/Repository/TmdbService/VM/Room/通知/已读/播放/构建脚本全部零改动；日历仅展示与导航。
- 测试：tracked裸mobile 22通过（Activity15+Policy7，无Robolectric依赖）；临时真实Android套件5通过（周默认/月恢复、两列66集bounded、远端日定位后标题仍在、空日不滚、48dp实测不裁点、today+selected前景、混源+坏URL不崩、竖屏单列）；expected RED 26/3failed留存。
- Lead原生收口两处只有真机可见的缺陷：三份activity根同时用fitsSystemWindows与padding导致系统清零padding（左侧文字被切），移除fits后14/10/14生效；空排期提示改占满agenda剩余空间并居中，不再留半屏死白。
- 实机验收（5556，1920x1080/density420/API28）：浅色周默认+空态居中、月视图两列真实海报与11月6日三集、选6日后窄胶囊高亮且agenda定位、暗色深蓝主题同样可读；暗色/系统主题已恢复no，旋转原值1/0保留。
- 数据保持：安装前后following表逐字段一致（watched_episode=3、read_watermark=0、notify不变），未清数据。
- 未验收：leanback实机遥控焦点与TV暗色；竖屏实机截图（仅XML/单元验证）；旋转/跨午夜；androidTest仅编译未在设备执行。以上均未宣称通过。
- 证据：F:/temp/webhtv-calendar-redesign/{before-5556.png,after-week3-5556.png,after-month3-5556.png,month-day6-selected.png,after-dark-5556.png,build-final*.log,test-*.log,mock-landscape.png,mock-portrait.png}。

## 竖屏可用性修复（2026-11-26 17:44 +08，follow-4-portrait-usability）
- 用户三项：5556高宽/顶部刘海；月历固定占高导致滚动agenda太小；提示/来源文案挤两行且无须显示TMDB域名。
- 已查设备硬事实：physical1080x1920/density480、override420，高刘海cutout.emulation.tall enabled，DisplayCutout top144px。Lead关闭tall叠加层，wm density reset原生480，分辨率保持1080x1920，原数据不清。before.png与before-normalized.png保留在F:/temp/webhtv-calendar-portrait-fix。
- 代码根因：竖屏render maxRows=6、日历固定在weighted agenda之上；页脚长hint+含themoviedb.org按钮横排。采用项目现有Material Coordinator/AppBar联动收起、月grid上限3行但完整月数据可滚、常驻短来源链接（无可见域名）与去工程提示。无产品/数据协议新决策。
- 基线52176f9118bc1ebbda217eb55d6968a3b1f3277d、dev2干净，guard已启动，scope仅日历展示12文件+本文。允许paths由guard列出，不改Repo/Policy/VM/TMDB/通知/已读/播放。
- 拓扑：一个紧耦合UI作者calendar-portrait-usability run1/3，Lead处理设备/文档/最终验收build。目标20–30分钟（实现约12–16min；build/install/360x640dp month验收约8–14min），约18:10前收口。
- 目标反馈：真实Activity360x640dp month初始agenda≥220dp、滑agenda后日期区collapse可恢复、footer无域名且hint不占固定行、caption按层次不乱折行；expectedRED后GREEN与裸22保持。
- 下一动作：作者final报告后Lead读最终diff与证据，单次build含mobile+TV/test编译，再5556竖屏月视图截图和滑动对比。

- Lead设备复核：overlay移除后DisplayDeviceInfo无cutout，原生1080x1920/480，before-normalized.png顶端黑色刘海与白条已消失；月6行挤压agenda问题仍可复现。数据快照before-db与先前一致（1条、watched3），未清任何数据。显式同app UID --user0可直达非导出Calendar，后续验收不用反复冷启动首页。

- 作者run1/3 ready：7生产文件（Activity行策略+3同构布局+3语言文案），Material AppBarLayout(48dp顶栏+面板scroll|enterAlways)+agenda content behavior，月竖屏3行/短屏2行、完整月内部可滚；真实Activity360x640dp 8/8Green（agenda 248dp vs 修前139），RED 3失败留存，裸mobile 22通过。作者明确未在真机证明collapse（Robolectric scrollRange恒0），Lead进行真机触控验收。
- Lead实机复验（5556 1080x1920/480=360x640dp，浅色）：顶栏一行不再折行（“刷新”）；页脚只剩“数据来源 TMDB”，hint已隐藏；月历完整4–6行但整页滚动——上滑后日期面板滚走、列表占满视口并可见4–5张卡（此前日历固定、仅露1张半）；行内文案去重为“已看到 E1 · 缓存 11月26日”。
- 过程中发现并修正两点仅真机可见的问题：1. Material AppBarLayout 收起链未生效（RecyclerView 被普通LinearLayout包住，CoordinatorLayout拿不到nested scroll），改为“固定顶栏+单一滚动面”后行为确定、无动画依赖；2. 中文“刷新排期”在360dp折成两行，缩短为“刷新”并把今天/刷新改wrap_content+maxLines1。
- 验证：裸mobile 15+7通过、androidTest编译通过；设备数据安装前后逐字段一致（watched=3）。未验收：leanback遥控焦点、横屏land布局实机、暗色竖屏截图。

## 点集进入播放/详情 + TV 按钮两字（2026-11-26 19:4x，follow-4-open-episode）
- 用户反馈：日历里点集数没有进详情/播放，而是退回追更页；应与追更模式一致。改为与 FollowingActivity.onContinue 同链路：有历史续播，无历史打开 TmdbDetailActivity；日历本身仍不写已读/通知/进度。
- 因此移除 FollowingActivity 中已死的 ActivityResult 回传（calendarLauncher/focusFromCalendar），日历入口改为普通 startActivity；设备测试的点击契约相应改为“点击后离开日历页”。
- 电视首页「追更」按钮原来拼「· 未读数」，在按钮宽度内被截成「追更…」；改为只显示两字名称，未读数仍在追更页顶部统计里。
- 实机（leanback arm64，5556）：首页按钮显示「追更」无省略号；日历月视图点「今天是太阳雨」卡片 → 打开 TmdbDetailActivity（截图为“正在加载 TMDB 详情…”）。手机包与电视包均构建通过。
