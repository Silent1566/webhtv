# OSD 媒体信息徽标（osd-media-badges）

**Recovery anchor**

- 目标：播放器显示当前媒体的徽标（分辨率 / 动态范围 / 画面来源与画质 / 视频编码 / 音频编码 / 声道 / 帧率 / 码率 / 容器 / 特别标签 / IMAX / 流媒体平台），**同一行横排**，每条为「现成徽标牌位图 + 可选文本 + 类别色胶囊」；没有对应牌的事实（帧率 / 码率 / 容器）只显示文本，不再用语义不符的自绘图标。展示语义两条：**控制栏唤醒时无条件显示**（不看「媒体信息」开关，因为控制栏自带片名与分辨率，徽标是它的补充信息）；**控制栏隐藏时**，只有「屏显设置 → 媒体信息」开关打开才作为常驻项显示，与标题 / 时间 / 进度 / 码率同级。开关进「屏显设置」多选（**默认关**），位置独立成行四角单选（默认右下角）。
- 验收：开关默认关闭时不显示任何常驻徽标；控制栏一旦唤醒，无论开关与否都显示徽标；打开开关且媒体信息可解析时，控制栏隐藏后徽标也在所选角落按类别色胶囊横排常驻；Exo/MPV/IJK 三引擎均不崩溃。（第三轮起按用户明确授权打包现成徽标位图，第四轮扩到参考 JSON 全量 **100 张**，第五轮并入扩充包再补 **27 张**、共 **127 张**，其中含 Dolby / DTS / IMAX / 各家流媒体平台的商标与字标，属于**用户已知并接受**的取舍。）
- Lane/scope：task_guard `osd-media-badges`（mode=standard），base HEAD `4dda4b56b8d67e0cb78e6617f7b0f776591383a9`，branch `dev1`。
- 回滚：单一逻辑提交，回滚该提交即可完整撤销；新增键 `player_osd_media_badges` / `player_osd_media_badge_position` 未被引用时是惰性偏好项，不会影响旧版本行为。
- 后续任务：`osd-badge-icons`（base HEAD `49e5b2c4bc05a5e1e05f2bc18327d1c02079b9f5`）把徽标从「常驻屏显」改为「控制栏可见时展示」并加图标；`osd-badge-plates`（base HEAD `7d4bee6ce1`）改为横排 + 类别色 + 现成徽标牌；`osd-badge-plates-all`（base HEAD `0dc165a9a2`）把参考 JSON 的 **100 条规则 + 100 张牌图全量入库**，并删掉语义不符的自绘兜底图标；`osd-badges-persist`（base HEAD `39e1b4a0d4`）把徽标从「只在控制栏可见时渲染」改为「控制栏唤醒无条件显示 + 开关打开后常驻显示」；`osd-badge-expansion`（base HEAD `192b55c2fc`）并入参考扩充包的媒体事实规则、把牌图扩到 127 张，并统一同行高度。

## 1. 需求与取舍

用户参考实现（截图）：播放器片上叠加 "4K ULTRA HD / WEB-DL NO RE-ENCODE / Dolby Vision / E-AC-3 5.1 48kHz / MKV / CHS 简中" 一套徽标，并要求"匹配媒体徽标""媒体徽标来自哪里""看是放到屏显中还是独立设置"。

结论分三档（用户已确认"接受只显示可证事实""允许共存"）：

| 档位 | 内容 | 本项目处置 |
| --- | --- | --- |
| 运行时可证 | 分辨率、HDR/DV、视频编码、帧率、码率、音频编码/声道/采样率、容器 | 默认显示 |
| 文件名可证 | 画面来源（REMUX / BLU-RAY / WEB）、画质（WEB-DL / WebRip / HDTV …）、特别标签（PROPER / REPACK / HYBRID / CRITERION …）、IMAX、流媒体平台（NF / CR / DSNP …） | 第四轮起**按文件名匹配**：素材只认播放器已拿到的 URL 文件名与标题，匹配不到就不显示（宁缺毋滥） |
| 半可证 | HLS/DASH/网盘等流类型 | 不显示（避免误导，且 manifest 无容器语义） |
| 不可证 | 字幕语言、声道以外的音轨语言标签 | **放弃** |

第三轮时的处置是「放弃发布标签」，理由：本项目片源来自爬虫直链 / HLS / 网盘，`containerMimeType` 常为 `application/x-mpegURL`，**仅凭编码/分辨率硬猜** WEB-DL/REMUX 是编造事实。第四轮用户要求「参考 JSON 全部入库、全部实现」，因此改为**从文件名/标题里做正则匹配**：参考 JSON 的 100 条 `pattern` 本来就是为文件名设计的（例如 REMUX 牌要求结尾带已知发布组名），文件名字符串本身就是证据，未命中就不产牌。字幕语言要从 `Sub` 取且与"媒体信息"语义不同，仍另议。

