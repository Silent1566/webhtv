package com.fongmi.android.tv.player.exo;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;

import java.io.EOFException;
import java.io.IOException;
import java.util.Collections;

/** Extracts Monkey's Audio frames in the packet layout expected by FFmpeg's APE decoder. */
final class ApeExtractor implements Extractor {

    static final String MIME_TYPE = "audio/ape";

    private static final int MAGIC = 0x2043414d; // "MAC " in little-endian form.
    private static final int MIN_VERSION = 3800;
    private static final int MAX_VERSION = 3990;
    private static final int NEW_DESCRIPTOR_VERSION = 3980;
    private static final int NEW_DESCRIPTOR_SIZE = 52;
    private static final int NEW_HEADER_SIZE = 24;
    private static final int OLD_HEADER_SIZE = 32;
    private static final int APE_EXTRADATA_SIZE = 6;
    private static final int PACKET_PREFIX_SIZE = 8;
    private static final int FLAG_8_BIT = 1;
    private static final int FLAG_HAS_PEAK_LEVEL = 4;
    private static final int FLAG_24_BIT = 8;
    private static final int FLAG_HAS_SEEK_ELEMENTS = 16;
    private static final int FLAG_CREATE_WAV_HEADER = 32;
    private static final int MAX_FRAME_COUNT = 1_000_000;
    private static final long MAX_METADATA_BYTES = 64L * 1024 * 1024;
    private static final long MAX_FRAME_SIZE = 64L * 1024 * 1024;

    private final byte[] sniffBuffer = new byte[6];
    private final byte[] integerBuffer = new byte[8];

