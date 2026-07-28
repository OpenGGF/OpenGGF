package com.openggf.tools;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResumableKosinskiDecoder {
    private static final byte[] ABC_STREAM = {
            0x17, 0x00,
            'A', 'B', 'C',
            0x00, 0x00, 0x00
    };

    @Test
    void pausesOnDescriptorBudgetAndMatchesSynchronousDecoder() throws Exception {
        ResumableKosinskiDecoder decoder = new ResumableKosinskiDecoder(ABC_STREAM);

        DecoderStepResult first = decoder.step(2);

        assertFalse(first.complete());
        assertArrayEquals(new byte[] {'A', 'B'}, decoder.output());

        while (!decoder.complete()) {
            decoder.step(1);
        }

        assertArrayEquals(
                KosinskiReader.decompress(Channels.newChannel(
                        new ByteArrayInputStream(ABC_STREAM))),
                decoder.output());
    }

    @Test
    void snapshotRestoresDescriptorAndOutputState() throws Exception {
        ResumableKosinskiDecoder original = new ResumableKosinskiDecoder(ABC_STREAM);
        original.step(2);

        ResumableKosinskiDecoder restored =
                ResumableKosinskiDecoder.fromSnapshot(original.snapshot());

        assertFalse(restored.complete());
        while (!original.complete()) {
            original.step(1);
        }
        while (!restored.complete()) {
            restored.step(1);
        }

        assertTrue(restored.complete());
        assertArrayEquals(original.output(), restored.output());
    }

    @Test
    void rejectsBackreferenceBeforeAnyOutput() throws Exception {
        byte[] invalidBackreference = {
                0x04, 0x00,
                (byte) 0xFF
        };

        ResumableKosinskiDecoder decoder =
                new ResumableKosinskiDecoder(invalidBackreference);

        assertThrows(java.io.IOException.class, () -> decoder.step(1));
    }

    @Test
    void scannerCountsStandardKosDescriptorThroughTerminator() throws Exception {
        KosinskiReader.StandardArchiveInfo info = KosinskiReader.inspectStandard(
                ABC_STREAM, 0);

        assertEquals(ABC_STREAM.length, info.compressedLength());
        assertEquals(3, info.decompressedLength());
    }
}
