package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public class PlayerHelper {

    public static String getDefaultUa() {
        return Util.getUserAgent(App.get(), BuildConfig.APPLICATION_ID);
    }

    public static String getUa() {
        return resolveUa(Setting.getUa(), PlayerHelper::getDefaultUa);
    }

    public static String resolveUa(String ua, Supplier<String> fallback) {
        return TextUtils.isEmpty(ua) ? fallback.get() : ua;
    }

    public static String getSubtitleMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    public static Bundle toBundle(Map<String, String> headers) {
        Bundle bundle = new Bundle();
        headers.forEach(bundle::putString);
        return bundle;
    }

    public static String describeFormat(Format format) {
        StringJoiner joiner = new StringJoiner(",");
        if (format.id != null) joiner.add(format.id);
        if (format.label != null) joiner.add(format.label);
        if (format.codecs != null) joiner.add(format.codecs);
        if (format.language != null) joiner.add(format.language);
        if (format.sampleMimeType != null) joiner.add(format.sampleMimeType);
        if (format.containerMimeType != null) joiner.add(format.containerMimeType);
        if (format.width != C.LENGTH_UNSET) joiner.add(String.valueOf(format.width));
        if (format.height != C.LENGTH_UNSET) joiner.add(String.valueOf(format.height));
        if (format.sampleRate != C.RATE_UNSET_INT) joiner.add(String.valueOf(format.sampleRate));
        if (format.channelCount != C.LENGTH_UNSET) joiner.add(String.valueOf(format.channelCount));
        if (format.averageBitrate != C.LENGTH_UNSET) joiner.add(String.valueOf(format.averageBitrate));
        return joiner.toString();
    }

    public static void share(Activity activity, String url, Map<String, String> headers, CharSequence title) {
        try {
            if (url == null || url.isEmpty()) return;
            Bundle bundle = toBundle(headers);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, url);
            intent.putExtra("extra_headers", bundle);
            intent.putExtra("title", title).putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(getChooser(intent));
        } catch (Exception ignored) {
        }
    }

    /**
     * 调用外置播放器。返回 true 只代表系统真的接走了这次 startActivityForResult；
     * 调用方必须在 true 时才把播放页标记成「已跳转」，否则一个被取消的选择器或启动异常
     * 会留下永久 redirect 标记，播放页暂停后不再恢复，表现为切外置失败即黑屏。
     */
    public static boolean choose(Activity activity, String url, Map<String, String> headers, boolean isVod, long position, CharSequence title) {
        if (activity == null || TextUtils.isEmpty(url)) {
            PlaybackSwitchLog.log("external", "choose aborted reason=%s", activity == null ? "no-activity" : "empty-url");
            return false;
        }
        PlaybackSwitchLog.log("external", "choose begin isVod=%s position=%d url=%s", isVod, position,
                url.substring(0, Math.min(300, url.length())));
        // 与上游一致：把原始 URL 直接交给外置播放器。之前把 URL 改写成我们自己进程的
        // 127.0.0.1 本地代理，外置 App 是独立进程、拉流周期长，代理 session 3 分钟
        // TTL 过期后分片请求返回 404，正是「外置能打开但黑屏」的根因。UA/Referer
        // 通过标准 headers extra 传给 MX/VLC，由外置播放器自己带头上 CDN。
        try {
            List<String> list = new ArrayList<>();
            if (headers != null) headers.forEach((key, value) -> {
                list.add(key);
                list.add(value);
            });
            Uri data = url.startsWith("file://") || url.startsWith("/") ? FileUtil.getShareUri(url) : Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // 与上游一致用 video/*：application/dash+xml 等窄 MIME 会让绝大多数
            // 外置播放器没有匹配的 intent-filter，候选为 0、选择器消失。
            intent.setDataAndType(data, "video/*");
            intent.putExtra("title", title).putExtra("return_result", isVod);
            // headers 用标准 String[]（key/value 交替），这正是 MX Player / VLC
            // 通用的外调协议。之前改成 ArrayList<String> 反而不被部分播放器识别。
            intent.putExtra("headers", list.toArray(String[]::new));
            if (isVod) intent.putExtra("position", (int) position);
            Intent chooser = getChooser(intent);
            if (chooser == null) {
                PlaybackSwitchLog.log("external", "choose FAILED reason=no-external-candidate");
                Notify.show(ResUtil.getString(R.string.error_play_external_unavailable));
                return false;
            }
            activity.startActivityForResult(chooser, 1001);
            PlaybackSwitchLog.log("external", "choose STARTED startActivityForResult req=1001 intentType=video/*");
            return true;
        } catch (Exception e) {
            PlaybackSwitchLog.log("external", "choose EXCEPTION message=%s", e.getMessage());
            Notify.show(ResUtil.getString(R.string.error_play_external_unavailable));
            return false;
        }
    }

    public static void onExternalResult(Intent data, Runnable onNext, LongConsumer seekTo) {
        try {
            if (data == null || data.getExtras() == null) return;
            long position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) App.post(onNext);
            if ("user".equals(endBy)) seekTo.accept(position);
        } catch (Exception ignored) {
        }
    }

    private static Intent getChooser(Intent intent) {
        List<ComponentName> components = new ArrayList<>();
        int external = 0;
        for (ResolveInfo resolveInfo : App.get().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            String pkgName = resolveInfo.activityInfo.packageName;
            if (pkgName.equals(App.get().getPackageName())) {
                components.add(new ComponentName(pkgName, resolveInfo.activityInfo.name));
            } else {
                external++;
            }
        }
        // 排除自身后没有候选时，chooser 只能显示一个永远无法完成播放的空列表。
        // 直接放弃跳转，保留 App 内部正在播放的画面。
        PlaybackSwitchLog.log("external", "getChooser candidates=%d excludedSelf=%d", external, components.size());
        if (external == 0) return null;
        return Intent.createChooser(intent, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, components.toArray(new ComponentName[0]));
    }
}
