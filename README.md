# WebHomeTV

WebHomeTV 是基于 [FongMi](https://github.com/FongMi/TV) / CatVod 生态二次开发的 Android 影音应用,保留原有点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力,并重点增强了 **WebHome 自定义首页**、**App Native SDK**、**管理页面**、**远程托管**、**WebHome 扩展**、**登录态学习/同步**、**网盘链接检测**、**站点健康排序**、**观影记录同步** 和 **Nostr/TMDB 推荐首页**。

项目的核心目标不是替换 CSP/Spider 体系,而是让 CSP 站点首页变成一个真正可开发的网页应用:开发者用 HTML/CSS/JavaScript 定制首页,再通过 App 暴露的 Native 能力完成搜索、播放、跨域请求、资源代理、最近观看、网盘检测和状态同步。

### 增强功能

- **网盘检测**:内置网盘分享链接有效性检测,WebHome 和本地 HTTP API 均可调用。
- **站点健康排序**:自动学习站点搜索、详情和播放成功率,搜索与换源优先使用更可用的站点;站点弹窗默认保留用户配置顺序,可在弹窗内单独开启健康排序。
- **管理页面**:在 App 内启动局域网浏览器管理页 `/m`,可管理本机或远端设备文件、登录态、同步目录、站点注入、接口、壳代理、搜索和推送,运行期间通过前台服务保活。
- **远程托管**:通过自建 Cloudflare/Deno/Vercel/Go/Rust 中转服务绑定多台 WebHTV 设备,支持设备状态、远程搜索/推送、接口配置、主页设置、一键同步和最近日志;Go/Rust 版支持 WebSocket 实时通道,不支持时自动回退 HTTP 轮询。部署说明和二进制见 [远程托管中转服务器文档及二进制](serverless)。
- **一键同步**:在同一局域网设备间同步配置、站源数据(Jar/脚本保存数据)、登录态、WebHome 数据、搜索记录、观看历史、收藏和应用设置,每项可单独勾选。
- **站点注入**:添加自定义 WebHome 或通用 CSP 站点,主列表显示核心摘要和快捷操作,新增/修改在独立表单中维护启用状态、插入位置、首页、搜索和换源行为;顶部“识别”可粘贴单个或多个松散站点 JSON 片段并自动归类追加;WebHome 站点级扩展可直接填写扩展 URL / JSON,也可选择本地 JS/CSS/JSON 自动生成配置。
- **WebHome 扩展**:给真实网页注入用户脚本,主列表显示扩展摘要和状态,新增/修改在独立表单中配置本地文件、远程链接/manifest、直接代码、表单生成或 JSON;匹配范围默认从当前点播配置的 WebHome 站点弹窗多选,也可切换到 CSP key 正则;提供调试工作台用于 Web 预览、Console/Network/Elements 和代码保存预览。
- **观影记录同步**:增强功能中提供独立总览页,包含总开关、本机 API 修改开关、远端同步源和 Webhook 上报。爬虫可通过 `/api/playback/current` 读取当前播放记录,也可在用户开启修改后调用 `/api/playback/progress`、`/api/playback/progress/batch` 或 `/api/playback/progress/delete` 写入/清理本地进度;App 也可从用户配置的远端 API 拉取批量记录合并到本地历史。完整协议见 `webhome-devkit/docs/应用完整开发文档.md` 的“观影记录同步”章节。
- **登录态学习**:用户手动开启后学习 Cookie、Token、接口 Jar 网盘登录文件等登录态路径,待确认项可在管理页查看/编辑,并可参与一键同步。
- **APP 代理**:配置代理地址和域名匹配规则,可按当前站点自动建议代理域名,用于改善特定站点、接口或播放链路的网络访问。
- **调试日志**:本机和局域网日志查看入口,便于排查播放、代理、站源和 WebHome 相关问题。

以上能力集中在设置页的"增强功能"入口,手机端和电视端均为独立设置页。

## 效果演示

https://github.com/user-attachments/assets/7249b787-a720-406c-8365-acaa0995cb6a

演示视频对应的站点配置(Nostr/TMDB 推荐首页):

```json
{
  "key": "Nostr",
  "name": "Nostr推荐",
  "type": 3,
  "api": "csp_Nostr",
  "homePage": "https://www.252035.xyz/xs/tvbox/nostr.html"
}
```

## 免费声明与社区分享

WebHomeTV 是基于开源生态二次开发的技术学习与研究项目,软件本体完全免费,不提供任何付费服务、影视内容、直播源、接口源、资源存储或内容分发能力。

本软件仅供技术学习、研究和个人测试使用,请在下载、安装或试用后 24 小时内自行卸载。继续使用本软件所产生的一切行为及后果,由使用者自行承担。

本软件不内置、不售卖、不传播任何影视资源,不对用户自行添加的接口、站源、插件、脚本、链接、网盘资源或第三方服务内容负责。使用者应遵守所在地法律法规,尊重版权方和内容提供方的合法权益,不得将本软件用于任何侵权、盗版、传播非法内容或其他违法违规用途。

严禁任何个人或组织以本软件名义进行售卖、引流、收费维护、会员服务、广告变现、盒子预装、电视盒子捆绑销售或其他任何形式的获益行为。对于将本软件内置于电视盒子、机顶盒、付费套餐或商业服务中进行销售、推广的行为,项目方明确反对并予以谴责。

社区内容:

- [网友自制分享](https://github.com/fish2018/webhtv/issues/13)

## 文档

完整开发说明见 [**应用完整开发文档.md**](webhome-devkit/docs/应用完整开发文档.md),包含:

- App 配置字段(点播、解析、直播、样式)
- Spider 开发,JS/Python Spider 运行时
- 本地 HTTP 服务端点总览
- WebHome SDK 全部方法的参数和返回值
- 透明背景、电视端遥控器 UX、性能最佳实践
- 网盘检测 API 和站点健康排序
- 观影记录同步、Webhook 上报和爬虫 HTTP API
- 管理页面和局域网 HTTP 能力
- 远程托管部署、绑定流程和能力边界
- WebHome 扩展脚本开发
- 登录态学习与同步
- PanSou 集成、Nostr 首页实现要点
- 隐藏功能和使用技巧
- Android Intent、DLNA、MediaSession
- CORS、Cookie 和网络策略

WebHome 主页、扩展、模板、示例和 AI skills 统一放在 [webhome-devkit/](webhome-devkit/) （附 [独立CNB仓库](https://cnb.cool/fish2018/ext)）：

- 扩展脚本开发指南见 [webhome-devkit/README.md](webhome-devkit/README.md)。
- 扩展示例见 [webhome-devkit/examples/extensions/](webhome-devkit/examples/extensions/)。
- 主页示例见 [webhome-devkit/examples/homepages/](webhome-devkit/examples/homepages/)。
- 模板见 [webhome-devkit/templates/](webhome-devkit/templates/)。
- AI 编程客户端如何接入和复用 Skills,见 [webhome-devkit/skills/](webhome-devkit/skills/)。


## 二开重点

### 1. CSP 站点支持自定义 WebHome 首页

站点配置新增首页字段,切换到该 CSP 站点时直接显示自定义网页:

```json
{
  "key": "webhome",
  "name": "WebHome",
  "type": 3,
  "api": "csp_Xxx",
  "homePage": "./nostr.html"
}
```

兼容字段:`homePage`、`home_page`、`webHome`、`web_home`。

如果配置文件来自在线地址,`./nostr.html` 会按配置文件 URL 做相对路径解析,方便把配置和首页 HTML 放在同一目录。

### 2. WebHome Native SDK

WebHome 页面会注入 `window.fongmi` 和简写 `window.fm`,网页可以直接调用 App 能力:

| 能力 | 说明 |
| --- | --- |
| `fm.req(url, options)` | 使用 App 内置 OkHttp 请求接口,绕过浏览器 CORS 限制 |
| `fm.res(url, options)` | 生成本地资源网关地址(`/webResource`),给图片、视频、字幕等 DOM 资源使用 |
| `fm.play(url, title, options)` | 播放直链或 `push://` 地址,`options` 可带 `pic` 和 `wallPic` |
| `fm.vod(siteKey, vodId, title, pic, options)` | 打开 App 原生 CSP 详情/播放链路,`options.wallPic` 可指定播放页背景图 |
| `fm.vodInline(payload)` | 从 WebHome 传入临时 VOD,支持多集直链或按集即时解析,打开 App 原生播放页 |
| `fm.preloadArtwork(pic, wallPic)` | 后台预热播放页海报和背景图,不阻塞后续播放跳转 |
| `fm.search(keyword, { direct, pic, wallPic })` | 调用 App 搜索,支持直接进入搜索结果,可把详情页图片带入后续播放 |
| `fm.openLive()` / `fm.openKeep()` / `fm.openSetting()` | 打开 App 原生直播、收藏和设置入口 |
| `fm.history()` | 读取最近观看记录 |
| `fm.stat()` | 获取当前播放状态、进度、时长等信息 |
| `fm.ctrl(action)` | 控制播放、暂停、停止、上一集、下一集等 |
| `fm.pan.check(items)` | 调用内置网盘链接有效性检测,`fm.check(items)` 是短别名 |
| `fm.pan.play({ type, url, password, title, pic, wallPic })` | 播放网盘分享、磁力、电驴、thunder 等需要进入 push 链路的地址,可带播放页图片 |
| `fm.config()` | 获取当前配置和网盘检测开关状态 |
| `fm.site()` | 获取当前站点信息 |
| `fm.device()` | 获取设备信息 |
| `fm.cache` | WebHome 可用的本地缓存(get/set/del) |
| `fm.ext` | 扩展脚本辅助能力(info/log/toast) |
| `fm.ui.setToolbar(visible)` | 控制 App 工具栏显示 |
| `fm.back()` / `fm.reload()` | 处理网页返回和刷新 |

播放页图片语义:`pic` 是海报/播放器默认图,`wallPic` 是播放页背景图。App 不会自动判断横竖屏,WebHome 应把竖版海报放在 `pic`,把横屏剧照/背景图放在 `wallPic`;播放背景只使用 `wallPic`,没有 `wallPic` 时显示 App 默认背景/壁纸,不会再用 `pic` 兜底。`fm.play`、`fm.vod`、`fm.vodInline`、`fm.pan.play` 共用这套语义;`fm.search(keyword, { direct: true, pic, wallPic })` 可把详情页图片带到原生搜索结果后续播放链路。详情页拿到图片后可先调用 `fm.preloadArtwork(pic, wallPic)` 预热原生 Glide 缓存,点击继续观看、搜索播放或播放时仍应直接调用对应入口,不要在点击后等待预热。WebHome 如果自己渲染最近观看并支持从 `push_agent` 记录继续播放,应在调用 `fm.pan.play()` 前用 `fm.cache` 按播放 URL 保存 `{ pic, wallPic }`,读取 `fm.history()` 后再恢复这些图片;原生历史通常只可靠保存海报,不能依赖它带回 `wallPic`。

SDK 相关事件:

- `fmsdk`:SDK 注入完成,页面早期脚本应等待该事件后再调用 `fm.*`。
- `fmresume`:App 从后台或锁屏恢复,detail 携带暂停时长,可用于补偿刷新数据。
- `fmurlchange`:History API 路由变化。
- `fmviewport`:WebView 尺寸变化,同时更新 `--fm-web-width/--fm-web-height` CSS 变量。

持久化数据建议优先使用 `fm.cache`,不要把账号、页面配置、同步身份等关键数据只放在 `localStorage`。`localStorage` 仍由 Android WebView 提供并按页面 origin 保存;但 `window.fm` 在页面加载完成后注入,页面早期脚本应等待 `fmsdk` 事件,或在检测到 `window.fongmiBridge` 但 `window.fm` 尚未就绪时短暂等待,避免误写到浏览器预览 fallback。

### 3. CORS 和资源加载增强

普通网页 `fetch()` 受浏览器 CORS 限制。WebHomeTV 提供两种内置能力:

- `fm.req()`:接口请求,返回 JSON、文本、二进制等数据。
- `fm.res()` / `/webResource`:图片、视频、字幕、CSS 背景等资源加载。

可处理常见跨域、Header、Cookie、资源防盗链问题,WebHome 页面不需要用户安装浏览器插件或关闭系统 WebView 安全策略。

### 4. 透明背景 WebHome

App WebView 支持透明背景,WebHome 页面可以让 App 壁纸透出,适合做沉浸式影视首页。要点:`html`/`body`/主容器保持透明,内容层用半透明中性背景,全屏浮层打开时隐藏底层页面。完整建议见开发文档"WebHome 透明背景"。

### 5. WebHome 路由、返回、刷新和恢复

- 使用 History API 管理详情页、搜索页、弹层等路由,App 返回键优先让网页内部回退,再退出 WebHome。
- `fm.reload()` 刷新当前 WebHome,不要求用户重启 App。
- App 从后台或锁屏恢复时派发 `fmresume` 事件,网页可保留页面状态并补偿刷新数据。
- 正常冷启动默认回到 WebHome 主页;详情页、弹层等 UI 快照只建议用于后台恢复或 App 明确带 `_fm_restore=1` 的 WebView 进程恢复场景。

电视端 WebHome 需按遥控器模型单独设计焦点,完整经验见开发文档"电视端遥控器 UX 最佳实践"。

### 6. 内置网盘链接检测和播放

网盘检测开关位于"增强功能"页,默认开启。开启后 WebHome 或自定义工具可调用 App 内置检测能力。

WebHome SDK:

```js
const config = await fm.config();
if (config.driveCheck) {
  const result = await fm.pan.check([
    { type: "aliyun", url: "https://www.aliyundrive.com/s/xxx" },
    { type: "quark", url: "https://pan.quark.cn/s/xxx" }
  ]);
}
```

本地 HTTP API:

```http
POST http://127.0.0.1:{port}/pan/check
Content-Type: application/json

{
  "items": [
    { "type": "quark", "url": "https://pan.quark.cn/s/xxx" }
  ]
}
```

检测接口支持批量提交,内部每批最多 10 条并发检测,超过 10 条自动分批。WebHome 开发时建议只检测用户当前可见范围内、且 App 支持的网盘类型,避免无意义请求和界面跳动。

`fm.pan.play({ type, url, password, title, pic, wallPic })` 是 WebHome 的网盘播放语义入口,内部复用 App 已有的 `push_agent/pvideo` 播放链路。因为底层进入 `SiteApi.PUSH`,磁力、电驴、thunder、jianpian 等地址也可以走这个入口。性能与直接推送 `push://` 基本一致,但语义更清晰,也方便后续 App 内部调整播放策略。`password` 参数保留在 API 形态中,当前播放链路主要依赖 App/JAR/pvideo 自身处理。`pic`/`wallPic` 只影响原生播放页展示图,不参与网盘解析。若后续从 WebHome 自己的“最近观看”再次打开同一个 push 记录,建议按 URL 缓存并恢复 `wallPic`,避免原生历史缺少剧照导致播放页没有背景。

### 6.1 调试日志

调试日志入口在"增强功能"页,默认关闭。开启后,App 记录 WebHome SDK 调用、`fm.req`/资源网关、`pan.check`、`pan.play`、本地 HTTP 服务、爬虫请求、push/pvideo 和播放状态等链路日志。

- 日志保存在当前 App 进程内,最多保留最近 2000 行;关闭开关或进程结束后不保留。
- 关闭调试日志时不弹 toast,并自动清空当前进程内日志。
- 开启后会打开 `/debug/logs` 页面,可刷新、下载、清空,也可通过同局域网地址查看。

### 7. PanSou 网盘搜索集成示例

`webhome-devkit/examples/homepages/nostr.html` 的详情页集成了 PanSou 类搜索能力,支持:

- 自定义盘搜服务地址、账号密码认证、自定义 TG 频道。
- 按网盘类型分 Tab 展示,对支持的类型调用 App 内置检测,只检测可见范围内的结果,检测结果用状态圆点表达。
- 点击资源前缓存 URL 对应的海报/剧照,再调用 `fm.pan.play({ type, url, password, title, pic, wallPic })` 交给 App 播放；这样原生播放页和 WebHome “最近观看”继续播放都能拿到同一组图片。

PanSou 搜索结果可能是异步补充的,示例页会轮询合并新增结果。

### 8. Nostr + TMDB 推荐首页示例

`webhome-devkit/examples/homepages/nostr.html` 是一个完整的 WebHome 首页示例,不只是 SDK demo。它包含:

- TMDB 今日趋势、电影、剧集、动画等榜单,中国大陆内容优先的推荐分区。
- 瀑布流卡片布局,移动端一行 3 个,宽屏自动显示更多列。
- Nostr 去中心化偏好同步;用户搜索、点击、播放时长等行为参与推荐计算,同一用户对同一条目的热度去重。
- 状态面板展示 SDK、TMDB、Nostr、PanSou、发布状态和身份信息。
- 支持清理本机测试数据和发布 Nostr 删除事件。
- 详情页优先使用 TMDB 横屏剧照作为播放页 `wallPic`,状态面板可开启高清剧照以向播放页传 TMDB `original` 图;没有横屏图时只传海报 `pic`,播放页不显示背景图而使用 App 默认背景/壁纸。进入详情后会后台预热原生播放页图片,不阻塞"继续观看"或"搜索播放"跳转。

示例页使用 TMDB API,请自行替换或管理 API Key,并遵守对应服务条款。

### 9. App 行为调整

- 启动 App 不再自动弹出版本更新窗口,用户仍可在设置页手动检查版本。
- 手机端和电视端都保留原有 FongMi/CatVod 能力。
- WebHome 能力优先面向手机端体验,同时兼顾电视遥控器焦点和返回操作。

## WebHome Devkit

| 文件 | 说明 |
| --- | --- |
| `webhome-devkit/docs/应用完整开发文档.md` | App、配置、WebHome SDK、扩展、网盘、管理页和调试能力的完整开发文档 |
| `webhome-devkit/README.md` | WebHome 扩展脚本开发指南 |
| `webhome-devkit/examples/homepages/nostr.html` | 正式推荐首页示例,集成 TMDB、Nostr、PanSou、网盘检测、透明背景 |
| `webhome-devkit/examples/extensions/` | 站点扩展示例 |
| `webhome-devkit/templates/` | WebHome 首页和扩展起步模板 |
| `webhome-devkit/skills/` | AI 编程客户端可安装的 WebHome skills |
| [`serverless/`](serverless/) | 远程托管中转服务器部署文档、Cloudflare/Deno/Vercel 示例和 Go/Rust 二进制 |

配置示例:

```json
{
  "sites": [
    {
      "key": "webhome_demo",
      "name": "WebHome 推荐",
      "type": 3,
      "api": "csp_Demo",
      "homePage": "./nostr.html"
    }
  ]
}
```

配置文件和示例 HTML 放在同一服务器目录时,`homePage` 可直接写相对路径。

## 构建

本节按“新机器 clone 后直接复制命令打包”为目标维护。当前分支使用较新的 Android/Gradle/Media3 组合，环境不满足时最常见的失败点是 JDK、Android SDK 37 和依赖下载网络。

### 环境要求

- JDK 21。不要使用 JDK 17；当前 `sourceCompatibility` / `targetCompatibility` 均为 Java 21。
- Android SDK Platform 37 和 Build Tools 37.0.0。当前 `compileSdk=37`、`minSdk=24`、`targetSdk=28`。
- 使用仓库内置 Gradle Wrapper：Gradle 9.5.1，Android Gradle Plugin 9.2.1。
- 能访问 Maven Central、Google Maven、Gradle Plugin Portal 和 JitPack。仓库内已带定制 Media3、nextlib 和本地 AAR，但普通 Android 依赖仍需要联网下载。

macOS/Linux 可用以下命令确认版本：

```bash
java -version
bash gradlew --version
```

如果依赖下载需要代理，先在当前终端设置：

```bash
export https_proxy=http://127.0.0.1:7897
export http_proxy=http://127.0.0.1:7897
export all_proxy=socks5://127.0.0.1:7897
```

### 从零 clone 到打包

安装或确认 Android SDK 后，确保根目录有 `local.properties`。Android Studio 打开项目时通常会自动生成；命令行构建可手动创建。macOS 默认路径示例：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
```

Linux 常见路径是 `$HOME/Android/Sdk`，Windows 使用 Android Studio 打开项目或创建 `local.properties`，内容类似 `sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk`。

如 SDK 未安装 API 37，可用 Android Studio SDK Manager 安装，或使用命令行工具：

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

clone 当前要构建的分支后直接打 debug 包：

```bash
git clone -b upgrade/media3-1.11-fongmi-20260705 https://github.com/fish2018/webhtv.git
cd webhtv
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug
```

如果构建主线发布分支，把 `-b upgrade/media3-1.11-fongmi-20260705` 换成对应分支，例如 `webhtv-latest-target28` 或 `webhtv-latest-target37`。

已 clone 仓库切换分支：

```bash
git fetch origin
git switch upgrade/media3-1.11-fongmi-20260705
bash gradlew clean
```

### 常用本地打包命令

debug 包适合本地安装测试，构建速度更快：

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug
bash gradlew :app:assembleMobileArmeabi_v7aDebug
bash gradlew :app:assembleLeanbackArm64_v8aDebug
bash gradlew :app:assembleLeanbackArmeabi_v7aDebug
```

release 包适合分发测试或正式发布：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease
bash gradlew :app:assembleMobileArmeabi_v7aRelease
bash gradlew :app:assembleLeanbackArm64_v8aRelease
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease
```

也可以一次打常用三包：手机 64 位、电视 32 位、电视 64 位。

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug
```

### APK 输出路径

debug 原始输出：

```text
app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
app/build/outputs/apk/mobileArmeabi_v7a/debug/app-mobile-armeabi_v7a-debug.apk
app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk
```

release 构建完成后会自动复制到 `Release/apk/`，Gradle 原始输出仍在 `app/build/outputs/apk/<flavor>/release/`：

```text
Release/apk/mobile-arm64_v8a.apk
Release/apk/mobile-armeabi_v7a.apk
Release/apk/leanback-arm64_v8a.apk
Release/apk/leanback-armeabi_v7a.apk
```

安装到已连接设备：

```bash
adb devices
adb install -r app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

### GitHub 手动发布

仓库内置 `.github/workflows/android-release.yml`,只支持在 GitHub Actions 页面手动触发,不会在每次 push 代码时自动打包。默认 tag 会从 `app/build.gradle` 读取当前 `versionName`:稳定版生成 `v<versionName>-yyyyMMddHHmm`;在 `fongmi-sync` 分支选择 `auto` 通道时生成测试版 `v<versionName>-beta-yyyyMMddHHmm`,APK/JSON 文件名同步追加 `-beta`。

工作流会构建 4 个 release APK,生成同名更新清单 JSON,发布到 GitHub Release,并可同步到 CNB 镜像仓库 `apk/` 目录。正式发布前建议在 GitHub Secrets 配置:

```text
RELEASE_KEYSTORE_BASE64  # release keystore 的 base64 内容
RELEASE_KEY_ALIAS        # key alias
RELEASE_STORE_PASSWORD   # store password,key password 复用该值
RELEASE_KEY_PASSWORD     # 可选,key password 与 store password 不同时配置
CNB_TOKEN                # 可选,用于同步 CNB
```

CNB 默认同步到 `https://cnb.cool/fish2018/webhtv.git`,如需修改,在 GitHub 仓库变量中设置 `CNB_REPO_URL`。

GitHub Actions 正式发布必须配置签名 secrets,否则会直接失败,避免使用 runner 临时 debug key 生成无法覆盖安装的 APK。

如果发布时忘记配置 `CNB_TOKEN` 或 CNB 同步失败,不需要重新打包。配置好 `CNB_TOKEN` 后,在 GitHub Actions 手动运行 `CNB Release Sync`,填写已有 `release_tag` 即可把该 GitHub Release 的 APK/JSON 补同步到 CNB；`release_tag` 留空时同步最新 release。

### 签名

默认不需要配置 release 签名文件。未配置签名时，release 包使用 debug signing 兜底，方便 clone 后直接打包测试。

如需正式签名，在根目录的 `local.properties` 增加以下字段；`keyPassword` 可省略，省略时复用 `storePassword`：

```properties
sdk.dir=/path/to/android/sdk
storeFile=/path/to/keystore.jks
keyAlias=your_alias
storePassword=your_password
keyPassword=your_key_password
```

### 播放层依赖

- `app/libs/*.aar`:内置 Hook、TVBus、Thunder、ForceTech、JianPian 播放能力依赖。
- `third_party/maven`:已生成的 `androidx.media3:*:1.11.0-alpha01-fongmi` 本地 Maven 产物，以及定制 `nextlib-media3ext`。
- `third_party/media-lock.json`:记录 Media3 锁定版本,升级 Media3 时使用(配套脚本 `scripts/build_media_deps.sh`)。
- `nextlib-media3ext`:`io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1-fongmi-softload`,提供 FFmpeg renderer。

`settings.gradle` 中的依赖顺序是仓库本地 `third_party/maven`、Maven Central、Google Maven、`app/libs` 和 JitPack。`app/build.gradle` 会强制所有 `androidx.media3` 依赖使用 `1.11.0-alpha01-fongmi`，避免传递依赖拉回官方版本。

### 常见构建失败

- `Unsupported class file major version`、`invalid source release: 21`：当前终端没有使用 JDK 21。
- `SDK location not found`：缺少 `local.properties`，或 `sdk.dir` 指向错误。
- `failed to find target with hash string 'android-37'`：未安装 Android SDK Platform 37。
- `Could not resolve ...`：依赖下载失败，检查网络或设置代理后重新执行 Gradle。
- `Permission denied: ./gradlew`：本仓库文档统一使用 `bash gradlew`，不依赖可执行位。

## 目录结构

```text
app/          Android 主应用(mobile/leanback 双 flavor)
catvod/       CatVod 抽象层、Spider 接口、网络和代理工具
quickjs/      JavaScript Spider 运行时
chaquo/       Python Spider 运行时
webhome-devkit/ WebHome 开发套件(文档、主页/扩展示例、模板、AI skills)
scripts/      Media3 本地依赖构建脚本
third_party/  Media3 本地 Maven 产物和版本锁定文件
Release/      release 构建的 APK 输出
other/        Logo 图片和辅助工具
```

## 开源说明

本仓库只提供技术实现和开发示例,不内置、不维护、不分发任何影视内容、播放源、资源站接口或网盘资源。项目中的搜索、播放、网盘检测、TMDB、Nostr、PanSou 等能力都需要用户自行配置合法服务和数据来源。

### 上游基线

本项目二开起始于[原版影视](https://github.com/FongMi/TV) commit `bec0f1d2fc22f394ba05f8e63a9ef2ba7ecbba0e`,当前已同步合并到[原版影视](https://github.com/FongMi/TV) commit `5fdff00a602dc56e8ba756174daef20edab024f2`。

### 友情链接
[![Linux.do](https://img.shields.io/badge/-Linux.do-1c1c1e?style=flat-square&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEuMiIgYmFzZVByb2ZpbGU9InRpbnktcHMiIHdpZHRoPSIxMjgiIGhlaWdodD0iMTI4IiB2aWV3Qm94PSIwIDAgMTIwIDEyMCI+CiAgPGNsaXBQYXRoIGlkPSJhIj4KICAgIDxjaXJjbGUgY3g9IjYwIiBjeT0iNjAiIHI9IjQ3Ii8+CiAgPC9jbGlwUGF0aD4KICA8Y2lyY2xlIGZpbGw9IiNmMGYwZjAiIGN4PSI2MCIgY3k9IjYwIiByPSI1MCIvPgogIDxyZWN0IGZpbGw9IiMxYzFjMWUiIGNsaXAtcGF0aD0idXJsKCNhKSIgeD0iMTAiIHk9IjEwIiB3aWR0aD0iMTAwIiBoZWlnaHQ9IjMwIi8+CiAgPHJlY3QgZmlsbD0iI2YwZjBmMCIgY2xpcC1wYXRoPSJ1cmwoI2EpIiB4PSIxMCIgeT0iNDAiIHdpZHRoPSIxMDAiIGhlaWdodD0iNDAiLz4KICA8cmVjdCBmaWxsPSIjZmZiMDAzIiBjbGlwLXBhdGg9InVybCgjYSkiIHg9IjEwIiB5PSI4MCIgd2lkdGg9IjEwMCIgaGVpZ2h0PSIzMCIvPgo8L3N2Zz4K)](https://linux.do/)
