# WebHome Skills 使用说明

这两个 skill 用于让 AI 客户端稳定生成、审查和调试 WebHTV/FongMi WebHome 相关内容。使用时要复制整个 skill 目录，不要只复制 `SKILL.md`，因为目录内还有 `references/`、`assets/`、`scripts/` 等配套材料。

下文命令默认在 `webhome-devkit/` 目录内执行；如果从主项目根目录执行，请把 `skills/...` 改成 `webhome-devkit/skills/...`。

## Skill 列表

| Skill | 适用任务 |
| --- | --- |
| `webhome-homepage-builder` | 开发 WebHome 单文件首页、`homePage`、Nostr/TMDB/PanSou 推荐页、透明 WebView UI、TV 遥控焦点、`fm.req`/`fm.res`/`fm.cache`/`fm.ui`、旧 Android WebView 兼容、首页数据源逆向和 WAF 可行性判断 |
| `webhome-extension-builder` | 开发 WebHome 注入扩展脚本、`sites[].extensions`、`webHomeExtensions`、原站增强、App 播放按钮、网盘/磁力/直链路由、`fm.vodInline` 按集解析、TV focus helper、扩展 manifest、网站 JS/API/播放器逆向 |

## 通用原则

1. 优先让客户端原生加载 skill。能加载目录型 skill 的客户端，应把整个 `skills/webhome-*` 目录复制到客户端的 skills 目录。
2. 不支持原生 skill 的客户端，用项目规则或自定义指令要求 AI 在相关任务前完整读取对应 `SKILL.md`。
3. 让 AI 明确读取关联 reference。`SKILL.md` 里写了何时读取 `references/`、`assets/`、`scripts/`，不要只让 AI 看摘要。
4. 做网站逆向时，如果 `curl`、probe、`fm.req` 异常，应切换 Playwright/CDP/Chrome DevTools/App WebView 调试做正常浏览器观察；不要做 WAF 绕过、验证码处理、clearance cookie 获取或隐私 token 提取。
5. 生成 WebHome 播放入口时，尽量传 `pic` 和 `wallPic`；`pic` 是海报/默认 artwork，`wallPic` 是原生播放页背景图，推荐横屏剧照/backdrop。没有 `wallPic` 时播放页使用 App 默认背景/壁纸，不用 `pic` 兜底。
6. 遇到登录、验证或盾页时，可以启动浏览器或连接 App WebView 让用户人工完成交互，再基于授权会话观察 DOM/Network/Console；skill 应强调观察和诊断，不应生成自动绕过验证的代码。
7. 移动端顶部有固定/粘性操作区时，只在 `edge`/`immersive`/fullscreen 等系统栏融合状态预留顶部安全区，退出全屏恢复普通 chrome 后要移除该预留。

## OpenAI Codex

Codex 支持本地 skills 目录。复制后开启新会话即可使用。

```bash
mkdir -p ~/.codex/skills
cp -R skills/webhome-homepage-builder ~/.codex/skills/
cp -R skills/webhome-extension-builder ~/.codex/skills/
```

使用方式：

```text
使用 webhome-homepage-builder，帮我做一个 WebHome 首页...
```

```text
使用 webhome-extension-builder，给 https://example.com 写一个 WebHome 扩展...
```

如果不想安装到全局，也可以在项目里保留 `skills/`，然后在提示词里要求：

```text
请先完整读取 skills/webhome-extension-builder/SKILL.md，并按其中要求读取必要 references/templates，再开始实现。
```

## Claude Code / Claude Skills

如果你的 Claude 客户端支持本地 Skills，按客户端文档导入整个 skill 文件夹。常见做法是放到用户级 skills 目录：

```bash
mkdir -p ~/.claude/skills
cp -R skills/webhome-homepage-builder ~/.claude/skills/
cp -R skills/webhome-extension-builder ~/.claude/skills/
```

如果当前版本不自动识别 `SKILL.md`，在 `CLAUDE.md` 加入项目规则：

```markdown
当任务涉及 WebHome 首页、homePage、Nostr/TMDB/PanSou 首页、fm SDK 或旧 Android WebView 兼容时，先完整读取 skills/webhome-homepage-builder/SKILL.md。

当任务涉及 WebHome 扩展、sites[].extensions、webHomeExtensions、App 播放按钮、fm.vodInline、网盘/磁力路由或网站 JS/API 逆向时，先完整读取 skills/webhome-extension-builder/SKILL.md。

读取 SKILL.md 后，根据其中 Source Material/Workflow 要求继续读取必要 references、assets/templates 或 scripts。不要跳过兼容性检查和 WAF/浏览器观察规则。
```

## OpenCode

OpenCode 常用目录是 `~/.config/opencode/skills`：

```bash
mkdir -p ~/.config/opencode/skills
cp -R skills/webhome-homepage-builder ~/.config/opencode/skills/
cp -R skills/webhome-extension-builder ~/.config/opencode/skills/
```

