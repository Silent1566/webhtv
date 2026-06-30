package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.BaseGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class EpisodeListDialog extends BaseAlertDialog implements FlagAdapter.OnClickListener, ArrayAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private final List<Integer> segmentStarts;
    private final List<Integer> segmentEnds;
    private final List<Episode> allEpisodes;

    private DialogEpisodeListBinding binding;
    private EpisodeAdapter episodeAdapter;
    private ArrayAdapter arrayAdapter;
    private FlagAdapter flagAdapter;
    private DialogInterface.OnDismissListener dismissListener;
    private List<Flag> flags;
    private int panelWidth;
    private boolean tmdbCard;
    private int selectedSegment;

    public EpisodeListDialog() {
        segmentStarts = new ArrayList<>();
        segmentEnds = new ArrayList<>();
        allEpisodes = new ArrayList<>();
    }

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
        return this;
    }

    public EpisodeListDialog tmdbCard(boolean tmdbCard) {
        this.tmdbCard = tmdbCard;
        return this;
    }

    public EpisodeListDialog dismissListener(DialogInterface.OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpisodeListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        panelWidth = getPanelWidth();
        if (tmdbCard) {
            binding.getRoot().setBackgroundColor(0x66111820);
            binding.getRoot().setPadding(ResUtil.dp2px(48), ResUtil.dp2px(34), ResUtil.dp2px(48), ResUtil.dp2px(26));
        }
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        setEpisodes(getSelectedFlag());
        binding.flag.setSelectedPosition(flagAdapter.getPosition());
    }

    private void setRecyclerView() {
        int spacing = ResUtil.dp2px(8);
        binding.flag.setHorizontalSpacing(spacing);
        binding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        flagAdapter.setOnKeyListener((view, keyCode, event) -> onFlagKey(event));
        binding.array.setHorizontalSpacing(spacing);
        binding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.array.setAdapter(arrayAdapter = new ArrayAdapter(this));
        arrayAdapter.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        binding.array.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        binding.episode.setHorizontalSpacing(spacing);
        binding.episode.setVerticalSpacing(spacing);
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, getEpisodeContentWidth()));
        episodeAdapter.setUseTmdbCard(tmdbCard);
        episodeAdapter.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        binding.episode.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        binding.flag.setOnKeyListener((view, keyCode, event) -> onFlagKey(event));
        binding.array.addOnChildViewHolderSelectedListener(new androidx.leanback.widget.OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null) selectSegment(position, true);
            }
        });
    }

    private int getPanelWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        if (tmdbCard) return screen;
        return Math.max(ResUtil.dp2px(420), Math.min(ResUtil.dp2px(680), Math.round(screen * 0.42f)));
    }

    private int getEpisodeContentWidth() {
        return panelWidth - ResUtil.dp2px(40);
    }

    private Flag getSelectedFlag() {
        if (flagAdapter == null || flagAdapter.getItemCount() == 0) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setEpisodes(Flag flag) {
        if (flag == null) return;
        List<Episode> episodes = flag.getEpisodes();
        allEpisodes.clear();
        allEpisodes.addAll(episodes);
        int column = tmdbCard ? getTmdbCardColumn() : EpisodeAdapter.getColumn(episodes, getEpisodeContentWidth());
        binding.episode.setNumColumns(column);
        episodeAdapter.setUseTmdbCard(tmdbCard);
        episodeAdapter.setGridMode(tmdbCard);
        episodeAdapter.setVerticalGridMode(true);
        episodeAdapter.setColumn(column);
        setSegments(episodes.size());
        setSegmentEpisodes(selectedSegment);
        scrollToSelectedEpisode();
    }

    private void setSegments(int size) {
        int segment = tmdbCard ? getTmdbEpisodeSegmentSize(size) : getEpisodeSegmentSize(size);
        List<String> items = new ArrayList<>();
        int selectedEpisode = getSelectedEpisodePosition(allEpisodes);
        segmentStarts.clear();
        segmentEnds.clear();
        if (tmdbCard || size > segment) for (int i = 0; i < size; i += segment) {
            segmentStarts.add(i);
            int end = Math.min(i + segment, size);
            segmentEnds.add(end);
            items.add((i + 1) + "-" + end);
        }
        selectedSegment = resolveSelectedSegment(selectedEpisode);
        arrayAdapter.setSegmentSize(segment);
        arrayAdapter.addAll(items);
        binding.array.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        flagAdapter.setNextFocusDown(items.isEmpty() ? R.id.episode : R.id.array);
        arrayAdapter.setNextFocus(R.id.flag, R.id.episode);
        episodeAdapter.setNextFocusUp(items.isEmpty() ? R.id.flag : R.id.array);
        episodeAdapter.setNextFocusDown(0);
    }

    private int resolveSelectedSegment(int episodePosition) {
        if (segmentStarts.isEmpty()) return 0;
        for (int i = 0; i < segmentStarts.size(); i++) {
            if (episodePosition >= segmentStarts.get(i) && episodePosition < segmentEnds.get(i)) return i;
        }
        return 0;
    }

    private void selectSegment(int position, boolean keepSegmentFocus) {
        if (position < 0 || position >= segmentStarts.size() || position == selectedSegment) return;
        selectedSegment = position;
        setSegmentEpisodes(position);
        if (keepSegmentFocus) keepArrayFocus(position);
    }

    private void setSegmentEpisodes(int position) {
        if (segmentStarts.isEmpty()) {
            episodeAdapter.addAll(allEpisodes);
            return;
        }
        position = Math.max(0, Math.min(position, segmentStarts.size() - 1));
        int start = segmentStarts.get(position);
        int end = segmentEnds.get(position);
        episodeAdapter.addAll(EpisodeRangePolicy.slice(allEpisodes, new EpisodeRangePolicy.Range("", start, end, position == selectedSegment)));
    }

    private void keepArrayFocus(int position) {
        binding.array.post(() -> {
            if (binding == null) return;
            binding.array.setSelectedPosition(position);
            RecyclerView.ViewHolder holder = binding.array.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
            else binding.array.requestFocus();
        });
    }

    private boolean onFlagKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
        focusLowerFromFlag();
        return true;
    }

    private boolean onArrayKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return false;
        if (KeyUtil.isUpKey(event)) {
            focusFlag();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            focusEpisodeFromArray();
            return true;
        }
        return false;
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
        int position = getFocusedPosition(binding.episode);
        int column = Math.max(1, episodeAdapter.getColumn());
        if (position == RecyclerView.NO_POSITION || position >= column) return false;
        focusUpperFromEpisode();
        return true;
    }

    private void focusUpperFromEpisode() {
        if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
        else focusFlag();
    }

    private void focusLowerFromFlag() {
        if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
        else focusPosition(binding.episode, episodeAdapter.getPosition());
    }

    private void focusEpisodeFromArray() {
        int position = binding.array.getSelectedPosition();
        selectSegment(position, false);
        focusPosition(binding.episode, getSegmentFocusPosition(position));
    }

    private int getSegmentFocusPosition(int position) {
        if (position < 0 || position >= segmentStarts.size()) return episodeAdapter.getPosition();
        int selectedEpisode = getSelectedEpisodePosition(allEpisodes);
        int start = segmentStarts.get(position);
        int end = segmentEnds.get(position);
        return selectedEpisode >= start && selectedEpisode < end ? selectedEpisode - start : 0;
    }

    private void focusFlag() {
        focusPosition(binding.flag, flagAdapter.getPosition());
    }

    private void focusArray() {
        int position = Math.max(0, binding.array.getSelectedPosition());
        focusPosition(binding.array, position);
    }

    private int getFocusedPosition(RecyclerView recycler) {
        View child = recycler.getFocusedChild();
        return child == null ? RecyclerView.NO_POSITION : recycler.getChildAdapterPosition(child);
    }

    private void focusPosition(BaseGridView grid, int position) {
        if (grid.getAdapter() == null || grid.getAdapter().getItemCount() == 0) return;
        position = Math.max(0, Math.min(position, grid.getAdapter().getItemCount() - 1));
        int target = position;
        grid.setSelectedPosition(target);
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.requestFocus();
            else grid.requestFocus();
        });
    }

    private boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    private int getEpisodeSegmentSize(int size) {
        return size <= 60 ? 20 : 40;
    }

    private int getTmdbEpisodeSegmentSize(int size) {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(size, episodeAdapter == null ? 0 : episodeAdapter.getPosition(), false);
        if (ranges.isEmpty()) return getEpisodeSegmentSize(size);
        EpisodeRangePolicy.Range first = ranges.get(0);
        return Math.max(1, first.end() - first.start());
    }

    private int getTmdbCardColumn() {
        return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);
    }

    private void scrollToSelectedEpisode() {
        int position = getSelectedEpisodePosition(allEpisodes);
        scrollToSegment(position);
        scrollToEpisode(position - getSegmentStart(selectedSegment), true);
    }

    private void scrollToSegment(int episodePosition) {
        if (segmentStarts.isEmpty()) return;
        selectedSegment = resolveSelectedSegment(episodePosition);
        binding.array.setSelectedPosition(selectedSegment);
        binding.array.scrollToPosition(selectedSegment);
    }

    private int getSegmentStart(int position) {
        if (position < 0 || position >= segmentStarts.size()) return 0;
        return segmentStarts.get(position);
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return 0;
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    private void scrollToEpisode(int position, boolean requestFocus) {
        if (position < 0 || position >= episodeAdapter.getItemCount()) return;
        binding.episode.post(() -> {
            if (binding == null) return;
            binding.episode.setSelectedPosition(position);
            if (requestFocus) binding.episode.requestFocus();
        });
    }

    @Override
    public void onItemClick(Flag item) {
        ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setEpisodes(item);
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }

    @Override
    public void onRevSort() {
    }

    @Override
    public void onRevPlay(TextView view) {
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) dismissListener.onDismiss(dialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable android.os.Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(0);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null || binding == null) return;
        window.getDecorView().setPadding(0, 0, 0, 0);
        clearParentPaddingAndFillHeight();
        int gravity = tmdbCard ? Gravity.CENTER : Gravity.END | Gravity.BOTTOM;
        int width = tmdbCard ? WindowManager.LayoutParams.MATCH_PARENT : panelWidth;
        window.setGravity(gravity);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = gravity;
        params.x = 0;
        params.y = 0;
        window.setAttributes(params);
        window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
        binding.getRoot().post(() -> {
            clearParentPaddingAndFillHeight();
            window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
        });
    }

    private void clearParentPaddingAndFillHeight() {
        View view = binding.getRoot();
        fillHeight(view);
        while (view.getParent() instanceof View parent) {
            if (parent instanceof ViewGroup group) group.setPadding(0, 0, 0, 0);
            fillHeight(parent);
            view = parent;
        }
    }

    private void fillHeight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(params);
        }
        view.setMinimumHeight(ResUtil.getScreenHeight(requireContext()));
    }
}
