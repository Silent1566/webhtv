# TV 播放器控制栏确认点击回归修复

## Recovery anchor

- 目标：修复 TV 版播放器控制栏按钮获得焦点后按确认键无响应。
- 验收：Leanback `VideoActivity.initEvent()` 为控制栏按钮保留 `OnClickListener`，遥控器确认键可沿 Android View 点击链执行既有动作；回归测试覆盖关键按钮，Leanback Java 编译通过。
- 根因证据：`7dc58af1b0bb28818b23f43748c3ac67f76e0449`（2026-09-13 05:57，C4 合并）将 Leanback `VideoActivity.initEvent()` 中原有控制栏监听块删除；合并前父提交包含播放器、解码、画质、LUT、弹幕等监听，合并后只剩播放/上一集/下一集/选集及上下调节监听。
- 当前修改：恢复丢失的 TV 控制栏监听，并恢复焦点滚动、选集/片头跳过初始化接线；新增 `PlayerControlFocusIntegrationTest.leanbackPlaybackControlButtonsKeepConfirmActionsWired` 防止再次回归。
- 已完成证据：`:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.PlayerControlFocusIntegrationTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 成功（2026-09-14）；`git diff --check` 成功。错误 flavor 的测试命令未找到测试，不作为代码失败证据。
- 未验证风险：尚未在实体 TV 上执行遥控器场景；本次仅改 Java 事件接线，没有打 APK。
- 回滚锚点：回滚本任务提交即可恢复到 `54f79af4e0fc1f5d7d7d7c159de5eab66a1db1f2`。
- 下一步：完成任务提交和恢复标签。
