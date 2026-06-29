package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchResult;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchStatus;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

public final class SubtitleManualSearchDialog {

    private SubtitleManualSearchDialog() {
    }

    public static void show(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host) {
        if (activity == null || session == null || host == null) return;
        showKeywordDialog(activity, session, host, session.getManualSearchKeyword(host));
    }

    private static void showKeywordDialog(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, String defaultKeyword) {
        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.search_keyword);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setMaxLines(1);
        input.setText(defaultKeyword);
        input.setSelectAllOnFocus(false);
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_manual_search)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.play_search, null)
                .show();
        LightDialog.apply(dialog);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.setOnClickListener(view -> {
            String keyword = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(keyword)) return;
            Util.hideKeyboard(input);
            dialog.dismiss();
            search(activity, session, host, keyword);
        });
        input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) positive.performClick();
            return true;
        });
        input.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            return positive.requestFocus();
        });
        Util.showKeyboard(input);
    }

    private static void search(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, String keyword) {
        Notify.show(R.string.subtitle_manual_searching);
        session.manualSearch(host, keyword, (request, result, applied) -> {
            if (!isAlive(activity)) return;
            if (!canShowCandidates(result)) {
                notifySearchResult(result);
                return;
            }
            showCandidates(activity, session, host, result.getCandidates());
        });
    }

    private static void showCandidates(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, List<SubtitleCandidate> candidates) {
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) labels[i] = label(candidates.get(i));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(ResUtil.getString(R.string.subtitle_manual_select_title, candidates.size()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setItems(labels, (d, which) -> resolve(activity, session, host, candidates.get(which)))
                .show();
        LightDialog.apply(dialog);
    }

    private static void resolve(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, SubtitleCandidate candidate) {
        Notify.show(R.string.subtitle_manual_resolving);
        session.resolveManual(host, candidate, (request, result, applied) -> {
            if (!isAlive(activity)) return;
            if (result != null && result.getStatus() == SubtitleMatchStatus.MATCHED && applied) {
                Notify.show(ResUtil.getString(R.string.subtitle_manual_applied, displayName(candidate)));
            } else if (result != null && result.getStatus() == SubtitleMatchStatus.MATCHED) {
                Notify.show(R.string.subtitle_manual_apply_failed);
            } else {
                notifySearchResult(result);
            }
        });
    }

    private static boolean canShowCandidates(SubtitleMatchResult result) {
        return result != null && result.getCandidates() != null && !result.getCandidates().isEmpty();
    }

    private static void notifySearchResult(SubtitleMatchResult result) {
        if (result == null) {
            Notify.show(R.string.subtitle_manual_search_failed);
            return;
        }
        if (result.getStatus() == SubtitleMatchStatus.SKIPPED && "provider_unavailable".equals(result.getReason())) {
            Notify.show(R.string.subtitle_auto_match_provider_unavailable);
        } else if (result.getStatus() == SubtitleMatchStatus.ERROR && "inactive".equals(result.getReason())) {
            Notify.show(R.string.subtitle_manual_inactive);
        } else if (result.getStatus() == SubtitleMatchStatus.ERROR) {
            Notify.show(ResUtil.getString(R.string.subtitle_auto_match_failed, readableReason(result.getReason())));
        } else {
            Notify.show(R.string.subtitle_manual_search_empty);
        }
    }

    private static String label(SubtitleCandidate candidate) {
        StringBuilder builder = new StringBuilder(displayName(candidate));
        String meta = meta(candidate);
        if (!TextUtils.isEmpty(meta)) builder.append('\n').append(meta);
        return builder.toString();
    }

    private static String meta(SubtitleCandidate candidate) {
        if (candidate == null) return "";
        StringBuilder builder = new StringBuilder();
        append(builder, candidate.getProvider());
        append(builder, candidate.getLanguage());
        append(builder, candidate.getFormat());
        if (candidate.getScore() > 0) append(builder, String.format(Locale.US, "%d", candidate.getScore()));
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (builder.length() > 0) builder.append(" · ");
        builder.append(value);
    }

    private static String displayName(SubtitleCandidate candidate) {
        if (candidate == null) return "";
        if (!TextUtils.isEmpty(candidate.getDisplayName())) return candidate.getDisplayName();
        return TextUtils.isEmpty(candidate.getCandidateId()) ? candidate.getProvider() : candidate.getCandidateId();
    }

    private static String readableReason(String reason) {
        return TextUtils.isEmpty(reason) ? ResUtil.getString(R.string.subtitle_auto_match_unknown_reason) : reason;
    }

    private static boolean isAlive(FragmentActivity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
