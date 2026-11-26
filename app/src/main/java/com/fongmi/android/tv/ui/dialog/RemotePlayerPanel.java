package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.remote.RemoteClient;
import com.fongmi.android.tv.remote.RemoteModels.CommandResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.remote.RemotePlayerTargets;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * 手机端的遥控面板：把按钮映射成 {@code player.command}，经由既有中转通道发给另一台设备。
 *
 * <p>只做发送与结果提示，不含语音、不含局域网直连——与
 * {@code docs/FORK-5-remote-control-decision.md} 的推荐范围一致。结果显示在命令真正发出之后，
 * 不在点击时先报成功。
 */
public final class RemotePlayerPanel {

    private static final String TYPE = "player.command";

    /** 面板项与线上动作一一对应，顺序即展示顺序。 */
    private static final int[] LABELS = {
            R.string.remote_panel_play_pause,
            R.string.remote_panel_prev,
            R.string.remote_panel_next,
            R.string.remote_panel_seek_back,
            R.string.remote_panel_seek_forward,
            R.string.remote_panel_back,
            R.string.remote_panel_up,
            R.string.remote_panel_down,
            R.string.remote_panel_left,
            R.string.remote_panel_right,
            R.string.remote_panel_enter,
            R.string.remote_panel_volume_up,
            R.string.remote_panel_volume_down,
    };

    private static final String[] ACTIONS = {
            "play_pause", "prev", "next", "seek_back", "seek_forward", "back",
            "up", "down", "left", "right", "enter", "volume_up", "volume_down",
    };

    private RemotePlayerPanel() {
    }

    public static void show(Fragment fragment) {
        Context context = fragment.requireContext();
        RemoteProfile profile = RemoteStore.firstProfile();
        if (profile == null) {
            toast(context, context.getString(R.string.remote_panel_no_profile));
            return;
        }
        List<RemotePlayerTargets.Target> targets = RemotePlayerTargets.peers(profile);
        if (targets.isEmpty()) {
            toast(context, context.getString(R.string.remote_panel_no_device));
        } else if (targets.size() == 1) {
            chooseAction(context, profile, targets.get(0));
        } else {
            chooseTarget(context, profile, targets);
        }
    }

    private static void chooseTarget(Context context, RemoteProfile profile, List<RemotePlayerTargets.Target> targets) {
        String[] names = new String[targets.size()];
        for (int i = 0; i < names.length; i++) names[i] = RemotePlayerTargets.label(targets.get(i));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.remote_panel_title)
                .setItems(names, (view, which) -> chooseAction(context, profile, targets.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void chooseAction(Context context, RemoteProfile profile, RemotePlayerTargets.Target target) {
        String[] items = new String[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) items[i] = context.getString(LABELS[i]);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.remote_panel_title_target, RemotePlayerTargets.label(target)))
                .setItems(items, (view, which) -> send(context, profile, target, items[which], ACTIONS[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    private static void send(Context context, RemoteProfile profile, RemotePlayerTargets.Target target, String label, String action) {
        Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        // seek 步长由执行端 RemotePlayerCommand 的默认值决定，此处不重复写一遍常量。
        Task.execute(() -> {
            String message;
            try {
                CommandResponse response = new RemoteClient(profile).createCommand(target.group, target.device.deviceId, TYPE, payload);
                String commandId = response == null ? "" : response.commandId;
                if (TextUtils.isEmpty(commandId) && response != null && response.command != null) commandId = response.command.id;
                message = TextUtils.isEmpty(commandId)
                        ? app.getString(R.string.remote_panel_rejected)
                        : app.getString(R.string.remote_panel_sent, label);
            } catch (Throwable error) {
                message = app.getString(R.string.remote_panel_failed, describe(error));
            }
            String result = message;
            App.post(() -> toast(app, result));
        });
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return TextUtils.isEmpty(message) ? error.getClass().getSimpleName() : message;
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