## 2. 数据来源：为什么不能来自 TMDB

核查 `TmdbService.java`：TMDB `append_to_response` 只提供 images / credits / translations / release_dates / videos 等元数据，**没有任何码率、编码器、声道、采样率、容器字段**。因此：

- 徽标必须来自播放器运行时事实（`PlayerManager.getVideoFormat()` / `getVideoPlaybackDetails()` / `getAudioPlaybackDiagnostics()` / `getUrl()`）。
- 截图那套图标其实是社区 media-info 图标集（Kodi / Emby / Jellyfin / Kometa overlay 同源），混杂了"发布标签"和"流事实"两类信息，不能整体照搬。

## 3. 图标与商标

- **（第一轮决策，第三轮经用户授权推翻，第四轮完成全量）** 第一轮原定不引入任何第三方图片素材：社区图标集（含 "4K ULTRA HD"、"Dolby Vision" 字标）属于各项目自有或商标方所有，直接打包有许可与商标风险。用户在第三轮明确表示**忽略许可证顾虑**、要求「用主流现成图标」，第四轮进一步要求「参考 JSON 的牌图全部入库」。因此最终打包参考徽标库的 **100 张**，并在第五轮并入扩充包的 **27 张**，共 **127 张 `.webp`**（`app/src/main/res/drawable-nodpi/badge_<group>_<slug>.webp`，合计约 646 KB），运行时零网络请求、零新依赖。
- 牌图来源与清洗：参考 JSON 的每条 `filter` 都带 `imageURL`（`https://cdn.xperience-app.com/badges/kingsize/<group>/<name>.webp`）。入库时用 PIL 按 alpha `getbbox()` 裁掉透明边，再等比缩放到**统一高度 72px**（原图内容高度 44–2158px 不等，不统一就会让同一行高低参差）。
- DOLBY、DTS、IMAX、各家流媒体平台字标等是注册商标 / 认证字标。本实现只在**播放当前媒体确实具备该属性时**显示对应牌（属性来自运行时轨道参数或文件名正则），属对播放内容的客观说明，不做官方认证暗示。
- 视觉分两层：
  - **有对应牌的事实** → 直接显示现成徽标位图（分辨率 / 动态范围 / 画面来源 / 画质 / 视频编码 / 音频编码 / 声道 / 特别标签 / IMAX / 流媒体平台），牌已写明的事实在文本里去重（见 `stripPlate` 等价逻辑：牌非空时文本只留牌没写的部分）。
  - **没有对应牌的事实**（帧率 / 码率 / 容器）→ **只显示 10sp 白色大写文本**，不再使用自绘矢量图标。第四轮删除了 7 个 `ic_badge_*.xml`，因为「显示器 / 齿轮 / 秒表 / 信号条 / 录像带」这类通用图形与具体事实语义不搭边（用户明确指出的问题）。
- chip 胶囊底色：`PlayerOsdController.categoryBackground(Group)` 运行时生成 `GradientDrawable(LEFT_RIGHT, {withAlpha(categoryColor, 0x8A), 0xD9040A12})` + `setCornerRadius(dp(4))` + 1dp 描边 `withAlpha(categoryColor, 0xE6)`；类别色见 `mediaBadgeColor(Group)`（分辨率 `#5A8DEE` / 动态范围 `#7B61FF` / 视频技术 `#22D3EE` / 画面来源 `#0EA5E9` / 画质 `#A855F7` / 版别 `#8B5CF6` / 视频编码 `#14B8A6` / 音频 `#EF4444` / 声道 `#F9731A` / 帧率 `#10B981` / 码率 `#F59E0B` / 特别标签 `#EC4899` / IMAX `#2563EB` / 流媒体 `#DC2626` / 容器 `#64748B`）。参考 JSON 里 10 组的配色其实全同（`tagColor #FF000000` / `borderColor #FFFFFFFF` / `textColor #FFFFFFFF` / `tagStyle bordered`），所以**炫彩类别色是本项目自定的**，不是参考数据给的。
## 4. 三引擎事实覆盖度

