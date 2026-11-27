package com.openggf.game.sonic2.audio.smps;

import com.openggf.data.Rom;
import com.openggf.data.compression.SaxmanDecompressor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Extracts the portable-package zPSG_EnvTbl payload from a REV00 or REV01 ROM. */
public final class Sonic2PackageEnvelopes {
    public static final int COUNT = 13;
    private static final int DECOMPRESS_SOUND_DRIVER = 0xec04a;
    private static final int PAYLOAD = 0xec0e8;
    private static final int COMPRESSED_SIZE = 0x0f64;
    private static final int TABLE = 0x1029;

    private Sonic2PackageEnvelopes() {}

    public static Map<Integer, byte[]> extract(Rom rom) throws IOException {
        // DecompressSoundDriver starts with LEA Snd_Driver(pc),A6 and MOVE.W #size,D7.
        int code = DECOMPRESS_SOUND_DRIVER;
        if (rom.getSize() < PAYLOAD + COMPRESSED_SIZE || rom.read16BitAddr(code) != 0x4dfa
                || code + 2 + (short) rom.read16BitAddr(code + 2) != PAYLOAD
                || rom.read16BitAddr(code + 4) != 0x3e3c || rom.read16BitAddr(code + 6) != COMPRESSED_SIZE) {
            throw new IOException("Unknown S2 Z80 sound driver layout for zPSG_EnvTbl (expected REV00 or REV01)");
        }
        byte[] driver = new SaxmanDecompressor().decompressRaw(
                rom.readBytes(PAYLOAD, COMPRESSED_SIZE), COMPRESSED_SIZE);
        int tableEnd = TABLE + COUNT * 2;
        if (driver.length < tableEnd) throw new IOException("Truncated S2 zPSG_EnvTbl");
        var envelopes = new HashMap<Integer, byte[]>();
        for (int index = 1; index <= COUNT; index++) {
            int slot = TABLE + (index - 1) * 2;
            int start = (driver[slot] & 255) | ((driver[slot + 1] & 255) << 8);
            if (start < tableEnd || start >= driver.length) {
                throw new IOException("Invalid S2 PSG envelope pointer " + index);
            }
            int end = start;
            int limit = Math.min(driver.length, start + 256);
            while (end < limit && (driver[end] & 255) < 0x80) end++;
            if (end == limit || (driver[end] & 255) != 0x80) {
                throw new IOException("Invalid S2 PSG envelope terminator " + index);
            }
            envelopes.put(index, Arrays.copyOfRange(driver, start, end + 1));
        }
        return Map.copyOf(envelopes);
    }
}
