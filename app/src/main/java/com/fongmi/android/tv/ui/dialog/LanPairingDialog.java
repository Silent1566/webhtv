package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.server.LanPairingController;
import com.fongmi.android.tv.server.LanPeers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

/**
 * Settings-facing UI for LAN control: master switch, one-time pairing code, bulk revoke.
 *
 * <p>Deliberately small: per-device revocation needs a list screen and is not part of this step,
 * so a bulk action keeps a compromised pairing recoverable until that screen exists.
 */
public final class LanPairingDialog {

    private LanPairingDialog() {
    }

    public static void show(Fragment fragment) {
        Context context = fragment.requireContext();
        LanPairingController controller = LanPeers.get();
        String[] items = {
                context.getString(R.string.lan_pairing_toggle),
                context.getString(R.string.lan_pairing_new_code),
                context.getString(R.string.lan_pairing_revoke_all),
        };
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.lan_pairing_title_state, stateShort(context, controller)))
                .setItems(items, (view, which) -> {
                    if (which == 0) {
                        controller.setEnabled(!controller.isEnabled());
                        Toast.makeText(context, stateText(context, controller), Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        String code = controller.issuePairingCode();
                        Toast.makeText(context, code == null ? context.getString(R.string.lan_pairing_state_off)
                                : context.getString(R.string.lan_pairing_code, code), Toast.LENGTH_LONG).show();
                    } else {
                        Map<String, byte[]> partners = controller.partners();
                        int revoked = 0;
                        for (String uuid : partners.keySet()) if (controller.revoke(uuid)) revoked++;
                        Toast.makeText(context, context.getString(R.string.lan_pairing_revoked, revoked), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String stateText(Context context, LanPairingController controller) {
        String state = controller.isEnabled()
                ? context.getString(R.string.lan_pairing_state_on)
                : context.getString(R.string.lan_pairing_state_off);
        return state + System.lineSeparator() + context.getString(R.string.lan_pairing_enabled_short, controller.partners().size());
    }

    /** Short state for the title; AlertDialog cannot show a message and item list at once. */
    private static String stateShort(Context context, LanPairingController controller) {
        return controller.isEnabled()
                ? context.getString(R.string.lan_pairing_enabled, controller.partners().size())
                : context.getString(R.string.lan_pairing_disabled);
    }
}
