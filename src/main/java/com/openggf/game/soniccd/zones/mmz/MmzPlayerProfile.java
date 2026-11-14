package com.openggf.game.soniccd.zones.mmz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.level.LevelManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Metallic Madness's part of the player routine, reading the typed inputs the zone's objects drive
 * ({@link MmzStageSignals}): the shrunk player's radii, the Spin Disc's stick flag, the bit-0 object
 * collision gate, the controlled branch's warp clear and {@code GetLevelBlock}'s layer branch.
 * Act 2's {@code PlayerCheckBlock} (src/r8/player_block_check.asm:3-91) moves the player along the conveyor
 * blocks. Not hosted: the shrunk player's animation set and solid-object box, the touch routine's layer
 * tests (src/r8/player_object_collide.asm:105, 280) and the saves of {@code miniSonic}.
 */
public final class MmzPlayerProfile implements CdZonePlayerProfile {
    /** Layout rows: {@code (y >> 1) & $780}; columns {@code x >> 8 & $7F} (common/block_collide.asm:5-16). */
    private static final int LAYOUT_ROW_MASK = 0xF;
    private static final int LAYOUT_COLUMN_MASK = 0x7F;
    /** A chunk is $200 bytes of 16x16 block words (block_collide.asm:39-49). */
    private static final int CHUNK_BYTES = 0x200;
    /**
     * {@code word_206414}: the conveyor blocks, the same list for every time zone ({@code off_20640C}'s four
     * entries point at it; player_block_check.asm:76-91).
     */
    private static final int[] CONVEYOR_BLOCKS = {0x10E, 0x10F, 0x110, 0x111, 0x112, 0x113, 0x114};
    /** {@code cmpi.w #$114,d4; bne; neg.w d0} (player_block_check.asm:46-49): this block runs the other way. */
    private static final int REVERSED_BLOCK = 0x114;

    private final MmzStageSignals signals = new MmzStageSignals();
    /** Act 2 assembles {@code r8/player_block_check.asm} and runs it ({@code cmpi.b #2,act; bne}); act 3 returns, act 1
     *  has the common {@code rts} (R8 Shared.asm:137-142, player_block_check.asm:3-6). */
    private final boolean conveyorBlocks;
    private final CdStageImage image;
    private final int chunksOffset;