| 事实 | ExoPlayer | MPV | IJK |
| --- | --- | --- | --- |
| width/height、frameRate、codecs、bitrate、ColorInfo | ✅ `Format` | ✅ `TrackInfo.toFormat()`（codecs/mime/宽高/帧率/ColorInfo/采样率/声道/平均码率） | ⚠️ `IjkSimplePlayer.buildFormat()` 字段较少 |
| Dolby Vision profile | ✅ `getVideoPlaybackDetails()` | ✅ | ➖ |
| 音频 codec/声道/采样率/码率 | ✅ `getAudioPlaybackDiagnostics().activeTrack()` | ✅（`MpvPlayer`） | ✅ `getAudioPlaybackDiagnostics()` |
| `containerMimeType` | ✅ | ❌ 不设置 | ❌ 不设置 |

因此容器徽标的策略是：**先看 `containerMimeType`，为空或未知时再退回 URL 扩展名白名单**（mkv/mp4/m4v/webm/mov/avi/flv/ts/m2ts/wmv/mpg/mpeg/3gp/ogv）；`m3u8`/`mpd` 不在白名单内，所以 HLS/DASH 永远不会冒出容器徽标，宁缺毋滥。

## 5. 方案对比

1. **不改**：屏显已有时间/网速/分辨率/进度/迷你/标题/诊断，缺"编码-码率-音轨"这一层信息；用户明确要求补，且这是排查"为什么糊/为什么没声音"的第一手线索。
2. **照搬社区徽标方案的整体做法**（打包整套图标集 + 直接用它们的 `pattern` 在**运行时**对媒体流做判定）：可证事实部分可行，但社区方案里绝大部分牌依赖**发布文件名**（REMUX / BLU-RAY / WEB-DL / 压制组 / 平台前缀），运行时轨道参数里根本没有这些字段；硬套会输出编造事实。
3. **窄适配（选中）**：**运行时事实**（分辨率/动态范围/编码/音频/声道/帧率/码率/容器）由播放器轨道参数决定；**文件名类牌**（画面来源/画质/特别标签/IMAX/流媒体平台）只对「播放器已拿到的 URL 文件名 + 标题」做正则匹配，未命中即不产牌；牌图用参考库全量 100 张 + 扩充包 27 张。可证、可测（纯 JVM 单测）、零新依赖、可回滚。

