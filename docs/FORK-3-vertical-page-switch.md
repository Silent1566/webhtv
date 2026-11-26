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

## 设备实测（2026-11-26 22:00–22:08，emulator-5556 竖屏 1080x1920）

场景：竖屏短剧《别触逆鳞》，`VideoActivity` 前台，`player().isPortrait()=true`，全屏。

| 用例 | 期望 | 实测 | 证据 |
|---|---|---|---|
| 正中上滑 | 切下一集 | 通过：第1集→第2集 | 截图 `F:/temp/vp_now.png`(第1集) / `vp_up.png`(第2集)；日志 `down rawX=540.0 width=1080 armed=true` + `settle dragY=-745.6 velocity=-992.6 threshold=240.0 commit=true` |
| 正中下滑 | 切上一集 | 通过：第2集→第1集 | 截图 `vp_down.png`(第1集) |
| 侧边四分之一滑（x=80） | 不换集 | 通过 | 日志 `down rawX=80.0 width=1080 armed=false`，无 settle |
| 40px 快速小幅拖动 | 回弹不换集 | 通过 | 日志 `down rawX=540.0 armed=true` + `settle dragY=-29.3 velocity=0.0 commit=false` |
| 稳定性 | 无崩溃 | 通过 | `logcat -b crash` 全程无 `FATAL EXCEPTION` |

**过程中的一次自我纠错（值得留档）**：早前"侧边滑也换集"看起来像门控失效，实际是两层原因叠加——(1) 控制层显示时事件被其吞掉，我的 handler 根本没收到；(2) 那些集数变化是短剧**自动续播**（每个片段 1:26–2:05，而测试跨度约 8 分钟）。加日志后同一手势得到 `armed=false`，证明门控正确。为避免再误判，`VerticalPageSwitcher` 保留 DEBUG 级 `TV-vpage` 日志（与本仓既有 `TV-playback-progress` 同类），记录 rawX/width/armed 与 dragY/velocity/threshold/commit。

## 未验范围（仍然保留）

- **MPV 内核路径未验**：实测播放走默认内核；若用户切到 MPV，竖屏短剧的同一手势未单独验证（代码路径相同，但内核切换会重建播放页）。
- **横滑进度、双击、亮度/音量手势未被本轮的自动化输入覆盖**：只验证了"侧边竖向滑动不触发换集"，未逐项回归其余手势。补验路径同前：竖屏手机 → 短剧播到第 2 集以上 → 上/下滑各 3 次 + 左右侧亮度音量 + 横滑进度 + 双击暂停。
- 对端页（`episodePeer`）的"↓ 集名"提示只在拖动过程中可见，本轮的截图采样恰好都落在动画完成之后，未取得对端页可见帧的截图。
