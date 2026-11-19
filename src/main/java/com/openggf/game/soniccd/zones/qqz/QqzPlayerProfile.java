package com.openggf.game.soniccd.zones.qqz;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdPlayerStageTables;
import com.openggf.game.soniccd.player.CdSpecialChunks;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.timetravel.CdTimeZone;
import com.openggf.level.LevelManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.CollisionSystemInternalAccess;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Quartz Quadrant's part of the player routine. Every stage file assembles
 * {@code src/r5/player_block_check.asm} as {@code PlayerCheckBlock}, the routine
 * {@code ObjSonic_SpecialCol} calls after the movement mode: when the block under
 * either foot is in the time zone's {@code PlayerBlocks} list the player rides a
 * conveyor. The zone also runs {@code ObjSonic_SpecialChunks} (scddisasm
 * src/Level/_Objects/Sonic/Main.asm:2500-2508) with the stage's roll tunnels.
 *
 * <p>{@code r5_conveyor_reverse} (the conveyor switch's; it negates the step, the wall angle and
 * the push, player_block_check.asm:320-337) is {@link QqzScrollHandler#conveyorReverse()}, which the
 * conveyor switch object drives and which the handler captures for rewind.
 * {@code boss_flags} (the boss's; the check returns while it is set, sub_20665A,
 * player_block_check.asm:11-13) is {@link QqzScrollHandler#bossFlags()}, which the boss drives.
 */
final class QqzPlayerProfile implements CdZonePlayerProfile {
    /** {@code loc_2068AA} (r5/player_block_check.asm:308-316) tests the full {@code time_zone} byte. */
    private static final int PAST_CONVEYOR_STEP = 0x8000;
    private static final int PRESENT_CONVEYOR_STEP = 0x10000;
    private static final int FUTURE_OR_WARP_CONVEYOR_STEP = 0x20000;
    /** {@code move.b #$C0,d1}: the {@code PlayerCheckBlockFront} angle (player_block_check.asm:319-323). */
    private static final int CONVEYOR_WALL_ANGLE = 0xC0;
    /** {@code addq.w #2,d2}: the probe sits 2px under the feet (sub_20665A, player_block_check.asm:15-19). */
    private static final int FOOT_PROBE_DROP = 2;
    /** Layout rows are $80 bytes, foreground first: row {@code (y >> 1) & $780}, column {@code x >> 8 & $7F} (GetBlock, src/common/block_collide.asm:5-16). */
    private static final int LAYOUT_ROW_MASK = 0xF;
    private static final int LAYOUT_COLUMN_MASK = 0x7F;
    /** {@code (n - 1) ror 7}: a chunk is $200 bytes of 16x16 block words (block_collide.asm:207-217). */
    private static final int CHUNK_BYTES = 0x200;
    /** {@code andi.w #$7FF,d0}: block ID and the unused bit 10, no flips or solidity (player_block_check.asm:25, 39). */
    private static final int BLOCK_ID_MASK = 0x7FF;

    private final CdSpecialChunks specialChunks;
    private final int[][] conveyorBlockLists;
    private final CdStageImage image;
    private final int chunksOffset;
    private final int zoneIndex;

    QqzPlayerProfile(CdStagePayload payload, CdStageImage image) {
        int[] ids = CdPlayerStageTables.specialChunks(payload, image);
        specialChunks = ids == null ? null
                : CdSpecialChunks.of(ids, -1, CdSpecialChunks.SpecialLoopRule.AIRBORNE_LOWER_PATH);
        conveyorBlockLists = new int[][] {
                CdPlayerStageTables.playerBlocks(payload, image, 0), // Past
                CdPlayerStageTables.playerBlocks(payload, image, 1), // Present
                CdPlayerStageTables.playerBlocks(payload, image, 2), // Bad Future
                CdPlayerStageTables.playerBlocks(payload, image, 3)  // Good Future
        };
        this.image = image;
        this.zoneIndex = com.openggf.game.soniccd.SonicCdZoneRegistry.zoneIndex(payload);
        // LevelDataIndex +8: the chunk table (CdLevel's loadChunks reads the same pointer).
        chunksOffset = image.offsetOf(image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8));
    }

    @Override
    public CdSpecialChunks specialChunks() {
        return specialChunks;
    }

    /**
     * {@code PlayerCheckBlock} / {@code sub_20665A} (scddisasm/src/r5/player_block_check.asm:3-42): the
     * block 2px under the left foot, then the right foot; the first in the list moves
     * the player one conveyor step and pushes it out of a wall ahead.
     */
    @Override
    public void blockCheck(AbstractPlayableSprite player) {
        if (bossFlags()) return;                             // tst.b boss_flags / bne.s locret_206658 (:12-13)
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) return;
        // PlayerCheckWarpCommitTimeZone (objects/player.asm:598-602) has already updated the live
        // period and warp flag. sub_2066BA (r5/player_block_check.asm:46-57) masks that flag for
        // eligibility and adds good_future only in the Future.
        CdTimeTravel timeTravel = CdTimeTravel.of(player.currentGameModule());
        CdPeriod loadedPeriod = level.payload().stage().period();
        CdTimeZone timeZone = timeTravel != null ? timeTravel.timeZone() : CdTimeZone.of(loadedPeriod);
        boolean goodFuture = timeTravel != null ? timeTravel.goodFuture() : loadedPeriod == CdPeriod.GOOD_FUTURE;
        int list = timeZone.nativeValue() + (timeZone == CdTimeZone.FUTURE && goodFuture ? 1 : 0);
        int[] conveyorBlocks = conveyorBlockLists[list];
        if (conveyorBlocks == null) return;
        int x = player.getCentreX();
        int footY = player.getCentreY() + player.getYRadius() + FOOT_PROBE_DROP;
        if (onConveyor(level, player, conveyorBlocks, x - player.getXRadius(), footY)
                || onConveyor(level, player, conveyorBlocks, x + player.getXRadius(), footY)) {
            // loc_2068AA (r5/player_block_check.asm:308-316): bit 7 takes the 2px step in any period.
            int step = timeTravel != null && timeTravel.warping() ? FUTURE_OR_WARP_CONVEYOR_STEP : switch (timeZone) {
                case PAST -> PAST_CONVEYOR_STEP;
                case PRESENT -> PRESENT_CONVEYOR_STEP;
                case FUTURE -> FUTURE_OR_WARP_CONVEYOR_STEP;
            };
            ride(player, step);
        }
    }

    private boolean onConveyor(CdLevel level, AbstractPlayableSprite player, int[] conveyorBlocks, int x, int y) {
        int block = blockAt(level, player, x & 0xFFFF, y & 0xFFFF) & BLOCK_ID_MASK;
        for (int id : conveyorBlocks) {
            if (id == block) return true;                             // loc_2066E2 / loc_2066F2 (scddisasm/src/r5/player_block_check.asm:62-73)
        }
        return false;
    }

    /**
     * {@code GetBlock} as Quartz Quadrant assembles it (block_collide.asm:3-25, 51-56, 199-222):
     * chunk 0 leaves the table base, the first word of chunk 1 (the chunk table starts on
     * a $10000 boundary, so the low word replaced by the chunk byte is zero); a loop
     * chunk takes the next chunk on the low path, with $29 becoming $51. Rows and columns
     * outside the foreground read as chunk 0, where the player cannot stand.
     */
    private int blockAt(CdLevel level, AbstractPlayableSprite player, int x, int y) {
        int row = (y >> 8) & LAYOUT_ROW_MASK;
        int column = (x >> 8) & LAYOUT_COLUMN_MASK;
        int chunk = 0;
        if (row < level.getMap().getHeight() && column < level.getMap().getWidth()) {
            chunk = level.getMap().getValue(0, column, row) & 0x7F;
            if (chunk != 0 && level.hasLoopFlag(column, row) && player.isLoopLowPlane()) {
                chunk = level.resolveCollisionBlockIndex(chunk, column, row);
            }
        }
        if (chunk == 0) return image.u16(chunksOffset);
        int cell = ((y >> 4) & 0xF) * 0x20 + ((x >> 4) & 0xF) * 2;
        return image.u16(chunksOffset + (chunk - 1) * CHUNK_BYTES + cell);
    }

    /** {@code tst.b boss_flags} (sub_20665A, player_block_check.asm:12): the boss's flag, held by the zone's scroll handler. */
    private boolean bossFlags() {
        var parallax = com.openggf.game.GameServices.parallax();
        return parallax != null && parallax.getHandler(zoneIndex) instanceof QqzScrollHandler handler
                && handler.bossFlags();
    }

    /** {@code tst.b r5_conveyor_reverse}: the switch's flag, held by the zone's scroll handler. */
    private boolean conveyorReverse() {
        var parallax = com.openggf.game.GameServices.parallax();
        return parallax != null && parallax.getHandler(zoneIndex) instanceof QqzScrollHandler handler
                && handler.conveyorReverse();
    }

    /**
     * loc_2068AA to locret_2068F8 (scddisasm/src/r5/player_block_check.asm:308-339). The wall probe is
     * {@code PlayerCheckBlockFront}, which leaves {@code $C0} (reversed {@code $40}) or the wall's
     * angle in both angle registers after the ground collision has written them; the player's
     * tail copies those to its tilt bytes (src/objects/player.asm:312-319), so a conveyor ride
     * keeps the next frame's ledge balance from seeing the empty-floor value 3.
     */
    private void ride(AbstractPlayableSprite player, int step) {
        boolean reverse = conveyorReverse();
        // neg.l d0; neg.b d1 when reversed (:320-323): $C0 becomes $40.
        NativePositionOps.addXPos16_16(player, reverse ? -step : step);   // add.l d0,obj.x(a0)
        CollisionSystem collision = player.currentCollisionSystemOrNull();
        if (collision == null) return;
        int distance = CollisionSystemInternalAccess.projectedWallDistance(collision, player,
                reverse ? -CONVEYOR_WALL_ANGLE & 0xFF : CONVEYOR_WALL_ANGLE);
        if (distance >= 0) return;                                    // tst.w d1; bpl.s
        NativePositionOps.addXPosPreserveSubpixel(player, reverse ? -distance : distance);  // neg.w d1; add.w d1,obj.x(a0)
    }
}
