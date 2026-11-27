package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.LanClient;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.function.BooleanSupplier;

import javax.net.ssl.SSLException;

import okhttp3.Response;

public final class LanRemotePairDialog {

    private LanRemotePairDialog() {
    }

    static Device snapshot(Device device) {
        Device target = new Device();
        target.setUuid(device.getUuid());
        target.setIp(device.getIp());
        target.setName(device.getName());
        return target;
    }

    static void withPairing(FragmentActivity activity, Device target, BooleanSupplier active, Runnable onReady, Runnable onCanceled) {
        if (target.getIp().isEmpty() || target.getUuid().isEmpty()) {
            Notify.show(R.string.device_offline);
            onCanceled.run();
            return;
        }
        Task.execute(() -> {
            boolean paired = false;
            boolean failed = false;
            try {
                paired = LanClient.paired(target.getUuid());
            } catch (RuntimeException e) {
                failed = true;
            }
            boolean ready = paired;
            boolean unavailable = failed;
            App.post(() -> {
                if (!active.getAsBoolean()) return;
                if (!visible(activity) || unavailable) {
                    if (unavailable && visible(activity)) Notify.show(R.string.lan_pairing_storage_error);
                    onCanceled.run();
                } else if (ready) onReady.run();
                else show(activity, target, () -> {
                    if (active.getAsBoolean()) onReady.run();
                }, onCanceled);
            });
        });
    }

    public static void show(FragmentActivity activity, Device device, Runnable onPaired) {
        show(activity, snapshot(device), onPaired, () -> {});
    }

    private static void show(FragmentActivity activity, Device target, Runnable onPaired, Runnable onCanceled) {
        if (!visible(activity)) {
            onCanceled.run();
            return;
        }
        if (target.getIp().isEmpty() || target.getUuid().isEmpty()) {
            Notify.show(R.string.device_offline);
            onCanceled.run();
            return;
        }
        WebHtvAlertDialogBuilder builder = new WebHtvAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_Dialog);
        Context context = builder.getContext();
        LinearLayout content = content(context);
        TextView status = text(content, false);
        TextView fingerprint = text(content, true);
        fingerprint.setVisibility(View.GONE);
        MaterialCheckBox confirm = new MaterialCheckBox(context);
        confirm.setText(R.string.lan_pairing_verify);
        confirm.setVisibility(View.GONE);
        content.addView(confirm);
        TextInputLayout input = new TextInputLayout(context);
        input.setHint(context.getString(R.string.lan_pairing_input));
        TextInputEditText code = new TextInputEditText(input.getContext());
        code.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        code.setSaveEnabled(false);
        input.addView(code);
        input.setVisibility(View.GONE);
        content.addView(input);