## 6. 实现落点

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/player/MediaBadgeCatalog.java` | 新增。参考 JSON 的 **100 条规则原样内嵌**，第五轮再并入扩充包 **27 条**（共 127 条）：`enum Group {RESOLUTION, DYNAMIC_RANGE, VIDEO_TECH, VIDEO_CODEC, AUDIO, CHANNELS, FRAME_RATE, BITRATE, CONTAINER, MEDIA_SOURCE, QUALITY, EDITION, SPECIAL_TAG, IMAX, STREAMING}`、`record Rule(Group group, String plate, String name, String pattern, boolean runtime)`、`rules()` 返回全部规则、`patterns()` 懒编译并双检缓存 `Pattern[]`。已验证全部 pattern **都能被 Java `Pattern.compile` 接受**（含带零宽字符的 `gq_hc_hdrip`），无需手写等价规则。 |
| `app/src/main/java/com/fongmi/android/tv/player/MediaBadgeResolver.java` | 纯逻辑。`record MediaBadge(String text, Group group, String plate)`；`resolve(Format video, PlayerEngine.VideoPlaybackDetails videoDetails, AudioPlaybackDiagnostics.Track audio, String url, String title)`；运行时组（RESOLUTION / DYNAMIC_RANGE / VIDEO_CODEC / AUDIO / CHANNELS）由轨道参数产出，其余两组来源见下；`displayOrder(Group)` 固定输出顺序；非运行时组**每组最多一枚**，避免把 OSD 塞满。 |
| `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` | `setMediaBadges(player)` 调用点补第 5 参 `source.getTitle()`；签名串改用 `badge.group()`；`createMediaBadge(...)` 只显示牌 + 文本（**删除 `mediaBadgeIcon(...)` 自绘图标分支**）；`categoryBackground(Group)` / `mediaBadgeColor(Group)` 改为 13 个分组（第六轮扩到 15 个）；`plateResource(String)` 改为查 `Map<String,Integer> PLATES`，`plates()` 里 127 行显式 `R.drawable.badge_*` 常量引用（防 release `shrinkResources` 误删）；`createMediaBadge` 的胶囊改为**固定高度 `dp(20)`**，让有牌与纯文本徽标同行等高。 |
| `app/src/test/java/com/fongmi/android/tv/player/MediaBadgeResolverTest.java` | 367 行 / 27 用例：分辨率十档（8K/4K/1440P/1080P/720P/576P/480P/360P/240P/144P）、竖屏按长边分档、动态范围各档（含 12bit）、编码映射（含 VVC）、帧率、码率、音频与声道拆分（含 LPCM）、容器 mime 与扩展名、文件名类牌（REMUX / BluRay / 特别标签 / 流媒体平台 / IMAX / 版别 / HDR Vivid / 3D / 高码率 / ISO）、运行时事实压过文件名、纯 URL 不产牌、`catalogIsConsistent`（127 条、plate 唯一、runtime 标记与分组一致）。 |
| `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java` | 键 `player_osd_media_badges`（默认 false）与 `player_osd_media_badge_position`（默认 3=右下）；`getDisplayChecked()` 8 元素、`putDisplayChecked()` 索引 7；`isOsdEnabled()` 计入徽标。 |
| `app/src/main/java/com/fongmi/android/tv/bean/Backup.java` | 两个新键加入 `APP_PREFS`。 |
| `app/src/main/res/layout/view_player_osd.xml` | `FlexboxLayout @+id/osdMediaBadges`：`flexDirection=row` + `flexWrap=wrap` + `justifyContent=flex_end` + `alignItems=center` → 同一行横排、放不下才换行。 |
| `app/src/main/res/drawable-nodpi/badge_<group>_<slug>.webp` | **127 张**现成徽标牌（合计约 646 KB）。命名规则：`badge_` + 参考 JSON 的 `groupId` + `_` + `imageURL` 的目录与文件名（`-`/`.` 转 `_`），例如 `gr` + `resolution/4k.webp` → `badge_gr_4k.webp`；`gms` + `media-source/blu-ray-3.webp` → `badge_gms_blu_ray_3.webp`。放在 `-nodpi` 保证不随屏幕密度缩放。 |
| `app/src/main/res/drawable/ic_badge_*.xml` | **第四轮删除全部 7 个**（分辨率 / 动态范围 / 视频编码 / 帧率 / 码率 / 音频 / 容器）。原因：通用图形与具体事实语义不搭边，用户点名要求去掉。 |
| 三语 `strings.xml` | `display_media_badges`、`osd_media_badge_position`、`select_media_badge_position` 四角数组；`select_player_osd` **末尾**追加第 8 项。 |
| 两设置页布局 + `SettingPlayerActivity` / `SettingPlayerFragment` | `@+id/osdMediaBadgePosition` 行 + `ChoiceDialog.showSingle` 四角单选。 |
| `app/src/main/res/layout/dialog_display.xml` | `@+id/display_media_badges` 芯片（`@style/DisplayControl`，默认 gone，仅 `Mode.PLAYER_OSD` 显示）。 |
| 两 flavor `app/src/{leanback,mobile}/res/layout/dialog_control.xml` | OSD 芯片行追加 `@+id/displayMediaBadges`（`@style/ControlSheetButton`）。 |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/DisplayDialog.java` | `enum Display.MEDIA_BADGES`、`setDisplay` switch 写 `putDisplayMediaBadges`、`initPlayerOsdView()`/`initPlayerOsdEvent()` 接线。 |
| 两 flavor `app/src/{leanback,mobile}/java/.../ui/dialog/ControlDialog.java` | `displays` 列表末尾追加 `binding.displayMediaBadges`；leanback 另加入 `bindRemoteFocus()` 焦点链。 |

### 6.1 运行时组的具体判定

