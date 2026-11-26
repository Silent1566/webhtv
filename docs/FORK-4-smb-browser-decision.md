# FORK-4 SMB 浏览入口（决策文档，未实施）

## 要实现的用户能力

在 App 内直接浏览局域网/NAS 的 SMB 共享（可收藏共享、进入目录、点视频就播），不用先用自己的文件管理器拼 URL。
这是 `klcb2010/webhtv`（60★，全网第二大活跃 fork）实测最有价值的一块。

## 当前项目已有实现（先纠正"整块空白"的旧结论）

| 事实 | 证据 |
|---|---|
| Exo 的 SMB 数据源**已经在出货包里** | `third_party/maven/androidx/media3/media3-datasource/1.11.0-alpha01-fongmi/*-sources.jar` 内含 `androidx/media3/datasource/SmbDataSource.java`；同包 `DefaultDataSource.class` 已引用 `smb`（`classes.jar` ASCII 扫描命中 `smb`/`SMB`） |
| 依赖已在版本目录 | `gradle/libs.versions.toml`：`smbj = "0.14.0"` |
| URI 与凭据契约 | `SmbDataSource.getAuthentication(Uri)`：`smb://user:pass@host[:port]/share/path`，无 userinfo 走 `AuthenticationContext.guest()`，端口默认 445，路径首段是共享名 |
| App 层零命中 | `app/` 树内无 `Smb|smbj`；只有 `PushParserTest.java:85` 提到 `smb://` |
| 加密存储已有先例 | `gitcloud/secure/GitCloudTokenStore.java`：AndroidKeyStore `AES/GCM`（alias `webhtv_git_cloud_token`）+ `SharedPreferences` 存 base64 密文，另有 `local-v1:` 文件密钥回退 |
| 没有引入加密新依赖 | 全仓无 `EncryptedSharedPreferences`/`MasterKey` 使用 |

**结论：缺的不是播放能力，而是浏览/凭据/入口三件事。**

## 三个方案

### A. 不改
竖屏/电视端仍需用户自己输入完整 SMB URL（且历史里会留下带密码的 URL）。产品上没解决 NAS 用户的真实痛点。

### B. 原样搬 klcb2010 的 mod 包（拒绝）
- 它用 `mod/` overlay + `inject_*.py` 注入：`SmbBrowserActivity`(1851) + `SmbClient`(363) + `SmbNetworkScanner`(102) + `SmbPlaybackProxy`(188)，靠 NanoHTTPD 本地代理把 SMB 变成 `http://127.0.0.1`（`SmbPlaybackProxy.createUrl` → `VideoActivity.startSmb`）。
- **拒绝理由 1（安全）**：凭据明文存 `SharedPreferences("smb_credentials")` 的 JSON（username/password/domain），本仓已有 KeyStore 方案，不应退回去。
- **拒绝理由 2（多余）**：本地代理是为了绕开"Exo 不认识 smb"。我们 AAR 里已经有 `SmbDataSource`，Exo 链路直接能播，代理只在 MPV 路径才需要。
- **拒绝理由 3（耦合）**：它依赖自家 14 处注入标记（`isSmbEntryEnabled`、`Func`/`FuncPresenter`、`menu_nav.xml` 等），与我们的设置页/首页按钮体系不同。

### C. 窄适配（推荐）
1. **浏览**：新增独立 `SmbBrowserActivity`（mobile 与 leanback 共用同一实现，TV 用焦点框样式），只做三件事：列出共享 → 列目录 → 点视频起播。收藏共享用现有 `Prefers` 存有序 key 列表。**不实现**删除/复制/壁纸复用（klcb2010 的这半边是它自己反复返工的重灾区，且涉及存储权限）。
2. **凭据**：新增 `SmbCredentialStore`，直接复刻 `GitCloudTokenStore` 的 KeyStore AES-GCM 结构（alias `webhtv_smb_credential`，pref `smb_credentials_v1`），按 `host + share` 存 `{username, password, domain}`。
3. **起播不落密**：历史/DB 里只落 `smb://host/share/path` 的**无凭据形态**；播放前由 `SmbUriResolver` 按 host+share 查凭据再拼 `userinfo`，仅把拼好的 URI 传给 `VideoActivity`，并保证该 URI 不进 logcat/调试日志/推送记录（复用现有 URL 脱敏工具）。
4. MPV 路径：如需支持，再单独评估小范围代理（本决策**不包含**，避免范围膨胀）。

## 推荐与理由

**推荐 C，分期落地，先 P1（浏览 + 播放 + 凭据），P2（收藏）再说，P3（删除/复制/MPV 代理）不做。**
- 复用既有 `SmbDataSource` 与既有加密先例，零新依赖、零代理、零存储权限。
- 相对 klcb2010 省掉 `SmbPlaybackProxy` 与三套存储权限代码，改动面从 ~2500 行降到约 700–900 行。
- 代价：TV 与手机要各写一套布局（这是本仓既有惯例，两个 flavor 本来就分开）。

## 验收标准

- 局域网 NAS：输入 host 与账号后能列出共享与目录；点 `.mkv/.mp4` 起播并能拖动进度（复用 `SmbDataSource` 的 seek）。
- 凭据只以密文存在于 `SharedPreferences`；`adb shell run-as` 读到的 pref 内容不可解；历史表与 logcat 中**不出现**密码。
- 删除/清空设备后再次进入，凭据仍可解（KeyStore 回退路径在无 keystore 的模拟器上也能工作）。
- 失败态：共享不存在、账号错、host 不可达三种情况各有明确提示，不崩溃。

