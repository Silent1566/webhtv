package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps provider results to the stable, transport-safe WebHome VOD contract. */
public final class WebHomeVodContract {

    public static final int VERSION = 1;
    static final int MAX_CONTRACT_BYTES = 768 * 1024;
    private static final int COLLECTION_BUDGET_BYTES = 384 * 1024;
    private static final int HOME_METADATA_BUDGET_BYTES = 128 * 1024;
    static final int MAX_REMOTE_ITEMS = 500;
    static final int MAX_REMOTE_CLASSES = 256;
    static final int MAX_REMOTE_FILTER_GROUPS = 256;
    static final int MAX_REMOTE_FILTERS_PER_GROUP = 64;
    static final int MAX_REMOTE_FILTER_VALUES = 256;
    static final int MAX_REMOTE_EXTEND = 32;
    static final int MAX_DETAIL_PEOPLE = 24;
    static final int MAX_DETAIL_GALLERY = 12;
    static final int MAX_DETAIL_RECOMMENDATIONS = 18;

    private WebHomeVodContract() {
    }

    public static JsonObject home(Site site, Result result, boolean isLeanback, boolean isLandscape, int suggestedColumns) {
        Result safeResult = result == null ? new Result() : result;
        MappingBudget itemBudget = new MappingBudget(COLLECTION_BUDGET_BYTES);
        MappingBudget classBudget = new MappingBudget(HOME_METADATA_BUDGET_BYTES);
        MappingBudget filterBudget = new MappingBudget(HOME_METADATA_BUDGET_BYTES);
        JsonArray mappedItems = items(site, safeResult.getList(), safeResult.getStyle(Style.rect()), itemBudget);
        JsonObject root = base(site, isLeanback, isLandscape, suggestedColumns);
        root.add("classes", classes(safeResult.getTypes(), classBudget));
        root.add("filters", filters(safeResult, filterBudget));
        root.add("items", mappedItems);
        root.addProperty("truncated", itemBudget.isTruncated() || classBudget.isTruncated()
                || filterBudget.isTruncated() || isTruncated(safeResult.getList()));
        root.add("capabilities", capabilities(safeResult));
        return root;
    }

    public static JsonObject category(Site site, String typeId, int page, boolean filter, Map<String, String> extend,
                                      Result result, boolean isLeanback, boolean isLandscape, int suggestedColumns) {
        Result safeResult = result == null ? new Result() : result;
        MappingBudget budget = new MappingBudget();
        int safePage = Math.max(1, page);
        int pageCount = Math.max(0, safeResult.getPageCount());
        JsonObject root = base(site, isLeanback, isLandscape, suggestedColumns);
        root.add("query", query(typeId, filter, extend));
        root.addProperty("page", safePage);
        root.addProperty("pageCount", pageCount);
        root.addProperty("hasMore", !safeResult.getList().isEmpty() && (pageCount == 0 || pageCount > safePage));
        root.add("items", items(site, safeResult.getList(), safeResult.getStyle(Style.rect()), budget));
        root.addProperty("truncated", budget.isTruncated() || isTruncated(safeResult.getList()));
        root.add("capabilities", capabilities(safeResult));
        return root;
    }

    public static JsonObject detail(Site site, Vod vod, WebThemePlaySession session, boolean favorite, History history,
            boolean canFavorite, boolean canPlay) {
        return detail(site, vod, session, favorite, history, canFavorite, canPlay, false,
                WebThemeDetailMetadata.EMPTY);
    }

    public static JsonObject detail(Site site, Vod vod, WebThemePlaySession session, boolean favorite, History history,
            boolean canFavorite, boolean canPlay, boolean canSearchRecommendations,
            WebThemeDetailMetadata metadata) {
        return detail(site, vod, session, favorite, history, true, true, canFavorite, canPlay,
                canSearchRecommendations, metadata);
    }

