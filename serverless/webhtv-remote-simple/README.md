# WebHTV Remote Cloudflare Worker Relay

这是“远程托管”简易版 Cloudflare Worker 中转服务。它只做在线命令中转和一次性同步文件中转，不需要 KV、R2、Durable Objects、数据库或必填环境变量，也不要求用户手动上传文件。

它使用 Worker 运行实例内的内存 Map 保存绑定码、在线设备快照、命令队列和同步临时文件。长期绑定状态保存在 App/主控端本地，`deviceId/groupId/grantId` 由 `serverOrigin + token` 派生。实例重启或被平台回收后，临时命令和同步文件会丢失，但同一个域名/origin 下不需要重新绑定；设备下一次 register/poll 会重建在线路由。需要离线队列、大文件暂存或长期备份时，再换完整版 Go/Rust 服务端或给 serverless 版本加存储增强。

## 部署

```bash
cd serverless/webhtv-remote-simple
npm install
cp wrangler.toml.example wrangler.toml
npm run deploy
```

`wrangler.toml.example` 已经是零绑定配置，复制后可以直接部署。

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

`/api/server/capabilities` 会返回 `serverMode=simple`、`relayMode=origin-token-memory` 和能力清单。完整版 Go/Rust 服务端应复用同一套字段，只是开放更多能力。