## 最小实施步骤

1. `SmbCredentialStore`（照抄 KeyStore AES-GCM，含 `local-v1:` 回退）+ 单测（加解密往返、密文不含明文）。
2. `SmbUriResolver`（无凭据 URI ↔ 带凭据 URI 转换）+ 单测（含特殊字符用户名、无密码、guest）。
3. `SmbBrowserActivity` + `SmbClient` 的**只读**子集（listShares/listDir），先只做浏览与起播。
4. 入口：mobile 菜单项 + leanback 首页按钮，各一处，默认关闭（开关沿用 `Setting` 既有风格）。
5. 浏览器/日志脱敏：确认 `SmbUriResolver` 产物不进入历史、推送、调试日志。

## 回滚

新增独立类 + 两处入口 + 一个 pref；删除新类并撤掉入口即恢复。无数据库 schema 变更、无依赖变更。

## 风险

- `smbj` 是阻塞 IO，大目录列举可能卡 UI：必须放后台线程并给超时，超时后关闭连接。
- KeyStore 在部分模拟器/老设备不可用：`local-v1:` 回退是**可用性**而非安全等价，需在文档与 UI 里说明。
- 用户可能在 URI 里带密码再从别处粘贴：`SmbUriResolver` 要把 userinfo 抽出来转存到凭据库并**从历史里剥掉**。

## 待你决策

1. 是否按 C 的 **P1 范围**（浏览 + 播放 + 凭据，不含收藏/删除/复制/MPV 代理）实施？
2. 入口默认**关闭**（需用户手动开启）是否可接受？——我推荐默认关闭，避免给所有用户多一个入口。

## 实施记录与实机验证（2026-11-27）

### 本轮两处代码改动（都已单测/构建验证）

1. **主机端口支持**（`SmbUriResolver.endpoint`）：`SmbUriResolver` 本来就在 URI 形态里接受 `host[:port]`，但浏览页把端口写死成 445（`SmbClient.connect(host, 445, …)`、`storageUri(host, 445, …)`）。本轮把"填 `主机:端口`"这条路打通：新增纯函数 `endpoint(String)`，默认 445，非法输入（空主机、空端口、`nas:0`、`nas:70000`、`nas:abc`、`a:1:2`）一律返回 null 并由界面提示 `smb_bad_host`，不猜端口。单测 3 例（默认端口、显式端口、八种非法输入），`SmbUriResolverTest` 共 10/10 通过。
2. **播放不能因为"没勾记住账号"退回 guest**：`play()` 原来只从 `SmbCredentialStore` 取凭据，于是"浏览用表单里的账号、播放却当访客"——浏览能列目录、点播放必然鉴权失败。改为优先用本次连接的表单凭据，库里存的作为兜底（`private SmbCredential credential`）。

### 用本机临时 SMB 服务做的端到端验证

测试服务是 impacket 的 `smbserver.py`（SMB2、带账号、非 445 端口 1445），共享里放 `top.mkv` 与子目录 `sub/sample.mkv`；模拟器（emulator-5556）连宿主机局域网地址 `192.168.50.3:1445`，用户名 `test`。测完已停服务并删除临时目录。

| 步骤 | 结果 | 证据 |
|---|---|---|
| 填 `192.168.50.3:1445` + 共享 + 账号 + 密码 → 连接 | 通过 | 列表显示 `webhtvtest/`、`目录 · sub`、`top.mkv · 2.4 MB` |
| 进子目录 | 通过 | 面包屑 `webhtvtest/sub`，列出 `sample.mkv · 2.4 MB` |
| 点击视频起播 | **播放失败** | 见下 |
| 未勾"记住账号"时也能鉴权打开文件 | 通过 | 失败点在第一次 READ（`SmbDataSource.read`），不是登录/打开——若退回 guest 会在 session setup 就失败 |

**播放失败的真实原因**：`com.hierynomus.smbj.common.SMBRuntimeException: … TransportException: Unable to deserialize SMB2 Packet Data`，即 **smbj 与 impacket 测试服务在 SMB2 报文上不兼容**，第一次 READ 就炸。这不是鉴权失败（文件已打开），也不是我们的数据源接错（栈里明确走到 `androidx.media3.datasource.SmbDataSource.read`）。impacket 的 smbserver 是简化实现，真机 NAS（Samba/Windows）不会走这条路径。

### 仍未验（需要一台真实 NAS 或 Samba）

- **SMB 起播与拖进度**：本轮只验到"列目录 + 鉴权打开文件"，读数据这一步被测试服务的协议实现卡住。最小补验：同一台设备连真实 NAS → 进子目录 → 点视频 → 能起播并拖动进度。
- 收藏/最近使用、删除/复制（P2 范围外，未做）。

### 复现方式（下次换真实服务时照抄）

```
python -m venv <venv> && <venv>/Scripts/python -m pip install impacket
<venv>/Scripts/smbserver.py webhtvtest <shareDir> -smb2support -username test -password <pw> -port 1445
```
把 `<shareDir>` 指向含测试视频的目录，App 里主机填 `<宿主机 IP>:1445`、共享名 `webhtvtest`。445 端口在本机被 Windows 自带 SMB 占用，绑定会报 `WinError 10013`，所以必须用非标准端口——这正是本轮补端口支持的由来。
