package com.openggf.data;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestRetailRomRevision {
    @Test
    void aRetailHeaderDoesNotVerifyDifferentBytes() {
        byte[] bytes = new byte[0x80000];
        byte[] serial = "GM 00001009-00".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(serial, 0, bytes, 0x180, serial.length);
        assertEquals(RetailRomRevision.UNKNOWN,
                RetailRomRevision.detect(RomByteReader.fromBytes(bytes)));
    }

    @Test
    void recognitionBelongsToTheImmutableReaderSnapshot() throws Exception {
        RomByteReader first = RomByteReader.fromBytes(new byte[0x80000]);
        RomByteReader other = RomByteReader.fromBytes(new byte[0x100000]);
        try (Rom rom = Rom.fromReader(first, "synthetic snapshot")) {
            assertSame(RetailRomRevision.detect(first), RetailRomRevision.detect(rom));
            assertEquals(RetailRomRevision.UNKNOWN, RetailRomRevision.detect(other));
            assertEquals(RetailRomRevision.UNKNOWN, RetailRomRevision.detect(first));
        }
    }
}
