package com.fongmi.android.tv.impl;

import android.view.View;

public interface UpdateListener {

    void onCancel(View view);

    void onConfirm(View view);

    default void onChannel(String channel) {
    }

    default void onClose() {
    }
}
