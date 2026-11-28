# Silent1566/webhtv 上游更新差异报告

- 生成日期：2026-11-27
- 对比基准（你的当前项目）：本地 v5.6.51 / versionCode 611，源码对应 fork 分支 `AZHUZHITAN-patch-1` @ `1467a2c8`（v5.6.50）+ 本地 v5.6.51 二维码修复
- 上游仓库：`Silent1566/webhtv`（parent 为 `webhtv/webhtv`）
- 上游默认分支 main：`3f469e2fe517`（2026-11-25，v5.6.0 / versionCode 560）
- 本报告为**只读评估**，未修改任何代码、未编译、未同步 Git

---

## 一、核心结论

1. **上游 main 已无新更新可合并。** main 的最新提交 `3f469e2fe517` 已在之前那次 merge（fork 的 `d2cd69ba`，父提交之一即 `3f469e2fe517`）中全量合入。用 `compare/3f469e2f...main` 验证结果为 `identical`（ahead=0, behind=0）。你的项目当前**领先**上游 main 16 个提交（fork 相对 merge-base 的 plus 定制）。
2. **真正未合入的更新在 beta / dev2 / dev1 / dev3 等开发分支**。它们相对 main 各有领先提交，详见第二节。
3. **这些分支的改动与你的 plus 深度定制大面积重叠**。你的项目相对上游 main 有 199 个定制文件（主题系统 Theme*、播放器 PlayerManager/IjkPlayerEngine、广告规则、following、焦点修复等），这正是你反复强调要保留的部分。虽然单分支直接冲突文件数不多（13～14 个），但这些分支的"安全文件"里包含大量**你已改动过的同名模块的不同版本**，实际合并冲突面远大于表面数字。强行 merge 会覆盖你的修改。
4. **唯一确认属于"上游已合入但本地漏掉"的内容**：2 个 mobile 文件，来自上游提交 `bb6cca4f79c9`（fix(mobile): allow native episode grids to share page scrolling）。本地增量同步时未跟上 fork 的 merge。详见第五节。

---

## 二、各上游分支相对 main 的领先情况

| 分支 | HEAD | 领先 main | 落后 main | 改动文件 | 与你定制重叠 | 说明 |
|---|---|---|---|---|---|---|
| beta | 7f92dd470f54 | 70 | 9 | 155 | 13 | 主力开发分支，功能最全 |
| dev2 | 1cfbafbe8f0e | 77 | 9 | 168 | 14 | dev 线最新，领先最多 |
| dev3 | a89978ff414d | 45 | 9 | 107 | 13 | 中等规模开发分支 |
| dev1 | e1be89bcd553 | 9 | 9 | 15 | 2 | 小批量修复 |

> 说明：`ahead` = 该分支有而 main 没有的提交数；`behind` = main 有而该分支没有的提交数。各分支都落后 main 9 个提交，说明它们是从 main 的旧点分叉的，合并时还需处理这 9 个 main 提交的回流。

---

## 三.1 分支 `beta` 更新清单（70 提交 / 155 文件）

HEAD：`7f92dd470f54fbf85796a148875ae3bbc3caf2cd`

### 提交列表（完整 40 位 ID）

