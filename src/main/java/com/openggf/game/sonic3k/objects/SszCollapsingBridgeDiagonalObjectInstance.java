package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code Obj_SSZCollapsingBridgeDiagonal} ({@code $7B}, sonic3k.asm:90222-90402): the sloped
 * sanctuary walkway that breaks into eight pieces. Thirty-five act-1 placements, {@code $00}
 * thirty-one times and {@code $80} four times — the largest single family in the act.
 *
 * <p>Init: {@code ori.b #$44,render_flags} (level coordinates plus the multi-sprite bit),
 * {@code height_pixels $20}, {@code width_pixels $40}, {@code priority $180},
 * {@code make_art_tile(ArtTile_SSZMisc+$20,2,1)} over {@code Map_SSZCollapsingBridge} frame 3,
 * {@code bset #7,status}. {@code mainspr_childsprites} is never set, so the multi-sprite bit draws
 * no sub-sprites — faithful, and worth knowing before anyone tries to explain a missing chain.
 *
 * <p><b>Subtype.</b> As with {@link SszCollapsingBridgeObjectInstance}, only bit 7 is read
 * ({@code tst.b subtype(a0)} / {@code bmi.s loc_44DFC}) and set means the walkway never collapses.
 *
 * <p>The solid is {@code SolidObjectTopSloped2} with {@code d1 = $40} over {@code byte_46658}, one
 * signed byte per two pixels — a 1:2 diagonal running {@code -6} to {@code $19}. The normal
 * collision window is exclusive at twice the half-width, so its samples are 0..63.
 * The direct sampler also permits raw ROM reads beyond that window; that capability
 * is not evidence that normal walkway contact actually over-reads the table.
 *
 * <p>{@code loc_44D98} snapshots {@code status(a0)} <em>before</em> the solid call and then, for
 * each player that was standing, clears {@code Status_InAir} again if they are airborne and not
 * jumping — the walkway re-grounds a player the slope would otherwise drop. {@code loc_44EBA}
 * advances the slope pointer four bytes and shrinks the half-width by eight every sixth frame,
 * steps the object eight pixels away from the player and two pixels up or down so the centre rides
 * the diagonal, and parks at {@code x_pos $7FFF} when the width runs out.
 */
public final class SszCollapsingBridgeDiagonalObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SpawnRewindRecreatable {
    private static final Logger LOGGER =
            Logger.getLogger(SszCollapsingBridgeDiagonalObjectInstance.class.getName());

    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code make_art_tile(ArtTile_SSZMisc+$20,2,1)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code move.b #3,mapping_frame(a0)}. */
    private static final int MAPPING_FRAME = 3;
    /** {@code moveq #$40,d1}. */
    private static final int HALF_WIDTH = 0x40;
    private static final int HEIGHT = 0x20;
    /** {@code byte_46658}, read straight from the ROM so the over-read past its end is faithful. */
    public static final int SLOPE_TABLE_ADDR = 0x046658;
    /** {@code moveq #8-1,d5}. */
    public static final int DEBRIS_COUNT = 8;
    /** {@code moveq #$38,d1}: the first piece's offset toward the player. */
    private static final int FIRST_PIECE_OFFSET = 0x38;
    /** {@code moveq #$10,d2}. */
    private static final int PIECE_STEP = 0x10;
    /** {@code move.w #$405,d3}: alternating mapping frames 5 and 4. */
    private static final int FRAME_WORD = 0x405;
    /** {@code moveq #-8,d6}: the vertical stagger, which flips every other piece. */
    private static final int STAGGER = 8;
    /** {@code moveq #6,d4} then {@code addi.w #6,d4}: delays 6..48. */
    private static final int FIRST_DELAY = 6;
    private static final int DELAY_STEP = 6;
    /** {@code move.w #6,$30(a0)}, {@code subq.w #8,$32(a0)}, {@code addq.l #4,$34(a0)}. */
    private static final int SHRINK_PERIOD = 6;
    private static final int SHRINK_STEP = 8;
    private static final int SLOPE_ADVANCE = 4;
    /** {@code moveq #2,d1}: the vertical step that keeps the centre on the diagonal. */
    private static final int DROP_STEP = 2;
    /** {@code move.w #$7FFF,x_pos(a0)}. */
    private static final int PARKED_X = 0x7FFF;

    private int x;
    private int y;
    private boolean collapsed;
    /** {@code $2E(a0)}: non-zero when the walkway is at or right of the player. */
    private boolean playerOnTheLeft;
    /** {@code $30(a0)}. */
    private int shrinkTimer;
    /** {@code $32(a0)}: the current solid half-width. */
    private int halfWidth = HALF_WIDTH;
    /** {@code $34(a0)}: how far the live slope pointer has advanced past {@code byte_46658}. */
    private int slopeOffset;
    private transient byte[] collisionSlope;
    private transient int collisionSlopeOffset = -1;

    public SszCollapsingBridgeDiagonalObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZCollapsingBridgeDiagonal");
        this.x = spawn.x();
        this.y = spawn.y();
    }

    /** {@code tst.b subtype(a0)} / {@code bmi}: bit 7 makes this walkway permanent. */
    private boolean neverCollapses() {
        return (getSpawn().subtype() & 0x80) != 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!collapsed) {
            if (!neverCollapses() && isPlayerRiding()) {
                collapse(player);
            }
            return;
        }
        // loc_44EBA.
        if (--shrinkTimer > 0) {
            return;
        }
        shrinkTimer = SHRINK_PERIOD;
        slopeOffset += SLOPE_ADVANCE;
        halfWidth -= SHRINK_STEP;
        if (halfWidth == 0) {
            x = PARKED_X;
        }
        // loc_44ED6: eight pixels away from the player, two pixels along the diagonal.
        int step = playerOnTheLeft ? SHRINK_STEP : -SHRINK_STEP;
        int drop = playerOnTheLeft ? DROP_STEP : -DROP_STEP;
        if (isRenderFlipped()) {
            drop = -drop;
        }
        x = (x + step) & 0xFFFF;
        y = (y - drop) & 0xFFFF;
    }

    /** {@code loc_44E02}-{@code loc_44E9C}. */
    private void collapse(PlayableEntity player) {
        collapsed = true;
        shrinkTimer = SHRINK_PERIOD;
        int playerX = player == null ? x : player.getCentreX() & 0xFFFF;
        playerOnTheLeft = x >= playerX;
        int offset = playerOnTheLeft ? -FIRST_PIECE_OFFSET : FIRST_PIECE_OFFSET;
        int step = playerOnTheLeft ? -PIECE_STEP : PIECE_STEP;
        int frames = playerOnTheLeft ? rotateFrameWord(FRAME_WORD) : FRAME_WORD;
        // moveq #-8,d6, negated with the rest when the player is on the left and again on a flip.
        int stagger = playerOnTheLeft ? STAGGER : -STAGGER;
        if (isRenderFlipped()) {
            frames = rotateFrameWord(frames);
            stagger = -stagger;
        }
        // loc_44E3E: tst.l d6 / bmi / addq.l #8,d6 — a positive stagger starts one step further.
        if (stagger >= 0) {
            stagger += STAGGER;
        }
        int pieceX = (x + offset) & 0xFFFF;
        int delay = FIRST_DELAY;
        for (int piece = 0; piece < DEBRIS_COUNT; piece++) {
            final int spawnX = pieceX;
            final int spawnY = (y + stagger) & 0xFFFF;
            final int spawnFrame = frames & 0xFF;
            final int spawnDelay = delay;
            if (spawnChild(() -> SszBridgeDebrisObjectInstance.piece(
                    spawnX, spawnY, spawnFrame, spawnDelay)) == null) {
                break;
            }
            pieceX = (pieceX - step) & 0xFFFF;
            frames = rotateFrameWord(frames);
            delay += DELAY_STEP;
            // btst #0,d5 / bne: the stagger only moves on every other piece, toward zero.
            if ((((DEBRIS_COUNT - 1) - piece) & 1) == 0) {
                stagger += stagger < 0 ? STAGGER : -STAGGER;
            }
        }
    }

    /** {@code ror.w #8,d3}. */
    private static int rotateFrameWord(int word) {
        return ((word >> 8) & 0xFF) | ((word & 0xFF) << 8);
    }

    /** {@code btst #0,render_flags(a0)}. */
    private boolean isRenderFlipped() {
        return (getSpawn().renderFlags() & 1) != 0;
    }

    /**
     * {@code SolidObjCheckSloped2} reads {@code (a2,d5.w)} with {@code a2} the live
     * {@code $34(a0)} pointer, so the sample comes from the ROM at the advanced offset. Ordinary contact
     * bounds restrict the index; direct diagnostic reads do not clamp it.
     */
    @Override
    public Integer sampleSlopeByte(int sampleIndex) {
        if (sampleIndex < 0 || tryServices() == null) {
            return null;
        }
        try {
            var rom = services().rom();
            if (rom == null) {
                return null;
            }
            return (int) (byte) rom.readBytes(
                    SLOPE_TABLE_ADDR + slopeOffset + sampleIndex, 1)[0];
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSZ diagonal slope sample unavailable", e);
            return null;
        }
    }

    @Override public byte[] getSlopeData() {
        // SolidObjectTopSloped2 and continued SolidObjSloped2 both consume the
        // live $34 pointer. The shared solver uses this table to select its
        // sloped path; returning null silently selects a flat 32-pixel platform,
        // even when sampleSlopeByte itself reads the correct ROM bytes.
        if (collisionSlope == null || collisionSlopeOffset != slopeOffset) {
            try {
                collisionSlope = services().rom().readBytes(SLOPE_TABLE_ADDR + slopeOffset, HALF_WIDTH + 1);
                collisionSlopeOffset = slopeOffset;
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException("SSZ diagonal collision slope", failure);
            }
        }
        return collisionSlope;
    }
    @Override public boolean isSlopeFlipped() { return isRenderFlipped(); }
    @Override public int getSlopeBaseline() { return 0; }
    @Override public Integer getDirectTopLandingOverlapLimit() { return 0x11; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    // Obj_SSZCollapsingBridgeDiagonal: art_tile bit15 is separate from SAT queue $180.
    @Override public boolean isHighPriority() { return true; }
    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }
    @Override public int getOnScreenHalfHeight() { return HEIGHT; }

    @Override
    public SolidObjectParams getSolidParams() {
        // loc_44EF6 passes the shrinking $32(a0) as d1 once the collapse starts.
        return SolidObjectParams.of(Math.max(halfWidth, 0), HEIGHT, HEIGHT);
    }

    public boolean collapsedForTest() { return collapsed; }
    public int halfWidthForTest() { return halfWidth; }
    public int slopeOffsetForTest() { return slopeOffset; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, x, y, isRenderFlipped(), false, PALETTE_LINE);
        }
    }
}
