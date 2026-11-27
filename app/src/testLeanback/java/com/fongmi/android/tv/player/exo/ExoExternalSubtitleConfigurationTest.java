package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.Init;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
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

    @Test
    public void infersKnownSubtitleMimeFromPathWithoutChangingSelectionFields() {
        String signedAss = "https://example.invalid/chinese.ASS?token=one.srt#ignored.vtt";
        Sub ass = Sub.create("Chinese ASS", signedAss, "zh", null);
        ass.setFlag(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED);
        Sub srt = Sub.create("English SRT", "https://example.invalid/english.sRt?signature=one", "en", "");
        Sub ssa = Sub.create("Japanese SSA", "https://example.invalid/japanese.SSA", "ja", "");
        Sub vtt = Sub.create("French VTT", "https://example.invalid/french.VtT#cue", "fr", null);

        List<MediaItem.SubtitleConfiguration> subtitles = createMedia(ass, srt, ssa, vtt).subtitleConfigurations;

        assertEquals(4, subtitles.size());
        assertEquals(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(signedAss))
                .setLabel("Chinese ASS").setMimeType(MimeTypes.TEXT_SSA).setLanguage("zh")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED)
                .setId("webhtv-ass-external:0").build(), subtitles.get(0));
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitles.get(1).mimeType);
        assertEquals(MimeTypes.TEXT_SSA, subtitles.get(2).mimeType);
        assertEquals("webhtv-ass-external:2", subtitles.get(2).id);
        assertEquals(MimeTypes.TEXT_VTT, subtitles.get(3).mimeType);
    }

    @Test
    public void explicitSubtitleMimeTakesPriorityOverConflictingPath() {
        Sub explicitVtt = Sub.create("WebVTT", ASS, "en", MimeTypes.TEXT_VTT);
        Sub explicitAss = Sub.create("ASS", SRT, "zh", MimeTypes.TEXT_SSA);

        List<MediaItem.SubtitleConfiguration> subtitles = createMedia(explicitVtt, explicitAss).subtitleConfigurations;

        assertEquals(MimeTypes.TEXT_VTT, subtitles.get(0).mimeType);
        assertNull(subtitles.get(0).id);
        assertEquals(MimeTypes.TEXT_SSA, subtitles.get(1).mimeType);
        assertEquals("webhtv-ass-external:1", subtitles.get(1).id);
    }

    @Test
    public void doesNotGuessMimeForUnknownOrMissingPathSuffix() {
        Sub unknown = Sub.create("Unknown", "https://example.invalid/subtitle.txt?file=sample.ass", "en", null);
        Sub noSuffix = Sub.create("No suffix", "https://example.invalid/download?file=sample.srt#sample.vtt", "en", "");
        Sub directory = Sub.create("Directory", "https://example.invalid/subtitle.ass/", "en", null);

        List<MediaItem.SubtitleConfiguration> subtitles = createMedia(unknown, noSuffix, directory).subtitleConfigurations;

        assertEquals(3, subtitles.size());
        for (MediaItem.SubtitleConfiguration subtitle : subtitles) {
            assertEquals("", subtitle.mimeType);
            assertNull(subtitle.id);
        }
    }

    @Test
    public void returnsSelectedSecondAssEvenWithSameLabelAndInvalidEntries() throws Exception {
        Sub first = sameNameAss("first.ass");
        Sub second = sameNameAss("second.ASS?token=one");
        Sub empty = Sub.create("Shared ASS", "", "zh", MimeTypes.TEXT_SSA);
        Sub srt = Sub.create("English SRT", SRT, "en", MimeTypes.APPLICATION_SUBRIP);
        PlaySpec spec = createSpec("episode-1", null, empty, first, srt, second);

        assertEquals(second.getUrl(), selectedSubtitleUrl(spec, ExoUtil.getMediaItem(spec, 0),
                assTrack("4:2:webhtv-ass-external:2")));
    }

    @Test
    public void followsActiveConfigurationWhenTheSourceListIsReordered() throws Exception {
        Sub first = sameNameAss("first.ass");
        Sub second = sameNameAss("second.ass");
        PlaySpec spec = createSpec("episode-1", first, second);
        MediaItem active = ExoUtil.getMediaItem(spec, 0);
        spec.getSubs().set(0, second);
        spec.getSubs().set(1, first);

        assertEquals(first.getUrl(), selectedSubtitleUrl(spec, active, assTrack("1:webhtv-ass-external:0")));
        assertEquals(second.getUrl(), selectedSubtitleUrl(spec, ExoUtil.getMediaItem(spec, 0),
                assTrack("1:webhtv-ass-external:0")));
    }

    @Test
    public void doesNotReuseExternalAssIndexForAnotherMediaItem() throws Exception {
        Sub shared = sameNameAss("shared.ass");
        Sub next = sameNameAss("next.ass");
        PlaySpec previousSpec = createSpec("episode-1", shared);
        MediaItem previous = ExoUtil.getMediaItem(previousSpec, 0);
        PlaySpec spec = createSpec("episode-2", next, shared);
        spec.setUrl("https://example.invalid/next.mp4");
        Format selected = assTrack("1:webhtv-ass-external:0");
        MediaItem queued = ExoUtil.getMediaItem(spec, 0, "short-drama:episode-2");

        assertNull(selectedSubtitleUrl(spec, previous, selected));
        assertEquals(next.getUrl(), selectedSubtitleUrl(spec, queued, selected));
    }

    @Test
    public void doesNotTreatEmbeddedOrUnknownAssTrackAsAnExternalFile() throws Exception {
        PlaySpec spec = createSpec("episode-1", sameNameAss("external.ass"));
        MediaItem active = ExoUtil.getMediaItem(spec, 0);

        assertNull(selectedSubtitleUrl(spec, active, assTrack("0:7")));
        assertNull(selectedSubtitleUrl(spec, active, assTrack("source:webhtv-ass-external:0")));
        assertNull(selectedSubtitleUrl(spec, active, assTrack("1:webhtv-ass-external:9")));
    }

    @Test
    public void stillLooksUpSelectedSrtByItsLabels() throws Exception {
        Sub srt = Sub.create("English SRT", SRT, "en", MimeTypes.APPLICATION_SUBRIP);
        PlaySpec spec = createSpec("episode-1", sameNameAss("external.ass"), srt);
        Format selected = new Format.Builder().setLabel("English SRT")
                .setLanguage("en").setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build();

        assertEquals(srt.getUrl(), selectedSubtitleUrl(spec, ExoUtil.getMediaItem(spec, 0), selected));
    }

    @Test
    public void keepsConcatenatedSourcesOnTheExistingSubtitleLookup() throws Exception {
        Sub external = sameNameAss("external.ass");
        PlaySpec spec = createSpec("concatenated", external);
        spec.setUrl("https://example.invalid/one.mp4|||1000***https://example.invalid/two.mp4|||1000");

        assertEquals(external.getUrl(), selectedSubtitleUrl(spec, MediaItem.fromUri(Uri.EMPTY),
                assTrack("0:1:webhtv-ass-external:0")));
    }

    private static Sub sameNameAss(String filename) {
        return Sub.create("Shared ASS", "https://example.invalid/" + filename, "zh", null);
    }

    private static Format assTrack(String id) {
        return new Format.Builder().setId(id).setLabel("Shared ASS").setLanguage("zh")
                .setSampleMimeType(MimeTypes.TEXT_SSA).build();
    }

    private static String selectedSubtitleUrl(PlaySpec spec, MediaItem item, Format selected) throws Exception {
        Tracks tracks = new Tracks(List.of(new Tracks.Group(new TrackGroup(selected), true,
                new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        PlayerManager manager = (PlayerManager) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), PlayerManager.class);
        setField(manager, "spec", spec);
        setField(manager, "playerType", PlayerSetting.EXO);
        setField(manager, "engine", Proxy.newProxyInstance(PlayerEngine.class.getClassLoader(),
                new Class<?>[]{PlayerEngine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentTracks")) return tracks;
                    throw new AssertionError(method.getName());
                }));
        setField(manager, "player", Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentMediaItem")) return item;
                    throw new AssertionError(method.getName());
                }));
        Sub sub = manager.getSelectedSubtitleSub();
        return sub == null ? null : sub.getUrl();
    }

    private static void setField(PlayerManager manager, String name, Object value) throws Exception {
        Field field = PlayerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static PlaySpec createSpec(String id, Sub... subs) {
        PlaySpec spec = PlaySpec.from(id, VIDEO, Map.of(), MediaMetadata.EMPTY);
        spec.setSub(null);
        spec.getSubs().addAll(Arrays.asList(subs));
        return spec;
    }

    private static MediaItem.LocalConfiguration createMedia(Sub... subs) {
        return ExoUtil.getMediaItem(createSpec("video", subs), 0).localConfiguration;
    }
}