- `25541eda19b057ab84d798606d7c7e654954c19f` 2026-11-24 — feat(android): safely import PC configs without activating them
- `d7d4c79b8614ee2302f25b892f725a5da4f93ae8` 2026-11-24 — fix(android): invalidate only the imported config catalog
- `21c925bb2c131ed50fc58594eb66bec491f00cd3` 2026-11-24 — test(android): cover targeted catalog invalidation
- `a904628207f4e1fe2a28bbe25b030202df5fe0fd` 2026-11-25 — fix(tv): support HDMI-CEC seek and held speed input
- `20ca1a3b178836380dd0db4568f7019d79d8e785` 2026-11-25 — test(tv): verify HDMI-CEC input and playback on emulator
- `97ccaa488caa93f75e4d0a74100c2ffa0a45ccaa` 2026-11-25 — 合并最新 beta 到 dev1，保留 CEC 修复与配置安全导入
- `c05690566c0020d716f5e6655694ad95b48a3c98` 2026-11-25 — Merge pull request #588 from Silent1566/dev1
- `ec46089b0ac7d1e1523be0e75a9e9cce8ecf10d4` 2026-11-25 — fix(mobile): clear stale playback errors when playback becomes ready
- `e1be89bcd55335130155c7494f6662b9fdaf4769` 2026-11-25 — 合并 beta 最新代码到 dev1，保留手机版播放错误提示修复
- `144d06b722fcfdd6eeb1e5ba7fe28624aab70304` 2026-11-25 — Merge pull request #589 from Silent1566/dev1
- `31d844b179a3c6dd144a22d8f19b7e4894aa4db8` 2026-11-26 — feat(intro-skip): 新增 TODB 数据源与彩蛋/广告/休息三类片段
- `6fa23d9788d3e8ffd3cc9dba1a942144bfabdaa5` 2026-11-26 — fix(intro-skip): TODB 在 Android 9 使用隔离 TLS 1.3 客户端
- `5a0666071740ccf732b954ca38d5db397dd84a0a` 2026-11-26 — Merge pull request #590 from Silent1566/dev2
- `9e921d7da71337e207e08ed9c049c07067b6d07c` 2026-11-26 — feat(following): add per-episode following calendar with week/month views
- `0d6ac40dd89f1d6e0cf27ad43459132cf28ef53f` 2026-11-26 — style(following): default calendar to week view and refine its visual hierarchy
- `52176f9118bc1ebbda217eb55d6968a3b1f3277d` 2026-11-26 — style(following): rebuild calendar surface with frosted strip and poster agenda
- `dacaa8a283b280ffe1831a0e9b34da3e278aba01` 2026-11-26 — fix(following): let the calendar scroll away and tighten portrait copy
- `d6380682917f2ae52d1d00f518dad87d15df4c21` 2026-11-26 — Merge remote-tracking branch 'origin/beta' into dev2
- `e1f0752f42c3edb03bed12895bb380ec1cb9d4ef` 2026-11-26 — Merge pull request #591 from Silent1566/dev2
- `30817a79d6b02515c024935aaabce1281addff74` 2026-11-26 — feat(following): open an episode from the calendar and shorten the TV label
- `787ea36b48207c1f7f1a487d1087924d81af027d` 2026-11-26 — Merge remote-tracking branch 'origin/beta' into dev2
- `319d0a8b9d9750691f6085b17c500c5ffc75353e` 2026-11-26 — docs(kn-1): record the PC-parity upgrade backlog assessed on 2026-11-26
- `8fd40e443de3f1fcdd6575ea8152a628ce0b1153` 2026-11-26 — Merge pull request #593 from Silent1566/docs/kn-1-pc-parity-backlog
- `fd52b99a32fef4075eccd9731eab78e7eef854d9` 2026-11-26 — Merge pull request #592 from Silent1566/dev2
- `a58b937363ab89902b0493846099866b1385d93e` 2026-11-26 — docs(fork-scan): assess borrowable commits in other WebHTV forks
- `2a602478f51a97e51592644361b92c32c9b1501f` 2026-11-26 — fix(history): keep a real title from being overwritten by progress placeholders
- `84a839632f5207f26c24eb7b16425be4e2e9ec8a` 2026-11-26 — fix(diagnostics): classify SpiderDebug log lines by their severity prefix
- `c174f045486fe8d80e9e3c18d9fe16e9c95c1844` 2026-11-26 — feat(mobile): vertical page switching for portrait short dramas
- `108d7320cff31e9e25a28254daf36001dfae7661` 2026-11-26 — docs(fork): decision records for SMB browser and LAN remote control
- `311a9983b78e010082571af2a0898d947c51d9f9` 2026-11-26 — feat(smb): resolve SMB playback URIs without persisting credentials
- `06a4edbabdaf668d14ca431ade2274b2cb095871` 2026-11-26 — feat(mobile): log vertical page-switch arm and commit decisions
- `ad89e198f0dd077c6ba8c792006f4be8e89e82b7` 2026-11-26 — docs(fork-3): record on-glass verification of vertical page switching
- `69b54da2c17275ba3603a73ae232876146e521db` 2026-11-26 — feat(smb): store SMB credentials with the existing Keystore AES-GCM pattern
- `504f5ee76a21b946d6a42a21a861ddebbeeda179` 2026-11-26 — feat(smb): add read-only SMB browsing client with bounded timeouts
- `bfb5152d0aa40acd6da7a1dee2ea1e08286283d7` 2026-11-26 — feat(smb): add the SMB browser entry and screen behind a default-off switch
- `981fa68955f5a7658c51e45cbebc9d732af8f604` 2026-11-26 — feat(smb): add the TV settings entry for the SMB browser
- `9d273a4500cba1825bc9874956f81b365be56887` 2026-11-26 — feat(remote): execute player.command actions from the existing command channel
- `a158dee3f39683e75bb4126be60bc178c0267f41` 2026-11-26 — feat(remote): add the mobile remote panel over the existing command channel
- `be87351714bd6182fac0becd01c79698f7df09d6` 2026-11-26 — docs(fork-3): record MPV gesture verification and correct the flick threshold claim
- `500e64d1740fd358a4383d7ad56478d25290a6e6` 2026-11-26 — feat(smb): accept host:port in the SMB browser and keep the playback credential
- `a920271ecceb773b35dfff55726c2f93f8c43e5f` 2026-11-27 — docs(fork-3): record why the side-zone retest needs new test points
- `d37ead8a102c64336658e4ce0ca8e160ad4d8395` 2026-11-27 — docs: add the remaining verification checklist
- `855fd672e9bab51e1d13173402e67df8ec8db313` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `4cd2532b0a0ad5fa7693809ee27318ac35c3b664` 2026-11-27 — Merge pull request #595 from Silent1566/dev2
- `4bca7fa4b6af953165f7409d82339ab52eb90bd4` 2026-11-27 — docs(kn-1-7): decide the LAN receive-authorization approach
- `1dfde3df4bcd18da5f27077dcf9ce6c1436f2c29` 2026-11-27 — docs(kn-1-7): settle the authorization decisions and re-order the steps
- `0ca91fd8fd85d4c7eacb8a75908de17be894b157` 2026-11-27 — feat(lan): add paired-device authorization policy
- `effc209bc00061b6ea063be52856b35df253e2fc` 2026-11-27 — feat(lan): add a pinned mutual-TLS channel primitive
- `4b2839ded371fcac9361fa7538334e6c771510c2` 2026-11-27 — docs(kn-1-7): record the on-device pinned handshake evidence
- `80fb2fc83f15b96f0feac73593c0c044de9f93fb` 2026-11-27 — feat(lan): add a persistence format for paired devices
- `b087ebcfaa5f629639b1f645ce96066e3685f36d` 2026-11-27 — feat(lan): compose the paired-device registry with an encrypted store
- `83eff6b96b16e19d462aa1cf0ee69e561d08fe23` 2026-11-27 — feat(lan): store paired devices in the Android Keystore
- `564ed346e55b0bd2b849dbd06157b1fb8c8c2a22` 2026-11-27 — feat(lan): add a single entry point for LAN pairing state
- `5c750e5d8fa7c4821a33053acf8b729ef58e3cb5` 2026-11-27 — feat(lan): add the settings entry for LAN pairing
- `59b41ed39f0ea3df63227748a474637f9244b00d` 2026-11-27 — feat(lan): enforce the LAN access policy on the action endpoint
- `fb5c3a900447c5417a296efe1dc1110f9c441826` 2026-11-27 — feat(lan): expose the sensitive-write permission in the pairing dialog
- `7c2c2e1e2e5cde30f28e21d9b37059d9f197ed86` 2026-11-27 — feat(lan): add the pairing endpoint that issues one credential
- `330ed65230677dfacc26d7d406e9eccf41899ee5` 2026-11-27 — feat(lan): store the credentials this device presents to paired targets
- `a89978ff414d838eee3470886a4b046f101dc950` 2026-11-27 — fix(wallpaper): avoid redundant static cache decodes on startup
- `c2dbf35c03742c8d274737b8d96394cb71c0e337` 2026-11-27 — feat(lan): add the first-party client facade for LAN pairing
- `1bebde02a9504e52b7c19090bfb3595ddd96400d` 2026-11-27 — Merge pull request #596 from Silent1566/dev3
- `aecc65c0fe0ff0017cb3e7443ce7dae8ac9dbc47` 2026-11-27 — feat(lan): finish secure pairing, authorization, and first-party integration
- `d97d8e680880341c881c9d746e97ff70259ec606` 2026-11-27 — Merge origin/beta into dev2
- `2fe22bf893e8c820df6462c25e755caf7ee55336` 2026-11-27 — Merge pull request #597 from Silent1566/dev2
- `9192da282a66ca69a3a89a79a989d88a0550ad9f` 2026-11-27 — test: scope vertical page switch gesture test to mobile
- `5d5f7008ba16920c8c42531bc63129f2772e603a` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `b029ff511be33aba4a628061a6190d9154a56151` 2026-11-27 — Merge pull request #598 from Silent1566/dev2
- `6787a7094fb5d41fec1ea81632f9e707b0f7efc8` 2026-11-27 — fix(exo): skip invalid external subtitle entries
- `95d391fcde138e385132942ec36c0129a7f79f48` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `7f92dd470f54fbf85796a148875ae3bbc3caf2cd` 2026-11-27 — Merge pull request #599 from Silent1566/dev2

### 与你定制重叠的文件（合并必冲突，13 个）

- `app/build.gradle`（modified +1/-0）
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`（modified +21/-0）
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`（modified +29/-0）
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`（modified +1/-1）
- `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`（modified +16/-3）
- `app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java`（modified +2/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java`（modified +35/-12）
- `app/src/main/res/values-zh-rCN/strings.xml`（modified +57/-0）
- `app/src/main/res/values-zh-rTW/strings.xml`（modified +57/-0）
- `app/src/main/res/values/strings.xml`（modified +57/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`（modified +1/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（modified +35/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`（modified +28/-0）

