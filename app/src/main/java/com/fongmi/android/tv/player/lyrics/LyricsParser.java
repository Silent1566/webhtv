package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsParser {

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<\\d+,-?\\d+>");
    private static final Pattern WORD_TOKEN = Pattern.compile("<(\\d+),(-?\\d+)>([^<]*)");
    private static final long AUX_TOLERANCE_MS = 800;
    private static final List<String> CREDIT_BLOCKED_LABELS = List.of(
            "企划", "策划", "监制", "制作", "制作人", "出品", "发行", "统筹", "项目", "宣发", "推广", "营销", "文案", "封面", "设计", "摄影", "版权",
            "演唱", "原唱", "翻唱", "歌手", "艺人",
            "编曲", "配器", "和声", "和音", "合音", "伴唱", "配唱", "声乐指导", "音乐总监", "录音", "混音", "母带", "母带工程", "音频编辑", "调音", "缩混",
            "钢琴", "吉他", "鼓", "鼓手", "贝斯", "键盘", "弦乐", "管弦", "管乐", "小提琴", "大提琴", "萨克斯", "长笛", "短笛", "笛子", "古琴", "二胡",
            "op", "sp", "cp", "publisher", "copyright", "producer", "production", "arranger", "arrangement", "mix", "mixing", "master", "mastering", "recording", "vocal", "chorus", "artist", "singer",
            "piano", "guitar", "drum", "drums", "bass", "keyboard", "strings", "violin", "cello", "sax", "flute"
    );

    private static class Mark {

        private final long time;
        private final int start;
        private final int end;

        private Mark(long time, int start, int end) {
            this.time = time;
            this.start = start;
            this.end = end;
        }
    }

    private static class Content {

        private final String text;
        private final List<LyricsWord> words;

        private Content(String text, List<LyricsWord> words) {
            this.text = text;
            this.words = words;
        }
    }

    private static class AuxLine {

        private final long time;
        private final String text;

        private AuxLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public static boolean hasTimedLine(String text) {
        return !TextUtils.isEmpty(text) && TIME.matcher(text).find();
    }

    public static String mergeTimedText(String primary, String... auxiliaries) {
        if (TextUtils.isEmpty(primary) || auxiliaries == null || auxiliaries.length == 0) return primary;
        List<AuxLine> auxLines = new ArrayList<>();
        for (String auxiliary : auxiliaries) collectAuxLines(auxLines, auxiliary);
        if (auxLines.isEmpty()) return primary;
        auxLines.sort(Comparator.comparingLong(item -> item.time));

        StringBuilder builder = new StringBuilder();
        Set<Integer> used = new HashSet<>();
        for (String raw : primary.replace("\r", "").split("\n")) {
            long time = firstTime(raw);
            if (time < 0) {
                builder.append(raw).append('\n');
                continue;
            }
            String auxiliary = findAuxLine(raw, time, auxLines, used);
            builder.append(raw);
            if (!TextUtils.isEmpty(auxiliary)) builder.append("\\n").append(auxiliary);
            builder.append('\n');
        }
        return builder.toString();
    }

    public static List<LyricsLine> parseTimed(String text) {
        List<LyricsLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return lines;
        for (String raw : text.replace("\r", "").split("\n")) {
            Matcher matcher = TIME.matcher(raw);
            List<Mark> marks = new ArrayList<>();
            while (matcher.find()) {
                marks.add(new Mark(parseTime(matcher), matcher.start(), matcher.end()));
            }
            if (marks.isEmpty()) continue;
            addLine(lines, raw, marks);
        }
        lines.sort(Comparator.comparingLong(LyricsLine::getTimeMs));
        return compact(lines);
    }

    public static List<LyricsLine> parsePlain(String text, long durationMs) {
        List<String> texts = new ArrayList<>();
        if (!TextUtils.isEmpty(text)) {
            for (String raw : text.replace("\r", "").split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("[") && line.endsWith("]")) continue;
                if (shouldDropCreditLine(line)) continue;
                texts.add(line);
            }
        }
        List<LyricsLine> lines = new ArrayList<>();
        if (texts.isEmpty()) return lines;
        long step = durationMs > 0 ? Math.max(3000, durationMs / Math.max(1, texts.size())) : 5000;
        for (int i = 0; i < texts.size(); i++) lines.add(new LyricsLine(i * step, texts.get(i)));
        return lines;
    }

    public static int findLine(List<LyricsLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        int low = 0;
        int high = lines.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).getTimeMs() <= positionMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    public static List<LyricsLine> filterMetadataLines(List<LyricsLine> lines, String title, String artist) {
        if (lines == null || lines.isEmpty()) return lines;
        List<LyricsLine> output = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            LyricsLine line = lines.get(i);
            if (i < 4 && line.getTimeMs() <= 10_000 && shouldDropIdentityLine(line.getText(), title, artist)) {
                changed = true;
                continue;
            }
            output.add(line);
        }
        return changed ? output : lines;
    }

    private static long parseTime(Matcher matcher) {
        long minute = parseLong(matcher.group(1));
        long second = parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long millis = 0;
        if (!TextUtils.isEmpty(fraction)) {
            if (fraction.length() == 1) millis = parseLong(fraction) * 100;
            else if (fraction.length() == 2) millis = parseLong(fraction) * 10;
            else millis = parseLong(fraction.substring(0, 3));
        }
        return minute * 60_000 + second * 1000 + millis;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void addLine(List<LyricsLine> lines, String raw, List<Mark> marks) {
        if (isPrefixTimed(raw, marks)) {
            Content content = parseContent(raw.substring(marks.get(marks.size() - 1).end));
            if (content.text.isEmpty()) return;
            if (shouldDropCreditLine(content.text)) return;
            for (Mark mark : marks) lines.add(new LyricsLine(mark.time, content.text, content.words));
            return;
        }
        List<Long> pending = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            Mark mark = marks.get(i);
            int next = i + 1 < marks.size() ? marks.get(i + 1).start : raw.length();
            Content content = parseContent(raw.substring(mark.end, next));
            pending.add(mark.time);
            if (content.text.isEmpty()) continue;
            if (shouldDropCreditLine(content.text)) {
                pending.clear();
                continue;
            }
            for (long time : pending) lines.add(new LyricsLine(time, content.text, content.words));
            pending.clear();
        }
    }

    private static boolean isPrefixTimed(String raw, List<Mark> marks) {
        int cursor = 0;
        for (Mark mark : marks) {
            if (!raw.substring(cursor, mark.start).trim().isEmpty()) return false;
            cursor = mark.end;
        }
        return true;
    }

    private static String cleanLyric(String text) {
        return WORD_TIME.matcher(text == null ? "" : text).replaceAll("").replace("\\n", "\n").trim();
    }

    private static Content parseContent(String raw) {
        String source = raw == null ? "" : raw;
        List<LyricsWord> words = parseWords(source);
        return new Content(cleanLyric(source), words);
    }

    private static List<LyricsWord> parseWords(String raw) {
        List<LyricsWord> words = new ArrayList<>();
        Matcher matcher = WORD_TOKEN.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String text = matcher.group(3);
            if (TextUtils.isEmpty(text)) continue;
            words.add(new LyricsWord(parseLong(matcher.group(1)), parseLong(matcher.group(2)), text));
        }
        return words;
    }

    private static List<LyricsLine> compact(List<LyricsLine> input) {
        List<LyricsLine> output = new ArrayList<>();
        String last = null;
        long lastTime = -1;
        for (LyricsLine line : input) {
            if (line.getText().equals(last) && line.getTimeMs() == lastTime && sameWords(line, output)) continue;
            output.add(line);
            last = line.getText();
            lastTime = line.getTimeMs();
        }
        return output;
    }

    private static boolean sameWords(LyricsLine line, List<LyricsLine> output) {
        return !output.isEmpty() && line.getWords().equals(output.get(output.size() - 1).getWords());
    }

    private static void collectAuxLines(List<AuxLine> output, String text) {
        if (TextUtils.isEmpty(text)) return;
        for (String raw : text.replace("\r", "").split("\n")) {
            List<Mark> marks = new ArrayList<>();
            Matcher matcher = TIME.matcher(raw);
            while (matcher.find()) marks.add(new Mark(parseTime(matcher), matcher.start(), matcher.end()));
            if (marks.isEmpty()) continue;
            Content content = parseContent(raw.substring(marks.get(marks.size() - 1).end));
            if (content.text.isEmpty()) continue;
            if (shouldDropCreditLine(content.text)) continue;
            for (Mark mark : marks) output.add(new AuxLine(mark.time, content.text));
        }
    }

    private static long firstTime(String raw) {
        Matcher matcher = TIME.matcher(raw == null ? "" : raw);
        return matcher.find() ? parseTime(matcher) : -1;
    }

    private static String findAuxLine(String raw, long time, List<AuxLine> auxLines, Set<Integer> used) {
        List<String> texts = new ArrayList<>();
        String primary = LyricsMatcher.normalize(cleanTimedLyric(raw));
        for (int i = 0; i < auxLines.size(); i++) {
            if (used.contains(i)) continue;
            AuxLine line = auxLines.get(i);
            long delta = Math.abs(line.time - time);
            if (delta > AUX_TOLERANCE_MS) continue;
            if (LyricsMatcher.normalize(line.text).equals(primary)) {
                used.add(i);
                continue;
            }
            if (!containsNormalized(texts, line.text)) texts.add(line.text);
            used.add(i);
        }
        return TextUtils.join(" / ", texts);
    }

    private static boolean containsNormalized(List<String> texts, String text) {
        String target = LyricsMatcher.normalize(text);
        for (String item : texts) if (LyricsMatcher.normalize(item).equals(target)) return true;
        return false;
    }

    private static String cleanTimedLyric(String text) {
        return cleanLyric(TIME.matcher(text == null ? "" : text).replaceAll(""));
    }

    private static boolean shouldDropCreditLine(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        int index = firstCreditDelimiter(value);
        if (index <= 0 || index > 24) return false;
        String label = normalizeCreditLabel(value.substring(0, index));
        if (label.isEmpty()) return false;
        return isBlockedCreditLabel(label);
    }

    private static boolean shouldDropIdentityLine(String text, String title, String artist) {
        String value = LyricsMatcher.normalize(text);
        String song = LyricsMatcher.normalize(title);
        String singer = LyricsMatcher.normalize(artist);
        if (value.isEmpty() || song.isEmpty() || singer.isEmpty()) return false;
        return value.contains(song) && value.contains(singer);
    }

    private static int firstCreditDelimiter(String text) {
        int colon = text.indexOf(':');
        int full = text.indexOf('：');
        if (colon < 0) return full;
        if (full < 0) return colon;
        return Math.min(colon, full);
    }

    private static String normalizeCreditLabel(String label) {
        return label.replaceAll("[\\s　\\[\\]【】()（）《》〈〉「」『』·•_\\-/|｜]+", "").toLowerCase();
    }

    private static boolean isBlockedCreditLabel(String label) {
        for (String item : CREDIT_BLOCKED_LABELS) if (label.equals(item) || label.contains(item)) return true;
        return false;
    }

}