    public static JsonObject detail(Site site, Vod vod, WebThemePlaySession session, boolean favorite, History history,
            boolean canReadFavorite, boolean canReadHistory, boolean canFavorite, boolean canPlay,
            boolean canSearchRecommendations, WebThemeDetailMetadata metadata) {
        Vod safeVod = vod == null ? new Vod() : vod;
        WebThemePlaySession safeSession = session == null ? new WebThemePlaySession() : session;
        WebThemeDetailMetadata safeMetadata = metadata == null ? WebThemeDetailMetadata.EMPTY : metadata;
        History visibleHistory = canReadHistory ? history : null;
        MappingBudget budget = new MappingBudget();
        safeSession.begin(site == null ? "" : site.getKey(), safeVod.getId());

        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("source", source(site));
        root.add("item", detailItem(site, safeVod));
        JsonObject media = detailMedia(safeMetadata);

        JsonArray sources = new JsonArray();
        boolean truncated = false;
        boolean hasEpisodeMetadata = false;
        int referenceCount = 0;
        String historyFlag = visibleHistory == null ? "" : value(visibleHistory.getVodFlag());
        String historyUrl = visibleHistory == null ? "" : value(visibleHistory.getEpisodeUrl());
        String historyName = visibleHistory == null ? "" : value(visibleHistory.getVodRemarks());
        String historySourceId = "";
        String historyEpisodeId = "";
        int inspectedEpisodes = 0;
        boolean stopSources = false;
        for (int sourceIndex = 0; sourceIndex < safeVod.getFlags().size() && sourceIndex < 64; sourceIndex++) {
            Flag flag = safeVod.getFlags().get(sourceIndex);
            if (flag == null) continue;
            String sourceId = "line-" + sourceIndex;
            JsonObject source = new JsonObject();
            source.addProperty("sourceId", sourceId);
            source.addProperty("name", limited(flag.getShow(), 256));
            boolean selectedSource = historyFlag.equals(flag.getFlag()) || (visibleHistory == null && sources.isEmpty());
            source.addProperty("selected", selectedSource);
            JsonArray episodes = new JsonArray();
            source.add("episodes", episodes);
            if (!budget.take(source)) {
                truncated = true;
                break;
            }
            for (int episodeIndex = 0; episodeIndex < flag.getEpisodes().size(); episodeIndex++) {
                Episode episode = flag.getEpisodes().get(episodeIndex);
                if (++inspectedEpisodes > WebThemePlaySession.MAX_REFERENCES * 2) {
                    truncated = true;
                    stopSources = true;
                    break;
                }
                if (episode == null || value(episode.getUrl()).isEmpty()) continue;
                if (episode.getUrl().length() > WebThemePlaySession.MAX_EPISODE_URL_LENGTH) {
                    truncated = true;
                    continue;
                }
                if (referenceCount >= WebThemePlaySession.MAX_REFERENCES) {
                    truncated = true;
                    break;
                }
                String playRef = safeSession.issue(site == null ? "" : site.getKey(), safeVod.getId(),
                        flag.getFlag(), episode.getName(), episode.getUrl());
                String episodeId = playRef;
                boolean selectedEpisode = historyFlag.equals(flag.getFlag())
                        && (historyUrl.equals(episode.getUrl())
                        || (!historyName.isEmpty() && historyName.equals(value(episode.getName()))));
                JsonObject item = new JsonObject();
                item.addProperty("episodeId", episodeId);
                item.addProperty("name", limited(episode.getName(), 512));
                item.addProperty("number", episode.getNumber());
                item.addProperty("playRef", playRef);
                item.addProperty("selected", selectedEpisode);
                TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
                boolean episodeHasMetadata = tmdbEpisode != null;
                if (tmdbEpisode != null) {
                    item.addProperty("title", limited(tmdbEpisode.getTitle(), 512));
                    item.addProperty("date", limited(tmdbEpisode.getDate(), 64));
                    item.addProperty("overview", limited(tmdbEpisode.getOverview(), 4_000));
                    item.addProperty("still", limited(tmdbEpisode.getStillUrl(), 4_096));
                    item.addProperty("rating", boundedRating(tmdbEpisode.getVoteAverage()));
                    item.addProperty("runtimeMinutes", Math.max(0, tmdbEpisode.getRuntime()));
                    item.addProperty("seasonNumber", Math.max(0, tmdbEpisode.getSeasonNumber()));
                }
                if (!budget.add(episodes, item)) {
                    truncated = true;
                    stopSources = true;
                    break;
                }
                if (episodeHasMetadata) hasEpisodeMetadata = true;
                referenceCount++;
                if (selectedEpisode) {
                    historySourceId = sourceId;
                    historyEpisodeId = episodeId;
                }
            }
            sources.add(source);
            if (stopSources) break;
            if (referenceCount >= WebThemePlaySession.MAX_REFERENCES) {
                if (sourceIndex + 1 < safeVod.getFlags().size()) truncated = true;
                break;
            }
        }
        if (budget.isTruncated()) truncated = true;
        if (safeVod.getFlags().size() > 64) truncated = true;
        JsonArray people = detailPeople(safeMetadata, budget);
        JsonArray gallery = detailGallery(safeMetadata, budget);
        JsonArray recommendations = detailRecommendations(safeMetadata, budget);
        if (budget.isTruncated()) truncated = true;
        root.add("media", media);
        root.add("people", people);
        root.add("gallery", gallery);
        root.add("sources", sources);
        root.addProperty("truncated", truncated);

        JsonObject state = new JsonObject();
        if (canReadFavorite) state.addProperty("favorite", favorite);
        if (visibleHistory != null) {
            JsonObject historyState = new JsonObject();
            historyState.addProperty("sourceId", historySourceId);
            historyState.addProperty("episodeId", historyEpisodeId);
            historyState.addProperty("positionMs", Math.max(0, visibleHistory.getPosition()));
            historyState.addProperty("durationMs", Math.max(0, visibleHistory.getDuration()));
            historyState.addProperty("updatedAt", Math.max(0, visibleHistory.getUpdateTime()));
            state.add("history", historyState);
        }
        root.add("state", state);
        root.add("recommendations", recommendations);

        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("canFavorite", canReadFavorite && canFavorite);
        capabilities.addProperty("canPlay", canPlay && referenceCount > 0);
        capabilities.addProperty("canSearchRecommendations", canSearchRecommendations);
        capabilities.addProperty("hasPeople", people.size() > 0);
        capabilities.addProperty("hasGallery", gallery.size() > 0);
        capabilities.addProperty("hasRecommendations", recommendations.size() > 0);
        capabilities.addProperty("hasEpisodeMetadata", hasEpisodeMetadata);
        capabilities.addProperty("tmdbEnriched", !safeMetadata.isEmpty());
        root.add("capabilities", capabilities);
        return root;
    }

