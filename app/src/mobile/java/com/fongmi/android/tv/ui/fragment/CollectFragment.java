package com.fongmi.android.tv.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.textview.MaterialTextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final int MENU_GROUP_ALL = 1;
    private static final int MENU_GROUP_OFFSET = 100;
    private static final int GROUP_POPUP_ITEM_HEIGHT = 44;
    private static final int GROUP_POPUP_MAX_ITEMS = 8;
    private static final int GRID_ITEM_MARGIN_DP = 4;
    private static final int GRID_TOP_PADDING_DP = 8;
    private static final int RESULT_END_PADDING_DP = 8;

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private List<String> mGroups;
    private final List<Collect> mAllCollectItems;
    private String mFilterGroup;
    private PopupWindow groupPopup;
    private int collectWidth;

    public CollectFragment() {
        mAllCollectItems = new ArrayList<>();
        mFilterGroup = "";
    }

    public static CollectFragment newInstance(String keyword) {
        return newInstance(keyword, null, "");
    }

    public static CollectFragment newInstance(String keyword, String siteKey) {
        return newInstance(keyword, siteKey, "", null, null);
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String group) {
        return newInstance(keyword, siteKey, group, null, null);
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String group, String pic, String wallPic) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        args.putString("group", group);
        args.putString("pic", pic);
        args.putString("wallPic", wallPic);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        return getArguments().getString("siteKey");
    }

    private String getSearchGroup() {
        String group = getArguments().getString("group");
        return group == null ? "" : group;
    }

    private boolean isSiteSearch() {
        return !TextUtils.isEmpty(getSiteKey());
    }

    private boolean isGroupSearch() {
        return !TextUtils.isEmpty(getSearchGroup());
    }

    private String getPic() {
        return getArguments().getString("pic");
    }

    private String getWallPic() {
        return getArguments().getString("wallPic");
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getTitleText());
    }

    private String getTitleText() {
        if (isSiteSearch()) return getString(com.fongmi.android.tv.R.string.search_result_current, getKeyword());
        if (isGroupSearch()) return getString(com.fongmi.android.tv.R.string.search_result_group, getSearchGroup(), getKeyword());
        return getString(com.fongmi.android.tv.R.string.search_result_all, getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        setSites();
        setWidth();
        setRecyclerView();
        setViewModel();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putBoolean("edit", true);
            getParentFragmentManager().setFragmentResult("result", result);
            getParentFragmentManager().popBackStack();
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        setResultLayout(false);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void setSites() {
        String siteKey = getSiteKey();
        String group = getSearchGroup();
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isSearchable()) continue;
            if (SiteBlockSetting.isBlocked(site)) continue;
            if (!TextUtils.isEmpty(siteKey) && !site.getKey().equals(siteKey)) continue;
            if (!TextUtils.isEmpty(group) && !site.inGroup(group)) continue;
            mSites.add(site);
        }
        SiteHealthStore.sortSites(mSites);
        mGroups = isSiteSearch() || isGroupSearch() ? new ArrayList<>() : Site.getGroups(mSites);
    }

    private void setWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / 2 - ResUtil.dp2px(40);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getName(), 14));
        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        int finalWidth = Math.max(minWidth, Math.min(contentWidth, maxWidth));
        collectWidth = finalWidth;
        ViewGroup.LayoutParams params = mBinding.collect.getLayoutParams();
        params.width = finalWidth;
        mBinding.collect.setLayoutParams(params);
    }

    private void search() {
        if (mSites.isEmpty()) return;
        Collect all = Collect.all();
        mAllCollectItems.clear();
        mAllCollectItems.add(all);
        mCollectAdapter.setItems(List.of(all), () -> mViewModel.searchContent(mSites, getKeyword(), false));
    }

    private int getCount() {
        int column = Setting.getSearchColumn();
        if (column == 0) return (ResUtil.isPad() || ResUtil.isLand(requireActivity())) ? 2 : 1;
        return Math.max(1, Math.min(column, 2));
    }

    private boolean isGrid() {
        return getCount() == 2;
    }

    private int[] getGridSize() {
        int span = getCount();
        int margin = ResUtil.dp2px(GRID_ITEM_MARGIN_DP);
        int space = ResUtil.dp2px(RESULT_END_PADDING_DP) + margin * 2 * span;
        int width = (ResUtil.getScreenWidth(requireActivity()) - collectWidth - space) / span;
        width = Math.max(ResUtil.dp2px(96), width);
        return new int[]{width, (int) (width / 0.75f), margin};
    }

    private void setResultLayout(boolean scrollTop) {
        setWidth();
        int count = getCount();
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(count);
        setResultPadding();
        mSearchAdapter.setGrid(isGrid(), getGridSize());
        if (scrollTop) mBinding.recycler.scrollToPosition(0);
    }

    private void setResultPadding() {
        int top = isGrid() ? ResUtil.dp2px(GRID_TOP_PADDING_DP) : 0;
        mBinding.recycler.setPadding(mBinding.recycler.getPaddingStart(), top, mBinding.recycler.getPaddingEnd(), mBinding.recycler.getPaddingBottom());
    }

    private void onColumnToggle() {
        Setting.putSearchColumn(Setting.getSearchColumn() == 1 ? 2 : 1);
        setResultLayout(true);
        requireActivity().invalidateOptionsMenu();
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        Collect collect = addMasterCollect(result.getList());
        if (!matchFilter(collect.getSite())) return;
        if (mCollectAdapter.getPosition() == 0) mSearchAdapter.addAll(result.getList());
        if (!hasCollect(mCollectAdapter.getItems(), collect)) mCollectAdapter.add(Collect.create(result.getList()));
        mCollectAdapter.add(result.getList());
    }

    private Collect addMasterCollect(List<Vod> items) {
        mAllCollectItems.get(0).getList().addAll(items);
        Site site = items.get(0).getSite();
        Collect collect = findCollect(mAllCollectItems, site.getKey());
        if (collect == null) {
            collect = new Collect(site, new ArrayList<>());
            mAllCollectItems.add(collect);
        }
        collect.getList().addAll(items);
        return collect;
    }

    private boolean hasCollect(List<Collect> items, Collect collect) {
        return findCollect(items, collect.getSite().getKey()) != null;
    }

    private Collect findCollect(List<Collect> items, String siteKey) {
        for (Collect item : items) if (item.getSite().getKey().equals(siteKey)) return item;
        return null;
    }

    private boolean matchFilter(Site site) {
        return TextUtils.isEmpty(mFilterGroup) || site.inGroup(mFilterGroup);
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getVod().getSite());
        if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
        if (same) mSearchAdapter.addAll(result.getList());
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mSearchAdapter.setItems(item.getList(), () -> mBinding.recycler.scrollToPosition(0));
        mCollectAdapter.setSelected(position);
        mScroller.setPage(item.getPage());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else {
            String pic = item.getPic().isEmpty() ? getPic() : item.getPic();
            VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic());
        }
    }

    @Override
    public boolean onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if ("all".equals(activated.getSite().getKey())) return false;
        mViewModel.searchContent(activated.getSite(), getKeyword(), false, page);
        activated.setPage(Integer.parseInt(page));
        return true;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_collect, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuItem group = menu.findItem(R.id.action_group);
        if (group != null) {
            group.setVisible(canFilterGroup());
            group.setTitle(TextUtils.isEmpty(mFilterGroup) ? getString(R.string.search_scope_all) : mFilterGroup);
        }
        MenuItem item = menu.findItem(R.id.action_column);
        if (item == null) return;
        Drawable icon = ContextCompat.getDrawable(requireContext(), getCount() == 1 ? R.drawable.ic_site_double_column : R.drawable.ic_site_single_column);
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(Color.WHITE);
        item.setIcon(icon);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_group) {
            onGroupFilter();
            return true;
        }
        if (menuItem.getItemId() == R.id.action_column) {
            onColumnToggle();
            return true;
        }
        return true;
    }

    private boolean canFilterGroup() {
        return !isSiteSearch() && !isGroupSearch() && mGroups != null && !mGroups.isEmpty();
    }

    private void onGroupFilter() {
        if (!canFilterGroup()) return;
        View anchor = mBinding.toolbar.findViewById(com.fongmi.android.tv.R.id.action_group);
        showGroupPopup(anchor == null ? mBinding.toolbar : anchor);
    }

    private void showGroupPopup(View anchor) {
        if (groupPopup != null) groupPopup.dismiss();
        int width = getGroupPopupWidth();
        int height = getGroupPopupHeight();
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackground(getGroupPopupBackground());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        content.setPadding(0, ResUtil.dp2px(6), 0, ResUtil.dp2px(6));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGroupPopupItem(content, getString(com.fongmi.android.tv.R.string.search_scope_all), MENU_GROUP_ALL);
        for (int i = 0; i < mGroups.size(); i++) addGroupPopupItem(content, mGroups.get(i), MENU_GROUP_OFFSET + i);
        groupPopup = new PopupWindow(scroll, width, height, true);
        groupPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        groupPopup.setOutsideTouchable(true);
        groupPopup.setElevation(ResUtil.dp2px(6));
        groupPopup.showAsDropDown(anchor, anchor.getWidth() - width, 0);
    }

    private GradientDrawable getGroupPopupBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(ResUtil.dp2px(6));
        return drawable;
    }

    private int getGroupPopupWidth() {
        int width = ResUtil.getTextWidth(getString(com.fongmi.android.tv.R.string.search_scope_all), 16);
        for (String group : mGroups) width = Math.max(width, ResUtil.getTextWidth(group, 16));
        int contentWidth = width + ResUtil.dp2px(36);
        int maxWidth = ResUtil.getScreenWidth(requireContext()) - ResUtil.dp2px(32);
        return Math.min(contentWidth, maxWidth);
    }

    private int getGroupPopupHeight() {
        int itemHeight = ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT);
        int padding = ResUtil.dp2px(12);
        int contentHeight = (mGroups.size() + 1) * itemHeight + padding;
        int maxHeight = Math.min(ResUtil.getScreenHeight(requireContext()) - mBinding.toolbar.getHeight() - ResUtil.dp2px(32), GROUP_POPUP_MAX_ITEMS * itemHeight + padding);
        return Math.min(contentHeight, Math.max(itemHeight + padding, maxHeight));
    }

    private void addGroupPopupItem(LinearLayoutCompat content, String text, int itemId) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(text);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setTextColor(0xFF202124);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
        view.setBackgroundResource(getSelectableItemBackground());
        view.setOnClickListener(v -> {
            if (groupPopup != null) groupPopup.dismiss();
            onGroupFilterSelected(itemId);
        });
        content.addView(view, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT)));
    }

    private int getSelectableItemBackground() {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private boolean onGroupFilterSelected(int itemId) {
        if (itemId == MENU_GROUP_ALL) {
            setFilterGroup("");
        } else if (itemId >= MENU_GROUP_OFFSET) {
            int index = itemId - MENU_GROUP_OFFSET;
            if (index >= 0 && index < mGroups.size()) setFilterGroup(mGroups.get(index));
        }
        return true;
    }

    private void setFilterGroup(String group) {
        mFilterGroup = group == null ? "" : group;
        applyFilterGroup(getActiveSiteKey());
        requireActivity().invalidateOptionsMenu();
    }

    private String getActiveSiteKey() {
        if (mCollectAdapter == null || mCollectAdapter.getItemCount() == 0) return "all";
        return mCollectAdapter.getActivated().getSite().getKey();
    }

    private void applyFilterGroup(String activeSiteKey) {
        List<Collect> items = getFilteredCollectItems(activeSiteKey);
        Collect activated = getSelectedCollect(items);
        mCollectAdapter.setItems(items, () -> {
            mSearchAdapter.setItems(activated.getList(), () -> mBinding.recycler.scrollToPosition(0));
            mScroller.setPage(activated.getPage());
        });
    }

    private List<Collect> getFilteredCollectItems(String activeSiteKey) {
        List<Collect> items = new ArrayList<>();
        Collect all = Collect.all();
        all.setSelected(false);
        items.add(all);
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect item = mAllCollectItems.get(i);
            if (!matchFilter(item.getSite())) continue;
            all.getList().addAll(item.getList());
            item.setSelected(item.getSite().getKey().equals(activeSiteKey));
            items.add(item);
        }
        if (getSelectedCollect(items) == all) all.setSelected(true);
        return items;
    }

    private Collect getSelectedCollect(List<Collect> items) {
        for (Collect item : items) if (item.isSelected()) return item;
        return items.get(0);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewModel.stopSearch();
        if (groupPopup != null) groupPopup.dismiss();
        SiteHealthStore.flush();
        mAllCollectItems.clear();
        requireActivity().removeMenuProvider(this);
    }
}
