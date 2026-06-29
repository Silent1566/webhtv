package com.fongmi.android.tv;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.GithubProxy;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Locale;

public class Updater implements Download.Callback, UpdateListener {

    private static final String DEFAULT_RELEASE_NOTES = "手动触发 GitHub Actions 构建发布。";
    private static final String SOURCE_CNB = "cnb";
    private static final String SOURCE_GITHUB = "github";
    private static final Updater INSTANCE = new Updater();

    private final LifecycleEventObserver lifecycleObserver = (source, event) -> {
        if (!(source instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) source;
        if (event == Lifecycle.Event.ON_DESTROY) unbind(activity);
    };

    private WeakReference<FragmentActivity> activityRef;
    private UpdateDialog dialog;
    private Download download;
    private Update stable;
    private Update beta;
    private Update selected;
    private boolean force;
    private boolean downloading;
    private boolean canceled;
    private int lastProgress = -1;
    private long lastBytes;
    private long lastTotal;
    private long lastSpeed;
    private long lastElapsed;

    private Updater() {
    }

    public static Updater create() {
        return INSTANCE;
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getName() {
        return BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
    }

    public Updater force() {
        force = true;
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        bind(activity);
        boolean forceCheck = force;
        force = false;
        if (downloading) {
            restoreDialog(activity);
            return;
        }
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity, forceCheck));
    }

    public void resume(FragmentActivity activity) {
        bind(activity);
        restoreDialog(activity);
    }

    private void doInBackground(FragmentActivity activity, boolean forceCheck) {
        stable = getUpdate(Update.CHANNEL_STABLE);
        beta = getUpdate(Update.CHANNEL_BETA);
        if (!stable.hasUpdate() && !beta.hasUpdate()) {
            if (forceCheck && (stable.hasManifest() || beta.hasManifest())) {
                selected = stable;
                App.post(() -> show(activity));
                return;
            }
            if (forceCheck) App.post(() -> Notify.show(hasErrorOnly() ? R.string.update_failed : R.string.update_latest));
            return;
        }
        selected = stable;
        App.post(() -> show(activity));
    }

    private Update getUpdate(String channel) {
        Update update = readUpdate(channel, Github.getCnbAsset(getManifestName(channel)), SOURCE_CNB);
        if (update.hasManifest()) return update;
        Update fallback = Update.CHANNEL_BETA.equals(channel) ? getGithubBetaUpdate(channel) : readUpdate(channel, Github.getGithubLatestAsset(getManifestName(channel)), SOURCE_GITHUB);
        return fallback.hasManifest() ? fallback : update;
    }

    private Update getGithubBetaUpdate(String channel) {
        String manifestName = getManifestName(channel);
        try {
            JSONArray releases = new JSONArray(OkHttp.string(Github.getReleasesApi()));
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || !isBetaRelease(release)) continue;
                String tag = release.optString("tag_name");
                String url = findAssetUrl(release.optJSONArray("assets"), manifestName);
                if (TextUtils.isEmpty(url) && !TextUtils.isEmpty(tag)) url = Github.getGithubReleaseAsset(tag, manifestName);
                if (TextUtils.isEmpty(url)) continue;
                Update update = readUpdate(channel, url, SOURCE_GITHUB);
                if (update.hasManifest()) return update;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Update.empty(channel);
    }

    private boolean isBetaRelease(JSONObject release) {
        String tag = release.optString("tag_name");
        return release.optBoolean("prerelease") || tag.contains("-beta-");
    }

