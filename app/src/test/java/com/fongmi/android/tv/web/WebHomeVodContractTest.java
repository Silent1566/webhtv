package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WebHomeVodContractTest {

    @Test
    public void home_mapsFlatSafeContractWithoutTransportSecrets() throws Exception {
        Site site = Site.get("demo", "Demo Source");
        site.setType(3);
        site.setHomePage("https://secret.example/home.html");
        setField(site, "header", Map.of("Cookie", "session=secret", "Authorization", "Bearer secret"));

        Result result = new Result();
        result.setTypes(List.of(new Gson().fromJson(
                "{\"type_id\":\"movie\",\"type_name\":\"Movies\",\"land\":1,\"ratio\":1.5,\"filter\":true}",
                com.fongmi.android.tv.bean.Class.class)));
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        filters.put("movie", List.of(new Gson().fromJson(
                "{\"key\":\"year\",\"name\":\"Year\",\"init\":\"2026\",\"value\":["
                        + "{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"}]}",
                Filter.class)));
        setField(result, "filters", filters);
        result.setList(List.of(TestVod.vod("vod-1", "Eclipse One")
                .details("https://img.example/1.jpg", "4K", "2026", "Sci-Fi", "A safe summary")
                .style(new Style("rect", 1.5f))));

        JsonObject json = WebHomeVodContract.home(site, result, false, true, 4);

        assertEquals(1, json.get("version").getAsInt());
        JsonObject source = json.getAsJsonObject("source");
        assertEquals("demo", source.get("key").getAsString());
        assertEquals("Demo Source", source.get("name").getAsString());
        assertEquals(3, source.get("type").getAsInt());
        assertFalse(source.has("header"));
        assertFalse(source.has("homePage"));
        assertFalse(json.toString().contains("session=secret"));
        assertFalse(json.toString().contains("Bearer secret"));

        JsonObject type = json.getAsJsonArray("classes").get(0).getAsJsonObject();
        assertEquals("movie", type.get("typeId").getAsString());
        assertEquals("Movies", type.get("typeName").getAsString());
        assertEquals("rect", type.getAsJsonObject("style").get("type").getAsString());
        assertEquals(1.5f, type.getAsJsonObject("style").get("ratio").getAsFloat(), 0.001f);

        JsonObject filter = json.getAsJsonObject("filters").getAsJsonArray("movie").get(0).getAsJsonObject();
        assertEquals("year", filter.get("key").getAsString());
        assertEquals("Year", filter.get("name").getAsString());
        assertEquals("2026", filter.get("init").getAsString());
        assertEquals("2026", filter.getAsJsonArray("values").get(0).getAsJsonObject().get("value").getAsString());

        JsonObject item = json.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(0, item.get("index").getAsInt());
        assertEquals("vod", item.get("kind").getAsString());
        assertEquals("vod-1", item.get("vodId").getAsString());
        assertEquals("demo", item.get("siteKey").getAsString());
        assertEquals("Eclipse One", item.get("name").getAsString());
        assertEquals("A safe summary", item.get("content").getAsString());
        assertFalse(item.has("vod_play_url"));

        JsonObject capabilities = json.getAsJsonObject("capabilities");
        assertTrue(capabilities.get("category").getAsBoolean());
        assertTrue(capabilities.get("filters").getAsBoolean());
        assertTrue(capabilities.get("recommend").getAsBoolean());
        JsonObject client = json.getAsJsonObject("client");
        assertFalse(client.get("isLeanback").getAsBoolean());
        assertTrue(client.get("isLandscape").getAsBoolean());
        assertEquals(4, client.get("suggestedColumns").getAsInt());
    }

    @Test
    public void category_mapsKindsPaginationAndRequestEcho() throws Exception {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        setField(result, "pagecount", 3);
        result.setList(List.of(
                TestVod.folder("folder-1", "Folder"),
                TestVod.action("action-1", "Action", "refresh-token"),
                TestVod.vod("vod-2", "Playable")));

        JsonObject json = WebHomeVodContract.category(site, "series", 2, true,
                Map.of("year", "2026"), result, true, false, 6);

        assertEquals("series", json.getAsJsonObject("query").get("typeId").getAsString());
        assertEquals(2, json.get("page").getAsInt());
        assertEquals(3, json.get("pageCount").getAsInt());
        assertTrue(json.get("hasMore").getAsBoolean());
        assertTrue(json.getAsJsonObject("query").get("filter").getAsBoolean());
        assertEquals("2026", json.getAsJsonObject("query").getAsJsonObject("extend").get("year").getAsString());

        JsonArray items = json.getAsJsonArray("items");
        assertEquals("folder", items.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("action", items.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals("refresh-token", items.get(1).getAsJsonObject().get("action").getAsString());
        assertEquals("vod", items.get(2).getAsJsonObject().get("kind").getAsString());
        assertTrue(json.getAsJsonObject("client").get("isLeanback").getAsBoolean());
    }

    @Test
    public void category_unknownPageCountKeepsPagingForNonEmptyPage() throws Exception {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        setField(result, "pagecount", 0);
        result.setList(List.of(TestVod.vod("vod-1", "Page one")));

        JsonObject json = WebHomeVodContract.category(site, "movie", 1, false,
                Map.of(), result, false, false, 3);

        assertEquals(0, json.get("pageCount").getAsInt());
        assertTrue(json.get("hasMore").getAsBoolean());
    }

    @Test
    public void category_unknownPageCountStopsAfterEmptyPage() throws Exception {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        setField(result, "pagecount", 0);
        result.setList(List.of());

        JsonObject json = WebHomeVodContract.category(site, "movie", 2, false,
                Map.of(), result, false, false, 3);

        assertEquals(0, json.get("pageCount").getAsInt());
        assertFalse(json.get("hasMore").getAsBoolean());
    }

    @Test
    public void category_capsRemoteItemCount() {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        result.setList(Collections.nCopies(WebHomeVodContract.MAX_REMOTE_ITEMS + 1,
                TestVod.vod("vod-1", "Repeated")));

        JsonObject json = WebHomeVodContract.category(site, "movie", 1, false,
                Map.of(), result, false, false, 3);

        assertEquals(WebHomeVodContract.MAX_REMOTE_ITEMS, json.getAsJsonArray("items").size());
        assertTrue(json.get("truncated").getAsBoolean());
        assertTrue(json.get("hasMore").getAsBoolean());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestVod extends Vod {
        private final String id;
        private final String name;
        private String pic = "";
        private String remarks = "";
        private String year = "";
        private String typeName = "";
        private String content = "";
        private String action = "";
        private boolean folder;
        private Style style = Style.rect();

        private TestVod(String id, String name) {
            this.id = id;
            this.name = name;
        }

        static TestVod vod(String id, String name) {
            return new TestVod(id, name);
        }

        static TestVod folder(String id, String name) {
            TestVod vod = new TestVod(id, name);
            vod.folder = true;
            return vod;
        }

        static TestVod action(String id, String name, String action) {
            TestVod vod = new TestVod(id, name);
            vod.action = action;
            return vod;
        }

        TestVod details(String pic, String remarks, String year, String typeName, String content) {
            this.pic = pic;
            this.remarks = remarks;
            this.year = year;
            this.typeName = typeName;
            this.content = content;
            return this;
        }

        TestVod style(Style style) {
            this.style = style;
            return this;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getPic() { return pic; }
        @Override public String getRemarks() { return remarks; }
        @Override public String getYear() { return year; }
        @Override public String getTypeName() { return typeName; }
        @Override public String getArea() { return ""; }
        @Override public String getDirector() { return ""; }
        @Override public String getActor() { return ""; }
        @Override public String getContent() { return content; }
        @Override public String getAction() { return action; }
        @Override public boolean isAction() { return !action.isEmpty(); }
        @Override public boolean isFolder() { return folder; }
        @Override public Style getStyle(Style defaultStyle) { return style == null ? defaultStyle : style; }
    }
}
