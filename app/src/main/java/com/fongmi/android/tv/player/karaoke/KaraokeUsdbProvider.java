package com.fongmi.android.tv.player.karaoke;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class KaraokeUsdbProvider implements KaraokeTrackProvider {

    private static final Pattern ROW = Pattern.compile("(?is)<tr\\s+class=\"list_tr[12][^\"]*\"[^>]*>\\s*([\\s\\S]*?)\\s*</tr>");
    private static final Pattern DETAIL_ID = Pattern.compile("show_detail\\((\\d+)\\)");
    private static final Pattern CELL = Pattern.compile("(?is)<td\\s+[^>]*>\\s*(?:<a[^>]*>)?([\\s\\S]*?)(?:</a>)?\\s*</td>");
    private static final Pattern RSS_ITEM = Pattern.compile("(?is)<item>(.*?)</item>");
    private static final Pattern RSS_TITLE = Pattern.compile("(?is)<title>(.*?)</title>");
    private static final Pattern RSS_GUID = Pattern.compile("(?is)<guid>(\\d+)</guid>");

    @Override
    public List<KaraokeTrackRepository.SearchResult> search(String keyword) throws Exception {
        List<KaraokeTrackRepository.SearchResult> results = new ArrayList<>();
        String id = KaraokeTrackRepository.parseUsdbId(keyword);
        if (!TextUtils.isEmpty(id)) {
            results.add(resultById(id));
            return results;
        }
        if (TextUtils.isEmpty(keyword) || keyword.trim().length() < 2) return results;
        addUnique(results, searchList(keyword, false));
        if (results.isEmpty()) addUnique(results, searchList(keyword, true));
        results.addAll(searchRss(keyword, "https://usdb.animux.de/rss/rss_new_top10.php"));
        results.addAll(searchRss(keyword, "https://usdb.animux.de/rss/rss_downloads_top10.php"));
        return results;
    }

    private static KaraokeTrackRepository.SearchResult resultById(String id) throws Exception {
        String detailUrl = "https://usdb.animux.de/?link=detail&id=" + id;
        String html = KaraokeTrackRepository.getRemoteText(detailUrl, null);
        String title = KaraokeTrackRepository.parseTitle(html);
        String artist = KaraokeTrackRepository.parseArtist(title);
        String song = KaraokeTrackRepository.parseSong(title);
        String bpm = KaraokeTrackRepository.parseDetailField(html, "BPM");
        String gap = KaraokeTrackRepository.parseDetailField(html, "GAP");
        String note = "BPM " + KaraokeTrackRepository.emptyDash(bpm) + " · GAP " + KaraokeTrackRepository.emptyDash(gap);
        return new KaraokeTrackRepository.SearchResult("USDB", song, artist, note, detailUrl, false);
    }

    private static List<KaraokeTrackRepository.SearchResult> searchList(String keyword, boolean artistSearch) throws Exception {
        List<KaraokeTrackRepository.SearchResult> results = new ArrayList<>();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("order", "lastchange");
        form.put("ud", "desc");
        form.put(artistSearch ? "interpret" : "title", keyword.trim());
        form.put("limit", "20");
        form.put("start", "0");
        String html = KaraokeTrackRepository.postRemoteText("https://usdb.animux.de/?link=list", form, null);
        Matcher row = ROW.matcher(html);
        while (row.find() && results.size() < 10) {
            KaraokeTrackRepository.SearchResult result = parseRow(row.group(1));
            if (result != null) results.add(result);
        }
        return results;
    }

    private static KaraokeTrackRepository.SearchResult parseRow(String html) {
        String id = KaraokeTrackRepository.find(DETAIL_ID, html, 1);
        if (TextUtils.isEmpty(id)) return null;
        List<String> cells = new ArrayList<>();
        Matcher cell = CELL.matcher(html);
        while (cell.find()) cells.add(KaraokeTrackRepository.html(cell.group(1)));
        if (cells.size() < 2) return null;
        String artist = cells.get(0);
        String title = cells.get(1);
        String language = cells.size() > 6 ? cells.get(6) : "";
        String note = "USDB #" + id + (TextUtils.isEmpty(language) ? "" : " · " + language);
        return new KaraokeTrackRepository.SearchResult("USDB", title, artist, note, "https://usdb.animux.de/?link=detail&id=" + id, false);
    }

    private static List<KaraokeTrackRepository.SearchResult> searchRss(String keyword, String url) throws Exception {
        List<KaraokeTrackRepository.SearchResult> results = new ArrayList<>();
        String html = KaraokeTrackRepository.getRemoteText(url, null);
        Matcher item = RSS_ITEM.matcher(html);
        String normalized = KaraokeTrackRepository.normalizeSearch(keyword);
        while (item.find() && results.size() < 5) {
            String block = item.group(1);
            String title = KaraokeTrackRepository.find(RSS_TITLE, block, 1);
            String id = KaraokeTrackRepository.find(RSS_GUID, block, 1);
            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(id)) continue;
            String clean = KaraokeTrackRepository.html(title);
            if (!KaraokeTrackRepository.normalizeSearch(clean).contains(normalized)) continue;
            results.add(new KaraokeTrackRepository.SearchResult("USDB RSS", KaraokeTrackRepository.parseSong(clean), KaraokeTrackRepository.parseArtist(clean), "USDB #" + id, "https://usdb.animux.de/?link=detail&id=" + id, false));
        }
        return results;
    }

    private static void addUnique(List<KaraokeTrackRepository.SearchResult> target, List<KaraokeTrackRepository.SearchResult> source) {
        if (source == null || source.isEmpty()) return;
        for (KaraokeTrackRepository.SearchResult item : source) {
            boolean exists = false;
            for (KaraokeTrackRepository.SearchResult result : target) {
                if (TextUtils.equals(result.getUrl(), item.getUrl())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) target.add(item);
        }
    }
}
