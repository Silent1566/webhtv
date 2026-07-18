package com.fongmi.android.tv.model;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Trans;

import java.util.concurrent.Callable;

public record SearchTask(Site site, String keyword, boolean quick, String page) implements Callable<Result> {

    public SearchTask(Site site, String keyword, boolean quick, String page) {
        this.keyword = Trans.t2s(keyword);
        this.quick = quick;
        this.page = page;
        this.site = site;
    }

    public static SearchTask create(Site site, String keyword, boolean quick) {
        return new SearchTask(site, keyword, quick, "1");
    }

    public static SearchTask create(Site site, String keyword, boolean quick, String page) {
        return new SearchTask(site, keyword, quick, page);
    }

    @Override
    public Result call() throws Exception {
        if (quick && !site.isQuickSearch()) return Result.empty();
        return SiteApi.searchContent(site, keyword, quick, page);
    }
}
