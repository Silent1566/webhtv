# Git 云盘功能设计方案

日期：2026-06-16

状态：评审增强版。本文档用于直接指导后续实现。

## 背景

App 用户经常需要保存、上传、下载和分享 `js`、`py`、`jar`、`go`、`json`、配置备份、WebHome 扩展、本地数据等文件。传统“网盘”需要额外账号、分享链稳定性和直链能力不可控，而 GitHub、CNB、GitLab、Gitea/Forgejo 等 Git 仓库天然具备版本管理、raw 链接、私有仓库、跨设备同步和长期可追溯等能力。

本功能建议定位为“Git 云盘”，不是完整开发者 Git 客户端。核心目标是让普通用户把 Git 仓库当作文件网盘和 App 数据备份空间使用。

## 评审结论

方案方向成立，但需要修正一个关键点：JGit 很适合作为“Git 云盘”的通用同步底座，但不应该成为所有操作的唯一入口。最佳实践是“平台 API + JGit”双通道：

- 平台 API 负责轻量操作：登录校验、列仓库、建仓库、目录浏览、读取文件、复制 raw、release asset。
- JGit 负责通用写入和同步：clone、pull、add、commit、push、跨平台一致的目录同步。

这样可以同时满足普通用户的“网盘”体验和 GitHub/CNB/GitLab/Gitea 等平台的差异化能力。

第一版应避免把 Git 概念暴露给普通用户。UI 只展示“上传、下载、备份、恢复、复制链接、同步”，内部再映射到 Git 操作。

## 产品定位

### 用户目标

- 上传常用文件：`js`、`py`、`jar`、`go`、`json`、`zip`、图片、接口配置。
- 复制 raw 链接，用于 CSP、WebHome、扩展脚本、配置订阅等场景。
- 多设备备份和恢复 App 数据。
- 管理多个仓库，区分公开资源库和私有备份库。
- 能看到历史版本，必要时回滚。

### 非目标

- 第一版不做完整 Git 客户端。
- 第一版不追求复杂分支管理、rebase、merge、stash、diff review。
- 第一版不建议处理大体积媒体库，例如影视文件、海量图片、超大二进制目录。
- 第一版不做任意目录实时双向同步，避免误删、冲突和仓库膨胀。

## 用户视角评估

### 小白用户关心的问题

- 我应该选哪个平台：默认推荐 GitHub，国内访问和已有 CNB 用户可选 CNB。
- 我应该创建公开还是私有仓库：备份数据默认私有，分享脚本/配置默认公开。
- 我上传后能不能直接用：公开仓库文件可以复制 raw 链接直接用，私有仓库不建议作为公开配置直链。
- 我不懂 Git 能不能用：可以，UI 不出现 commit、branch、push 等术语，除非进入高级模式。
- 我换设备怎么恢复：登录同一 Git 账号，选择私有备份仓库，选择备份包恢复。

### 用户主流程

1. 打开“Git 云盘”。
2. 选择平台，填写 token。
3. App 校验 token，并提示权限是否足够。
4. 用户选择已有仓库，或创建仓库。
5. 如果用途是备份，默认创建私有仓库。
6. 上传文件、复制 raw 链接，或执行一键备份。
7. 换设备时选择仓库和备份文件恢复。

### 登录方式

只支持 token 登录。第一版不支持 OAuth、账号密码、短信验证码、扫码登录、SSH key。

用户侧只需要填写：

- 平台：GitHub、CNB、GitLab、Gitea/Forgejo、Gitee 等。
- 服务地址：仅自建 GitLab/Gitea/Forgejo 需要填写。
- Token：Personal Access Token、Access Token 或 App Password。
- 账号备注名：可选，用于本地多账号区分。

用户不需要填写 Git 用户名和密码。JGit 执行 HTTPS 认证时，由各 provider 自动生成内部凭据：

- GitHub：用户名可使用账号名或 `x-access-token`，密码使用 token。
- CNB：用户名固定 `cnb`，密码使用 Access Token。
- GitLab/Gitea/Forgejo/Gitee：用户名使用账号名或平台推荐占位值，密码使用 token。

