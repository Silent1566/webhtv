package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultLayoutTest {

    @Test
    public void collectActivityUsesSearchLayoutSettingForCardRatio() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String source = read(sourcePath);
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_collect.xml"));
        String layout = read(layoutPath);

        assertTrue("TV search results must read the portrait/landscape layout setting",
                source.contains("Setting.getSearchUi()"));
        assertTrue("TV search results need a horizontal site row for landscape layout",
                layout.contains("android:id=\"@+id/collectHorizontal\""));
        assertTrue("Horizontal site row must not consume the result area height",
                layout.contains("android:layout_height=\"56dp\""));
        assertTrue("Landscape layout must show the horizontal site row",
                source.contains("mBinding.collectHorizontal.setVisibility(horizontal ? android.view.View.VISIBLE : android.view.View.GONE);"));
        assertTrue("Landscape layout must hide the vertical side site list",
                source.contains("mBinding.collect.setVisibility(horizontal ? android.view.View.GONE : android.view.View.VISIBLE);"));
        assertTrue("Landscape result grid should align with the title and horizontal site row",
                source.contains("mBinding.recycler.setPadding(ResUtil.dp2px(horizontal ? 24 : 0), 0, ResUtil.dp2px(24), ResUtil.dp2px(24));"));
        assertTrue("Landscape result grid should avoid overly narrow cards",
                source.contains("Math.min(isSearchLandscape() ? 6 : 7"));
        assertTrue("Landscape layout should keep the normal poster card ratio while using the wider result area",
                source.contains("SEARCH_CARD_RATIO"));
        assertTrue("Changing the layout setting should rebuild the result grid",
                source.contains("updateRecyclerLayout();"));
    }

    @Test
    public void listModeUsesCompactSearchRowsInsteadOfPosterHeight() throws Exception {
        Path collectPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String collect = read(collectPath);
        Path adapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "SearchAdapter.java"));
        String adapter = read(adapterPath);
        Path listLayoutPath = findLeanbackResPath().resolve(Path.of("layout", "adapter_search_list.xml"));
        String listLayout = read(listLayoutPath);

        assertTrue("TV list mode should have a fixed compact row height",
                collect.contains("SEARCH_LIST_ROW_HEIGHT_DP"));
        assertTrue("TV list mode must not calculate height from full-width poster ratio",
                collect.contains("return isListMode(count) ? ResUtil.dp2px(SEARCH_LIST_ROW_HEIGHT_DP) : (int) (getItemWidth(count) / SEARCH_CARD_RATIO);"));
        assertTrue("Search adapter must receive whether it is rendering compact list rows",
                collect.contains("new SearchAdapter(this, getItemWidth(count), getItemHeight(count), isListMode(count))"));
        assertTrue("TV search adapter should inflate a dedicated list row layout",
                adapter.contains("AdapterSearchListBinding"));
        assertTrue("Compact list row should keep a modest fixed XML preview height",
                listLayout.contains("android:layout_height=\"116dp\""));
        assertTrue("Compact list row should use a side poster instead of a full-width image",
                listLayout.contains("android:layout_toEndOf=\"@+id/image\""));
    }

    @Test
    public void personalSettingsExposeSearchLayoutSwitch() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java"));
        String source = read(sourcePath);
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_setting_personal.xml"));
        String layout = read(layoutPath);
        int searchUi = layout.indexOf("android:id=\"@+id/searchUi\"");
        int searchColumn = layout.indexOf("android:id=\"@+id/searchColumn\"");
        String searchUiSection = layout.substring(searchUi, searchColumn);

        assertTrue("TV personal settings must bind the search layout switch",
                source.contains("mBinding.searchUi.setOnClickListener(this::setSearchUi);"));
        assertTrue("TV personal settings must show the current search layout label",
                source.contains("mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);"));
        assertFalse("Search layout setting should be visible in TV personal settings",
                searchUiSection.contains("android:visibility=\"gone\""));
    }

    @Test
    public void personalSettingsExposePlaybackSpeedControlsOnTvAndMobile() throws Exception {
        Path tvSourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java"));
        String tvSource = read(tvSourcePath);
        Path tvLayoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_setting_personal.xml"));
        String tvLayout = read(tvLayoutPath);
        Path mobileSourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPersonalFragment.java"));
        String mobileSource = read(mobileSourcePath);
        Path mobileLayoutPath = findMobileResPath().resolve(Path.of("layout", "fragment_setting_personal.xml"));
        String mobileLayout = read(mobileLayoutPath);

        assertTrue("TV personal settings must include playback speed entry",
                tvLayout.contains("android:id=\"@+id/playSpeed\""));
        assertTrue("TV personal settings must open playback speed dialog",
                tvSource.contains("mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);"));
        assertTrue("TV playback speed setting must persist the default speed",
                tvSource.contains("PlayerSetting.putDefaultSpeed(value);"));
        assertFalse("TV personal settings must not duplicate the existing long-press speed setting",
                tvLayout.contains("@string/player_speed"));

        assertTrue("Mobile personal settings must include playback speed entry",
                mobileLayout.contains("android:id=\"@+id/playSpeed\""));
        assertTrue("Mobile personal settings must open playback speed dialog",
                mobileSource.contains("mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);"));
        assertTrue("Mobile playback speed setting must persist the default speed",
                mobileSource.contains("PlayerSetting.putDefaultSpeed(value);"));
        assertFalse("Mobile personal settings must not duplicate the existing long-press speed setting",
                mobileLayout.contains("@string/player_speed"));
    }

    @Test
    public void traditionalChineseResourcesIncludeSearchLayoutOptions() throws Exception {
        Path stringsPath = findMainResPath().resolve(Path.of("values-zh-rTW", "strings.xml"));
        String strings = read(stringsPath);

        assertTrue("Traditional Chinese resources must include the search layout options",
                strings.contains("<string-array name=\"select_search_ui\">"));
        assertTrue("Traditional Chinese resources must include the playback speed label",
                strings.contains("<string name=\"setting_play_speed\">"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
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
}
