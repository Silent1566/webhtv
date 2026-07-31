package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityWebThemeDetailBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.TmdbSitePolicy;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.helper.TmdbUIAdapter;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.WebThemeDetailMetadata;
import com.fongmi.android.tv.web.WebThemePage;
import com.fongmi.android.tv.web.WebThemeRoute;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class WebThemeDetailActivity extends BaseActivity implements HomeWebController.Listener {

    private static final String EXTRA_MANIFEST = "manifest";
    private static final String EXTRA_SITE_KEY = "siteKey";
    private static final String EXTRA_VOD_ID = "vodId";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_PIC = "pic";
    private static final String EXTRA_REMARKS = "remarks";
    private static final String EXTRA_CONTENT = "content";

    private ActivityWebThemeDetailBinding mBinding;
    private HomeWebController controller;
    private TmdbUIAdapter tmdbAdapter;
    private Site site;
    private Vod tmdbVod;
    private String vodId;
    private String title;
    private String pic;
    private String remarks;
    private String content;
    private boolean fallbackStarted;

    public static void start(Activity activity, String manifestUrl, String siteKey, String vodId,
            String title, String pic, String remarks) {
        start(activity, manifestUrl, siteKey, vodId, title, pic, remarks, "");
    }

    public static void start(Activity activity, String manifestUrl, String siteKey, String vodId,
            String title, String pic, String remarks, String content) {
        Intent intent = new Intent(activity, WebThemeDetailActivity.class);
        intent.putExtra(EXTRA_MANIFEST, manifestUrl);
        intent.putExtra(EXTRA_SITE_KEY, siteKey);
        intent.putExtra(EXTRA_VOD_ID, vodId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_PIC, pic);
        intent.putExtra(EXTRA_REMARKS, remarks);
        intent.putExtra(EXTRA_CONTENT, content);
        activity.startActivity(intent);
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityWebThemeDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String manifestUrl = value(getIntent().getStringExtra(EXTRA_MANIFEST));
        String siteKey = value(getIntent().getStringExtra(EXTRA_SITE_KEY));
        vodId = value(getIntent().getStringExtra(EXTRA_VOD_ID));
        title = value(getIntent().getStringExtra(EXTRA_TITLE));
        pic = value(getIntent().getStringExtra(EXTRA_PIC));
        remarks = value(getIntent().getStringExtra(EXTRA_REMARKS));
        content = value(getIntent().getStringExtra(EXTRA_CONTENT));
        site = VodConfig.get().getSite(siteKey);
        if (site == null || vodId.isEmpty()) {
            finish();
            return;
        }
        tmdbAdapter = new TmdbUIAdapter(this);
        controller = new HomeWebController(this, mBinding.web, this);
        controller.setViewport(getViewport());
        boolean accepted;
        try {
            accepted = controller.loadThemePage(site, manifestUrl, WebThemePage.DETAIL,
                    WebThemeRoute.detail(vodId, title, pic, remarks, content));
        } catch (RuntimeException ignored) {
            accepted = false;
        }
        if (!accepted) fallbackToNative();
    }

    @Override
    public void onWebLoading() {
        mBinding.loading.setVisibility(View.VISIBLE);
    }

    @Override
    public void onWebReady() {
        mBinding.loading.setVisibility(View.GONE);
        mBinding.web.requestFocus();
    }

    @Override
    public void onWebError() {
        fallbackToNative();
    }

    @Override
    public void onDetailVodLoaded(Vod vod) {
        runOnUiThread(() -> {
            if (tmdbAdapter == null || vod == null || vod == tmdbVod || isFinishing() || isDestroyed()) return;
            if (!TmdbSitePolicy.isEnabled(site.getKey(), vodId)) return;
            tmdbVod = vod;
            tmdbAdapter.autoMatch(vod.getName(), vod);
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event == null || controller == null || tmdbAdapter == null || event.getVod() != tmdbVod) return;
        RefreshEvent.Type type = event.getType();
        if (type != RefreshEvent.Type.VOD_CORE && type != RefreshEvent.Type.VOD_RECOMMENDATIONS
                && type != RefreshEvent.Type.VOD_EPISODE_TITLES) return;
        Vod vod = event.getVod();
        controller.setDetailMetadata(WebThemeDetailMetadata.fromTmdb(
                tmdbAdapter.getTmdbItem(),
                tmdbAdapter.getTmdbDetail(),
                tmdbAdapter.getCast(),
                tmdbAdapter.getCreators(),
                tmdbAdapter.getPhotos(),
                tmdbAdapter.getRecommendations()));
        controller.dispatchDetailChanged();
    }

    private void fallbackToNative() {
        if (fallbackStarted || site == null || isFinishing() || isDestroyed()) return;
        fallbackStarted = true;
        TmdbDetailActivity.start(this, site.getKey(), vodId, title, pic, remarks);
        finish();
    }

    @Override
    protected void onBackInvoked() {
        if (controller == null || !controller.handleBack()) finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller != null) {
            controller.onResume();
            if (tmdbVod != null) controller.dispatchDetailChanged();
        }
    }

    @Override
    protected void onPause() {
        if (controller != null) controller.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tmdbAdapter != null) tmdbAdapter.release();
        if (controller != null) controller.destroy();
        super.onDestroy();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