### 其余改动文件（142 个，节选前 40）

- `app/src/androidTest/java/com/fongmi/android/tv/following/FollowingCalendarActivityDeviceTest.java`（added +212/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/player/exo/subtitle/DualSubtitlePlaybackTest.java`（modified +4/-1）
- `app/src/androidTest/java/com/fongmi/android/tv/server/LanPeerStoreTest.java`（added +120/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/server/LanSecureIntegrationTest.java`（added +271/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/server/LanTlsIdentityAndroidTest.java`（added +51/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/service/TodbTlsDeviceTest.java`（added +83/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/ui/custom/CustomKeyDownCecDeviceTest.java`（added +504/-0）
- `app/src/androidTestMobile/java/com/fongmi/android/tv/ui/activity/MobilePlayerErrorRecoveryDeviceTest.java`（added +194/-0）
- `app/src/leanback/java/com/fongmi/android/tv/bean/Func.java`（modified +3/-6）
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`（modified +89/-18）
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/CastDialog.java`（modified +66/-15）
- `app/src/leanback/res/layout/activity_following.xml`（modified +17/-0）
- `app/src/leanback/res/layout/activity_following_calendar.xml`（added +326/-0）
- `app/src/leanback/res/layout/activity_setting_enhance.xml`（modified +56/-0）
- `app/src/leanback/res/layout/item_following_calendar_day.xml`（added +41/-0）
- `app/src/leanback/res/layout/item_following_calendar_entry.xml`（added +82/-0）
- `app/src/main/AndroidManifest.xml`（modified +13/-0）
- `app/src/main/java/com/fongmi/android/tv/api/config/VodSiteCatalog.java`（modified +15/-0）
- `app/src/main/java/com/fongmi/android/tv/following/FollowingCalendarPolicy.java`（added +165/-0）
- `app/src/main/java/com/fongmi/android/tv/following/FollowingCalendarRepository.java`（added +219/-0）
- `app/src/main/java/com/fongmi/android/tv/model/FollowingCalendarViewModel.java`（added +220/-0）
- `app/src/main/java/com/fongmi/android/tv/player/IntroSkipKinds.java`（modified +13/-1）
- `app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java`（modified +6/-2）
- `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`（modified +5/-1）
- `app/src/main/java/com/fongmi/android/tv/remote/RemoteCommandExecutor.java`（modified +9/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerCommand.java`（added +91/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerOps.java`（added +95/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerTargets.java`（added +54/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanActionGate.java`（added +76/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanClient.java`（added +181/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanClientHeaders.java`（added +29/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPairingController.java`（added +125/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPairingEndpoint.java`（added +46/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerAuthorization.java`（added +155/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerPersist.java`（added +92/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerRegistry.java`（added +116/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerStore.java`（added +293/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeers.java`（added +48/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanSecureChannel.java`（added +154/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanSenderCredentialStore.java`（added +79/-0）

## 三.2 分支 `dev2` 更新清单（77 提交 / 168 文件）

HEAD：`1cfbafbe8f0e4795d5b370fe89c3249ee82b1785`

### 提交列表（完整 40 位 ID）

