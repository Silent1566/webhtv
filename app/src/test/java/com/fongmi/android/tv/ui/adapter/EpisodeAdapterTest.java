package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpisodeAdapterTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void tmdbCardTitleSeparatesSourceFileSize() {
        Episode episode = Episode.create("[5.32G] 01.mkv", "https://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "楚云嫁入齐府成...", "2026-06-20", "", "", 0, 47);
        episode.setTmdbEpisode(tmdbEpisode);

        String title = EpisodeAdapter.getCardTitle(episode);

        assertEquals("1. 楚云嫁入齐府成...", title);
        assertEquals("[5.32G]", EpisodeAdapter.getCardFileSize(episode, title, true));
        assertEquals("", EpisodeAdapter.getCardFileSize(episode, title, false));
    }

    @Test
    public void mobileTmdbEpisodeCardsBindFileSizeBadge() throws Exception {
        String gridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String horiHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java")));
        String gridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String horiLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_hori.xml")));

        assertTrue("mobile grid TMDB cards must expose a fileSize badge",
                gridLayout.contains("android:id=\"@+id/fileSize\"")
                        && gridHolder.contains("binding.cardTitle.setText(cardTitle);")
                        && gridHolder.contains("bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle), showMeta);"));
        assertTrue("mobile horizontal TMDB cards must expose a fileSize badge",
                horiLayout.contains("android:id=\"@+id/fileSize\"")
                        && horiHolder.contains("binding.cardTitle.setText(cardTitle);")
                        && horiHolder.contains("bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle));"));
    }

    @Test
    public void leanbackTmdbEpisodeCardsBindFileSizeBadge() throws Exception {
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_card.xml")));

        assertTrue("TV TMDB episode cards must expose a fileSize badge",
                layout.contains("android:id=\"@+id/fileSize\"")
                        && adapter.contains("String cardTitle = getCardTitle(item, tmdbEpisode);")
                        && adapter.contains("binding.cardTitle.setText(cardTitle);")
                        && adapter.contains("bindFileSize(binding, getCardFileSize(item, cardTitle), showMeta);"));
    }

    @Test
    public void detailEpisodeNamesAndLabelsMarqueeWhenActive() throws Exception {
        String detailLayout = read(findMainResPath().resolve(Path.of("layout", "adapter_tmdb_episode.xml")));
        String detailAdapter = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java")));
        assertMarquee("detail episode name", detailLayout, "@+id/index");
        assertConstrainedMarquee("detail episode date", detailLayout, "@+id/date");
        assertConstrainedMarquee("detail episode badge", detailLayout, "@+id/badge");
        assertConstrainedMarquee("detail episode file size", detailLayout, "@+id/fileSize");
        assertTrue("detail episode cards must activate the name and related labels together",
                detailAdapter.contains("holder.binding.index.setSelected(active);")
                        && detailAdapter.contains("holder.binding.date.setSelected(active);")
                        && detailAdapter.contains("holder.binding.badge.setSelected(active);")
                        && detailAdapter.contains("holder.binding.fileSize.setSelected(active);"));
    }

    @Test
    public void mobilePlaybackEpisodeNamesAndLabelsMarqueeWhenActive() throws Exception {
        String mobileGridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String mobileHoriLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_hori.xml")));
        String mobileGridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String mobileHoriHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java")));
        assertMarquee("mobile grid episode name", mobileGridLayout, "@+id/cardTitle");
        assertConstrainedMarquee("mobile grid episode meta", mobileGridLayout, "@+id/meta");
        assertConstrainedMarquee("mobile grid episode file size", mobileGridLayout, "@+id/fileSize");
        assertMarquee("mobile horizontal episode text", mobileHoriLayout, "@+id/text");
        assertMarquee("mobile horizontal episode name", mobileHoriLayout, "@+id/cardTitle");
        assertConstrainedMarquee("mobile horizontal episode file size", mobileHoriLayout, "@+id/fileSize");
        assertTrue("mobile grid episode cards must activate the name and related labels together",
                mobileGridHolder.contains("binding.cardTitle.setSelected(active);")
                        && mobileGridHolder.contains("binding.meta.setSelected(active);")
                        && mobileGridHolder.contains("binding.fileSize.setSelected(active);"));
        assertTrue("mobile horizontal episode items must activate overflowing text",
                mobileHoriHolder.contains("binding.text.setOnFocusChangeListener")
                        && mobileHoriHolder.contains("binding.cardTitle.setSelected(active);")
                        && mobileHoriHolder.contains("binding.fileSize.setSelected(active);"));
        assertTrue("recycled mobile holders must clear hidden text marquee state",
                mobileGridHolder.contains("binding.text.setActivated(false);")
                        && mobileGridHolder.contains("setMarquee(false);")
                        && mobileHoriHolder.contains("binding.text.setActivated(false);")
                        && mobileHoriHolder.contains("setTextMarquee(false);")
                        && mobileHoriHolder.contains("binding.text.isActivated()"));
    }

    @Test
    public void tvPlaybackEpisodeNamesAndLabelsMarqueeWhenActive() throws Exception {
        String leanbackTextLayout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode.xml")));
        String leanbackCardLayout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_card.xml")));
        String leanbackAdapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        assertMarquee("TV episode text", leanbackTextLayout, "@+id/text");
        assertMarquee("TV episode name", leanbackCardLayout, "@+id/cardTitle");
        assertConstrainedMarquee("TV episode date", leanbackCardLayout, "@+id/dateBadge");
        assertConstrainedMarquee("TV episode file size", leanbackCardLayout, "@+id/fileSize");
        assertTrue("TV episode items must activate overflowing text while selected or focused",
                leanbackAdapter.contains("textView.setOnFocusChangeListener")
                        && leanbackAdapter.contains("binding.cardTitle.setSelected(active);")
                        && leanbackAdapter.contains("binding.dateBadge.setSelected(active);")
                        && leanbackAdapter.contains("binding.fileSize.setSelected(active);"));
    }

    @Test
    public void detailAndPlaybackSourceLabelsMarqueeWhenActive() throws Exception {
        assertConstrainedMarquee("mobile source label", read(findMobileResPath().resolve(Path.of("layout", "adapter_flag.xml"))), "@+id/text");
        assertConstrainedMarquee("mobile TMDB source label", read(findMobileResPath().resolve(Path.of("layout", "adapter_flag_tmdb.xml"))), "@+id/text");
        assertConstrainedMarquee("TV source label", read(findLeanbackResPath().resolve(Path.of("layout", "adapter_flag.xml"))), "@+id/text");

        String detailActivity = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        assertTrue("programmatic detail labels must be width-constrained marquees",
                detailActivity.contains("button.setMaxWidth(ResUtil.dp2px(CHIP_MAX_WIDTH_DP));")
                        && detailActivity.contains("button.setEllipsize(TextUtils.TruncateAt.MARQUEE);")
                        && detailActivity.contains("button.setMarqueeRepeatLimit(-1);")
                        && detailActivity.contains("button.setHorizontallyScrolling(true);")
                        && detailActivity.contains("button.setSingleLine(true);"));
        assertTrue("programmatic detail labels must marquee while visible on mobile or active on TV",
                detailActivity.contains("button.setSelected(!Util.isLeanback() || selected || focused);"));
    }

    private static void assertMarquee(String owner, String layout, String id) throws Exception {
        Element element = findById(parseLayout(layout), id);
        assertTrue(owner + " must marquee only when its text overflows",
                "marquee".equals(androidAttribute(element, "ellipsize"))
                        && "marquee_forever".equals(androidAttribute(element, "marqueeRepeatLimit"))
                        && "true".equals(androidAttribute(element, "scrollHorizontally"))
                        && "true".equals(androidAttribute(element, "singleLine")));
    }

    private static void assertConstrainedMarquee(String owner, String layout, String id) throws Exception {
        Element element = findById(parseLayout(layout), id);
        assertMarquee(owner, layout, id);
        assertTrue(owner + " must have a finite width so overflow can occur",
                !"wrap_content".equals(androidAttribute(element, "layout_width"))
                        || !androidAttribute(element, "maxWidth").isEmpty());
    }

    private static Element parseLayout(String layout) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(layout.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }

    private static Element findById(Element root, String id) {
        if (id.equals(androidAttribute(root, "id"))) return root;
        NodeList nodes = root.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (id.equals(androidAttribute(element, "id"))) return element;
        }
        throw new AssertionError("Missing layout view " + id);
    }

    private static String androidAttribute(Element element, String name) {
        return element.getAttributeNS(ANDROID_NS, name);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
