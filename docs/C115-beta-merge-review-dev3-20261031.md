# C115-beta-merge-review-dev3-20261031

## Recovery anchor

- **目标**：合并 `origin/beta` 最新代码后，评审 `dev3` 上「已提交未推送」的改动（含合并提交）；有必修项则修复并验证，
  通过后再次评审，循环直到通过；然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后归零（`origin/beta` 是 HEAD 的祖先），或明确证明无需再合并。
  2. 评审覆盖 `origin/beta..HEAD` 的全部净差异；每条结论有可复核的 `路径:行号` 证据。
  3. 每个必修项均已修复并配「会变红」的守卫；无必修项时须用变异验证证明现有守卫非空转；修复后复评通过。
  4. 定向单测 0 失败；工作区除初始脏项外无残留改动。
  5. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`assessment`（`docs`）
- **任务守卫**：`C115-beta-merge-review-dev3-20261031`，base HEAD `d1686cb44d9f84371a65a34aed709426701c549c`，branch `dev3`
- **当前状态**：评审通过（无必修项），遗留项经用户裁定「仅记录」；交付坐标见 §7

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `a5469c5ceac088d824fbd79df8a80e49580a6887`（"Merge pull request #506 from Silent1566/dev2"，2026-10-31 13:59:20 +0800） |
| 本次评审基线 | `dev3` HEAD = `d1686cb44d9f84371a65a34aed709426701c549c` |
| 祖先关系 | `git merge-base --is-ancestor origin/beta HEAD` → **exit 0（true）** |
| 落后 / 领先 | `git rev-list --left-right --count origin/beta...HEAD` = **0 / 1** |
| 合并动作 | **无需合并**：`origin/beta` 已全部包含在 `dev3` 中，再 merge 只会产生空提交，故不做 |
| 待评审净差异 | `git diff --stat origin/beta..HEAD` = **2 files changed, 136 insertions(+), 0 deletions** |

`dev3` 领先 `origin/beta` 的提交只有一个：

- `d1686cb44d9f84371a65a34aed709426701c549c` Fix TV speed adjustment UP key being swallowed by control focus helper（Silent1566，2026-10-31 15:35:09 +0800）

其余 9 个未推送到 `origin/dev3` 的提交（`a5469c5cea`/#506、`454b4aaaeb`/#507、`9d8ea553da`/#509、`707dc2a1c6`、`7f4e148892`、`f9ebfec8d4`、`21c10db4a1`、`086a25cc25`、`0acd75b104`）的内容**已在 `beta` 中**，
因此净差异只有这一个提交。

### 1.1 为什么不能沿用上一轮评审跳过

仓库内已有 `docs/C114-beta-merge-review-dev3-20261031.md`，但其覆盖范围止于合并提交 `a2d4a04faa62d9ec4f5997f7527825692e87c4e4`（PR #509）。
对该文档 grep `d1686cb` / `dispatchSpeedAdjust` / `SpeedAdjustKey` → **0 命中**，即上一轮评审**没有覆盖** `d1686cb44d`。
用户给出的跳过前提（"之前的评审已覆盖当前所有修改的代码"）不成立，本轮必须评审该提交。

### 1.2 为什么必须新建 PR

PR #509（base `beta` ← head `dev3`）已于 2026-10-31T05:58:57Z **MERGED**；`gh pr list -R Silent1566/webhtv --state open` → **[]**。
`d1686cb44d` 是 #509 之后新增的提交，需要一个新的 PR 才能进入 `beta`。

---

## 2. 评审范围

`git --no-pager diff origin/beta..HEAD` 全量（7754 字符）逐行读毕，两处改动：

