package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackUtil {

    public static int count(Tracks tracks, int type) {
        return tracks.getGroups().stream().filter(trackGroup -> trackGroup.getType() == type).mapToInt(trackGroup -> trackGroup.length).sum();
    }

    public static void reset(Player player) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
    }

    public static void reset(Player player, int type) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, false).build());
    }

    public static boolean preferAAC(Player player) {
        TrackInfo info = findAAC(player);
        if (info == null) return false;
        if (info.trackGroup.isTrackSelected(info.trackIndex)) return false;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(info.trackGroup.getMediaTrackGroup(), List.of(info.trackIndex))).build());
        return true;
    }

    public static boolean hasTrack(Player player, List<Track> tracks, int type) {
        for (Track track : tracks) {
            if (track.getType() == type && find(player, track) != null) return true;
        }
        return false;
    }

    private static TrackInfo find(Player player, Track track) {
        if (track.getFormat() == null) return null;
        Tracks currentTracks = player.getCurrentTracks();
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() != track.getType()) continue;
            for (int i = 0; i < trackGroup.length; i++) {
                if (!trackGroup.isTrackSupported(i)) continue;
                Format format = trackGroup.getTrackFormat(i);
                if (track.getFormat().equals(PlayerHelper.describeFormat(format))) {
                    return new TrackInfo(trackGroup, i);
                }
            }
        }
        return null;
    }

    private static TrackInfo findAAC(Player player) {
        TrackInfo best = null;
        for (Tracks.Group trackGroup : player.getCurrentTracks().getGroups()) {
            if (trackGroup.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < trackGroup.length; i++) {
                Format format = trackGroup.getTrackFormat(i);
                if (!trackGroup.isTrackSupported(i) || !isAAC(format)) continue;
                TrackInfo info = new TrackInfo(trackGroup, i);
                if (best == null || getBitrate(format) > getBitrate(best.trackGroup.getTrackFormat(best.trackIndex))) best = info;
            }
        }
        return best;
    }

    private static boolean isAAC(Format format) {
        String codecs = format.codecs == null ? "" : format.codecs.toLowerCase();
        return MimeTypes.AUDIO_AAC.equals(format.sampleMimeType) || codecs.contains("mp4a") || codecs.contains("aac");
    }

    private static int getBitrate(Format format) {
        return Math.max(format.averageBitrate, format.peakBitrate);
    }

    public static void setTrackSelection(Player player, List<Track> tracks) {
        Map<Integer, TrackGroup> mediaGroupMapByType = new HashMap<>();
        Map<Integer, Integer> selectedIndexMapByType = new HashMap<>();
        for (Track track : tracks) {
            if (track.isDisabled()) {
                mediaGroupMapByType.put(track.getType(), null);
                continue;
            }
            TrackInfo info = find(player, track);
            if (info == null) continue;
            int type = info.trackGroup.getType();
            mediaGroupMapByType.put(type, info.trackGroup.getMediaTrackGroup());
            if (track.isSelected()) selectedIndexMapByType.put(type, info.trackIndex);
        }
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        mediaGroupMapByType.forEach((type, mediaGroup) -> {
            builder.setTrackTypeDisabled(type, mediaGroup == null);
            if (mediaGroup == null) return;
            Integer selectedIndex = selectedIndexMapByType.get(type);
            List<Integer> indices = selectedIndex != null ? List.of(selectedIndex) : List.of();
            builder.setOverrideForType(new TrackSelectionOverride(mediaGroup, indices));
        });
        player.setTrackSelectionParameters(builder.build());
    }

    private record TrackInfo(Tracks.Group trackGroup, int trackIndex) {
    }
}
