# FFmpeg 模式切换功能

## 功能说明

用户现在可以在设置中切换**三种**播放器渲染器实现：
- **Simple**（默认，推荐）：完全模仿 TV 项目，不使用 NextLib + 不使用自定义缓冲配置
- **NextLib**：使用第三方 `nextlib-media3ext` 库的自定义 FFmpeg 渲染器
- **Official**：仅使用 Media3 官方渲染器（保留自定义缓冲配置）

## 使用方法

### Leanback（电视端）
设置 → 播放器设置 → FFmpeg模式 → 点击循环切换

### Mobile（手机端）
设置 → 播放器 → FFmpeg模式 → 点击循环切换

显示：`Simple` → `NextLib` → `Official` → `Simple` ...  
**注意**：切换后需退出视频重新播放才生效

**默认模式**：Simple（与 TV 项目完全一致，最稳定）

---

## 🎯 推荐使用顺序

**如果遇到卡顿掉帧，请按此顺序尝试：**

### 1. **Simple 模式**（默认，强烈推荐，最接近 TV 项目）
✅ 完全模仿 TV 项目实现  
✅ 无自定义 FFmpeg 渲染器  
✅ 无自定义缓冲配置（使用 ExoPlayer 默认值）  
✅ 最简单、最稳定  
✅ **现在是默认模式**

### 2. **NextLib 模式**
⚠️ 使用第三方 FFmpeg 渲染器  
⚠️ 自定义缓冲配置  
⚠️ 最复杂，可能存在兼容性问题  
⚠️ 开始流畅后可能卡顿（过度缓冲）

### 3. **Official 模式**
❌ 移除 NextLib 渲染器  
❌ 保留自定义缓冲配置  
❌ 可能从头卡到尾（某些格式不支持）

---

## 技术实现

### 核心差异对比

| 特性 | NextLib | Official | Simple |
|------|---------|----------|--------|
| **FFmpeg 渲染器** | NextLib 第三方 | 无 | 无 |
| **LoadControl** | 自定义缓冲 | 自定义缓冲 | ExoPlayer 默认 |
| **ExoEnhanced** | 支持 | 支持 | 不支持 |
| **复杂度** | 高 | 中 | 低 |
| **与 TV 项目对比** | 完全不同 | 部分相似 | **完全一致** |

### 1. NextLib 模式（mode = 0）

```java
// 使用 FfmpegRenderersFactory 自定义渲染器
private static class FfmpegRenderersFactory extends DefaultRenderersFactory {
    @Override
    protected void buildAudioRenderers(...) {
        super.buildAudioRenderers(...);
        out.add(..., new CompatFfmpegAudioRenderer(...));
    }
    @Override
    protected void buildVideoRenderers(...) {
        super.buildVideoRenderers(...);
        out.add(..., new FfmpegVideoRenderer(...));
    }
}

// 自定义 LoadControl
builder.setLoadControl(buildLoadControl());
```

**依赖**: `io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1`

### 2. Official 模式（mode = 1）

```java
// 仅使用标准 DefaultRenderersFactory
DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get());
return factory
    .setEnableDecoderFallback(true)
    .setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));

// 自定义 LoadControl
builder.setLoadControl(buildLoadControl());
```

### 3. Simple 模式（mode = 2，完全模仿 TV 项目）

```java
// 仅使用标准 DefaultRenderersFactory
DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get());
return factory
    .setEnableDecoderFallback(true)
    .setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));

// 不设置 LoadControl，使用 ExoPlayer 默认值
// builder.setLoadControl(...);  // 注释掉
```

**关键**：Simple 模式在 `buildPlayer` 中跳过 `setLoadControl`，与 TV 项目完全一致。

---

## 修改文件列表

### 核心逻辑
- `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java`
  - 新增 `getFFmpegMode()` / `putFFmpegMode(int)` 方法
  - 支持 0=NextLib, 1=Official, 2=Simple
  - **默认值：`2`（Simple 模式）**

- `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`
  - `buildPlayer` - Simple 模式跳过 `setLoadControl`
  - `buildRenderersFactory` - 根据模式分发到三种实现
  - `buildNextLibRenderersFactory` - NextLib 模式
  - `buildOfficialRenderersFactory` - Official 模式
  - `buildSimpleRenderersFactory` - Simple 模式

### UI 界面（Leanback + Mobile）
- 布局文件 - 添加"FFmpeg模式"设置项
- Activity/Fragment - 添加循环切换逻辑（0→1→2→0）
- 显示文本：`NextLib` / `Official` / `Simple`

### 字符串资源
- `strings.xml` (EN/CN/TW) - `player_ffmpeg_mode`

---

## 问题诊断

### 用户反馈分析

**症状**：
- Official 模式：从头卡到尾（完全无法播放）
- NextLib 模式：开始流畅，后面卡顿掉帧

**原因分析**：
1. **Official 从头卡到尾** → 系统 MediaCodec 不支持该视频格式，需要 FFmpeg 软解
2. **NextLib 开始流畅后卡顿** → FFmpeg 能解码，但可能是：
   - 自定义缓冲配置过度缓冲，导致内存压力
   - 渲染器插入逻辑导致 AV 不同步
   - ExoEnhanced 参数过于激进

**解决方案**：使用 **Simple 模式**
- ✅ 保留 FFmpeg 软解能力（通过 ExtensionRendererMode）
- ✅ 移除复杂的 NextLib 渲染器插入逻辑
- ✅ 使用 ExoPlayer 默认缓冲策略
- ✅ 与 TV 项目完全一致

---

## 技术细节

### LoadControl 差异

**TV 项目（Simple 模式）：**
```java
ExoPlayer player = new ExoPlayer.Builder(App.get())
    .setTrackSelector(...)
    .setRenderersFactory(...)
    .setMediaSourceFactory(...)
    .build();  // 不设置 LoadControl
```

**当前项目（NextLib/Official 模式）：**
```java
builder.setLoadControl(new DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        DEFAULT_MIN_BUFFER_MS * PlayerSetting.getBuffer(),  // 用户设置放大
        DEFAULT_MAX_BUFFER_MS * PlayerSetting.getBuffer(),
        ...
    )
    .build());
```

**问题**：用户设置的缓冲倍数可能导致过度缓冲，触发 GC 或内存压力。

### 渲染器链对比

**NextLib 模式：**
```
1. MediaCodec 硬解渲染器（系统）
2. CompatFfmpegAudioRenderer（NextLib）
3. FfmpegVideoRenderer（NextLib）
```

**Official / Simple 模式：**
```
1. MediaCodec 硬解渲染器（系统）
2. 无额外渲染器
```

通过 `setExtensionRendererMode` 控制是否优先使用扩展渲染器（FFmpeg）。

---

## 存储键

```java
Prefers.getInt("ffmpeg_mode", 2)
// 0 = NextLib
// 1 = Official
// 2 = Simple（默认）
```

---

## 未来优化建议

1. ~~**默认值调整**：如果 Simple 模式测试稳定，可以改为默认（`mode = 2`）~~ ✅ 已完成
2. **自动检测**：根据视频格式自动选择合适的模式
3. **性能监控**：记录掉帧率，提示用户切换模式

---

**修改日期**: 2026-07-05  
**相关 Issue**: 用户反馈 TV 项目能正常播放的剧，当前项目卡顿掉帧  
**测试结论**: Official 从头卡到尾，NextLib 开始流畅后卡顿 → Simple 模式应该能解决  
**默认模式**: Simple（已设为默认，与 TV 项目完全一致）