然后在任务里直接点名 skill，或用自然语言描述 WebHome 首页/扩展任务，让客户端按 description 触发。

## Cursor

Cursor 通常不直接执行 `SKILL.md`，建议用项目规则桥接。在项目里创建 `.cursor/rules/webhome-skills.mdc`：

```markdown
---
description: Use WebHome skills for WebHTV homepage and extension work
alwaysApply: false
---

涉及 WebHome 首页、homePage、Nostr/TMDB/PanSou、fm SDK、透明 WebView、TV 遥控焦点、旧 Android WebView 兼容时，先完整读取 `skills/webhome-homepage-builder/SKILL.md`。

涉及 WebHome 扩展、`sites[].extensions`、`webHomeExtensions`、App 播放按钮、网盘/磁力/直链路由、`fm.vodInline`、网站 JS/API 逆向时，先完整读取 `skills/webhome-extension-builder/SKILL.md`。

读取 SKILL.md 后按其中要求读取必要 `references/`、`assets/`、`scripts/`。生成播放入口时优先传 `pic` 和 `wallPic`。直接 HTTP 异常时用 Playwright/CDP/浏览器调试观察，不做 WAF 绕过。
```

## Windsurf

Windsurf 可用 `.windsurfrules` 或工作区规则：

```markdown
WebHome 相关任务必须先读取本项目 `skills/`：

- 首页/homePage/透明 WebView/PanSou/Nostr/TMDB/TV 焦点/旧 WebView 兼容：读 `skills/webhome-homepage-builder/SKILL.md`
- 扩展脚本/sites[].extensions/webHomeExtensions/App 播放按钮/fm.vodInline/网站逆向：读 `skills/webhome-extension-builder/SKILL.md`

按 SKILL.md 指示继续读取 references、templates、scripts。不要只凭记忆生成。curl 或 fm.req 异常时切换正常浏览器观察。
```

## Cline / Roo Code

可放到 `.clinerules`、`.roo/rules/webhome-skills.md` 或扩展的 Custom Instructions：

```markdown
当用户要求开发、修改、审查 WebHome 首页或扩展时：

1. 根据任务类型完整读取：
   - `skills/webhome-homepage-builder/SKILL.md`
   - `skills/webhome-extension-builder/SKILL.md`
2. 按 SKILL.md 的 Source Material 继续读取必要 references/templates/scripts。
3. 保持旧 Android WebView 兼容，JS 语法基线 ES2017。
4. 播放入口传 `pic`/`wallPic`，需要时调用 `fm.preloadArtwork(pic, wallPic)`。
5. 网站逆向先做浏览器/Playwright/CDP 观察，不写 WAF 绕过逻辑。
```

## Gemini CLI / Continue / Aider / 其他客户端

这类客户端通常用项目规则文件、系统提示词或手动上下文。推荐在项目根目录放一个 `AGENTS.md` 或把下面内容放入客户端自定义指令：

```markdown
本项目有 WebHome skills：

- `skills/webhome-homepage-builder/SKILL.md`
- `skills/webhome-extension-builder/SKILL.md`

凡是 WebHome 首页、扩展、fm SDK、PanSou、Nostr/TMDB、TV 遥控焦点、WebView 兼容、网站 JS/API 逆向任务，开始前必须读取对应 SKILL.md，并按其中指示读取 references、assets、scripts。
```

如果客户端支持上传文件夹或知识库，上传整个 `skills/` 目录；如果只支持单文件上下文，至少上传对应 `SKILL.md` 和它点名要求读取的 reference/template 文件。

## 快速提示词模板

开发 WebHome 首页：

```text
使用 webhome-homepage-builder。目标是制作一个 WebHome 单文件首页，要求兼容旧 Android WebView、透明背景、TV 遥控焦点、fm.req/fm.res、播放时传 pic/wallPic。请先读取 skill 和必要 references 后再实现。
```

开发 WebHome 扩展：

```text
使用 webhome-extension-builder。目标站点是 <URL>，站点 key 是 <key>。需要在详情页注入 App 播放按钮，支持网盘/磁力/直链，播放时传 pic/wallPic。若 curl/probe 异常，请用 Playwright 或浏览器调试观察，不要做 WAF 绕过。
```

审查现有文件：

```text
使用对应 WebHome skill，审查这个首页/扩展。优先指出旧 WebView 兼容问题、SDK 误用、TV 焦点/返回问题、播放 artwork 缺失、WAF/会话假设和测试缺口。
```

## 分发

分发给其他客户端时保留目录结构：

```text
skills/
  webhome-homepage-builder/
    SKILL.md
    references/
    assets/
    scripts/
  webhome-extension-builder/
    SKILL.md
    references/
    assets/
    scripts/
```

可以直接压缩 `skills/` 目录。解压后按目标客户端说明复制到对应位置，或保留在项目内通过规则文件引用。
