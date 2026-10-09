# SITE-INJECT-FULLPAGE-20261009：站点注入竖屏铺满整页，底部按钮区不再被内容压扁

## Recovery anchor

- 目标：手机竖屏打开「增强功能 → 站点注入」时，条目较多会让界面最下方按钮被压缩（用户现场照片 `/tmp/orca-paste-1791539810953-cd64f9a7-ed87-4eea-8220-82182fe8e580.png`，竖屏 1080×2400@440dpi ≈ 392.7×872.7dp）；按用户建议把该界面改为**竖屏全屏整页**形式，底部取消/确定永远保持 40dp 并贴在页面底部，条目列表改为滚动视口。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/CustomCspDialogLayoutTest.java`、本文件（`app/src/main/res/layout/dialog_custom_csp.xml` 已在 scope 内但本次无需改动）。
- 保护面：任务 guard 启动时工作区干净（pre-existing dirty path 0 个）；分支 `dev1`，基线 HEAD `67d930ed63a4102652b894b1812df42825c8b078`。
- 验收：① 竖屏任意比例下 root 撑满窗口、取消/确定与按钮区实测 40dp 且贴底；② 列表成为滚动视口（viewport > 0）；③ 横屏（电视/横屏手机）窗口仍为 0.76×0.98，行为与修复前逐项一致；④ 文本模式/条目编辑/空搜索结果等短内容场景页面仍铺满；⑤ 定向 JVM 测量测试含变异检验；⑥ 双 flavor 全量单测通过；⑦ 设备实测覆盖 3 种竖屏比例 + 横屏 + 短内容。
- 回滚：撤销本任务原子提交即可恢复到「窗口 WRAP_CONTENT + 滚动区 wrap_content/0.58H 上限 + 无 weight」的旧行为；无数据迁移、无依赖或 native 变更。
- 下一步唯一动作：无（已交付）。

## 1. 根因（设备实测证据）

修复前 `CustomCspDialog.onStart()` 竖屏走「按屏幕比例手算高度」：

```java
params.width  = (int) (screenWidth * 0.94f);
params.height = WindowManager.LayoutParams.WRAP_CONTENT;      // 窗口按内容高度
rootParams.height = WRAP_CONTENT;                             // 根布局按内容高度
scrollParams.height = WRAP_CONTENT; scrollParams.weight = 0;   // 滚动区不参与分配
binding.contentScroll.setMaxHeight((int) (screenHeight * 0.58f));
```

窗口、根布局、滚动区三者都是 `wrap_content`，且没有 weight 让任何子视图收缩。内容高度一旦超过窗口可用高度，`LinearLayout` 无处可缩，最后两个子视图（列表 + 按钮区）被摆到窗口下沿之外裁掉。

dev1 模拟器实测（`192.168.50.3:5555`，`wm size 1080x2160` + `wm density 440` → 392.7×785.5dp 竖屏，`/sdcard/TV/CustomCsp/registry.json` 临时写 5 条注入，装修复前包）：

| 位置 | 声明高度 | 实测（uiautomator bounds） | 结论 |
|---|---:|---|---|
| 取消 | 40dp | `[87,2031][528,2127]` = 96px = **34.9dp** | 被压扁 |
| 确定 | 40dp | `[550,2031][992,2127]` = 96px = **34.9dp** | 被压扁 |
| 第 3 张卡片操作行（启用/修改/首页/删除） | 36dp | `[82,1973][998,1998]` = 25px = **9.1dp** | 被挤出可视区 |
| 弹窗面板宽度 | — | 内容区 `[87…992]`（窗口 0.94×屏宽） | 非整页 |

对照用户照片：按钮圆角上下被切、第 3 张卡片操作行只剩一条边——同一现象；用户机型屏更高（2400 vs 2160），条目更长（`api: file:///TV/CustomCsp/...` 折行），压缩更明显。

## 2. 证据来源