    private String findAssetUrl(JSONArray assets, String name) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !name.equals(asset.optString("name"))) continue;
            return asset.optString("browser_download_url");
        }
        return "";
    }

    private Update readUpdate(String channel, String manifestUrl, String source) {
        Update update = Update.empty(channel);
        try {
            String text = OkHttp.string(GithubProxy.apply(manifestUrl));
            if (TextUtils.isEmpty(text)) throw new IllegalStateException("Empty update manifest: " + manifestUrl);
            JSONObject object = new JSONObject(text);
            update.name = object.optString("name");
            update.desc = object.optString("desc");
            update.notes = object.optString("notes");
            update.channel = object.optString("channel", channel);
            update.code = object.optInt("code");
            update.apk = object.optString("apk");
            update.size = object.optLong("size");
            update.sha256 = object.optString("sha256");
            update.apkUrl = getApkUrl(update, source);
            if (isDefaultReleaseNotes(update.notes)) update.notes = "";
            if (TextUtils.isEmpty(update.notes)) {
                String notes = getReleaseNotes(update.name);
                if (!TextUtils.isEmpty(notes)) update.notes = notes;
            }
        } catch (Exception e) {
            e.printStackTrace();
            update.error = e.getMessage();
        }
        return update;
    }

    private String getManifestName(String channel) {
        return getAssetName(channel, "json");
    }

    private String getDefaultApkName(String channel) {
        return getAssetName(channel, "apk");
    }

    private String getAssetName(String channel, String ext) {
        String suffix = Update.CHANNEL_BETA.equals(channel) ? "-beta" : "";
        return getName() + suffix + "." + ext;
    }

    private String getApkUrl(Update update, String source) {
        String apk = TextUtils.isEmpty(update.apk) ? getDefaultApkName(update.channel) : update.apk;
        if (apk.startsWith("http://") || apk.startsWith("https://")) return apk;
        if (SOURCE_GITHUB.equals(source) && !TextUtils.isEmpty(update.name)) return Github.getGithubReleaseAsset(update.name, apk);
        return Github.getCnbAsset(apk);
    }

    private boolean isDefaultReleaseNotes(String notes) {
        return !TextUtils.isEmpty(notes) && DEFAULT_RELEASE_NOTES.equals(notes.trim());
    }

    private String getReleaseNotes(String tag) {
        if (TextUtils.isEmpty(tag)) return "";
        String notes = readReleaseNotes(tag);
        if (!TextUtils.isEmpty(notes) || tag.startsWith("v")) return notes;
        return readReleaseNotes("v" + tag);
    }

    private String readReleaseNotes(String tag) {
        try {
            return new JSONObject(OkHttp.string(GithubProxy.apply(Github.getReleaseApi(tag)))).optString("body");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasErrorOnly() {
        return !stable.hasManifest() && !beta.hasManifest() && (!TextUtils.isEmpty(stable.error) || !TextUtils.isEmpty(beta.error));
    }

    private void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        bind(activity);
        dismiss();
        String channel = selected == null ? Update.CHANNEL_STABLE : selected.channel;
        dialog = UpdateDialog.create().stable(stable).beta(beta).selected(channel).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (selected == null || !selected.hasUpdate()) {
            Notify.show(R.string.update_latest);
            return;
        }
        view.setEnabled(false);
        downloading = true;
        canceled = false;
        resetProgress();
        Path.clear(getFile());
        setDialogProgress(0, 0, selected.size, 0, 0);
        download = Download.create(selected.apkUrl, getFile()).tag(selected.apkUrl);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        if (downloading) {
            canceled = true;
            downloading = false;
            if (download != null) download.cancel();
            download = null;
            resetProgress();
            Notify.show(R.string.update_canceled);
            dismiss();
            return;
        }
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    @Override
    public void onClose() {
        dialog = null;
    }

    @Override
    public void onChannel(String channel) {
        Setting.putUpdateChannel(channel);
        selected = Update.CHANNEL_BETA.equals(channel) ? beta : stable;
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismissAllowingStateLoss();
        } catch (Exception ignored) {
        } finally {
            dialog = null;
        }
    }

    @Override
    public void progress(int progress) {
        setDialogProgress(progress, 0, 0, 0, 0);
    }

    @Override
    public void progress(int progress, long bytes, long total, long speed, long elapsed) {
        setDialogProgress(progress, bytes, total, speed, elapsed);
    }

    private void setDialogProgress(int progress, long bytes, long total, long speed, long elapsed) {
        if (canceled || !downloading) return;
        long manifestSize = selected == null ? 0 : selected.size;
        if (total <= 0 && manifestSize > 0) total = manifestSize;
        if (progress < 0 && total > 0 && bytes > 0) progress = (int) (bytes * 100.0 / total);
        lastProgress = progress;
        lastBytes = bytes;
        lastTotal = total;
        lastSpeed = speed;
        lastElapsed = elapsed;
        if (dialog == null) return;
        if (!dialog.setProgress(progress, bytes, total, speed, elapsed)) dialog = null;
    }

    @Override
    public void error(String msg) {
        if (canceled) return;
        downloading = false;
        download = null;
        resetProgress();
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        if (canceled) return;
        download = null;
        Update target = selected;
        Task.execute(() -> {
            String error = validate(file, target);
            App.post(() -> {
                if (canceled) return;
                downloading = false;
                resetProgress();
                if (!TextUtils.isEmpty(error)) {
                    Path.clear(file);
                    Notify.show(error);
                    dismiss();
                    return;
                }
                FileUtil.openFile(file);
                dismiss();
            });
        });
    }

    private void restoreDialog(FragmentActivity activity) {
        if (!downloading || selected == null) return;
        show(activity);
        setDialogProgress(lastProgress, lastBytes, lastTotal, lastSpeed, lastElapsed);
    }

    private String validate(File file, Update update) {
        if (file == null || !file.exists() || file.length() <= 0) return ResUtil.getString(R.string.update_download_invalid);
        if (update != null && update.size > 0 && file.length() != update.size) return ResUtil.getString(R.string.update_download_incomplete);
        if (update != null && !TextUtils.isEmpty(update.sha256) && !update.sha256.equalsIgnoreCase(sha256(file))) return ResUtil.getString(R.string.update_download_checksum);
        if (App.get().getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0) == null) return ResUtil.getString(R.string.update_download_invalid);
        return "";
    }

    private String sha256(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) builder.append(String.format(Locale.ROOT, "%02x", value));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void bind(FragmentActivity activity) {
        if (activity == null) return;
        FragmentActivity old = activityRef == null ? null : activityRef.get();
        if (old == activity) return;
        if (old != null) old.getLifecycle().removeObserver(lifecycleObserver);
        activityRef = new WeakReference<>(activity);
        activity.getLifecycle().addObserver(lifecycleObserver);
    }

    private void unbind(FragmentActivity activity) {
        FragmentActivity current = activityRef == null ? null : activityRef.get();
        if (current != activity) return;
        activity.getLifecycle().removeObserver(lifecycleObserver);
        activityRef = null;
        if (!downloading) dialog = null;
    }

    private void resetProgress() {
        lastProgress = -1;
        lastBytes = 0;
        lastTotal = 0;
        lastSpeed = 0;
        lastElapsed = 0;
    }
}
