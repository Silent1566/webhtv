package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomViewPager;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private CollectFragment getFragment() {
        return (CollectFragment) getPager().getAdapter().instantiateItem(getPager(), 0);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        mAdapter.clear();
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.searchColumn.setOnClickListener(this::setSearchColumn);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.horiPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                getRecycler().setSelectedPosition(position);
                getRecycler().requestFocus();
            }
        });
        mBinding.vertPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                getRecycler().setSelectedPosition(position);
                getRecycler().requestFocus();
            }
        });
        mBinding.horiRecycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
        mBinding.vertRecycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        mAdapter = new CollectAdapter();
        setupRecycler(mBinding.horiRecycler);
        setupRecycler(mBinding.vertRecycler);
        applySearchUi();
    }

    private void setupRecycler(BaseGridView recycler) {
        if (recycler instanceof HorizontalGridView view) {
            view.setHorizontalSpacing(ResUtil.dp2px(16));
            view.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        } else if (recycler instanceof VerticalGridView view) {
            view.setVerticalSpacing(ResUtil.dp2px(16));
        }
        recycler.setAdapter(mAdapter);
    }

    private void applySearchUi() {
        mBinding.horiLayout.setVisibility(Setting.getSearchUi() == 0 ? View.VISIBLE : View.GONE);
        mBinding.vertLayout.setVisibility(Setting.getSearchUi() == 1 ? View.VISIBLE : View.GONE);
        mBinding.searchColumn.setText(getSearchColumn());
        mBinding.searchUi.setText(getSearchUi());
    }

    private CustomViewPager getPager() {
        return Setting.getSearchUi() == 0 ? mBinding.horiPager : mBinding.vertPager;
    }

    private BaseGridView getRecycler() {
        return Setting.getSearchUi() == 0 ? mBinding.horiRecycler : mBinding.vertRecycler;
    }

    private String getSearchUi() {
        return getResources().getStringArray(R.array.select_search_ui)[Setting.getSearchUi()];
    }

    private String getSearchColumn() {
        return getResources().getStringArray(R.array.select_search_column)[Setting.getSearchColumn()];
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, result -> {
            if (result.getList().isEmpty()) return;
            getFragment().addVideo(result.getList());
            mAdapter.add(Collect.create(result.getList()));
            getPager().getAdapter().notifyDataSetChanged();
        });
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), new TypeToken<List<String>>() {}.getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
    }

    private void setPager() {
        getPager().setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void setSearchUi(View view) {
        int position = Math.max(0, getRecycler().getSelectedPosition());
        Setting.putSearchUi((Setting.getSearchUi() + 1) % getResources().getStringArray(R.array.select_search_ui).length);
        applySearchUi();
        setPager();
        mOldView = null;
        App.post(() -> {
            if (mAdapter.getItemCount() == 0) return;
            getRecycler().setSelectedPosition(Math.min(position, mAdapter.getItemCount() - 1));
            getRecycler().requestFocus();
        }, 100);
    }

    private void setSearchColumn(View view) {
        int column = Setting.getSearchColumn();
        Setting.putSearchColumn(column >= 3 ? 1 : column + 1);
        mBinding.searchColumn.setText(getSearchColumn());
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("android:switcher:" + getPager().getId() + ":" + getPager().getCurrentItem());
        if (fragment instanceof CollectFragment collect) collect.setColumn();
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mAdapter.add(Collect.all());
        getPager().getAdapter().notifyDataSetChanged();
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            getPager().setCurrentItem(getRecycler().getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), mAdapter.get(position));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
