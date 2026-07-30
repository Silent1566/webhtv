package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps provider results to the stable, transport-safe WebHome VOD contract. */
public final class WebHomeVodContract {

    public static final int VERSION = 1;
    static final int MAX_REMOTE_ITEMS = 500;

    private WebHomeVodContract() {
    }

    public static JsonObject home(Site site, Result result, boolean isLeanback, boolean isLandscape, int suggestedColumns) {
        Result safeResult = result == null ? new Result() : result;
        JsonObject root = base(site, isLeanback, isLandscape, suggestedColumns);
        root.add("classes", classes(safeResult.getTypes()));
        root.add("filters", filters(safeResult));
        root.add("items", items(site, safeResult.getList(), safeResult.getStyle(Style.rect())));
        root.addProperty("truncated", isTruncated(safeResult.getList()));
        root.add("capabilities", capabilities(safeResult));
        return root;
    }

    public static JsonObject category(Site site, String typeId, int page, boolean filter, Map<String, String> extend,
                                      Result result, boolean isLeanback, boolean isLandscape, int suggestedColumns) {
        Result safeResult = result == null ? new Result() : result;
        int safePage = Math.max(1, page);
        int pageCount = Math.max(0, safeResult.getPageCount());
        JsonObject root = base(site, isLeanback, isLandscape, suggestedColumns);
        root.add("query", query(typeId, filter, extend));
        root.addProperty("page", safePage);
        root.addProperty("pageCount", pageCount);
        root.addProperty("hasMore", !safeResult.getList().isEmpty() && (pageCount == 0 || pageCount > safePage));
        root.add("items", items(site, safeResult.getList(), safeResult.getStyle(Style.rect())));
        root.addProperty("truncated", isTruncated(safeResult.getList()));
        root.add("capabilities", capabilities(safeResult));
        return root;
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
        object.addProperty("key", site == null ? "" : site.getKey());
        object.addProperty("name", site == null ? "" : site.getName());
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

    private static JsonArray classes(List<com.fongmi.android.tv.bean.Class> values) {
        JsonArray array = new JsonArray();
        for (com.fongmi.android.tv.bean.Class value : values) {
            if (value == null) continue;
            JsonObject object = new JsonObject();
            object.addProperty("typeId", value.getTypeId());
            object.addProperty("typeName", value.getTypeName());
            object.addProperty("typeFlag", value.getTypeFlag());
            object.addProperty("folder", value.isFolder());
            object.addProperty("filter", value.getFilter());
            object.add("style", style(value.getStyle()));
            array.add(object);
        }
        return array;
    }

    private static JsonObject filters(Result result) {
        LinkedHashMap<String, List<Filter>> values = new LinkedHashMap<>(result.getFilters());
        for (com.fongmi.android.tv.bean.Class type : result.getTypes()) {
            if (type == null || type.getFilters().isEmpty() || values.containsKey(type.getTypeId())) continue;
            values.put(type.getTypeId(), type.getFilters());
        }
        JsonObject object = new JsonObject();
        for (Map.Entry<String, List<Filter>> entry : values.entrySet()) {
            JsonArray array = new JsonArray();
            for (Filter filter : entry.getValue()) {
                if (filter != null) array.add(filter(filter));
            }
            object.add(entry.getKey(), array);
        }
        return object;
    }

    private static JsonObject filter(Filter filter) {
        JsonObject object = new JsonObject();
        object.addProperty("key", filter.getKey());
        object.addProperty("name", filter.getName());
        object.addProperty("init", filter.getInit());
        JsonArray values = new JsonArray();
        for (Value value : filter.getValue()) {
            if (value == null) continue;
            JsonObject item = new JsonObject();
            item.addProperty("name", value.getN());
            item.addProperty("value", value.getV());
            item.addProperty("selected", value.isSelected());
            values.add(item);
        }
        object.add("values", values);
        return object;
    }

    private static JsonArray items(Site site, List<Vod> values, Style defaultStyle) {
        JsonArray array = new JsonArray();
        for (int index = 0; index < values.size() && array.size() < MAX_REMOTE_ITEMS; index++) {
            Vod value = values.get(index);
            if (value == null) continue;
            JsonObject object = new JsonObject();
            object.addProperty("index", index);
            object.addProperty("kind", value.isAction() ? "action" : value.isFolder() ? "folder" : "vod");
            object.addProperty("vodId", value.getId());
            object.addProperty("siteKey", site == null ? "" : site.getKey());
            object.addProperty("name", value.getName());
            object.addProperty("pic", value.getPic());
            object.addProperty("remarks", value.getRemarks());
            object.addProperty("year", value.getYear());
            object.addProperty("typeName", value.getTypeName());
            object.addProperty("area", value.getArea());
            object.addProperty("director", value.getDirector());
            object.addProperty("actor", value.getActor());
            object.addProperty("content", value.getContent());
            if (value.isAction()) object.addProperty("action", value.getAction());
            object.add("style", style(value.getStyle(defaultStyle)));
            array.add(object);
        }
        return array;
    }

    private static boolean isTruncated(List<Vod> values) {
        int count = 0;
        for (Vod value : values) {
            if (value != null && ++count > MAX_REMOTE_ITEMS) return true;
        }
        return false;
    }

    private static JsonObject style(Style value) {
        Style safe = value == null ? Style.rect() : value;
        JsonObject object = new JsonObject();
        object.addProperty("type", safe.getType());
        object.addProperty("ratio", safe.getRatio());
        return object;
    }

    private static JsonObject capabilities(Result result) {
        boolean hasFilters = !result.getFilters().isEmpty();
        if (!hasFilters) {
            for (com.fongmi.android.tv.bean.Class type : result.getTypes()) {
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
        object.addProperty("typeId", typeId == null ? "" : typeId);
        object.addProperty("filter", filter);
        JsonObject values = new JsonObject();
        if (extend != null) {
            for (Map.Entry<String, String> entry : extend.entrySet()) {
                if (entry.getKey() != null) values.addProperty(entry.getKey(), entry.getValue());
            }
        }
        object.add("extend", values);
        return object;
    }
}
