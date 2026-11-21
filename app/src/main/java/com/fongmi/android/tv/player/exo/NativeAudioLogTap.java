package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Folds the media3ext native decoder log (LOG_TAG {@code ffmpeg_jni}) into the existing
 * diagnostic stream so an in-app export carries native decode/resample timings without adb.
 *
 * <p>Runs at most one short {@code logcat -d} per diagnostic tick on a private daemon thread;
 * never on the player thread, never unbounded, and every failure is swallowed into a bounded
 * diagnostic note so the export stays useful.
 */
final class NativeAudioLogTap {

    private static final String TAG = "ffmpeg_jni";
    private static final int MAX_LINES_PER_TICK = 20;
    private static final int SEEN_CAPACITY = 256;
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "native-audio-log-tap");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final Map<String, Boolean> SEEN =
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > SEEN_CAPACITY;
                }
            });

    private NativeAudioLogTap() {
    }

    static void request(PlaybackDiagnosticCollector log) {
        if (log == null || !BUSY.compareAndSet(false, true)) return;
        try {
            EXECUTOR.execute(() -> {
                try {
                    drain(log);
                } catch (RuntimeException ignored) {
                    // Diagnostics must never disturb playback.
                } finally {
                    BUSY.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            BUSY.set(false);
        }
    }

    private static void drain(PlaybackDiagnosticCollector log) {
        Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "brief", "-s", TAG)
                    .redirectErrorStream(true)
                    .start();
            int emitted = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains(TAG) || !line.contains("av3a-stats")) continue;
                    String key = Integer.toHexString(line.hashCode());
                    if (SEEN.put(key, Boolean.TRUE) != null) continue;
                    if (emitted >= MAX_LINES_PER_TICK) continue;
                    emitted++;
                    String body = line.substring(line.indexOf("av3a-stats"));
                    log.emit("audio.native", "logcat-ffmpeg_jni", e -> e
                            .observed("stage", "native-audio-window")
                            .observed("line", body)
                            .observed("metricScope", "native decode/resample timing window; not physical audibility"));
                }
            }
        } catch (Exception ignored) {
            // A missing logcat binary or a policy denial simply yields no native lines.
        } finally {
            if (process != null) process.destroy();
        }
    }
}