- `25541eda19b057ab84d798606d7c7e654954c19f` 2026-11-24 — feat(android): safely import PC configs without activating them
- `d7d4c79b8614ee2302f25b892f725a5da4f93ae8` 2026-11-24 — fix(android): invalidate only the imported config catalog
- `21c925bb2c131ed50fc58594eb66bec491f00cd3` 2026-11-24 — test(android): cover targeted catalog invalidation
- `a904628207f4e1fe2a28bbe25b030202df5fe0fd` 2026-11-25 — fix(tv): support HDMI-CEC seek and held speed input
- `20ca1a3b178836380dd0db4568f7019d79d8e785` 2026-11-25 — test(tv): verify HDMI-CEC input and playback on emulator
- `97ccaa488caa93f75e4d0a74100c2ffa0a45ccaa` 2026-11-25 — 合并最新 beta 到 dev1，保留 CEC 修复与配置安全导入
- `c05690566c0020d716f5e6655694ad95b48a3c98` 2026-11-25 — Merge pull request #588 from Silent1566/dev1
- `ec46089b0ac7d1e1523be0e75a9e9cce8ecf10d4` 2026-11-25 — fix(mobile): clear stale playback errors when playback becomes ready
- `e1be89bcd55335130155c7494f6662b9fdaf4769` 2026-11-25 — 合并 beta 最新代码到 dev1，保留手机版播放错误提示修复
- `144d06b722fcfdd6eeb1e5ba7fe28624aab70304` 2026-11-25 — Merge pull request #589 from Silent1566/dev1
- `31d844b179a3c6dd144a22d8f19b7e4894aa4db8` 2026-11-26 — feat(intro-skip): 新增 TODB 数据源与彩蛋/广告/休息三类片段
- `6fa23d9788d3e8ffd3cc9dba1a942144bfabdaa5` 2026-11-26 — fix(intro-skip): TODB 在 Android 9 使用隔离 TLS 1.3 客户端
- `5a0666071740ccf732b954ca38d5db397dd84a0a` 2026-11-26 — Merge pull request #590 from Silent1566/dev2
- `9e921d7da71337e207e08ed9c049c07067b6d07c` 2026-11-26 — feat(following): add per-episode following calendar with week/month views
- `0d6ac40dd89f1d6e0cf27ad43459132cf28ef53f` 2026-11-26 — style(following): default calendar to week view and refine its visual hierarchy
- `52176f9118bc1ebbda217eb55d6968a3b1f3277d` 2026-11-26 — style(following): rebuild calendar surface with frosted strip and poster agenda
- `dacaa8a283b280ffe1831a0e9b34da3e278aba01` 2026-11-26 — fix(following): let the calendar scroll away and tighten portrait copy
- `d6380682917f2ae52d1d00f518dad87d15df4c21` 2026-11-26 — Merge remote-tracking branch 'origin/beta' into dev2
- `e1f0752f42c3edb03bed12895bb380ec1cb9d4ef` 2026-11-26 — Merge pull request #591 from Silent1566/dev2
- `30817a79d6b02515c024935aaabce1281addff74` 2026-11-26 — feat(following): open an episode from the calendar and shorten the TV label
- `787ea36b48207c1f7f1a487d1087924d81af027d` 2026-11-26 — Merge remote-tracking branch 'origin/beta' into dev2
- `319d0a8b9d9750691f6085b17c500c5ffc75353e` 2026-11-26 — docs(kn-1): record the PC-parity upgrade backlog assessed on 2026-11-26
- `8fd40e443de3f1fcdd6575ea8152a628ce0b1153` 2026-11-26 — Merge pull request #593 from Silent1566/docs/kn-1-pc-parity-backlog
- `fd52b99a32fef4075eccd9731eab78e7eef854d9` 2026-11-26 — Merge pull request #592 from Silent1566/dev2
- `a58b937363ab89902b0493846099866b1385d93e` 2026-11-26 — docs(fork-scan): assess borrowable commits in other WebHTV forks
- `2a602478f51a97e51592644361b92c32c9b1501f` 2026-11-26 — fix(history): keep a real title from being overwritten by progress placeholders
- `84a839632f5207f26c24eb7b16425be4e2e9ec8a` 2026-11-26 — fix(diagnostics): classify SpiderDebug log lines by their severity prefix
- `c174f045486fe8d80e9e3c18d9fe16e9c95c1844` 2026-11-26 — feat(mobile): vertical page switching for portrait short dramas
- `108d7320cff31e9e25a28254daf36001dfae7661` 2026-11-26 — docs(fork): decision records for SMB browser and LAN remote control
- `311a9983b78e010082571af2a0898d947c51d9f9` 2026-11-26 — feat(smb): resolve SMB playback URIs without persisting credentials
- `06a4edbabdaf668d14ca431ade2274b2cb095871` 2026-11-26 — feat(mobile): log vertical page-switch arm and commit decisions
- `ad89e198f0dd077c6ba8c792006f4be8e89e82b7` 2026-11-26 — docs(fork-3): record on-glass verification of vertical page switching
- `69b54da2c17275ba3603a73ae232876146e521db` 2026-11-26 — feat(smb): store SMB credentials with the existing Keystore AES-GCM pattern
- `504f5ee76a21b946d6a42a21a861ddebbeeda179` 2026-11-26 — feat(smb): add read-only SMB browsing client with bounded timeouts
- `bfb5152d0aa40acd6da7a1dee2ea1e08286283d7` 2026-11-26 — feat(smb): add the SMB browser entry and screen behind a default-off switch
- `981fa68955f5a7658c51e45cbebc9d732af8f604` 2026-11-26 — feat(smb): add the TV settings entry for the SMB browser
- `9d273a4500cba1825bc9874956f81b365be56887` 2026-11-26 — feat(remote): execute player.command actions from the existing command channel
- `a158dee3f39683e75bb4126be60bc178c0267f41` 2026-11-26 — feat(remote): add the mobile remote panel over the existing command channel
- `be87351714bd6182fac0becd01c79698f7df09d6` 2026-11-26 — docs(fork-3): record MPV gesture verification and correct the flick threshold claim
- `500e64d1740fd358a4383d7ad56478d25290a6e6` 2026-11-26 — feat(smb): accept host:port in the SMB browser and keep the playback credential
- `a920271ecceb773b35dfff55726c2f93f8c43e5f` 2026-11-27 — docs(fork-3): record why the side-zone retest needs new test points
- `d37ead8a102c64336658e4ce0ca8e160ad4d8395` 2026-11-27 — docs: add the remaining verification checklist
- `855fd672e9bab51e1d13173402e67df8ec8db313` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `4cd2532b0a0ad5fa7693809ee27318ac35c3b664` 2026-11-27 — Merge pull request #595 from Silent1566/dev2
- `4bca7fa4b6af953165f7409d82339ab52eb90bd4` 2026-11-27 — docs(kn-1-7): decide the LAN receive-authorization approach
- `1dfde3df4bcd18da5f27077dcf9ce6c1436f2c29` 2026-11-27 — docs(kn-1-7): settle the authorization decisions and re-order the steps
- `0ca91fd8fd85d4c7eacb8a75908de17be894b157` 2026-11-27 — feat(lan): add paired-device authorization policy
- `effc209bc00061b6ea063be52856b35df253e2fc` 2026-11-27 — feat(lan): add a pinned mutual-TLS channel primitive
- `4b2839ded371fcac9361fa7538334e6c771510c2` 2026-11-27 — docs(kn-1-7): record the on-device pinned handshake evidence
- `80fb2fc83f15b96f0feac73593c0c044de9f93fb` 2026-11-27 — feat(lan): add a persistence format for paired devices
- `b087ebcfaa5f629639b1f645ce96066e3685f36d` 2026-11-27 — feat(lan): compose the paired-device registry with an encrypted store
- `83eff6b96b16e19d462aa1cf0ee69e561d08fe23` 2026-11-27 — feat(lan): store paired devices in the Android Keystore
- `564ed346e55b0bd2b849dbd06157b1fb8c8c2a22` 2026-11-27 — feat(lan): add a single entry point for LAN pairing state
- `5c750e5d8fa7c4821a33053acf8b729ef58e3cb5` 2026-11-27 — feat(lan): add the settings entry for LAN pairing
- `59b41ed39f0ea3df63227748a474637f9244b00d` 2026-11-27 — feat(lan): enforce the LAN access policy on the action endpoint
- `fb5c3a900447c5417a296efe1dc1110f9c441826` 2026-11-27 — feat(lan): expose the sensitive-write permission in the pairing dialog
- `7c2c2e1e2e5cde30f28e21d9b37059d9f197ed86` 2026-11-27 — feat(lan): add the pairing endpoint that issues one credential
- `330ed65230677dfacc26d7d406e9eccf41899ee5` 2026-11-27 — feat(lan): store the credentials this device presents to paired targets
- `a89978ff414d838eee3470886a4b046f101dc950` 2026-11-27 — fix(wallpaper): avoid redundant static cache decodes on startup
- `c2dbf35c03742c8d274737b8d96394cb71c0e337` 2026-11-27 — feat(lan): add the first-party client facade for LAN pairing
- `1bebde02a9504e52b7c19090bfb3595ddd96400d` 2026-11-27 — Merge pull request #596 from Silent1566/dev3
- `aecc65c0fe0ff0017cb3e7443ce7dae8ac9dbc47` 2026-11-27 — feat(lan): finish secure pairing, authorization, and first-party integration
- `d97d8e680880341c881c9d746e97ff70259ec606` 2026-11-27 — Merge origin/beta into dev2
- `2fe22bf893e8c820df6462c25e755caf7ee55336` 2026-11-27 — Merge pull request #597 from Silent1566/dev2
- `9192da282a66ca69a3a89a79a989d88a0550ad9f` 2026-11-27 — test: scope vertical page switch gesture test to mobile
- `5d5f7008ba16920c8c42531bc63129f2772e603a` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `b029ff511be33aba4a628061a6190d9154a56151` 2026-11-27 — Merge pull request #598 from Silent1566/dev2
- `6787a7094fb5d41fec1ea81632f9e707b0f7efc8` 2026-11-27 — fix(exo): skip invalid external subtitle entries
- `95d391fcde138e385132942ec36c0129a7f79f48` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `7f92dd470f54fbf85796a148875ae3bbc3caf2cd` 2026-11-27 — Merge pull request #599 from Silent1566/dev2
- `63cde9c6c516018556b71c61e793d85335a33db7` 2026-11-27 — fix(exo): infer missing external subtitle MIME from URL path
- `2ef5aea4bfbb3f7f35186ff34a40977f723b14c0` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `b5f851b109f78a023b9f61e7d1f52165f216eca1` 2026-11-27 — fix(exo): resolve selected external ASS by current media identity
- `160af2450e351f3b4863729a5725ac2291e3bceb` 2026-11-27 — fix(subtitle): harden subtitle ZIP extraction against traversal and corruption
- `f4214f9fc8b5dd37eab4a5a1f7450615f4c27b00` 2026-11-27 — fix(server): return 404 for a missing local file instead of 500
- `fd0b2fc10360338b572ab77bca638c6e3e1521a0` 2026-11-27 — fix(server): confine local file endpoints to the shared root
- `1cfbafbe8f0e4795d5b370fe89c3249ee82b1785` 2026-11-27 — fix(server): route uploaded ZIP files through the hardened extractor

