package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.databinding.DialogFilterBinding;
import com.fongmi.android.tv.impl.FilterListener;
import com.fongmi.android.tv.ui.adapter.FilterAdapter;

import java.util.List;

public class FilterDialog extends BaseBottomSheetDialog {

    private DialogFilterBinding binding;
    private FilterListener listener;
    private List<Filter> filter;

    public static FilterDialog create() {
        return new FilterDialog();
    }

    public FilterDialog filter(List<Filter> filter) {
        this.filter = filter;
        return this;
    }

    public void show(Fragment fragment) {
        for (Fragment f : fragment.getChildFragmentManager().getFragments()) if (f instanceof FilterDialog) return;
        show(fragment.getChildFragmentManager(), null);
        this.listener = (FilterListener) fragment;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogFilterBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(new FilterAdapter(listener, filter));
    }

    @Override
    protected boolean transparent() {
        return true;
    }
}
