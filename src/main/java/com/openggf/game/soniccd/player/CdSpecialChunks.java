package com.openggf.game.soniccd.player;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The body of Sonic CD's {@code ObjSonic_SpecialChunks} past its zone gate (scddisasm
 * src/Level/_Objects/Sonic/Main.asm:2510-2637), the CD form of Sonic 1's Sonic_Loops:
 * roll tunnels force a roll, loop chunks pick the collision path from the player's
 * position in the chunk and its angle. The four IDs are the stage's {@code SpecChunks}
 * ({@link CdPlayerStageTables#specialChunks}); a zone profile supplies the record with
 * its zone's variant. Path bit 6 of {@code oSprFlags} is the engine's loop low plane.
 *
 * @param loop          {@code specialChunks+0}
 * @param specialLoop   {@code specialChunks+1}
 * @param specialRoll   {@code specialChunks+2}
 * @param roll          {@code specialChunks+3}
 * @param specialRollMinY the in-chunk Y from which the special roll tunnel rolls, or -1
 *                      when it always rolls. Palmtree Panic tests {@code (oY & $FF) >= $90}
 *                      and otherwise goes on to the loop checks (Main.asm:2522-2532); the
 *                      other zones branch straight to {@code .RollTunnel}.
 * @param specialLoopRule what the special loop chunk does ({@code .CheckIfInAir}, Main.asm:2549-2556)
 */
public record CdSpecialChunks(int loop, int specialLoop, int specialRoll, int roll, int specialRollMinY,
                              SpecialLoopRule specialLoopRule) {
    /** {@code .CheckIfInAir}'s two shapes. */
    public enum SpecialLoopRule {
        /** Airborne forces the lower path; grounded runs the ordinary loop checks. */
        AIRBORNE_LOWER_PATH,
        /** Stardust Speedway's {@code .SSZ} quadrant test (Main.asm:2607-2637). */
        STARDUST_QUADRANTS
    }

    /** The record for a stage's four {@code SpecChunks} bytes. */
    public static CdSpecialChunks of(int[] ids, int specialRollMinY, SpecialLoopRule rule) {
        return new CdSpecialChunks(ids[0], ids[1], ids[2], ids[3], specialRollMinY, rule);
    }

    /** The record for {@code payload}'s own {@code SpecChunks}, or null when its file has none. */
    public static CdSpecialChunks forStage(com.openggf.game.soniccd.CdStagePayload payload,
                                           com.openggf.game.soniccd.level.CdStageImage image,
                                           int specialRollMinY, SpecialLoopRule rule) {
        int[] ids = CdPlayerStageTables.specialChunks(payload, image);
        return ids == null ? null : of(ids, specialRollMinY, rule);
    }

    /**
     * Runs the routine for the raw layout byte the player is in ({@code move.b (a1,d0.w),d1}
     * keeps the loop bit, Main.asm:2511-2518). Returns true when it reached
     * {@code .RollTunnel}, whose {@code jmp ObjSonic_StartRoll} the caller performs.
     */
    public boolean apply(AbstractPlayableSprite player, int rawChunk) {
        int x = player.getCentreX() & 0xFFFF;
        int y = player.getCentreY() & 0xFFFF;
        if (rawChunk == specialRoll) {
            if (specialRollMinY < 0 || (y & 0xFF) >= specialRollMinY) {
                return true;                                   // .RollTunnel
            }
        } else if (rawChunk == roll) {
            return true;                                       // .NotRoll -> .RollTunnel
        }
        // .CheckIfLoop (Main.asm:2540-2547)
        if (rawChunk == loop) {
            checkLoopPosition(player, x);
        } else if (rawChunk == specialLoop) {
            if (specialLoopRule == SpecialLoopRule.STARDUST_QUADRANTS) {
                stardustQuadrants(player, x, y);
            } else if (player.getAir()) {
                player.setLoopLowPlane(false);                 // .CheckIfInAir: bclr #6
            } else {
                checkLoopPosition(player, x);
            }
        } else {
            player.setLoopLowPlane(false);                     // bclr #6,oSprFlags
        }
        return false;
    }

    /** {@code .CheckIfLeft} to {@code .End}: byte compares on the low byte of oX (Main.asm:2558-2589). */
    private static void checkLoopPosition(AbstractPlayableSprite player, int x) {
        int inChunk = x & 0xFF;
        if (inChunk < 0x2C) {
            player.setLoopLowPlane(false);
            return;
        }
        if (inChunk >= 0xE0) {
            player.setLoopLowPlane(true);
            return;
        }
        int angle = player.getAngle() & 0xFF;
        if (!player.isLoopLowPlane()) {
            if (angle != 0 && angle <= 0x80) player.setLoopLowPlane(true);   // .CheckAngle
        } else if (angle > 0x80) {
            player.setLoopLowPlane(false);                                     // .HighPath
        }
    }

    /** {@code .SSZ}: no change while rising, then quadrant bounds in the chunk (Main.asm:2607-2637). */
    private static void stardustQuadrants(AbstractPlayableSprite player, int x, int y) {
        if (player.getYSpeed() < 0) return;
        int inY = y & 0xFF;
        if ((x & 0xFF) < 0x80) {
            if (inY < 0x38) {
                player.setLoopLowPlane(true);                  // .SetLowPlane: bset #6
            } else if (inY >= 0x80) {
                player.setLoopLowPlane(false);                 // .SetHighPlane: bclr #6
            }
        } else if (inY < 0x38) {
            player.setLoopLowPlane(false);                     // .CheckIfAbove -> .SetHighPlane
        } else if (inY >= 0x80) {
            player.setLoopLowPlane(true);                      // -> .SetLowPlane
        }
    }
}
