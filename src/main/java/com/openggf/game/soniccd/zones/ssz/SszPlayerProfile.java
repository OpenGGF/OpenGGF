package com.openggf.game.soniccd.zones.ssz;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.ssz.ObjSparks;
import com.openggf.game.soniccd.objects.ssz.SszStageLayer;
import com.openggf.game.soniccd.player.CdPlayerStageTables;
import com.openggf.game.soniccd.player.CdSpecialChunks;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Stardust Speedway's part of the common player (scddisasm pin 1ecc5f3a, USA):
 * <ul>
 *   <li>{@code GetLevelBlock}'s Stardust Speedway loop branch (src/Level/Collision Floor.asm:557-597,
 *   the r-dialect's src/common/block_collide.asm:112-135): loop chunks 4 and 6 take the next chunk
 *   on the player's loop path; chunks $28, $3C, $37, $2F and $16 take the next chunk while the
 *   stage {@code layer} byte the path-swap object toggles is set ({@link SszStageLayer}); other
 *   loop chunks read as themselves;</li>
 *   <li>{@code PlayerCheckBlock} (src/r7/player_block_check.asm:3-102): every fourth level frame, a
 *   player moving at $A00 or faster over a spark block of the time zone's list throws sparks
 *   (object $09) at its feet, flipped when moving left;</li>
 *   <li>{@code ObjSonic_SpecialChunks} with the stage's quadrant rule.</li>
 * </ul>
 */
public final class SszPlayerProfile implements CdZonePlayerProfile {
    /** {@code cmpi.w #$A00,d0; bcs} (player_block_check.asm:11-12). */
    private static final int SPARK_SPEED = 0xA00;
    /** {@code addq.w #8,d2} (player_block_check.asm:15). */
    private static final int PROBE_DROP = 8;
    /** {@code andi.w #$7FF,d0} (player_block_check.asm:18). */
    private static final int BLOCK_ID_MASK = 0x7FF;
    /** Layout rows: {@code (y >> 1) & $780}; columns {@code x >> 8 & $7F} (block_collide.asm:5-16). */
    private static final int LAYOUT_ROW_MASK = 0xF;
    private static final int LAYOUT_COLUMN_MASK = 0x7F;
    /** A chunk is $200 bytes of 16x16 block words (block_collide.asm:207-217). */
    private static final int CHUNK_BYTES = 0x200;
    /** The sparks object's ID ({@code move.b #9,obj.id(a1)}, player_block_check.asm:94). */
    private static final int SPARKS_ID = 0x09;

    private final CdSpecialChunks chunks;
    private final int[] sparkBlocks;
    private final CdStageImage image;
    private final int chunksOffset;

    public SszPlayerProfile(CdStagePayload payload, CdStageImage image) {
        this.chunks = CdSpecialChunks.forStage(payload, image, -1, CdSpecialChunks.SpecialLoopRule.STARDUST_QUADRANTS);
        CdPeriod period = payload.stage().period();
        int timeZone = CdLevel.nativeTimeZone(period);
        // time_zone & $7F, plus good_future in the future (player_block_check.asm:19-25).
        int list = timeZone == 2 && period == CdPeriod.GOOD_FUTURE ? 3 : timeZone;
        this.sparkBlocks = CdPlayerStageTables.sparkBlocks(payload, image, list);
        this.image = image;
        // LevelDataIndex +8: the chunk table (CdLevel reads the same pointer).
        this.chunksOffset = image.offsetOf(image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8));
    }

    @Override
    public CdSpecialChunks specialChunks() {
        return chunks;
    }

    /** The Stardust Speedway loop branch (Collision Floor.asm:557-597). */
    /** Rewind: the stage's {@code layer} byte ({@link SszStageLayer}). */
    @Override
    public Object captureSignals(CdLevel level) {
        return SszStageLayer.of(level).value();
    }

    @Override
    public void restoreSignals(CdLevel level, Object snapshot) {
        if (snapshot instanceof Integer layer) SszStageLayer.of(level).restore(layer);
    }

    @Override
    public int playerLoopChunk(CdLevel level, int loopChunk, boolean onLoopPath) {
        return loopChunk(loopChunk, onLoopPath, SszStageLayer.of(level).isSet());
    }

    /** The branch's arithmetic for a loop chunk, the player's loop path and the {@code layer} byte. */
    static int loopChunk(int loopChunk, boolean onLoopPath, boolean layer) {
        if (loopChunk == 4 || loopChunk == 6) {
            return onLoopPath ? loopChunk + 1 : loopChunk;         // .SwapChunkIfLow
        }
        if (!layer) return loopChunk;                              // tst.b layer; beq .GetBlock
        return switch (loopChunk) {
            case 0x28, 0x3C, 0x37, 0x2F, 0x16 -> loopChunk + 1;     // .SwapChunk: addq.b #1,d1
            default -> loopChunk;
        };
    }

    /** {@code PlayerCheckBlock} (player_block_check.asm:3-102). */
    @Override
    public void blockCheck(AbstractPlayableSprite player) {
        if (sparkBlocks == null) return;
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) return;
        if ((levels.getFrameCounter() & 3) != 0) return;            // move.b stage_frames+1,d0; andi.b #3
        if (Math.abs((int) player.getXSpeed()) < SPARK_SPEED) return;
        int x = player.getCentreX() & 0xFFFF;
        int y = (player.getCentreY() + PROBE_DROP) & 0xFFFF;
        int block = blockAt(level, player, x, y) & BLOCK_ID_MASK;
        for (int id : sparkBlocks) {
            if (id == block) {
                spawnSparks(player, levels);
                return;
            }
        }
    }

    /** loc_2065BA: sparks $09 at the player's feet, flipped when moving left. */
    private void spawnSparks(AbstractPlayableSprite player, LevelManager levels) {
        if (levels.getObjectManager() == null) return;
        int flip = player.getXSpeed() < 0 ? 1 : 0;                  // tst.w obj.x_speed(a0); bpl; bset #0
        int y = (player.getCentreY() + player.getYRadius()) & 0xFFFF;
        levels.getObjectManager().addDynamicObject(new ObjSparks(
                new ObjectSpawn(player.getCentreX() & 0xFFFF, y, SPARKS_ID, 0, flip, false, 0)));
    }

    /**
     * {@code GetBlock} for the player: chunk 0 reads the table's first word; a loop chunk takes
     * this zone's loop branch; rows and columns past the foreground read chunk 0.
     */
    private int blockAt(CdLevel level, AbstractPlayableSprite player, int x, int y) {
        int row = (y >> 8) & LAYOUT_ROW_MASK;
        int column = (x >> 8) & LAYOUT_COLUMN_MASK;
        int chunk = 0;
        if (row < level.getMap().getHeight() && column < level.getMap().getWidth()) {
            chunk = level.getMap().getValue(0, column, row) & 0x7F;
            if (chunk != 0 && level.hasLoopFlag(column, row)) {
                chunk = playerLoopChunk(level, chunk, player.isLoopLowPlane());
            }
        }
        if (chunk == 0) return image.u16(chunksOffset);
        int cell = ((y >> 4) & 0xF) * 0x20 + ((x >> 4) & 0xF) * 2;
        return image.u16(chunksOffset + (chunk - 1) * CHUNK_BYTES + cell);
    }
}