### 与你定制重叠的文件（合并必冲突，14 个）

- `app/build.gradle`（modified +1/-0）
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`（modified +21/-0）
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`（modified +29/-0）
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`（modified +1/-1）
- `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`（modified +32/-1）
- `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`（modified +16/-3）
- `app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java`（modified +2/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java`（modified +35/-12）
- `app/src/main/res/values-zh-rCN/strings.xml`（modified +57/-0）
- `app/src/main/res/values-zh-rTW/strings.xml`（modified +57/-0）
- `app/src/main/res/values/strings.xml`（modified +57/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`（modified +1/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（modified +35/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`（modified +28/-0）

### 其余改动文件（154 个，节选前 40）

- `app/src/androidTest/java/com/fongmi/android/tv/following/FollowingCalendarActivityDeviceTest.java`（added +212/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/player/exo/subtitle/DualSubtitlePlaybackTest.java`（modified +34/-1）
- `app/src/androidTest/java/com/fongmi/android/tv/server/LanPeerStoreTest.java`（added +120/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/server/LanSecureIntegrationTest.java`（added +271/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/server/LanTlsIdentityAndroidTest.java`（added +51/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/service/TodbTlsDeviceTest.java`（added +83/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/ui/custom/CustomKeyDownCecDeviceTest.java`（added +504/-0）
- `app/src/androidTestMobile/java/com/fongmi/android/tv/ui/activity/MobilePlayerErrorRecoveryDeviceTest.java`（added +194/-0）
- `app/src/leanback/java/com/fongmi/android/tv/bean/Func.java`（modified +3/-6）
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`（modified +89/-18）
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/CastDialog.java`（modified +66/-15）
- `app/src/leanback/res/layout/activity_following.xml`（modified +17/-0）
- `app/src/leanback/res/layout/activity_following_calendar.xml`（added +326/-0）
- `app/src/leanback/res/layout/activity_setting_enhance.xml`（modified +56/-0）
- `app/src/leanback/res/layout/item_following_calendar_day.xml`（added +41/-0）
- `app/src/leanback/res/layout/item_following_calendar_entry.xml`（added +82/-0）
- `app/src/main/AndroidManifest.xml`（modified +13/-0）
- `app/src/main/java/com/fongmi/android/tv/api/config/VodSiteCatalog.java`（modified +15/-0）
- `app/src/main/java/com/fongmi/android/tv/following/FollowingCalendarPolicy.java`（added +165/-0）
- `app/src/main/java/com/fongmi/android/tv/following/FollowingCalendarRepository.java`（added +219/-0）
- `app/src/main/java/com/fongmi/android/tv/model/FollowingCalendarViewModel.java`（added +220/-0）
- `app/src/main/java/com/fongmi/android/tv/player/IntroSkipKinds.java`（modified +13/-1）
- `app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java`（modified +6/-2）
- `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`（modified +22/-4）
- `app/src/main/java/com/fongmi/android/tv/player/exo/ass/AssInput.java`（modified +12/-9）
- `app/src/main/java/com/fongmi/android/tv/remote/RemoteCommandExecutor.java`（modified +9/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerCommand.java`（added +91/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerOps.java`（added +95/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerTargets.java`（added +54/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanActionGate.java`（added +76/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanClient.java`（added +181/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanClientHeaders.java`（added +29/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPairingController.java`（added +125/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPairingEndpoint.java`（added +46/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerAuthorization.java`（added +155/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerPersist.java`（added +92/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerRegistry.java`（added +116/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeerStore.java`（added +293/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanPeers.java`（added +48/-0）
- `app/src/main/java/com/fongmi/android/tv/server/LanSecureChannel.java`（added +154/-0）

## 三.3 分支 `dev3` 更新清单（45 提交 / 107 文件）

HEAD：`a89978ff414d838eee3470886a4b046f101dc950`

### 提交列表（完整 40 位 ID）