UI 必须在 token 输入框旁提供平台超链接：

- 获取 Token。
- 权限说明。
- 平台主页。

Token 页面应使用外部浏览器打开，不在 App 内 WebView 承载登录页面，避免用户误解 App 会接管账号登录。

### 需要避免的体验问题

- 不要让用户手动填写 clone URL，优先通过仓库选择器生成。
- 不要要求用户理解默认分支，默认使用平台返回的 default branch。
- 不要在普通操作中要求填写 commit message，自动生成即可。
- 不要把私有 raw 链接伪装成可公开访问链接。
- 不要在 TV 端提供复杂代码编辑器。

## 支持平台

第一优先级：

- GitHub：必须支持，作为完整能力和验收基准。
- CNB：必须支持基础能力，符合当前用户使用场景。

第二优先级：

- GitLab.com 和自建 GitLab。
- Gitea / Forgejo。

第三优先级：

- Gitee。
- Bitbucket。

### 平台链接配置

UI 中每个平台都应展示“获取 Token”和“帮助文档”入口。第一版内置以下链接：

| 平台 | 平台主页 | 获取 Token | 帮助文档 |
| --- | --- | --- | --- |
| GitHub | https://github.com | https://github.com/settings/personal-access-tokens | https://docs.github.com/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens |
| CNB | https://cnb.cool | https://cnb.cool | https://docs.cnb.cool/zh/guide/git-access.html |
| GitLab.com | https://gitlab.com | https://gitlab.com/-/user_settings/personal_access_tokens | https://docs.gitlab.com/user/profile/personal_access_tokens/ |
| Gitea | 用户填写服务地址 | `{baseUrl}/user/settings/applications` | https://docs.gitea.com/usage/profile-readme#applications |
| Forgejo | 用户填写服务地址 | `{baseUrl}/user/settings/applications` | https://forgejo.org/docs/latest/user/settings/applications/ |
| Gitee | https://gitee.com | https://gitee.com/profile/personal_access_tokens | https://gitee.com/help/articles/4191 |
| Bitbucket | https://bitbucket.org | https://bitbucket.org/account/settings/app-passwords/ | https://support.atlassian.com/bitbucket-cloud/docs/app-passwords/ |

CNB 如果后续确认存在稳定的 token 创建直达页，应替换当前平台主页链接。没有稳定直达页时，按钮文案使用“打开 CNB”，旁边提供 Git 访问文档。

## 仓库策略

### 公开资源仓库

适合保存可公开访问的文件：

- WebHome 主页 HTML。
- WebHome 扩展 JS/CSS。
- 公开 CSP 配置。
- 可公开分享的脚本和 JSON。

公开仓库的优势是 raw 链接无需 token，适合直接被 App、WebView、播放器或其他设备读取。

### 私有备份仓库

备份 App 数据时建议使用私有仓库。用户创建仓库时提供“公开 / 私有”选项，并在备份流程中默认推荐私有。

私有仓库适合保存：

- App 接口配置。
- 登录状态归档。
- 搜索历史、收藏、播放记录。
- WebHome 本地状态和用户扩展配置。
- 其他带有隐私或账号痕迹的数据。

UI 文案建议明确提示：备份数据可能包含个人配置或登录状态，推荐保存到私有仓库。

### 推荐仓库模板

创建仓库时提供用途模板：

- App 备份仓库：默认私有，默认路径 `/apps/webhtv/backups/`。
- 公开资源仓库：默认公开，默认路径 `/apps/webhtv/public/`。
- WebHome 扩展仓库：默认公开，默认路径 `/apps/webhtv/extensions/`。
- 混合仓库：由用户自行选择路径和可见性。

备份仓库不要和公开 raw 资源混用，避免用户误把隐私数据提交到公开仓库。

## 技术路线

### 推荐总体路线

采用“双通道”架构：

1. 平台 API 通道：用于账号、仓库列表、创建仓库、浏览文件、复制 raw、release asset 上传下载等轻量操作。
2. JGit 通道：用于把仓库当作同步目录，完成 clone、pull、add、commit、push、冲突处理和跨平台统一写入。

