package com.fongmi.android.tv.player.mpv;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MpvConfigStore {

    private static final String KEY_NAME = "mpv_config_name";
    private static final String KEY_SOURCE = "mpv_config_source";
    private static final String KEY_TYPE = "mpv_config_type";
    private static final String KEY_HISTORY = "mpv_config_history";
    public static final String TARGET_MPV_CONF = "mpv.conf";
    public static final String TARGET_INPUT_CONF = "input.conf";
    public static final String TARGET_SCRIPTS = "scripts";
    private static final String TYPE_DEFAULT = "default";
    private static final String TYPE_FILE = "file";
    private static final String TYPE_URL = "url";
    private static final String CONFIG_DIR = "mpv";
    private static final String CONFIG_FILE = "mpv.conf";
    private static final Type HISTORY_LIST = new TypeToken<List<History>>() {}.getType();

    private MpvConfigStore() {
    }

    public static File configDir() {
        File dir = new File(App.get().getFilesDir(), CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
        ensureSubDir("scripts");
        ensureSubDir("script-opts");
        ensureSubDir("fonts");
        ensureSubDir("shaders");
        return dir;
    }

    public static File configFile() {
        return new File(configDir(), CONFIG_FILE);
    }

    public static File targetFile(String target) {
        if (TARGET_INPUT_CONF.equals(target)) return new File(configDir(), TARGET_INPUT_CONF);
        return configFile();
    }

    public static File scriptsDir() {
        return new File(configDir(), TARGET_SCRIPTS);
    }

    public static void ensureReady() {
        File file = configFile();
        if (!file.isFile() || file.length() == 0) {
            writeText(defaultConfig());
            putDefault(TARGET_MPV_CONF);
        }
    }

    public static String getName() {
        return getName(TARGET_MPV_CONF);
    }

    public static String getName(String target) {
        return Prefers.getString(key(KEY_NAME, target));
    }

    public static String getSource() {
        return getSource(TARGET_MPV_CONF);
    }

    public static String getSource(String target) {
        return Prefers.getString(key(KEY_SOURCE, target));
    }

    public static boolean isUrl() {
        return isUrl(TARGET_MPV_CONF);
    }

    public static boolean isUrl(String target) {
        return TYPE_URL.equals(Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT));
    }

    public static String summary() {
        return "mpv.conf: " + summary(TARGET_MPV_CONF);
    }

    public static String summary(String target) {
        if (TARGET_SCRIPTS.equals(target)) return scriptsSummary();
        String type = Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT);
        String name = getName(target);
        String source = getSource(target);
        if (TextUtils.isEmpty(type) || TYPE_DEFAULT.equals(type)) return ResUtil.getString(R.string.mpv_config_default);
        if (!TextUtils.isEmpty(name)) return name;
        if (TYPE_FILE.equals(type)) return TextUtils.isEmpty(source) ? ResUtil.getString(R.string.mpv_config_local) : fileName(source);
        if (TYPE_URL.equals(type)) return TextUtils.isEmpty(source) ? ResUtil.getString(R.string.mpv_config_url) : source;
        return ResUtil.getString(R.string.mpv_config_default);
    }

    public static void applyDefault() {
        applyDefault(TARGET_MPV_CONF);
    }

    public static void applyDefault(String target) {
        if (TARGET_SCRIPTS.equals(target)) {
            clearScripts();
            putDefault(target);
            return;
        }
        if (TARGET_INPUT_CONF.equals(target)) {
            File file = targetFile(target);
            if (file.isFile()) file.delete();
            putDefault(target);
            return;
        }
        writeText(defaultConfig());
        putDefault(target);
    }

    public static void applyFile(String path, String name) throws IOException {
        applyFile(TARGET_MPV_CONF, path, name);
    }

    public static void applyFile(String target, String path, String name) throws IOException {
        if (TextUtils.isEmpty(path)) throw new IOException(App.get().getString(R.string.mpv_config_empty));
        File source = Path.local(path);
        if (!source.isFile()) source = new File(path);
        if (!source.isFile() || source.length() == 0) throw new IOException(App.get().getString(R.string.mpv_config_file_invalid));
        byte[] data = Path.readToByte(source);
        if (data.length == 0) throw new IOException(App.get().getString(R.string.mpv_config_file_invalid));
        File out = TARGET_SCRIPTS.equals(target) ? scriptFile(path, name) : targetFile(target);
        writeBytes(out, data);
        Prefers.put(key(KEY_NAME, target), TextUtils.isEmpty(name) ? fileName(path) : name);
        Prefers.put(key(KEY_SOURCE, target), path);
        Prefers.put(key(KEY_TYPE, target), TYPE_FILE);
        addHistory(target, TYPE_FILE, TextUtils.isEmpty(name) ? fileName(path) : name, path);
    }

    public static void applyUrl(String url, String name) throws IOException {
        applyUrl(TARGET_MPV_CONF, url, name);
    }

    public static void applyUrl(String target, String url, String name) throws IOException {
        if (!isHttpUrl(url)) throw new IOException(App.get().getString(R.string.mpv_config_url_invalid));
        String text = OkHttp.string(url, 15000);
        if (TextUtils.isEmpty(text)) throw new IOException(App.get().getString(R.string.mpv_config_download_empty));
        File out = TARGET_SCRIPTS.equals(target) ? scriptFile(url, name) : targetFile(target);
        writeText(out, text);
        Prefers.put(key(KEY_NAME, target), TextUtils.isEmpty(name) ? fileName(url) : name);
        Prefers.put(key(KEY_SOURCE, target), url);
        Prefers.put(key(KEY_TYPE, target), TYPE_URL);
        addHistory(target, TYPE_URL, TextUtils.isEmpty(name) ? fileName(url) : name, url);
    }

    public static void applySource(String target, String source, String name) throws IOException {
        if (isHttpUrl(source)) applyUrl(target, source, name);
        else applyFile(target, source, name);
    }

    public static boolean hasHistory(String target) {
        return !getAvailableHistory(target).isEmpty();
    }

    public static CharSequence[] historyLabels(String target) {
        List<History> items = getAvailableHistory(target);
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = label(items.get(i));
        return labels;
    }

    public static void applyHistory(String target, int index) throws IOException {
        List<History> items = getAvailableHistory(target);
        if (index < 0 || index >= items.size()) throw new IOException(App.get().getString(R.string.mpv_config_history_empty));
        History item = items.get(index);
        if (TYPE_FILE.equals(item.type)) applyFile(target, item.source, item.name);
        else if (TYPE_URL.equals(item.type)) applyUrl(target, item.source, item.name);
    }

    public static boolean removeHistory(String target, int index) {
        List<History> items = getHistory(target);
        List<History> available = getAvailableHistory(target, items);
        if (index < 0 || index >= available.size()) return false;
        items.remove(available.get(index));
        Prefers.put(key(KEY_HISTORY, target), App.gson().toJson(items));
        return true;
    }

    private static void putDefault(String target) {
        Prefers.put(key(KEY_NAME, target), "");
        Prefers.put(key(KEY_SOURCE, target), "");
        Prefers.put(key(KEY_TYPE, target), TYPE_DEFAULT);
    }

    private static void writeText(String text) {
        writeText(configFile(), text);
    }

    private static void writeText(File file, String text) {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(byte[] data) {
        writeBytes(configFile(), data);
    }

    private static void writeBytes(File file, byte[] data) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
        } catch (IOException ignored) {
        }
    }

    private static String fileName(String value) {
        if (TextUtils.isEmpty(value)) return "";
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
        return TextUtils.isEmpty(name) ? CONFIG_FILE : name;
    }

    private static boolean isHttpUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        return value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private static File scriptFile(String source, String name) {
        String file = TextUtils.isEmpty(name) ? fileName(source) : name;
        if (!file.endsWith(".lua") && !file.endsWith(".js")) file = file + ".lua";
        return new File(scriptsDir(), file);
    }

    private static List<History> getHistory(String target) {
        try {
            List<History> items = App.gson().fromJson(Prefers.getString(key(KEY_HISTORY, target), "[]"), HISTORY_LIST);
            return items == null ? new ArrayList<>() : items;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<History> getAvailableHistory(String target) {
        return getAvailableHistory(target, getHistory(target));
    }

    private static List<History> getAvailableHistory(String target, List<History> items) {
        List<History> available = new ArrayList<>();
        String currentType = Prefers.getString(key(KEY_TYPE, target), TYPE_DEFAULT);
        String currentSource = getSource(target);
        for (History item : items) {
            boolean current = TextUtils.equals(item.type, currentType) && TextUtils.equals(item.source, currentSource);
            if (!current) available.add(item);
        }
        return available;
    }

    private static void addHistory(String target, String type, String name, String source) {
        if (TextUtils.isEmpty(source)) return;
        List<History> items = getHistory(target);
        for (int i = items.size() - 1; i >= 0; i--) {
            History item = items.get(i);
            if (TextUtils.equals(item.type, type) && TextUtils.equals(item.source, source)) items.remove(i);
        }
        History item = new History();
        item.type = type;
        item.name = name;
        item.source = source;
        item.time = System.currentTimeMillis();
        items.add(0, item);
        while (items.size() > 20) items.remove(items.size() - 1);
        Prefers.put(key(KEY_HISTORY, target), App.gson().toJson(items));
    }

    private static String label(History item) {
        String name = TextUtils.isEmpty(item.name) ? fileName(item.source) : item.name;
        String type = TYPE_FILE.equals(item.type) ? ResUtil.getString(R.string.mpv_config_local) : ResUtil.getString(R.string.mpv_config_url);
        return name + " · " + type;
    }

    private static String scriptsSummary() {
        File[] files = scriptsDir().listFiles(file -> file.isFile() && (file.getName().endsWith(".lua") || file.getName().endsWith(".js")));
        int count = files == null ? 0 : files.length;
        if (count <= 0) return ResUtil.getString(R.string.mpv_config_default);
        return ResUtil.getString(R.string.mpv_config_scripts_count, count);
    }

    private static void clearScripts() {
        File[] files = scriptsDir().listFiles(file -> file.isFile() && (file.getName().endsWith(".lua") || file.getName().endsWith(".js")));
        if (files == null) return;
        for (File file : files) file.delete();
    }

    private static String key(String base, String target) {
        if (TARGET_MPV_CONF.equals(target)) return base;
        return base + "_" + target.replace('.', '_').replace('/', '_');
    }

    private static void ensureSubDir(String name) {
        File dir = new File(App.get().getFilesDir(), CONFIG_DIR + File.separator + name);
        if (!dir.exists()) dir.mkdirs();
    }

    private static String defaultConfig() {
        return "# WebHTV MPV default config\n"
                + "# Loaded by libmpv from files/mpv/mpv.conf. Keep Android-only output options in app code.\n"
                + "\n"
                + "profile=fast\n"
                + "hls-bitrate=max\n"
                + "cache=yes\n"
                + "cache-secs=20\n"
                + "cache-pause=yes\n"
                + "cache-pause-initial=no\n"
                + "demuxer-thread=yes\n"
                + "demuxer-seekable-cache=auto\n"
                + "demuxer-max-bytes=64MiB\n"
                + "demuxer-max-back-bytes=64MiB\n"
                + "demuxer-readahead-secs=20\n"
                + "http-allow-redirect=yes\n"
                + "sub-ass=yes\n"
                + "sub-ass-override=yes\n"
                + "embeddedfonts=yes\n"
                + "sub-fix-timing=yes\n"
                + "sub-use-margins=yes\n"
                + "sub-font-provider=none\n"
                + "volume-max=100\n";
    }

    private static final class History {
        String type;
        String name;
        String source;
        long time;
    }
}
