package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.LanPairingController;
import com.fongmi.android.tv.server.LanPeerStore;
import com.fongmi.android.tv.server.LanPeers;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LanPairingDialog {

    private LanPairingDialog() {
    }

    public static void show(Fragment fragment) {
        show(fragment, () -> {});
    }

    public static void show(Fragment fragment, Runnable refresh) {
        if (fragment.getView() != null && fragment.getActivity() != null) show(fragment.getActivity(), fragment.getViewLifecycleOwner(), refresh);
    }

    public static void show(FragmentActivity activity, Runnable refresh) {
        show(activity, activity, refresh);
    }

    public static void updateSummary(TextView summary) {
        Task.loaderExecutor().execute(() -> {
            String value;
            try {
                value = state(summary.getContext(), read());
            } catch (RuntimeException e) {
                value = summary.getContext().getString(R.string.lan_pairing_storage_error);
            }
            String text = value;
            App.post(() -> {
                if (summary.isAttachedToWindow()) summary.setText(text);
            });
        });
    }

    private static Snapshot read() {
        LanPairingController controller = LanPeers.get();
        boolean enabled = controller.isEnabled();
        List<String> ids = new ArrayList<>(controller.partners().keySet());
        Collections.sort(ids);
        return new Snapshot(enabled, LanPeerStore.isLocalWriteEnabled(), fingerprint(), ids);
    }

    private static String fingerprint() {
        Server server = Server.get();
        return server.getLanPort() == 0 ? "" : server.getLanFingerprint();
    }

    private static String state(Context context, Snapshot snapshot) {
        if (!snapshot.enabled) return context.getString(R.string.lan_pairing_disabled);
        return context.getString(snapshot.fingerprint.isEmpty() ? R.string.lan_pairing_not_ready : R.string.lan_pairing_enabled, snapshot.ids.size());
    }

    private static void show(FragmentActivity activity, LifecycleOwner owner, Runnable refresh) {
        if (!LanRemotePairDialog.visible(activity)) return;
        WebHtvAlertDialogBuilder builder = builder(activity);
        Context context = builder.getContext();
        LinearLayout content = LanRemotePairDialog.content(context);
        TextView status = LanRemotePairDialog.text(content, false);
        TextView policy = LanRemotePairDialog.text(content, false);
        policy.setText(R.string.lan_pairing_policy);
        MaterialSwitch enabled = new MaterialSwitch(context);
        enabled.setText(R.string.lan_pairing_toggle);
        content.addView(enabled);
        MaterialSwitch writes = new MaterialSwitch(context);
        writes.setText(R.string.lan_pairing_local_write);
        content.addView(writes);
        MaterialButton generate = button(content, R.string.lan_pairing_new_code);
        TextView code = LanRemotePairDialog.text(content, true);
        TextView digest = LanRemotePairDialog.text(content, true);
        MaterialButton devices = button(content, R.string.lan_pairing_entry);
        MaterialButton revokeAll = button(content, R.string.lan_pairing_revoke_all);
        AlertDialog dialog = builder.setTitle(R.string.lan_pairing_entry).setView(LanRemotePairDialog.scroll(content))
                .setNegativeButton(android.R.string.cancel, null).create();
        if (!LanRemotePairDialog.open(owner, dialog, refresh)) return;

        class Manager {
            void run(Operation operation) {
                enabled.setEnabled(false);
                writes.setEnabled(false);
                generate.setEnabled(false);
                devices.setEnabled(false);
                revokeAll.setEnabled(false);
                code.setVisibility(View.GONE);
                digest.setVisibility(View.GONE);
                status.setText(R.string.lan_pairing_loading);
                Task.execute(() -> {
                    Result result = null;
                    boolean failed = false;
                    try {
                        result = operation.run();
                    } catch (RuntimeException e) {
                        failed = true;
                    }
                    Snapshot snapshot;
                    try {
                        snapshot = read();
                    } catch (RuntimeException e) {
                        snapshot = null;
                    }
                    boolean error = failed;
                    Snapshot current = snapshot;
                    Result outcome = result;
                    App.post(() -> {
                        refresh.run();
                        if (!dialog.isShowing() || !LanRemotePairDialog.visible(activity)) return;
                        if (current == null || error) Notify.show(R.string.lan_pairing_storage_error);
                        if (current == null) {
                            status.setText(R.string.lan_pairing_storage_error);
                            return;
                        }
                        render(current);
                        if (outcome == null) return;
                        if (outcome.code != null && current.enabled && !current.fingerprint.isEmpty()) {
                            code.setText(context.getString(R.string.lan_pairing_code, outcome.code));
                            digest.setText(context.getString(R.string.lan_pairing_fingerprint, LanRemotePairDialog.grouped(current.fingerprint)));
                            code.setVisibility(View.VISIBLE);
                            digest.setVisibility(View.VISIBLE);
                        } else if (outcome.preparing) status.setText(R.string.lan_pairing_preparing);
                        if (outcome.revoked > 0) Notify.show(context.getString(R.string.lan_pairing_revoked, outcome.revoked));
                    });
                });
            }

            void render(Snapshot snapshot) {
                status.setText(state(context, snapshot));
                enabled.setOnCheckedChangeListener(null);
                writes.setOnCheckedChangeListener(null);
                enabled.setChecked(snapshot.enabled);
                writes.setChecked(snapshot.localWrite);
                enabled.setOnCheckedChangeListener((view, checked) -> run(() -> {
                    LanPeers.get().setEnabled(checked);
                    if (checked) Server.get().start();
                    return null;
                }));
                writes.setOnCheckedChangeListener((view, checked) -> run(() -> {
                    LanPeerStore.setLocalWriteEnabled(checked);
                    return null;
                }));
                enabled.setEnabled(true);
                writes.setEnabled(true);
                generate.setEnabled(snapshot.enabled);
                devices.setText(context.getString(R.string.lan_pairing_manage, snapshot.ids.size()));
                devices.setEnabled(!snapshot.ids.isEmpty());
                revokeAll.setEnabled(!snapshot.ids.isEmpty());
                devices.setOnClickListener(view -> {
                    String[] ids = snapshot.ids.toArray(new String[0]);
                    Task.execute(() -> {
                        String[] labels = deviceLabels(ids);
                        App.post(() -> {
                            if (!dialog.isShowing()) return;
                            AlertDialog list = builder(activity).setTitle(R.string.lan_pairing_entry)
                                    .setItems(labels, (popup, which) -> confirm(context.getString(R.string.lan_pairing_revoke_one, labels[which]), ids[which]))
                                    .setNegativeButton(android.R.string.cancel, null).create();
                            LanRemotePairDialog.open(owner, list, null);
                        });
                    });
                });
            }

            void confirm(String message, String id) {
                AlertDialog confirmation = builder(activity).setTitle(R.string.lan_pairing_revoke_confirm).setMessage(message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.lan_pairing_revoke_confirm, (popup, which) -> run(() -> {
                            LanPairingController controller = LanPeers.get();
                            int count = 0;
                            if (id == null) {
                                for (String target : controller.partners().keySet()) if (controller.revoke(target)) count++;
                            } else if (controller.revoke(id)) count++;
                            return new Result(null, false, count);
                        })).create();
                LanRemotePairDialog.open(owner, confirmation, null);
            }
        }
        Manager manager = new Manager();
        generate.setOnClickListener(view -> manager.run(() -> {
            if (fingerprint().isEmpty()) {
                Server.get().start();
                return new Result(null, true, 0);
            }
            return new Result(LanPeers.get().issuePairingCode(), false, 0);
        }));
        revokeAll.setOnClickListener(view -> manager.confirm(context.getString(R.string.lan_pairing_revoke_all_confirm), null));
        manager.run(() -> null);
    }

    private static String[] deviceLabels(String[] ids) {
        Map<String, String> knownNames = new HashMap<>();
        try {
            for (Device device : Device.getAll()) if (!device.getName().isEmpty()) knownNames.put(device.getUuid(), device.getName());
        } catch (RuntimeException ignored) {
            // The registry is authoritative; local discovery names are optional.
        }
        String[] labels = ids.clone();
        for (int i = 0; i < ids.length; i++) {
            String name = knownNames.get(ids[i]);
            if (name != null) labels[i] = name + " · " + ids[i];
        }
        return labels;
    }

    private static WebHtvAlertDialogBuilder builder(Context context) {
        return new WebHtvAlertDialogBuilder(context, R.style.ThemeOverlay_WebHTV_Dialog);
    }

    private static MaterialButton button(LinearLayout content, int label) {
        MaterialButton button = new MaterialButton(content.getContext());
        button.setText(label);
        content.addView(button);
        return button;
    }

    private interface Operation {
        Result run();
    }

    private record Snapshot(boolean enabled, boolean localWrite, String fingerprint, List<String> ids) {
    }

    private record Result(String code, boolean preparing, int revoked) {
    }
}
