package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.databinding.DialogRemoteTrustTextCommandBinding;
import com.fongmi.android.tv.server.LanClient;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Response;

public class ApkPushUrlDialog extends BaseAlertDialog {

    private static final String ACK = "APK URL accepted";
    private static final String TAG = "apk_push_url";

    private final okhttp3.OkHttpClient client = OkHttp.client(Constant.TIMEOUT_SYNC);
    private final Device device;
    private DialogRemoteTrustTextCommandBinding binding;
    private Call call;

    private ApkPushUrlDialog(Device device) {
        this.device = device;
    }

    public static ApkPushUrlDialog create(Device device) {
        return new ApkPushUrlDialog(device);
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) == null) show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogRemoteTrustTextCommandBinding.inflate(LayoutInflater.from(requireActivity()));
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.title.setText(getString(R.string.apk_push_url_target, device == null ? "" : device.getName()));
        binding.inputLayout.setHint(R.string.apk_push_url_hint);
        binding.positive.setText(R.string.apk_push_url_send);
        CharSequence clip = Util.getClipText();
        if (!TextUtils.isEmpty(clip)) {
            String url = Sniffer.getUrl(clip.toString());
            if (isValid(url)) binding.input.setText(url);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.input.requestFocus();
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(view -> dismiss());
        binding.positive.setOnClickListener(view -> send());
        binding.input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            send();
            return true;
        });
    }

    private void send() {
        if (binding == null || !binding.positive.isEnabled()) return;
        String url = binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Notify.show(R.string.apk_push_url_required);
            binding.input.requestFocus();
            return;
        }
        if (!isValid(url)) {
            Notify.show(R.string.apk_push_url_invalid);
            binding.input.requestFocus();
            return;
        }
        if (device == null || TextUtils.isEmpty(device.getIp())) {
            Notify.show(R.string.device_offline);
            dismissAllowingStateLoss();
            return;
        }
        Device target = LanRemotePairDialog.snapshot(device);
        var current = binding;
        binding.positive.setEnabled(false);
        LanRemotePairDialog.withPairing(requireActivity(), target, () -> binding == current,
                () -> send(target, url, current), () -> {
                    if (binding == current) binding.positive.setEnabled(true);
                });
    }

    private void send(Device target, String url, DialogRemoteTrustTextCommandBinding current) {
        Task.execute(() -> {
            Call pending;
            try {
                pending = LanClient.newCall(client, target.getIp().concat("/action?do=apk_url"), target.getUuid(),
                        new FormBody.Builder().add("device", Device.get().toString()).add("url", url).build());
            } catch (IOException e) {
                App.post(() -> {
                    if (binding == current) failed(target, R.string.lan_pairing_required);
                });
                return;
            }
            App.post(() -> {
                if (binding != current) {
                    pending.cancel();
                    return;
                }
                call = pending;
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (call.isCanceled()) return;
                        App.post(() -> {
                            if (binding == current) failed(target, LanRemotePairDialog.connectionError(e));
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        int result;
                        try (Response res = response) {
                            if (LanRemotePairDialog.denied(res.code())) result = R.string.lan_pairing_denied;
                            else if (!res.isSuccessful()) result = R.string.lan_pairing_send_failed;
                            else if (ACK.equals(res.body() == null ? "" : res.body().string().trim())) result = R.string.apk_push_url_sent;
                            else result = R.string.apk_push_url_unsupported;
                        } catch (IOException e) {
                            result = R.string.lan_pairing_send_failed;
                        }
                        int message = result;
                        App.post(() -> {
                            if (binding != current || call.isCanceled()) return;
                            if (message == R.string.apk_push_url_sent) {
                                Notify.show(message);
                                dismissAllowingStateLoss();
                            } else failed(target, message);
                        });
                    }
                });
            });
        });
    }

    private void failed(Device target, int reason) {
        binding.positive.setEnabled(true);
        if (LanRemotePairDialog.repairable(reason)) LanRemotePairDialog.offerRepair(getActivity(), target, reason);
        else Notify.show(reason);
    }

    private boolean isValid(String value) {
        HttpUrl url = HttpUrl.parse(value);
        return url != null && "https".equals(url.scheme()) && url.username().isEmpty() && url.password().isEmpty();
    }

    @Override
    public void onDestroyView() {
        if (call != null) call.cancel();
        binding = null;
        super.onDestroyView();
    }
}
