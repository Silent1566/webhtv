package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.EpisodePhotoAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * 剧集详情对话框
 */
public class EpisodeDetailDialog {

    public static void show(FragmentActivity activity, Episode episode) {
        show(activity, episode, null);
    }

    public static void show(FragmentActivity activity, Episode episode, Site site) {
        TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
        if (tmdbEpisode == null) {
            // 电影没有分集对象，尝试从宿主获取影片级数据
            if (activity instanceof com.fongmi.android.tv.ui.host.TmdbDetailHost) {
                com.fongmi.android.tv.ui.host.TmdbDetailHost host = (com.fongmi.android.tv.ui.host.TmdbDetailHost) activity;
                com.fongmi.android.tv.bean.TmdbItem movieItem = host.getMatchedTmdbItem();
                if (movieItem != null && movieItem.isMovie()) {
                    showMovieDetail(activity, episode, movieItem);
                    return;
                }
            }
            // 没有TMDB数据，显示简单信息
            showSimpleDialog(activity, episode);
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);

        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView rating = view.findViewById(R.id.rating);
        TextView date = view.findViewById(R.id.date);
        TextView runtime = view.findViewById(R.id.runtime);
        TextView overview = view.findViewById(R.id.overview);
        TextView photosLabel = view.findViewById(R.id.photosLabel);
        androidx.leanback.widget.HorizontalGridView photosGrid = view.findViewById(R.id.photosGrid);
        TextView guestsLabel = view.findViewById(R.id.guestsLabel);
        androidx.leanback.widget.HorizontalGridView guestsGrid = view.findViewById(R.id.guestsGrid);

        // 加载剧照 - 使用原图
        if (!tmdbEpisode.getStillUrl().isEmpty()) {
            Glide.with(activity)
                    .load(tmdbImageUrl(tmdbEpisode.getStillUrl(), "original"))
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .fitCenter()
                    .into(still);
            still.setVisibility(View.VISIBLE);
        } else {
            still.setVisibility(View.GONE);
        }

        // 设置标题
        title.setText(tmdbEpisode.getDisplayTitle());

        // 显示原始名称（刮削前的源站文件名）
        String sourceName = episode.getName();
        String displayTitle = tmdbEpisode.getDisplayTitle();
        // 只有当原始名称非空且与刮削标题不完全相同时才显示
        if (!android.text.TextUtils.isEmpty(sourceName) && !sourceName.equals(displayTitle)) {
            originalName.setText("原始名称：" + sourceName);
            originalName.setVisibility(View.VISIBLE);
        } else {
            originalName.setVisibility(View.GONE);
        }

        // 设置评分
        if (tmdbEpisode.getVoteAverage() > 0) {
            rating.setText(String.format("★ %.1f", tmdbEpisode.getVoteAverage()));
            rating.setVisibility(View.VISIBLE);
        } else {
            rating.setVisibility(View.GONE);
        }

        // 设置日期
        if (!tmdbEpisode.getDate().isEmpty()) {
            date.setText(String.format("播出日期: %s", tmdbEpisode.getDate()));
            date.setVisibility(View.VISIBLE);
        } else {
            date.setVisibility(View.GONE);
        }

        // 设置时长
        if (tmdbEpisode.getRuntime() > 0) {
            runtime.setText(String.format("时长: %d 分钟", tmdbEpisode.getRuntime()));
            runtime.setVisibility(View.VISIBLE);
        } else {
            runtime.setVisibility(View.GONE);
        }

        // 设置简介
        if (!tmdbEpisode.getOverview().isEmpty()) {
            overview.setText(tmdbEpisode.getOverview());
            overview.setVisibility(View.VISIBLE);
        } else {
            overview.setText("暂无简介");
        }

        // 初始隐藏本集图片，异步加载
        photosLabel.setVisibility(View.GONE);
        photosGrid.setVisibility(View.GONE);
        guestsLabel.setVisibility(View.GONE);
        guestsGrid.setVisibility(View.GONE);

        // 异步加载本集图片与客串演员
        loadEpisodeMedia(activity, tmdbEpisode, site, photosLabel, photosGrid, guestsLabel, guestsGrid);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(view);

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        // 设置全屏显示，不透明背景
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            // 使用纯色背景（布局中已有半透明黑色背景）
            alertDialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
    }

