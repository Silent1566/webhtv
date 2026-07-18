package com.fongmi.android.tv.player.karaoke;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.text.TextUtils;

import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsWord;
import com.github.catvod.crawler.SpiderDebug;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class KaraokePitchTrackGenerator {

    private static final double BPM = 6000.0;
    private static final long BEAT_MS = 10;
    private static final int RAP_PITCH = 60;
    private static final int MAX_NOTES = 900;
    private static final long MIN_NOTE_MS = 120;
    private static final long MIN_STABLE_NOTE_MS = 180;
    private static final long DEFAULT_LAST_LINE_MS = 3000;
    private static final long LINE_UNIT_MS = 520;
    private static final int MAX_LINE_UNITS = 10;
    private static final long WORD_COMPACT_MS = 280;
    private static final int FRAME_SIZE = 2048;
    private static final int HOP_SIZE = 1024;
    private static final int COARSE_HOP_SIZE = 2048;
    private static final int DETECTOR_BATCH_SIZE = 8;
    private static final int ANALYSIS_TARGET_SAMPLE_RATE = 16_000;
    private static final long ANALYSIS_WINDOW_EXTRA_MS = 600;
    private static final long PRECISION_BOUNDARY_MS = 280;
    private static final long LOW_CONFIDENCE_FINE_MS = 520;
    private static final long DECODE_SEEK_GRACE_MS = 1200;
    private static final double ANALYSIS_HIGH_PASS_HZ = 80.0;
    private static final double ANALYSIS_LOW_PASS_HZ = 3500.0;
    private static final double MIN_FREQUENCY_HZ = 80.0;
    private static final double MAX_FREQUENCY_HZ = 800.0;
    private static final double MIN_VOLUME = 0.006;
    private static final double SILENCE_PREFILTER_VOLUME = 0.003;
    private static final double MIN_CONFIDENCE = 0.18;
    private static final double MIN_WINDOW_VALID_RATIO = 0.14;
    private static final double MAX_WINDOW_SPREAD = 8.0;
    private static final double MIN_RAW_PITCH_RATIO = 0.08;
    private static final double MIN_TRACK_PITCH_RATIO = 0.18;
    private static final long WINDOW_MARGIN_MS = 70;
    private static final int MIN_MIDI = 40;
    private static final int MAX_MIDI = 84;
    private static final int GAP_FILL_MAX_NOTES = 8;
    private static final int OUTLIER_STEP = 9;
    private static final long TINY_RUN_MS = 220;
    private static final int MERGE_STRONG_DELTA = 1;
    private static final int MERGE_WEAK_DELTA = 2;
    private static final long MERGE_GAP_MS = 140;
    private static final long MERGE_MAX_MS = 2600;
    private static final long RAP_MERGE_MAX_MS = 5200;
    private static final double KEY_CORRECTION_MAX_QUALITY = 0.68;
    private static final int MAX_PATH_CANDIDATES = 4;
    private static final double PATH_MISSING_PENALTY = 0.22;
    private static final double PATH_LINE_BREAK_RELAX = 0.56;
    private static final double[][] KEY_TABLE = {
            {0.19, 0.00, 0.21, 0.00, 0.21, 0.08, 0.00, 0.13, 0.00, 0.11, 0.00, 0.07},
            {0.09, 0.21, 0.00, 0.17, 0.00, 0.18, 0.08, 0.00, 0.13, 0.00, 0.14, 0.00},
            {0.00, 0.07, 0.19, 0.00, 0.18, 0.00, 0.17, 0.08, 0.00, 0.19, 0.00, 0.12},
            {0.11, 0.00, 0.10, 0.26, 0.00, 0.16, 0.00, 0.17, 0.07, 0.00, 0.13, 0.00},
            {0.00, 0.14, 0.00, 0.07, 0.28, 0.00, 0.17, 0.00, 0.13, 0.06, 0.00, 0.15},
            {0.15, 0.00, 0.16, 0.00, 0.13, 0.17, 0.00, 0.16, 0.00, 0.13, 0.10, 0.00},
            {0.00, 0.15, 0.00, 0.16, 0.00, 0.12, 0.17, 0.00, 0.14, 0.00, 0.14, 0.12},
            {0.09, 0.00, 0.16, 0.00, 0.16, 0.00, 0.11, 0.17, 0.00, 0.16, 0.00, 0.15},
            {0.18, 0.07, 0.00, 0.15, 0.00, 0.14, 0.00, 0.09, 0.19, 0.00, 0.18, 0.00},
            {0.00, 0.18, 0.10, 0.00, 0.14, 0.00, 0.15, 0.00, 0.10, 0.17, 0.00, 0.16},
            {0.13, 0.00, 0.15, 0.09, 0.00, 0.16, 0.00, 0.18, 0.00, 0.12, 0.17, 0.00},
            {0.00, 0.11, 0.00, 0.19, 0.13, 0.00, 0.16, 0.00, 0.12, 0.00, 0.08, 0.21}
    };
    public static final int STAGE_PREPARE = 0;
    public static final int STAGE_DECODE = 1;
    public static final int STAGE_ANALYZE = 2;
    public static final int STAGE_WRITE = 3;
    public static final int STAGE_FINISH = 4;

    private KaraokePitchTrackGenerator() {
    }

    public interface Progress {

        void onProgress(int percent, int stage, long elapsedMs, long remainingMs);
    }

    public static boolean canGenerate(KaraokeTrackRepository.MediaInput input, List<LyricsLine> lines) {
        return input != null && !input.isEmpty() && KaraokeGeneratedTrackBuilder.canGenerate(lines);
    }

    public static String build(KaraokeTrackRepository.MediaInput input, List<LyricsLine> lines) throws Exception {
        return build(input, lines, null);
    }

    public static String build(KaraokeTrackRepository.MediaInput input, List<LyricsLine> lines, Progress progress) throws Exception {
        ProgressReporter reporter = new ProgressReporter(progress);
        if (!canGenerate(input, lines)) throw new IllegalStateException("no timed lyrics");
        reporter.update(1, STAGE_PREPARE);
        List<Segment> segments = segments(lines, input.getDuration());
        if (segments.size() < 3) throw new IllegalStateException("not enough lyric timing");
        List<PitchFrame> frames = decode(input, segments, reporter);
        if (frames.isEmpty()) throw new IllegalStateException("no pitch frames");
        reporter.update(78, STAGE_ANALYZE);
        String text = buildText(input.getKeyword(), input.getArtist(), segments, notes(segments, frames), reporter, "Generated pitch scoring track from local audio; octave corrected and smoothed");
        reporter.update(100, STAGE_FINISH);
        return text;
    }

    static String buildTextFromCandidates(String title, String artist, List<Segment> segments, List<List<PitchCandidate>> candidates, ProgressReporter reporter, String comment) {
        return buildText(title, artist, segments, notesFromCandidates(segments, candidates), reporter, comment);
    }

    private static String buildText(String title, String artist, List<Segment> segments, List<Note> notes, ProgressReporter reporter, String comment) {
        StringBuilder builder = new StringBuilder();
        builder.append("#TITLE:").append(tag(title, "Generated pitch track")).append('\n');
        builder.append("#ARTIST:").append(tag(artist, "Unknown")).append('\n');
        builder.append("#BPM:").append(BPM).append('\n');
        builder.append("#GAP:0").append('\n');
        builder.append("#COMMENT:").append(tag(comment, "Generated pitch scoring track from local audio")).append('\n');
        reporter.update(82, STAGE_ANALYZE);
        stabilize(notes);
        smoothLineContour(notes);
        notes = mergeNotes(notes);
        applyPseudoKey(notes);
        correctOctaves(notes);
        smoothOutliers(notes);
        smoothLineContour(notes);
        notes = mergeNotes(notes);
        notes = absorbTinyRuns(notes);
        notes = mergeNotes(notes);
        reporter.update(90, STAGE_WRITE);
        int count = 0;
        int observed = 0;
        int pitched = 0;
        for (Note note : notes) {
            if (count >= MAX_NOTES) break;
            char prefix = note.pitch >= 0 ? ':' : 'R';
            if (note.observed) observed++;
            if (note.pitch >= 0) pitched++;
            appendNote(builder, prefix, note.segment.startMs, note.segment.endMs, note.pitch >= 0 ? note.pitch : RAP_PITCH, note.segment.text);
            count++;
            if (note.segment.lineEnd) builder.append("-\n");
        }
        if (count < 3) throw new IllegalStateException("not enough notes");
        if (observed < 3 || observed < Math.round(count * MIN_RAW_PITCH_RATIO)) throw new IllegalStateException("pitch quality too low");
        if (pitched < 3 || pitched < Math.round(count * MIN_TRACK_PITCH_RATIO)) throw new IllegalStateException("pitch quality too low");
        builder.append('E').append('\n');
        return builder.toString();
    }

    private static List<Note> notes(List<Segment> segments, List<PitchFrame> frames) {
        List<List<PitchCandidate>> candidates = new ArrayList<>();
        for (Segment segment : segments) candidates.add(PitchWindow.from(frames, segment.startMs, segment.endMs).candidates());
        return notesFromCandidates(segments, candidates);
    }

    private static List<Note> notesFromCandidates(List<Segment> segments, List<List<PitchCandidate>> candidates) {
        List<PitchCandidate> path = smoothCandidatePath(segments, candidates);
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) notes.add(new Note(segments.get(i), path.get(i)));
        return notes;
    }

    private static List<PitchCandidate> smoothCandidatePath(List<Segment> segments, List<List<PitchCandidate>> candidates) {
        if (segments.isEmpty()) return Collections.emptyList();
        int count = segments.size();
        double[][] scores = new double[count][];
        int[][] previous = new int[count][];
        for (int i = 0; i < count; i++) {
            List<PitchCandidate> row = safeCandidates(candidates.get(i));
            scores[i] = new double[row.size()];
            previous[i] = new int[row.size()];
            for (int j = 0; j < row.size(); j++) {
                PitchCandidate candidate = row.get(j);
                double emission = emissionScore(candidate);
                if (i == 0) {
                    scores[i][j] = emission;
                    previous[i][j] = -1;
                    continue;
                }
                List<PitchCandidate> prevRow = safeCandidates(candidates.get(i - 1));
                double best = -Double.MAX_VALUE;
                int bestIndex = 0;
                for (int k = 0; k < prevRow.size(); k++) {
                    double score = scores[i - 1][k] + emission - transitionPenalty(prevRow.get(k), candidate, segments.get(i - 1).lineEnd);
                    if (score > best) {
                        best = score;
                        bestIndex = k;
                    }
                }
                scores[i][j] = best;
                previous[i][j] = bestIndex;
            }
        }
        int bestIndex = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < scores[count - 1].length; i++) {
            if (scores[count - 1][i] > bestScore) {
                bestScore = scores[count - 1][i];
                bestIndex = i;
            }
        }
        PitchCandidate[] path = new PitchCandidate[count];
        for (int i = count - 1; i >= 0; i--) {
            List<PitchCandidate> row = safeCandidates(candidates.get(i));
            PitchCandidate chosen = row.get(bestIndex);
            path[i] = chosen;
            bestIndex = previous[i][bestIndex];
            if (bestIndex < 0) break;
        }
        for (int i = 0; i < path.length; i++) if (path[i] == null) path[i] = PitchCandidate.EMPTY;
        normalizePathOctaves(path);
        List<PitchCandidate> result = new ArrayList<>();
        Collections.addAll(result, path);
        return result;
    }

    private static List<PitchCandidate> safeCandidates(List<PitchCandidate> candidates) {
        return candidates == null || candidates.isEmpty() ? Collections.singletonList(PitchCandidate.EMPTY) : candidates;
    }

    private static double emissionScore(PitchCandidate candidate) {
        if (candidate == null || candidate.pitch < 0) return -PATH_MISSING_PENALTY;
        return candidate.quality * 1.85;
    }

    private static double transitionPenalty(PitchCandidate previous, PitchCandidate current, boolean lineBreak) {
        if (previous == null || current == null || previous.pitch < 0 || current.pitch < 0) return PATH_MISSING_PENALTY;
        int pitch = normalizeOctave(current.pitch, previous.pitch);
        int jump = Math.abs(pitch - previous.pitch);
        double penalty;
        if (jump <= 1) penalty = 0.015 * jump;
        else if (jump <= 3) penalty = 0.04 + (jump - 1) * 0.045;
        else if (jump <= 7) penalty = 0.18 + (jump - 3) * 0.075;
        else penalty = 0.50 + (jump - 7) * 0.16;
        if (jump >= 11) penalty += 0.24;
        return lineBreak ? penalty * PATH_LINE_BREAK_RELAX : penalty;
    }

    private static void normalizePathOctaves(PitchCandidate[] path) {
        Integer anchor = null;
        for (int i = 0; i < path.length; i++) {
            PitchCandidate candidate = path[i];
            if (candidate == null || candidate.pitch < 0) continue;
            int pitch = anchor == null ? candidate.pitch : normalizeOctave(candidate.pitch, anchor);
            path[i] = new PitchCandidate(pitch, candidate.quality);
            anchor = pitch;
        }
    }

    private static void stabilize(List<Note> notes) {
        correctOctaves(notes);
        smoothOutliers(notes);
        fillMissing(notes);
        correctOctaves(notes);
        smoothOutliers(notes);
    }

    private static void smoothLineContour(List<Note> notes) {
        int start = 0;
        while (start < notes.size()) {
            int end = start;
            while (end < notes.size() && !notes.get(end).segment.lineEnd) end++;
            end = Math.min(notes.size() - 1, end);
            smoothLineContour(notes, start, end);
            start = end + 1;
        }
    }

    private static void smoothLineContour(List<Note> notes, int start, int end) {
        List<Integer> pitches = new ArrayList<>();
        for (int i = start; i <= end; i++) if (notes.get(i).pitch >= 0) pitches.add(notes.get(i).pitch);
        if (pitches.size() < 2) return;
        Collections.sort(pitches);
        int median = pitches.get(pitches.size() / 2);
        int low = pitches.get(0);
        int high = pitches.get(pitches.size() - 1);
        boolean mostlyFlat = high - low <= 2;
        for (int i = start; i <= end; i++) {
            Note note = notes.get(i);
            if (note.pitch < 0) continue;
            int normalized = normalizeOctave(note.pitch, median);
            boolean microJitter = mostlyFlat && Math.abs(normalized - median) <= 1;
            if (microJitter || ((Math.abs(normalized - median) <= 1 || mostlyFlat) && (note.quality < 0.55 || note.estimated))) {
                note.pitch = median;
                note.estimated = true;
            } else {
                note.pitch = normalized;
            }
        }
    }

    private static List<Note> mergeNotes(List<Note> notes) {
        List<Note> merged = new ArrayList<>();
        for (Note note : notes) {
            if (!merged.isEmpty() && canMerge(merged.get(merged.size() - 1), note)) {
                merged.set(merged.size() - 1, merge(merged.get(merged.size() - 1), note));
            } else {
                merged.add(note);
            }
        }
        return merged;
    }

    private static boolean canMerge(Note previous, Note next) {
        if (previous == null || next == null || previous.segment.lineEnd) return false;
        if (next.segment.startMs - previous.segment.endMs > MERGE_GAP_MS) return false;
        long duration = next.segment.endMs - previous.segment.startMs;
        long previousDuration = previous.segment.endMs - previous.segment.startMs;
        long nextDuration = next.segment.endMs - next.segment.startMs;
        if (previous.pitch < 0 && next.pitch < 0) return duration <= RAP_MERGE_MAX_MS;
        if (previous.pitch < 0 || next.pitch < 0) return duration <= MERGE_MAX_MS && Math.min(previousDuration, nextDuration) <= TINY_RUN_MS;
        if (duration > MERGE_MAX_MS) return false;
        int delta = Math.abs(normalizeOctave(next.pitch, previous.pitch) - previous.pitch);
        if (delta <= MERGE_STRONG_DELTA) return true;
        return delta <= MERGE_WEAK_DELTA
                && (previous.estimated || next.estimated || previous.quality < 0.55 || next.quality < 0.55 || Math.min(previousDuration, nextDuration) < MIN_STABLE_NOTE_MS);
    }

    private static Note merge(Note previous, Note next) {
        long previousDuration = Math.max(1, previous.segment.endMs - previous.segment.startMs);
        long nextDuration = Math.max(1, next.segment.endMs - next.segment.startMs);
        int pitch;
        int nextPitch = next.pitch;
        if (previous.pitch < 0 && next.pitch < 0) {
            pitch = -1;
        } else if (previous.pitch < 0) {
            pitch = next.pitch;
        } else if (next.pitch < 0) {
            pitch = previous.pitch;
        } else {
            nextPitch = normalizeOctave(next.pitch, previous.pitch);
            pitch = clampMidi(Math.round((previous.pitch * previousDuration + nextPitch * nextDuration) / (float) (previousDuration + nextDuration)));
        }
        Segment segment = new Segment(previous.segment.startMs, next.segment.endMs, previous.segment.text + next.segment.text);
        segment.lineEnd = next.segment.lineEnd;
        boolean estimated = previous.estimated || next.estimated || previous.pitch != pitch || nextPitch != pitch;
        boolean observed = previous.observed || next.observed;
        double quality = Math.max(previous.quality, next.quality);
        return new Note(segment, pitch, observed, quality, estimated);
    }

    private static List<Note> absorbTinyRuns(List<Note> notes) {
        List<Note> result = new ArrayList<>(notes);
        int index = 0;
        while (index < result.size()) {
            Note note = result.get(index);
            if (note.segment.endMs - note.segment.startMs >= TINY_RUN_MS) {
                index++;
                continue;
            }
            int previousIndex = index > 0 && !result.get(index - 1).segment.lineEnd ? index - 1 : -1;
            int nextIndex = index + 1 < result.size() && !note.segment.lineEnd ? index + 1 : -1;
            if (previousIndex < 0 && nextIndex < 0) {
                index++;
                continue;
            }
            boolean mergePrevious = shouldAbsorbToPrevious(result, previousIndex, nextIndex, note);
            if (mergePrevious) {
                result.set(previousIndex, merge(result.get(previousIndex), note));
                result.remove(index);
                index = Math.max(0, previousIndex - 1);
            } else {
                result.set(index, merge(note, result.get(nextIndex)));
                result.remove(nextIndex);
            }
        }
        return result;
    }

    private static boolean shouldAbsorbToPrevious(List<Note> notes, int previousIndex, int nextIndex, Note note) {
        if (previousIndex < 0) return false;
        if (nextIndex < 0) return true;
        Note previous = notes.get(previousIndex);
        Note next = notes.get(nextIndex);
        int previousCost = mergeCost(previous, note);
        int nextCost = mergeCost(note, next);
        if (previousCost != nextCost) return previousCost < nextCost;
        long previousDuration = previous.segment.endMs - previous.segment.startMs;
        long nextDuration = next.segment.endMs - next.segment.startMs;
        return previousDuration >= nextDuration;
    }

    private static int mergeCost(Note left, Note right) {
        if (left.pitch < 0 && right.pitch < 0) return 0;
        if (left.pitch < 0 || right.pitch < 0) return 2;
        return Math.abs(normalizeOctave(right.pitch, left.pitch) - left.pitch);
    }

    private static void applyPseudoKey(List<Note> notes) {
        int key = detectPseudoKey(notes);
        if (key < 0) return;
        for (Note note : notes) {
            if (note.pitch < 0 || (!note.estimated && note.quality > KEY_CORRECTION_MAX_QUALITY)) continue;
            int pc = pitchClass(note.pitch);
            if (KEY_TABLE[key][pc] > 0) continue;
            int up = pitchClass(note.pitch + 1);
            int down = pitchClass(note.pitch - 1);
            int shift = KEY_TABLE[key][up] >= KEY_TABLE[key][down] ? 1 : -1;
            note.pitch = clampMidi(note.pitch + shift);
            note.estimated = true;
        }
    }

    private static int detectPseudoKey(List<Note> notes) {
        double[] distribution = new double[12];
        double total = 0;
        for (Note note : notes) {
            if (note.pitch < 0) continue;
            double duration = Math.max(1, note.segment.endMs - note.segment.startMs);
            double weight = duration * Math.max(0.25, note.quality);
            distribution[pitchClass(note.pitch)] += weight;
            total += weight;
        }
        if (total <= 0) return -1;
        int best = -1;
        double bestScore = 0;
        for (int key = 0; key < KEY_TABLE.length; key++) {
            double score = 0;
            for (int pc = 0; pc < distribution.length; pc++) score += KEY_TABLE[key][pc] * distribution[pc];
            if (score > bestScore) {
                bestScore = score;
                best = key;
            }
        }
        return best;
    }

    private static void correctOctaves(List<Note> notes) {
        Integer anchor = null;
        for (Note note : notes) {
            if (note.pitch < 0) continue;
            if (anchor != null) note.pitch = normalizeOctave(note.pitch, anchor);
            if (anchor != null && Math.abs(note.pitch - anchor) > OUTLIER_STEP && note.quality < 0.45) {
                note.pitch = -1;
                note.estimated = true;
                continue;
            }
            anchor = note.pitch;
        }
        anchor = null;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note note = notes.get(i);
            if (note.pitch < 0) continue;
            if (anchor != null) note.pitch = normalizeOctave(note.pitch, anchor);
            anchor = note.pitch;
        }
    }

    private static void smoothOutliers(List<Note> notes) {
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            if (note.pitch < 0) continue;
            Neighbor previous = previous(notes, i);
            Neighbor next = next(notes, i);
            if (previous == null || next == null || previous.distance > 3 || next.distance > 3) continue;
            int target = Math.round((previous.note.pitch + next.note.pitch) / 2.0f);
            note.pitch = normalizeOctave(note.pitch, target);
            if (Math.abs(note.pitch - target) > OUTLIER_STEP && note.quality < 0.65) {
                note.pitch = clampMidi(target);
                note.estimated = true;
            }
        }
    }

    private static void fillMissing(List<Note> notes) {
        int index = 0;
        while (index < notes.size()) {
            if (notes.get(index).pitch >= 0) {
                index++;
                continue;
            }
            int start = index;
            while (index < notes.size() && notes.get(index).pitch < 0) index++;
            int end = index;
            int length = end - start;
            Note previous = start > 0 ? notes.get(start - 1) : null;
            Note next = end < notes.size() ? notes.get(end) : null;
            if (length > GAP_FILL_MAX_NOTES) continue;
            if (previous != null && previous.pitch >= 0 && next != null && next.pitch >= 0) {
                int nextPitch = normalizeOctave(next.pitch, previous.pitch);
                for (int i = start; i < end; i++) {
                    float progress = (i - start + 1) / (float) (length + 1);
                    notes.get(i).pitch = clampMidi(Math.round(previous.pitch + (nextPitch - previous.pitch) * progress));
                    notes.get(i).estimated = true;
                }
            } else if (previous != null && previous.pitch >= 0) {
                for (int i = start; i < end; i++) {
                    notes.get(i).pitch = previous.pitch;
                    notes.get(i).estimated = true;
                }
            } else if (next != null && next.pitch >= 0) {
                for (int i = start; i < end; i++) {
                    notes.get(i).pitch = next.pitch;
                    notes.get(i).estimated = true;
                }
            }
        }
    }

    private static Neighbor previous(List<Note> notes, int index) {
        for (int i = index - 1; i >= 0; i--) if (notes.get(i).pitch >= 0) return new Neighbor(notes.get(i), index - i);
        return null;
    }

    private static Neighbor next(List<Note> notes, int index) {
        for (int i = index + 1; i < notes.size(); i++) if (notes.get(i).pitch >= 0) return new Neighbor(notes.get(i), i - index);
        return null;
    }

    private static int normalizeOctave(int pitch, int reference) {
        int best = pitch;
        int bestDistance = Math.abs(best - reference);
        for (int candidate = pitch - 24; candidate <= pitch + 24; candidate += 12) {
            if (candidate < MIN_MIDI || candidate > MAX_MIDI) continue;
            int distance = Math.abs(candidate - reference);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return clampMidi(best);
    }

    private static int clampMidi(int pitch) {
        return Math.max(MIN_MIDI, Math.min(MAX_MIDI, pitch));
    }

    private static int pitchClass(int pitch) {
        return ((pitch % 12) + 12) % 12;
    }

    private static void appendNote(StringBuilder builder, char prefix, long startMs, long endMs, int pitch, String lyric) {
        long safeStart = Math.max(0, startMs);
        long safeEnd = Math.max(safeStart + MIN_NOTE_MS, endMs);
        int startBeat = (int) Math.max(0, Math.round(safeStart / (double) BEAT_MS));
        int lengthBeat = (int) Math.max(1, Math.round((safeEnd - safeStart) / (double) BEAT_MS));
        builder.append(prefix).append(' ')
                .append(startBeat).append(' ')
                .append(lengthBeat).append(' ')
                .append(pitch).append(' ')
                .append(lyric(lyric)).append('\n');
    }

    private static List<PitchFrame> decode(KaraokeTrackRepository.MediaInput input, List<Segment> segments, ProgressReporter reporter) throws Exception {
        KaraokeAudioExtractor.Opened source = null;
        MediaCodec decoder = null;
        try {
            reporter.update(5, STAGE_DECODE);
            source = KaraokeAudioExtractor.open(input);
            MediaExtractor extractor = source.extractor;
            int track = source.track;
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (TextUtils.isEmpty(mime)) throw new IllegalStateException("unknown audio mime");
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            return decodeLoop(extractor, decoder, format, analysisWindows(segments), precisionWindows(segments), reporter, durationMs(input, format));
        } finally {
            if (source != null) source.close();
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {
                }
                decoder.release();
            }
        }
    }

    private static List<PitchFrame> decodeLoop(MediaExtractor extractor, MediaCodec decoder, MediaFormat inputFormat, List<AnalysisWindow> windows, List<AnalysisWindow> precisionWindows, ProgressReporter reporter, long durationMs) {
        long startMs = System.currentTimeMillis();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        PitchFrameCollector collector = new PitchFrameCollector(sampleRate(inputFormat), windows, precisionWindows);
        int windowIndex = 0;
        int seekedWindowIndex = -1;
        boolean seekByWindow = windows != null && !windows.isEmpty();
        if (seekByWindow) {
            seekToWindow(extractor, decoder, collector, windows.get(0));
            seekedWindowIndex = 0;
        }
        boolean inputDone = false;
        boolean outputDone = false;
        MediaFormat outputFormat = inputFormat;
        while (!outputDone) {
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(10_000);
                if (inputIndex >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    if (input == null) continue;
                    int size = extractor.readSampleData(input, 0);
                    if (size < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        long sampleTimeUs = extractor.getSampleTime();
                        if (seekByWindow) {
                            long sampleMs = Math.max(0, sampleTimeUs / 1000L);
                            while (windowIndex < windows.size() && sampleMs > windows.get(windowIndex).endMs + DECODE_SEEK_GRACE_MS) windowIndex++;
                            if (windowIndex >= windows.size()) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, sampleTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                                continue;
                            }
                            AnalysisWindow window = windows.get(windowIndex);
                            if (sampleMs < window.startMs - DECODE_SEEK_GRACE_MS && seekedWindowIndex != windowIndex) {
                                seekToWindow(extractor, decoder, collector, window);
                                seekedWindowIndex = windowIndex;
                                continue;
                            }
                        }
                        decoder.queueInputBuffer(inputIndex, 0, size, sampleTimeUs, extractor.getSampleFlags());
                        reporter.decode(sampleTimeUs, durationMs);
                        extractor.advance();
                    }
                }
            }
            int outputIndex = decoder.dequeueOutputBuffer(info, 10_000);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = decoder.getOutputFormat();
                collector.setSourceSampleRate(sampleRate(outputFormat));
            } else if (outputIndex >= 0) {
                ByteBuffer output = decoder.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) collect(collector, output, info, outputFormat);
                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                decoder.releaseOutputBuffer(outputIndex, false);
            }
        }
        List<PitchFrame> frames = collector.frames();
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("karaoke-pitch", "decode/analyze done cost=%sms sourceRate=%d analysisRate=%d frames=%d windows=%d skippedInactive=%d skippedSilent=%d",
                    System.currentTimeMillis() - startMs, collector.sourceSampleRate(), collector.sampleRate(), frames.size(), windows == null ? 0 : windows.size(), collector.skippedInactive(), collector.skippedSilent());
        }
        return frames;
    }

    private static void seekToWindow(MediaExtractor extractor, MediaCodec decoder, PitchFrameCollector collector, AnalysisWindow window) {
        if (window == null || window.startMs <= 0) return;
        extractor.seekTo(window.startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        decoder.flush();
        collector.resetStreamState();
    }

    private static void collect(PitchFrameCollector collector, ByteBuffer buffer, MediaCodec.BufferInfo info, MediaFormat format) {
        int channels = Math.max(1, format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1);
        int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING) ? format.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
        ByteBuffer data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        data.position(info.offset);
        data.limit(info.offset + info.size);
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) collectFloat(collector, data, channels, Math.max(0, info.presentationTimeUs));
        else collectPcm16(collector, data, channels, Math.max(0, info.presentationTimeUs));
    }

    private static void collectPcm16(PitchFrameCollector collector, ByteBuffer data, int channels, long presentationTimeUs) {
        int frameBytes = channels * 2;
        int samples = data.remaining() / frameBytes;
        long sourceIndex = collector.sourceIndexOf(presentationTimeUs);
        for (int i = 0; i < samples; i++) {
            float mono = 0;
            for (int c = 0; c < channels; c++) mono += data.getShort() / 32768f;
            collector.add(mono / channels, sourceIndex + i);
        }
    }

    private static void collectFloat(PitchFrameCollector collector, ByteBuffer data, int channels, long presentationTimeUs) {
        int frameBytes = channels * 4;
        int samples = data.remaining() / frameBytes;
        long sourceIndex = collector.sourceIndexOf(presentationTimeUs);
        for (int i = 0; i < samples; i++) {
            float mono = 0;
            for (int c = 0; c < channels; c++) mono += data.getFloat();
            collector.add(mono / channels, sourceIndex + i);
        }
    }

    private static int sampleRate(MediaFormat format) {
        return format != null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? Math.max(1, format.getInteger(MediaFormat.KEY_SAMPLE_RATE)) : 44_100;
    }

    private static long durationMs(KaraokeTrackRepository.MediaInput input, MediaFormat format) {
        if (input != null && input.getDuration() > 0) return input.getDuration();
        if (format != null && format.containsKey(MediaFormat.KEY_DURATION)) return Math.max(0, format.getLong(MediaFormat.KEY_DURATION) / 1000L);
        return 0;
    }

    private static List<AnalysisWindow> analysisWindows(List<Segment> segments) {
        List<AnalysisWindow> windows = new ArrayList<>();
        if (segments == null || segments.isEmpty()) return windows;
        for (Segment segment : segments) {
            long start = Math.max(0, segment.startMs - ANALYSIS_WINDOW_EXTRA_MS);
            long end = Math.max(start + MIN_NOTE_MS, segment.endMs + ANALYSIS_WINDOW_EXTRA_MS);
            if (!windows.isEmpty() && start <= windows.get(windows.size() - 1).endMs) {
                windows.get(windows.size() - 1).endMs = Math.max(windows.get(windows.size() - 1).endMs, end);
            } else {
                windows.add(new AnalysisWindow(start, end));
            }
        }
        return windows;
    }

    private static List<AnalysisWindow> precisionWindows(List<Segment> segments) {
        List<AnalysisWindow> windows = new ArrayList<>();
        if (segments == null || segments.isEmpty()) return windows;
        for (Segment segment : segments) {
            addPrecisionWindow(windows, segment.startMs);
            addPrecisionWindow(windows, segment.endMs);
        }
        return windows;
    }

    private static void addPrecisionWindow(List<AnalysisWindow> windows, long centerMs) {
        long start = Math.max(0, centerMs - PRECISION_BOUNDARY_MS);
        long end = Math.max(start + MIN_NOTE_MS, centerMs + PRECISION_BOUNDARY_MS);
        if (!windows.isEmpty() && start <= windows.get(windows.size() - 1).endMs) {
            windows.get(windows.size() - 1).endMs = Math.max(windows.get(windows.size() - 1).endMs, end);
        } else {
            windows.add(new AnalysisWindow(start, end));
        }
    }

    static List<Segment> segments(List<LyricsLine> lines, long durationMs) {
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < lines.size() && segments.size() < MAX_NOTES; i++) {
            LyricsLine line = lines.get(i);
            if (line == null || TextUtils.isEmpty(line.getText())) continue;
            long startMs = line.getTimeMs();
            long endMs = lineEnd(lines, i, durationMs);
            int before = segments.size();
            if (line.hasWords()) appendWordSegments(segments, line, startMs, endMs);
            else appendLineSegments(segments, line.getText(), startMs, endMs);
            if (segments.size() > before) segments.get(segments.size() - 1).lineEnd = true;
        }
        return segments;
    }

    private static void appendWordSegments(List<Segment> segments, LyricsLine line, long lineStartMs, long lineEndMs) {
        List<LyricsWord> words = line.getWords();
        List<Segment> raw = new ArrayList<>();
        for (int i = 0; i < words.size() && segments.size() < MAX_NOTES; i++) {
            LyricsWord word = words.get(i);
            if (word == null || TextUtils.isEmpty(word.getText())) continue;
            long startMs = lineStartMs + word.getStartOffsetMs();
            long endMs = word.getDurationMs() > 0 ? startMs + word.getDurationMs() : nextWordStart(lineStartMs, lineEndMs, words, i);
            raw.add(new Segment(startMs, endMs, word.getText()));
        }
        if (raw.isEmpty()) return;
        if (shouldCompactWords(raw, lineEndMs - lineStartMs)) appendCompactedSegments(segments, raw, lineEndMs - lineStartMs);
        else for (Segment segment : raw) if (segments.size() < MAX_NOTES) segments.add(segment);
    }

    private static boolean shouldCompactWords(List<Segment> raw, long durationMs) {
        if (raw.size() <= 4) return false;
        int target = targetUnitCount(raw.size(), durationMs);
        if (raw.size() <= target) return false;
        int shortText = 0;
        int shortDuration = 0;
        for (Segment segment : raw) {
            if (codePointCount(segment.text) <= 1) shortText++;
            if (segment.endMs - segment.startMs <= WORD_COMPACT_MS) shortDuration++;
        }
        return shortText >= Math.round(raw.size() * 0.7f) || shortDuration >= Math.round(raw.size() * 0.65f);
    }

    private static void appendCompactedSegments(List<Segment> output, List<Segment> raw, long durationMs) {
        int target = Math.max(1, Math.min(raw.size(), targetUnitCount(raw.size(), durationMs)));
        int groupSize = (int) Math.ceil(raw.size() / (double) target);
        for (int i = 0; i < raw.size() && output.size() < MAX_NOTES; i += groupSize) {
            int end = Math.min(raw.size(), i + groupSize);
            StringBuilder text = new StringBuilder();
            for (int j = i; j < end; j++) text.append(raw.get(j).text);
            output.add(new Segment(raw.get(i).startMs, raw.get(end - 1).endMs, text.toString()));
        }
    }

    private static int codePointCount(String text) {
        return TextUtils.isEmpty(text) ? 0 : text.codePointCount(0, text.length());
    }

    private static void appendLineSegments(List<Segment> segments, String text, long startMs, long endMs) {
        List<String> units = splitUnits(text, Math.max(MIN_NOTE_MS, endMs - startMs));
        if (units.isEmpty()) return;
        long durationMs = Math.max(MIN_NOTE_MS * units.size(), endMs - startMs);
        long unitMs = Math.max(MIN_NOTE_MS, durationMs / units.size());
        for (int i = 0; i < units.size() && segments.size() < MAX_NOTES; i++) {
            long unitStart = startMs + unitMs * i;
            long unitEnd = i == units.size() - 1 ? startMs + durationMs : unitStart + unitMs;
            segments.add(new Segment(unitStart, unitEnd, units.get(i)));
        }
    }

    private static long lineEnd(List<LyricsLine> lines, int index, long durationMs) {
        long startMs = lines.get(index).getTimeMs();
        long nextMs = index + 1 < lines.size() ? lines.get(index + 1).getTimeMs() : 0;
        long fallback = startMs + DEFAULT_LAST_LINE_MS;
        if (durationMs > startMs + MIN_NOTE_MS) fallback = Math.min(durationMs, fallback);
        if (nextMs <= startMs) return fallback;
        return Math.max(startMs + MIN_NOTE_MS, nextMs);
    }

    private static long nextWordStart(long lineStartMs, long lineEndMs, List<LyricsWord> words, int index) {
        if (index + 1 < words.size()) return lineStartMs + words.get(index + 1).getStartOffsetMs();
        return lineEndMs;
    }

    private static List<String> splitUnits(String text, long durationMs) {
        String clean = lyric(text);
        List<String> units = new ArrayList<>();
        if (TextUtils.isEmpty(clean)) return units;
        if (clean.matches(".*\\s+.*")) {
            for (String unit : clean.split("\\s+")) if (!TextUtils.isEmpty(unit)) units.add(unit);
            return compactUnits(units, targetUnitCount(units.size(), durationMs), " ");
        }
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < clean.length(); ) {
            int codePoint = clean.codePointAt(i);
            chars.add(new String(Character.toChars(codePoint)));
            i += Character.charCount(codePoint);
        }
        return compactUnits(chars, targetUnitCount(chars.size(), durationMs), "");
    }

    private static int targetUnitCount(int sourceCount, long durationMs) {
        if (sourceCount <= 0) return 0;
        if (sourceCount <= 4) return sourceCount;
        int byDuration = Math.max(1, Math.round(durationMs / (float) LINE_UNIT_MS));
        int byText = Math.max(1, (int) Math.ceil(sourceCount / 2.0));
        return Math.max(1, Math.min(Math.min(MAX_LINE_UNITS, sourceCount), Math.min(byDuration, byText)));
    }

    private static List<String> compactUnits(List<String> source, int targetCount, String separator) {
        List<String> units = new ArrayList<>();
        if (source.isEmpty()) return units;
        int count = Math.max(1, Math.min(source.size(), targetCount));
        int groupSize = (int) Math.ceil(source.size() / (double) count);
        for (int i = 0; i < source.size(); i += groupSize) {
            StringBuilder builder = new StringBuilder();
            for (int j = i; j < Math.min(source.size(), i + groupSize); j++) {
                if (builder.length() > 0) builder.append(separator);
                builder.append(source.get(j));
            }
            units.add(builder.toString());
        }
        return units;
    }

    private static String tag(String value, String fallback) {
        String text = lyric(value);
        return TextUtils.isEmpty(text) ? fallback : text;
    }

    private static String lyric(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    static class Segment {

        final long startMs;
        final long endMs;
        final String text;
        boolean lineEnd;

        private Segment(long startMs, long endMs, String text) {
            this.startMs = Math.max(0, startMs);
            this.endMs = Math.max(this.startMs + MIN_NOTE_MS, endMs);
            this.text = text;
        }
    }

    private static class Note {

        private final Segment segment;
        private final boolean observed;
        private final double quality;
        private int pitch;
        private boolean estimated;

        private Note(Segment segment, PitchCandidate candidate) {
            this(segment, candidate.pitch, candidate.pitch >= 0, candidate.quality, false);
        }

        private Note(Segment segment, int pitch, boolean observed, double quality, boolean estimated) {
            this.segment = segment;
            this.pitch = pitch >= 0 ? clampMidi(pitch) : -1;
            this.quality = Math.max(0, Math.min(1, quality));
            this.observed = observed;
            this.estimated = estimated;
        }
    }

    static class PitchCandidate {

        static final PitchCandidate EMPTY = new PitchCandidate(-1, 0);

        final int pitch;
        final double quality;

        PitchCandidate(int pitch, double quality) {
            this.pitch = pitch >= 0 ? clampMidi(pitch) : -1;
            this.quality = Math.max(0, Math.min(1, quality));
        }
    }

    private static class Neighbor {

        private final Note note;
        private final int distance;

        private Neighbor(Note note, int distance) {
            this.note = note;
            this.distance = distance;
        }
    }

    private static class PitchFrame {

        private final long timeMs;
        private final double frequencyHz;
        private final double volume;
        private final double confidence;

        private PitchFrame(KaraokePitchSample sample) {
            this.timeMs = sample.getTimestampMs();
            this.frequencyHz = sample.getFrequencyHz();
            this.volume = sample.getVolume();
            this.confidence = sample.getConfidence();
        }

        private boolean valid() {
            return frequencyHz >= MIN_FREQUENCY_HZ && frequencyHz <= MAX_FREQUENCY_HZ && volume >= MIN_VOLUME && confidence >= MIN_CONFIDENCE;
        }

        private double midi() {
            return KaraokePitch.frequencyToMidi(frequencyHz);
        }

        private double weight() {
            return confidence * Math.sqrt(Math.max(MIN_VOLUME, volume));
        }
    }

    private static class AnalysisWindow {

        private final long startMs;
        private long endMs;

        private AnalysisWindow(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static class PitchFrameCollector {

        private final float[] ring = new float[FRAME_SIZE];
        private final long[] timeRing = new long[FRAME_SIZE];
        private final float[] frame = new float[FRAME_SIZE];
        private final List<AnalysisWindow> windows;
        private final List<AnalysisWindow> precisionWindows;
        private final List<FrameJob> frameJobs = new ArrayList<>();
        private final List<DetectionFrame> pendingDetectionFrames = new ArrayList<>(DETECTOR_BATCH_SIZE);
        private final ExecutorService detectorExecutor = Executors.newFixedThreadPool(detectorThreadCount());
        private final ThreadLocal<DetectorState> detectorState = ThreadLocal.withInitial(DetectorState::new);
        private VoiceBandpassFilter filter;
        private Biquad resampleLowPass;
        private long sampleCount;
        private long lastFrameSampleCount = -1;
        private long previousSourceIndex;
        private double nextOutputSourcePosition;
        private double resampleStep;
        private float previousSourceSample;
        private boolean hasPreviousSourceSample;
        private int windowIndex;
        private int precisionWindowIndex;
        private int skippedInactive;
        private int skippedSilent;
        private int sourceSampleRate;
        private int sampleRate;
        private volatile long forceFineUntilMs;

        private PitchFrameCollector(int sourceSampleRate, List<AnalysisWindow> windows, List<AnalysisWindow> precisionWindows) {
            this.windows = windows == null ? Collections.emptyList() : windows;
            this.precisionWindows = precisionWindows == null ? Collections.emptyList() : precisionWindows;
            setSourceSampleRate(sourceSampleRate);
        }

        private void setSourceSampleRate(int sourceSampleRate) {
            int safeSource = Math.max(1, sourceSampleRate);
            int safeTarget = Math.min(safeSource, ANALYSIS_TARGET_SAMPLE_RATE);
            if (this.sourceSampleRate == safeSource && this.sampleRate == safeTarget && filter != null) return;
            flushDetectionBatch();
            this.sourceSampleRate = safeSource;
            this.sampleRate = safeTarget;
            this.filter = new VoiceBandpassFilter(safeTarget);
            this.resampleLowPass = safeSource > safeTarget ? Biquad.lowPass(ANALYSIS_LOW_PASS_HZ, safeSource) : null;
            this.resampleStep = safeSource / (double) safeTarget;
        }

        private long sourceIndexOf(long presentationTimeUs) {
            return Math.max(0, Math.round(presentationTimeUs * sourceSampleRate / 1_000_000.0));
        }

        private void add(float value, long sourceIndex) {
            if (hasPreviousSourceSample && (sourceIndex <= previousSourceIndex || sourceIndex - previousSourceIndex > sourceSampleRate)) resetStreamState();
            if (resampleLowPass != null) value = (float) resampleLowPass.process(value);
            if (sampleRate >= sourceSampleRate) {
                addResampled(value, timeMs(sourceIndex));
                return;
            }
            if (!hasPreviousSourceSample) {
                previousSourceSample = value;
                previousSourceIndex = sourceIndex;
                hasPreviousSourceSample = true;
                addResampled(value, timeMs(sourceIndex));
                nextOutputSourcePosition = sourceIndex + resampleStep;
                return;
            }
            while (nextOutputSourcePosition <= sourceIndex) {
                double span = Math.max(1, sourceIndex - previousSourceIndex);
                double fraction = Math.max(0, Math.min(1, (nextOutputSourcePosition - previousSourceIndex) / span));
                addResampled((float) (previousSourceSample + (value - previousSourceSample) * fraction), timeMs(nextOutputSourcePosition));
                nextOutputSourcePosition += resampleStep;
            }
            previousSourceSample = value;
            previousSourceIndex = sourceIndex;
        }

        private void addResampled(float value, long timeMs) {
            if (filter != null) value = filter.process(value);
            int index = (int) (sampleCount % FRAME_SIZE);
            ring[index] = value;
            timeRing[index] = Math.max(0, timeMs);
            sampleCount++;
            if (sampleCount < FRAME_SIZE) return;
            long centerMs = timeRing[(int) ((sampleCount - FRAME_SIZE / 2L) % FRAME_SIZE)];
            int hop = isFinePrecision(centerMs) ? HOP_SIZE : COARSE_HOP_SIZE;
            if (lastFrameSampleCount >= 0 && sampleCount - lastFrameSampleCount < hop) return;
            lastFrameSampleCount = sampleCount;
            if (!isActive(centerMs)) {
                skippedInactive++;
                return;
            }
            copyFrame();
            double volume = rms(frame);
            if (volume < SILENCE_PREFILTER_VOLUME) {
                skippedSilent++;
                frameJobs.add(FrameJob.ready(new PitchFrame(new KaraokePitchSample(centerMs, 0, volume, 0))));
            } else {
                pendingDetectionFrames.add(new DetectionFrame(frame.clone(), centerMs, volume, sampleRate));
                if (pendingDetectionFrames.size() >= DETECTOR_BATCH_SIZE) flushDetectionBatch();
            }
        }

        private void flushDetectionBatch() {
            if (pendingDetectionFrames.isEmpty()) return;
            List<DetectionFrame> batch = new ArrayList<>(pendingDetectionFrames);
            pendingDetectionFrames.clear();
            frameJobs.add(FrameJob.future(detectorExecutor.submit(() -> detectBatch(batch))));
        }

        private List<PitchFrame> detectBatch(List<DetectionFrame> batch) {
            List<PitchFrame> output = new ArrayList<>(batch.size());
            for (DetectionFrame item : batch) {
                YinPitchDetector detector = detectorState.get().detector(item.sampleRate);
                KaraokePitchSample sample = detector.detect(item.input, item.input.length, item.timeMs, item.volume);
                if (sample.getConfidence() < MIN_CONFIDENCE) forceFineUntilMs = Math.max(forceFineUntilMs, item.timeMs + LOW_CONFIDENCE_FINE_MS);
                output.add(new PitchFrame(sample));
            }
            return output;
        }

        private long timeMs(double sourceIndex) {
            return Math.max(0, Math.round(sourceIndex * 1000.0 / sourceSampleRate));
        }

        private void resetStreamState() {
            flushDetectionBatch();
            sampleCount = 0;
            lastFrameSampleCount = -1;
            previousSourceIndex = 0;
            nextOutputSourcePosition = 0;
            previousSourceSample = 0;
            hasPreviousSourceSample = false;
            forceFineUntilMs = 0;
            precisionWindowIndex = 0;
            filter = new VoiceBandpassFilter(sampleRate);
            resampleLowPass = sourceSampleRate > sampleRate ? Biquad.lowPass(ANALYSIS_LOW_PASS_HZ, sourceSampleRate) : null;
        }

        private void copyFrame() {
            long start = sampleCount - FRAME_SIZE;
            for (int i = 0; i < FRAME_SIZE; i++) frame[i] = ring[(int) ((start + i) % FRAME_SIZE)];
        }

        private boolean isActive(long timeMs) {
            if (windows.isEmpty()) return true;
            while (windowIndex < windows.size() && timeMs > windows.get(windowIndex).endMs) windowIndex++;
            return windowIndex < windows.size() && timeMs >= windows.get(windowIndex).startMs;
        }

        private boolean isFinePrecision(long timeMs) {
            if (timeMs <= forceFineUntilMs) return true;
            if (precisionWindows.isEmpty()) return true;
            while (precisionWindowIndex < precisionWindows.size() && timeMs > precisionWindows.get(precisionWindowIndex).endMs) precisionWindowIndex++;
            return precisionWindowIndex < precisionWindows.size() && timeMs >= precisionWindows.get(precisionWindowIndex).startMs;
        }

        private double rms(float[] input) {
            double sum = 0;
            for (float sample : input) sum += sample * sample;
            return Math.min(1, Math.sqrt(sum / Math.max(1, input.length)));
        }

        private List<PitchFrame> frames() {
            flushDetectionBatch();
            List<PitchFrame> result = new ArrayList<>(frameJobs.size());
            for (FrameJob job : frameJobs) {
                List<PitchFrame> frames = job.await();
                if (frames == null || frames.isEmpty()) continue;
                for (PitchFrame frame : frames) {
                    if (frame.volume >= SILENCE_PREFILTER_VOLUME && frame.confidence < MIN_CONFIDENCE) forceFineUntilMs = Math.max(forceFineUntilMs, frame.timeMs + LOW_CONFIDENCE_FINE_MS);
                    result.add(frame);
                }
            }
            detectorExecutor.shutdownNow();
            result.sort(Comparator.comparingLong(frame -> frame.timeMs));
            return result;
        }

        private int sourceSampleRate() {
            return sourceSampleRate;
        }

        private int sampleRate() {
            return sampleRate;
        }

        private int skippedInactive() {
            return skippedInactive;
        }

        private int skippedSilent() {
            return skippedSilent;
        }

        private static int detectorThreadCount() {
            int cores = Runtime.getRuntime().availableProcessors();
            return Math.max(1, Math.min(3, cores - 1));
        }

        private static class FrameJob {

            private final List<PitchFrame> frames;
            private final Future<List<PitchFrame>> future;

            private FrameJob(List<PitchFrame> frames, Future<List<PitchFrame>> future) {
                this.frames = frames;
                this.future = future;
            }

            private static FrameJob ready(PitchFrame frame) {
                return new FrameJob(Collections.singletonList(frame), null);
            }

            private static FrameJob future(Future<List<PitchFrame>> future) {
                return new FrameJob(null, future);
            }

            private List<PitchFrame> await() {
                if (frames != null) return frames;
                if (future == null) return null;
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (ExecutionException e) {
                    return null;
                }
            }
        }

        private static class DetectionFrame {

            private final float[] input;
            private final long timeMs;
            private final double volume;
            private final int sampleRate;

            private DetectionFrame(float[] input, long timeMs, double volume, int sampleRate) {
                this.input = input;
                this.timeMs = timeMs;
                this.volume = volume;
                this.sampleRate = sampleRate;
            }
        }

        private static class DetectorState {

            private int sampleRate;
            private YinPitchDetector detector;

            private YinPitchDetector detector(int sampleRate) {
                if (detector == null || this.sampleRate != sampleRate) {
                    this.sampleRate = sampleRate;
                    detector = new YinPitchDetector(sampleRate, 0.12, 0.08, FRAME_SIZE, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ);
                }
                return detector;
            }
        }
    }

    private static class VoiceBandpassFilter {

        private final Biquad highPass;
        private final Biquad lowPass;

        private VoiceBandpassFilter(double sampleRate) {
            highPass = Biquad.highPass(ANALYSIS_HIGH_PASS_HZ, sampleRate);
            lowPass = Biquad.lowPass(ANALYSIS_LOW_PASS_HZ, sampleRate);
        }

        private float process(float sample) {
            double value = highPass.process(sample);
            value = lowPass.process(value);
            return (float) Math.max(-1, Math.min(1, value));
        }
    }

    private static class Biquad {

        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double x1;
        private double x2;
        private double y1;
        private double y2;

        private Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        private static Biquad highPass(double cutoff, double sampleRate) {
            double w0 = 2 * Math.PI * cutoff / sampleRate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2 * 0.707);
            double a0 = 1 + alpha;
            return new Biquad((1 + cos) / 2 / a0, -(1 + cos) / a0, (1 + cos) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
        }

        private static Biquad lowPass(double cutoff, double sampleRate) {
            double w0 = 2 * Math.PI * cutoff / sampleRate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2 * 0.707);
            double a0 = 1 + alpha;
            return new Biquad((1 - cos) / 2 / a0, (1 - cos) / a0, (1 - cos) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
        }

        private double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    static class ProgressReporter {

        private static final long THROTTLE_MS = 350;

        private final Progress progress;
        private final long startMs = System.currentTimeMillis();
        private int lastPercent = -1;
        private int lastStage = -1;
        private long lastEmitMs;

        ProgressReporter(Progress progress) {
            this.progress = progress;
        }

        void decode(long sampleTimeUs, long durationMs) {
            if (durationMs <= 0 || sampleTimeUs < 0) {
                update(Math.max(lastPercent, 12), STAGE_DECODE);
                return;
            }
            double ratio = Math.max(0, Math.min(1, sampleTimeUs / (durationMs * 1000.0)));
            update(5 + (int) Math.round(ratio * 68), STAGE_DECODE);
        }

        void update(int percent, int stage) {
            if (progress == null) return;
            long now = System.currentTimeMillis();
            int safePercent = Math.max(0, Math.min(100, Math.max(percent, lastPercent)));
            if (safePercent < 100 && safePercent == lastPercent && stage == lastStage && now - lastEmitMs < THROTTLE_MS) return;
            if (safePercent < 100 && now - lastEmitMs < THROTTLE_MS && stage == lastStage) return;
            lastPercent = safePercent;
            lastStage = stage;
            lastEmitMs = now;
            long elapsedMs = Math.max(0, now - startMs);
            long remainingMs = safePercent > 3 && safePercent < 100 ? Math.round(elapsedMs * (100 - safePercent) / (double) safePercent) : -1;
            progress.onProgress(safePercent, stage, elapsedMs, remainingMs);
        }
    }

    private static class PitchWindow {

        private final List<FrameValue> values;
        private final int total;

        private PitchWindow(List<FrameValue> values, int total) {
            this.values = values;
            this.total = total;
        }

        private static PitchWindow from(List<PitchFrame> frames, long startMs, long endMs) {
            List<FrameValue> values = new ArrayList<>();
            int total = 0;
            long safeStart = Math.max(0, startMs - WINDOW_MARGIN_MS);
            long safeEnd = Math.max(safeStart + MIN_NOTE_MS, endMs + WINDOW_MARGIN_MS);
            for (PitchFrame frame : frames) {
                if (frame.timeMs < safeStart) continue;
                if (frame.timeMs >= safeEnd) break;
                total++;
                if (frame.valid()) values.add(new FrameValue(frame.midi(), frame.weight()));
            }
            return new PitchWindow(values, total);
        }

        private PitchCandidate candidate() {
            List<PitchCandidate> candidates = candidates();
            return candidates.isEmpty() ? PitchCandidate.EMPTY : candidates.get(0);
        }

        private List<PitchCandidate> candidates() {
            if (values.size() < 2 || total <= 0) return Collections.emptyList();
            double ratio = values.size() / (double) total;
            if (ratio < MIN_WINDOW_VALID_RATIO) return Collections.emptyList();
            Collections.sort(values, (a, b) -> Double.compare(a.midi, b.midi));
            double median = values.get(values.size() / 2).midi;
            double spread = percentile(0.80) - percentile(0.20);
            if (spread > MAX_WINDOW_SPREAD) return Collections.emptyList();
            int medianPitch = clampMidi((int) Math.round(median));
            double[] scores = weightedScores();
            double totalWeight = totalWeight();
            if (totalWeight <= 0) return Collections.singletonList(new PitchCandidate(medianPitch, ratio * 0.35));
            double bestScore = 0;
            for (int pitch = MIN_MIDI; pitch <= MAX_MIDI; pitch++) bestScore = Math.max(bestScore, scores[pitch]);
            if (bestScore <= 0) return Collections.singletonList(new PitchCandidate(medianPitch, ratio * 0.35));
            double spreadFactor = Math.max(0.15, 1.0 - spread / Math.max(1.0, MAX_WINDOW_SPREAD));
            List<PitchCandidate> result = new ArrayList<>();
            boolean[] used = new boolean[MAX_MIDI + 1];
            for (int count = 0; count < MAX_PATH_CANDIDATES; count++) {
                int bestPitch = -1;
                double best = 0;
                for (int pitch = MIN_MIDI; pitch <= MAX_MIDI; pitch++) {
                    int normalized = normalizeOctave(pitch, medianPitch);
                    if (used[normalized]) continue;
                    if (scores[pitch] <= bestScore * 0.16 && count > 0) continue;
                    if (scores[pitch] > best) {
                        best = scores[pitch];
                        bestPitch = normalized;
                    }
                }
                if (bestPitch < 0) break;
                double modeQuality = Math.max(0, Math.min(1, best / totalWeight));
                double quality = ratio * spreadFactor * Math.max(0.35, modeQuality);
                result.add(new PitchCandidate(bestPitch, quality));
                markUsed(used, bestPitch);
            }
            if (result.isEmpty()) result.add(new PitchCandidate(likelyPitch(medianPitch), ratio * spreadFactor));
            Collections.sort(result, (a, b) -> Double.compare(b.quality, a.quality));
            return result;
        }

        private double percentile(double p) {
            if (values.isEmpty()) return 0;
            int index = (int) Math.max(0, Math.min(values.size() - 1, Math.round((values.size() - 1) * p)));
            return values.get(index).midi;
        }

        private int likelyPitch(int median) {
            double[] scores = weightedScores();
            int best = -1;
            double bestScore = 0;
            for (int pitch = MIN_MIDI; pitch <= MAX_MIDI; pitch++) {
                if (scores[pitch] > bestScore) {
                    bestScore = scores[pitch];
                    best = pitch;
                }
            }
            if (best < 0) return clampMidi(median);
            return normalizeOctave(best, median);
        }

        private double modeQuality(int pitch) {
            double[] scores = weightedScores();
            double total = 0;
            for (FrameValue value : values) total += value.weight;
            if (total <= 0) return 0;
            double score = 0;
            for (int candidate = pitch - 24; candidate <= pitch + 24; candidate += 12) {
                if (candidate >= 0 && candidate < scores.length) score = Math.max(score, scores[candidate]);
            }
            return Math.max(0, Math.min(1, score / total));
        }

        private double totalWeight() {
            double total = 0;
            for (FrameValue value : values) total += value.weight;
            return total;
        }

        private void markUsed(boolean[] used, int pitch) {
            for (int candidate = pitch - 1; candidate <= pitch + 1; candidate++) {
                if (candidate >= 0 && candidate < used.length) used[candidate] = true;
            }
            for (int candidate = pitch - 24; candidate <= pitch + 24; candidate += 12) {
                if (candidate >= 0 && candidate < used.length) used[candidate] = true;
            }
        }

        private double[] weightedScores() {
            double[] scores = new double[MAX_MIDI + 1];
            for (FrameValue value : values) {
                int pitch = clampMidi((int) Math.round(value.midi));
                scores[pitch] += value.weight;
                if (pitch > MIN_MIDI) scores[pitch - 1] += value.weight * 0.22;
                if (pitch < MAX_MIDI) scores[pitch + 1] += value.weight * 0.22;
            }
            return scores;
        }
    }

    private static class FrameValue {

        private final double midi;
        private final double weight;

        private FrameValue(double midi, double weight) {
            this.midi = midi;
            this.weight = Math.max(0.01, weight);
        }
    }
}
