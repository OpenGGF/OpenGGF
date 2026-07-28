package com.openggf.game.sonic1.events;

/**
 * Logical timing model for the queued Nemesis art that gates the Final Zone boss.
 *
 * <p>The engine uploads the art synchronously, but the S1 ROM leaves the second
 * SBZ PLC in {@code v_plc_buffer} when gameplay begins and later appends
 * {@code PLC_FZBoss}. Normal level VBlank consumes three tiles per frame, with
 * one setup frame per PLC entry. Keeping that timing state preserves gameplay
 * branches which poll the queue without delaying the actual renderer.</p>
 */
final class Sonic1FzPlcTimingQueue {
    private static final int LEVEL_START_VBLANK_SLICES_ALREADY_RUN = 7;
    private static final int[] SBZ2_ENTRY_TILE_COUNTS = {
            16, 41, 31, 15, 15, 20, 49, 4, 48, 12, 8, 16, 14
    };
    private static final int[] FZ_BOSS_ENTRY_TILE_COUNTS = {
            76, 156, 108, 144, 17
    };

    private int framesRemaining;

    void resetForFinalZoneGameplay() {
        framesRemaining = framesForEntries(SBZ2_ENTRY_TILE_COUNTS)
                - LEVEL_START_VBLANK_SLICES_ALREADY_RUN;
    }

    void clear() {
        framesRemaining = 0;
    }

    void enqueueFzBossCue() {
        framesRemaining += framesForEntries(FZ_BOSS_ENTRY_TILE_COUNTS);
    }

    void tickVBlank() {
        if (framesRemaining > 0) {
            framesRemaining--;
        }
    }

    int framesRemaining() {
        return framesRemaining;
    }

    void restoreFramesRemaining(int framesRemaining) {
        this.framesRemaining = Math.max(0, framesRemaining);
    }

    private static int framesForEntries(int[] tileCounts) {
        int frames = 0;
        for (int tileCount : tileCounts) {
            frames += 1 + ((tileCount + 2) / 3);
        }
        return frames;
    }
}