    private static JsonObject detailMedia(WebThemeDetailMetadata metadata) {
        JsonObject media = new JsonObject();
        TmdbItem item = metadata.getItem();
        JsonObject detail = metadata.getDetail();
        if (item == null && detail == null) return media;

        int tmdbId = item == null ? 0 : item.getTmdbId();
        String mediaType = item == null ? "" : item.getMediaType();
        String originalName = string(detail, "movie".equalsIgnoreCase(mediaType) ? "original_title" : "original_name");
        if (originalName.isEmpty()) originalName = string(detail, "original_title", "original_name");
        String releaseDate = string(detail, "first_air_date", "release_date");
        String backdrop = item == null ? "" : item.getBackdropUrl();
        if (backdrop.isEmpty() && !metadata.getGallery().isEmpty()) backdrop = value(metadata.getGallery().get(0));
        double rating = number(detail, "vote_average");
        if (rating <= 0 && item != null) rating = item.getRating();

        media.addProperty("tmdbId", Math.max(0, tmdbId));
        media.addProperty("mediaType", limited(mediaType, 32));
        media.addProperty("originalName", limited(originalName, 512));
        media.addProperty("tagline", limited(string(detail, "tagline"), 1_024));
        media.addProperty("releaseDate", limited(releaseDate, 64));
        media.addProperty("lastAirDate", limited(string(detail, "last_air_date"), 64));
        media.addProperty("status", limited(string(detail, "status"), 128));
        media.addProperty("backdrop", limited(backdrop, 4_096));
        media.addProperty("rating", boundedRating(rating));
        media.addProperty("voteCount", positiveInt(detail, "vote_count"));
        media.addProperty("runtimeMinutes", runtime(detail));
        media.addProperty("seasonCount", positiveInt(detail, "number_of_seasons"));
        media.addProperty("episodeCount", positiveInt(detail, "number_of_episodes"));
        media.addProperty("originalLanguage", limited(item == null ? "" : item.getOriginalLanguage(), 32));
        media.addProperty("originCountry", limited(item == null ? "" : item.getOriginCountry(), 128));
        media.add("genres", genres(detail));
        return media;
    }