- **分辨率**：按**长边**分档（竖屏素材不会被短边压成低清档）≥7000 `gr_8k` / ≥3840 `gr_4k` / ≥2560 `gr_1440p` / ≥1920 `gr_1080p` / ≥1280 `gr_720p` / ≥1024 `gr_576p` / ≥854 `gr_480p` / ≥640 `gr_360p` / ≥426 `gr_240p` / ≥256 `gr_144p`；都不满足时输出 `长边X短边` 文本、无牌。
- **动态范围**：Dolby Vision → `gv_dolby_vision` 牌 + `P{profile}` 文本；`COLOR_TRANSFER_ST2084` → `gv_hdr10`；`COLOR_TRANSFER_HLG` → `gv_hlg`；其余 HDR → `gv_hdr`；位深 ≥12 → `gv_12bit`、≥10 → `gv_10bit`（仅在 SDR 路径上补位深牌）；SDR 且 8bit 不产牌。
- **视频编码**：`dvhe`/`dvh1` → `ge_hevc`、`dav1` → `ge_av1`、`dvav`/`dva1` → `ge_avc`、`hev1`/`hvc1`/`hevc`/`h265` → `ge_hevc`、`avc`/`h264` → `ge_avc`、`av01`/`av1` → `ge_av1`；`vvc`/`h266`/`h.266`/`x266` → `ge_vvc`；VP8/VP9/MPEG-4 无对应牌，只输出文本。
- **音频 / 声道**：拆成两枚。音频牌由 `AudioPlaybackDiagnostics.codecLabel(...)` 归一后映射（AAC / FLAC / Opus / DD / DD+ / TrueHD / Atmos 系列 / DTS 系列），牌为空时把编码名拼在文本前（新增 `LPCM` 牌）；声道牌由 `channelLabel(int)` → 2.0 / 5.1 / 6.1 / 7.1，牌已写明则文本只留采样率与码率。**5.1.2 / 5.1.4 / 7.1.4 只能靠文件名兜底命中**：`AudioPlaybackDiagnostics.Track` 只携带声道**个数**，`channelLabel(int)` 无法表达高度声道，而 CHANNELS 又属运行时组，故这三张牌实际仅在 URL/标题里出现该字样时才有机会产出（且运行时组已被占用时不会触发）。
- **帧率 / 码率 / 容器**：保留原逻辑（整数帧率 `60FPS`、小数去尾零 `23.976FPS`；`max(averageBitrate, bitrate)` ≥1e6 → 一位小数 `MBPS` 否则 `KBPS`；容器先 `containerMimeType` 再 URL 扩展名白名单，`m3u8`/`mpd` 不在白名单）。**三类都无牌，只显示文本。**

### 6.2 文件名组的具体判定

候选串 = `path(url)`（去掉 query/fragment 的最后一个路径段）+ `source.getTitle()`（去重）。对候选串依次跑 127 条 `Pattern.find()`：

- 非运行时组（MEDIA_SOURCE / QUALITY / EDITION / SPECIAL_TAG / IMAX / STREAMING / **VIDEO_TECH** / FRAME_RATE）**每组只取第一条命中**，顺序按参考 JSON 的原始顺序，避免同一行堆满同类牌。
- 运行时组（分辨率 / 动态范围 / 视频编码 / 音频 / 声道）如果轨道参数**没有**产出，再用文件名兜底补一枚；已经产出的一律不被覆盖（`runtimeFactsWinOverTheFileName` 用例守住这条）。
- 第五轮新增的两条非运行时组：**VIDEO_TECH**（HDR Vivid / 3D）与 **EDITION**（CC标准收藏 / 港台日美英德法欧韩版 / 箭影 / 碟影 / 全区版），都只认文件名；同组多命中时只保留排在前面的那条（例如 `HDR.Vivid.3D` 只出 HDR Vivid）。
- 已知行为：`gms_remux_1/2/3`、`gms_blu_ray_*`、`gms_web_*` 的 pattern 要求文件名结尾带已知发布组名（如 `-FraMeSToR`），没有发布组的 `...REMUX...mkv` 只会命中 `gq_remux`。这是参考实现的原样行为，未做放宽。

## 7. 开关入口（三个，全部等价）

| 入口 | 载体 | 文案 |
| --- | --- | --- |
| 设置 → 屏显设置（多选） | `select_player_osd` 第 8 项 → `PlayerSettingsRow` 开关 | 「媒体信息」（zh-rCN）/「媒體資訊」（zh-rTW）/「Media info」 |
| 播放控件 → 屏显芯片弹窗（TV/手机） | 两 flavor `ControlDialog` 第 8 个芯片 `displayMediaBadges` | 同上 |
| TMDB 详情页内联控件 → 屏显芯片弹窗 | `DisplayDialog`（`Mode.PLAYER_OSD`）`display_media_badges` | 同上 |

三者都读写同一对键（`player_osd_media_badges` + `player_osd_media_badge_position`），`ControlDialog.toggleDisplaySetting(index)` 与 `DisplayDialog.setDisplay(...)` 都汇入 `PlayerSetting.putDisplayChecked(...)` / `putDisplayMediaBadges(...)`，随后调用 `onDisplayChanged()` / `inlineControlDialogDisplayChanged()`，由 `PlayerOsdController.render()` 下一次轮询生效。

## 8. 已知缺口（有意为之）

