# WebHTV Rust Remote Relay

Rust 版远程托管中转服务，兼容现有 HTTP relay API，并支持 WebSocket 实时命令通道。

## 运行

已编译好的 Linux 二进制在：

```text
dist/webhtv-remote-rust-linux-amd64
dist/webhtv-remote-rust-linux-arm64
```

直接运行：

```bash
chmod +x dist/webhtv-remote-rust-linux-amd64
WEBHTV_REMOTE_ADDR=0.0.0.0:8787 ./dist/webhtv-remote-rust-linux-amd64
```

也可以从源码运行：

```bash
cd serverless/webhtv-remote-rust
cargo run --release
```

默认监听 `0.0.0.0:8787`，可通过环境变量覆盖：

```bash
WEBHTV_REMOTE_ADDR=127.0.0.1:8787 cargo run --release
```

验证：

```bash
curl http://127.0.0.1:8787/api/server/capabilities
```

正常会返回 `serverMode=rust`、`relayMode=rust-memory-websocket` 和 `webSocket=true`。

## 能力

- 兼容现有接口：设备注册、绑定码、设备列表、命令、同步分片。
- WebSocket：`/api/device/ws`，App 会优先使用；连接失败时自动回退 HTTP 轮询。
- 内存状态：绑定码、在线快照、命令队列和同步分片保存在进程内。
- 单文件部署：适合自建 VPS、NAS、软路由、容器或反向代理后运行。

## 反向代理

如果部署在 Nginx/Caddy/Traefik 后面，需要透传 WebSocket。新版 App 会自动发送 `X-WebHTV-Origin`，服务端优先使用该值作为 `serverOrigin`；没有该头时才回退到 `X-Forwarded-Host` / `Host`。因此新版 App 通常不需要额外配置，反向代理仍建议正确透传外部访问 origin，兼容旧客户端和手动调试请求。

Nginx 示例：

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
```

## 限制

当前版本与 Go 过渡版一样是内存 relay，进程重启后绑定码、未投递命令、同步临时文件会丢失；同一个域名/origin 下，App 本地保存的 `deviceToken/groupToken` 可以在下一次 register/poll 时恢复设备和设备组。需要持久命令历史、备份中心或文件管理时，再扩展 SQLite/对象存储版本。
