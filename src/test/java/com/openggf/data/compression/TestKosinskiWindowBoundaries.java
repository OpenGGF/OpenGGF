package com.openggf.data.compression;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestKosinskiWindowBoundaries {
    @Test
    void shortOffsetZeroCopiesFrom256BytesBackInBothDecoders() throws Exception {
        assertWindowCopy(256, false);
    }

    @Test
    void longOffsetZeroCopiesFrom8192BytesBackInBothDecoders() throws Exception {
        assertWindowCopy(8192, true);
    }

    private static void assertWindowCopy(int history, boolean longMatch) throws Exception {
        Stream stream = new Stream();
        byte[] expected = new byte[history + (longMatch ? 3 : 2)];
        for (int index = 0; index < history; index++) {
            expected[index] = (byte) (index + 17);
            stream.bit(true);
            stream.byteValue(expected[index]);
        }
        stream.bit(false);
        stream.bit(longMatch);
        if (longMatch) {
            stream.byteValue(0);
            stream.byteValue(1); // encoded offset 0, length 3
        } else {
            stream.bit(false);
            stream.bit(false); // length 2
            stream.byteValue(0);
        }
        for (int index = history; index < expected.length; index++) {
            expected[index] = expected[index - history];
        }
        stream.bit(false);
        stream.bit(true);
        stream.byteValue(0);
        stream.byteValue(0);
        stream.byteValue(0); // terminator
        byte[] encoded = stream.bytes();

        assertArrayEquals(expected, KosinskiReader.decompress(
                Channels.newChannel(new ByteArrayInputStream(encoded))), "synchronous decoder");
        ResumableKosinskiDecoder decoder = new ResumableKosinskiDecoder(encoded);
        while (!decoder.complete()) decoder.step(31);
        assertArrayEquals(expected, decoder.output(), "resumable decoder");
        assertEquals(expected.length, KosinskiReader.inspectStandard(encoded, 0).decompressedLength());
    }

    /** Descriptor refill occurs immediately on consuming its sixteenth bit, as in KosDec. */
    private static final class Stream {
        private final byte[] bytes = new byte[10000];
        private int size = 2;
        private int descriptor;
        private int bit;

        void bit(boolean value) {
            if (value) bytes[descriptor + bit / 8] |= (byte) (1 << (bit % 8));
            if (++bit == 16) {
                descriptor = size;
                size += 2;
                bit = 0;
            }
        }

        void byteValue(int value) { bytes[size++] = (byte) value; }
        byte[] bytes() { return Arrays.copyOf(bytes, size); }
    }
}
