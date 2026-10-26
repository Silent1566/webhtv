package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.TmdbConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TMDB 请求语言：用户显式配置过就尊重用户，否则跟随界面语言的中文变体。
 *
 * <p>背景：{@code TmdbConfig.language} 经 sanitize 后总是非空（缺省 {@code zh-CN}），
 * 单看值无法判断用户是否表达过偏好。早期方案想直接让请求语言跟随界面语言，
 * 那样会覆盖用户显式选择（例如用户故意「繁体界面 + 简体 TMDB 数据」），
 * 所以先引入 {@code languageConfigured} 标记再跟随。
 */
public class TmdbLanguageFollowUiTest {

    private static final int FOLLOW_SYSTEM = 0;
    private static final int SIMPLIFIED = 1;
    private static final int TRADITIONAL = 2;

    @Test
    public void unconfiguredConfigFollowsTraditionalUi() {
        TmdbConfig config = new TmdbConfig();

        assertFalse("未保存过语言的配置不能算「已配置」", config.isLanguageConfigured());
        assertEquals("zh-TW", TmdbLanguagePolicy.requestLanguage(config, TRADITIONAL));
    }

    @Test
    public void unconfiguredConfigFollowsSimplifiedUi() {
        TmdbConfig config = new TmdbConfig();

        assertEquals("zh-CN", TmdbLanguagePolicy.requestLanguage(config, SIMPLIFIED));
    }

    @Test
    public void followSystemUiKeepsConfiguredDefault() {
        TmdbConfig config = new TmdbConfig();

        // 跟随系统时界面语言不是明确的中文变体，不干预，保持配置值。
        assertEquals(TmdbLanguagePolicy.requestLanguage(config), TmdbLanguagePolicy.requestLanguage(config, FOLLOW_SYSTEM));
    }

    @Test
    public void explicitlyConfiguredLanguageAlwaysWins() {
        TmdbConfig config = new TmdbConfig();
        config.setLanguage("zh-CN");

        assertTrue(config.isLanguageConfigured());
        assertEquals("繁体界面下也必须尊重用户选定的 zh-CN",
                "zh-CN", TmdbLanguagePolicy.requestLanguage(config, TRADITIONAL));
    }

    @Test
    public void explicitlyConfiguredNonChineseLanguageIsUntouched() {
        TmdbConfig config = new TmdbConfig();
        config.setLanguage("en-US");

        assertEquals("en-US", TmdbLanguagePolicy.requestLanguage(config, TRADITIONAL));
    }

    @Test
    public void languageForInterfaceOnlyHandlesChineseVariants() {
        assertEquals("zh-CN", TmdbLanguagePolicy.languageForInterface(SIMPLIFIED));
        assertEquals("zh-TW", TmdbLanguagePolicy.languageForInterface(TRADITIONAL));
        assertEquals("", TmdbLanguagePolicy.languageForInterface(FOLLOW_SYSTEM));
        assertEquals("", TmdbLanguagePolicy.languageForInterface(99));
    }
}
