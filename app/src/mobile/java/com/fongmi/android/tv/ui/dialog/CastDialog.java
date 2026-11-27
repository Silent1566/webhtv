package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.dlna.DLNACast;
import com.fongmi.android.tv.dlna.DLNACastManager;
import com.fongmi.android.tv.server.LanClient;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class CastDialog extends BaseBottomSheetDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, DLNACastManager.DeviceListener {

    private final FormBody.Builder body;
    private final OkHttpClient client;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private CastVideo video;
    private boolean historyReady;
    private boolean fm;
    private boolean sending;
    private Call castCall;

    public CastDialog() {
        scanTask = new ScanTask(this);
        body = new FormBody.Builder();
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public static CastDialog create() {
        return new CastDialog();
    }

    public CastDialog history(History history) {
        if (history == null || TextUtils.isEmpty(history.getVodId())) return this;
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/")) fd = Server.get().getAddress() + "/file" + fd.replace(Path.rootPath(), "");
        if (fd.startsWith("file")) fd = Server.get().getAddress() + "/" + fd.replace(Path.rootPath(), "").replace("://", "");
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        body.add("history", history.toString().replace(id, fd));
        historyReady = true;
        return this;
    }

    public CastDialog video(CastVideo video) {
        this.video = video;
        return this;
    }

    public CastDialog fm(boolean fm) {
        this.fm = fm;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof CastDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm ? View.VISIBLE : View.GONE);
        DLNACastManager.get().init(requireActivity());
        DLNACastManager.get().setDeviceListener(this);
        setRecyclerView();
        getDevice();
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void setRecyclerVisible() {
        if (binding == null) return;
        binding.recycler.setVisibility(adapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void getDevice() {
        adapter.setItems(Device.getAll(), () -> {
            adapter.sort(DLNACastManager.get().getRegistered(), this::setRecyclerVisible);
            if (adapter.getItemCount() == 0) onRefresh();
            else DLNACastManager.get().search();
        });
    }

    private void onScan() {
        launcher.launch(new Intent(requireActivity(), ScanActivity.class));
    }

    private void onRefresh() {
        adapter.clear(() -> {
            Device.delete();
            if (fm) scanTask.start();
            DLNACastManager.get().search();
            adapter.sort(DLNACastManager.get().getRegistered(), this::setRecyclerVisible);
        });
    }

    private void onCasted() {
        if (binding == null || !isAdded()) return;
        if (requireActivity() instanceof CastDialog.Listener listener) listener.onCasted();
        dismiss();
    }

    @Override
    public void onDeviceAdded(Device device) {
        if (binding == null) return;
        binding.recycler.setVisibility(View.VISIBLE);
        adapter.sort(device);
    }

    @Override
    public void onDeviceRemoved(Device device) {
        adapter.remove(device);
    }

    @Override
    public void onFind(Device device) {
        if (binding == null) return;
        binding.recycler.setVisibility(View.VISIBLE);
        adapter.sort(device);
    }

    @Override
    public void onItemClick(Device item) {
        if (sending || binding == null) return;
        if (item.isDLNA()) {
            if (video == null || TextUtils.isEmpty(video.url())) {
                Notify.show(R.string.cast_not_ready);
                return;
            }
            new DLNACast(video, this::onCasted).cast(item);
        } else {
            if (!historyReady) {
                Notify.show(R.string.cast_not_ready);
                return;
            }
            Device target = LanRemotePairDialog.snapshot(item);
            DialogDeviceBinding current = binding;
            FormBody payload = body.build();
            sending = true;
            LanRemotePairDialog.withPairing(requireActivity(), target, () -> binding == current,
                    () -> send(target, payload, current), () -> { if (binding == current) sending = false; });
        }
    }

    private void send(Device target, FormBody payload, DialogDeviceBinding current) {
        Task.execute(() -> {
            Call pending;
            try {
                pending = LanClient.newCall(client, target.getIp().concat("/action?do=cast"), target.getUuid(), payload);
            } catch (IOException e) {
                App.post(() -> finishCast(current, target, R.string.lan_pairing_required));
                return;
            }
            App.post(() -> {
                if (binding != current) {
                    pending.cancel();
                    return;
                }
                castCall = pending;
                pending.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (!call.isCanceled()) App.post(() -> finishCast(current, target, LanRemotePairDialog.connectionError(e)));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        int message;
                        try (Response res = response) {
                            if (LanRemotePairDialog.denied(res.code())) message = R.string.lan_pairing_denied;
                            else message = res.isSuccessful() && res.body() != null && "OK".equals(res.body().string())
                                    ? 0 : R.string.lan_pairing_send_failed;
                        } catch (IOException e) {
                            message = R.string.lan_pairing_send_failed;
                        }
                        int result = message;
                        if (!call.isCanceled()) App.post(() -> finishCast(current, target, result));
                    }
                });
            });
        });
    }

    private void finishCast(DialogDeviceBinding current, Device target, int message) {
        if (binding != current) return;
        sending = false;
        if (message == 0) onCasted();
        else if (LanRemotePairDialog.repairable(message)) LanRemotePairDialog.offerRepair(getActivity(), target, message);
        else Notify.show(message);
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    @Override
    public void onDestroyView() {
        if (castCall != null) castCall.cancel();
        sending = false;
        super.onDestroyView();
        DLNACastManager.get().setDeviceListener(null);
        DLNACastManager.get().release(requireActivity());
        scanTask.stop();
        binding = null;
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) scanTask.start(result.getData().getStringExtra("address"));
    });

    public interface Listener {

        void onCasted();
    }
}
