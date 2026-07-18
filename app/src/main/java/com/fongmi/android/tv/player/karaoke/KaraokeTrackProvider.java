package com.fongmi.android.tv.player.karaoke;

import java.util.List;

interface KaraokeTrackProvider {

    List<KaraokeTrackRepository.SearchResult> search(String keyword) throws Exception;
}
