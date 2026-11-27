package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
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
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.LanClient;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class SyncDialog extends BaseBottomSheetDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener {

    private final FormBody.Builder body;
    private final OkHttpClient client;
    private final TypedArray mode;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private String type;
    private Call syncCall;
    private boolean sending;

    public SyncDialog() {
        body = new FormBody.Builder();
        scanTask = new ScanTask(this);
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
        mode = ResUtil.getTypedArray(R.array.cast_mode);
    }

    public static SyncDialog create() {
        return new SyncDialog();
    }

    public SyncDialog history() {
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        body.add("targets", App.gson().toJson(History.get()));
        return type("history");
    }

    public SyncDialog keep() {
        body.add("device", Device.get().toString());
        body.add("targets", App.gson().toJson(Keep.getVod()));
        body.add("configs", App.gson().toJson(Config.findUrls()));
        return type("keep");
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SyncDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private SyncDialog type(String type) {
        this.type = type;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.mode.setVisibility(View.VISIBLE);
        setRecyclerView();
        getDevice();
        setMode();
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(v -> onMode());
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void getDevice() {
        adapter.setItems(Device.getAll(), () -> {
            if (binding == null) return;
            if (adapter.getItemCount() == 0) onRefresh();
            else binding.recycler.setVisibility(View.VISIBLE);
        });
    }

    private void setMode() {
        int index = Setting.getSyncMode();
        binding.mode.setImageResource(mode.getResourceId(index, 0));
        binding.mode.setTag(String.valueOf(index));
    }

    private void onMode() {
        int index = Setting.getSyncMode();
        Setting.putSyncMode(index = index == mode.length() - 1 ? 0 : ++index);
        binding.mode.setImageResource(mode.getResourceId(index, 0));
        binding.mode.setTag(String.valueOf(index));
    }

    private void onScan() {
        launcher.launch(new Intent(requireActivity(), ScanActivity.class));
    }

    private void onRefresh() {
        adapter.clear(() -> {
            if (binding == null) return;
            Device.delete();
            scanTask.start();
            binding.recycler.setVisibility(View.GONE);
        });
    }

    private void onSuccess() {
        dismiss();
    }

    @Override
    public void onFind(Device device) {
        if (binding == null) return;
        binding.recycler.setVisibility(View.VISIBLE);
        adapter.sort(device);
    }

    @Override
    public void onItemClick(Device item) {
        if (binding != null) send(item, binding.mode.getTag().toString(), false);
    }

    @Override
    public boolean onLongClick(Device item) {
        if (binding == null) return false;
        String mode = binding.mode.getTag().toString();
        if (mode.equals("0")) return false;
        send(item, mode, true);
        return true;
    }

    private void send(Device item, String mode, boolean force) {
        if (sending) return;
        Device target = LanRemotePairDialog.snapshot(item);
        String url = String.format(Locale.ROOT, "%s/action?do=sync&mode=%s&type=%s%s",
                target.getIp(), mode, type, force ? "&force=true" : "");
        DialogDeviceBinding current = binding;
        FormBody payload = body.build();
        sending = true;
        LanRemotePairDialog.withPairing(requireActivity(), target, () -> binding == current,
                () -> send(target, url, payload, current, !mode.equals("1")),
                () -> { if (binding == current) sending = false; });
    }

    private void send(Device target, String url, FormBody payload, DialogDeviceBinding current, boolean reverse) {
        Task.execute(() -> {
            Call pending;
            try {
                if (reverse) Server.get().start();
                pending = LanClient.newCall(client, url, target.getUuid(), payload);
            } catch (IOException e) {
                App.post(() -> finish(current, target, R.string.lan_pairing_required));
                return;
            }
            App.post(() -> {
                if (binding != current) {
                    pending.cancel();
                    return;
                }
                syncCall = pending;
                pending.enqueue(new Callback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        try (Response res = response) {
                            int message;
                            if (res.isSuccessful()) message = 0;
                            else if (reverse && LanRemotePairDialog.reciprocalPairingRequired(res)) message = R.string.lan_pairing_reverse_sync;
                            else if (LanRemotePairDialog.denied(res.code())) message = R.string.lan_pairing_denied;
                            else message = reverse ? R.string.lan_pairing_reverse_sync : R.string.sync_failed;
                            if (!call.isCanceled()) App.post(() -> finish(current, target, message));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (!call.isCanceled()) App.post(() -> finish(current, target, LanRemotePairDialog.connectionError(e)));
                    }
                });
            });
        });
    }

    private void finish(DialogDeviceBinding current, Device target, int message) {
        if (binding != current) return;
        sending = false;
        if (message == 0) onSuccess();
        else if (LanRemotePairDialog.repairable(message)) LanRemotePairDialog.offerRepair(getActivity(), target, message);
        else Notify.show(message);
    }

    @Override
    public void onDestroyView() {
        if (syncCall != null) syncCall.cancel();
        sending = false;
        super.onDestroyView();
        scanTask.stop();
        binding = null;
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) scanTask.start(result.getData().getStringExtra("address"));
    });
}
