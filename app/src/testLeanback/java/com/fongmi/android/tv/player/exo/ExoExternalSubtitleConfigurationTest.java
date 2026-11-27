package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.Init;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExoExternalSubtitleConfigurationTest {

    private static final String VIDEO = "https://example.invalid/video.mp4";
    private static final String ASS = "https://example.invalid/chinese.ass";
    private static final String SRT = "https://example.invalid/english.srt";

    @Before
    public void setUp() {
        Init.set(RuntimeEnvironment.getApplication());
        Setting.putAdblock(false);
    }

    @Test
    public void skipsInvalidEntriesAndPreservesValidSubtitleFieldsInOrder() {
        Sub ass = Sub.create("Chinese ASS", ASS, "zh", MimeTypes.TEXT_SSA);
        ass.setFlag(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED);
        Sub empty = Sub.create("Unavailable", "", "zh", MimeTypes.TEXT_SSA);
        Sub srt = Sub.create("English SRT", SRT, "en", MimeTypes.APPLICATION_SUBRIP);

        MediaItem.LocalConfiguration configuration = createMedia(null, ass, empty, srt);

        assertEquals(Uri.parse(VIDEO), configuration.uri);
        List<MediaItem.SubtitleConfiguration> subtitles = configuration.subtitleConfigurations;
        assertEquals(2, subtitles.size());
        assertEquals(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(ASS))
                .setLabel("Chinese ASS").setMimeType(MimeTypes.TEXT_SSA).setLanguage("zh")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED)
                .setId("webhtv-ass-external:0").build(), subtitles.get(0));
        assertEquals(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(SRT))
                .setLabel("English SRT").setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(), subtitles.get(1));
    }

    @Test
    public void allMissingAddressesLeaveVideoWithNoExternalTracks() {
        MediaItem.LocalConfiguration configuration = createMedia(
                Sub.create("Empty address", "", "en", MimeTypes.APPLICATION_SUBRIP),
                Sub.create("Missing address", null, "zh", MimeTypes.TEXT_SSA));

        assertEquals(Uri.parse(VIDEO), configuration.uri);
        assertTrue(configuration.subtitleConfigurations.isEmpty());
    }

    private static MediaItem.LocalConfiguration createMedia(Sub... subs) {
        PlaySpec spec = PlaySpec.from("video", VIDEO, Map.of(), MediaMetadata.EMPTY);
        spec.setSub(null);
        spec.getSubs().addAll(Arrays.asList(subs));
        return ExoUtil.getMediaItem(spec, 0).localConfiguration;
    }
}
