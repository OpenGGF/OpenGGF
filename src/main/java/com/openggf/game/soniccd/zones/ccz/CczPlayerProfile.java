package com.openggf.game.soniccd.zones.ccz;

import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.audio.CdSoundRequests;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.ccz.CczFlipperLaunch;
import com.openggf.game.soniccd.objects.ccz.CczFlipperObject;
import com.openggf.game.soniccd.objects.ccz.CczGlassShatter;
import com.openggf.game.soniccd.player.CdPlayerStageTables;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Collision Chaos's part of the common player (scddisasm pin 1ecc5f3a, USA):
 * <ul>
 *   <li>{@code PlayerCheckFlipper} from {@code PlayerCheckJump} (src/objects/player.asm:1715-1718,
 *   2933-2970): a jump from a pinball flipper takes the flipper's launch instead of the
 *   standard velocity ({@link CczFlipperLaunch});</li>
 *   <li>the zone's {@code PlayerObjectCollide} opens with {@code btst #6,obj.var_2c(a0); bne}
 *   (src/r3/player_object_collide.asm:3-5): a hidden player (the Pocket's bit 6,
 *   {@code AbstractPlayableSprite.setHidden}) has no object collision;</li>
 *   <li>{@code PlayerCheckBlock} (src/r3/player_block_check.asm:3-552): two probes on the side the
 *   velocity points to; a block of the time zone's {@code PlayerBlocks} list bounces the player by
 *   its kind (glass blocks break, round and square bumpers, slopes), ends a hurt routine with 120
 *   invulnerable frames and plays its sound.</li>
 * </ul>
 * The glass break plays the sub-CPU command $92 ({@code SCMD_BREAKSFX}).
 * The zone has no special chunks ({@code ObjSonic_SpecialChunks}' zone gate, scddisasm/src/Level/_Objects/Sonic/Main.asm:2500-2508).
 */
public final class CczPlayerProfile implements CdZonePlayerProfile {
    /** {@code muls.w #-$700}: every bounce's speed (player_block_check.asm:315-322). */
    private static final int BOUNCE = -0x700;
    /** {@code move.w #120,obj.var_30(a0)} after a hurt bounce (player_block_check.asm:233-235). */
    private static final int HURT_BOUNCE_INVULNERABILITY = 120;
    /** Layout rows: GetBlock {@code (y >> 1) & $780}; PlaceBlock {@code & $380} (scddisasm/src/r4/stage_draw_1b.asm:328-330). */
    private static final int LAYOUT_ROW_MASK = 0xF;
    private static final int PLACE_ROW_MASK = 0x7;
    private static final int LAYOUT_COLUMN_MASK = 0x7F;
    private static final int CHUNK_BYTES = 0x200;
    /** {@code PlayerBlocks} entry index to routine (off_206930, player_block_check.asm:279-296). */
    private static final int[] KIND = {0, 0, 4, 4, 8, 0xA, 0xC, 0xC, 0x10, 0x12, 0x12, 0x12, 0x18, 0xA, 0xA, 0x1E};

    private final int[] blocks;
    private final CdStagePayload payload;
    private final CdStageImage image;
    private final int chunksOffset;

    public CczPlayerProfile(CdStagePayload payload, CdStageImage image) {
        this.payload = payload;
        this.image = image;
        CdPeriod period = payload.stage().period();
        int timeZone = CdLevel.nativeTimeZone(period);
        // time_zone & $7F, plus good_future in the future (player_block_check.asm:208-218).
        int list = timeZone == 2 && period == CdPeriod.GOOD_FUTURE ? 3 : timeZone;
        this.blocks = CdPlayerStageTables.playerBlocks(payload, image, list);
        this.chunksOffset = image.offsetOf(image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8));
    }

    @Override
    public boolean flipperLaunch(AbstractPlayableSprite player) {
        LevelManager levels = player.currentLevelManagerIfAvailable();
        var services = levels == null || levels.getObjectManager() == null ? null
                : levels.getObjectManager().getObjectServices();
        return CczFlipperLaunch.launch(player, services);
    }

    @Override
    public boolean standingOnFlipper(AbstractPlayableSprite player) {
        return player.isOnObject() && CczFlipperObject.standingOn(player) != null;
    }

    /**
     * {@code MoveObject} (src/common/object.asm:65-96): {@code move.w #-$100,d1}, negated when
     * the flipper's {@code oFlags} bit 0 (its X flip) is set, added to the X speed it moves by.
     */
    @Override
    public int standingObjectMoveXBias(AbstractPlayableSprite player) {
        if (!player.isOnObject()) return 0;
        CczFlipperObject flipper = CczFlipperObject.standingOn(player);
        if (flipper == null) return 0;
        return flipper.flipperFlippedHorizontally() ? 0x100 : -0x100;
    }

    @Override
    public boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        return !player.isHidden();
    }

    /** {@code PlayerCheckBlock} and {@code sub_2066FC} (scddisasm/src/r3/player_block_check.asm:3-148). */
    @Override
    public void blockCheck(AbstractPlayableSprite player) {
        if (blocks == null || player.getAnimationId() == 0x2B) return;     // cmpi.b #$2B,obj.anim_id
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) return;
        int side = (TrigLookupTable.calcAngle(player.getXSpeed(), player.getYSpeed()) - 0x20) & 0xC0;
        int x = player.getCentreX();
        int y = player.getCentreY();
        int w = (byte) player.getXRadius();
        int h = (byte) player.getYRadius();
        switch (side) {
            case 0x40 -> {                                                  // loc_2067E0: left
                if (!probe(level, levels, player, x - w, y - (h - 6))) probe(level, levels, player, x - w, y + h);
            }
            case 0x80 -> {                                                  // loc_206766: up
                if (!probe(level, levels, player, x - w, y - h)) probe(level, levels, player, x + w, y - h);
            }
            case 0xC0 -> {                                                  // loc_2067A2: right
                if (!probe(level, levels, player, x + w, y - (h - 6))) probe(level, levels, player, x + w, y + h);
            }
            default -> {                                                    // down
                if (!probe(level, levels, player, x - w, y + h)) probe(level, levels, player, x + w, y + h);
            }
        }
    }

    /** {@code sub_20681E} and {@code loc_2068DA} (scddisasm/src/r3/player_block_check.asm:194-276): true when bounced. */
    private boolean probe(CdLevel level, LevelManager levels, AbstractPlayableSprite player, int px, int py) {
        px &= 0xFFFF;
        py &= 0xFFFF;
        int word = blockWord(level, player, px, py);
        int id = word & 0x7FF;
        if (id == 0) return false;
        int index = -1;
        for (int i = 0; i < blocks.length && index < 0; i++) {
            if (blocks[i] == id) index = i;
        }
        if (index < 0) return false;
        if (player.isHurt()) {                                              // cmpi.b #4,obj.routine; subq.b #2
            player.setHurt(false);
            player.setInvulnerableFrames(HURT_BOUNCE_INVULNERABILITY);
        }
        boolean bounced = dispatch(KIND[index], index * 2, level, levels, player, px, py, word);
        if (index >= 2) {                                                   // cmpi.w #3,d1; bcs
            player.currentAudioManager().playSfx(index == 2 || index == 3 ? 0xBE : 0xB5);
        }
        return bounced;
    }

    /** The routine table (off_206930); false when a slope bumper lets the player pass ({@code r3_no_bumper}). */
    private boolean dispatch(int kind, int d1, CdLevel level, LevelManager levels, AbstractPlayableSprite player,
                             int px, int py, int word) {
        boolean hFlip = (word & 0x800) != 0;                                // btst #$B,d4
        int lx = px & 0xF;
        switch (kind) {
            case 0 -> breakGlass(d1, level, levels, player, px, py, hFlip);
            case 4 -> launchFrom(player, (px & 0xFFF0) + 8, (py & 0xFFF0) + 8, true);
            case 0x1E -> launchFrom(player, (px & 0xFFF0) + 8, (py & 0xFFF0) + 8, false);
            case 0xA -> bounceY(player);
            case 0xC -> bounceX(player);
            case 8 -> {
                if (lx < 8 ? hFlip : !hFlip) bounceY(player);
                else launchFrom(player, (px & 0xFFF0) + 8, (py & 0xFFF0) + 8, false);
            }
            case 0x10 -> {
                if (hFlip) {
                    if (lx < 8) bounceX(player); else bounceY(player);
                } else {
                    return slope(lx, player, px, py, hFlip);                // bra.w Routine12 with d1 = x & $F
                }
            }
            case 0x12 -> {
                return slope(d1, player, px, py, hFlip);
            }
            case 0x18 -> {
                if (lx < 8 ? hFlip : !hFlip) bounceX(player); else bounceY(player);
            }
            default -> { }
        }
        return true;
    }

    /** {@code PlayerCheckBlock_2_Routine12} (scddisasm/src/r3/player_block_check.asm:442-460): false is {@code r3_no_bumper}. */
    private boolean slope(int d1, AbstractPlayableSprite player, int px, int py, boolean hFlip) {
        int which = (short) (d1 - 0x12);
        if (which >= 0) {
            int ly = py & 0xF;
            int lx = px & 0xF;
            if (!hFlip) lx = (0xF - lx) & 0xFF;                            // neg.b d3; addi.b #$F,d3
            int height = CdPlayerStageTables.bumperSlope(payload, image, which / 2, lx);
            if (ly < height) return false;                                  // cmp.b; bcc loc_206AFC
        }
        reflect(player, hFlip);
        return true;
    }

    /** loc_206AFC (scddisasm/src/r3/player_block_check.asm:462-480): the speed reflected about the slope. */
    private void reflect(AbstractPlayableSprite player, boolean hFlip) {
        int angle = TrigLookupTable.calcAngle(player.getXSpeed(), player.getYSpeed());
        angle = (-((angle - 0x80) & 0xFF) - 0x20) & 0xFF;                   // addi.b #-$80; neg.b; subi.b #$20
        if (hFlip) angle = (angle + 0x40) & 0xFF;
        setSpeeds(player, angle, false);
    }

    /** {@code PlayerCheckBlock_2_RoutineA} / {@code RoutineC}: $700 against the current direction. */
    private static void bounceY(AbstractPlayableSprite player) {
        player.setYSpeed((short) (player.getYSpeed() < 0 ? 0x700 : -0x700));
        airborne(player);
    }

    private static void bounceX(AbstractPlayableSprite player) {
        player.setXSpeed((short) (player.getXSpeed() < 0 ? 0x700 : -0x700));
        airborne(player);
    }

    /** Away from point ({@code tx}, {@code ty}) at $700, or half that (Routine4, Routine1E). */
    private static void launchFrom(AbstractPlayableSprite player, int tx, int ty, boolean half) {
        int angle = TrigLookupTable.calcAngle((short) (tx - player.getCentreX()), (short) (ty - player.getCentreY()));
        setSpeeds(player, angle, half);
    }

    private static void setSpeeds(AbstractPlayableSprite player, int angle, boolean half) {
        int shift = half ? 9 : 8;                                           // asr.l #8 (,asr.l #1)
        player.setXSpeed((short) ((TrigLookupTable.cosHex(angle) * BOUNCE) >> shift));
        player.setYSpeed((short) ((TrigLookupTable.sinHex(angle) * BOUNCE) >> shift));
        airborne(player);
    }

    /** loc_206A3A: air set, roll jump, push and the jump flag cleared. */
    private static void airborne(AbstractPlayableSprite player) {
        player.setAir(true);
        player.setRollingJump(false);
        player.setPushing(false);
        player.setJumping(false);
    }

    /**
     * {@code PlayerCheckBlock_2_Routine0} (scddisasm/src/r3/player_block_check.asm:298-347): a glass block. The
     * player bounces away from the block corner, the glass shatter $29 appears there and the four
     * blocks of the 32x32 square are cleared in their chunk ({@code PlaceBlock} with block 0); then the sub-CPU
     * command $92 (loc_2069CC, :336-348).
     */
    private void breakGlass(int d1, CdLevel level, LevelManager levels, AbstractPlayableSprite player,
                            int px, int py, boolean hFlip) {
        int ty = (py & 0xFFF0) + ((d1 & 0xFF) == 0 ? 0x10 : 0);
        int tx = (px & 0xFFF0) + (hFlip ? 0 : 0x10);
        launchFrom(player, tx, ty, false);
        if (levels.getObjectManager() != null) {
            levels.getObjectManager().addDynamicObject(
                    new CczGlassShatter(new ObjectSpawn(tx & 0xFFFF, ty & 0xFFFF, 0x29, 0, 0, false, 0)));
        }
        placeEmptyBlocks(level, levels, new int[][] {{tx, ty}, {tx - 0x10, ty}, {tx - 0x10, ty - 0x10},
                {tx, ty - 0x10}});
        // PlaceBlockAtPos loads the VDP row delta into the object loop's counter: move.l #$800000,d7
        // (Level/Block Draw.asm:286-293) and never restores it, so when the player's routine returns to
        // UpdateObjects the dbf d7 (common/object.asm:5-23) finds d7.w = 0 and the frame's object pass ends
        // after the player's slot: no other object runs on the frame the glass breaks (native RAM, R33C-fight
        // row 1070: d7 = $00800000 on return, only slot 0 executed). FixBugs = 0: the fixed routine would keep
        // d7 and the pass would go on.
        com.openggf.level.objects.ObjectManagerInternalAccess.overrideRemainingObjectLoopSlotsAfterPlayerSlot(
                levels.getObjectManager(), 0);
        if (levels.getObjectManager() != null) {
            CdSoundRequests.glassBreak(levels.getObjectManager().getObjectServices());
        }
    }

    /** {@code PlaceBlock} with block word 0, through the layout mutation pipeline (rewind-captured). */
    private static void placeEmptyBlocks(CdLevel level, LevelManager levels, int[][] points) {
        var pipeline = com.openggf.game.GameServices.zoneLayoutMutationPipeline();
        LevelMutationSurface surface = LevelMutationSurface.forLevel(level);
        LayoutMutationContext context = new LayoutMutationContext(surface, levels::applyMutationEffects);
        pipeline.applyImmediately(mutation -> {
            MutationEffects effects = MutationEffects.NONE;
            for (int[] point : points) {
                int x = point[0] & 0xFFFF;
                int y = point[1] & 0xFFFF;
                int row = (y >> 8) & PLACE_ROW_MASK;
                int column = (x >> 8) & LAYOUT_COLUMN_MASK;
                if (row >= level.getMap().getHeight() || column >= level.getMap().getWidth()) continue;
                int chunk = level.getMap().getValue(0, column, row) & 0x7F;
                if (chunk == 0 || chunk >= level.getBlockCount()) continue;     // beq.s locret
                int[] state = level.getBlock(chunk).saveState();
                state[((y >> 4) & 0xF) * 16 + ((x >> 4) & 0xF)] = 0;
                effects = mutation.surface().restoreBlockState(chunk, state);
            }
            return effects;
        }, context);
    }

    /**
     * {@code GetBlock}: the block word under (x, y), with the player's loop rule. The stage file
     * supplies the word; a cell {@code PlaceBlock} has cleared reads 0.
     */
    private int blockWord(CdLevel level, AbstractPlayableSprite player, int x, int y) {
        int row = (y >> 8) & LAYOUT_ROW_MASK;
        int column = (x >> 8) & LAYOUT_COLUMN_MASK;
        int chunk = 0;
        if (row < level.getMap().getHeight() && column < level.getMap().getWidth()) {
            int raw = level.getMap().getValue(0, column, row) & 0xFF;
            chunk = raw == 0 ? 0 : level.playerCollisionBlockIndex(raw, column, row, player.isLoopLowPlane());
        }
        if (chunk == 0) return image.u16(chunksOffset);
        int cellX = (x >> 4) & 0xF;
        int cellY = (y >> 4) & 0xF;
        if (chunk < level.getBlockCount() && level.getBlock(chunk).getChunkDesc(cellX, cellY).get() == 0) return 0;
        return image.u16(chunksOffset + (chunk - 1) * CHUNK_BYTES + cellY * 0x20 + cellX * 2);
    }
}
