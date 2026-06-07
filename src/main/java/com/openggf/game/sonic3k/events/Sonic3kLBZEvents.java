package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Launch Base Zone dynamic level events.
 *
 * <p>ROM: {@code LBZ1_ScreenEvent} / {@code LBZ1_CheckLayoutMod}
 * (docs/skdisasm/s3.asm:74713-75083). LBZ1's "interior reveal" spaces are
 * foreground layout copies: entering one of four player rectangles copies
 * chunk-index bytes from hidden staging rows into the visible foreground
 * layout, and leaving the matching exit X range restores the covered variant.
 */
public final class Sonic3kLBZEvents extends Sonic3kZoneEvents {
    private static final int FG_LAYER = 0;
    private static final int ENDING_CLEAR_X = 0x74;
    private static final int ENDING_CLEAR_Y = 0;
    private static final int ENDING_CLEAR_WIDTH = 4;
    private static final int ENDING_CLEAR_HEIGHT = 12;
    private static final int ENDING_COPY_SOURCE_X = 0x98;
    private static final int ENDING_COPY_SOURCE_Y = 0;
    private static final int ENDING_COPY_DEST_X = 0x74;
    private static final int ENDING_COPY_DEST_Y = 9;
    private static final int ENDING_COPY_WIDTH = 4;
    private static final int ENDING_COPY_HEIGHT = 3;
    private static final int ENDING_COLLAPSE_START_X = 0x3B60;
    private static final int ENDING_COLLAPSE_COLUMN_COUNT = 10;
    private static final int ENDING_COLLAPSE_MAX_SCROLL = -0x300;
    private static final int ENDING_COLLAPSE_GLOBAL_ACCEL = 0x100;
    private static final int ENDING_COLLAPSE_RUMBLE_MASK = 0x0F;
    private static final int[] ENDING_COLLAPSE_SCROLL_SPEED = {
            0x1EE, 0x1F2, 0x0C7, 0x1B3, 0x1B7, 0x198, 0x00E, 0x139
    };

    private boolean endingCollapseActive;
    private boolean endingCollapseFinished;
    private int endingCollapseGlobalFixed;
    private int endingCollapsePhase;
    private final int[] endingCollapseFixed = new int[ENDING_COLLAPSE_COLUMN_COUNT];
    private final int[] endingCollapseScroll = new int[ENDING_COLLAPSE_COLUMN_COUNT];

    /**
     * S3K layout row pointers are interleaved FG/BG word pairs. In the ROM,
     * {@code a3 + 4 * row} addresses an FG row pointer after {@code a3} is set
     * to {@code Level_layout_main}, so {@link CopySpec} {@code sourceY}/{@code destY}
     * are absolute FG block rows.
     *
     * <p>The ROM {@code LBZ1_DoModN} routines copy hidden staging rows into the
     * <em>visible</em> foreground: mods 2/3/4 write into {@code (a3)} (FG row 0)
     * and mod 1 into {@code 8(a3)} (FG row 2), regardless of which staging row
     * they read from. Destination rows must therefore stay anchored to the
     * visible door rows (0/2) and never track the source staging row, or the
     * door chunks are never swapped and the door stays solid.
     */
    private static final LayoutMod[] LBZ1_LAYOUT_MODS = {
            new LayoutMod(
                    1,
                    new TriggerRange(0x13E0, 0x16A0, 0x0100, 0x0580),
                    new ExitRange(0x1376, 0x170A),
                    new CopySpec(0x80, 0, 0x26, 2, 8, 9),
                    new CopySpec(0x88, 0, 0x26, 2, 8, 9),
                    true),
            new LayoutMod(
                    2,
                    new TriggerRange(0x2160, 0x2520, 0x0000, 0x0700),
                    new ExitRange(0x20F6, 0x258A),
                    new CopySpec(0x80, 9, 0x42, 0, 10, 14),
                    new CopySpec(0x8A, 9, 0x42, 0, 10, 14),
                    false),
            new LayoutMod(
                    3,
                    new TriggerRange(0x3A60, 0x3BA0, 0x0000, 0x0600),
                    new ExitRange(0x39F6, 0x3C0A),
                    new CopySpec(0x94, 0, 0x74, 0, 4, 12),
                    new CopySpec(0x98, 0, 0x74, 0, 4, 12),
                    false),
            new LayoutMod(
                    4,
                    new TriggerRange(0x3DE0, 0x3FA0, 0x0000, 0x0300),
                    new ExitRange(0x3D76, 0x400A),
                    new CopySpec(0x94, 12, 0x7A, 0, 6, 6),
                    new CopySpec(0x9A, 12, 0x7A, 0, 6, 6),
                    false)
    };

