package com.fongmi.android.tv.smb;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 给阻塞的 smbj 调用套一层有界等待。
 *
 * <p>smbj 的 IO 不接受超时参数，也不能保证响应中断，因此超时后必须由调用方通过
 * {@code onTimeout} 关闭连接来真正解除阻塞；这里只负责"不再无限等待 + 中断工作线程"。
 */
public final class SmbTimeout {

    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "smb-io-" + THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private SmbTimeout() {
    }

    /**
     * 在限定时间内取回任务结果。
     *
     * @param onTimeout 超时后的清理动作（通常是关闭 smbj 连接），必须不抛异常
     * @throws TimeoutException 超时
     * @throws Exception        任务自身抛出的异常，保持原样向上抛
     */
    public static <T> T call(Callable<T> task, long timeoutMs, Runnable onTimeout) throws Exception {
        if (task == null) throw new IllegalArgumentException("task == null");
        if (timeoutMs <= 0L) throw new IllegalArgumentException("timeoutMs must be positive");
        Future<T> future = EXECUTOR.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            if (onTimeout != null) onTimeout.run();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }
}