    /**
     * 电影场景（TV版简化版）：长按线路展示 TmdbItem 基本信息
     * TV端 VideoActivity 只持有 TmdbItem，没有 detail JSON，
     * 所以不加载演员与剧照，只显示标题/评分/简介。
     */
    private static void showMovieDetail(FragmentActivity activity, Episode episode, com.fongmi.android.tv.bean.TmdbItem movieItem) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);

        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView rating = view.findViewById(R.id.rating);
        TextView date = view.findViewById(R.id.date);
        TextView runtime = view.findViewById(R.id.runtime);
        TextView overview = view.findViewById(R.id.overview);
        TextView photosLabel = view.findViewById(R.id.photosLabel);
        androidx.leanback.widget.HorizontalGridView photosGrid = view.findViewById(R.id.photosGrid);
        TextView guestsLabel = view.findViewById(R.id.guestsLabel);
        androidx.leanback.widget.HorizontalGridView guestsGrid = view.findViewById(R.id.guestsGrid);

        // 背景图：影片 backdrop
        String backdrop = movieItem.getBackdropUrl();
        if (!android.text.TextUtils.isEmpty(backdrop)) {
            Glide.with(activity)
                    .load(backdrop)
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .fitCenter()
                    .into(still);
            still.setVisibility(View.VISIBLE);
        } else {
            still.setVisibility(View.GONE);
        }

        // 标题：影片名
        String movieTitle = movieItem.getTitle();
        title.setText(android.text.TextUtils.isEmpty(movieTitle) ? episode.getName() : movieTitle);

        // 原始名称：源站文件名，完整多行展示
        String sourceName = episode.getName();
        if (!android.text.TextUtils.isEmpty(sourceName) && !sourceName.equals(movieTitle)) {
            originalName.setText("原始名称：" + sourceName);
            originalName.setVisibility(View.VISIBLE);
        } else {
            originalName.setVisibility(View.GONE);
        }

        // 评分
        if (movieItem.getRating() > 0) {
            rating.setText(String.format("★ %.1f", movieItem.getRating()));
            rating.setVisibility(View.VISIBLE);
        } else {
            rating.setVisibility(View.GONE);
        }

        // 日期/时长：TmdbItem 里没有，隐藏
        date.setVisibility(View.GONE);
        runtime.setVisibility(View.GONE);

        // 简介
        String movieOverview = movieItem.getOverview();
        if (!android.text.TextUtils.isEmpty(movieOverview)) {
            overview.setText(movieOverview);
        } else {
            overview.setText("暂无简介");
        }

        // 演员与剧照：VideoActivity 没有 detail JSON，无法加载，隐藏
        photosLabel.setVisibility(View.GONE);
        photosGrid.setVisibility(View.GONE);
        guestsLabel.setVisibility(View.GONE);
        guestsGrid.setVisibility(View.GONE);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(view);

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        // 设置全屏显示
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            alertDialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
    }

    private static void showSimpleDialog(FragmentActivity activity, Episode episode) {
        // 标题放固定文案，源站文件名放可换行的正文，避免长名被单行标题截断
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.detail_tmdb_empty)
                .setMessage(episode.getName())
                .setPositiveButton("关闭", null)
                .show();
    }

    private static void loadEpisodeMedia(FragmentActivity activity, TmdbEpisode tmdbEpisode, Site site,
                                         TextView photosLabel, androidx.leanback.widget.HorizontalGridView photosGrid,
                                         TextView guestsLabel, androidx.leanback.widget.HorizontalGridView guestsGrid) {
        // 只有有tmdbId才能加载图片
        if (tmdbEpisode.getTmdbId() == 0) {
            android.util.Log.d("EpisodeDetail", "跳过图片加载: tmdbId为0");
            return;
        }

        android.util.Log.d("EpisodeDetail", "开始加载图片: tmdbId=" + tmdbEpisode.getTmdbId() +
            ", season=" + tmdbEpisode.getSeasonNumber() + ", episode=" + tmdbEpisode.getNumber());

        new Thread(() -> {
            try {
                // 调用TMDB API获取剧集图片
                TmdbService service = new TmdbService();
                TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());

                android.util.Log.d("EpisodeDetail", "开始请求TMDB API...");

                JsonObject episodeJson = service.episode(
                    tmdbEpisode.getTmdbId(),
                    tmdbEpisode.getSeasonNumber(),
                    tmdbEpisode.getNumber(),
                    config
                );

                android.util.Log.d("EpisodeDetail", "TMDB API返回成功");

                List<String> photos = service.episodePhotos(episodeJson, config);
                List<TmdbPerson> guests = service.episodeGuests(episodeJson, config);

                android.util.Log.d("EpisodeDetail", "图片数量: " + (photos != null ? photos.size() : 0));
                android.util.Log.d("EpisodeDetail", "客串演员数量: " + (guests != null ? guests.size() : 0));

                if ((photos != null && !photos.isEmpty()) || (guests != null && !guests.isEmpty())) {
                    activity.runOnUiThread(() -> {
                        if (photos != null && !photos.isEmpty()) {
                            photosLabel.setVisibility(View.VISIBLE);
                            photosGrid.setVisibility(View.VISIBLE);
                            photosGrid.setHorizontalSpacing(ResUtil.dp2px(12));
                            photosGrid.setRowHeight(ResUtil.dp2px(124));

                            EpisodePhotoAdapter photoAdapter = new EpisodePhotoAdapter(photos,
                                    (url, position) -> PhotoViewerDialog.show(activity, photos, position, null));
                            photosGrid.setAdapter(photoAdapter);
                        }

                        if (guests != null && !guests.isEmpty()) {
                            guestsLabel.setVisibility(View.VISIBLE);
                            guestsGrid.setVisibility(View.VISIBLE);
                            guestsGrid.setHorizontalSpacing(ResUtil.dp2px(12));
                            guestsGrid.setRowHeight(ResUtil.dp2px(154));

                            TmdbPersonAdapter guestAdapter = new TmdbPersonAdapter(person -> TmdbPersonDialog.show(activity, person, site));
                            guestAdapter.setItems(guests);
                            guestsGrid.setAdapter(guestAdapter);
                        }

                        android.util.Log.d("EpisodeDetail", "图片显示成功");
                    });
                } else {
                    android.util.Log.d("EpisodeDetail", "没有图片数据");
                }
            } catch (Exception e) {
                // 加载失败，保持隐藏状态
                android.util.Log.e("EpisodeDetail", "加载图片失败", e);
            }
        }).start();
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst("(/t/p/)([^/]+)(/)", "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }
}
