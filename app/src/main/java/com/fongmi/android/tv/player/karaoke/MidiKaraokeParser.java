package com.fongmi.android.tv.player.karaoke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MidiKaraokeParser {

    private static final int DEFAULT_TEMPO_US = 500_000;
    private static final int MAX_NOTES = 4000;

    private MidiKaraokeParser() {
    }

    public static boolean looksLikeMidi(byte[] data) {
        return data != null && data.length >= 14 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd';
    }

    public static String toUltraStar(String name, byte[] data) throws Exception {
        Reader reader = new Reader(data);
        reader.expect("MThd");
        int headerLength = reader.readInt();
        int headerEnd = reader.position() + headerLength;
        reader.readShort();
        int tracks = reader.readShort();
        int division = reader.readShort();
        reader.position(headerEnd);
        if ((division & 0x8000) != 0 || division <= 0) throw new IllegalArgumentException("unsupported midi timing");
        List<TrackNotes> parsed = new ArrayList<>();
        int tempoUs = DEFAULT_TEMPO_US;
        for (int i = 0; i < tracks && reader.remaining() >= 8; i++) {
            reader.expect("MTrk");
            int length = reader.readInt();
            TrackNotes track = parseTrack(reader.sliceEnd(length));
            if (track.tempoUs > 0 && tempoUs == DEFAULT_TEMPO_US) tempoUs = track.tempoUs;
            if (!track.notes.isEmpty()) parsed.add(track);
        }
        TrackNotes best = bestTrack(parsed);
        if (best == null || best.notes.isEmpty()) throw new IllegalArgumentException("no midi notes");
        return buildUltraStar(name, best, tempoUs, division);
    }

    private static TrackNotes parseTrack(Reader reader) throws Exception {
        TrackNotes track = new TrackNotes();
        Map<Integer, OpenNote> open = new HashMap<>();
        long tick = 0;
        int running = -1;
        while (reader.remaining() > 0) {
            tick += reader.readVar();
            int status = reader.read();
            if (status < 0x80) {
                if (running < 0) throw new IllegalArgumentException("missing running status");
                reader.back();
                status = running;
            } else if (status < 0xF0) {
                running = status;
            }
            if (status == 0xFF) {
                int type = reader.read();
                int length = (int) reader.readVar();
                if (type == 0x51 && length == 3) track.tempoUs = reader.readTempo();
                else reader.skip(length);
                continue;
            }
            if (status == 0xF0 || status == 0xF7) {
                reader.skip((int) reader.readVar());
                continue;
            }
            if (status >= 0xF0) {
                reader.skip(systemEventLength(status));
                continue;
            }
            int command = status & 0xF0;
            int channel = status & 0x0F;
            int p1 = reader.read();
            int p2 = command == 0xC0 || command == 0xD0 ? 0 : reader.read();
            if (channel == 9) continue;
            if (command == 0x90 && p2 > 0) {
                open.put(channel * 128 + p1, new OpenNote(tick, p1));
            } else if (command == 0x80 || command == 0x90) {
                OpenNote note = open.remove(channel * 128 + p1);
                if (note != null && tick > note.startTick) track.add(new MidiNote(note.startTick, tick, note.pitch));
            }
        }
        return track;
    }

    private static TrackNotes bestTrack(List<TrackNotes> tracks) {
        TrackNotes best = null;
        double bestScore = 0;
        for (TrackNotes track : tracks) {
            double score = track.score();
            if (score > bestScore) {
                bestScore = score;
                best = track;
            }
        }
        return best;
    }

    private static String buildUltraStar(String name, TrackNotes track, int tempoUs, int division) {
        double bpm = 60_000_000.0 / Math.max(1, tempoUs) * division;
        StringBuilder builder = new StringBuilder();
        builder.append("#TITLE:").append(titleOf(name)).append('\n');
        builder.append("#ARTIST:\n");
        builder.append("#BPM:").append(String.format(Locale.US, "%.3f", bpm)).append('\n');
        builder.append("#GAP:0\n");
        int count = 0;
        for (MidiNote note : track.notes) {
            if (++count > MAX_NOTES) break;
            long start = Math.max(0, note.startTick);
            long length = Math.max(1, note.endTick - note.startTick);
            if (start > Integer.MAX_VALUE || length > Integer.MAX_VALUE) continue;
            builder.append(": ")
                    .append(start).append(' ')
                    .append(length).append(' ')
                    .append(note.pitch).append('\n');
        }
        builder.append("E\n");
        return builder.toString();
    }

    private static int systemEventLength(int status) {
        return switch (status) {
            case 0xF1, 0xF3 -> 1;
            case 0xF2 -> 2;
            default -> 0;
        };
    }

    private static String titleOf(String name) {
        String title = name == null ? "" : name.trim();
        int slash = Math.max(title.lastIndexOf('/'), title.lastIndexOf('\\'));
        if (slash >= 0) title = title.substring(slash + 1);
        int dot = title.lastIndexOf('.');
        if (dot > 0) title = title.substring(0, dot);
        return title.trim();
    }

    private static class TrackNotes {

        private final List<MidiNote> notes = new ArrayList<>();
        private long durationTicks;
        private int humanNotes;
        private int tempoUs;

        private void add(MidiNote note) {
            notes.add(note);
            durationTicks += Math.max(0, note.endTick - note.startTick);
            if (note.pitch >= 45 && note.pitch <= 84) humanNotes++;
        }

        private double score() {
            return humanNotes * 10.0 + notes.size() + durationTicks / 1000.0;
        }
    }

    private static class MidiNote {

        private final long startTick;
        private final long endTick;
        private final int pitch;

        private MidiNote(long startTick, long endTick, int pitch) {
            this.startTick = startTick;
            this.endTick = endTick;
            this.pitch = pitch;
        }
    }

    private static class OpenNote {

        private final long startTick;
        private final int pitch;

        private OpenNote(long startTick, int pitch) {
            this.startTick = startTick;
            this.pitch = pitch;
        }
    }

    private static class Reader {

        private final byte[] data;
        private final int end;
        private int pos;

        private Reader(byte[] data) {
            this(data, 0, data == null ? 0 : data.length);
        }

        private Reader(byte[] data, int start, int end) {
            this.data = data == null ? new byte[0] : data;
            this.pos = Math.max(0, start);
            this.end = Math.max(this.pos, Math.min(this.data.length, end));
        }

        private int position() {
            return pos;
        }

        private void position(int value) {
            pos = Math.max(0, Math.min(data.length, value));
        }

        private int remaining() {
            return end - pos;
        }

        private Reader sliceEnd(int length) {
            int nextEnd = Math.min(end, pos + Math.max(0, length));
            Reader slice = new Reader(data, pos, nextEnd);
            pos = nextEnd;
            return slice;
        }

        private void expect(String value) throws Exception {
            for (int i = 0; i < value.length(); i++) {
                if (read() != value.charAt(i)) throw new IllegalArgumentException("invalid midi");
            }
        }

        private int read() throws Exception {
            if (pos >= end) throw new IllegalArgumentException("unexpected midi end");
            return data[pos++] & 0xFF;
        }

        private void back() {
            if (pos > 0) pos--;
        }

        private int readShort() throws Exception {
            return (read() << 8) | read();
        }

        private int readInt() throws Exception {
            return (read() << 24) | (read() << 16) | (read() << 8) | read();
        }

        private int readTempo() throws Exception {
            return (read() << 16) | (read() << 8) | read();
        }

        private long readVar() throws Exception {
            long value = 0;
            for (int i = 0; i < 4; i++) {
                int b = read();
                value = (value << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) return value;
            }
            return value;
        }

        private void skip(int length) {
            pos = Math.min(end, pos + Math.max(0, length));
        }
    }
}
