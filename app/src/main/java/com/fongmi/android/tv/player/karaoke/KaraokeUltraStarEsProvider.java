package com.fongmi.android.tv.player.karaoke;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class KaraokeUltraStarEsProvider implements KaraokeTrackProvider {

    private static final Pattern ITEM = Pattern.compile("(?is)<li\\s+title=\"See all complete information of ([^\"]+)\"([\\s\\S]*?)(?=\\n\\s*<li\\s+title=\"See all complete information of |\\n\\s*</ul>)");
    private static final Pattern TXT = Pattern.compile("(?is)href=\"([^\"]*/canciones/descargar/txt/[^\"]+)\"");
    private static final Pattern ARTIST = Pattern.compile("(?is)<a\\s+href=\"/[^\"]*/canciones\\?artista=[^\"]*\"[^>]*>(.*?)</a>");
    private static final Pattern TITLE = Pattern.compile("(?is)<a>(.*?)</a>");

    @Override
    public List<KaraokeTrackRepository.SearchResult> search(String keyword) throws Exception {
        List<KaraokeTrackRepository.SearchResult> results = new ArrayList<>();
        if (TextUtils.isEmpty(keyword) || keyword.trim().length() < 2) return results;
        String url = "https://ultrastar-es.org/en/canciones?busqueda=" + KaraokeTrackRepository.encode(keyword.trim());
        String html = KaraokeTrackRepository.getRemoteText(url, null);
        Matcher matcher = ITEM.matcher(html);
        while (matcher.find() && results.size() < 12) {
            String fallback = KaraokeTrackRepository.html(matcher.group(1));
            String block = matcher.group(2);
            String txt = KaraokeTrackRepository.absoluteUrl("https://ultrastar-es.org", KaraokeTrackRepository.find(TXT, block, 1));
            if (TextUtils.isEmpty(txt)) continue;
            String artist = KaraokeTrackRepository.html(KaraokeTrackRepository.find(ARTIST, block, 1));
            String title = KaraokeTrackRepository.html(KaraokeTrackRepository.findLast(TITLE, block, 1));
            if (TextUtils.isEmpty(artist)) artist = KaraokeTrackRepository.parseArtist(fallback);
            if (TextUtils.isEmpty(title)) title = KaraokeTrackRepository.parseSong(fallback);
            results.add(new KaraokeTrackRepository.SearchResult("UltraStar-ES", title, artist, "可能需要登录下载", txt, true));
        }
        return results;
    }
}
