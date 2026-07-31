package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Immutable TMDB enrichment snapshot consumed by the public detail DTO mapper. */
public final class WebThemeDetailMetadata {

    public static final WebThemeDetailMetadata EMPTY = new WebThemeDetailMetadata(
            null, null, List.of(), List.of(), List.of(), List.of());

    private final TmdbItem item;
    private final JsonObject detail;
    private final List<TmdbPerson> cast;
    private final List<TmdbPerson> crew;
    private final List<String> gallery;
    private final List<TmdbItem> recommendations;

    private WebThemeDetailMetadata(TmdbItem item, JsonObject detail, List<TmdbPerson> cast,
            List<TmdbPerson> crew, List<String> gallery, List<TmdbItem> recommendations) {
        this.item = item;
        this.detail = detail == null ? null : detail.deepCopy();
        this.cast = copy(cast);
        this.crew = copy(crew);
        this.gallery = copy(gallery);
        this.recommendations = copy(recommendations);
    }

    public static WebThemeDetailMetadata fromTmdb(TmdbItem item, JsonObject detail, List<TmdbPerson> cast,
            List<TmdbPerson> crew, List<String> gallery, List<TmdbItem> recommendations) {
        if (item == null && detail == null && empty(cast) && empty(crew) && empty(gallery) && empty(recommendations)) {
            return EMPTY;
        }
        return new WebThemeDetailMetadata(item, detail, cast, crew, gallery, recommendations);
    }

    TmdbItem getItem() {
        return item;
    }

    JsonObject getDetail() {
        return detail;
    }

    List<TmdbPerson> getCast() {
        return cast;
    }

    List<TmdbPerson> getCrew() {
        return crew;
    }

    List<String> getGallery() {
        return gallery;
    }

    List<TmdbItem> getRecommendations() {
        return recommendations;
    }

    boolean isEmpty() {
        return this == EMPTY || (item == null && detail == null && cast.isEmpty() && crew.isEmpty()
                && gallery.isEmpty() && recommendations.isEmpty());
    }

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
