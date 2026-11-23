package com.fongmi.android.tv.player.engine;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * 等价于旧内置 IJK 源码里的 IMediaPlayer.Listener 组合回调接口。
 * IJK 内核已换成 blbl(cat3399)/debugly 的预编译 AAR（ffmpeg7 单库），
 * 其 IMediaPlayer 不含该嵌套接口，这里在业务侧重建，方便 IjkSimplePlayer
 * 一并实现所有回调而不逐个声明。
 */
public interface IjkPlayerListener extends
        IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnBufferingUpdateListener,
        IMediaPlayer.OnVideoSizeChangedListener,
        IMediaPlayer.OnTimedTextListener {
}