这样既能保证 GitHub/CNB 等平台的通用性，又能避免只依赖某个平台的私有 API。

### 操作路由规则

| 场景 | 首选实现 | 备用实现 | 原因 |
| --- | --- | --- | --- |
| token 校验 | 平台 API | 无 | JGit 无法提供完整账号信息 |
| 列仓库/建仓库 | 平台 API | 无 | Git 协议不负责平台资源管理 |
| 浏览目录 | 平台 API | JGit 本地 worktree | API 更快，不需要 clone |
| 复制公开 raw | 平台 raw 规则 | 本地规则转换 | 不需要 clone |
| 上传小文本 | 平台 Contents/File API | JGit | GitHub/GitLab/Gitea 体验更轻 |
| 上传普通文件 | JGit | 平台 Contents API | 保持跨平台一致 |
| 上传大 zip/jar | release/asset | JGit | 避免污染 Git 历史 |
| App 备份 | JGit 或 release asset | 平台 asset | 备份可追踪，也可避免大文件入库 |
| App 恢复 | JGit pull 或 asset 下载 | archive 下载 | 取决于备份保存方式 |
| 删除/重命名 | 第二阶段再做 | JGit | 需要冲突和误删保护 |

## JGit 评估

### 适合使用 JGit 的原因

- 纯 Java 实现，不需要 NDK、JNI 或内置 git 二进制。
- Android 可集成，适合在后台线程或前台服务中执行。
- 支持标准 Git HTTPS 认证，GitHub、CNB、GitLab、Gitea/Forgejo 理论上都可用。
- 能实现“本地目录映射到远端仓库”的网盘式体验。
- 能解决 CNB OpenAPI 未明确提供仓库文件写入接口的问题。

### 需要控制的风险

- APK 体积会增加，预计压缩后增加数 MB，需要实测。
- Git 不适合大量大文件，仓库历史会膨胀。
- 冲突处理复杂，普通用户不适合面对 Git 概念。
- clone/pull/push 是长耗时操作，必须放后台线程、前台服务或任务队列。
- Android 存储路径、权限和缓存清理需要严格控制。

### 建议使用方式

JGit 不作为所有操作的唯一入口，而是用于同步型能力：

- 首次绑定仓库：clone 到 App 私有工作目录。
- 上传/更新文件：复制到工作目录，add，commit，push。
- 备份数据：生成 zip，写入工作目录，commit，push。
- 恢复数据：pull 后从工作目录选择备份 zip 恢复。
- 多设备同步：pull，再检查本地和远端差异。

平台 API 继续负责：

- 快速列仓库。
- 创建仓库。
- 获取仓库基础信息。
- 生成 raw 链接。
- 下载 archive。
- release asset 上传下载。

### Android 集成注意事项

- JGit 依赖体积需要实测，不能只引用理论值；实现时应记录引入前后的 APK/AAB 体积。
- JGit 相关操作必须在后台任务中执行，禁止在 UI 线程执行。
- clone、pull、push 应显示可取消进度，并在取消后清理临时目录。
- 首次 clone 不建议对未知大仓库自动执行，必须先展示仓库大小或给出风险提示。
- shallow clone、sparse checkout 在 Android/JGit 上需要单独验证，不作为第一版强依赖能力。
- 如果使用 WorkManager，需要新增依赖并评估现有工程影响；第一版也可以先用现有线程池、Service 或对话框任务队列实现手动同步。
- JGit 可能引入日志、SSH、加密相关依赖和 R8 规则，第一版只支持 HTTPS + token，不支持 SSH。

## 通用 Provider 设计

建议新增 `gitcloud` 相关包，提供统一模型和能力声明。

```java
interface GitCloudProvider {
    GitProviderType type();
    ProviderCapabilities capabilities();
    AccountInfo validateToken(GitAccount account);
    List<GitRepo> listRepos(GitAccount account);
    GitRepo createRepo(GitAccount account, CreateRepoRequest request);
    List<GitBranch> listBranches(GitAccount account, GitRepo repo);
    List<GitFile> listFiles(GitAccount account, GitRepo repo, String ref, String path);
    GitFileContent readFile(GitAccount account, GitRepo repo, String ref, String path);
    SaveResult saveSmallFile(GitAccount account, GitRepo repo, String branch, String path, byte[] data, SaveOptions options);
    String rawUrl(GitRepo repo, String ref, String path);
    DownloadRef archiveUrl(GitAccount account, GitRepo repo, String ref, String path);
}
```

