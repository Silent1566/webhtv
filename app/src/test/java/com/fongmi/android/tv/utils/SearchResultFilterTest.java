package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SearchResultFilterTest {

    @Test
    public void matchesExactAndContainedTitles() {
        assertTrue(SearchResultFilter.matches("庆余年", "庆余年"));
        assertTrue(SearchResultFilter.matches("庆余年", "[腾讯] 庆余年 第二季 4K"));
        assertTrue(SearchResultFilter.matches("The Last of Us", "The.Last.of.Us.S01"));
    }

    @Test
    public void matchesEquivalentSeasonNotation() {
        assertTrue(SearchResultFilter.matches("庆余年2", "庆余年 第二季"));
        assertTrue(SearchResultFilter.matches("The Last of Us Season 2", "The Last of Us S02"));
    }

    @Test
    public void rejectsShortTitleFuzzyFalsePositives() {
        assertFalse(SearchResultFilter.matches("长相思", "长相守"));
        assertFalse(SearchResultFilter.matches("无间道2", "无间道3"));
        assertFalse(SearchResultFilter.matches("三体2023", "三体2024"));
    }

    @Test
    public void rejectsNumericPrefixButAllowsSeparateMetadata() {
        assertFalse(SearchResultFilter.matches("无间道2", "无间道20"));
        assertFalse(SearchResultFilter.matches("三体2", "三体2024"));
        assertTrue(SearchResultFilter.matches("无间道2", "无间道2 2024 4K"));
        assertTrue(SearchResultFilter.matches("The Last of Us Season 2", "The Last of Us S02E03"));
    }

    @Test
    public void acceptsHighSimilarityWhenNumbersAgree() {
        assertTrue(SearchResultFilter.matches("流浪地球2", "流浪地求2"));
        assertTrue(SearchResultFilter.matches("The Last of Us", "The Last of Us"));
    }

    @Test
    public void shortEnglishWordsUseWholeTokenMatching() {
        assertTrue(SearchResultFilter.matches("IT", "IT Chapter Two"));
        assertFalse(SearchResultFilter.matches("IT", "Little Women"));
    }

    @Test
    public void oneCharacterKeywordOnlyMatchesExactly() {
        assertFalse(SearchResultFilter.canFilter("囧"));
        assertTrue(SearchResultFilter.matches("囧", "囧"));
        assertFalse(SearchResultFilter.matches("囧", "泰囧"));
    }

    @Test
    public void normalizesTraditionalCaseAndFullWidthText() {
        assertTrue(SearchResultFilter.matches("慶餘年", "庆余年"));
        assertTrue(SearchResultFilter.matches("ＡＢＣ", "abc"));
    }

    @Test
    public void filterPreservesSourceOrder() {
        Vod first = vod("庆余年 第二季");
        Vod ignored = vod("长相守");
        Vod third = vod("庆余年");

        List<Vod> result = SearchResultFilter.filter(List.of(first, ignored, third), "庆余年", true);

        assertSame(first, result.get(0));
        assertSame(third, result.get(1));
    }

    @Test
    public void emptyFilterResultRemainsMutableForIncrementalTvUpdates() {
        List<Vod> result = SearchResultFilter.filter(List.of(), "庆余年", true);

        result.add(vod("庆余年"));

        assertEquals(1, result.size());
    }

    private Vod vod(String name) {
        return new Vod() {
            @Override
            public String getName() {
                return name;
            }
        };
    }
}
