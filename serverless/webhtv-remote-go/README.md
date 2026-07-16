# WebHTV Go Remote Relay

Go 版远程托管中转服务，兼容现有 HTTP API，并额外提供 WebSocket 实时命令通道。

## 运行

已编译好的 Linux 二进制在：

```text
dist/webhtv-remote-go-linux-amd64
dist/webhtv-remote-go-linux-arm64
```

直接运行：

```bash
chmod +x dist/webhtv-remote-go-linux-amd64
WEBHTV_REMOTE_ADDR=:8787 ./dist/webhtv-remote-go-linux-amd64
```

也可以从源码运行：

```bash
cd serverless/webhtv-remote-go
go mod tidy
go run .
```

默认监听 `:8787`，可通过环境变量覆盖：

```bash
WEBHTV_REMOTE_ADDR=:8787 go run .
```

## 能力

- 兼容现有接口：设备注册、绑定码、设备列表、命令、同步分片。
- 新增 WebSocket：`/api/device/ws`。
- App 会优先使用 WebSocket；服务端不支持时自动回退 HTTP 轮询。
- 当前版本为内存存储，进程重启会丢失绑定码、命令队列和同步分片。

## 反向代理

若部署在 Nginx/Caddy 后面，需要透传 WebSocket：

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
```

新版 App 会自动发送 `X-WebHTV-Origin`，服务端优先使用该值作为 `serverOrigin`；没有该头时才回退到 `X-Forwarded-Host` / `Host`。因此新版 App 通常不需要额外配置，反向代理仍建议正确透传外部访问的 scheme/host，兼容旧客户端和手动调试请求。