- 徽标的两条展示路径：控制栏唤醒时无条件显示（`setMediaBadges` 不再看开关）；控制栏隐藏时由「媒体信息」开关决定（关闭时 `setMediaBadges` 立即隐藏并清空重建签名）。
- 角位内缩按控制栏状态分支：控制栏可见时上两角 `topMargin=64dp` / 下两角 `bottomMargin=80dp`（避开顶栏按钮列与底部进度条）；控制栏隐藏时上两角 `0`（靠布局自带的 14dp padding）、下两角 `22dp`（抬高一行，避开常驻进度 / 码率文本）。
- mobile 播放器在 `showControl()` 里先 `setOsdSuppressed(true)` 隐藏整个 OSD 根，因此徽标走 `renderMediaBadgesOnly()` 这条独立放行路径。
- 徽标与标题/分辨率/时间**允许同角共存**（用户确认）：徽标容器是独立 `FlexboxLayout`，同角重叠时由 `layout_gravity` 决定层级，二者可能相互覆盖——有意的「零侵入」取舍。
- 文件名类牌依赖**播放器拿到的文件名/标题**：网盘直链常是随机串（如 `/d/xxxxx`），此时这类牌一律不出现；这不是缺陷，是「不编造」的边界。
- `gms_*` 来源牌与部分 `gq_*` 画质牌要求发布组名，缺组名的文件名只能命中更粗的画质牌。
- 音频**语言**与字幕语言不产牌：`Sub` 的语言标签与「媒体信息」语义不同，另议。
- MPV/IJK 引擎的 `Format` 不设 `containerMimeType`，其容器徽标只能来自 URL 扩展名白名单，网盘直链可能因此缺容器徽标。
- 同一行徽标**等高靠容器固定高 `dp(20)` 实现**（第五轮）：牌位图按 72px 高统一缩放进 `dp(16)` 高度，纯文本胶囊原先只有一行 10sp 文本，两者会差 4px；现在外层 `LinearLayout` 直接给定高，牌与文本都居中，摆脱「牌撑高、文本塌陷」的参差。
- 高度声道（5.1.2 / 5.1.4 / 7.1.4）**没有运行时来源**：`AudioPlaybackDiagnostics.Track` 只带声道个数，`channelLabel(int)` 的映射上限是 7.1；三张高度声道牌只在文件名/标题里出现该字样时才有机会产出，且刊物组 CHANNELS 若已被运行时轨道占用则不补。要真正识别需扩展 `Track` 或解析 codec 串，属另一任务。
- 本轮的扩充包 27 条里，`gv_8bit` / `gv_12bit` 只在**SDR 路径**上作为位深牌出现（HDR 已有各自的动态范围牌），`gvf_3d` / `gvf_hdrvivid` / 13 张版别牌只认文件名；这些都属于「有证据才显示」，不会凭空产生。

## 9. 验证

- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "*MediaBadgeResolverTest*" --tests "*PlayerDisplaySettingSyncTest*" --tests "*ControlDialogOsdSettingsTest*"`
- `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "*PlayerDisplaySettingSyncTest*" --tests "*MediaBadgeResolverTest*"`
- `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:assembleMobileArm64_v8aDebug`

### 设备实测（emulator-5554，x86_64 + ARM 转译，1920×1080，sdk28，zh-CN）

- 第 8 个芯片：播放控件弹窗内 `resource-id=com.silent.android.webhtv:id/displayMediaBadges`、`text=媒体信息`；点击后 `selected` false→true，prefs 落盘 `<boolean name="player_osd_media_badges" value="true" />`。
- 屏显设置多选摘要：勾选后 `@id/osdText` 显示「媒体信息」，证明 8 项 `select_player_osd` 与 8 元素 `getDisplayChecked()` 长度同步、无索引错位。
- 位置四角（`player_osd_media_badge_position` → `@id/osdMediaBadges` bounds）：`0` 左上 / `1` 右上 / `2` 左下 / `3` 右下（默认），控制栏可见时四角均内缩避开控制栏。
- 控制栏自动隐藏后再 dump，只剩 `@id/osd`，**没有 `@id/osdMediaBadges` 节点** → 这条是**改动前**（开关关闭）的观测；`osd-badges-persist` 之后改为：开关关闭时依旧如此，开关打开时常驻路径同样渲染徽标（见 §9 本轮实测）。
- 全屏下位置 `1`（右上）徽标 `[1644,519][1895,735]`，与进度条不重叠。

### 第五轮：常驻显示 + 控制栏唤醒无条件显示（task_guard `osd-badges-persist`，base HEAD `39e1b4a0d4`）

用户原话：「媒体信息在屏显设置中开启时非控制栏唤醒的时候也显示和其他屏显一样，但是默认关闭，控制栏唤醒的时候不关是否开启了这个屏显都显示」。据此语义拆成两条：开关打开后控制栏隐藏时常驻显示；控制栏唤醒时无条件显示。

- 设备实测（emulator-5554，同一部样片，prefs 直接改写 `player_osd_media_badges` 与 `player_osd_title` 后 `run-as ... cp` 回写）：
  - 开关**打开**、控制栏**隐藏**（全屏常驻路径）：`@id/osd` 与 `@id/osdMediaBadges` 同时在树中，`@id/osdMediaBadges` bounds `[163,444][939,524]`，子节点文本 `44.1KHZ 64KBPS` / `29.94FPS` / `3.1MBPS` / `MP4`（另有 5 枚纯牌位图节点无文本）→ 徽标与标题同属常驻屏显。截图目视 9 枚横排徽标（HD / Dolby VISION·HDR10 / WebDL / AVC / AAC 44.1KHZ 64KBPS / 2.0 / 29.94FPS / 3.1MBPS / MP4）。
  - 开关**关闭**、控制栏**隐藏**：`id/osd` 0 处、`osdMediaBadges` 0 处 → 与其它屏显项一致全部不显示（默认行为不变）。
  - 开关**关闭**、控制栏**唤醒**：`id/osd` 1 处、`osdMediaBadges` 1 处，bounds `[101,183][703,299]`，文本 `44.1KHZ 64KBPS` / `29.94FPS` / `3.1MBPS` → **无需打开开关即显示**，两条需求同时成立。
- 注意 `input tap 960 540` 在 TMDB 详情小窗上会落到右侧信息区的按钮（曾误入「搜索」底部弹窗），唤醒控制栏应点小窗内的视频区域（本次用 `input tap 500 330`）。

### 第六轮：扩充包规则 + 牌图扩到 127 张 + 同行等高（task_guard `osd-badge-expansion`，base HEAD `192b55c2fc`）

用户原话：「扩展包可以加，还要这些徽标高度需要统一一下，建议采用大的」。据此做两件事：并入扩充包的媒体事实规则、把同行徽标统一到原「有牌」那档高度。

- **规则扩充**：`MediaBadgeCatalog` 由 100 条增至 **127 条**，新增 15 组中的 `VIDEO_TECH` 与 `EDITION` 两组；新增条目：`gr_8k`、`ge_vvc`、`ga_lpcm`、`ga_lossless`、`gv_8bit`、`gv_12bit`、`gvf_hdrvivid`、`gvf_3d`、`gfr_hfr`、`gms_iso`、`gms_high_bitrate`、`gc_7_1_4`、`gc_5_1_4`、`gc_5_1_2`、13 张版别牌（`ged_*`）。明确**不并入**扩充包里 506 条番号 / 431 条番号代号 / 400 条发布组 / 各类厂牌与演员类规则（本项目片源给不出这类证据），也不并入字幕语言与配音语言规则（语义另议）。
- **牌图**：`drawable-nodpi/badge_*.webp` 由 100 张增至 **127 张**（实测 661706 字节，约 646 KB）。版别牌没有可用图形资产（源 SVG 只是单个 `<text>` 元素），改用 `C:\Windows\Fonts\msyhbd.ttc` 72px 白字透明底自绘，命名统一按 `Group` 前缀（`ged_` = EDITION、`gvf_` = VIDEO_TECH、`gv_` = DYNAMIC_RANGE）。
- **高度统一**：`createMediaBadge` 的胶囊 `FlexboxLayout.LayoutParams` 由 `WRAP_CONTENT` 高改为固定 `dp(20)`。
- 单测：新增 `expansionRuntimeFactsAreRecognised`、`expansionFileNameFactsAreRecognised`、`expansionAudioFactsComeFromTheFileName` 三例；`catalogIsConsistent` 的总数断言 100 → 127。首跑 1 例失败（`HDR.Vivid.3D` 文件名里 VIDEO_TECH 只出一枚，断言 `gvf_3d` 存在不成立），改为断言 `gvf_3d` 不存在并另用 `3D` 文件名单独验证后全绿。
- 设备实测（emulator-5554）：样片改名为 `Movie.2024.港版.HDR.Vivid.3D.8K.LPCM.7.1.4.WEB-DL.mkv`，唤醒控制栏后 `@id/osdMediaBadges` 下 **11 个胶囊 / 15 张牌位图**，实测 9 个胶囊高度**全部 35px**（改动前纯文本胶囊 30px、有牌 34px）；截图中第一行 `HD` / `HDR` / `HDR Vivid` / `WebDL` / `港版`，第二行 `AVC` / `AAC 44.1KHZ 64KBPS` / `2.0` / `29.94FPS` / `3.1MBPS` / `MP4`。新增的 `港版`（紫色 EDITION）与 `HDR Vivid`（青色 VIDEO_TECH）均正确渲染。`8K` / `LPCM` / `7.1.4` / `3D` 未出现属预期：运行时轨道参数（656×1280 → 720p、AVC、AAC 2.0、29.94FPS、3.1MBPS）优先于文件名。
- 构建：`MediaBadgeResolverTest` 27 例 + `PlayerDisplaySettingSyncTest` + `ControlDialogOsdSettingsTest` 全绿，`compileLeanbackArm64_v8aDebugJavaWithJavac` 通过，`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL（APK 204,566,448 字节）。