    @Override
    public void update(int act, int frameCounter) {
        if (act != 0 || !hasRuntime()) {
            return;
        }
        updateEndingCollapse(frameCounter);

        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(zoneRuntimeRegistry()).orElse(null);
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (state == null || player == null) {
            return;
        }
        if (endingCollapseActive) {
            return;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        int activeMod = state.getActiveInteriorLayoutMod();
        if (activeMod != 0) {
            LayoutMod mod = modById(activeMod);
            if (mod != null && !mod.exitRange().contains(playerX)) {
                applyLayoutCopy(mod.coveredCopy());
                state.setActiveInteriorLayoutMod(0);
            }
            return;
        }

        for (LayoutMod mod : LBZ1_LAYOUT_MODS) {
            if (mod.id() == 3 && state.isInteriorLayoutMod3Disabled()) {
                continue;
            }
            if (mod.matches(playerX, playerY)) {
                applyLayoutCopy(mod.revealCopy());
                state.setActiveInteriorLayoutMod(mod.id());
                return;
            }
        }
    }

    /**
     * ROM: {@code LBZ1_ModEndingLayout} sets {@code Events_bg+$02}, which
     * prevents layout mod 3 from re-entering after the boss/ending building
     * has been cleared.
     */
    public void disableInteriorLayoutMod3() {
        if (!hasRuntime()) {
            return;
        }
        S3kRuntimeStates.currentLbz(zoneRuntimeRegistry())
                .ifPresent(state -> {
                    state.setInteriorLayoutMod3Disabled(true);
                    if (state.getActiveInteriorLayoutMod() == 3) {
                        state.setActiveInteriorLayoutMod(0);
                    }
                });
    }

    /**
     * Applies the post-collapse LBZ1 boss-area building layout.
     *
     * <p>ROM: {@code LBZ1_ModEndingLayout} clears a 4x12 strip at FG columns
     * {@code $74..$77}, then copies three lower rows from hidden staging columns
     * {@code $98..$9B} into rows 9-11. It also disables layout mod 3 so the
     * interior reveal logic cannot restore the demolished building.
     */
    public void applyEndingLayout() {
        if (!hasRuntime()) {
            return;
        }
        Level level = levelManager().getCurrentLevel();
        if (level == null || level.getMap() == null) {
            return;
        }
        LayoutMutationContext context = new LayoutMutationContext(
                LevelMutationSurface.forLevel(level),
                levelManager()::applyMutationEffects);
        zoneLayoutMutationPipeline().applyImmediately(
                mutationContext -> applyEndingLayout(level.getMap(), mutationContext.surface()),
                context);
        disableInteriorLayoutMod3();
    }

    /**
     * ROM: {@code Events_fg_4 = -1} arms {@code LBZ1_EventVScroll}. The screen
     * event animates ten foreground VScroll columns down to {@code -$300};
     * {@code LBZ1_ModEndingLayout} is called only after every column has
     * finished falling.
     */
    public void startEndingCollapse() {
        if (!hasRuntime() || endingCollapseActive || endingCollapseFinished) {
            return;
        }
        endingCollapseActive = true;
        endingCollapseGlobalFixed = 0;
        endingCollapsePhase = 0;
        Arrays.fill(endingCollapseFixed, 0);
        Arrays.fill(endingCollapseScroll, 0);
    }

    public boolean isEndingCollapseActive() {
        return endingCollapseActive;
    }

    public boolean isEndingCollapseFinished() {
        return endingCollapseFinished;
    }

    public int[] getEndingCollapseScrollForTest() {
        return Arrays.copyOf(endingCollapseScroll, endingCollapseScroll.length);
    }

    public short[] buildEndingCollapseForegroundVScrollOverride(int cameraX) {
        if (!endingCollapseActive) {
            return null;
        }

        short[] override = new short[20];
        int firstScreenColumn = (ENDING_COLLAPSE_START_X >> 4) - Math.floorDiv(cameraX + 15, 16);
        boolean anyVisible = false;
        boolean anyScrolled = false;
        for (int i = 0; i < ENDING_COLLAPSE_COLUMN_COUNT; i++) {
            int screenColumn = firstScreenColumn + i;
            if (screenColumn < 0 || screenColumn >= override.length) {
                continue;
            }
            int scroll = endingCollapseScroll[i];
            override[screenColumn] = (short) scroll;
            anyVisible = true;
            anyScrolled |= scroll != 0;
        }
        return anyVisible && anyScrolled ? override : null;
    }

    private void updateEndingCollapse(int frameCounter) {
        if (!endingCollapseActive) {
            return;
        }

        int globalFixedDelta = endingCollapseGlobalFixed;
        endingCollapseGlobalFixed += ENDING_COLLAPSE_GLOBAL_ACCEL;
        int phaseWord = endingCollapsePhase >> 6;
        endingCollapsePhase++;

        int unfinishedColumns = ENDING_COLLAPSE_COLUMN_COUNT;
        for (int i = 0; i < ENDING_COLLAPSE_COLUMN_COUNT; i++) {
            int speedIndex = ((phaseWord + ((i + 1) * 2)) & 0x0E) >> 1;
            int deltaFixed = (ENDING_COLLAPSE_SCROLL_SPEED[speedIndex] << 4) + globalFixedDelta;
            endingCollapseFixed[i] -= deltaFixed;
            int scroll = endingCollapseFixed[i] >> 16;
            if (scroll <= ENDING_COLLAPSE_MAX_SCROLL) {
                scroll = ENDING_COLLAPSE_MAX_SCROLL;
                endingCollapseFixed[i] = ENDING_COLLAPSE_MAX_SCROLL << 16;
                unfinishedColumns--;
            }
            endingCollapseScroll[i] = scroll;
        }

        camera().setShakeOffsets(0, (frameCounter & 1) == 0 ? 2 : -2);
        if (((frameCounter - 1) & ENDING_COLLAPSE_RUMBLE_MASK) == 0 && unfinishedColumns > 0) {
            audio().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        }
        if (unfinishedColumns == 0) {
            finishEndingCollapse();
        }
    }

    private void finishEndingCollapse() {
        endingCollapseActive = false;
        endingCollapseFinished = true;
        camera().setShakeOffsets(0, 0);
        Arrays.fill(endingCollapseScroll, 0);
        applyEndingLayout();
        audio().playSfx(Sonic3kSfx.CRASH.id);
    }

    private static LayoutMod modById(int id) {
        for (LayoutMod mod : LBZ1_LAYOUT_MODS) {
            if (mod.id() == id) {
                return mod;
            }
        }
        return null;
    }

    private void applyLayoutCopy(CopySpec spec) {
        Level level = levelManager().getCurrentLevel();
        if (level == null || level.getMap() == null) {
            return;
        }
        LayoutMutationContext context = new LayoutMutationContext(
                LevelMutationSurface.forLevel(level),
                levelManager()::applyMutationEffects);
        zoneLayoutMutationPipeline().applyImmediately(
                mutationContext -> copyRect(level.getMap(), mutationContext.surface(), spec),
                context);
    }

    private static MutationEffects copyRect(Map map, LevelMutationSurface surface, CopySpec spec) {
        MutationEffects combined = MutationEffects.NONE;
        for (int row = 0; row < spec.height(); row++) {
            for (int col = 0; col < spec.width(); col++) {
                int blockIndex = map.getValue(FG_LAYER, spec.sourceX() + col, spec.sourceY() + row) & 0xFF;
                combined = mergeEffects(combined, surface.setBlockInMap(
                        FG_LAYER,
                        spec.destX() + col,
                        spec.destY() + row,
                        blockIndex));
            }
        }
        return combined;
    }

    private static MutationEffects applyEndingLayout(Map map, LevelMutationSurface surface) {
        MutationEffects combined = MutationEffects.NONE;
        for (int row = 0; row < ENDING_CLEAR_HEIGHT; row++) {
            for (int col = 0; col < ENDING_CLEAR_WIDTH; col++) {
                combined = mergeEffects(combined, surface.setBlockInMap(
                        FG_LAYER,
                        ENDING_CLEAR_X + col,
                        ENDING_CLEAR_Y + row,
                        0));
            }
        }
        for (int row = 0; row < ENDING_COPY_HEIGHT; row++) {
            for (int col = 0; col < ENDING_COPY_WIDTH; col++) {
                int blockIndex = map.getValue(
                        FG_LAYER,
                        ENDING_COPY_SOURCE_X + col,
                        ENDING_COPY_SOURCE_Y + row) & 0xFF;
                combined = mergeEffects(combined, surface.setBlockInMap(
                        FG_LAYER,
                        ENDING_COPY_DEST_X + col,
                        ENDING_COPY_DEST_Y + row,
                        blockIndex));
            }
        }
        return combined;
    }

    private static MutationEffects mergeEffects(MutationEffects left, MutationEffects right) {
        if (left == null || left.isEmpty()) {
            return right != null ? right : MutationEffects.NONE;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        BitSet dirtyPatterns = (BitSet) left.dirtyPatterns().clone();
        dirtyPatterns.or(right.dirtyPatterns());
        return new MutationEffects(
                dirtyPatterns,
                left.dirtyRegionProcessingRequired() || right.dirtyRegionProcessingRequired(),
                left.foregroundRedrawRequired() || right.foregroundRedrawRequired(),
                left.allTilemapsRedrawRequired() || right.allTilemapsRedrawRequired(),
                left.objectResyncRequired() || right.objectResyncRequired(),
                left.ringResyncRequired() || right.ringResyncRequired());
    }

    private record LayoutMod(int id,
                             TriggerRange triggerRange,
                             ExitRange exitRange,
                             CopySpec revealCopy,
                             CopySpec coveredCopy,
                             boolean hasLowerRightCornerExclusion) {
        boolean matches(int playerX, int playerY) {
            if (!triggerRange.contains(playerX, playerY)) {
                return false;
            }
            return !hasLowerRightCornerExclusion
                    || playerX < 0x1580
                    || playerY < 0x0400;
        }
    }

    private record TriggerRange(int minX, int maxX, int minY, int maxY) {
        boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    private record ExitRange(int minX, int maxX) {
        boolean contains(int x) {
            return x >= minX && x <= maxX;
        }
    }

    private record CopySpec(int sourceX, int sourceY, int destX, int destY, int width, int height) {
    }
}
