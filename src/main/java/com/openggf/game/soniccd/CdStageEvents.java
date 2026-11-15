package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.camera.CameraMotionInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * {@code RunLevelEvents} (Level/_Events.asm:7-53; {@code StageEvents} in
 * src/common/events.asm, src/r4/events.asm and src/r8/events.asm). The USA build calls it
 * first in {@code LevelScroll}, before {@code ScrollCamX} and {@code ScrollCamY}
 * (Palmtree Panic/Scroll (Act 1 Present).asm:189-192), so it reads the previous frame's
 * camera and this frame's scroll clamps to the boundary it leaves. Each zone's events set
 * the target ({@code destBottomBound}) or write the boundary itself; the tail then moves
 * the live boundary 4 pixels toward the target, 16 on the way down while the camera is
 * within 8 pixels of it and the player is airborne, and sets {@code btmBoundShift}
 * (Level/_Events.asm:19-53). The camera's {@code maxY} is {@code bottomBound}; its target is
 * kept equal so the shared boundary easing, which follows Sonic 1's later order and 2-pixel
 * step, never moves it.
 *
 * <p>Not hosted, because they need state no hosted owner provides yet: the boss arenas
 * ({@code CheckBossStart}/{@code SetBossBounds} and every {@code bossFlags} branch, read as
 * clear) and Palmtree Panic 1's left-boundary lock at $820. Palmtree Panic 1's 3D-ramp
 * {@code camYCenter} is hosted as the camera's Y bias.
 * {@code btmBoundShift} stays set until the scroll's {@code .StopCam} clears it; the camera
 * keeps it for one scroll, which differs only on a frame after the boundary stops.
 */
final class CdStageEvents {
    private CdStageEvents() { }

    /** Giving up from boredom ({@code oAnim} $2B). */
    private static final int ANIM_GIVE_UP = 0x2B;
    /** {@code moveq #4,d1} (Level/_Events.asm:20; src/common/events.asm:16). */
    private static final int SHIFT_STEP = 4;
    /** {@code cmpi.w #$1C16,objPlayerSlot+oX} / {@code #$21C6} (Level/_Events.asm:90-93). */
    private static final int RAMP_CENTRE_FROM_X = 0x1C16;
    private static final int RAMP_CENTRE_TO_X = 0x21C6;
    /** {@code move.w #$88,camYCenter} (Level/_Events.asm:94). */
    private static final int RAMP_CAMERA_Y_CENTRE = 0x88;

    /** One frame's events for {@code payload}, before the camera scroll. */
    static void run(CdStagePayload payload, Camera camera, AbstractPlayableSprite player) {
        if (camera == null || player == null) return;
        if (player.getDead()) {
            // Level_MainLoop skips LevelScroll, and with it these events, while the player's
            // routine is 6 or more, pinning both boundaries to the camera (Level/Main.asm:411-415).
            camera.setMaxY(camera.getY());
            return;
        }
        // Every hosted zone event writes destBottomBound each frame or not at all; where it
        // writes nothing (an act 3 before its boss) the target is still LevelSizeLoad's, which
        // is the boundary itself, so it starts as the boundary.
        int bottom = camera.getMaxY() & 0xFFFF;
        Bounds b = new Bounds(bottom, bottom);
        if (com.openggf.game.soniccd.objects.boss.CdBossObjects.hostsLevelEvents(payload)) {
            // The act 3 branch ran in the boss tier (objects/boss): the boss object writes both words while
            // bossFlags is set (e.g. sub_20B9CA, objects/r1/boss.asm:46-65) and LevEvents_PPZ3 writes the target
            // only while it is clear (Level/_Events.asm:127-139), so the target is the camera's, not this branch's.
            b.dest = camera.getMaxYTarget() & 0xFFFF;
        } else {
            zoneEvents(payload, camera, player, b);
        }
        // The give-up pin is in Level/_Events.asm:14-17 and src/common/events.asm:10-13; the
        // Tidal Tempest and Metallic Madness tails (src/r4/events.asm, src/r8/events.asm) have none.
        CdZone zone = payload.stage().zone();
        int cameraY = camera.getY() & 0xFFFF;
        if (zone != CdZone.TIDAL_TEMPEST && zone != CdZone.METALLIC_MADNESS
                && (player.getAnimationId() & 0xFF) == ANIM_GIVE_UP) {
            b.bottom = cameraY;
            b.dest = cameraY;
        }
        boolean shifting = false;
        if (b.dest != b.bottom) {
            int step = SHIFT_STEP;
            if (b.dest < b.bottom) {
                // Up: from the camera when it is past the target (bls, unsigned).
                step = -SHIFT_STEP;
                if (cameraY > b.dest) b.bottom = cameraY & 0xFFFE;
            } else if (((cameraY + 8) & 0xFFFF) >= b.bottom && player.getAir()) {
                step = SHIFT_STEP * 4;                         // add.w d1,d1 twice
            }
            b.bottom = (b.bottom + step) & 0xFFFF;
            shifting = true;
        }
        camera.setMaxY((short) b.bottom);                      // and its target: no shared easing
        if (shifting) CameraMotionInternalAccess.markBottomBoundaryShifting(camera);
    }