- `25541eda19b057ab84d798606d7c7e654954c19f` 2026-11-24 — feat(android): safely import PC configs without activating them
- `d7d4c79b8614ee2302f25b892f725a5da4f93ae8` 2026-11-24 — fix(android): invalidate only the imported config catalog
- `21c925bb2c131ed50fc58594eb66bec491f00cd3` 2026-11-24 — test(android): cover targeted catalog invalidation
- `a904628207f4e1fe2a28bbe25b030202df5fe0fd` 2026-11-25 — fix(tv): support HDMI-CEC seek and held speed input
- `20ca1a3b178836380dd0db4568f7019d79d8e785` 2026-11-25 — test(tv): verify HDMI-CEC input and playback on emulator
- `97ccaa488caa93f75e4d0a74100c2ffa0a45ccaa` 2026-11-25 — 合并最新 beta 到 dev1，保留 CEC 修复与配置安全导入
- `c05690566c0020d716f5e6655694ad95b48a3c98` 2026-11-25 — Merge pull request #588 from Silent1566/dev1
- `ec46089b0ac7d1e1523be0e75a9e9cce8ecf10d4` 2026-11-25 — fix(mobile): clear stale playback errors when playback becomes ready
- `e1be89bcd55335130155c7494f6662b9fdaf4769` 2026-11-25 — 合并 beta 最新代码到 dev1，保留手机版播放错误提示修复
- `144d06b722fcfdd6eeb1e5ba7fe28624aab70304` 2026-11-25 — Merge pull request #589 from Silent1566/dev1
- `31d844b179a3c6dd144a22d8f19b7e4894aa4db8` 2026-11-26 — feat(intro-skip): 新增 TODB 数据源与彩蛋/广告/休息三类片段
- `6fa23d9788d3e8ffd3cc9dba1a942144bfabdaa5` 2026-11-26 — fix(intro-skip): TODB 在 Android 9 使用隔离 TLS 1.3 客户端
- `5a0666071740ccf732b954ca38d5db397dd84a0a` 2026-11-26 — Merge pull request #590 from Silent1566/dev2
- `9e921d7da71337e207e08ed9c049c07067b6d07c` 2026-11-26 — feat(following): add per-episode following calendar with week/month views
- `0d6ac40dd89f1d6e0cf27ad43459132cf28ef53f` 2026-11-26 — style(following): default calendar to week view and refine its visual hierarchy
- `52176f9118bc1ebbda217eb55d6968a3b1f3277d` 2026-11-26 — style(following): rebuild calendar surface with frosted strip and poster agenda
- `dacaa8a283b280ffe1831a0e9b34da3e278aba01` 2026-11-26 — fix(following): let the calendar scroll away and tighten portrait copy
- `d6380682917f2ae52d1d00f518dad87d15df4c21` 2026-11-26 — Merge remote-tracking branch 'origin/beta' into dev2
- `e1f0752f42c3edb03bed12895bb380ec1cb9d4ef` 2026-11-26 — Merge pull request #591 from Silent1566/dev2
- `30817a79d6b02515c024935aaabce1281addff74` 2026-11-26 — feat(following): open an episode from the calendar and shorten the TV label
- `787ea36b48207c1f7f1a487d1087924d81af027d` 2026-11-26 — Merge remote-tracking branch 'origin/beta' into dev2
- `319d0a8b9d9750691f6085b17c500c5ffc75353e` 2026-11-26 — docs(kn-1): record the PC-parity upgrade backlog assessed on 2026-11-26
- `8fd40e443de3f1fcdd6575ea8152a628ce0b1153` 2026-11-26 — Merge pull request #593 from Silent1566/docs/kn-1-pc-parity-backlog
- `fd52b99a32fef4075eccd9731eab78e7eef854d9` 2026-11-26 — Merge pull request #592 from Silent1566/dev2
- `a58b937363ab89902b0493846099866b1385d93e` 2026-11-26 — docs(fork-scan): assess borrowable commits in other WebHTV forks
- `2a602478f51a97e51592644361b92c32c9b1501f` 2026-11-26 — fix(history): keep a real title from being overwritten by progress placeholders
- `84a839632f5207f26c24eb7b16425be4e2e9ec8a` 2026-11-26 — fix(diagnostics): classify SpiderDebug log lines by their severity prefix
- `c174f045486fe8d80e9e3c18d9fe16e9c95c1844` 2026-11-26 — feat(mobile): vertical page switching for portrait short dramas
- `108d7320cff31e9e25a28254daf36001dfae7661` 2026-11-26 — docs(fork): decision records for SMB browser and LAN remote control
- `311a9983b78e010082571af2a0898d947c51d9f9` 2026-11-26 — feat(smb): resolve SMB playback URIs without persisting credentials
- `06a4edbabdaf668d14ca431ade2274b2cb095871` 2026-11-26 — feat(mobile): log vertical page-switch arm and commit decisions
- `ad89e198f0dd077c6ba8c792006f4be8e89e82b7` 2026-11-26 — docs(fork-3): record on-glass verification of vertical page switching
- `69b54da2c17275ba3603a73ae232876146e521db` 2026-11-26 — feat(smb): store SMB credentials with the existing Keystore AES-GCM pattern
- `504f5ee76a21b946d6a42a21a861ddebbeeda179` 2026-11-26 — feat(smb): add read-only SMB browsing client with bounded timeouts
- `bfb5152d0aa40acd6da7a1dee2ea1e08286283d7` 2026-11-26 — feat(smb): add the SMB browser entry and screen behind a default-off switch
- `981fa68955f5a7658c51e45cbebc9d732af8f604` 2026-11-26 — feat(smb): add the TV settings entry for the SMB browser
- `9d273a4500cba1825bc9874956f81b365be56887` 2026-11-26 — feat(remote): execute player.command actions from the existing command channel
- `a158dee3f39683e75bb4126be60bc178c0267f41` 2026-11-26 — feat(remote): add the mobile remote panel over the existing command channel
- `be87351714bd6182fac0becd01c79698f7df09d6` 2026-11-26 — docs(fork-3): record MPV gesture verification and correct the flick threshold claim
- `500e64d1740fd358a4383d7ad56478d25290a6e6` 2026-11-26 — feat(smb): accept host:port in the SMB browser and keep the playback credential
- `a920271ecceb773b35dfff55726c2f93f8c43e5f` 2026-11-27 — docs(fork-3): record why the side-zone retest needs new test points
- `d37ead8a102c64336658e4ce0ca8e160ad4d8395` 2026-11-27 — docs: add the remaining verification checklist
- `855fd672e9bab51e1d13173402e67df8ec8db313` 2026-11-27 — Merge remote-tracking branch 'origin/beta' into dev2
- `4cd2532b0a0ad5fa7693809ee27318ac35c3b664` 2026-11-27 — Merge pull request #595 from Silent1566/dev2
- `a89978ff414d838eee3470886a4b046f101dc950` 2026-11-27 — fix(wallpaper): avoid redundant static cache decodes on startup

### 与你定制重叠的文件（合并必冲突，13 个）

- `app/build.gradle`（modified +1/-0）
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`（modified +14/-0）
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`（modified +29/-0）
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`（modified +1/-1）
- `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`（modified +16/-3）
- `app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java`（modified +2/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java`（modified +35/-12）
- `app/src/main/res/values-zh-rCN/strings.xml`（modified +57/-0）
- `app/src/main/res/values-zh-rTW/strings.xml`（modified +57/-0）
- `app/src/main/res/values/strings.xml`（modified +57/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`（modified +1/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（modified +35/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`（modified +21/-0）

### 其余改动文件（94 个，节选前 40）

