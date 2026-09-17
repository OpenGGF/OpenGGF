package com.openggf.game.sonic3k.render;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteMaskReplayRole;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePieceRenderer;

import java.io.IOException;

/**
 * Lava Reef's special rock sprites: hardware sprites the ROM appends to the sprite table itself,
 * with no object slot behind them.
 *
 * <p>{@code Draw_LRZ_Special_Rock_Sprites} (sonic3k.asm:39556-39647) keeps a front and a back
 * pointer into an X-sorted placement list of six-byte {@code (sprite index, x, y)} records.
 * Routine 0 ({@code loc_1CADE}) picks the act's list and scans both pointers from its head; routine
 * 2 ({@code loc_1CB20}) walks each pointer forward and backward from where it was, so the cost is
 * proportional to how far the camera moved. The window key is {@code Camera_X_pos - 8}, forced to 1
 * when the camera is at or left of 8 ({@code subq.w #8,d4 / bhi / moveq #1,d4}), and the back key is
 * that plus {@code $150}.
 *
 * <p>{@code sub_1CB68} (39656-39694) emits the records between the two pointers. The vertical test
 * is the unsigned {@code 0 <= y - Camera_Y_pos + 8 < 240}; there is no exhaustion check, only
 * {@code subq.w #1,d7} per rock, so a crowded screen can overrun the ROM's sprite budget. Each
 * record indexes {@code LRZ_Rock_SpriteData} (39699), eight bytes of
 * {@code (y offset, size, art tile, x offset)} in hardware-sprite form.
 *
 * <p>{@code Render_Sprites_NextLevel} (sonic3k.asm:36386-36390) calls it once, while the sprite
 * table pointer is still on priority level 0 and only for {@code Current_zone} 9, so the rocks sit
 * behind everything already in bucket 0 and in front of every later bucket.
 *
 * <p>Presentation choice for wider viewports: the ROM's {@code $150} back key is 16 pixels wider
 * than the 320-pixel screen, so a wide viewport would cut rocks off at the right edge. The captured
 * window stays exactly the ROM's; the extra columns a wide viewport shows are resolved at draw time
 * from the same sorted list, and the vertical window grows the same way for a taller viewport.
 */
public final class LrzRockSpriteRenderer {

    /** {@code lea (LRZ1_Rock_Placement).l,a1} / {@code LRZ2_Rock_Placement} (sonic3k.lst). */
    private static final int LRZ1_ROCK_PLACEMENT_ADDR = 0x0CAD00;
    private static final int LRZ2_ROCK_PLACEMENT_ADDR = 0x0CB81A;
    /** {@code lea LRZ_Rock_SpriteData(pc),a1}: 30 eight-byte entries at {@code $1CBBE}. */
    private static final int LRZ_ROCK_SPRITE_DATA_ADDR = 0x01CBBE;
    private static final int ROCK_SPRITE_ENTRY_COUNT = 30;
    private static final int ROCK_SPRITE_ENTRY_BYTES = 8;
    private static final int PLACEMENT_RECORD_BYTES = 6;
    /** A placement list is terminated by {@code $FFFF} in its X word. */
    private static final int PLACEMENT_TERMINATOR_X = 0xFFFF;
    private static final int MAX_PLACEMENT_BYTES = 0x1000;

    /** {@code subq.w #8,d4} then {@code addi.w #$150,d4} (loc_1CAF4, loc_1CB20). */
    private static final int WINDOW_FRONT_MARGIN = 8;
    private static final int WINDOW_WIDTH = 0x150;
    /** {@code move.w #240,d5} and the {@code addq.w #8,d1} that precedes the compare. */
    private static final int VERTICAL_WINDOW = 240;
    private static final int VERTICAL_WINDOW_MARGIN = 8;
    /** The screen the ROM's two window constants were written for. */
    private static final int ROM_VIEWPORT_WIDTH = 320;
    private static final int ROM_VIEWPORT_HEIGHT = 224;

    private final int[] placementSpriteIndex;
    private final int[] placementX;
    private final int[] placementY;
    private final int[] entryYOffset;
    private final int[] entrySize;
    private final int[] entryArtTile;
    private final int[] entryXOffset;

    private LrzRockSpriteRenderer(int[] spriteIndex, int[] x, int[] y,
                                  int[] entryYOffset, int[] entrySize,
                                  int[] entryArtTile, int[] entryXOffset) {
        this.placementSpriteIndex = spriteIndex;
        this.placementX = x;
        this.placementY = y;
        this.entryYOffset = entryYOffset;
        this.entrySize = entrySize;
        this.entryArtTile = entryArtTile;
        this.entryXOffset = entryXOffset;
    }

