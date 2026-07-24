package com.fongmi.android.tv.ui.dialog;

final class AboutDialogLayout {

    private AboutDialogLayout() {
    }

    static int calculateHeight(int screenHeight, int verticalSafeSpace) {
        return Math.max(1, screenHeight - Math.max(0, verticalSafeSpace));
    }
}
