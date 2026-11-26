package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ItemFollowingCalendarDayBinding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 七列日历的日格：只表达日期、当天排期集数、相邻月与今天/选中，不承载任何请求。 */
public class FollowingCalendarDayAdapter extends RecyclerView.Adapter<FollowingCalendarDayAdapter.Holder> {

    public interface Listener {
        void onSelectDay(LocalDate day);
    }

    public static final class Cell {
        public final LocalDate day;
        public final boolean outOfMonth;
        public final int count;
        public final boolean today;
        public final boolean selected;

        public Cell(LocalDate day, boolean outOfMonth, int count, boolean today, boolean selected) {
            this.day = day;
            this.outOfMonth = outOfMonth;
            this.count = count;
            this.today = today;
            this.selected = selected;
        }
    }

    private final Listener listener;
    private final List<Cell> cells = new ArrayList<>();
    private LocalDate today;

    public FollowingCalendarDayAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setCells(List<Cell> next, LocalDate today) {
        this.today = today;
        cells.clear();
        if (next != null) cells.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return cells.get(position).day.toEpochDay();
    }

    @Override
    public int getItemCount() {
        return cells.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFollowingCalendarDayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Cell cell = cells.get(position);
        Context context = holder.itemView.getContext();
        holder.binding.dayCell.setSelected(cell.selected);
        // 今天用实心主色块承担锚点，选中用浅底描边；两者都不是「有排期」的暗示，排期只由下方的
        // 集数数字表达，避免把视觉重量和平凡的空日期混为一谈。
        holder.binding.dayCell.setBackgroundResource(cell.today
                ? R.drawable.following_calendar_today_bg : R.drawable.following_calendar_day_bg);
        holder.binding.dayNumber.setText(String.valueOf(cell.day.getDayOfMonth()));
        holder.binding.dayNumber.setTextColor(context.getColor(cell.selected ? R.color.webhtv_color_on_primary_container
                : cell.today ? R.color.webhtv_color_on_primary
                : cell.outOfMonth ? R.color.following_text_secondary : R.color.following_text_primary));
        holder.binding.dayNumber.setTypeface(null, cell.today || cell.selected ? Typeface.BOLD : Typeface.NORMAL);
        holder.binding.dayCount.setVisibility(cell.count > 0 ? View.VISIBLE : View.INVISIBLE);
        holder.binding.dayCount.setText(cell.count > 0 ? String.valueOf(cell.count) : "");
        holder.binding.dayCount.setTextColor(context.getColor(cell.today && !cell.selected
                ? R.color.webhtv_color_on_primary : R.color.following_accent));
        holder.binding.dayCell.setContentDescription(context.getString(R.string.following_calendar_day_desc,
                FollowingCalendarEntryAdapter.dayLabel(context, cell.day, today == null ? cell.day : today), cell.count));
        holder.binding.dayCell.setOnClickListener(view -> {
            if (listener != null) listener.onSelectDay(cell.day);
        });
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        private final ItemFollowingCalendarDayBinding binding;

        Holder(ItemFollowingCalendarDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