| 文件 | 改动 |
| --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | +22（新方法 `dispatchSpeedAdjust` 及其调用点） |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/SpeedAdjustKeyContractTest.java` | +114（新文件，4 个 `@Test`） |

为判定其正确性，另外取证了被改文件之外的 6 处调用链与样式定义（见 §3、§4 的 `路径:行号`）。

---

## 3. 缺陷根因认定（成立）

**现象**：TV 端把焦点停在控制栏的「倍速」按钮上，按 UP 不加速；按 DOWN 却正常减速。

**根因链**（每一步都有代码证据）：

1. `app/src/leanback/res/values/styles.xml:80-89` 的 `<style name="Control">` 只给了 DOWN 方向邻居：
   :86 `<item name="android:nextFocusDown">@id/timeBar</item>`。
2. 全仓 `nextFocus` 普查：**没有任何 chip 设置 `nextFocusUp`**。倍速按钮
   `app/src/leanback/res/layout/view_control_vod_action.xml:108-113`（`CustomUpDownView`，`style=@style/Control`）因此 UP 方向在控制栏内没有可聚焦目标。
3. `app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java` 的 `handleKey`：
   第 39 行 `if (next == null || !isDescendant(root, next)) return true;` —— `focusSearch(FOCUS_UP)` 返回 null 时判定为
   「会把焦点甩出控制栏」并**消费掉按键**（返回 true）。
4. 于是 UP 事件根本到不了 `app/src/leanback/.../VideoActivity.java:1678` 绑定的 `speed.setUpListener(this::onSpeedAdd)`；
   而 DOWN 能生效是因为 `CustomUpDownView.dispatchKeyEvent`（`app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomUpDownView.java`）
   在 `hasEvent` 为真时自行拦截并 `return true`，事件在到达 `handleKey` 之前就被消费。

⇒ 用户的「UP 被吞」描述与代码路径完全吻合，修复方向（在 `handleKey` 之前接管 UP）正确。

---

## 4. 修复内容评审

### 4.1 新增方法（`VideoActivity.java:8793-8812`）

```java
    private boolean dispatchSpeedAdjust(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !isVisible(mBinding.control.getRoot())) return false;
        if (getCurrentFocus() != mBinding.control.action.speed) return false;
        if (KeyUtil.isUpKey(event)) {
            onSpeedAdd();
            return true;
        } else if (KeyUtil.isDownKey(event)) {
            onSpeedSub();
            return true;
        }
        return false;
    }
