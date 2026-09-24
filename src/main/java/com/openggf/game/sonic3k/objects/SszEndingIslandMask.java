package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** loc_591D6/59208: eight opaque child squares, not an X=0 hardware sprite mask. */
public final class SszEndingIslandMask extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int screenY = 0xF0;
    private int backgroundFraction;

    public SszEndingIslandMask(ObjectSpawn spawn) { super(spawn, "SszEndingIslandMask"); }

    /** loc_58B20: the CPU writes $66666666 through all sixteen pattern tiles. */
    public static void fillTiles(com.openggf.level.objects.ObjectServices services) {
        var level = services.currentLevel();
        services.zoneLayoutMutationPipeline().applyImmediately(context -> {
            var dirty = new java.util.BitSet();
            byte[] bytes = new byte[32];
            java.util.Arrays.fill(bytes, (byte) 0x66);
            for (int tile = 0x7F0; tile < 0x800; tile++) {
                var pattern = new com.openggf.level.Pattern();
                pattern.fromSegaFormat(bytes);
                dirty.or(context.surface().setPattern(tile, pattern).dirtyPatterns());
            }
            // These high tiles may not exist when the level-art sheet is first
            // registered. Rebind its blank placeholders before the GPU refresh.
            var provider = (com.openggf.game.sonic3k.Sonic3kObjectArtProvider)
                    services.levelManager().getGameModule().getObjectArtProvider();
            try {
                var art = new com.openggf.game.sonic3k.Sonic3kObjectArt(level, services.romReader());
                provider.refreshSheetPatterns(Sonic3kObjectArtKeys.SSZ_ENDING_ISLAND_MASK,
                        art.buildLevelArtSheetFromRom(0x59270, 0, 0,
                                com.openggf.game.sonic3k.S3kSpriteDataLoader.MappingFormat.STANDARD, 1));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            return new com.openggf.game.mutation.MutationEffects(dirty, false, false, false, true, false, false);
        }, new com.openggf.game.mutation.LayoutMutationContext(
                com.openggf.game.mutation.LevelMutationSurface.forLevel(level),
                services.levelManager()::applyMutationEffects));
    }

    @Override public void update(int clock, PlayableEntity player) {
        var state = (SszZoneRuntimeState) services().zoneRuntimeState();
        if (state.backgroundRoutine() != 0) {
            int y = state.backgroundCameraY();
            if (y != 0x80) {
                // The ROM subtracts from the camera-copy longword. The shared camera
                // API stores its integer word; this sole fractional writer retains
                // the low word here, captured with the object for forward replay.
                int fixed = (y << 16) | backgroundFraction;
                fixed -= 0x6000;
                state.setBackgroundCameraY(fixed >> 16);
                backgroundFraction = fixed & 0xFFFF;
            }
            screenY = (0x2C0 - state.backgroundCameraY()) & 0xFFFF;
        }
        if (state.foregroundRoutine() == 0x18) ObjectLifetimeOps.deleteNoRespawn(this);
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return false; }
    @Override public int getPriorityBucket() { return 7; } // priority=$380
    @Override public int getOnScreenHalfHeight() { return 0x40; }
    int screenYForTest() { return screenY; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ENDING_ISLAND_MASK);
        if (renderer == null || !renderer.isReady()) return;
        var camera = services().camera();
        // Child positions are VDP screen coordinates (origin=$80), independent
        // of world scrolling. Preserve the native four-column/two-row footprint;
        // any widescreen extension must follow the island's actual framing.
        for (int row = 0; row < 2; row++)
            for (int column = 0; column < 4; column++)
                renderer.drawFrameIndex(0, camera.getX() + 0x22 + column * 0x40,
                        camera.getY() + screenY - 0xC0 + row * 0x40, false, false);
    }
}