### 第三轮：现成徽标牌 + 横排炫彩（task_guard `osd-badge-plates`，base HEAD `7d4bee6ce1`）

用户要求：徽标改「同一行横排 + 炫彩配色 + 用主流现成图标」（并明确授权忽略许可证问题）。

- 布局：`osdMediaBadges` 从竖排一列改回 `row` + `wrap` + `justifyContent=flex_end`。
- 视觉：新增类别色渐变胶囊 + 同类色描边。
- 牌图：当时只打包 18 张。
- 设备实测：`@id/osdMediaBadges` bounds `[119,383][939,423]`（宽 820 高 40，一行），子节点 `656X1280` / AVC 牌 / `29.94FPS` / `3.1MBPS` / `AAC 2.0 44.1KHZ 64KBPS` / `MP4`。

### 第四轮：全量入库 + 消除语义不符图标（task_guard `osd-badge-plates-all`，base HEAD `0dc165a9a2`）

用户原话：「为什么不都入库补都实现，还要你现在的图标和内容都不搭边吧」。据此做三件事：

1. **牌图全量入库**：参考 JSON 100 条 filter 的 100 张 `imageURL` 全部下载、PIL 裁 bbox、统一高 72px，落 `drawable-nodpi/badge_<group>_<slug>.webp`（0 缺失 / 0 重复 / 约 497 KB）；旧 18 张扬弃牌删除。
2. **规则全量实现**：`MediaBadgeCatalog` 内嵌 100 条原始 `pattern`（实测 Java `Pattern.compile` 100/100 通过），`MediaBadgeResolver` 按「运行时事实优先、文件名兜底」合并；非运行时组每组最多一枚。
3. **消除语义不符图标**：删除 `mediaBadgeIcon(Icon)` 与 7 个 `ic_badge_*.xml`，只保留牌 + 文本两种形态；`Icon` 枚举整体替换为 `MediaBadgeCatalog.Group`。

验证：
- `MediaBadgeResolverTest` 20 用例全绿（含 `catalogIsConsistent`、`runtimeFactsWinOverTheFileName`、`streamingPlatformIsRecognisedFromTheReleaseName`、`portraitVideoIsGradedByItsLongSide`）。
- 一次真实崩溃被修：`PLATES = plates()` 里误写成 `PLATES.put(...)`（静态初始化期间 `PLATES` 还是 null）→ `ExceptionInInitializerError`，`PlayerOsdController.<clinit>` 抛 NPE，App 一进 TMDB 详情就崩。改为 `map.put(...)` 后正常。**静态字段初始化方法里绝不能给该字段自身赋值。**
- 设备实测（emulator-5554，播放文件名 `Movie.2024.1080p.WEB-DL.DV.HDR10.DDP5.1.Atmos.HEVC-FraMeSToR.mp4`，小窗内联控件）：`@id/osdMediaBadges` bounds `[163,343][939,423]`，子节点依次 `HD` / `Dolby VISION·HDR10` / `WebDL` / `AVC` / `AAC 44.1KHZ 64KBPS` / `2.0` / `29.94FPS` / `3.1MBPS` / `MP4` —— 9 枚，全部是**参考库的真实牌图**，文件名类（HD / WebDL）与运行时类（AVC / AAC / 2.0 / 帧率 / 码率 / 容器）同时在列；`HD` 对应 `gr_720p`（样片实际 656×1280，按长边判 720p 档）。
- 构建：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL，`adb install -r -t` 成功。

- 既有回归守卫（必须保持通过）：`PlayerDisplaySettingSyncTest`（数组顺序、设置页偏好、四角白色文本、persistentSuppressed 顺序、Backup 键）、`ControlDialogOsdSettingsTest`（两 flavor 芯片 id 与 `getDisplayChecked()`/`putDisplayChecked(checked)`、live 5 项、`onDisplayChanged()` 重启）。