访问日期：2026-10-09（China Standard Time）。

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 用户现场照片 | `/tmp/orca-paste-1791539810953-cd64f9a7-ed87-4eea-8220-82182fe8e580.png`（576×1280，即 1080×2400@440dpi） | A | 竖屏、条目多时底部按钮被压扁；用户同时提出「内容多，可改成全屏整页形式」 |
| 本仓库 `CustomCspDialog.onStart()` | HEAD `67d930ed6`（修复前） | A（本地） | 竖屏三分支全为 wrap_content + 滚动区 0.58H 上限 + 无 weight，是根因的静态证据 |
| 设备实测（修复前/后） | dev1 模拟器 `192.168.50.3:5555`，`uiautomator dump` bounds + `dumpsys window` 窗口 frame | A | 修复前 34.9dp/9.1dp → 修复后 40dp/滚动视口；见第 1、4 节 |
| 本仓库 `AboutDialog.configureFullscreenWindow()` | HEAD `67d930ed6` | A（本地） | 仓库既有的「铺满全屏 + 权重滚动区 + maxHeight 0 + 必须 ADJUST_RESIZE」范式与大量注释；本次直接沿用而不是新造模型 |
| 本仓库 `MpvConfigEditorDialog` / `TmdbSearchDialog` / `GithubProxyDialog` | HEAD `67d930ed6` | A（本地） | 全屏/全宽且带输入框的弹窗统一 `SOFT_INPUT_ADJUST_RESIZE`，确认键盘处理口径 |
| Android `LinearLayout` 测量语义（EXACTLY 下 match_parent 子视图可拿满窗口高度；wrap_content 父级会把 match_parent 退化为按内容） | 同上设备实测反证（文本模式短内容时 root 只到内容高度） | A | 决定追加 `expandToWindow()`：只把 root 改成 match_parent 仍会在短内容时不贴底 |
| Material 3 对话框结构（`setView` 的自定义视图被 AlertController 以 `MATCH_PARENT × WRAP_CONTENT` 放进 `@id/custom`） | `com.google.android.material:material:1.14.0` | A | 说明面板链默认 wrap_content，需一并撑开；也是 `expandToWindow` 只改高度不改宽度的依据 |

不适用类别记录：本改动只涉及单个弹窗的窗口尺寸与线性布局权重分配，不涉及解码/渲染/ABI/打包/依赖，无需上游播放器依赖类证据；无新增依赖与规格变更。

## 3. 方案比较与采用

1. **不变更**：拒绝。设备实测 34.9dp，用户报告仍成立。
2. **只把竖屏窗口高度改成整屏、其余不动**：拒绝。窗口给了高度但 root 仍是 `wrap_content`、滚动区无 weight，内容溢出时按钮区照样被裁。
3. **竖屏铺满整页 + 滚动区吃权重 + 面板链撑开（采用）**：
   - 竖屏（`!land`）：窗口 `MATCH_PARENT × MATCH_PARENT`，root `MATCH_PARENT`，`@id/custom → customPanel → parentPanel` 一并 `MATCH_PARENT`，滚动区 `height=0 + weight=1`、`maxHeight=0`，底部按钮区高度固定由内容决定（40dp）并贴底。
   - 内容比窗口短时（文本模式 JSON 编辑器、空搜索结果）同样铺满，不会出现「页面下半截露出后面的界面、按钮区悬在中间」。
   - 键盘：铺满后窗口没有余量可上推，按仓库既有口径显式 `SOFT_INPUT_ADJUST_RESIZE`（AboutDialog 同处理），键盘弹出时窗口高度收缩、滚动区缩小、按钮区抬到键盘之上。
   - 横屏保持 `0.76×0.98` 与既有逻辑（原本就是 `height=0 + weight=1 + maxHeight=0`），电视端视觉零变化。
4. **改成独立 Activity/整页导航**：拒绝。站点注入的保存、权限、配置重载、剪贴板浮层都挂在对话框生命周期上；换承载容器会牵动保存时序与焦点策略，收益不比方案 3 大。

## 4. 实施设计

