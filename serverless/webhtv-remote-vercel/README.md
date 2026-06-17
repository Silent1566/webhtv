# WebHTV Remote Vercel Edge Relay

这是 Vercel Edge Functions 版本的“远程托管”简易中转服务。它内置同一套 relay 逻辑，不需要 KV、数据库、对象存储或环境变量。

## 部署

在 Vercel 新建项目时，入口目录选择仓库里的：

```text
serverless/webhtv-remote-vercel
```

接口入口是：

```text
serverless/webhtv-remote-vercel/api/index.js
```

`api/index.js` 只依赖同目录的 `relay.js`，所以把 `serverless/webhtv-remote-vercel` 作为 Vercel 项目根目录也能部署。

部署后访问：

```text
GET /api/server/capabilities
```

返回 `relayMode=origin-token-memory` 即表示是零配置在线中转版。

## 限制

运行实例重启或被平台回收后，绑定码、未投递命令、同步临时文件会丢失；同一个域名/origin 下不需要重新绑定。App/主控端本地保存 `deviceToken/groupToken`，设备下一次 register/poll 会重建在线路由。需要离线队列、大文件暂存或长期备份时，再换完整版 Go/Rust 服务端或给 serverless 版本加存储增强。
