package com.openggf.audio.smps;

import java.io.IOException;

/** Resolves native package asset references against one launch cartridge. */
public interface PackageStockResolver {
    record Locator(String table, int index, int songId) {}

    byte[] resolve(Locator locator) throws IOException;

    default boolean unknownImage() { return false; }

    static byte[] decodedDacSample(DacData dac, int note) {
        if (dac == null) return null;
        var entry = dac.mappingForNote(note);
        var sample = entry == null ? null : dac.sample(entry.sampleId());
        if (sample == null || sample.length() > 65535) return null;
        byte[] bytes = new byte[sample.length()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = sample.byteAt(i);
        return bytes;
    }
}
