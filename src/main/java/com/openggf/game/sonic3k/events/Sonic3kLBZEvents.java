package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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

    /**
     * S3K layout row pointers are interleaved FG/BG word pairs. In the ROM,
     * {@code a3 + 4 * row} addresses an FG row pointer after {@code a3} is set
     * to {@code Level_layout_main}.
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
                    new CopySpec(0x80, 9, 0x42, 9, 10, 14),
                    new CopySpec(0x8A, 9, 0x42, 9, 10, 14),
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
                    new CopySpec(0x94, 12, 0x7A, 12, 6, 6),
                    new CopySpec(0x9A, 12, 0x7A, 12, 6, 6),
                    false)
    };

    @Override
    public void update(int act, int frameCounter) {
        if (act != 0 || !GameServices.hasRuntime()) {
            return;
        }
        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElse(null);
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (state == null || player == null) {
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
        if (!GameServices.hasRuntime()) {
            return;
        }
        S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry())
                .ifPresent(state -> state.setInteriorLayoutMod3Disabled(true));
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