    private static final class Bounds {
        int bottom;
        int dest;

        Bounds(int bottom, int dest) {
            this.bottom = bottom;
            this.dest = dest;
        }
    }

    private static void zoneEvents(CdStagePayload payload, Camera camera, AbstractPlayableSprite player, Bounds b) {
        int act = payload.stage().actNumber();
        int camX = camera.getX() & 0xFFFF;
        switch (payload.stage().zone()) {
            case PALMTREE_PANIC -> {
                if (act == 1 && payload.stage().period() == CdPeriod.PRESENT) {
                    // LevEvents_PPZ1 (_Events.asm:86-95): within the second 3D ramp (player X
                    // $1C16 to $21C5, unsigned bcs/bcc) camYCenter is $88. It is written every
                    // frame after the player's own easing toward $60 (Sonic/Main.asm:1122-1136,
                    // 1607-1613), so the scroll reads $88 and takes its 2-pixel step
                    // (Scroll (Act 1 Present).asm:663).
                    int playerX = player.getCentreX() & 0xFFFF;
                    if (playerX >= RAMP_CENTRE_FROM_X && playerX < RAMP_CENTRE_TO_X) {
                        camera.setYPosBias((short) RAMP_CAMERA_Y_CENTRE);
                    }
                    // _Events.asm:96-116: $710 before the first 3D ramp, $410 from camera X
                    // $840, $310 from $E00.
                    b.dest = camX < 0x840 ? 0x710 : camX < 0xE00 ? 0x410 : 0x310;
                } else {
                    b.dest = 0x310;                            // LevEvents_PPZ2/PPZ3 (_Events.asm:121-136)
                }
            }
            case COLLISION_CHAOS -> b.dest = 0x510;            // LevEvents_CCZ12/CCZ3 (_Events.asm:159-169)
            case TIDAL_TEMPEST -> {
                switch (act) {
                    case 1 -> b.dest = 0x510;                  // R41Events (src/r4/events.asm:59-61)
                    case 2 -> {
                        // LevEvents_TTZ2 (_Events.asm:338-354; R42Events, src/r4/events.asm:65-80):
                        // both words $800 for the wrapping section while the camera is left of
                        // $200, else $710; giving up takes .NoWrap.
                        boolean wrap = (player.getAnimationId() & 0xFF) != ANIM_GIVE_UP && camX < 0x200;
                        b.bottom = wrap ? 0x800 : 0x710;
                        b.dest = b.bottom;
                    }
                    default -> { }                             // LevEvents_TTZ3: boss only (_Events.asm:358-372)
                }
            }
            case QUARTZ_QUADRANT -> b.dest = act == 3
                    ? 0x320                                    // LevEvents_QQZ3, USA (_Events.asm:250-264)
                    : 0x310;                                   // LevEvents_QQZ12 (_Events.asm:244-246)
            case WACKY_WORKBENCH -> b.dest = 0x710;            // LevEvents_WWZ (_Events.asm:179-183)
            case STARDUST_SPEEDWAY -> {
                switch (act) {
                    case 1 -> b.dest = 0x510;                  // LevEvents_SSZ1 (_Events.asm:395-397)
                    case 2 -> b.dest = 0x710;                  // LevEvents_SSZ2 (_Events.asm:401-403)
                    default -> {
                        // LevEvents_SSZ3 (_Events.asm:407-435): $210 until the race, then $120,
                        // written to the boundary at once when within 4 pixels.
                        int x = player.getCentreX();
                        if (x < 0x930 || x >= 0xDC0) {
                            b.dest = 0x210;
                        } else {
                            b.dest = 0x120;
                            if (Math.abs((short) (0x120 - b.bottom)) < 4) b.bottom = 0x120;
                        }
                    }
                }
            }
            // sub_203568 / sub_203570 (src/r8/events.asm:60-70).
            case METALLIC_MADNESS -> b.dest = act == 3 ? 0x310 : 0x710;
        }
    }
}
