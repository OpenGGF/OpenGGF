package com.openggf.game.soniccd.timetravel;

/**
 * The values {@code SaveTimeWarpData} stores and {@code LoadTimeWarpData} restores
 * (Sonic/Main.asm:532-567; Load Saved Data.asm:7-40). Positions are the player's centre
 * ({@code oX}/{@code oY}); {@code playerFlags} is {@code oFlags} with the on-object (bit 3)
 * and underwater (bit 6) bits already cleared (Sonic/Main.asm:539-541); {@code timeFrames}
 * is {@code time} as minutes*3600 + seconds*60 + frames, capped at 5:00
 * (Sonic/Main.asm:558-564).
 *
 * <p>The camera fields are stored but the destination's {@code LevelSizeLoad_StartPos}
 * recomputes {@code cameraX}/{@code cameraY} from the restored position ({@code .SetupCamera},
 * Palmtree Panic/Scroll (Act 1 Present).asm:63-112), so only the background cameras and
 * the bottom boundary keep their saved values on arrival.
 */
public record CdWarpCarry(int centreX, int centreY, int playerFlags, short groundSpeed, short xSpeed,
                          short ySpeed, int rings, int livesFlags, long timeFrames, int waterRoutine,
                          int bottomBound, int cameraX, int cameraY, int waterHeight, boolean waterFullscreen,
                          boolean miniPlayer) {
    /** {@code cmpi.l #(5<<16)|(0<<8)|0,d0; bcs .CapTime} (Sonic/Main.asm:558-561). */
    public static final long TIME_CAP_FRAMES = 5L * 60 * 60;
    /** {@code bclr #3} and {@code bclr #6} of {@code warpPlayerFlags} (Sonic/Main.asm:540-541). */
    public static final int SAVED_FLAG_MASK = ~((1 << 3) | (1 << 6)) & 0xFF;

    public CdWarpCarry {
        playerFlags &= SAVED_FLAG_MASK;
        timeFrames = Math.min(timeFrames, TIME_CAP_FRAMES);
    }

    public boolean facingLeft() { return (playerFlags & 0x01) != 0; }
    public boolean inAir() { return (playerFlags & 0x02) != 0; }
    public boolean rolling() { return (playerFlags & 0x04) != 0; }
    public boolean rollJumping() { return (playerFlags & 0x10) != 0; }
    public boolean pushing() { return (playerFlags & 0x20) != 0; }
}
