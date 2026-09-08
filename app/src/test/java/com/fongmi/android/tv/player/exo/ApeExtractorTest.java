package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

public final class ApeExtractorTest {

    @Test
    public void sniffRecognizesCurrentApeHeader() throws Exception {
        byte[] source = apeFile(3990, 1, 4);
        ApeExtractor extractor = new ApeExtractor();

        assertTrue(extractor.sniff(input(source)));
        assertFalse(extractor.sniff(input(new byte[] {'B', 'A', 'D', ' ', 0, 0})));
        byte[] invalidVersion = Arrays.copyOf(source, source.length);
        invalidVersion[4] = (byte) 0xAF;
        invalidVersion[5] = 0x0E;
        assertFalse(extractor.sniff(input(invalidVersion)));
    }

    @Test
    public void readEmitsFfmpegCompatiblePacketsAndSeekMap() throws Exception {
        byte[] source = apeFile(3970, 3, 4);
        ApeExtractor extractor = new ApeExtractor();
        CaptureOutput output = new CaptureOutput();
        extractor.init(output);
        ExtractorInput input = input(source);
        PositionHolder seekPosition = new PositionHolder();

        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, seekPosition));
        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, seekPosition));
        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, seekPosition));
        assertEquals(Extractor.RESULT_END_OF_INPUT, extractor.read(input, seekPosition));

        assertEquals(3, output.track.samples.size());
        assertEquals("audio/ape", output.track.format.sampleMimeType);
        assertEquals(44100, output.track.format.sampleRate);
        assertEquals(2, output.track.format.channelCount);
        assertEquals(6, output.track.format.initializationData.get(0).length);
        assertArrayEquals(new byte[] {(byte) 0x82, 0x0F, (byte) 0x88, 0x13, 0, 0},
                output.track.format.initializationData.get(0));
        assertEquals(73728 * 4, littleEndianInt(output.track.samples.get(0), 0));
        assertEquals(0, littleEndianInt(output.track.samples.get(0), 4));
        assertEquals(4, output.track.samples.get(0).length - 8);
        long expectedBlocks = 73728L * 4 * 2 + 4;
        assertEquals(expectedBlocks * C.MICROS_PER_SECOND / 44100,
                output.seekMap.getDurationUs());
        assertEquals(44, output.seekMap.getSeekPoints(0).first.position);
        assertNotNull(output.seekMap.getSeekPoints(1_000_000).first);
    }

    @Test
    public void truncatedHeaderFailsWithoutEmittingSamples() throws Exception {
        ApeExtractor extractor = new ApeExtractor();
        CaptureOutput output = new CaptureOutput();
        extractor.init(output);

        assertThrows(EOFException.class,
                () -> extractor.read(input(new byte[] {'M', 'A', 'C', ' ', (byte) 0x82, 0x0F}),
                        new PositionHolder()));
        assertTrue(output.track.samples.isEmpty());
    }

    private static byte[] apeFile(int version, int frameCount, int frameSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeInt(output, 0x2043414d);
        writeShort(output, version);
        writeShort(output, 5000);
        writeShort(output, 0);
        writeShort(output, 2);
        writeInt(output, 44100);
        writeInt(output, 0);
        writeInt(output, 0);
        writeInt(output, frameCount);
        writeInt(output, 4);
        int firstFrame = 32 + frameCount * 4;
        for (int index = 0; index < frameCount; index++) {
            writeInt(output, index == 0 ? 0 : firstFrame + index * frameSize);
        }
        for (int index = 0; index < frameCount * frameSize; index++) {
            output.write(index + 1);
        }
        return output.toByteArray();
    }

    private static ExtractorInput input(byte[] source) {
        return new DefaultExtractorInput(new ByteArrayDataReader(source), 0, source.length);
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static final class ByteArrayDataReader implements DataReader {
        private final ByteArrayInputStream input;

        ByteArrayDataReader(byte[] source) {
            input = new ByteArrayInputStream(source);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return input.read(buffer, offset, length);
        }
    }

    private static final class CaptureOutput implements ExtractorOutput {
        final CaptureTrackOutput track = new CaptureTrackOutput();
        SeekMap seekMap;

        @Override
        public TrackOutput track(int id, int type) {
            assertEquals(0, id);
            assertEquals(C.TRACK_TYPE_AUDIO, type);
            return track;
        }

        @Override
        public void endTracks() {
        }

        @Override
        public void seekMap(SeekMap seekMap) {
            this.seekMap = seekMap;
        }
    }

    private static final class CaptureTrackOutput implements TrackOutput {
        Format format;
        final java.util.List<byte[]> samples = new java.util.ArrayList<>();

        @Override
        public void format(Format format) {
            this.format = format;
        }

        @Override
        public int sampleData(DataReader input, int length, boolean allowEndOfInput, int sampleDataPart)
                throws IOException {
            byte[] data = new byte[length];
            int read = input.read(data, 0, length);
            if (read == -1 && allowEndOfInput) return 0;
            if (read != length) throw new EOFException();
            return read;
        }

        @Override
        public void sampleData(ParsableByteArray data, int length, int sampleDataPart) {
            byte[] sample = new byte[length];
            data.readBytes(sample, 0, length);
            samples.add(sample);
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset, CryptoData cryptoData) {
        }
    }
}