```

三道门禁顺序正确：**先判动作与可见性，再判焦点身份，最后才判方向**。若把方向判断提前，
任意控件上的 UP/DOWN 都会被 `onSpeedAdd/onSpeedSub` 抢走。javadoc（:8793-8800）把根因写在代码旁，
未来改 `Control` 样式或 `handleKey` 的人能直接看到为什么需要这个补丁。

### 4.2 调用点（`VideoActivity.java:8837-8843`）

```java
        if (isVisible(mBinding.control.getRoot())) {
            setR1Callback();
            if (dispatchOpeningEndingAdjust(event)) return true;
            if (dispatchSpeedAdjust(event)) return true;      // ← 8840，本次新增
            if (PlayerControlFocusHelper.handleKey(mBinding.control.getRoot(), getFocus2(), event)) return true;
            if (PlayerControlFocusHelper.containsFocus(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        }
```

- 位置正确：在 `handleKey` **之前**，否则 UP 已被吞，补丁无效。
- 与相邻的 `dispatchOpeningEndingAdjust`（:8763-8769）保持同一写法与同一门禁
  （`!KeyUtil.isActionDown(event) || !isVisible(control.getRoot())`），其分派体
  `dispatchOpeningAdjust`(:8771-8780) / `dispatchEndingAdjust`(:8782-8791) 就是 `isUpKey→onXxxAdd / isDownKey→onXxxSub`。
  新方法把「门禁 + 焦点分派 + 上下判断」合并在一个方法内，语义等价，可读性更好。
- 仍在 `isVisible(control.getRoot())` 块内 ⇒ 控制栏隐藏时不接管，不干扰全屏下的
  `onEpisodeKey`(:8844) / `mAudioStageVisible && dispatchAudioStageKey`(:8846) / `mKeyDown.hasEvent`(:8847) 等后续分支。

### 4.3 非回归论证

| 场景 | 修复前 | 修复后 | 结论 |
| --- | --- | --- | --- |
| speed 上按 DOWN | `CustomUpDownView` 拦截 → `onSpeedSub` | 同（`dispatchSpeedAdjust` 走同一处理器） | 语义不变 |
| speed 上按 UP | 被 `handleKey` 吞掉 | `onSpeedAdd` | 目标修复 |
| speed 上按 LEFT/RIGHT | `handleKey` 判定邻居并放行/拦截 | 新方法对非 UP/DOWN 返回 false → 仍走 `handleKey` | 不变 |
| speed 上按 CHANNEL_UP/DOWN、PAGE_UP/DOWN、MEDIA_PREVIOUS/NEXT | `CustomUpDownView` 拦截 → add/subSpeed | 同（这些键都被 `KeyUtil.isUpKey/isDownKey` 覆盖，且 `handleKey` 的 `isFocusKey` 只含 DPAD/confirm） | 不变 |
| 焦点不在 speed（其他 chip / 进度条） | `handleKey` 处理 | 新方法第二道门禁返回 false → 仍走 `handleKey` | 不变 |
| 控制栏隐藏 | 直接跳过该块 | 直接跳过该块 | 不变 |
| `mFocus2` 记忆 | speed 会被记住（`hasRememberedFocus`:8753-8757 只排除 opening/ending） | 同；且 UP 时在 `handleKey` 前 return true，不会写入 `mFocus2` | 无副作用 |

另核对了另外两处 `handleKey` 调用点，均不受影响：
`app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1297-1302` 的 `dispatchKeyEvent` **从不调用** `PlayerControlFocusHelper.handleKey`，
故 LIVE 控制栏不存在被吞 UP 的问题（其 `speed.setUpListener/setDownListener` 在 :239-240 一直有效）；
`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:5739` 属手机 flavour 且 `app/src/mobile` 下
**没有任何** `CustomUpDownView`/`setUpListener`/`setDownListener` 引用 ⇒ 本次修复对手机 flavour 零影响。

### 4.4 契约测试评审（`SpeedAdjustKeyContractTest.java`，114 行）

4 个 `@Test`，均为源码文本断言（`org.junit.Test` + `java.nio.file.Files/Path`，不依赖 Robolectric）：

| 用例 | 断言内容 |
| --- | --- |
| `speedButtonAdjustsOnBothArrows` | 方法体含 `onSpeedAdd()`/`onSpeedSub()`；`isUpKey` 出现在 `isDownKey` 之前；两分支各自带 `return true` |
| `adjustmentOnlyRunsForTheFocusedSpeedButtonWhileControlsAreShown` | `isActionDown`、`isVisible(control.getRoot())`、`mBinding.control.action.speed` 三者位置均 > 0 且都早于 `isUpKey`（锁死「门禁先于方向」） |
| `speedAdjustmentIsDispatchedBeforeTheFocusFinderSwallowsUp` | `dispatchKeyEvent` 内 `dispatchSpeedAdjust` 位置 > 0、早于 `handleKey`；含字面量 `if (dispatchSpeedAdjust(event)) return true;`；且 `opening` 早于 `speed`（锁死相邻顺序） |
| `theFixDoesNotWeakenTheFocusFinderGuard` | helper 的 `handleKey` 仍含 `if (next == null || !isDescendant(root, next)) return true;`，且剥掉注释后**不含** `speed`（禁止把特殊判断塞回 helper） |

最后一条尤其重要：它防止后人为了「省事」把倍速判断塞进 `PlayerControlFocusHelper` ——
那会让一个通用焦点工具依赖具体业务控件，并把 LiveActivity/TmdbDetailActivity 一起卷进来。
断言用 `withoutJavaComments`（正则剥 `/* */` 与 `//`）实现，注释里出现 `speed` 不会误伤。

风格上与本目录既有 Robolectric 测试（`PlayerControlFocusHelperSeekReturnTest.java`）不同，
但对象是**按键分派顺序**这类结构性契约，源码断言是恰当且更廉价的工具。

---

## 5. 验证证据

| 项 | 命令 / 位置 | 结果 |
| --- | --- | --- |
| 修复态定向单测 | `gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SpeedAdjustKeyContractTest` | **BUILD SUCCESSFUL in 34s**（后台 job `pwsh-8ff007a2365f40ae`，exit 0）；`TEST-...SpeedAdjustKeyContractTest.xml`：`tests="4" skipped="0" failures="0" errors="0"`，timestamp `2026-10-31T08:50:05Z` |
| **变异验证** | 临时删除 `VideoActivity.java:8840` 的 `if (dispatchSpeedAdjust(event)) return true;` 后重跑同一测试 | **`speedAdjustmentIsDispatchedBeforeTheFocusFinderSwallowsUp` FAILED / `java.lang.AssertionError at SpeedAdjustKeyContractTest.java:73` / 4 tests completed, 1 failed / BUILD FAILED in 55s / exit 1** |
| 变异后复原 | 用同一编辑反向恢复该行，`git status --porcelain=v1` | 只剩 3 个初始脏项（`?? %SystemDrive%/`、`?? .dbg.txt`、`?? .design-evidence/`），工作区与 HEAD 一致 |

变异验证是本节的核心：它证明 4 个用例不是「读一遍源码永远为真」的空转断言 —— 一旦修复被移除，测试立刻变红并指向确切行号。

---

## 6. 遗留项（用户裁定：仅记录，不改）

同路径存在**既有**（非本提交引入）的守卫缺口：

- `VideoActivity.java:5276-5280` `onSpeedAdd()`、:5282-5286 `onSpeedSub()`、:5288-5293 `onSpeedLong()`、:5295-5297 `saveUserSpeed()`
  都没有 `isServiceReady() || !isOwner() || player().isEmpty()` 守卫，而同文件的 `onSpeed()`（:5267-5274）有。
- `protected PlayerManager player()` = `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:110-112`
  `return mService == null ? null : mService.player();`；`mService` 在 :1011-1013（releaseBinding）与 :1339-1342（onServiceDisconnected）被置 null。
  服务断开后这些方法会 NPE。
- 片头/片尾的 `onOpeningAdd/onOpeningSub/onEndingAdd/onEndingSub` 完全同构，缺口一致。

**为什么不修**：DOWN 与长按在本修复之前就走这条路径，本提交只是让 UP 也走上来；补守卫会同时改变 DOWN/长按/片头/片尾的行为，
超出「UP 被吞」的范围，属行为扩张。按 `AGENTS.md` §2「相邻既有缺陷只报告不顺手修」，且经用户裁定，
记录于此并在 PR 描述中列为遗留项，另开任务处理。

---

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并远端 beta | 无需合并：`origin/beta` = `a5469c5ceac0…68b7` 已是 HEAD 祖先，`rev-list --left-right --count origin/beta...HEAD` = 0 / 1 |
| 评审提交 | `d1686cb44d9f84371a65a34aed709426701c549c`（2 files, +136 −0） |
| 评审结论 | **通过，无必修项**（根因认定成立、位置正确、非回归论证见 §4.3、守卫经变异验证非空转） |
| 验证 | 定向单测 4/4 通过（34s）；变异态 1 失败（55s，AssertionError at `SpeedAdjustKeyContractTest.java:73`） |
| 遗留项 | `onSpeedAdd/onSpeedSub/onSpeedLong/saveUserSpeed` 缺播放服务守卫（既有、非本次引入），仅记录，见 §6 |
| 本轮修正产物 | 本文件 `docs/C115-beta-merge-review-dev3-20261031.md`（代码零改动） |
| 推送 dev3 | 见提交后的回填 |
| PR 到 beta | 见提交后的回填 |
| 拉取远端 | 见提交后的回填 |