JGit 独立为同步引擎：

```java
interface GitDriveEngine {
    GitDriveState bind(GitDriveConfig config);
    GitDriveState pull(GitDriveConfig config);
    CommitResult commitAndPush(GitDriveConfig config, List<FileChange> changes);
    GitDriveState status(GitDriveConfig config);
    void cancel(String taskId);
}
```

### 核心模型

建议至少定义以下模型，避免 UI 和 provider 互相耦合：

- `GitAccount`：平台、服务地址、用户名、token 引用 key、创建时间、最后校验时间。
- `GitRepo`：owner、name、fullName、cloneUrl、webUrl、defaultBranch、privateRepo、providerType。
- `GitFile`：path、name、directory、size、sha、downloadUrl、rawUrl。
- `GitDriveConfig`：accountId、repoId、branch、worktreeDir、purpose、defaultRemotePath。
- `GitTask`：任务类型、状态、进度、错误、可取消标记。
- `ProviderCapabilities`：是否支持私有仓库创建、contents 写入、release asset、archive、raw、分页。

## Provider 能力差异

### GitHub

必须完整支持：

- Token 校验。
- 列出用户仓库。
- 创建公开/私有仓库。
- 浏览目录。
- 读取文件。
- 复制 raw 链接。
- 通过 Contents API 编辑小文本文件。
- 通过 JGit 同步目录。
- release asset 上传下载。

推荐 token：

- Fine-grained PAT。
- 只读场景：Contents read。
- 写入和备份：Contents read/write。
- 私有仓库：授予目标私有仓库访问权限。

实现细节：

- GitHub API 使用 `Authorization: Bearer <token>`。
- fine-grained token 需要授权目标仓库，否则即使 token 有效也看不到仓库。
- 创建私有仓库失败时，要区分权限不足、组织策略限制、仓库名冲突。
- 小文本文件可优先使用 Contents API，更新时必须带旧 `sha`，防止覆盖远端修改。
- 私有仓库 raw 链接不能作为普通公开链接使用。App 内可通过带 token 的下载逻辑读取，但复制给外部使用前必须提示限制。

### CNB

必须支持：

- Token 校验。
- 列仓库。
- 创建公开/私有仓库。
- 浏览目录。
- raw 链接。
- archive 下载。
- 通过 JGit 完成写入、同步和备份。

CNB HTTPS Git 认证：

- 用户名固定为 `cnb`。
- 密码为 Access Token。
- 仓库地址使用 `https://cnb.cool/空间/仓库.git` 或兼容 URL。

实现细节：

- CNB 平台 API 用于列仓库、建仓库、raw、archive。
- CNB 仓库文件写入优先走 JGit，避免依赖尚不明确的 contents write API。
- CNB 创建私有仓库时要确认 token 是否有空间资源写权限。

### GitLab / Gitea / Forgejo

建议支持：

- 自定义服务地址。
- Token 校验。
- 仓库列表。
- 创建仓库。
- 浏览文件。
- raw 链接。
- JGit 同步。

平台 API 不完整时，写入统一走 JGit。

## 与当前代码的落点

建议新增包：

```text
app/src/main/java/com/fongmi/android/tv/gitcloud/
app/src/main/java/com/fongmi/android/tv/gitcloud/provider/
app/src/main/java/com/fongmi/android/tv/gitcloud/drive/
app/src/main/java/com/fongmi/android/tv/gitcloud/secure/
```

建议新增 UI：

```text
app/src/main/java/com/fongmi/android/tv/ui/dialog/GitCloudDialog.java
app/src/mobile/res/layout/dialog_git_cloud.xml
app/src/leanback/res/layout/dialog_git_cloud.xml
```

可复用现有能力：

