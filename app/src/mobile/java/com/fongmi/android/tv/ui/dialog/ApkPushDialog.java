package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.server.LanClient;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.adapter.SyncDeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ProgressRequestBody;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.Future;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApkPushDialog extends BaseBottomSheetDialog implements SyncDeviceAdapter.OnClickListener, ScanTask.Listener, NsdDeviceDiscovery.Listener {

    private static final String TAG_DISCOVERY = "apk_push_discovery";
    private static final String TAG_TRANSFER = "apk_push_transfer";
    private static final String PART_NAME = "apk";
    private static final int MAX_RETRY = 2;
    private static final MediaType APK = MediaType.parse("application/vnd.android.package-archive");

    private final OkHttpClient client = OkHttp.client(Constant.TIMEOUT_SYNC_TRANSFER);
    private final NsdDeviceDiscovery discovery = new NsdDeviceDiscovery(this);
    private final Device target;
    private final Uri uri;

    private DialogDeviceBinding binding;
    private SyncDeviceAdapter adapter;
    private ScanTask scanTask = new ScanTask(this);
    private Call uploadCall;
    private File apk;
    private Future<?> preparation;
    private volatile boolean pushing;
    private long lastProgressUpdate;
    private Listener listener;

    private ApkPushDialog(Device target, Uri uri) {
        this.target = target;
        this.uri = uri;
    }

    public static ApkPushDialog create() {
        return new ApkPushDialog(null, null);
    }

    public static ApkPushDialog create(Device target, Uri uri) {
        return new ApkPushDialog(target, uri);
    }

    public ApkPushDialog listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        String tag = isDiscoveryMode() ? TAG_DISCOVERY : TAG_TRANSFER;
        if (activity.getSupportFragmentManager().findFragmentByTag(tag) == null) show(activity.getSupportFragmentManager(), tag);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Server.get().start();
        binding.scan.setVisibility(View.GONE);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter = new SyncDeviceAdapter(this));
        if (isDiscoveryMode()) {
            binding.title.setText(R.string.apk_push_select_device);
            refresh();
        } else {
            binding.title.setText(R.string.apk_push_preparing);
            binding.refresh.setVisibility(View.GONE);
            DialogDeviceBinding current = binding;
            Device device = LanRemotePairDialog.snapshot(target);
            App.post(() -> {
                if (binding != current || !isAdded()) return;
                LanRemotePairDialog.withPairing(requireActivity(), device, () -> binding == current,
                        () -> prepare(device, current), () -> { if (binding == current) dismissAllowingStateLoss(); });
            });
        }
    }

    @Override
    protected void initEvent() {
        binding.refresh.setOnClickListener(view -> refresh());
    }

    private void prepare(Device device, DialogDeviceBinding current) {
        Context context = requireContext().getApplicationContext();
        preparation = Task.submit(() -> {
            File temp = null;
            try {
                String name = getDisplayName(context);
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    App.post(() -> fail(current, device, R.string.apk_push_invalid));
                    return;
                }
                name = sanitize(name);
                temp = Path.cache("apk-push-" + System.currentTimeMillis() + ".apk");
                copyUri(context, temp);
                if (context.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(), 0) == null) {
                    App.post(() -> fail(current, device, R.string.apk_push_invalid));
                    return;
                }
                String digest = sha256(temp);
                if (Thread.currentThread().isInterrupted()) return;
                File file = temp;
                String fileName = name;
                App.post(() -> {
                    if (binding != current) {
                        Path.clear(file);
                        return;
                    }
                    apk = file;
                    binding.title.setText(getString(R.string.apk_push_ready, fileName, FileUtil.byteCountToDisplaySize(file.length())));
                    pushing = true;
                    upload(current, device, file, fileName, digest, 0);
                });
                temp = null;
            } catch (ApkPreparationException e) {
                App.post(() -> fail(current, device, e.message));
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) App.post(() -> fail(current, device, R.string.apk_push_read_failed));
            } finally {
                if (temp != null) Path.clear(temp);
            }
        });
    }

    private String getDisplayName(Context context) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private String sanitize(String name) {
        String result = name.replace('\\', '_').replace('/', '_').replace('\u0000', '_').trim();
        return result.isEmpty() ? "app.apk" : result;
    }

    private void copyUri(Context context, File target) throws Exception {
        long available = FileUtil.getAvailableStorageSpace(Path.cache());
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new ApkPreparationException(R.string.apk_push_read_failed);
            try (BufferedInputStream input = new BufferedInputStream(raw); BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(Path.create(target)))) {
                byte[] buffer = new byte[64 * 1024];
                long written = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    written += count;
                    if (available > 0 && written + 16L * 1024 * 1024 > available) throw new ApkPreparationException(R.string.apk_push_no_space);
                    output.write(buffer, 0, count);
                }
            }
        }
        if (target.length() <= 0) throw new ApkPreparationException(R.string.apk_push_read_failed);
    }

    private void refresh() {
        if (pushing || !isDiscoveryMode() || binding == null) return;
        discovery.stop();
        scanTask.stop();
        scanTask = new ScanTask(this);
        adapter.clear(() -> {
            if (binding == null) return;
            binding.recycler.setVisibility(View.GONE);
            discovery.start();
            scanTask.start();
        });
    }

    @Override
    public void onServiceFound(String url) {
        scanTask.start(url);
    }

    @Override
    public void onFind(Device device) {
        if (binding == null || device == null || !device.isApp() || Device.get().equals(device)) return;
        adapter.sort(device, () -> { if (binding != null) binding.recycler.setVisibility(View.VISIBLE); });
    }

    @Override
    public void onLost(Device device) {
        if (binding == null) return;
        adapter.remove(device, () -> {
            if (binding != null) binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onFinish() {
    }

    @Override
    public void onItemClick(Device device) {
        if (pushing || !isDiscoveryMode()) return;
        stopScan();
        Listener callback = listener;
        dismissAllowingStateLoss();
        if (callback != null) callback.onSelected(device);
    }

    private void upload(DialogDeviceBinding current, Device device, File file, String fileName, String sha256, int retry) {
        Task.execute(() -> {
            RequestBody fileBody = new ProgressRequestBody(file, APK, (written, total) -> onProgress(current, written, total));
            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("device", Device.get().toString())
                    .addFormDataPart("name", fileName)
                    .addFormDataPart("size", String.valueOf(file.length()))
                    .addFormDataPart("sha256", sha256)
                    .addFormDataPart(PART_NAME, fileName, fileBody)
                    .build();
            Call pending;
            try {
                pending = LanClient.newCall(client, device.getIp() + "/action?do=apk", device.getUuid(), body);
            } catch (IOException e) {
                App.post(() -> fail(current, device, R.string.lan_pairing_required));
                return;
            }
            App.post(() -> {
                if (binding != current || !pushing) {
                    pending.cancel();
                    return;
                }
                uploadCall = pending;
                pending.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (!call.isCanceled()) finish(LanRemotePairDialog.connectionError(e));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        int message;
                        try (Response res = response) {
                            if (LanRemotePairDialog.denied(res.code())) message = R.string.lan_pairing_denied;
                            else if (!res.isSuccessful()) message = R.string.lan_pairing_send_failed;
                            else message = res.body() != null && "APK received".equals(res.body().string().trim())
                                    ? 0 : R.string.apk_push_receiver_unsupported;
                        } catch (IOException e) {
                            message = R.string.lan_pairing_send_failed;
                        }
                        if (!call.isCanceled()) finish(message);
                    }

                    private void finish(int message) {
                        App.post(() -> {
                            if (binding != current || !pushing) return;
                            if (message == 0) {
                                pushing = false;
                                Notify.show(R.string.apk_push_success);
                                dismissAllowingStateLoss();
                            } else if (!LanRemotePairDialog.repairable(message) && retry < MAX_RETRY) {
                                App.post(() -> {
                                    if (binding == current && pushing) upload(current, device, file, fileName, sha256, retry + 1);
                                }, 600);
                            } else fail(current, device, message);
                        });
                    }
                });
            });
        });
    }

    private void onProgress(DialogDeviceBinding current, long written, long total) {
        long now = System.currentTimeMillis();
        if (now - lastProgressUpdate < 250 && written < total) return;
        lastProgressUpdate = now;
        int percent = total <= 0 ? 0 : (int) Math.min(100, written * 100 / total);
        App.post(() -> {
            if (binding == current && pushing) binding.title.setText(getString(R.string.apk_push_progress, percent, FileUtil.byteCountToDisplaySize(written), FileUtil.byteCountToDisplaySize(total)));
        });
    }

    private void fail(DialogDeviceBinding current, Device device, int message) {
        if (binding != current) return;
        pushing = false;
        if (LanRemotePairDialog.repairable(message)) LanRemotePairDialog.offerRepair(getActivity(), device, message);
        else Notify.show(getString(R.string.apk_push_failed, getString(message)));
        dismissAllowingStateLoss();
    }

    private static final class ApkPreparationException extends IOException {
        final int message;

        ApkPreparationException(int message) {
            this.message = message;
        }
    }

    private boolean isDiscoveryMode() {
        return target == null || uri == null;
    }

    private void stopScan() {
        discovery.stop();
        scanTask.stop();
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    @Override
    public void onDestroyView() {
        pushing = false;
        if (preparation != null) preparation.cancel(true);
        if (uploadCall != null) uploadCall.cancel();
        stopScan();
        if (apk != null) Path.clear(apk);
        binding = null;
        super.onDestroyView();
    }

    public interface Listener {

        void onSelected(Device device);
    }
}
