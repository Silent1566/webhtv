# WebHTV Remote Cloudflare Worker Relay

这是“远程托管”简易版 Cloudflare Worker 中转服务。它只做在线命令中转和一次性同步文件中转，不需要 KV、R2、数据库或必填环境变量，也不要求用户手动上传文件。

Cloudflare Worker 普通全局变量不能保证两台设备命中同一个运行实例，所以默认配置使用 Durable Object 统一承载绑定码、在线设备快照和命令队列，并保存轻量状态快照。长期绑定状态仍保存在 App/主控端本地，`deviceId/groupId/grantId` 由 `serverOrigin + token` 派生。同步文件分片仍是短期中转数据，不适合作为长期备份存储。需要离线队列、大文件暂存或长期备份时，再换完整版 Go/Rust 服务端或给 serverless 版本加 R2 等存储增强。

新版 App 会在请求头中自动携带 `X-WebHTV-Origin`，服务端优先用该值作为 `serverOrigin`。因此 Cloudflare 同时存在 `*.workers.dev` 和自定义域名时，只要 App 中填写的是同一个自定义域名，就不需要额外配置环境变量，也不会因为平台默认域名参与 ID 派生而导致绑定码校验失败。旧客户端未携带该头时仍回退到请求实际 origin。

## 部署

```bash
cd serverless/webhtv-remote-cloudflare
npm install
cp wrangler.toml.example wrangler.toml
npm run deploy
```

`wrangler.toml.example` 已包含 Durable Object 绑定和迁移配置，复制后可以直接部署。旧版本如果已经有 `wrangler.toml`，需要手动补上：

```toml
[[durable_objects.bindings]]
name = "RELAY_DO"
class_name = "WebHTVRemoteRelayDO"

[[migrations]]
tag = "v1"
new_sqlite_classes = ["WebHTVRemoteRelayDO"]
```

## 核心流程

1. 任意 WebHTV App 调用 `/api/device/register` 注册设备并保存 `deviceId/deviceToken`。
2. 被控端生成 `bindGrantToken`，调用 `/api/device/bind-code` 生成 6 位绑定码。
3. 主控端 Web 控制台调用 `/api/groups/claim` 输入绑定码，服务端返回 `groupToken/groupTokenHash/bindGrantToken`，主控端本地保存。
4. 另一台 WebHTV App 可用同一个 `groupToken` 注册为来源设备，也可以通过绑定码加入同一个设备组。
5. 主控端 Web 控制台调用 `/api/sync/create`，选择来源设备、目标设备和 `SyncOptions`。
6. 来源设备轮询到 `remoteSync.export` 命令后自动生成 `backup`、`syncFiles` 和同步内部文件包并提交到 Worker。
7. 目标设备轮询到 `remoteSync.restore` 命令后自动拉取临时文件并恢复。

## 约定

设备请求头：

```text
X-Device-Id: <deviceId>
Authorization: Bearer <deviceToken>
```

主控端 Web 控制台请求头：

```text
Authorization: Bearer <groupToken>
```

`/api/server/capabilities` 会返回 `serverMode=cloudflare`、`relayMode=cloudflare-durable-object` 和能力清单。没有配置 `RELAY_DO` 时会降级为 `origin-token-memory`，该模式只适合本地调试，不建议生产使用。完整版 Go/Rust 服务端应复用同一套字段，只是开放更多能力。