    private static JsonArray detailPeople(WebThemeDetailMetadata metadata, MappingBudget budget) {
        JsonArray people = new JsonArray();
        Set<String> seen = new HashSet<>();
        addPeople(people, seen, metadata.getCast(), "cast", budget);
        addPeople(people, seen, metadata.getCrew(), "crew", budget);
        return people;
    }

    private static void addPeople(JsonArray output, Set<String> seen, List<TmdbPerson> values, String kind,
            MappingBudget budget) {
        int inspected = 0;
        for (TmdbPerson person : values) {
            if (inspected++ >= MAX_DETAIL_PEOPLE * 4) {
                budget.truncate();
                break;
            }
            if (output.size() >= MAX_DETAIL_PEOPLE) break;
            if (person == null) continue;
            String name = value(person.getName());
            if (name.isEmpty()) continue;
            String identity = kind + ':' + (person.getPersonId() > 0
                    ? person.getPersonId() : limited(name, 256).toLowerCase(Locale.ROOT));
            if (!seen.add(identity)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("personId", Math.max(0, person.getPersonId()));
            item.addProperty("kind", kind);
            item.addProperty("name", limited(name, 256));
            item.addProperty("role", limited(person.getSubtitle(), 512));
            item.addProperty("department", limited(person.getKnownForDepartment(), 128));
            item.addProperty("profile", limited(person.getProfileUrl(), 4_096));
            if (!budget.add(output, item)) break;
        }
    }

    private static JsonArray detailGallery(WebThemeDetailMetadata metadata, MappingBudget budget) {
        JsonArray gallery = new JsonArray();
        Set<String> seen = new LinkedHashSet<>();
        int inspected = 0;
        for (String value : metadata.getGallery()) {
            if (inspected++ >= MAX_DETAIL_GALLERY * 4) {
                budget.truncate();
                break;
            }
            String image = limited(value, 4_096);
            if (image.isEmpty() || !seen.add(image)) continue;
            if (!budget.add(gallery, new com.google.gson.JsonPrimitive(image))) break;
            if (gallery.size() >= MAX_DETAIL_GALLERY) break;
        }
        return gallery;
    }

    private static JsonArray detailRecommendations(WebThemeDetailMetadata metadata, MappingBudget budget) {
        JsonArray recommendations = new JsonArray();
        Set<String> seen = new HashSet<>();
        int inspected = 0;
        for (TmdbItem value : metadata.getRecommendations()) {
            if (inspected++ >= MAX_DETAIL_RECOMMENDATIONS * 4) {
                budget.truncate();
                break;
            }
            if (recommendations.size() >= MAX_DETAIL_RECOMMENDATIONS) break;
            if (value == null) continue;
            String name = value(value.getTitle());
            if (name.isEmpty()) continue;
            String identity = value.getMediaType() + ':'
                    + (value.getTmdbId() > 0 ? value.getTmdbId() : limited(name, 512));
            if (!seen.add(identity)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("tmdbId", Math.max(0, value.getTmdbId()));
            item.addProperty("mediaType", limited(value.getMediaType(), 32));
            item.addProperty("name", limited(name, 512));
            item.addProperty("subtitle", limited(value.getSubtitle(), 1_024));
            item.addProperty("overview", limited(value.getOverview(), 4_000));
            item.addProperty("pic", limited(value.getPosterUrl(), 4_096));
            item.addProperty("backdrop", limited(value.getBackdropUrl(), 4_096));
            item.addProperty("rating", boundedRating(value.getRating()));
            if (!budget.add(recommendations, item)) break;
        }
        return recommendations;
    }

    private static JsonArray genres(JsonObject detail) {
        JsonArray result = new JsonArray();
        if (detail == null || !detail.has("genres") || !detail.get("genres").isJsonArray()) return result;
        int inspected = 0;
        for (JsonElement element : detail.getAsJsonArray("genres")) {
            if (inspected++ >= 48) break;
            if (!element.isJsonObject()) continue;
            String name = string(element.getAsJsonObject(), "name");
            if (!name.isEmpty()) result.add(limited(name, 128));
            if (result.size() >= 12) break;
        }
        return result;
    }

    private static int runtime(JsonObject detail) {
        int runtime = positiveInt(detail, "runtime");
        if (runtime > 0 || detail == null || !detail.has("episode_run_time")
                || !detail.get("episode_run_time").isJsonArray()) return runtime;
        JsonArray values = detail.getAsJsonArray("episode_run_time");
        if (values.isEmpty() || values.get(0).isJsonNull()) return 0;
        try {
            return Math.max(0, values.get(0).getAsInt());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int positiveInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
        try {
            return Math.max(0, object.get(key).getAsInt());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static double number(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
        try {
            return object.get(key).getAsDouble();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static double boundedRating(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(10, value)) : 0;
    }

    private static String string(JsonObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) continue;
            try {
                String value = object.get(key).getAsString();
                if (!value.isEmpty()) return value;
            } catch (RuntimeException ignored) {
            }
        }
        return "";
    }

    private static JsonObject detailItem(Site site, Vod vod) {
        JsonObject item = new JsonObject();
        item.addProperty("vodId", limited(vod.getId(), 2048));
        item.addProperty("siteKey", site == null ? "" : limited(site.getKey(), 256));
        item.addProperty("name", limited(vod.getName(), 512));
        item.addProperty("pic", limited(vod.getPic(), 4096));
        item.addProperty("remarks", limited(vod.getRemarks(), 1024));
        item.addProperty("year", limited(vod.getYear(), 64));
        item.addProperty("area", limited(vod.getArea(), 256));
        item.addProperty("typeName", limited(vod.getTypeName(), 256));
        item.addProperty("actor", limited(vod.getActor(), 2048));
        item.addProperty("director", limited(vod.getDirector(), 1024));
        item.addProperty("content", limited(vod.getContent(), 20_000));
        return item;
    }

    private static String limited(String value, int maxLength) {
        String safe = value(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static JsonObject base(Site site, boolean isLeanback, boolean isLandscape, int suggestedColumns) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("source", source(site));
        root.add("client", client(isLeanback, isLandscape, suggestedColumns));
        return root;
    }

    private static JsonObject source(Site site) {
        JsonObject object = new JsonObject();
        object.addProperty("key", site == null ? "" : limited(site.getKey(), 256));
        object.addProperty("name", site == null ? "" : limited(site.getName(), 512));
        object.addProperty("type", site == null ? 0 : site.getType());
        return object;
    }

    private static JsonObject client(boolean isLeanback, boolean isLandscape, int suggestedColumns) {
        JsonObject object = new JsonObject();
        object.addProperty("isLeanback", isLeanback);
        object.addProperty("isLandscape", isLandscape);
        object.addProperty("suggestedColumns", Math.max(1, suggestedColumns));
        return object;
    }

    private static JsonArray classes(List<com.fongmi.android.tv.bean.Class> values, MappingBudget budget) {
        JsonArray array = new JsonArray();
        int inspected = 0;
        for (com.fongmi.android.tv.bean.Class value : values) {
            if (inspected++ >= MAX_REMOTE_CLASSES) {
                if (values.size() > inspected - 1) budget.truncate();
                break;
            }
            if (value == null) continue;
            if (array.size() >= MAX_REMOTE_CLASSES) break;
            JsonObject object = new JsonObject();
            object.addProperty("typeId", limited(value.getTypeId(), 256));
            object.addProperty("typeName", limited(value.getTypeName(), 512));
            object.addProperty("typeFlag", limited(value.getTypeFlag(), 256));
            object.addProperty("folder", value.isFolder());
            object.addProperty("filter", value.getFilter());
            object.add("style", style(value.getStyle()));
            if (!budget.add(array, object)) break;
        }
        return array;
    }

    private static JsonObject filters(Result result, MappingBudget budget) {
        LinkedHashMap<String, List<Filter>> values = new LinkedHashMap<>();
        int inspectedGroups = 0;
        for (Map.Entry<String, List<Filter>> entry : result.getFilters().entrySet()) {
            if (inspectedGroups++ >= MAX_REMOTE_FILTER_GROUPS) break;
            String key = limited(entry.getKey(), 256);
            if (!key.isEmpty() && !values.containsKey(key)) values.put(key, entry.getValue());
        }
        int inspectedTypes = 0;
        for (com.fongmi.android.tv.bean.Class type : result.getTypes()) {
            if (inspectedTypes++ >= MAX_REMOTE_CLASSES || values.size() >= MAX_REMOTE_FILTER_GROUPS) break;
            if (type == null) continue;
            String key = limited(type.getTypeId(), 256);
            if (key.isEmpty() || type.getFilters().isEmpty() || values.containsKey(key)) continue;
            values.put(key, type.getFilters());
        }
        JsonObject object = new JsonObject();
        for (Map.Entry<String, List<Filter>> entry : values.entrySet()) {
            if (!budget.take(new com.google.gson.JsonPrimitive(entry.getKey()))) break;
            JsonArray array = new JsonArray();
            List<Filter> group = entry.getValue();
            if (group != null) {
                int inspected = 0;
                for (Filter filter : group) {
                    if (inspected++ >= MAX_REMOTE_FILTERS_PER_GROUP) {
                        if (group.size() > inspected - 1) budget.truncate();
                        break;
                    }
                    if (filter == null) continue;
                    if (array.size() >= MAX_REMOTE_FILTERS_PER_GROUP) break;
                    JsonObject mapped = filter(filter, budget);
                    if (mapped == null) break;
                    array.add(mapped);
                }
            }
            object.add(entry.getKey(), array);
        }
        return object;
    }

    private static JsonObject filter(Filter filter, MappingBudget budget) {
        JsonObject object = new JsonObject();
        object.addProperty("key", limited(filter.getKey(), 64));
        object.addProperty("name", limited(filter.getName(), 256));
        object.addProperty("init", limited(filter.getInit(), 512));
        JsonArray values = new JsonArray();
        object.add("values", values);
        if (!budget.take(object)) return null;
        int inspected = 0;
        for (Value value : filter.getValue()) {
            if (inspected++ >= MAX_REMOTE_FILTER_VALUES) {
                if (filter.getValue().size() > inspected - 1) budget.truncate();
                break;
            }
            if (value == null) continue;
            if (values.size() >= MAX_REMOTE_FILTER_VALUES) break;
            JsonObject item = new JsonObject();
            item.addProperty("name", limited(value.getN(), 512));
            item.addProperty("value", limited(value.getV(), 512));
            item.addProperty("selected", value.isSelected());
            if (!budget.add(values, item)) break;
        }
        return object;
    }

    private static JsonArray items(Site site, List<Vod> values, Style defaultStyle, MappingBudget budget) {
        JsonArray array = new JsonArray();
        for (int index = 0; index < values.size() && index < MAX_REMOTE_ITEMS
                && array.size() < MAX_REMOTE_ITEMS; index++) {
            Vod value = values.get(index);
            if (value == null) continue;
            JsonObject object = new JsonObject();
            object.addProperty("index", index);
            object.addProperty("kind", value.isAction() ? "action" : value.isFolder() ? "folder" : "vod");
            object.addProperty("vodId", limited(value.getId(), 2048));
            object.addProperty("siteKey", site == null ? "" : limited(site.getKey(), 256));
            object.addProperty("name", limited(value.getName(), 512));
            object.addProperty("pic", limited(value.getPic(), 4096));
            object.addProperty("remarks", limited(value.getRemarks(), 1024));
            object.addProperty("year", limited(value.getYear(), 64));
            object.addProperty("typeName", limited(value.getTypeName(), 256));
            object.addProperty("area", limited(value.getArea(), 256));
            object.addProperty("director", limited(value.getDirector(), 1024));
            object.addProperty("actor", limited(value.getActor(), 2048));
            object.addProperty("content", limited(value.getContent(), 20_000));
            if (value.isAction()) object.addProperty("action", limited(value.getAction(), 4096));
            object.add("style", style(value.getStyle(defaultStyle)));
            if (!budget.add(array, object)) break;
        }
        return array;
    }

    private static boolean isTruncated(List<Vod> values) {
        return values.size() > MAX_REMOTE_ITEMS;
    }

    private static JsonObject style(Style value) {
        Style safe = value == null ? Style.rect() : value;
        float ratio = safe.getRatio();
        if (Float.isNaN(ratio) || Float.isInfinite(ratio)) ratio = Style.rect().getRatio();
        JsonObject object = new JsonObject();
        object.addProperty("type", limited(safe.getType(), 32));
        object.addProperty("ratio", ratio);
        return object;
    }

    private static JsonObject capabilities(Result result) {
        boolean hasFilters = !result.getFilters().isEmpty();
        if (!hasFilters) {
            int inspected = 0;
            for (com.fongmi.android.tv.bean.Class type : result.getTypes()) {
                if (inspected++ >= MAX_REMOTE_CLASSES) break;
                if (type != null && (type.getFilter() || !type.getFilters().isEmpty())) {
                    hasFilters = true;
                    break;
                }
            }
        }
        JsonObject object = new JsonObject();
        object.addProperty("category", !result.getTypes().isEmpty());
        object.addProperty("filters", hasFilters);
        object.addProperty("recommend", !result.getList().isEmpty());
        return object;
    }

    private static JsonObject query(String typeId, boolean filter, Map<String, String> extend) {
        JsonObject object = new JsonObject();
        object.addProperty("typeId", limited(typeId, 256));
        object.addProperty("filter", filter);
        JsonObject values = new JsonObject();
        if (extend != null) {
            int count = 0;
            for (Map.Entry<String, String> entry : extend.entrySet()) {
                if (count++ >= MAX_REMOTE_EXTEND) break;
                String key = limited(entry.getKey(), 64);
                if (!key.isEmpty() && !values.has(key)) values.addProperty(key, limited(entry.getValue(), 512));
            }
        }
        object.add("extend", values);
        return object;
    }

    private static final class MappingBudget {

        private int remaining;
        private boolean truncated;

        private MappingBudget() {
            this(COLLECTION_BUDGET_BYTES);
        }

        private MappingBudget(int remaining) {
            this.remaining = Math.max(0, remaining);
        }

        private boolean add(JsonArray array, JsonElement value) {
            if (!take(value)) return false;
            array.add(value);
            return true;
        }

        private boolean take(JsonElement value) {
            int bytes = value.toString().getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes <= remaining) {
                remaining -= bytes;
                return true;
            }
            truncate();
            return false;
        }

        private void truncate() {
            truncated = true;
            remaining = 0;
        }

        private boolean isTruncated() {
            return truncated;
        }
    }
}
