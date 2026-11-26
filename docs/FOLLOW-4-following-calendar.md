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
