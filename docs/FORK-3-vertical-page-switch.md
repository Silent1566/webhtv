# FORK-3 竖屏短剧整页上下滑切集

## Recovery anchor

- 目标：竖屏全屏播放时，上下滑直接切上一集/下一集（抖音式整页切换），不破坏既有亮度/音量/进度手势。
- 状态：实施中。来源 fork `wyicibo1-netizen/webhtv`（`dc1cc9d1f96cde9fcff3a1dcd6230b260571be33` 主提交 + `05e7c88b1cb7e97c3a5c7d56c46af6becbfe7852` 双层页去闪 + `dc580f7b1c33551213dbfb8b182638c4b7f484da` 黑场过渡）。
- 分支/HEAD：`dev2` / `84a839632f5207f26c24eb7b16425be4e2e9ec8a`。
- 下一步：装机验收（截图 + 手势实测）后提交。

## 决策：移植（窄适配），不引入新版式

三个选项的取舍：

1. **不改**：竖屏短剧仍需点开选集面板才能下一集，与主流短剧体验差距明显；fork 已有经过三轮真机修复的实现，改动面可控。
2. **原样整搬 fork 分支**：其 `VideoActivity` 同期还有 Compose→View 迁移、壁纸/主题等无关改动，整搬会把无关风险带进来，且必然冲突。拒绝。
3. **窄适配移植（选定）**：只取手势类与接线；判定逻辑拆成纯函数类以便单测；对端页几何在手势开始时从当前页同步，避免与 dev2 的内嵌/全屏切换逻辑耦合；不新增字符串资源（用箭头 + 集名，缺集时留空）。

## 与现有实现的关系

- dev2 `app/src/mobile/.../VideoActivity.java:1958` 的 `mBinding.video.setOnTouchListener` 已是手势总入口，插在 `dispatchDiscMenuTouch` 之后、`mKeyDown` 之前，保持既有优先级：碟片菜单 > 翻页 > 亮度/音量/进度。
- 只在 `isFullscreen() && player().isPortrait() && getEpisodeCount() > 1` 时启用；抬手不改集则回弹，纵向位移必须大于横向位移才会接管，因此横滑手势不受影响。
- 起手位置限页面中间 50% 宽度，左右两侧四分之一仍归既有亮度/音量手势。

## 验收标准

- 竖屏全屏、多集播放时，上滑切下一集、下滑切上一集，过程不出现应用背景。
- 位移不足或速度不够时回弹且不换集；无相邻集时拖动明显阻尼且松手回弹。
- 横滑、双击、亮度/音量、进度拖动行为不变。
- 内嵌（非全屏）竖屏不触发翻页。

## 回滚

删除本提交即可恢复：改动仅为新增两个类 + `VideoActivity` 一处接线 + `activity_video.xml` 一个 `View.GONE` 的对端页。

## 验证记录

- 单元测试：`VerticalPageSwitchGesturesTest` 3/3 通过（RED 先行：类缺失时用例失败，实现后转绿）。覆盖起手区域（中间 50% 手动宽度、未测量页面不启用）、纵向主导判定（slop 边界、横滑不接管）、位移/速度阈值（严格大于、比例与 dp 上限取小、快速上滑）。
- 构建：`:app:assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL 2m36s，产出 `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`。
- 设备冒烟（emulator-5556，1920x1080）：新包安装成功（versionName 5.6.0），冷启动进入首页无崩溃，点开影片进入 `TmdbDetailActivity` 无崩溃。截图 `F:/temp/vp_before.png`（旧包首页）、`vp_after_home2.png`（新包首页）、`vp_detail.png`（新包详情页）。

## 未验范围（必须显式保留）

- **竖屏上下滑切集本身未在设备上验证**：本机两个可用模拟器都是 1920x1080 横屏面板（`wm size` 均为横向，`user_rotation=1` 不生效），无法构造 `isFullscreen() && player().isPortrait()` 的竖屏全屏短剧会话；这一状态同时受 TMDB 网络加载影响。
- 因此本次验收边界是：判定逻辑有单测、整包能编译安装运行、既有页面不回归；**手势在真机竖屏的表现（跟手性、回弹、与亮度/音量/进度手势的冲突）仍待竖屏设备补验**。
- 若要收敛剩余风险，最小补验路径：竖屏手机 → 竖屏短剧全屏播到第 2 集以上 → 上滑/下滑各 3 次，确认切集、回弹、左右侧亮度音量仍生效、横滑进度不变形。