- `app/src/androidTest/java/com/fongmi/android/tv/following/FollowingCalendarActivityDeviceTest.java`（added +212/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/service/TodbTlsDeviceTest.java`（added +83/-0）
- `app/src/androidTest/java/com/fongmi/android/tv/ui/custom/CustomKeyDownCecDeviceTest.java`（added +504/-0）
- `app/src/androidTestMobile/java/com/fongmi/android/tv/ui/activity/MobilePlayerErrorRecoveryDeviceTest.java`（added +194/-0）
- `app/src/leanback/java/com/fongmi/android/tv/bean/Func.java`（modified +3/-6）
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`（modified +89/-18）
- `app/src/leanback/res/layout/activity_following.xml`（modified +17/-0）
- `app/src/leanback/res/layout/activity_following_calendar.xml`（added +326/-0）
- `app/src/leanback/res/layout/activity_setting_enhance.xml`（modified +28/-0）
- `app/src/leanback/res/layout/item_following_calendar_day.xml`（added +41/-0）
- `app/src/leanback/res/layout/item_following_calendar_entry.xml`（added +82/-0）
- `app/src/main/AndroidManifest.xml`（modified +13/-0）
- `app/src/main/java/com/fongmi/android/tv/api/config/VodSiteCatalog.java`（modified +15/-0）
- `app/src/main/java/com/fongmi/android/tv/following/FollowingCalendarPolicy.java`（added +165/-0）
- `app/src/main/java/com/fongmi/android/tv/following/FollowingCalendarRepository.java`（added +219/-0）
- `app/src/main/java/com/fongmi/android/tv/model/FollowingCalendarViewModel.java`（added +220/-0）
- `app/src/main/java/com/fongmi/android/tv/player/IntroSkipKinds.java`（modified +13/-1）
- `app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java`（modified +6/-2）
- `app/src/main/java/com/fongmi/android/tv/remote/RemoteCommandExecutor.java`（modified +9/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerCommand.java`（added +91/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerOps.java`（added +95/-0）
- `app/src/main/java/com/fongmi/android/tv/remote/RemotePlayerTargets.java`（added +54/-0）
- `app/src/main/java/com/fongmi/android/tv/server/Nano.java`（modified +15/-1）
- `app/src/main/java/com/fongmi/android/tv/server/process/ConfigImport.java`（added +283/-0）
- `app/src/main/java/com/fongmi/android/tv/server/process/DebugLogs.java`（modified +1/-1）
- `app/src/main/java/com/fongmi/android/tv/server/process/Manage.java`（modified +46/-0）
- `app/src/main/java/com/fongmi/android/tv/service/IntroSkipService.java`（modified +233/-28）
- `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java`（modified +112/-2）
- `app/src/main/java/com/fongmi/android/tv/service/TodbHttpClient.java`（added +64/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbClient.java`（added +174/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbCredential.java`（added +37/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbCredentialCodec.java`（added +40/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbCredentialStore.java`（added +168/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbPath.java`（added +64/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbTimeout.java`（added +54/-0）
- `app/src/main/java/com/fongmi/android/tv/smb/SmbUriResolver.java`（added +190/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingCalendarActivity.java`（added +404/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/activity/SmbBrowserActivity.java`（added +308/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingCalendarDayAdapter.java`（added +106/-0）
- `app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingCalendarEntryAdapter.java`（added +268/-0）

## 三.4 分支 `dev1` 更新清单（9 提交 / 15 文件）

HEAD：`e1be89bcd55335130155c7494f6662b9fdaf4769`

### 提交列表（完整 40 位 ID）

- `25541eda19b057ab84d798606d7c7e654954c19f` 2026-11-24 — feat(android): safely import PC configs without activating them
- `d7d4c79b8614ee2302f25b892f725a5da4f93ae8` 2026-11-24 — fix(android): invalidate only the imported config catalog
- `21c925bb2c131ed50fc58594eb66bec491f00cd3` 2026-11-24 — test(android): cover targeted catalog invalidation
- `a904628207f4e1fe2a28bbe25b030202df5fe0fd` 2026-11-25 — fix(tv): support HDMI-CEC seek and held speed input
- `20ca1a3b178836380dd0db4568f7019d79d8e785` 2026-11-25 — test(tv): verify HDMI-CEC input and playback on emulator
- `97ccaa488caa93f75e4d0a74100c2ffa0a45ccaa` 2026-11-25 — 合并最新 beta 到 dev1，保留 CEC 修复与配置安全导入
- `c05690566c0020d716f5e6655694ad95b48a3c98` 2026-11-25 — Merge pull request #588 from Silent1566/dev1
- `ec46089b0ac7d1e1523be0e75a9e9cce8ecf10d4` 2026-11-25 — fix(mobile): clear stale playback errors when playback becomes ready
- `e1be89bcd55335130155c7494f6662b9fdaf4769` 2026-11-25 — 合并 beta 最新代码到 dev1，保留手机版播放错误提示修复

### 与你定制重叠的文件（合并必冲突，2 个）

- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`（modified +1/-0）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（modified +1/-0）

### 其余改动文件（13 个，节选前 40）

