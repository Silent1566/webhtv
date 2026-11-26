package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ItemFollowingCalendarEntryBinding;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Entry;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.LocalRecord;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.LocalReason;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Source;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.TemporalState;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Section;
import com.fongmi.android.tv.utils.ImgUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 日历分段列表：日期标题 + 集行，其后固定保留日期待定区与本地记录区。
 *
 * <p>去重的是「重复」，不是信息：同一天里同一部剧的剧名只在第一行出现，排期来源与缓存时间
 * 提到日期标题上，于是每一行只剩真正逐集变化的东西——集号、集名、看过没有。集行仍只把身份
 * 交回原追剧列表定位，不提供已读/通知/播放等写入动作。
 */
public class FollowingCalendarEntryAdapter extends RecyclerView.Adapter<FollowingCalendarEntryAdapter.Holder> {

    public interface Listener {
        void onEntryIdentity(String identityKey);
    }

    private static final class Row {
        final String header, series, episode, status, identityKey, poster;
        final boolean row, showSeries;

        private Row(String header, String series, String episode, String status, String identityKey,
                    String poster, boolean row, boolean showSeries) {
            this.header = header;
            this.series = series;
            this.episode = episode;
            this.status = status;
            this.identityKey = identityKey;
            this.poster = poster;
            this.row = row;
            this.showSeries = showSeries;
        }

        static Row header(String text) {
            return new Row(text, null, null, null, null, null, false, false);
        }
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private final List<LocalDate> dayHeaders = new ArrayList<>();

    public FollowingCalendarEntryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setSections(Context context, List<Section> sections, LocalDate today) {
        rows.clear();
        dayHeaders.clear();
        for (Section section : sections == null ? List.<Section>of() : sections) {
            switch (section.kind) {
                case DAY -> {
                    rows.add(Row.header(sectionDayTitle(context, section.day, section.entries.size(), today)
                            + sectionSourceSuffix(context, section.entries, today)));
                    dayHeaders.add(section.day);
                    addEntries(context, section, today);
                }
                case UNDATED -> {
                    rows.add(Row.header(context.getString(R.string.following_calendar_section_undated, section.entries.size())));
                    addEntries(context, section, today);
                }
                case LOCAL -> {
                    rows.add(Row.header(context.getString(R.string.following_calendar_section_local, section.records.size())));
                    for (LocalRecord record : section.records) rows.add(recordRow(context, record));
                }
            }
        }
        notifyDataSetChanged();
    }

    private void addEntries(Context context, Section section, LocalDate today) {
        String previous = null;
        for (Entry entry : section.entries) {
            boolean showSeries = !TextUtils.equals(previous, entry.identityKey);
            rows.add(entryRow(context, entry, showSeries));
            previous = entry.identityKey;
        }
    }

