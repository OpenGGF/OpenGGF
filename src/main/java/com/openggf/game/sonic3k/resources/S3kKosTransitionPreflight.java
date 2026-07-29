package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.tools.KosinskiReader;

import java.io.IOException;

/** Validates a direct/direct/module transition batch before its first FIFO mutation. */
public final class S3kKosTransitionPreflight {
    private static final int INSPECTION_LIMIT = 0x40000;

    private S3kKosTransitionPreflight() {
    }

    public static void validate(
            Rom rom,
            S3kKosDecompressionQueue directQueue,
            S3kKosModuleQueue moduleQueue,
            int chunkSource,
            int blockSource,
            int artSource) throws IOException {
        if (directQueue.availableCapacity() < 2 || !moduleQueue.hasCapacity()) {
            throw new IllegalStateException(
                    "S3K transition Kos batch requires two direct slots and one module slot");
        }
        inspectStandard(rom, chunkSource);
        inspectStandard(rom, blockSource);
        inspectModuled(rom, artSource);
    }

    private static void inspectStandard(Rom rom, int source) throws IOException {
        KosinskiReader.inspectStandard(readInspectionWindow(rom, source), 0);
    }

    private static void inspectModuled(Rom rom, int source) throws IOException {
        KosinskiReader.inspectModuled(readInspectionWindow(rom, source), 0);
    }

    private static byte[] readInspectionWindow(Rom rom, int source) throws IOException {
        long remaining = rom.getSize() - source;
        if (source < 0 || remaining < 2) {
            throw new IOException("Kosinski source is outside ROM: 0x"
                    + Integer.toHexString(source));
        }
        return rom.readBytes(source, (int) Math.min(remaining, INSPECTION_LIMIT));
    }
}
