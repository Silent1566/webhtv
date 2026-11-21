# TV-RANGE-FOCUS-20261114 — 多集短剧分段焦点漂移

## 目标与边界
- quick-fix：原生增强与影视原生模式的分段条滚出首屏后，左右键仍可准确选中 321–360 等分段。
- 修改仅限 TV VideoActivity、VideoActivityArrayFocusTest 与本记录；保护启动前的 .scratch-goal/**（task guard 展开为 371 个路径）。
- 不改分组大小、播放器内核、布局样式、依赖或手机实现；只做本地提交与恢复标签，不推送。

## 决策与计划
- 用户报告：超过 300 集，281–320 到 321–360 邻接处不能定位；两个模式均出现。两个模式共用 TV VideoActivity 分段条。
- 已有证据：VideoActivity.java:4810 用 indexOfChild 返回挂载子项下标；4842/4846 用 getChildAt(position) 查找列表位置。HorizontalGridView 横向回收后这些值不等于 adapter position。
- 修复方案：沿用现有焦点流程，针对分段条改用 RecyclerView 的 adapter-position 读写。以行为级红绿测试验证，不扩大模块或公开接口。
- 执行拓扑：L1，单子代理负责修复与自测；调度者保留设备捕获、独立验收、文档及提交责任。

## 验收
- 回归用真实横向 RecyclerView/HorizontalGridView 及真实生产方法，确保已挂载首项不是 adapter 0；禁止仅源码字符串断言。
- 左右跨 281–320 / 321–360、反向返回，滚动后高位分段、起止边界与焦点离开分段行不抢回。
- 同场景、同视口、同主题的 Before/After；可用设备 SDK 28，1920×1080、density 280。
- 一个 TV arm64 定向测试/编译，不跑全部 ABI 或播放器原生重建。

## Before 设备证据
- 不修改用户配置，通过已存在的 push_agent 本地文件前缀回退传入 480 集合成播放列表；所有媒体地址为 127.0.0.1:1，不依赖真实站源或影片。此验证不代表已搜索或播放用户示例片名。
- SDK 28、1920×1080、density 280；用既有 detail_runtime_mode 意图覆盖分别启动模式 2（影视原生）和 5（原生增强），不修改持久化模式设置。非导出 Activity 通过应用自身 debug UID 和 --user 0 启动。
- 两模式：确认起始焦点为 241–280 后按 RIGHT 两次，预期 321–360，实际 281–320。滚动后首个可见分段变为 41–80，不再是 adapter 0。
- 两模式：确认起始焦点为 361–400 后按 LEFT 一次，预期 321–360，实际 201–240。
- XML/JSON/PNG：/tmp/webhtv-TV-RANGE-FOCUS-20261114/device-before-{direct,enhanced}-from-{left,right}.{xml,json,png}；enhanced 的 setup XML 和 direct-right-setup XML 记录按键前真实焦点。

## 根因与修复
- 根因：分段条（HorizontalGridView）横向回收后，挂载子项下标不再等于完整列表位置；VideoActivity 用 indexOfChild 当焦点位置、用 getChildAt(position) 当请求目标，于是边框漂移、高段位不可达。
- 修复（7 增 6 删，仅既有方法）：focusedArrayPosition 改用 getChildAdapterPosition；applyArrayFocus 的即时与 posted 兜底改用 findViewHolderForAdapterPosition，且 posted 兜底在「焦点已离开分段行或网格选中位已变」时直接返回，保持既有焦点权威语义。不改布局、样式、分组大小、播放器、依赖。
- 回归测试 app/src/testLeanback/.../VideoActivityArrayFocusTest.java（9 例）：真实 HorizontalGridView + 真实 ArrayAdapter + 生产私有焦点/按键/守卫方法，断言真实回收（首个挂载项位置 > 0）；覆盖 9→10→11→12→11→10→9、10→9、首/末/空/零位移、NO_POSITION 哨兵、离开行后不被抢焦点、陈旧分段回调不夺权、已展示分段纯 no-op。

## 红绿与验收证据
- RED（生产代码未改时留存）：/tmp/webhtv-TV-RANGE-FOCUS-20261114/red-final.log；9 例 6 失败；首个挂载 adapter=7、焦点 adapter=9、挂载下标=2，focusedArrayPosition 返回 2；requestArrayFocus(4) 实际落到 adapter5；RIGHT 9→实际 4；LEFT 10→仍 10。
- GREEN：green-run1.log，9/9 通过；调度者独立复跑 assembleLeanbackArm64_v8aDebug + 同一过滤测试，accept-build.log 显示 BUILD SUCCESSFUL 56s。
- After（同一 480 集 fixture、同一 1080p/主题、干净进程、同步骤）：模式 2 与模式 5 均为 左行 241–280 + RIGHT×2 → 321–360、右行 321–360 + LEFT×1 → 281–320；device-after-modes.json 记录 BOTH_MODES_PASS，截图 device-after-mode{2,5}-from-{left,right}.png。
- 与 Before 对照一致：同一场景下 Before 分别是 281–320 与 201–240（模式 2、5 相同），确认修复针对用户报告的确切症状。

## 环境交接
- 子代理 sdk27 探针无效：~/.m2 内 android-all-instrumented 8.1.0-robolectric-4611349-i7.jar 仅 26050432 字节、SHA512 不匹配、BadZipFile，源自被中断的下载。不作为仓库既有缺陷证据；该 jar 已移出缓存到本任务产物目录以便恢复。sdk28 缓存不受影响，最终红绿均为 sdk28。
- 设备验证用 run-as 以应用自身 debug UID 启动非导出 VideoActivity，并通过既有 detail_runtime_mode 意图覆盖切换模式；未修改用户持久化设置、站源或影片数据。已回滚基准：/tmp/webhtv-TV-RANGE-FOCUS-20261114/rollback/installed-before.apk。

## 任务簿
- Before 与失败复现：completed，run 1/3。
- 修复及自测：in_progress，run 1/3；子代理 67c82995-9cdf-4eae-92e2-db0ae8f6cb65。
- 独立验收：completed，run 0/3（diff 审阅 + 独立构建/测试 + 双模式双方向 After）。
- 提交/恢复标签：in_progress，run 0/3。

## Recovery anchor
- 分支 dev1；任务起点 b0ebb8ca722d6f7e66c56d69b003378395d34dc1；guard TV-RANGE-FOCUS-20261114 已启动。
- Before 两模式双向失败已确认；修复与行为级红绿回归由子代理执行，代码变更尚未经调度者验收。
- 进度说明：原估 15 分钟已超；原因是子代理首次 sdk-27 运行下载缺失 android-all 包导致约 10 分钟挂起，以及设备双模式 Before 采集。已停止可选排查，只走窄路径（红→修→一次 TV arm64 编译→After→提交）。
- 环境发现（子代理，pre-existing 与本次改动无关）：本工作区 sdk-27 Robolectric 用例全部在 DeviceConfig.java:259 报 NoSuchFieldError android.util.DisplayMetrics.noncompatWidthPixels；维持 sdk 28 + 真实布局 inflate，测试内仅替换 BitmapFactory.decodeStream 1×1 以避免 media3 PlayerView 资源缺图，生产源不受影响。
- 风险：异步布局回焦需验证不落到错误已挂载项、不抢离开分段行后的焦点；After 必须重放相同视口/主题/步骤。
- 状态：修复已验收；未决项仅提交与本地恢复标签。
- 下一动作：task_guard finish 原子提交三处范围内文件并创建 recovery 标签。
