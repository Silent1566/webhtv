package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

public class EpisodeListDialog extends AppCompatDialogFragment implements FlagAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private DialogEpisodeListBinding binding;
    private EpisodeGroupAdapter groupAdapter;
    private EpisodeAdapter episodeAdapter;
    private SpaceItemDecoration episodeDecoration;
    private FlagAdapter flagAdapter;
    private List<Flag> flags;
    private int episodeSpanCount = 4;
    private boolean reverse;
    private boolean tmdbCard;

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
        return this;
    }

    public EpisodeListDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public EpisodeListDialog tmdbCard(boolean tmdbCard) {
        this.tmdbCard = tmdbCard;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogEpisodeListBinding.inflate(inflater, container, false);
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(view -> dismiss());
        binding.getRoot().setClickable(true);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getWidth(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        overlay.addView(binding.getRoot(), params);
        return overlay;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
    }

    private int getWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        return Math.max(ResUtil.dp2px(360), Math.min(ResUtil.dp2px(560), Math.round(screen * 0.44f)));
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        Util.hideSystemUI(window);
    }

    private void initView() {
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        setGroups(getSelectedFlag());
        binding.flag.scrollToPosition(flagAdapter.getPosition());
    }

    private void setRecyclerView() {
        binding.flag.setHasFixedSize(true);
        binding.flag.setItemAnimator(null);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        binding.group.setHasFixedSize(true);
        binding.group.setItemAnimator(null);
        binding.group.setAdapter(groupAdapter = new EpisodeGroupAdapter(this));
        binding.episode.setHasFixedSize(true);
        binding.episode.setItemAnimator(null);
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        episodeAdapter.setUseTmdbCard(tmdbCard);
        binding.episode.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncEpisodeGroupByScroll();
            }
        });
    }

    private Flag getSelectedFlag() {
        if (flagAdapter.isEmpty()) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setGroups(Flag flag) {
        if (flag == null) return;
        List<Episode> episodes = flag.getEpisodes();
        int maxGroupSize = tmdbCard ? EpisodeRangePolicy.CARD_PAGE_MAX_SIZE : 0;
        groupAdapter.addAll(EpisodeGroupAdapter.build(episodes.size(), getSelectedEpisodePosition(episodes), reverse, maxGroupSize));
        setEpisodes(episodes);
        binding.group.scrollToPosition(groupAdapter.getPosition());
        binding.episode.scrollToPosition(episodeAdapter.getPosition());
    }

    private void setEpisodes(List<Episode> episodes) {
        setEpisodeItems(episodes);
        selectEpisodeGroupByPosition(episodeAdapter.getPosition());
    }

    private void setEpisodeItems(List<Episode> episodes) {
        episodeAdapter.setUseTmdbCard(tmdbCard);
        updateEpisodeSpan(episodes);
        episodeAdapter.addAll(episodes);
    }

    private void updateEpisodeSpan(List<Episode> episodes) {
        int span = getEpisodeSpan(episodes);
        if (span == episodeSpanCount) return;
        episodeSpanCount = span;
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        if (episodeDecoration != null) binding.episode.removeItemDecoration(episodeDecoration);
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
    }

    private int getEpisodeSpan(List<Episode> episodes) {
        if (tmdbCard) return 2;
        EpisodeTitleCompact.apply(episodes);
        int maxLen = 0;
        for (Episode item : episodes) maxLen = Math.max(maxLen, item.getDisplayName().length());
        if (maxLen >= 12) return PlayerSetting.getEpisodeColumn();
        int ideal = maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int available = Math.max(ResUtil.dp2px(240), getWidth() - ResUtil.dp2px(28));
        int span = available / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(4, span));
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public void onItemClick(Flag item) {
        ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setGroups(item);
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        groupAdapter.setSelected(item);
        scrollEpisodeToPosition(item.start);
        binding.group.scrollToPosition(groupAdapter.getPosition());
    }

    private void syncEpisodeGroupByScroll() {
        RecyclerView.LayoutManager manager = binding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)) return;
        int position = getEpisodeGroupSyncPosition((GridLayoutManager) manager);
        if (position == RecyclerView.NO_POSITION) return;
        selectEpisodeGroupByPosition(position);
    }

    private int getEpisodeGroupSyncPosition(GridLayoutManager manager) {
        if (!binding.episode.canScrollVertically(1) && binding.episode.canScrollVertically(-1)) {
            return manager.findLastVisibleItemPosition();
        }
        return manager.findFirstVisibleItemPosition();
    }

    private void selectEpisodeGroupByPosition(int position) {
        if (groupAdapter == null || groupAdapter.isEmpty()) return;
        int current = groupAdapter.getPosition();
        List<EpisodeGroupAdapter.Group> groups = groupAdapter.getItems();
        for (int i = 0; i < groups.size(); i++) {
            EpisodeGroupAdapter.Group group = groups.get(i);
            if (position < group.start || position >= group.end) continue;
            if (i != current) {
                groupAdapter.setSelected(group);
                binding.group.scrollToPosition(i);
            }
            return;
        }
    }

    private void scrollEpisodeToPosition(int position) {
        RecyclerView.LayoutManager manager = binding.episode.getLayoutManager();
        if (manager instanceof GridLayoutManager) ((GridLayoutManager) manager).scrollToPositionWithOffset(position, 0);
        else binding.episode.scrollToPosition(position);
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }
}