        String label = target.getName().isEmpty() ? target.getUuid() : target.getName();
        AlertDialog dialog = builder.setTitle(context.getString(R.string.lan_pairing_remote_title, label))
                .setView(scroll(content)).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.lan_pairing_submit, null).create();
        boolean[] paired = {false};
        if (!open(activity, dialog, () -> { if (!paired[0]) onCanceled.run(); })) {
            onCanceled.run();
            return;
        }
        var action = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Runnable probe = () -> {
            status.setText(R.string.lan_pairing_probing);
            action.setEnabled(false);
            Task.execute(() -> {
                String provisional;
                try {
                    provisional = LanClient.discoverFingerprint(target.getIp());
                } catch (IOException | RuntimeException e) {
                    provisional = null;
                }
                String candidate = provisional;
                App.post(() -> {
                    if (!dialog.isShowing() || !visible(activity)) return;
                    if (candidate == null || candidate.isEmpty()) {
                        status.setText(R.string.lan_pairing_probe_failed);
                        action.setText(R.string.lan_pairing_retry);
                        action.setEnabled(true);
                        return;
                    }
                    status.setText(R.string.lan_pairing_verify_prompt);
                    fingerprint.setText(context.getString(R.string.lan_pairing_fingerprint, grouped(candidate)));
                    fingerprint.setVisibility(View.VISIBLE);
                    confirm.setVisibility(View.VISIBLE);
                    action.setText(R.string.lan_pairing_submit);
                    confirm.setOnCheckedChangeListener((button, checked) -> {
                        input.setVisibility(checked ? View.VISIBLE : View.GONE);
                        if (!checked) code.setText(null);
                        action.setEnabled(checked);
                    });
                    action.setOnClickListener(view -> {
                        if (!confirm.isChecked() || !visible(activity)) return;
                        String value = code.getText() == null ? "" : code.getText().toString();
                        if (!value.matches("[0-9]{8}")) {
                            input.setError(context.getString(R.string.lan_pairing_input));
                            code.requestFocus();
                            return;
                        }
                        input.setError(null);
                        code.setText(null);
                        confirm.setEnabled(false);
                        code.setEnabled(false);
                        action.setEnabled(false);
                        status.setText(R.string.lan_pairing_submitting);
                        Task.execute(() -> {
                            boolean success = false;
                            try {
                                success = LanClient.pair(target.getIp(), target.getUuid(), candidate, value).success();
                            } catch (Exception ignored) {
                                // Only fixed local messages are rendered, never a raw response.
                            }
                            boolean accepted = success;
                            App.post(() -> {
                                if (!dialog.isShowing() || !visible(activity)) return;
                                if (accepted) {
                                    paired[0] = true;
                                    dialog.dismiss();
                                    onPaired.run();
                                } else {
                                    status.setText(R.string.lan_pairing_remote_failed);
                                    confirm.setEnabled(true);
                                    code.setEnabled(true);
                                    action.setEnabled(true);
                                }
                            });
                        });
                    });
                });
            });
        };
        action.setOnClickListener(view -> probe.run());
        probe.run();
    }

    static void offerRepair(FragmentActivity activity, Device target, int reason) {
        if (!visible(activity)) return;
        AlertDialog dialog = new WebHtvAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_Dialog)
                .setTitle(R.string.lan_pairing_entry).setMessage(reason)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.lan_pairing_repair, (popup, which) ->
                        show(activity, target, () -> Notify.show(R.string.lan_pairing_send_again))).create();
        open(activity, dialog, null);
    }

    static boolean repairable(int reason) {
        return reason == R.string.lan_pairing_required || reason == R.string.lan_pairing_denied;
    }

    static int connectionError(IOException error) {
        return error instanceof SSLException ? R.string.lan_pairing_required : R.string.lan_pairing_send_failed;
    }

    static boolean reciprocalPairingRequired(Response response) {
        try {
            return response.code() == 403 && "Reciprocal LAN pairing required".equals(response.peekBody(96).string());
        } catch (IOException e) {
            return false;
        }
    }

    static boolean denied(int code) {
        return code == 401 || code == 403;
    }

    static boolean visible(FragmentActivity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    static boolean open(LifecycleOwner owner, AlertDialog dialog, Runnable dismissed) {
        Lifecycle lifecycle = owner.getLifecycle();
        if (!lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return false;
        DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
            @Override
            public void onStop(LifecycleOwner owner) {
                dialog.dismiss();
            }
        };
        dialog.setOnDismissListener(ignored -> {
            lifecycle.removeObserver(observer);
            if (dismissed != null) dismissed.run();
        });
        lifecycle.addObserver(observer);
        dialog.show();
        return true;
    }

    static LinearLayout content(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int margin = ResUtil.dp2px(24);
        layout.setPadding(margin, ResUtil.dp2px(12), margin, ResUtil.dp2px(12));
        return layout;
    }

    static ScrollView scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(content.getContext());
        scroll.addView(content);
        return scroll;
    }

    static TextView text(LinearLayout content, boolean selectable) {
        TextView text = new TextView(content.getContext());
        text.setTextSize(16);
        text.setTextIsSelectable(selectable);
        text.setPadding(0, ResUtil.dp2px(6), 0, ResUtil.dp2px(6));
        content.addView(text);
        return text;
    }

    static String grouped(String fingerprint) {
        return fingerprint.replaceAll("(?i)([0-9a-f]{8})(?=[0-9a-f])", "$1 ");
    }
}
