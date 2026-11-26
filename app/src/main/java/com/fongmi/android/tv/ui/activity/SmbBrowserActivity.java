package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySmbBrowserBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.smb.SmbClient;
import com.fongmi.android.tv.smb.SmbCredential;
import com.fongmi.android.tv.smb.SmbCredentialStore;
import com.fongmi.android.tv.smb.SmbPath;
import com.fongmi.android.tv.smb.SmbUriResolver;
import com.github.catvod.utils.Prefers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMB 浏览入口（实验能力，默认关闭）：连接一台 NAS 的某个共享，列目录，点文件起播。
 *
 * <p>范围与 {@code docs/FORK-4-smb-browser-decision.md} 的 P1 一致：只读浏览 + 播放 + 凭据加密保存；
 * 不做共享枚举、删除、复制、MPV 代理。凭据只以密文进 {@link SmbCredentialStore}，
 * 历史与日志里只出现无凭据形态的 URI（见 {@link SmbUriResolver#stripCredentials(String)}）。
 */
public class SmbBrowserActivity extends AppCompatActivity {

    private static final long TIMEOUT_MS = 8000L;

    public static void start(Activity activity) {
        if (activity == null) return;
        activity.startActivity(new Intent(activity, SmbBrowserActivity.class));
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<SmbClient.Entry> entries = new ArrayList<>();

    private ActivitySmbBrowserBinding binding;
    @Nullable
    private SmbClient client;
    private String host = "";
    private int port = 445;
    private String share = "";
    /** 本次连接实际使用的凭据：未勾选“记住账号”时播放也必须用它，否则会退回 guest。 */
    private SmbCredential credential;
    private String dir = "";
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Setting.isSmbBrowserEnabled()) {
            // 入口默认关闭：即使被外部 Intent 拉起也不执行任何网络动作。
            finish();
            return;
        }
        binding = ActivitySmbBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle(R.string.smb_browser_title);
        binding.list.setAdapter(new RowAdapter());
        binding.host.setText(Prefers.getString("smb_last_host"));
        binding.share.setText(Prefers.getString("smb_last_share"));
        binding.user.setText(Prefers.getString("smb_last_user"));
        binding.connect.setOnClickListener(view -> connect());
        binding.up.setOnClickListener(view -> goUp());
        binding.list.setOnItemClickListener((parent, view, position, id) -> onRowClicked(position));
        status(getString(R.string.smb_root));
    }

    private void connect() {
        if (busy) return;
        String nextHost = text(binding.host);
        String nextShare = text(binding.share);
        if (nextHost.isEmpty() || nextShare.isEmpty()) {
            status(getString(R.string.smb_need_host_share));
            return;
        }
        String user = text(binding.user);
        String password = binding.password.getText() == null ? "" : binding.password.getText().toString();
        String nextSharePath = SmbPath.normalize(nextShare);
        if (nextSharePath == null || nextSharePath.isEmpty()) {
            status(getString(R.string.smb_need_host_share));
            return;
        }
        SmbUriResolver.Endpoint endpoint = SmbUriResolver.endpoint(nextHost);
        if (endpoint == null) {
            status(getString(R.string.smb_bad_host));
            return;
        }
        host = endpoint.host;
        port = endpoint.port;
        share = SmbPath.share(nextSharePath);
        dir = "";
        Prefers.put("smb_last_host", nextHost);
        Prefers.put("smb_last_share", share);
        Prefers.put("smb_last_user", user);
        credential = user.isEmpty() ? null : SmbCredential.of(user, password, "");
        boolean remember = binding.remember.isChecked() && credential != null;
        setBusy(true);
        status(getString(R.string.smb_connecting));
        worker.execute(() -> {
            List<SmbClient.Entry> result = null;
            String error = null;
            try {
                closeClient();
                client = SmbClient.connect(host, port, credential, TIMEOUT_MS);
                result = client.listDir(share, dir, TIMEOUT_MS);
            } catch (IOException e) {
                error = message(e);
            }
            if (remember && error == null) saveCredential(credential);
            final List<SmbClient.Entry> loaded = result;
            final String failure = error;
            runOnUiThread(() -> {
                setBusy(false);
                if (failure != null) {
                    status(failure);
                    return;
                }
                entries.clear();
                if (loaded != null) entries.addAll(loaded);
                notifyRowsChanged();
                status(entries.isEmpty() ? getString(R.string.smb_empty_dir) : location());
            });
        });
    }

    private void goUp() {
        if (busy || client == null) return;
        String parent = SmbPath.parent(dir);
        if (parent.equals(dir)) return;
        dir = parent;
        refresh();
    }

    private void refresh() {
        setBusy(true);
        worker.execute(() -> {
            List<SmbClient.Entry> result = null;
            String error = null;
            try {
                result = client == null ? null : client.listDir(share, dir, TIMEOUT_MS);
            } catch (IOException e) {
                error = message(e);
            }
            final List<SmbClient.Entry> loaded = result;
            final String failure = error;
            runOnUiThread(() -> {
                setBusy(false);
                if (failure != null) {
                    status(failure);
                    return;
                }
                entries.clear();
                if (loaded != null) entries.addAll(loaded);
                notifyRowsChanged();
                status(entries.isEmpty() ? getString(R.string.smb_empty_dir) : location());
            });
        });
    }

    private void onRowClicked(int position) {
        if (position < 0 || position >= entries.size()) return;
        SmbClient.Entry entry = entries.get(position);
        if (entry.directory) {
            dir = entry.path;
            refresh();
            return;
        }
        play(entry);
    }

    private void play(SmbClient.Entry entry) {
        String storage = SmbUriResolver.storageUri(host, port, share, entry.path);
        if (storage == null) {
            toast(getString(R.string.smb_play_failed, entry.name));
            return;
        }
        SmbCredential auth = credential != null ? credential : SmbCredentialStore.get(SmbUriResolver.hostKey(host, share));
        String playback = SmbUriResolver.playbackUri(storage, auth == null ? "" : auth.username,
                auth == null ? "" : auth.password, "");
        if (playback == null) {
            toast(getString(R.string.smb_play_failed, entry.name));
            return;
        }
        // 只把带凭据的 URI 交给播放器，不落库、不打日志。
        VideoActivity.start(this, playback);
    }

    private void saveCredential(SmbCredential credential) {
        String key = SmbUriResolver.hostKey(host, share);
        try {
            SmbCredentialStore.put(key, credential);
            runOnUiThread(() -> toast(getString(R.string.smb_credential_saved)));
        } catch (Exception e) {
            runOnUiThread(() -> toast(getString(R.string.smb_credential_failed, message(e))));
        }
    }

    private void notifyRowsChanged() {
        if (binding.list.getAdapter() instanceof BaseAdapter) ((BaseAdapter) binding.list.getAdapter()).notifyDataSetChanged();
    }

    private String location() {
        return share + (dir.isEmpty() ? "/" : "/" + dir);
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.connect.setEnabled(!value);
        binding.up.setEnabled(!value);
    }

    private void status(String text) {
        binding.status.setText(text);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static String text(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return TextUtils.isEmpty(message) ? error.getClass().getSimpleName() : message;
    }

    private void closeClient() {
        if (client == null) return;
        client.close();
        client = null;
    }

    @Override
    protected void onDestroy() {
        closeClient();
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class RowAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public SmbClient.Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = convertView instanceof TextView ? (TextView) convertView : newRow();
            SmbClient.Entry entry = getItem(position);
            view.setText(entry.directory
                    ? getString(R.string.smb_folder_row, entry.name)
                    : getString(R.string.smb_file_row, entry.name, humanSize(entry.size)));
            return view;
        }

        private TextView newRow() {
            TextView view = new TextView(SmbBrowserActivity.this);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(8, 24, 8, 24);
            view.setTextColor(getColor(R.color.white));
            view.setTextSize(16f);
            return view;
        }
    }

    static String humanSize(long bytes) {
        if (bytes <= 0L) return "";
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        for (String unit : units) {
            value /= 1024d;
            if (value < 1024d) return String.format(Locale.ROOT, "%.1f %s", value, unit);
        }
        return String.format(Locale.ROOT, "%.1f PB", value / 1024d);
    }
}
