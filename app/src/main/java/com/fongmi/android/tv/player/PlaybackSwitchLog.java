package com.fongmi.android.tv.player;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 始终开启的播放切换诊断日志。
 *
 * <p>与 {@code SpiderDebug}/{@code DebugLogStore} 不同，这里不依赖用户预先打开「调试日志」开关：
 * 手动切换内核失败、IJK 加载失败、外置播放器跳转与返回都是偶发且难以复现的现场，必须无条件留痕。
 * 每条记录同时写入 {@code filesDir/webhtv-switch-diag.txt}（供一键分享）与 logcat（供 adb 抓取）。
 * 文件超过 {@link #MAX_BYTES} 时保留最新部分，绝不影响播放主流程。仅使用 API 24 可用的 IO。
 */
public final class PlaybackSwitchLog {

    private static final String TAG = "SwitchDiag";
    private static final String FILE_NAME = "webhtv-switch-diag.txt";
    private static final int MAX_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private PlaybackSwitchLog() {
    }

    public static void log(String tag, String format, Object... args) {
        String message;
        try {
            message = args == null || args.length == 0 ? format : String.format(Locale.US, format, args);
        } catch (Throwable e) {
            message = format;
        }
        String line = TIME.format(new Date()) + " [" + tag + "] " + message + "\n";
        Log.d(TAG + "/" + tag, line.substring(0, line.length() - 1));
        synchronized (LOCK) {
            OutputStream out = null;
            try {
                File file = file();
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                out = new FileOutputStream(file, true);
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Throwable ignored) {
                // 诊断日志绝不能影响播放
            } finally {
                closeQuietly(out);
            }
            trim();
        }
    }

    public static File file() {
        return new File(App.get().getFilesDir(), FILE_NAME);
    }

    public static String read() {
        synchronized (LOCK) {
            InputStream in = null;
            try {
                File file = file();
                if (!file.exists()) return "";
                in = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Throwable e) {
                return "";
            } finally {
                closeQuietly(in);
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            OutputStream out = null;
            try {
                out = new FileOutputStream(file(), false);
            } catch (Throwable ignored) {
            } finally {
                closeQuietly(out);
            }
        }
    }

    public static void share(FragmentActivity activity) {
        try {
            File file = file();
            if (!file.exists() || file.length() == 0) {
                Notify.show("暂无切换诊断日志，请先复现一次切换失败再分享");
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "WebHTV 播放切换诊断日志");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "分享诊断日志"));
        } catch (Exception e) {
            Notify.show("分享诊断日志失败：" + e.getMessage());
        }
    }

    private static void trim() {
        File file = file();
        if (!file.exists() || file.length() <= MAX_BYTES) return;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(file);
            long skip = file.length() - MAX_BYTES / 2;
            long skipped = 0;
            while (skipped < skip) {
                long s = in.skip(skip - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            byte[] rest = readAll(in);
            out = new FileOutputStream(file, false);
            out.write(rest);
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Throwable ignored) {
        }
    }
}
