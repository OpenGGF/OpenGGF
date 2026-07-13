package com.openggf.game.rewind;

import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/** Placement setup shared by graph tests that need all fixture parents live at capture. */
public final class S3kGraphRewindTestSupport {
    private static final int LOAD_AHEAD = 0x280;
    private static final int UNLOAD_BEHIND = 0x80;
    private static final int CHUNK_MASK = 0xFF80;

    private S3kGraphRewindTestSupport() {
    }

    /**
     * Chooses a chunk-aligned camera X whose native S3K placement window contains every spawn.
     * Graph fixtures intentionally exercise multiple placement parents at once, so an invalid
     * fixture span fails loudly instead of depending on a widescreen renderer to widen ObjPosLoad.
     */
    public static int cameraXFor(List<ObjectSpawn> spawns) {
        if (spawns.isEmpty()) {
            return 0;
        }
        int minX = spawns.stream().mapToInt(ObjectSpawn::x).min().orElseThrow();
        int maxX = spawns.stream().mapToInt(ObjectSpawn::x).max().orElseThrow();
        int cameraChunk = maxX < LOAD_AHEAD ? 0 : (maxX - 0x200) & CHUNK_MASK;
        int windowStart = Math.max(0, cameraChunk - UNLOAD_BEHIND);
        int windowEnd = cameraChunk + LOAD_AHEAD;
        if (minX < windowStart || maxX >= windowEnd) {
            throw new IllegalArgumentException(
                    "S3K graph fixture span does not fit one native placement window: "
                            + Integer.toHexString(minX) + ".." + Integer.toHexString(maxX));
        }
        return cameraChunk;
    }
}