    /** {@code tst.b (Current_act).w / beq loc_1CAF4}: act 1 takes the first list, anything else the second. */
    public static LrzRockSpriteRenderer load(Rom rom, int actIndex) throws IOException {
        int listAddress = actIndex == 0 ? LRZ1_ROCK_PLACEMENT_ADDR : LRZ2_ROCK_PLACEMENT_ADDR;
        byte[] raw = rom.readBytes(listAddress, MAX_PLACEMENT_BYTES);

        int count = 0;
        while ((count + 1) * PLACEMENT_RECORD_BYTES <= raw.length
                && word(raw, count * PLACEMENT_RECORD_BYTES + 2) != PLACEMENT_TERMINATOR_X) {
            count++;
        }
        // The terminator is kept as the last entry so both scans stop on it exactly as the ROM's
        // unsigned compare against $FFFF does. It is never inside the drawn half-open range.
        int[] spriteIndex = new int[count + 1];
        int[] x = new int[count + 1];
        int[] y = new int[count + 1];
        for (int record = 0; record < count; record++) {
            int offset = record * PLACEMENT_RECORD_BYTES;
            spriteIndex[record] = word(raw, offset);
            x[record] = word(raw, offset + 2);
            y[record] = word(raw, offset + 4);
        }
        spriteIndex[count] = PLACEMENT_TERMINATOR_X;
        x[count] = PLACEMENT_TERMINATOR_X;
        y[count] = PLACEMENT_TERMINATOR_X;

        byte[] attributes = rom.readBytes(LRZ_ROCK_SPRITE_DATA_ADDR,
                ROCK_SPRITE_ENTRY_COUNT * ROCK_SPRITE_ENTRY_BYTES);
        int[] entryYOffset = new int[ROCK_SPRITE_ENTRY_COUNT];
        int[] entrySize = new int[ROCK_SPRITE_ENTRY_COUNT];
        int[] entryArtTile = new int[ROCK_SPRITE_ENTRY_COUNT];
        int[] entryXOffset = new int[ROCK_SPRITE_ENTRY_COUNT];
        for (int entry = 0; entry < ROCK_SPRITE_ENTRY_COUNT; entry++) {
            int offset = entry * ROCK_SPRITE_ENTRY_BYTES;
            entryYOffset[entry] = (short) word(attributes, offset);
            // move.w (a2)+,d6 / move.b d6,(a6): only the low byte reaches the size field.
            entrySize[entry] = word(attributes, offset + 2) & 0xFF;
            entryArtTile[entry] = word(attributes, offset + 4);
            entryXOffset[entry] = (short) word(attributes, offset + 6);
        }

        return new LrzRockSpriteRenderer(spriteIndex, x, y,
                entryYOffset, entrySize, entryArtTile, entryXOffset);
    }

    /** Placement records excluding the terminator. */
    public int placementCount() {
        return placementX.length - 1;
    }

    public int placementX(int record) {
        return placementX[record];
    }

    /**
     * {@code Draw_LRZ_Special_Rock_Sprites}. Routine 0 scans from the list head and then leaves
     * {@code LRZ_rocks_routine} at 2, after which each frame only walks the two pointers.
     */
    public void advanceWindow(LrzZoneRuntimeState state, int cameraX) {
        int frontKey = windowFrontKey(cameraX);
        int backKey = (frontKey + WINDOW_WIDTH) & 0xFFFF;

        if (state.rocksRoutine() == 0) {
            state.setRocksRoutine(2);
            int front = lowerBoundFrom(0, frontKey);
            state.setRocksWindow(front, lowerBoundFrom(front, backKey));
            return;
        }
        int front = lowerBoundFrom(state.rocksFrontIndex(), frontKey);
        state.setRocksWindow(front, lowerBoundFrom(state.rocksBackIndex(), backKey));
    }

    /** {@code move.w (Camera_X_pos).w,d4 / subq.w #8,d4 / bhi / moveq #1,d4}. */
    static int windowFrontKey(int cameraX) {
        int key = (cameraX - WINDOW_FRONT_MARGIN) & 0xFFFF;
        return ((cameraX & 0xFFFF) > WINDOW_FRONT_MARGIN) ? key : 1;
    }

    /**
     * The ROM's two scans reduced to their result: the first record whose X is not below
     * {@code key}, reached by walking forward and then backward from {@code start}. The list is
     * X-sorted and ends in {@code $FFFF}, so both walks terminate.
     */
    private int lowerBoundFrom(int start, int key) {
        int index = Math.max(0, Math.min(start, placementX.length - 1));
        while (index < placementX.length - 1 && key > placementX[index]) {
            index++;
        }
        while (index > 0 && key <= placementX[index - 1]) {
            index--;
        }
        return index;
    }

    /**
     * {@code sub_1CB68}. {@code viewportWidth}/{@code viewportHeight} only widen the ROM window for
     * a viewport larger than the 320x224 it was written for; at 320x224 this is the ROM's exact set.
     */
    public void draw(GraphicsManager graphics, LrzZoneRuntimeState state,
                     int cameraX, int cameraY, int viewportWidth, int viewportHeight) {
        forEachVisibleRock(state, cameraX, cameraY, viewportWidth, viewportHeight,
                record -> drawRock(graphics, placementSpriteIndex[record],
                        placementX[record], placementY[record]));
    }

