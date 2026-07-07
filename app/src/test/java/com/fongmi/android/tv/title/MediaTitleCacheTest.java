package com.fongmi.android.tv.title;

import com.fongmi.android.tv.bean.AiConfig;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public class MediaTitleCacheTest {

    @Test
    public void key_changesWhenTitleExtractionPromptChanges() {
        MediaTitleRequest request = MediaTitleRequest.builder()
                .siteKey("site")
                .vodId("vod")
                .rawTitle("F 凡人#修仙传 动漫 B")
                .build();
        AiConfig first = AiConfig.objectFrom("{}");
        AiConfig second = AiConfig.objectFrom("{}");
        second.setTitleExtractionPrompt("优先把 # 分隔的中文片名合并");

        MediaTitleCache cache = new MediaTitleCache();

        assertNotEquals(cache.key(request, first), cache.key(request, second));
    }
}