    /** 分段标题在适配器里的位置；用于选日后滚动到该日分段。 */
    public int indexOfDay(LocalDate day) {
        if (day == null) return -1;
        int header = 0;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).row) continue;
            if (header < dayHeaders.size() && day.equals(dayHeaders.get(header))) return index;
            header++;
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public long getItemId(int position) {
        Row row = rows.get(position);
        return row.row ? (row.identityKey + "#" + row.episode + "#" + row.status).hashCode() : row.header.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFollowingCalendarEntryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        holder.binding.entryHeader.setVisibility(row.row ? View.GONE : View.VISIBLE);
        holder.binding.entryRow.setVisibility(row.row ? View.VISIBLE : View.GONE);
        if (!row.row) {
            holder.binding.entryHeader.setText(row.header);
            return;
        }
        holder.binding.entryTitle.setVisibility(row.showSeries ? View.VISIBLE : View.GONE);
        holder.binding.entryTitle.setText(row.series);
        holder.binding.entryMeta.setText(row.episode);
        // 剧名隐去时这一行就是主信息，放大加粗顶上；有剧名时它退成副信息。
        holder.binding.entryMeta.setTextSize(row.showSeries ? 12f : 15f);
        holder.binding.entryMeta.setTypeface(null, row.showSeries ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        holder.binding.entryStatus.setText(row.status);
        holder.binding.entryStatus.setVisibility(TextUtils.isEmpty(row.status) ? View.GONE : View.VISIBLE);
        ImgUtil.load(row.series, row.poster, holder.binding.entryPoster);
        holder.binding.entryRow.setOnClickListener(view -> {
            if (listener != null && !TextUtils.isEmpty(row.identityKey)) listener.onEntryIdentity(row.identityKey);
        });
    }

    private Row entryRow(Context context, Entry entry, boolean showSeries) {
        StringBuilder episode = new StringBuilder(context.getString(R.string.following_calendar_episode, entry.seasonNumber, entry.episodeNumber));
        if (!TextUtils.isEmpty(entry.name)) episode.append(" · ").append(entry.name);
        // 已到排期日是每行的默认事实，重复七遍只会把真正有信息量的「看过没有」埋掉；
        // 排期来源已提到日期标题，只有今天/待播/日期待定与错误才占状态位。
        StringBuilder status = new StringBuilder();
        if (entry.temporalState != TemporalState.PAST) status.append(context.getString(temporalLabelResource(entry.temporalState)));
        int watchLabel = watchLabelResource(entry.temporalState, entry.watched);
        if (watchLabel != 0) {
            if (status.length() > 0) status.append(" · ");
            status.append(context.getString(watchLabel, entry.episodeNumber));
        }
        if (!TextUtils.isEmpty(entry.sanitizedError)) {
            if (status.length() > 0) status.append(" · ");
            status.append(entry.sanitizedError);
        }
        return new Row(null, entry.title, episode.toString(), status.toString(), entry.identityKey,
                entry.poster, true, showSeries);
    }

    private Row recordRow(Context context, LocalRecord record) {
        String episode = context.getString(R.string.following_calendar_local_summary, record.seasonNumber, record.watchedEpisode,
                record.releasedEpisodes, record.totalEpisodes);
        String status = reasonLabel(context, record.reason);
        if (!TextUtils.isEmpty(record.sanitizedError)) status += " · " + record.sanitizedError;
        return new Row(null, record.title, episode, status, record.identityKey, record.poster, true, true);
    }

    /** 同一段里所有条目同源时，把来源与缓存时间合并到日期标题（避免每行重复同一句）。 */
    private static String sectionSourceSuffix(Context context, List<Entry> entries, LocalDate today) {
        Source source = null;
        long cachedAt = -1;
        for (Entry entry : entries) {
            if (source == null) {
                source = entry.source;
                cachedAt = entry.cachedAtMillis;
                continue;
            }
            if (source != entry.source || cachedAt != entry.cachedAtMillis) return "";
        }
        if (source == null) return "";
        String label = sourceLabel(context, source, cachedAt, today);
        return label.isEmpty() ? "" : " · " + label;
    }

    public static int watchLabelResource(TemporalState state, boolean watched) {
        if (state != TemporalState.PAST && state != TemporalState.TODAY) return 0;
        return watched ? R.string.following_calendar_watched : R.string.following_calendar_unwatched;
    }

    public static int temporalLabelResource(TemporalState state) {
        return switch (state) {
            case PAST -> R.string.following_calendar_temporal_past;
            case TODAY -> R.string.following_calendar_temporal_today;
            case FUTURE -> R.string.following_calendar_temporal_future;
            default -> R.string.following_calendar_temporal_unknown;
        };
    }

    private static String sourceLabel(Context context, Source source, long cachedAtMillis, LocalDate today) {
        String label = switch (source == null ? Source.NONE : source) {
            case NETWORK -> context.getString(R.string.following_calendar_source_network);
            case FRESH_CACHE -> context.getString(R.string.following_calendar_source_cache);
            case STALE_CACHE -> context.getString(R.string.following_calendar_source_stale);
            default -> "";
        };
        if (label.isEmpty() || cachedAtMillis <= 0) return label;
        LocalDate cachedAt = Instant.ofEpochMilli(cachedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        return label + " · " + context.getString(R.string.following_calendar_cached_at, dayLabel(context, cachedAt, today));
    }

    private static String reasonLabel(Context context, LocalReason reason) {
        if (reason == null) return "";
        return switch (reason) {
            case NO_IDENTITY -> context.getString(R.string.following_calendar_reason_no_identity);
            case UNSUPPORTED -> context.getString(R.string.following_calendar_reason_unsupported);
            case NO_CONFIG -> context.getString(R.string.following_calendar_reason_no_config);
            case LOADING -> context.getString(R.string.following_calendar_reason_loading);
            case FAILED -> context.getString(R.string.following_calendar_reason_failed);
            case EMPTY -> context.getString(R.string.following_calendar_reason_empty);
            case INVALID_DATA -> context.getString(R.string.following_calendar_reason_invalid);
            default -> context.getString(R.string.following_calendar_reason_rate);
        };
    }

    public static String dayLabel(Context context, LocalDate day, LocalDate today) {
        if (day == null) return "";
        if (today != null && day.getYear() == today.getYear())
            return context.getString(R.string.following_calendar_day_label, day.getMonthValue(), day.getDayOfMonth());
        return context.getString(R.string.following_calendar_day_label_year, day.getYear(), day.getMonthValue(), day.getDayOfMonth());
    }

    public static String weekdayLabel(Context context, LocalDate day) {
        if (day == null) return "";
        String[] names = context.getResources().getStringArray(R.array.following_weekdays);
        int index = day.getDayOfWeek().getValue() % 7;
        return index < names.length ? names[index] : "";
    }

    public static String sectionDayTitle(Context context, LocalDate day, int count, LocalDate today) {
        return context.getString(R.string.following_calendar_section_day, dayLabel(context, day, today), weekdayLabel(context, day), count);
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        private final ItemFollowingCalendarEntryBinding binding;

        Holder(ItemFollowingCalendarEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
