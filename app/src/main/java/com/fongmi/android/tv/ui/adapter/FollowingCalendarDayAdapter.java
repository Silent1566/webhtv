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
        android.graphics.drawable.GradientDrawable selected = new android.graphics.drawable.GradientDrawable();
        selected.setColor(context.getColor(cell.selected ? R.color.calendar_selected : android.R.color.transparent));
        selected.setCornerRadius(14 * context.getResources().getDisplayMetrics().density);
        holder.binding.dayMark.setBackground(selected);
        holder.binding.dayNumber.setBackgroundResource(cell.today ? R.drawable.following_calendar_today_bg : 0);
        holder.binding.dayNumber.setText(String.valueOf(cell.day.getDayOfMonth()));
        holder.binding.dayNumber.setTextColor(context.getColor(cell.today ? R.color.webhtv_color_on_primary
                : cell.selected ? R.color.calendar_selected_text
                : cell.outOfMonth ? R.color.calendar_secondary : R.color.calendar_text));
        holder.binding.dayNumber.setTypeface(null, cell.today || cell.selected ? Typeface.BOLD : Typeface.NORMAL);
        holder.binding.dayCount.setVisibility(cell.count > 0 ? View.VISIBLE : View.INVISIBLE);
        holder.binding.dayCount.setText("");
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