    public MmzPlayerProfile(CdStagePayload payload, CdStageImage image) {
        this.conveyorBlocks = payload != null && image != null && payload.stage().actNumber() == 2;
        this.image = image;
        // LevelDataIndex +8: the chunk table (CdLevel reads the same pointer).
        this.chunksOffset = conveyorBlocks
                ? image.offsetOf(image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8)) : 0;
    }

    public MmzStageSignals signals() {
        return signals;
    }

    /** {@code layer} ({@code stage_layer}), which the Revolve Door flips and the monitors and time posts test. */
    @Override
    public int playerLayerForObjects() {
        return signals.stageLayer();
    }

    @Override
    public Object captureSignals(com.openggf.game.soniccd.level.CdLevel level) {
        return signals.capture();
    }

    @Override
    public void restoreSignals(com.openggf.game.soniccd.level.CdLevel level, Object snapshot) {
        if (snapshot instanceof MmzStageSignals.Snapshot s) signals.restore(s);
    }

    /** {@code tst.b miniSonic} (player.asm:143 and the other readers listed on {@link MmzStageSignals}). */
    @Override
    public boolean miniPlayer(AbstractPlayableSprite player) {
        return signals.shrunk();
    }

    /** {@code move.b respawn_shrunk/warp_shrunk,shrunk_player} (common/respawn_load.asm:34, 82). */
    @Override
    public void restoreMiniPlayer(boolean mini) {
        signals.setShrunk(mini);
    }

    /** Standing $13/9, rolling $E/7 ({@code PlayerInit}, {@code PlayerStartRollSetNormal}). */
    private static final int[] NORMAL_RADII = {9, 0x13, 7, 0x0E};
    /**
     * Shrunk: standing $A/5 and rolling 8/5, so a roll moves the centre by 2, not 5
     * (src/objects/player.asm:143-147, 1409-1415, 1669-1675, 1756-1760, 2168-2174).
     */
    private static final int[] MINI_RADII = {5, 0x0A, 5, 0x08};

    /**
     * Each frame before the player's routine: the radii its later writes use follow
     * {@code shrunk_player}, and the signal's copy of {@code obj.var_38} (the Spin Disc's hold, the player's
     * stick flag: {@code PlayerCheckFall} and the steep-slope falls return while it is set,
     * player.asm:1885-1887 and src/common/player_ground_collide.asm:102, 239, 305, 371) follows the
     * shared stick-to-surface flag the disc set.
     */
    @Override
    public void beforePlayerRoutine(AbstractPlayableSprite player) {
        int[] radii = signals.shrunk() ? MINI_RADII : NORMAL_RADII;
        if (player.getStandYRadius() != radii[1]) {
            com.openggf.sprites.playable.PlayableSpriteInternalAccess.setBodyRadii(player,
                    radii[0], radii[1], radii[2], radii[3]);
        }
        // obj.var_38 is the player's stick flag itself: the Spin Disc writes it (MmzSpinDisc), and GetOffObject
        // (common/solid_object.asm:29) and the jump (objects/player.asm:1750) clear it through the shared flag.
        if (signals.discHold() != 0 && !player.isStickToConvex()) signals.setDiscHold(0);
    }

    /**
     * {@code PlayerObjectCollide} opens with {@code btst #0,obj.var_2c(a0); bne} (no object
     * collision; src/r8/player_object_collide.asm:3-7): the Revolve Door's hold or a
     * controlling object.
     */
    @Override
    public boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        return !signals.revolveHold() && !player.isObjectControlled();
    }

    /**
     * Metallic Madness's controlled branch clears the warp instead of running it:
     * {@code clr.w timeWarpTimer; clr.b timeWarp} (Main.asm:300-307).
     */
    @Override
    public void whileObjectControlled(AbstractPlayableSprite player) {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(player.currentGameModule());
        if (tt != null) tt.clearTimerAndStars();
    }

    /**
     * {@code GetLevelBlock}'s Metallic Madness branch for a player (Collision Floor.asm:599-641):
     * the loop path is never tested; while the stage {@code layer} byte is set (the Revolve Door
     * flips it) six loop chunks read their other-layer chunk.
     */
    @Override
    public int playerLoopChunk(com.openggf.game.soniccd.level.CdLevel level, int loopChunk, boolean onLoopPath) {
        return loopChunk(loopChunk, signals.stageLayer());
    }

    /**
     * Act 2's {@code PlayerCheckBlock} (loc_20635A..locret_2063CE, player_block_check.asm:8-53): the block under the
     * left foot ({@code x - width}, {@code y + height + 2}), else the one under the right foot, on the conveyor list
     * moves the player one pixel: right, left for an X-flipped block (bit $B), and the other way again on block $114.
     */
    @Override
    public void blockCheck(AbstractPlayableSprite player) {
        if (!conveyorBlocks) return;
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) return;
        int y = (player.getCentreY() + player.getYRadius() + 2) & 0xFFFF;
        int word = blockWord(level, player, (player.getCentreX() - player.getXRadius()) & 0xFFFF, y);
        if (!isConveyor(word)) {
            word = blockWord(level, player, (player.getCentreX() + player.getXRadius()) & 0xFFFF, y);
            if (!isConveyor(word)) return;
        }
        int step = (word & 0x800) != 0 ? -1 : 1;                     // btst #$B,d4; beq; neg.w d0
        if ((word & 0x7FF) == REVERSED_BLOCK) step = -step;
        NativePositionOps.addXPosPreserveSubpixel(player, step);     // add.w d0,obj.x(a0)
    }

    /** sub_2063D0: {@code andi.w #$7FF} block ID in {@code word_206414}. */
    private static boolean isConveyor(int word) {
        int block = word & 0x7FF;
        for (int id : CONVEYOR_BLOCKS) if (id == block) return true;
        return false;
    }

    /** {@code GetBlock}: the block word under (x, y), with this zone's layer branch for a loop chunk. */
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
        return image.u16(chunksOffset + (chunk - 1) * CHUNK_BYTES + cellY * 0x20 + cellX * 2);
    }

    /** The branch's arithmetic for a loop chunk and the {@code layer} byte. */
    static int loopChunk(int loopChunk, int layer) {
        if (layer == 0) return loopChunk;                          // tst.b layer; beq .GetBlock
        return switch (loopChunk) {
            case 0x46 -> 0x6A;
            case 0x48 -> 0x6B;
            case 0x4A -> 0x6C;
            case 0x10 -> 0x6D;
            case 0x63 -> 0x6E;
            case 0x43 -> 0x6F;
            default -> loopChunk;
        };
    }
}