- `app/src/androidTest/java/com/fongmi/android/tv/ui/custom/CustomKeyDownCecDeviceTest.java`（added +504/-0）
- `app/src/androidTestMobile/java/com/fongmi/android/tv/ui/activity/MobilePlayerErrorRecoveryDeviceTest.java`（added +194/-0）
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`（modified +89/-18）
- `app/src/main/java/com/fongmi/android/tv/api/config/VodSiteCatalog.java`（modified +15/-0）
- `app/src/main/java/com/fongmi/android/tv/server/Nano.java`（modified +15/-1）
- `app/src/main/java/com/fongmi/android/tv/server/process/ConfigImport.java`（added +283/-0）
- `app/src/main/java/com/fongmi/android/tv/server/process/Manage.java`（modified +46/-0）
- `app/src/test/java/com/fongmi/android/tv/server/process/CatalogInvalidateTest.java`（added +51/-0）
- `app/src/test/java/com/fongmi/android/tv/server/process/ConfigImportTest.java`（added +323/-0）
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownCecInputTest.java`（added +355/-0）
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/MobilePlayerErrorRecoveryTest.java`（added +188/-0）
- `docs/C-TV-SEEK-OWNERSHIP-tv-keys.md`（modified +101/-0）
- `docs/pc-config-copy-import.md`（added +41/-0）

---

## 四、你的 199 个定制文件（必须保留，不可被上游覆盖）

以下文件是你的 plus 版相对上游 main 的定制，任何上游合并都必须保留本地版本：

- `README.md`
- `UPDATE_NOTES.md`
- `app/build.gradle`
- `app/libs/ijkplayer-cmake-release.aar`
- `app/schemas/com.fongmi.android.tv.db.AppDatabase/50.json`
- `app/src/androidTest/java/com/fongmi/android/tv/theme/TvFocusRingResolutionDeviceTest.java`
- `app/src/leanback/AndroidManifest.xml`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/CastActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/KeepActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAdActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAiActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAppearanceActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPersonalActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingSubtitleActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingTmdbActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/KeepAdapter.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/QualityAdapter.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomRowPresenter.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/DohDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/EpisodeListDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/presenter/FuncPresenter.java`
- `app/src/leanback/res/drawable-nodpi/startup_logo.png`
- `app/src/leanback/res/drawable-nodpi/wallpaper_1.webp`
- `app/src/leanback/res/drawable/selector_control_sheet_button.xml`
- `app/src/leanback/res/drawable/selector_episode_dialog_page.xml`
- `app/src/leanback/res/drawable/selector_video_item.xml`
- `app/src/leanback/res/layout/activity_setting_ad.xml`
- `app/src/leanback/res/layout/activity_setting_personal.xml`
- `app/src/leanback/res/layout/activity_setting_player.xml`
- `app/src/leanback/res/layout/dialog_ad_rule_manage.xml`
- `app/src/leanback/res/mipmap-hdpi/ic_banner.png`
- `app/src/leanback/res/mipmap-mdpi/ic_banner.png`
- `app/src/leanback/res/mipmap-xhdpi/ic_banner.png`
- `app/src/leanback/res/mipmap-xxhdpi/ic_banner.png`
- `app/src/leanback/res/mipmap-xxxhdpi/ic_banner.png`
- `app/src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/AdRuleTransfer.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Backup.java`
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Keep.java`
- `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`
- `app/src/main/java/com/fongmi/android/tv/db/Migrations.java`
- `app/src/main/java/com/fongmi/android/tv/db/dao/HistoryDao.java`
- `app/src/main/java/com/fongmi/android/tv/following/FollowingPlaybackBridge.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`
- `app/src/main/java/com/fongmi/android/tv/playback/ProgressVault.java`
- `app/src/main/java/com/fongmi/android/tv/player/PlaybackSwitchLog.java`
- `app/src/main/java/com/fongmi/android/tv/player/PlayerHelper.java`
- `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/IjkPlayerEngine.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/IjkPlayerListener.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/IjkSimplePlayer.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/SystemSimplePlayer.java`
- `app/src/main/java/com/fongmi/android/tv/player/exo/ExoHlsAdblockDataSource.java`
- `app/src/main/java/com/fongmi/android/tv/player/ijk/IjkFirstFrameWatchdog.java`
- `app/src/main/java/com/fongmi/android/tv/player/ijk/IjkRuntimeProfilePolicy.java`
- `app/src/main/java/com/fongmi/android/tv/player/mpv/MpvPreloadPolicy.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/M3u8.java`
- `app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java`
- `app/src/main/java/com/fongmi/android/tv/setting/AppBranding.java`
- `app/src/main/java/com/fongmi/android/tv/setting/KernelPerformanceSetting.java`
- `app/src/main/java/com/fongmi/android/tv/setting/PlaybackPerformanceSetting.java`
- `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java`
- `app/src/main/java/com/fongmi/android/tv/setting/PreloadSetting.java`
- `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeContrast.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemePresets.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeProfileStore.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeResolver.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeRole.java`
- `app/src/main/java/com/fongmi/android/tv/theme/ThemeTokens.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- `app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbCardFocusHelper.java`
- `app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbEpisodeAdapter.java`
- `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java`
- `app/src/main/java/com/fongmi/android/tv/ui/custom/ReturnFocusHint.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/ChoiceDialog.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/DebugLogDialog.java`
- `app/src/main/java/com/fongmi/android/tv/utils/EpisodeTitleCompact.java`
- `app/src/main/java/com/fongmi/android/tv/utils/Github.java`
- `app/src/main/java/com/fongmi/android/tv/utils/GithubProxy.java`
- `app/src/main/java/com/fongmi/android/tv/utils/HlsAdblockNotice.java`
- `app/src/main/java/com/fongmi/android/tv/utils/UiRestart.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/AbstractMediaPlayer.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/IMediaPlayer.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/IjkLibLoader.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/IjkMediaCodecInfo.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/IjkMediaMeta.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/IjkMediaPlayer.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/IjkTimedText.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/MediaSource.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/a.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/annotations/AccessedByNative.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/annotations/CalledByNative.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/exceptions/IjkMediaException.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/ffmpeg/FFmpegApi.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/misc/AndroidTrackInfo.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/misc/IAndroidIO.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/misc/IMediaDataSource.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/misc/ITrackInfo.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/misc/IjkTrackInfo.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/pragma/DebugLog.java`
- `app/src/main/java/tv/danmaku/ijk/media/player/pragma/Pragma.java`
- `app/src/main/jniLibs/arm64-v8a/libijkffmpeg.so`
- `app/src/main/jniLibs/arm64-v8a/libijkplayer.so`
- `app/src/main/jniLibs/arm64-v8a/libijksdl.so`
- `app/src/main/jniLibs/armeabi-v7a/libijkffmpeg.so`
- `app/src/main/jniLibs/armeabi-v7a/libijkplayer.so`
- `app/src/main/jniLibs/armeabi-v7a/libijksdl.so`
- `app/src/main/res/color/focus_ring_primary.xml`
- `app/src/main/res/color/following_button_primary_text.xml`
- `app/src/main/res/color/following_button_secondary_text.xml`
- `app/src/main/res/color/plugin_manager_action_icon.xml`
- `app/src/main/res/drawable-nodpi/startup_logo.png`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/drawable/selector_episode_card.xml`
- `app/src/main/res/drawable/selector_mpv_tab_focus.xml`
- `app/src/main/res/drawable/selector_tmdb_cast_focus.xml`
- `app/src/main/res/drawable/selector_tmdb_media_focus.xml`
- `app/src/main/res/layout/dialog_cache_management.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/values-night/webhtv_tokens.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/ids.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/webhtv_tokens.xml`
- `app/src/mobile/AndroidManifest.xml`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/KeepActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/adapter/KeepAdapter.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingAdFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPersonalFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPlayerFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java`
- `app/src/mobile/res/drawable-nodpi/mobile_startup.png`
- `app/src/mobile/res/drawable-nodpi/wallpaper_1.webp`
- `app/src/mobile/res/layout/dialog_ad_rule_manage.xml`
- `app/src/mobile/res/layout/dialog_cache_management.xml`
- `app/src/mobile/res/layout/fragment_setting_ad.xml`
- `app/src/mobile/res/layout/fragment_setting_personal.xml`
- `app/src/mobile/res/layout/fragment_setting_player.xml`
- `app/src/test/java/com/fongmi/android/tv/player/exo/ExoHlsAdblockDataSourceTest.java`
- `app/src/test/java/com/fongmi/android/tv/setting/PlayerSettingTest.java`
- `app/src/test/java/com/fongmi/android/tv/setting/PreloadDefaultsPolicyTest.java`
- `app/src/test/java/com/fongmi/android/tv/setting/SettingPlayerSeekBarContractTest.java`
- `app/src/test/java/com/fongmi/android/tv/theme/ThemeBinderContractTest.java`
- `app/src/test/java/com/fongmi/android/tv/theme/ThemeControllerContractTest.java`
- `app/src/test/java/com/fongmi/android/tv/theme/ThemePresetsTest.java`
- `app/src/test/java/com/fongmi/android/tv/theme/ThemeResolverOverrideTest.java`
- `app/src/test/java/com/fongmi/android/tv/utils/HlsAdblockNoticeTest.java`
- `app/src/test/java/com/fongmi/android/tv/utils/HlsAdblockPipelineTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/theme/ThemeBinderFocusRingBehaviorTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/theme/ThemeBinderItemOverlayTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/NativeEnhancedPlaybackStyleFocusTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/SearchFieldFocusStateTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/SettingBackFocusContractTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvAppSurfaceFocusRingDeviceTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/RemoteFocusAndPagingContractTest.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivityLayoutTest.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/TvAppExitSourceTest.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java`
- `build_release_optimized.sh`
- `docs/ijk-single-lib-provenance.md`
- `gradle.properties`
- `local.properties`
- `nodejs/build.gradle`
- `scripts/build_ijk_native.sh`
- `scripts/bump_version.py`
- `third_party/ijk-single-lib-lock.json`

---

## 五、唯一确认漏合的上游内容（可安全补齐）

上游提交 `bb6cca4f79c9f835da0a96502d5f71a44004d218` — fix(mobile): allow native episode grids to share page scrolling（2026-11-23，已随 `d2cd69ba` 合入 fork，但本地增量同步漏掉）：

| 文件 | 本地状态 | fork 状态 | 差异 |
|---|---|---|---|
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 12005 行 | 12006 行 | 本地少 1 行：`mBinding.episode.setNestedScrollingEnabled(shouldUseUpstreamNativeEpisodeModule());` |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java` | 4182 行 | 4202 行 | 本地少 21 行配套测试（native episode viewport nested scroll 相关） |

> 注意：本地这 2 个文件还有你自己的改动（三方都不同），补齐时需手工合并这 1 行/21 行，不能直接覆盖。

---

## 六、建议与后续

- 若目标是"同步上游 main 的正式更新"：**已无更新可做**，你的项目已是最新且领先。
- 若目标是"引入 beta/dev 的新功能"：建议采用**逐功能评估 + 定向移植**而非整分支 merge，一次一个功能单元，避免覆盖 199 个定制文件。可指定具体功能（如某个播放器修复、主题改进）让我单独评估。
- 若只是想补齐第五节漏合的 2 行改动：可单独执行，风险最低。

（报告结束。所有数据来自 GitHub API 实时查询，提交 ID 为完整 40 位。）