- 增强功能入口：`SettingEnhanceFragment` 和 `SettingEnhanceActivity`。
- 备份归档：`SyncFiles`。
- 登录状态归档：`LoginStateSync`。
- 管理页文件/配置接口：`Manage`。
- raw 链接规则：`WebHomeRawAdapter`，后续可抽出 `GitRawUrlResolver`。

## 本地存储设计

### 工作目录

建议放在 App 私有目录，避免污染用户文件区：

```text
files/git-drive/{accountId}/{repoId}/worktree
files/git-drive/{accountId}/{repoId}/tmp
files/git-drive/{accountId}/{repoId}/backup
```

用户选择本地文件上传时，先复制到临时目录，再写入 worktree。

工作目录要求：

- 每个仓库独立 worktree。
- clone 失败不能污染已有可用 worktree，应使用临时目录完成后再切换。
- 提供“清理本地缓存”操作，只删除 worktree，不删除远端仓库。
- 本地 worktree 不纳入 App 备份，避免递归备份 `.git`。

### 映射规则

提供预设目录：

- `/apps/webhtv/configs/`
- `/apps/webhtv/backups/`
- `/apps/webhtv/webhome/`
- `/apps/webhtv/extensions/`
- `/apps/webhtv/scripts/`
- `/apps/webhtv/jars/`

普通用户不需要理解 Git 路径，UI 用“配置备份”“脚本”“扩展”“Jar 包”等分类展示。

默认 `.gitignore` 建议：

```gitignore
.DS_Store
*.tmp
*.log
cache/
tmp/
```

不要默认忽略 `zip`、`jar`，因为用户明确有上传这些文件的需求；但上传时要做大小提示。

## 备份设计

复用现有能力：

- `SyncFiles`：归档 `TV`、`TVBox`、`TVData`。
- `LoginStateSync`：归档登录状态。
- 管理页已有配置和 CSP 能力可继续复用。

备份文件命名建议：

```text
webhtv-backup-20260616-213000.zip
webhtv-login-state-20260616-213000.zip
webhtv-configs-20260616-213000.json
```

建议同时生成 manifest：

```json
{
  "app": "WebHTV",
  "version": 1,
  "createdAt": "2026-06-16T21:30:00+08:00",
  "device": "Android",
  "items": ["configs", "loginState", "webHome", "settings"],
  "archive": "webhtv-backup-20260616-213000.zip"
}
```

manifest 用于恢复前展示备份内容，也便于后续版本兼容。

备份流程：

1. 用户选择仓库，默认推荐私有仓库。
2. 选择备份内容。
3. App 生成 zip/json。
4. 写入 Git worktree 的 `/apps/webhtv/backups/`。
5. 自动 commit：`backup: WebHTV data 2026-06-16 21:30`
6. push 到远端。

备份隐私规则：

- token、Git 账号信息、远端仓库凭据不得进入备份。
- 备份登录状态前必须提示可能包含 cookie、localStorage 或站点登录痕迹。
- 恢复前必须展示备份来源、时间、内容类型。
- zip 恢复必须继续沿用现有路径安全校验，禁止 `../` 路径穿越。

恢复流程：

1. 选择账号和仓库。
2. pull 最新状态。
3. 展示备份列表。
4. 用户选择备份文件。
5. App 校验 zip 内容和路径安全。
6. 调用现有 restore 能力恢复。

## Raw 链接设计

当前项目已有 `WebHomeRawAdapter`，已支持 GitHub、CNB、GitLab、Gitee、Bitbucket、Gitea-like raw 链接适配。新功能应复用或抽取其中的 raw URL 规则，避免重复实现。

复制 raw 链接时：

- 公开仓库：直接复制公开 raw URL。
- 私有仓库：提示 raw URL 可能需要 token，不适合公开配置引用。
- GitHub 私有 raw 不建议直接作为外部配置链接，除非 App 自己带 token 拉取。

建议增加两种复制模式：

- 公开 raw 链接：只在公开仓库或公开文件可用时显示为主按钮。
- App 授权链接：仅 App 内部使用，实际通过 provider API/token 拉取，不把 token 拼进 URL。

