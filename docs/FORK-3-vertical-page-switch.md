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
| 40px 慢速小幅拖动 | 回弹不换集 | 通过 | 日志 `down rawX=540.0 armed=true` + `settle dragY=-29.3 velocity=0.0 commit=false` |
| 稳定性 | 无崩溃 | 通过 | `logcat -b crash` 全程无 `FATAL EXCEPTION` |

**过程中的一次自我纠错（值得留档）**：早前"侧边滑也换集"看起来像门控失效，实际是两层原因叠加——(1) 控制层显示时事件被其吞掉，我的 handler 根本没收到；(2) 那些集数变化是短剧**自动续播**（每个片段 1:26–2:05，而测试跨度约 8 分钟）。加日志后同一手势得到 `armed=false`，证明门控正确。为避免再误判，`VerticalPageSwitcher` 保留 DEBUG 级 `TV-vpage` 日志（与本仓既有 `TV-playback-progress` 同类），记录 rawX/width/armed 与 dragY/velocity/threshold/commit。

**一处口径修正（2026-11-27 复核）**：上表那条"40px 小幅拖动回弹"用的是慢速拖动（`velocity=0.0`），**快速小幅甩动仍会切集**——这是设计意图，不是漏判：`VerticalPageSwitchGestures.commits` 的判据是"位移超过 `min(18% 页高, 240dp)` **或** 速度超过 `FLICK_VELOCITY=350 px/s`"。本轮实测 40px 快速甩动得到 `settle dragY=-37.6 velocity=-457.3 threshold=240.0 commit=true`，即速度分支生效，与短视频类应用的"轻甩换条"手感一致。若要禁止这种情况，需要改的是速度阈值，不是位移阈值。

## 设备实测（2026-11-27 00:12–00:21，MPV 内核，emulator-5556 竖屏 1080x1920）

设置 → 播放设置 → 播放器内核 切到 MPV 后重放《别触逆鳞》，内核已确认为 MPV：`MPV_SIZE: video layout new=1080x1920 old=1080x1920 fullscreen=true land=false scale=0 player=MPV`。

| 用例 | 期望 | 实测 | 证据 |
|---|---|---|---|
| 正中上滑 | 切下一集 | 通过：第20集→第21集 | 截图前后对比；`TV-vpage: down rawX=540.0 armed=true` + `settle dragY=-990.0 velocity=32.0 threshold=240.0 commit=true` |
| 正中下滑 | 切上一集 | 通过 | 截图：`准备播放：第13集`（自第14集回退） |
| 左侧四分之一（x=80） | 不换集 | 通过 | `TV-vpage: down rawX=80.0 armed=false`，无 settle |
| 右侧四分之一（x=1000） | 不换集 | 通过 | `TV-vpage: down rawX=1000.0 armed=false`，无 settle |
| 横滑（900→300） | 进度改变、不换集 | 通过 | 进度 01:29 → 01:09，集数未变 |
| 双击中央 | 暂停/继续 | 通过 | 中央按钮由"暂停"变"播放"，进度在采样间隔内保持 01:15 |
| 稳定性 | 无崩溃 | 通过 | `logcat -b crash` 全程无 `FATAL` |

**再次踩到"自动续播"这个坑**：本轮采样窗口里出现过两次"无故换集"，事后都查明是 1:00–2:10 的片段自然播完续播（其中一次从 00:52/01:00 直接跳到"准备播放：第20集"）。判定手势是否生效只能看 `TV-vpage` 的 `down`/`settle` 行，不能只看画面集数——这也正是上一轮加日志的原因。

## 未验范围（仍然保留）

- **亮度/音量的实际生效值**：只验到"侧边竖向滑动不触发换集"和手势被正确过滤，没有测量亮度/音量数值是否真的改变；且贴边测点受系统手势导航干扰，测点需按下一节改正。
- **对端页（`episodePeer`）的"↓ 集名"提示**只在拖动过程中可见，两轮截图采样都落在动画完成之后，始终没有取得对端页可见帧。

## 补验尝试与一处方法论修正（2026-11-27 08:45–08:55）

**这轮补验没做成，两个原因都要写下来**：

1. **系统手势导航会吃掉贴边手势**。`settings get secure navigation_mode` 未设置（即默认手势导航）。此前用的 x=80 / x=1000 正好落在系统"边缘返回"手势区，`input swipe` 在这两个位置很可能被系统当成返回，而不是交给 App 的亮度/音量处理。所以"侧区竖滑不换集"虽然 `armed=false` 日志是真的，**但它并不能证明亮度/音量区仍然可用**——测点位本身就选错了。
2. **源不稳**。这段短剧在本次窗口里持续缓冲，并出现几十秒内连续换集（第3集→第4集→第6集→第8集→第9集），构造不出稳定的 60 秒手势窗口。

实测到的两条：x=900 竖滑得到 `TV-vpage: down rawX=900.0 armed=false`（handler 看到了并主动放行）；x=150 竖滑连 `down` 都没有——说明左侧竖滑在我这层之前就被既有消费者拿走了（接线顺序是 碟片菜单 → 翻页 → mKeyDown）。**没有观察到任何由这两个手势引起的换集**，但亮度/音量数值是否真的改变、对端页"↓ 集名"的可见帧，仍然没取到。

**下次补验按这个来**（写死，免得再走一遍）：
- 用一台**稳定、多集**的片源（真实 NAS 上的短剧文件夹最合适），连续播放 2 分钟以上不缓冲；
- 亮度用 x≈150、音量用 x≈900（都在边缘手势区之外），每次滑动后立刻截图找亮度/音量浮层；
- 对端页用"后台 `input swipe` + 滑到一半截图"（本轮已用此法，只是当时源在缓冲，画面是黑的）。
- **MPV 下的跟手动画观感**（拖动时页面跟手程度、回弹曲线）没有量化评估，只确认了判定与切集结果正确。
