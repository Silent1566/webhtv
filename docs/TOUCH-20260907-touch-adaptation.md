# TOUCH-20260907 TV 触摸适配

## 授权与验收
用户已明确“按计划实施”。一个任务/提交覆盖：首页自由触摸滚动、站源触摸滚动不切换数据、按站源恢复结果位置、TV 点播全屏横滑进度/左半屏竖滑亮度/右半屏竖滑音量。原反馈“高度”按已批准方案解释为亮度，不新增画面缩放。开关关闭和遥控器操作保留原有语义；不修改 mobile、直播、投屏和播放器内核。

## 决策就绪的证据（访问日期 2026-09-07）
- A / 当前工程：基线 `db59f3eeb85ff0e4ed993e814d9b81b08036bcb7`；`HomeActivity.setRecyclerView/initEvent`、`CollectActivity.scheduleCollect/setSearchItemsLazy`、`CustomKeyDownVod.onTouchEvent`、`VideoActivity.initEvent` 是实际路径。先前草稿只有源码字符串测试通过，不能证明回弹解决。
- A / 精确依赖源码、官方 API 注释：`https://dl.google.com/dl/android/maven2/androidx/leanback/leanback-grid/1.0.0/leanback-grid-1.0.0-sources.jar`（本机依赖 leanback 1.2.0 → grid 1.0.0）；`GridLayoutManager.onLayoutChildren` 的 `scrollToFocus` 仅在 `FOCUS_SCROLL_ALIGNED` 成立，且与是否触摸无关；`focusToViewInLayout` 会重新对齐旧选中项。这比此前“焦点可能抢占”的推测更直接。
- A / 官方 API：上述 jar 的 `BaseGridView.setFocusScrollStrategy`、`setFocusSearchDisabled` 注释及实现。触摸期间改用 ITEM 策略并阻止子焦点回收；按键到来时恢复原策略并从当前可见项恢复焦点，不能永久关闭 D-pad。
- A / 上游维护证据：同一 `GridLayoutManager` 的 b/67370222 注释说明布局对齐/可见项裁剪的反馈循环风险。它不是用户设备的复现记录，不据此声称“已真机复现”。本任务不修改/升级 AndroidX。
- B / 成熟本地实现与测试：`main/.../PlayerGesture.java`、`utils/BrightnessPolicy.java` 和 `testMobile/.../VideoActivityLayoutTest.java`；借鉴窗口亮度和音乐音量控制。**不直接复用整个 PlayerGesture**：其侧边四分区、中央竖滑切集动画、双指缩放、长按倍速及小窗消费 DOWN 均超出本次批准语义。最初草稿直接接入会破坏小窗点击进入全屏，因此撤销这部分未提交草稿，补充现有 TV 控制器的可选滑动能力。
- 官方在线文档尝试：web 工具未返回正文，agent-reach/Jina 读取 Android 手势文档出现 TLS EOF；以可获取的 Google Maven 精确版本源码及其 API 文档为依据，不声称已读取失败网页。
- 论文/博客/性能基准：本变更不发明识别算法、不变更渲染/解码/ABI；无须用论文替代 Android 事件契约。性能验收为 MOVE 中常数开销、无 Toast 连发/全树重复扫描/网络请求。

### 方案比较与选定
1. 不变更：无法满足三项反馈，拒绝。
2. 直接复用上游/共享行为：Leanback 默认对齐造成回弹；共享 PlayerGesture 引入未授权缩放/切集等，拒绝原样接入。
3. 窄适配（采用）：触摸期间关闭焦点对齐和焦点驱动业务切换，遥控器恢复；结果按实际展示站源记录首项+像素偏移并隔离延迟任务；TV 自有手势仅在优化开关开启且全屏生效，使用现有 seek 通道、BrightnessPolicy 和 AudioManager，无依赖升级。

### 验收和回滚
- 首页和两种站源排列：多次拖动/惯性滚动不被布局拉回；触摸列表不自动切源，点击才切换；按 D-pad 后仍能导航。
- 结果：同源重按不回顶，A→B→A 恢复 A 的首项与像素偏移；快速连续切源不得错记位置；新搜索/筛选清除不适用位置；空结果及列表缩短不得越界。
- 手势：横滑预览、仅正常 UP 提交一次；CANCEL/多指/失焦不提交；左/右半屏起点锁定亮度/音量；OSD 抬手清理；小窗点击、关闭优化、控制栏按钮和遥控器不回归。
- 回滚：恢复本任务唯一提交前状态；未提交阶段回退仅限 guard 内任务补丁，不触及其他工作。关闭触屏优化可回到旧输入行为。

## Recovery anchor
- 目标/范围：上述完整验收；继续原 `TOUCH-20260907` guard，原始脏路径 0，当前脏文件均为任务草稿。
- 分支/HEAD：`dev1` / `db59f3eeb85ff0e4ed993e814d9b81b08036bcb7`。
- 已完成：精确 Leanback 布局原因已确认；旧草稿 arm64 Java 编译及源码测试成功，但没有充分覆盖目标，不能用它闭合任务。
- 未验证：即将替换的触摸导航、结果状态与 TV 手势；设备端真实 MotionEvent 行为。
- 下一步：在原 scope 内实现所选窄适配，并用行为测试/代表性设备场景验证。