禁止生成包含 token 的 URL，例如 `https://token@host/path` 或 query token。

## UI 设计

入口名称建议：`Git 云盘`。

放置位置：

- 增强功能页面。
- 与“一键同步”“管理页面”“WebHome 扩展”同级。

### 手机端

结构：

- 顶部：账号、平台、仓库、分支。
- 主体：文件列表。
- 底部：上传、备份、恢复、复制 raw、更多。
- 详情页：预览、下载、编辑、复制 raw、删除。

创建仓库弹窗：

- 仓库名。
- 描述。
- 可见性：私有 / 公开。
- 用途：备份数据 / 公开资源。

当用途选择“备份数据”时，默认选中私有仓库。

### 引导和空状态

首次进入展示三项操作：

- 连接 GitHub。
- 连接 CNB。
- 使用自建 Git 服务。

连接后如果没有仓库，展示两个创建按钮：

- 创建私有备份仓库。
- 创建公开资源仓库。

仓库为空时，展示快捷入口：

- 上传文件。
- 一键备份。
- 新建目录。

错误提示要翻译成用户能理解的语言：

- `401`：token 无效或已过期。
- `403`：token 权限不足。
- `404`：仓库不存在，或 token 没有访问该私有仓库。
- push rejected：远端有新内容，请先同步。

### TV 端

结构：

- 左列：账号和仓库。
- 中列：目录和文件。
- 右列：文件详情和操作。

TV 端第一版不强调代码编辑，只提供：

- 浏览。
- 上传。
- 下载。
- 复制 raw。
- 备份。
- 恢复。

## 同步模式

第一版建议只做手动同步：

- 拉取。
- 上传并推送。
- 备份并推送。
- 恢复前拉取。

第一版不做后台自动双向同步。原因：

- Android 后台限制复杂。
- 用户数据和远端同时修改时容易产生冲突。
- 普通用户难以理解自动删除和自动覆盖。

后续自动同步只建议用于备份追加，不建议默认双向覆盖。

后续再增加：

- App 启动后静默检查。
- 定时备份。
- Wi-Fi 下自动同步。
- 仅充电时同步。

## 冲突策略

普通用户不应该看到复杂 Git 冲突。

第一版策略：

- push 前先 pull。
- 如果自动合并成功，继续 commit/push。
- 如果冲突，停止并提示：
- 保留本机版本。
- 使用远端版本。
- 另存为新文件。

备份文件通常使用时间戳命名，冲突概率较低。

禁止第一版静默覆盖远端同名文件。上传同名文件时默认追加时间戳或要求用户确认覆盖。

## 文件大小策略

Git 仓库适合小文件和中等配置包，不适合大媒体。

建议默认限制：

- 文本编辑：10 MB 以下。
- 普通 Git 提交上传：50 MB 以下提示。
- 超过 50 MB 的 jar/zip 优先建议 release asset。
- 超过 100 MB 强提示不建议提交到 Git 历史。
- 目录上传超过 200 个文件时，建议打包为 zip 或取消。

后续如果要支持更大文件，应优先考虑 release asset，而不是直接提交到 Git 历史。

上传策略：

- `js/css/json/py/go/html/txt/md`：优先提交到仓库树，便于 raw 使用。
- `jar/zip/db/图片包`：默认询问“提交到仓库”还是“作为附件上传”。
- App 备份 zip：默认放 release asset 或备份目录，具体由用户选择“可追踪历史”还是“避免仓库膨胀”。

## 安全设计

### Token 存储

Token 不能放普通 `Prefers`。

建议使用：

- Android Keystore。
- 或 EncryptedSharedPreferences。

要求：

- token 不进入备份包。
- token 不输出日志。
- token 不出现在崩溃报告。
- UI 只显示掩码。
- 支持一键清除账号。

如果工程暂不引入 `EncryptedSharedPreferences`，需要实现基于 Android Keystore 的最小安全存储封装，不能退回普通 `Prefers`。

### 权限提示

GitHub：

- 公开资源：Contents read/write。
- 私有备份：目标私有仓库 Contents read/write。
- Release asset：需要 release 相关权限。

CNB：

