package com.fongmi.android.tv.ui.helper;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class TouchOptimizationHelper {

    private static final Map<View, Boolean> ORIGINAL_TOUCH_MODES = new WeakHashMap<>();
    private static final Set<RecyclerView> RECYCLERS_WITH_LISTENER = Collections.newSetFromMap(new WeakHashMap<>());

    private TouchOptimizationHelper() {
    }

    public static void sync(View root) {
        if (!Util.isLeanback()) return;
        if (root == null) return;
        if (Setting.isTouchOptimized()) optimize(root);
        else restore(root);
    }

    public static void sync(Dialog dialog) {
        if (!Util.isLeanback()) return;
        if (dialog == null || dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        decor.post(() -> sync(decor));
    }

    public static void optimize(View root) {
        if (root == null || !Setting.isTouchOptimized()) return;
        traverse(root, true);
    }

    public static void restore(View root) {
        if (root == null) return;
        traverse(root, false);
    }

    private static void traverse(View view, boolean optimize) {
        if (isInputView(view)) return;
        if (optimize && view instanceof RecyclerView recycler) attachListener(recycler);
        if (optimize) {
            if (view.isFocusableInTouchMode()) {
                if (!ORIGINAL_TOUCH_MODES.containsKey(view)) ORIGINAL_TOUCH_MODES.put(view, true);
                view.setFocusableInTouchMode(false);
            }
        } else {
            Boolean original = ORIGINAL_TOUCH_MODES.remove(view);
            if (Boolean.TRUE.equals(original)) view.setFocusableInTouchMode(true);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) traverse(group.getChildAt(i), optimize);
        }
    }

    private static void attachListener(RecyclerView recycler) {
        if (!RECYCLERS_WITH_LISTENER.add(recycler)) return;
        recycler.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                sync(view);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
    }

    private static boolean isInputView(View view) {
        return view instanceof WebView || view instanceof EditText || view.onCheckIsTextEditor();
    }
}