    /** One placement record the ROM would emit this frame, in sprite-table order. */
    @FunctionalInterface
    public interface RockRecordVisitor {
        void visitRock(int record);
    }

    /**
     * The records {@code sub_1CB68} walks and the vertical test each one has to pass. Split out of
     * {@link #draw} so the selection can be asserted without a graphics device.
     */
    public void forEachVisibleRock(LrzZoneRuntimeState state, int cameraX, int cameraY,
                                   int viewportWidth, int viewportHeight, RockRecordVisitor visitor) {
        int front = state.rocksFrontIndex();
        int back = state.rocksBackIndex();
        int extraWidth = Math.max(0, viewportWidth - ROM_VIEWPORT_WIDTH);
        if (extraWidth > 0) {
            back = lowerBoundFrom(back, (windowFrontKey(cameraX) + WINDOW_WIDTH + extraWidth) & 0xFFFF);
        }
        if (front >= back) {
            return;
        }
        int verticalWindow = VERTICAL_WINDOW + Math.max(0, viewportHeight - ROM_VIEWPORT_HEIGHT);

        for (int record = front; record < back; record++) {
            int relativeY = (placementY[record] - cameraY + VERTICAL_WINDOW_MARGIN) & 0xFFFF;
            if (relativeY >= verticalWindow) {
                continue;
            }
            visitor.visitRock(record);
        }
    }

    public int placementY(int record) {
        return placementY[record];
    }

    public int placementSpriteIndex(int record) {
        return placementSpriteIndex[record];
    }

    /** {@code LRZ_Rock_SpriteData} entry words, for tests that decode the same bytes from ROM. */
    public int entryArtTile(int spriteIndex) {
        return entryArtTile[spriteIndex];
    }

    public int entrySize(int spriteIndex) {
        return entrySize[spriteIndex];
    }

    public int entryXOffset(int spriteIndex) {
        return entryXOffset[spriteIndex];
    }

    public int entryYOffset(int spriteIndex) {
        return entryYOffset[spriteIndex];
    }

    /**
     * {@code loc_1CADE}'s full scan from the list head, independent of the incremental walk. A
     * routine-2 frame must land on the same pair.
     */
    public int[] fullScanWindow(int cameraX) {
        int frontKey = windowFrontKey(cameraX);
        int front = lowerBoundFrom(0, frontKey);
        return new int[] {front, lowerBoundFrom(front, (frontKey + WINDOW_WIDTH) & 0xFFFF)};
    }

    private void drawRock(GraphicsManager graphics, int spriteIndex, int worldX, int worldY) {
        if (spriteIndex < 0 || spriteIndex >= ROCK_SPRITE_ENTRY_COUNT) {
            return;
        }
        int artTile = entryArtTile[spriteIndex];
        int size = entrySize[spriteIndex];
        int widthTiles = ((size >> 2) & 3) + 1;
        int heightTiles = (size & 3) + 1;
        int patternIndex = artTile & 0x7FF;
        boolean hFlip = (artTile & 0x0800) != 0;
        boolean vFlip = (artTile & 0x1000) != 0;
        int palette = (artTile >> 13) & 3;
        boolean priority = (artTile & 0x8000) != 0;

        SpritePieceRenderer.PreparedPiece piece = new SpritePieceRenderer.PreparedPiece(
                (short) (worldX + entryXOffset[spriteIndex]),
                (short) (worldY + entryYOffset[spriteIndex]),
                widthTiles,
                heightTiles,
                patternIndex,
                patternIndex,
                palette,
                hFlip,
                vFlip,
                priority,
                graphics.getCurrentSpriteHighPriority(),
                SpriteMaskReplayRole.NORMAL,
                0,
                widthTiles,
                0,
                heightTiles,
                "s3k.lrz.rock");

        if (graphics.isSpriteSatCollectionActive()) {
            graphics.submitSpriteSatPiece(piece);
            return;
        }
        PatternDesc desc = new PatternDesc();
        SpritePieceRenderer.renderPreparedPiece(piece,
                (tileIndex, tileHFlip, tileVFlip, tilePalette, drawX, drawY) -> {
                    int descIndex = tileIndex & 0x7FF;
                    if (priority) {
                        descIndex |= 0x8000;
                    }
                    if (tileHFlip) {
                        descIndex |= 0x0800;
                    }
                    if (tileVFlip) {
                        descIndex |= 0x1000;
                    }
                    descIndex |= (tilePalette & 3) << 13;
                    desc.set(descIndex);
                    graphics.renderPatternWithId(tileIndex, desc, drawX, drawY);
                });
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