- 读取仓库：repo 读权限。
- 创建仓库：空间/仓库创建权限。
- push：仓库写权限。

## 错误恢复和可观测性

每个 Git 云盘任务都要有状态：

- 等待中。
- 执行中。
- 已完成。
- 已取消。
- 失败，可重试。

失败时记录脱敏日志：

- provider。
- repo full name。
- 操作类型。
- HTTP 状态码或 Git 错误类型。
- 不记录 token、Authorization header、完整私有 raw URL。

用户可复制诊断信息，但诊断信息必须脱敏。

## 实施计划

### 第 1 阶段：GitHub MVP

- 新增 Git 云盘入口。
- 新增安全 token 存储。
- GitHub token 校验。
- GitHub 仓库列表。
- GitHub 创建仓库，支持公开/私有。
- 文件浏览。
- raw 链接复制。
- JGit clone/pull/push 基础验证。
- 上传小文本文件，优先 Contents API。
- 上传普通文件并通过 JGit commit/push。
- 备份 App 数据到私有仓库。
- 从私有仓库恢复备份。
- 同名文件覆盖确认。
- 脱敏错误日志。

### 第 2 阶段：CNB 支持

- CNB token 校验。
- CNB 仓库列表。
- CNB 创建公开/私有仓库。
- CNB raw/archive。
- JGit HTTPS push。
- 备份/恢复复用 GitDriveEngine。
- 公开 raw 和私有仓库提示。

### 第 3 阶段：GitLab/Gitea/Forgejo

- 自定义服务地址。
- Provider 能力识别。
- 文件浏览和 raw。
- JGit 写入和同步。

### 第 4 阶段：体验增强

- 文本文件编辑。
- 自动同步。
- 冲突向导。
- 版本历史和回滚。
- Web 管理页集成拖拽上传。

## 开发验证清单

### GitHub

- fine-grained PAT 只授权一个私有仓库时，只能看到该仓库。
- token 权限不足时，能给出明确提示。
- 创建公开仓库成功。
- 创建私有仓库成功。
- 上传 `json` 后 raw 链接可访问。
- 私有仓库复制 raw 时出现限制提示。
- 更新同名文件不会静默覆盖远端新版本。
- 备份 zip 上传后可在新设备恢复。

### CNB

- token 校验成功。
- 创建私有仓库成功。
- JGit HTTPS clone/push 成功。
- raw 链接复制成功。
- token 权限不足时提示可理解。

### Android

- 旋转屏幕或退出页面不会导致任务泄漏。
- 上传/clone 可取消。
- 清理缓存不删除远端仓库。
- App 重启后能恢复任务结果或显示上次失败。
- R8 打包后 JGit 功能可用。

## 验收标准

GitHub MVP：

- 用户可以配置 GitHub token。
- 用户可以创建私有仓库。
- 用户可以上传一个 `json` 文件并复制 raw 链接。
- 用户可以上传一个 `jar` 或 `zip` 文件。
- 用户可以一键备份 App 数据到私有仓库。
- 用户可以从私有仓库恢复备份。
- 用户无法误把备份仓库默认创建为公开仓库。
- 私有仓库 raw 链接不会被误提示为公开可用。
- token 不会出现在日志、备份、导出配置中。

CNB：

- 用户可以配置 CNB token。
- 用户可以创建私有仓库。
- 用户可以通过 JGit push 文件。
- 用户可以复制 CNB raw 链接。

## 结论

“Git 云盘”应该以 GitHub 为第一完整实现，以 CNB 为重点适配平台，以 JGit 作为跨平台写入和同步底座。平台 API 负责轻量、快速、体验好的操作；JGit 负责真正通用的 clone/pull/commit/push。

备份数据默认推荐私有仓库，公开仓库主要用于分享 raw 资源。第一版优先解决上传、raw、备份、恢复四个高频痛点，不把复杂 Git 概念暴露给普通用户。

评审后的关键修正是：不要把 JGit 简化理解为“所有功能都用它做”。JGit 是网盘式同步的底座，平台 API 是用户体验的加速层。两者结合，才能同时做到通用、可用、可维护。