1. `onStart()` 把尺寸逻辑抽成静态方法 `applyPageSizing(window, root, scroll, land, screenWidth, screenHeight)`（可被 JVM 测量测试直接调用，不再依赖 Activity 上下文里的 `ResUtil` 全局）。
2. 竖屏分支：窗口 `MATCH_PARENT`、`ADJUST_RESIZE`；root `MATCH_PARENT`；`expandToWindow(root)` 把 root 之上的 `MarginLayoutParams` 祖先（`@id/custom`、customPanel、parentPanel）高度一路改成 `MATCH_PARENT`，到 DecorView（窗口参数不是 `MarginLayoutParams`）自然停止。
3. 两种朝向统一：滚动区 `height=0`、`weight=1`、`setMaxHeight(0)`（竖屏不再用 `0.58H` 手算上限，横屏值本来就是这个组合）。
4. 新增 `CustomCspDialogLayoutTest`（Robolectric 真实 framework 测量）：
   - `portraitFullPageKeepsFooterAtNaturalHeightForEveryRatio`：5 种竖屏比例（含用户机型 393×873、小屏 360×640、大屏 412×915、竖屏平板 617×1097、键盘顶掉 300dp 的 393×573）下断言 root 撑满、取消/确定/按钮区均 40dp、按钮区贴底、滚动区 viewport > 0 且不与按钮区重叠。
   - `shortContentStillFillsTheWholePage`：内容很短的场景同样撑满并贴底（覆盖 `expandToWindow` 的必要性）。
   - `landscapeKeepsProportionalDialogAndPinnedFooter`：横屏仍是 0.76×0.98 + 权重滚动区 + 40dp 贴底。
   - `legacyProportionalParamsPushFooterOutsideThePage`：变异检验，换回修复前参数后断言按钮区被摆到页面之外（设备上即"被压扁"）。

## 5. 验证

### 5.1 设备实测（dev1 模拟器 `192.168.50.3:5555`，覆盖安装，签名一致，未卸载）

命令：`bash scripts/build_arm64_debug_install.sh`（mobile/arm64-v8a Debug）。

| 场景 | 屏幕/密度 | 修复前 | 修复后 |
|---|---|---|---|
| 用户机型近似（5 条注入） | 1080×2160 @440（392.7×785.5dp 竖屏） | 取消/确定 96px=34.9dp；第 3 卡操作行 25px=9.1dp | root `[0,66][1080,2160]`，取消/确定/按钮区 **110px=40dp**，列表 viewport 1238px，按钮区贴底 |
| 小屏手机 | 1080×1920 @480（360×640dp） | — | 按钮区 120px=**40dp**，列表 viewport 915px |
| 短屏手机 | 1080×1800 @440（392.7×654.5dp） | — | 按钮区 110px=**40dp**，列表 viewport 878px |
| 横屏手机/电视比例 | 1920×1080 @280（686×386dp） | 窗口 1459×1058，按钮区 70px=40dp | 窗口 **1459×1058（0.76×0.98 不变）**，按钮区 70px=**40dp** |
| 文本模式（426dp JSON 编辑器，短内容） | 1080×2160 @440 | 页面底部留空隙 | root 撑满 2094px，按钮区 40dp 贴底 |
| 条目编辑面板 / 空搜索结果 | 1080×2160 @440 | — | 页面撑满，按钮区 40dp 贴底 |

说明：该模拟器无法真正显示 IME（`ty=INPUT_METHOD` 窗口 frame 高度为 0），键盘场景改由 JVM 测量测试的 393×573dp 窗口覆盖；窗口属性 `sim={adjust=resize}` 已在设备上确认生效。

### 5.2 定向 JVM 测试

- `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.CustomCspDialogLayoutTest"`：4/4 通过。
- 变异检验：把 `applyPageSizing` 临时改回修复前参数（窗口/root/滚动区 wrap_content、滚动区无 weight）→ 3/4 失败（`portraitFullPageKeepsFooterAtNaturalHeightForEveryRatio`、`shortContentStillFillsTheWholePage`、`landscapeKeepsProportionalDialogAndPinnedFooter`），恢复后 4/4 通过；证明断言不是恒真。
- `--tests "com.fongmi.android.tv.ui.dialog.*" --tests "com.fongmi.android.tv.ui.bean.*"`：通过。

### 5.3 全量单测

- `:app:testLeanbackArm64_v8aDebugUnitTest` → BUILD SUCCESSFUL，**4144 项 / 0 failure / 0 error / 2 skipped**。
- `:app:testMobileArm64_v8aDebugUnitTest` → BUILD SUCCESSFUL，**4980 项 / 0 failure / 0 error / 2 skipped**。

### 5.4 设备状态回收

- `registry.json` 已还原为用户原文件（`md5 bce0777921bc78ec1991f4d89f1b3d87` 一致）。
- `wm size reset` / `wm density reset` / `accelerometer_rotation=1` 已恢复；应用已 force-stop。

## 6. 复现与回滚

- 复现：竖屏装任意条目数 ≥3 的注入注册表，打开「增强功能 → 站点注入」，观察底部按钮高度（`adb shell uiautomator dump` 取 bounds）。
- 回滚：`git revert <本次提交>`；只影响该弹窗窗口尺寸与布局权重，无数据/依赖/native 变更。
