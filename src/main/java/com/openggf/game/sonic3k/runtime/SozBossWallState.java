package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** sub_56706: thirteen background bands, nine opening rows and twelve defeat rows. */
public final class SozBossWallState {
    static final int SNAPSHOT_BYTES = 4 * 13 * Integer.BYTES;
    public static final int SOUND_HIT = 1;
    public static final int SOUND_COLLAPSE = 2;
    public static final int SOUND_RECOVERY = 4;
    private final int[] offsets = new int[13];
    private final int[] displacement = new int[13];
    private final int[] velocity = new int[13];
    private final int[] delays = new int[13];

    public int rowOffset(int row) { return (short) (offsets[row] + (displacement[row] >> 16)); }

    /** Returns sound bits; event and object handshake words remain in their shared owner. */
    public int tick(SozEventState state, boolean defeatRequested, int[] delayTable) {
        int sounds = 0;
        if (defeatRequested && state.bossWallRoutine() != 0x14) {
            for (int row = 1; row <= 12; row++) delays[row] = (row - 1) * 5;
            state.bossWallRoutine(0x14);
        }
        // Native branch fallthrough executes a newly-entered stage in the same dispatch.
        switch (state.bossWallRoutine()) {
            case 0:
                if (state.bossWallHitY() == 0) return 0;
                for (int row = 1; row <= 9; row++) offsets[row] = row - 10;
                state.bossWallTimer(3);
                state.bossWallRoutine(4);
                sounds |= SOUND_HIT;
            case 4:
                state.bossWallTimer((short) (state.bossWallTimer() - 1));
                if (state.bossWallTimer() >= 0) return sounds;
                for (int row = 1; row <= 9; row++) offsets[row] = 2 * row - 19;
                state.bossWallTimer(8);
                state.bossWallRoutine(8);
            case 8:
                state.bossWallTimer((short) (state.bossWallTimer() - 1));
                if (state.bossWallTimer() >= 0) return sounds;
                // hitY - cameraY + bgY - $440; bgY = cameraY + $488 - bossY.
                int relativeHit = (state.bossWallHitY() + 0x48 - state.bossY()) & 0xFFFF;
                // SUBI/BCC: an unsigned borrow clamps the pre-shift result to zero.
                int raw = (state.bossWallHitY() + 0x488 - state.bossY()) & 0xFFFF;
                if (raw < 0x440) relativeHit = 0;
                int byteOffset = Math.min(0x10, (relativeHit >>> 3) & 0xFFFE);
                for (int row = 1; row <= 9; row++) {
                    velocity[row] = 0x80000;
                    delays[row] = delayTable[(0x10 - byteOffset) / 2 + row - 1];
                }
                state.backgroundRowReplacement(0);
                state.bossWallRoutine(0x0C);
                sounds |= SOUND_COLLAPSE;
            case 0x0C:
                int finished = 0;
                for (int row = 1; row <= 9; row++) {
                    int delay = (byte) (delays[row] >> 8);
                    if (delay < 0) { finished++; continue; }
                    if (delay > 0) { delays[row] = (delays[row] - 0x100) & 0xFFFF; continue; }
                    displacement[row] -= velocity[row];
                    velocity[row] -= 0x2800;
                    if (velocity[row] < 0) delays[row] |= 0xFF00;
                }
                if (finished != 9) {
                    if (finished != 0 && state.backgroundRowReplacement() == 0) {
                        state.backgroundRowReplacement(-1);
                        sounds |= SOUND_RECOVERY;
                    }
                    return sounds;
                }
                Arrays.fill(offsets, 1, 10, 0);
                state.bossWallTimer(0);
                state.bossWallRoutine(0x10);
            case 0x10:
                if (state.bossWallTimer() != 0) {
                    state.bossWallTimer(state.bossWallTimer() - 1);
                    if (state.bossWallTimer() == 0) {
                        state.bossWallHitY(0);
                        state.bossWallRoutine(0);
                    }
                    return sounds;
                }
                int recovered = 0;
                for (int row = 1; row <= 9; row++) {
                    int delay = (byte) delays[row];
                    if (delay < 0) { recovered++; continue; }
                    if (delay > 0) { delays[row] = (delays[row] & 0xFF00) | ((delay - 1) & 255); continue; }
                    int step = velocity[row];
                    velocity[row] -= 0x2800;
                    displacement[row] -= step;
                    if (displacement[row] >= 0) {
                        displacement[row] = 0;
                        delays[row] |= 0xFF;
                    }
                }
                if (recovered == 9) state.bossWallTimer(15);
                return sounds;
            case 0x14:
                for (int row = 1; row <= 12; row++) {
                    if (delays[row] != 0) { delays[row]--; continue; }
                    int whole = (short) (displacement[row] >> 16);
                    whole = Math.max(-0x100, whole - 5);
                    displacement[row] = (whole << 16) | (displacement[row] & 0xFFFF);
                }
                return sounds;
            default:
                throw new IllegalStateException("Unknown SOZ boss wall routine " + state.bossWallRoutine());
        }
    }

    public boolean defeatRetractionComplete() {
        for (int row = 1; row <= 12; row++) if ((short) (displacement[row] >> 16) > -0x100) return false;
        return true;
    }

    void capture(ByteBuffer buffer) {
        for (int[] values : new int[][]{offsets, displacement, velocity, delays})
            for (int value : values) buffer.putInt(value);
    }

    void restore(ByteBuffer buffer) {
        for (int[] values : new int[][]{offsets, displacement, velocity, delays})
            for (int i = 0; i < values.length; i++) values[i] = buffer.getInt();
    }
}
