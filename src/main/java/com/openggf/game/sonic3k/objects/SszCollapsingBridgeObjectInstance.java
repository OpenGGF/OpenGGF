package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_SSZCollapsingBridge} ({@code $7C}, sonic3k.asm:90120-90220): the flat sanctuary
 * bridge section that breaks into four pieces when it is stood on. Eight act-1 placements,
 * {@code $00} seven times and {@code $80} once.
 *
 * <p>Init: {@code bset #2,render_flags}, {@code height_pixels $10}, {@code width_pixels $20},
 * {@code priority $180}, {@code make_art_tile(ArtTile_SSZMisc+$20,2,1)} over
 * {@code Map_SSZCollapsingBridge}, {@code bset #7,status}. Mapping frame stays 0.
 *
 * <p><b>Subtype.</b> Only bit 7 is ever read ({@code tst.b subtype(a0)} / {@code bmi.s
 * loc_44C96}), and set means <em>never collapses</em> — a permanent top solid. Bits 0-6 are not
 * read at all, which is why the seven {@code $00} placements and the one {@code $80} are the whole
 * story.
 *
 * <p>{@code loc_44C9C} picks the standing player, records on which side of the bridge they are in
 * {@code $2E(a0)} ({@code scc} after {@code cmp.w x_pos(a1),d4}), and lays four
 * {@link SszBridgeDebrisObjectInstance} pieces from that side inward: X starts {@code $18} px
 * toward the player and steps {@code $10} back, the mapping frame alternates through the
 * {@code $102} word, and the hang delays are 6, 12, 18 and 24. Then {@code loc_44D22} shrinks the
 * solid half-width from {@code $20} by 8 every sixth frame, walks the whole object 8 px away from
 * the player each time so the near edge is the one that retreats, and parks it at
 * {@code x_pos $7FFF} once the width reaches zero. It stays solid at the shrinking width
 * throughout.
 */
public final class SszCollapsingBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code make_art_tile(ArtTile_SSZMisc+$20,2,1)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code moveq #$20,d1} / {@code moveq #$10,d2} / {@code moveq #9,d3}. */
    private static final int HALF_WIDTH = 0x20;
    private static final int HEIGHT = 0x10;
    private static final int LANDING = 9;
    /** {@code moveq #4-1,d5}. */
    public static final int DEBRIS_COUNT = 4;
    /** {@code moveq #$18,d1}: the first piece's offset toward the player. */
    private static final int FIRST_PIECE_OFFSET = 0x18;
    /** {@code moveq #$10,d2}: the step between pieces. */
    private static final int PIECE_STEP = 0x10;
    /** {@code move.w #$102,d3}: alternating mapping frames 2 and 1. */
    private static final int FRAME_WORD = 0x102;
    /** {@code moveq #6,d4} then {@code addi.w #6,d4}. */
    private static final int FIRST_DELAY = 6;
    private static final int DELAY_STEP = 6;
    /** {@code move.w #6,$30(a0)} and {@code subq.w #8,$32(a0)}. */
    private static final int SHRINK_PERIOD = 6;
    private static final int SHRINK_STEP = 8;
    /** {@code move.w #$7FFF,x_pos(a0)}. */
    private static final int PARKED_X = 0x7FFF;

    private int x;
    private boolean collapsed;
    /** {@code $2E(a0)}: non-zero when the bridge is at or right of the player. */
    private boolean playerOnTheLeft;
    /** {@code $30(a0)}: frames until the next shrink step. */
    private int shrinkTimer;
    /** {@code $32(a0)}: the current solid half-width. */
    private int halfWidth = HALF_WIDTH;

    public SszCollapsingBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZCollapsingBridge");
        this.x = spawn.x();
    }

    /** {@code tst.b subtype(a0)} / {@code bmi}: bit 7 makes this section permanent. */
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
        // loc_44D22.
        if (--shrinkTimer > 0) {
            return;
        }
        shrinkTimer = SHRINK_PERIOD;
        halfWidth -= SHRINK_STEP;
        if (halfWidth == 0) {
            x = PARKED_X;
        }
        // loc_44D3A: the edge nearest the player is the one that retreats.
        x = (x + (playerOnTheLeft ? SHRINK_STEP : -SHRINK_STEP)) & 0xFFFF;
    }

    /** {@code loc_44C9C}-{@code loc_44D0E}. */
    private void collapse(PlayableEntity player) {
        collapsed = true;
        shrinkTimer = SHRINK_PERIOD;
        int playerX = player == null ? x : player.getCentreX() & 0xFFFF;
        // cmp.w x_pos(a1),d4 / scc $2E(a0): set when the bridge is not left of the player.
        playerOnTheLeft = x >= playerX;
        int offset = playerOnTheLeft ? -FIRST_PIECE_OFFSET : FIRST_PIECE_OFFSET;
        int step = playerOnTheLeft ? -PIECE_STEP : PIECE_STEP;
        int frames = playerOnTheLeft ? rotateFrameWord(FRAME_WORD) : FRAME_WORD;
        if (isRenderFlipped()) {
            frames = rotateFrameWord(frames);
        }
        int pieceX = (x + offset) & 0xFFFF;
        int delay = FIRST_DELAY;
        for (int piece = 0; piece < DEBRIS_COUNT; piece++) {
            final int spawnX = pieceX;
            final int spawnFrame = frames & 0xFF;
            final int spawnDelay = delay;
            if (spawnChild(() -> SszBridgeDebrisObjectInstance.piece(
                    spawnX, getY(), spawnFrame, spawnDelay)) == null) {
                break;
            }
            pieceX = (pieceX - step) & 0xFFFF;
            frames = rotateFrameWord(frames);
            delay += DELAY_STEP;
        }
    }

    /** {@code ror.w #8,d3}: the two mapping frames swap on every piece. */
    private static int rotateFrameWord(int word) {
        return ((word >> 8) & 0xFF) | ((word & 0xFF) << 8);
    }

    /** {@code btst #0,render_flags(a0)}: the layout's X-flip bit. */
    private boolean isRenderFlipped() {
        return (getSpawn().renderFlags() & 1) != 0;
    }

    @Override public int getX() { return x; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }
    @Override public int getOnScreenHalfHeight() { return HEIGHT; }

    @Override
    public SolidObjectParams getSolidParams() {
        // loc_44D48 passes the shrinking $32(a0) as d1 once the collapse starts.
        return SolidObjectParams.of(Math.max(halfWidth, 0), HEIGHT, LANDING);
    }

    public boolean collapsedForTest() { return collapsed; }
    public int halfWidthForTest() { return halfWidth; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, getY(), isRenderFlipped(), false, PALETTE_LINE);
        }
    }
}
