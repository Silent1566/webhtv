package com.fongmi.android.tv.ui.dialog;

final class UpdateDialogLayout {

    private static final float SCREEN_HEIGHT_RATIO = 0.42f;

    private UpdateDialogLayout() {
    }

    static int calculateScrollHeight(int screenHeight, int normalMinHeight, int normalMaxHeight, int downloadMinHeight, boolean progressVisible, int progressPanelHeight) {
        int height = Math.max(normalMinHeight, Math.min(normalMaxHeight, (int) (screenHeight * SCREEN_HEIGHT_RATIO)));
        if (!progressVisible) return height;
        return Math.max(downloadMinHeight, height - Math.max(0, progressPanelHeight));
    }

    static int fitScrollHeight(int preferredHeight, int availableHeight, int chromeHeight, int verticalMargin) {
        int maxDialogHeight = Math.min(availableHeight, Math.max(chromeHeight + 1, availableHeight - Math.max(0, verticalMargin)));
        return Math.max(1, Math.min(preferredHeight, maxDialogHeight - chromeHeight));
    }

    static boolean hasProgressPanelHeightChanged(int previousHeight, int currentHeight) {
        return previousHeight != currentHeight;
    }
}
