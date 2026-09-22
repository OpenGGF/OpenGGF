package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.SszLaunchControllerObjectInstance;
import com.openggf.game.sonic3k.objects.SszLaunchPieceObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.io.UncheckedIOException;

/** SSZ1_ScreenEvent stages 0/4/8; the launch object owns player/camera motion separately. */
final class SszLaunchWorld {
    private SszLaunchWorld() { }

    static void begin(ObjectServices services, SszZoneRuntimeState state) {
        services.objectManager().createDynamicObject(() -> new SszLaunchControllerObjectInstance(
                new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        state.launch().initialize();
        advanceColumns(services, state);
        for (var candidate : services.playerQuery().playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                ObjectControlState.none().applyTo(sprite);
                sprite.setAir(false);
                sprite.setAnimationId(Sonic3kAnimationIds.WAIT);
                sprite.setAnimationFrameIndex(0);
                sprite.setAnimationFrameCount(0);
                sprite.setControlLocked(true);
                sprite.clearLogicalInputState();
            }
        }
        services.camera().setScrollLocked(true);
        state.launch().shake().writeFlag(0xFF00);
        state.setForegroundRoutine(4);
    }

    static void update(ObjectServices services, SszZoneRuntimeState state) {
        pollTerrain(services, state);
        if (state.foregroundRoutine() == 4) {
            if (state.eventsFg4() == 0) {
                advanceColumns(services, state);
                return;
            }
            installLaunchWorld(services, state);
            return;
        }
        state.launch().advanceDeathEgg(services.camera().getY() & 65535);
    }

    private static void advanceColumns(ObjectServices services, SszZoneRuntimeState state) {
        boolean complete = state.launch().advance(services.camera().getYCopy());
        // The ROM follows _unkFAA4's SST slot. Resolve its present owner, not a retained pointer.
        for (var object : services.objectManager().getActiveObjects()) {
            if (object instanceof SszMechaSonicObjectInstance boss
                    && boss.getSlotIndex() == state.carriedObjectSlot()) {
                int offset = (((boss.getX() - 0x19A0) & 65535) >>> 3) & 0xFFFC;
                if (offset / 4 < 10)
                    boss.carryOnCollapsingColumn(0x660 - (state.launch().offset(offset / 4) >> 16));
                if (complete) boss.deleteOnNextObjectPass();
                break;
            }
        }
        if (complete) state.setEventsFg4(0xFF);
    }

    private static void installLaunchWorld(ObjectServices services, SszZoneRuntimeState state) {
        try {
            var art = S3kRuntimeArtCoordinator.from(services);
            state.launch().setJob(0, art.directQueue().queueStandardKos(services.rom(), 0x1CEFE4, 0xFFFF0180).ordinal());
            state.launch().setJob(1, art.directQueue().queueStandardKos(services.rom(), 0x1CE312, 0xFFFF90B8).ordinal());
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(services.kosinskiModuleQueue(), services);
            int[] sources = {0x1CE832, 0x1541B0};
            int[] tiles = {0x73, 0x348};
            for (int i = 0; i < 2; i++) {
                services.kosinskiModuleQueue().enqueue(services.rom(), sources[i], tiles[i] * 32);
                state.launch().setJob(i + 2, art.moduleQueue().queue(services.rom(), sources[i], tiles[i]).ordinal());
            }
            var level = services.currentLevel();
            var context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                    services.levelManager()::applyMutationEffects);
            services.zoneLayoutMutationPipeline().applyImmediately(c -> {
                int lastThree = level.getLayerWidthBlocks(0) - 3;
                for (int row = 0; row < 2; row++)
                    for (int x = 0; x < 3; x++) c.surface().setBlockInMap(0, lastThree + x, row, 4 + 3 * row + x);
                return MutationEffects.redrawAllTilemaps();
            }, context);
            S3kPaletteWriteSupport.applyLine(services.paletteOwnershipRegistryOrNull(),
                    level, services.graphicsManager(), "ssz-launch",
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, services.rom().readBytes(0x577DA, 32));
            state.launch().advanceDeathEgg(services.camera().getY() & 65535);
            state.setUnkEE98(0xFF00);
            spawnSpiral(services);
            state.setEventsFg4(0xFF00 | state.eventsFg4Low());
            state.setForegroundRoutine(8);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void spawnSpiral(ObjectServices services) {
        var manager = services.objectManager();
        int previous = -1;
        outer: for (int row = 0; row < 10; row++) {
            for (int kind = 0; kind < (row == 9 ? 2 : 4); kind++) {
                int x = new int[]{0x1A38, 0x1A80, 0x1A48, 0x19F8}[kind];
                int y = new int[]{0x5A8, 0x58C, 0x570, 0x554}[kind] - row * 0x70;
                // Supply the kind before construction; the first uses AllocateObject, later
                // entries scan forward from the previous successful slot (CreateNewSprite4).
                final int childKind = kind;
                var child = ObjectConstructionContext.construct(services, () -> new SszLaunchPieceObjectInstance(
                        new ObjectSpawn(x, y, 0, childKind, 0, false, 0)));
                if (previous < 0) manager.addDynamicObject(child);
                else manager.addDynamicObjectAfterSlot(child, previous);
                if (child.isDestroyed() || child.getSlotIndex() < 0) break outer;
                previous = child.getSlotIndex();
            }
        }
    }

    private static void pollTerrain(ObjectServices services, SszZoneRuntimeState state) {
        var art = S3kRuntimeArtCoordinator.from(services);
        for (int i = 0; i < 4; i++) {
            long ordinal = state.launch().job(i);
            if (ordinal < 0) continue;
            var kind = i < 2 ? HardwareWorkKind.KOS_DECOMPRESSION_QUEUE : HardwareWorkKind.KOS_MODULE_QUEUE;
            var handle = services.hardwareTiming().pendingHandle(kind, ordinal).orElseThrow();
            if (i < 2) {
                if (!art.directQueue().isReady(handle)) continue;
                byte[] bytes = art.directQueue().claim(handle);
                int index = i;
                var level = (Sonic3kLevel) services.currentLevel();
                services.zoneLayoutMutationPipeline().applyImmediately(c -> {
                    if (index == 0) level.applyBlockOverlay(bytes, 0x180, false);
                    else level.applyChunkOverlay(bytes, 0xB8, false);
                    return MutationEffects.redrawAllTilemaps();
                }, new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                        services.levelManager()::applyMutationEffects));
            } else {
                if (!art.moduleQueue().isReady(handle)) continue;
                art.moduleQueue().claim(handle);
            }
            state.launch().setJob(i, -1);
        }
    }
}
