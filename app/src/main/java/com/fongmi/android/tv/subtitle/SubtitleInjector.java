package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;

public final class SubtitleInjector {

    public Sub toSub(SubtitleAsset asset) {
        if (asset == null) return null;
        Sub sub = Sub.create(asset.getDisplayName(), asset.getUri(), asset.getLanguage(), asset.getMimeType());
        if (asset.getSelectionFlag() != 0) sub.setFlag(asset.getSelectionFlag());
        return sub;
    }
}