    private ExtractorOutput extractorOutput;
    private TrackOutput trackOutput;
    private Header header;
    private int currentFrame;

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        boolean available = input.peekFully(sniffBuffer, 0, sniffBuffer.length, true);
        input.resetPeekPosition();
        if (!available) return false;
        return readIntLittleEndian(sniffBuffer, 0) == MAGIC
                && readUnsignedShortLittleEndian(sniffBuffer, 4) >= MIN_VERSION
                && readUnsignedShortLittleEndian(sniffBuffer, 4) <= MAX_VERSION;
    }

    @Override
    public void init(ExtractorOutput output) {
        extractorOutput = output;
        trackOutput = output.track(0, C.TRACK_TYPE_AUDIO);
        output.endTracks();
    }

    @Override
    public @ReadResult int read(
            @NonNull ExtractorInput input, @NonNull PositionHolder seekPosition) throws IOException {
        if (header == null) parseHeader(input);
        if (currentFrame >= header.totalFrames) return RESULT_END_OF_INPUT;

        long framePosition = header.framePositions[currentFrame];
        if (input.getPosition() != framePosition) {
            seekPosition.position = framePosition;
            return RESULT_SEEK;
        }

        int frameSize = header.frameSizes[currentFrame];
        int packetSize = checkedPacketSize(frameSize);
        byte[] packet = new byte[packetSize];
        writeLittleEndianInt(packet, 0, header.frameBlocks(currentFrame));
        writeLittleEndianInt(packet, 4, header.frameSkips[currentFrame]);
        input.readFully(packet, PACKET_PREFIX_SIZE, frameSize);
        trackOutput.sampleData(new ParsableByteArray(packet), packet.length);
        trackOutput.sampleMetadata(
                header.frameTimesUs[currentFrame], C.BUFFER_FLAG_KEY_FRAME, packet.length, 0, null);
        currentFrame++;
        return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {
        if (header == null) {
            currentFrame = 0;
            return;
        }
        currentFrame = header.frameIndexForPosition(position);
    }

    @Override
    public void release() {
        extractorOutput = null;
        trackOutput = null;
        header = null;
        currentFrame = 0;
    }

    private void parseHeader(ExtractorInput input) throws IOException {
        long startPosition = input.getPosition();
        byte[] base = new byte[NEW_DESCRIPTOR_SIZE];
        input.readFully(base, 0, OLD_HEADER_SIZE);
        int magic = readIntLittleEndian(base, 0);
        int version = readUnsignedShortLittleEndian(base, 4);
        if (magic != MAGIC || version < MIN_VERSION || version > MAX_VERSION) {
            throw new IOException("Invalid APE header");
        }

        long descriptorLength;
        long headerLength;
        long seekTableLength;
        long wavHeaderLength;
        long wavTailLength;
        int compressionType;
        int formatFlags;
        long blocksPerFrame;
        long finalFrameBlocks;
        long totalFramesLong;
        int bitsPerSample;
        int channels;
        long sampleRateLong;
        boolean legacy = version < NEW_DESCRIPTOR_VERSION;

        if (!legacy) {
            input.readFully(base, OLD_HEADER_SIZE, NEW_DESCRIPTOR_SIZE - OLD_HEADER_SIZE);
            descriptorLength = readUnsignedIntLittleEndian(base, 8);
            headerLength = readUnsignedIntLittleEndian(base, 12);
            seekTableLength = readUnsignedIntLittleEndian(base, 16);
            wavHeaderLength = readUnsignedIntLittleEndian(base, 20);
            wavTailLength = readUnsignedIntLittleEndian(base, 32);
            if (descriptorLength < NEW_DESCRIPTOR_SIZE || descriptorLength > MAX_METADATA_BYTES
                    || headerLength < NEW_HEADER_SIZE || headerLength > MAX_METADATA_BYTES
                    || seekTableLength > MAX_METADATA_BYTES) {
                throw new IOException("Invalid APE descriptor lengths");
            }
            skipFully(input, descriptorLength - NEW_DESCRIPTOR_SIZE);
            byte[] audioHeader = new byte[NEW_HEADER_SIZE];
            input.readFully(audioHeader, 0, audioHeader.length);
            compressionType = readUnsignedShortLittleEndian(audioHeader, 0);
            formatFlags = readUnsignedShortLittleEndian(audioHeader, 2);
            blocksPerFrame = readUnsignedIntLittleEndian(audioHeader, 4);
            finalFrameBlocks = readUnsignedIntLittleEndian(audioHeader, 8);
            totalFramesLong = readUnsignedIntLittleEndian(audioHeader, 12);
            bitsPerSample = readUnsignedShortLittleEndian(audioHeader, 16);
            channels = readUnsignedShortLittleEndian(audioHeader, 18);
            sampleRateLong = readUnsignedIntLittleEndian(audioHeader, 20);
            skipFully(input, headerLength - NEW_HEADER_SIZE);
        } else {
            descriptorLength = 0;
            headerLength = OLD_HEADER_SIZE;
            compressionType = readUnsignedShortLittleEndian(base, 6);
            formatFlags = readUnsignedShortLittleEndian(base, 8);
            channels = readUnsignedShortLittleEndian(base, 10);
            sampleRateLong = readUnsignedIntLittleEndian(base, 12);
            wavHeaderLength = readUnsignedIntLittleEndian(base, 16);
            wavTailLength = readUnsignedIntLittleEndian(base, 20);
            totalFramesLong = readUnsignedIntLittleEndian(base, 24);
            finalFrameBlocks = readUnsignedIntLittleEndian(base, 28);
            if ((formatFlags & FLAG_HAS_PEAK_LEVEL) != 0) {
                readUnsignedInt(input);
                headerLength += 4;
            }
            long seekElements = totalFramesLong;
            if ((formatFlags & FLAG_HAS_SEEK_ELEMENTS) != 0) {
                seekElements = readUnsignedInt(input);
                headerLength += 4;
            }
            seekTableLength = checkedMultiply(seekElements, 4, "seek table length");
            bitsPerSample = (formatFlags & FLAG_8_BIT) != 0
                    ? 8 : (formatFlags & FLAG_24_BIT) != 0 ? 24 : 16;
            if (version >= 3950) {
                blocksPerFrame = 73728L * 4;
            } else if (version >= 3900 || (version >= 3800 && compressionType >= 4000)) {
                blocksPerFrame = 73728;
            } else {
                blocksPerFrame = 9216;
            }
            if ((formatFlags & FLAG_CREATE_WAV_HEADER) == 0) {
                skipFully(input, wavHeaderLength);
            } else if (wavHeaderLength != 0) {
                throw new IOException("APE generated WAV header has non-zero stored length");
            }
        }

        if (totalFramesLong <= 0 || totalFramesLong > MAX_FRAME_COUNT
                || blocksPerFrame <= 0 || blocksPerFrame > Integer.MAX_VALUE
                || finalFrameBlocks <= 0 || finalFrameBlocks > Integer.MAX_VALUE
                || sampleRateLong <= 0 || sampleRateLong > Integer.MAX_VALUE
                || channels <= 0 || channels > 2
                || (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24)
                || seekTableLength < checkedMultiply(totalFramesLong, 4, "seek table minimum")) {
            throw new IOException("Unsupported or invalid APE stream parameters");
        }

        int totalFrames = (int) totalFramesLong;
        long[] rawPositions = new long[totalFrames];
        long firstFrame = checkedAdd(
                startPosition,
                checkedAdd(
                        checkedAdd(descriptorLength, headerLength, "APE header"),
                        checkedAdd(
                                checkedAdd(seekTableLength, wavHeaderLength, "APE data offset"),
                                legacy && version < 3810 ? totalFramesLong : 0,
                                "APE legacy data offset"),
                        "APE data offset"),
                "APE data offset");
        for (int i = 0; i < totalFrames; i++) {
            long entry = readUnsignedInt(input);
            if (i == 0) {
                rawPositions[i] = firstFrame;
            } else {
                rawPositions[i] = entry + startPosition;
                if (rawPositions[i] <= rawPositions[i - 1]) {
                    throw new IOException("APE seek table is not increasing");
                }
            }
        }
        long extraSeekEntries = (seekTableLength / 4) - totalFrames;
        skipFully(input, checkedMultiply(extraSeekEntries, 4, "APE extra seek entries"));
        if (!legacy) skipFully(input, wavHeaderLength);

        int[] legacyBits = null;
        if (legacy && version < 3810) {
            legacyBits = new int[totalFrames];
            for (int i = 0; i < totalFrames; i++) legacyBits[i] = readUnsignedByte(input);
        }

        long[] rawSizes = new long[totalFrames];
        for (int i = 0; i + 1 < totalFrames; i++) {
            rawSizes[i] = rawPositions[i + 1] - rawPositions[i];
        }
        long fileLength = input.getLength();
        long finalSize = fileLength > 0
                ? fileLength - rawPositions[totalFrames - 1] - wavTailLength
                : -1;
        if (finalSize <= 0) finalSize = finalFrameBlocks * 8L;
        finalSize -= finalSize & 3;
        rawSizes[totalFrames - 1] = finalSize;

        long[] framePositions = new long[totalFrames];
        int[] frameSizes = new int[totalFrames];
        int[] frameSkips = new int[totalFrames];
        long[] frameTimesUs = new long[totalFrames];
        long totalBlocks = 0;
        long maxFrameSize = 0;
        for (int i = 0; i < totalFrames; i++) {
            long skip = (rawPositions[i] - firstFrame) & 3;
            long position = rawPositions[i] - skip;
            long size = checkedAdd(rawSizes[i], skip, "APE frame size");
            size = (size + 3) & ~3L;
            if (legacyBits != null) {
                if (i > 0 && legacyBits[i] != 0) {
                    size = checkedAdd(size, 4, "APE legacy frame size");
                }
                skip = checkedAdd(skip * 8, legacyBits[i], "APE legacy bit offset");
            }
            if (size <= 0 || size > MAX_FRAME_SIZE || size > Integer.MAX_VALUE - PACKET_PREFIX_SIZE) {
                throw new IOException("APE frame exceeds supported size");
            }
            framePositions[i] = position;
            frameSizes[i] = (int) size;
            frameSkips[i] = (int) skip;
            frameTimesUs[i] = toTimeUs(totalBlocks, (int) sampleRateLong);
            totalBlocks = checkedAdd(
                    totalBlocks,
                    i + 1 == totalFrames ? finalFrameBlocks : blocksPerFrame,
                    "APE sample count");
            maxFrameSize = Math.max(maxFrameSize, size);
        }
        long durationUs = toTimeUs(totalBlocks, (int) sampleRateLong);
        byte[] extraData = new byte[APE_EXTRADATA_SIZE];
        writeLittleEndianShort(extraData, 0, version);
        writeLittleEndianShort(extraData, 2, compressionType);
        writeLittleEndianShort(extraData, 4, formatFlags);
        header = new Header(
                version,
                compressionType,
                formatFlags,
                (int) blocksPerFrame,
                (int) finalFrameBlocks,
                totalFrames,
                bitsPerSample,
                channels,
                (int) sampleRateLong,
                durationUs,
                framePositions,
                frameSizes,
                frameSkips,
                frameTimesUs,
                extraData,
                (int) Math.min(Integer.MAX_VALUE, maxFrameSize + PACKET_PREFIX_SIZE));
        trackOutput.format(
                new Format.Builder()
                        .setContainerMimeType(MIME_TYPE)
                        .setSampleMimeType(MIME_TYPE)
                        .setChannelCount(channels)
                        .setSampleRate((int) sampleRateLong)
                        .setAverageBitrate(
                                (int) Math.min(Integer.MAX_VALUE, (fileLength > 0
                                        ? fileLength * 8L * sampleRateLong / Math.max(1, totalBlocks)
                                        : 0)))
                        .setMaxInputSize(header.maxInputSize)
                        .setInitializationData(Collections.singletonList(extraData))
                        .build());
        trackOutput.durationUs(durationUs);
        extractorOutput.seekMap(new ApeSeekMap(durationUs, framePositions, frameTimesUs));
    }

    private int readUnsignedByte(ExtractorInput input) throws IOException {
        int read = input.read(integerBuffer, 0, 1);
        if (read != 1) throw new EOFException("Truncated APE header");
        return integerBuffer[0] & 0xFF;
    }

    private long readUnsignedInt(ExtractorInput input) throws IOException {
        input.readFully(integerBuffer, 0, 4);
        return readUnsignedIntLittleEndian(integerBuffer, 0);
    }

    private static int checkedPacketSize(int frameSize) throws IOException {
        if (frameSize < 0 || frameSize > Integer.MAX_VALUE - PACKET_PREFIX_SIZE) {
            throw new IOException("Invalid APE packet size");
        }
        return frameSize + PACKET_PREFIX_SIZE;
    }

    private static void skipFully(ExtractorInput input, long length) throws IOException {
        while (length > 0) {
            int chunk = (int) Math.min(length, Integer.MAX_VALUE);
            input.skipFully(chunk);
            length -= chunk;
        }
    }

    private static long checkedAdd(long left, long right, String what) throws IOException {
        if (right < 0 || left > Long.MAX_VALUE - right) throw new IOException("Overflow in " + what);
        return left + right;
    }

    private static long checkedMultiply(long left, long right, String what) throws IOException {
        if (left < 0 || right < 0 || (left != 0 && right > Long.MAX_VALUE / left)) {
            throw new IOException("Overflow in " + what);
        }
        return left * right;
    }

    private static long toTimeUs(long blocks, int sampleRate) throws IOException {
        if (blocks < 0 || blocks > Long.MAX_VALUE / C.MICROS_PER_SECOND) {
            throw new IOException("APE duration overflow");
        }
        return blocks * C.MICROS_PER_SECOND / sampleRate;
    }

    private static int readIntLittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static long readUnsignedIntLittleEndian(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    private static int readUnsignedShortLittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static void writeLittleEndianInt(byte[] data, int offset, long value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeLittleEndianShort(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    static final class Header {
        final int fileVersion;
        final int compressionType;
        final int formatFlags;
        final int blocksPerFrame;
        final int finalFrameBlocks;
        final int totalFrames;
        final int bitsPerSample;
        final int channels;
        final int sampleRate;
        final long durationUs;
        final long[] framePositions;
        final int[] frameSizes;
        final int[] frameSkips;
        final long[] frameTimesUs;
        final byte[] extraData;
        final int maxInputSize;

        Header(
                int fileVersion,
                int compressionType,
                int formatFlags,
                int blocksPerFrame,
                int finalFrameBlocks,
                int totalFrames,
                int bitsPerSample,
                int channels,
                int sampleRate,
                long durationUs,
                long[] framePositions,
                int[] frameSizes,
                int[] frameSkips,
                long[] frameTimesUs,
                byte[] extraData,
                int maxInputSize) {
            this.fileVersion = fileVersion;
            this.compressionType = compressionType;
            this.formatFlags = formatFlags;
            this.blocksPerFrame = blocksPerFrame;
            this.finalFrameBlocks = finalFrameBlocks;
            this.totalFrames = totalFrames;
            this.bitsPerSample = bitsPerSample;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.durationUs = durationUs;
            this.framePositions = framePositions;
            this.frameSizes = frameSizes;
            this.frameSkips = frameSkips;
            this.frameTimesUs = frameTimesUs;
            this.extraData = extraData;
            this.maxInputSize = maxInputSize;
        }

        long frameBlocks(int index) {
            return index + 1 == totalFrames ? finalFrameBlocks : blocksPerFrame;
        }

        int frameIndexForPosition(long position) {
            int low = 0;
            int high = framePositions.length - 1;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (framePositions[middle] <= position) low = middle;
                else high = middle - 1;
            }
            return low;
        }
    }

    private static final class ApeSeekMap implements SeekMap {
        private final long durationUs;
        private final long[] positions;
        private final long[] timesUs;

        ApeSeekMap(long durationUs, long[] positions, long[] timesUs) {
            this.durationUs = durationUs;
            this.positions = positions;
            this.timesUs = timesUs;
        }

        @Override
        public boolean isSeekable() {
            return positions.length > 0;
        }

        @Override
        public long getDurationUs() {
            return durationUs;
        }

        @Override
        public SeekPoints getSeekPoints(long timeUs) {
            int index = 0;
            int low = 0;
            int high = timesUs.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (timesUs[middle] <= timeUs) {
                    index = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            SeekPoint first = new SeekPoint(timesUs[index], positions[index]);
            if (index == timesUs.length - 1 || timesUs[index] == timeUs) {
                return new SeekPoints(first);
            }
            return new SeekPoints(first, new SeekPoint(timesUs[index + 1], positions[index + 1]));
        }
    }
}
