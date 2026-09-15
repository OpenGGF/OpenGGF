package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SozEventState;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;

/** SOZ screen and special events. Mutable native state belongs to the zone runtime. */
public final class Sonic3kSOZEvents extends Sonic3kZoneEvents {
    private final SozAct1Events act1=new SozAct1Events(this);
    private int[] wallDelayTable;
    private byte[] shakeTable;

    private void ensureRomTables() {
        if (wallDelayTable != null) return;
        try {
            byte[] bytes = rom().readBytes(0x56AD2, 34);
            wallDelayTable = new int[17];
            for (int i = 0; i < 17; i++) wallDelayTable[i] = ((bytes[i * 2] & 255) << 8) | (bytes[i * 2 + 1] & 255);
            shakeTable = rom().readBytes(0x4F424, 84);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot load SOZ event tables", failure);
        }
    }

    private SozZoneRuntimeState state() {
        return S3kRuntimeStates.currentSoz(zoneRuntimeRegistry()).orElseThrow();
    }

    @Override public void update(int act, int frameCounter) {
        ensureRomTables();
        var state = state();
        var events = state.events();
        claimReadyResources(events);
        var player = spriteManager().getMainPlayable();
        if (player == null) return;
        int playerX = player.getCentreX() & 0xFFFF;
        int playerY = player.getCentreY() & 0xFFFF;
        if (!events.initialized()) initializeScreen(act, playerX, events);
        camera().setYCopy((short) (camera().getYCopy() + events.screenShakeOffset()));
        if (act == 0 || events.seamlessEntry()) {
            act1.update(act,frameCounter);
            return;
        }
        if (state.consumeSandCorkForegroundFlag() != 0) openAct2ForegroundPassage();
        updateAct2Sand(state, playerX, playerY);
        updateAct2BossBackground(state, playerX, frameCounter);
    }

    private void initializeScreen(int act, int playerX, SozEventState events) {
        events.initialized(true);
        if (act == 0) {
            // SOZ1_BackgroundInit aliases BG row7 to row0, then seals row6 column13.
            zoneLayoutMutationPipeline().queue(context -> {
                var map = levelManager().getCurrentLevel().getMap();
                for (int x = 0; x < map.getWidth(); x++)
                    context.surface().setBlockInMap(1, x, 7, map.getValue(1, x, 0) & 255);
                context.surface().setBlockInMap(1, 13, 6, 0xFD);
                return MutationEffects.redrawAllTilemaps();
            });
        } else {
            // SOZ2_ScreenInit / BackgroundInit. These are load-position gates, not route guesses.
            camera().setVerticalWrapEnabled(true, 0x800);
            events.foregroundRoutine(8);
            events.backgroundRoutine(playerX < 0x2980 ? 0x10 : 0x20);
            clearAct2BackgroundColumns();
            if (playerX >= 0x4E80) openAct2ForegroundPassage();
        }
    }

    void clearAct2BackgroundColumns() {
        zoneLayoutMutationPipeline().queue(context -> {
            // sub_5697E: ten BG chunk references in rows14 and15.
            for (int row : new int[]{14,15}) for (int x=0x17;x<0x21;x++)
                context.surface().setBlockInMap(1,x,row,0);
            return MutationEffects.redrawAllTilemaps();
        });
    }

    /** sub_5622C copies stored layout cells, including the four lower foreground rows. */
    private void openAct2ForegroundPassage() {
        zoneLayoutMutationPipeline().queue(context -> {
            var map = levelManager().getCurrentLevel().getMap();
            for (int x = 0; x < 4; x++)
                context.surface().setBlockInMap(0, 0x95 + x, 7, map.getValue(0, 0xAB + x, 7) & 255);
            for (int row = 10; row < 14; row++) for (int x = 0; x < 9; x++)
                context.surface().setBlockInMap(0, 0x8C + x, row, map.getValue(0, 0xAB + x, row) & 255);
            return MutationEffects.redrawAllTilemaps();
        });
    }

    private void updateAct2Sand(SozZoneRuntimeState state, int playerX, int playerY) {
        var events = state.events();
        int routine = events.backgroundRoutine();
        boolean exitSand = playerX >= 0x2A00 && playerY >= 0x140 && playerY <= 0x180;
        if ((routine == 0x10 || routine == 0x14 || routine == 0x18) && exitSand) {
            events.savedBackgroundX((short) (camera().getXCopy() - 0x1930));
            events.savedBackgroundY((short) (camera().getYCopy() + 0x2E0 + events.sandHeight()));
            events.backgroundRoutine(0x1C);
            // loc_56484 falls through to the two-column redraw immediately.
            events.redrawRemaining(0x1D);
            events.specialRoutine(0);
            events.backgroundCollision(false);
            gameState().setBackgroundCollisionFlag(false);
            state.consumeSandCorkBackgroundFlag();
            return;
        }
        switch (routine) {
            case 0x10 -> {
                if (state.consumeSandCorkBackgroundFlag() != 0) {
                    boolean upperRoom = playerY < 0x400;
                    events.backgroundRoutine(upperRoom ? 0x14 : 0x18);
                    events.sandPosition((upperRoom ? 0x80 : 0x3E0) << 16);
                    events.specialRoutine(0x10);
                }
            }
            case 0x14 -> {
                if (state.consumeSandCorkBackgroundFlag() != 0) {
                    events.backgroundRoutine(0x18);
                    events.specialRoutine(0x10);
                } else if ((events.sandHeight() & 0xFFFF) >= 0x400) events.specialRoutine(0);
            }
            case 0x18 -> {
                if ((events.sandHeight() & 0xFFFF) >= 0xA00) events.specialRoutine(0);
            }
            case 0x1C -> {
                events.redrawRemaining(events.redrawRemaining() - 2);
                if (events.redrawRemaining() < 0) {
                    events.savedBackgroundX(0);
                    events.backgroundRoutine(0x20);
                }
            }
            default -> { }
        }
        // loc_56474 dispatches Go_CheckPlayerRelease for both native player slots.
        if (events.backgroundRoutine() == 0x14 || events.backgroundRoutine() == 0x18) {
            var objects = levelManager().getObjectManager();
            if (objects != null) {
                for (var player : objectServices().playerQuery().playersFor(
                        com.openggf.level.objects.ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                    objects.checkPlayerReleaseFromObjectFloor(player);
                }
            }
        }
    }

    private void updateAct2BossBackground(SozZoneRuntimeState state, int playerX, int frameCounter) {
        var events = state.events();
        if (events.backgroundRoutine() == 0x20 && playerX >= 0x5000
                && (camera().getY() & 0xFFFF) >= 0x500) {
            events.bossX(0x5250);
            events.bossY(0x6D8);
            // loc_56528: copy each source byte before clearing it; rows are native width apart.
            zoneLayoutMutationPipeline().queue(context -> {
                var map = levelManager().getCurrentLevel().getMap();
                for (int row = 0; row < 16; row++) for (int x = 0; x < 4; x++) {
                    int value = map.getValue(1, x, row) & 255;
                    context.surface().setBlockInMap(1, x + 4, row, value);
                    context.surface().setBlockInMap(1, x, row, 0);
                }
                return MutationEffects.redrawAllTilemaps();
            });
            camera().setMinY((short) 0x500);
            camera().setMaxY((short) 0x680);
            camera().setMaxYTarget((short) 0x680);
            camera().setMaxX((short) 0x5140);
            queueCustomResources(events, 0x1B0720, 0x1B0860);
            state.lighting().enterBossLight();
            for (int row = 7; row >= 0; row--) {
                final int index = row;
                if (spawnObject(() -> new com.openggf.game.sonic3k.objects.SozBossWallObjectInstance(
                        new com.openggf.level.objects.ObjectSpawn(0, 0, 0, index, 0, false, 0))) == null) break;
            }
            events.backgroundRoutine(0x24);
            // sub_56706 also executes during arena initialization.
            updateBossWall(state);
        } else if (events.backgroundRoutine() == 0x24 || events.backgroundRoutine() == 0x28) {
            if (events.backgroundRoutine() == 0x24) {
                camera().setMinY(camera().getY());
                if (camera().getY() == camera().getMaxY()) events.backgroundRoutine(0x28);
            }
            state.lighting().holdBossTimers();
            if ((short) state.sandCorkBackgroundFlag() < 0) {
                events.savedBackgroundX((short) (camera().getXCopy() + 0x1240 - events.bossX() - 0x100));
                events.redrawRemaining(15);
                events.backgroundRoutine(0x2C);
                queueCustomResources(events, 0x1AD68E, 0x1AD81E);
                loadPaletteFromPalPointers(0x1B);
            } else updateBossWall(state);
        } else if (events.backgroundRoutine() == 0x2C && events.artJobOrdinal() < 0) {
            events.redrawRemaining(events.redrawRemaining() - 2);
            if (events.redrawRemaining() < 0) {
                events.savedBackgroundX(0);
                events.backgroundRoutine(0x30);
                camera().setMaxX((short) 0x52C0);
                state.lighting().resumeTorch();
            }
        }
        if (events.backgroundRoutine() >= 0x24) updateShake(events, frameCounter);
    }

    private void updateBossWall(SozZoneRuntimeState state) {
        var events = state.events();
        if (spriteManager().getMainPlayable().getDead()) return;
        boolean defeat = state.sandCorkBackgroundFlag() != 0;
        int sounds = events.bossWall().tick(events, defeat, wallDelayTable);
        if ((sounds & 1) != 0) objectServices().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.BOSS_HIT.id);
        if ((sounds & 2) != 0) objectServices().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.COLLAPSE.id);
        if ((sounds & 4) != 0) objectServices().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.BOSS_RECOVERY.id);
        if (defeat) {
            camera().setMaxX((short) 0x5140);
            if (events.bossWall().defeatRetractionComplete()) state.requestSandCorkRelease(false);
        }
    }

    void updateShake(SozEventState events, int levelFrameCounter) {
        int flag = events.screenShakeFlag();
        int offset = 0;
        if (!spriteManager().getMainPlayable().getDead() && flag != 0) {
            if (flag < 0) offset = shakeTable[20 + (levelFrameCounter & 63)] & 255;
            else {
                events.screenShakeFlag(--flag);
                offset = shakeTable[flag];
            }
        }
        events.screenShakeOffset(offset);
    }

    void queueCustomResources(SozEventState events, int blocksAddress, int artAddress) {
        if (events.blockJobOrdinal() >= 0 || events.artJobOrdinal() >= 0)
            throw new IllegalStateException("SOZ replacement requested before previous resources retired");
        try {
            events.blockJobOrdinal(directKosQueue().queueStandardKos(rom(), blocksAddress,
                    com.openggf.game.sonic3k.resources.S3kKosRamDestinations.blockTableOffset(0xEA0)).ordinal());
            events.artJobOrdinal(moduleKosQueue().queue(rom(), artAddress, 0x315).ordinal());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot queue SOZ custom resources", failure);
        }
    }

    private void claimReadyResources(SozEventState events) {
        // Rebind from captured ordinals every pass: no stale facade survives rewind.
        if (events.blockJobOrdinal() >= 0) {
            var handle = hardwareTiming().pendingHandle(
                    com.openggf.game.timing.HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,
                    events.blockJobOrdinal()).orElseThrow();
            if (directKosQueue().isReady(handle)) {
                byte[] blocks = directKosQueue().claim(handle);
                events.blockJobOrdinal(-1);
                zoneLayoutMutationPipeline().queue(context -> {
                    var level = levelManager().getCurrentLevel();
                    for (int offset = 0; offset < blocks.length; offset += 8) {
                        int chunkIndex = 0x1D4 + offset / 8;
                        var chunk = level.getChunk(chunkIndex);
                        int[] values = new int[6];
                        for (int word = 0; word < 4; word++) values[word] =
                                ((blocks[offset + word * 2] & 255) << 8) | (blocks[offset + word * 2 + 1] & 255);
                        values[4] = chunk.getSolidTileIndex();
                        values[5] = chunk.getSolidTileAltIndex();
                        context.surface().restoreChunkState(chunkIndex, values);
                    }
                    return MutationEffects.redrawAllTilemaps();
                });
            }
        }
        if (events.artJobOrdinal() >= 0) {
            var handle = hardwareTiming().pendingHandle(
                    com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE,
                    events.artJobOrdinal()).orElseThrow();
            if (moduleKosQueue().isReady(handle)) {
                byte[] art = moduleKosQueue().claim(handle);
                events.artJobOrdinal(-1);
                zoneLayoutMutationPipeline().queue(context -> {
                    MutationEffects effects = MutationEffects.redrawAllTilemaps();
                    for (int offset = 0; offset < art.length; offset += 32) {
                        var pattern = new com.openggf.level.Pattern();
                        pattern.fromSegaFormat(java.util.Arrays.copyOfRange(art, offset, offset + 32));
                        effects = mergeEffects(effects, context.surface().setPattern(0x315 + offset / 32, pattern));
                    }
                    com.openggf.game.sonic3k.Sonic3kPlcLoader.refreshAffectedRenderers(
                            java.util.List.of(new com.openggf.game.sonic3k.Sonic3kPlcLoader.TileRange(0x315, art.length / 32)),
                            levelManager());
                    return effects;
                });
            }
        }
    }

    private static MutationEffects mergeEffects(MutationEffects first, MutationEffects second) {
        var dirty = first.dirtyPatterns();
        dirty.or(second.dirtyPatterns());
        return new MutationEffects(dirty, first.dirtyRegionProcessingRequired() || second.dirtyRegionProcessingRequired(),
                first.foregroundRedrawRequired() || second.foregroundRedrawRequired(),
                first.allTilemapsRedrawRequired() || second.allTilemapsRedrawRequired(),
                first.patternLookupRefreshRequired() || second.patternLookupRefreshRequired(),
                first.objectResyncRequired() || second.objectResyncRequired(),
                first.ringResyncRequired() || second.ringResyncRequired());
    }

    /** SpecialEvents loc_569BA runs before player physics, with 16.16 sand movement. */
    public void updateSpecialEvents(int act) {
        if (act != 1 || spriteManager().getMainPlayable() == null
                || spriteManager().getMainPlayable().getDead()) return;
        var events = state().events();
        if (events.specialRoutine() == 0x10) {
            events.sandPosition(events.sandPosition() + 0xA000);
            events.backgroundCollision(true);
            gameState().setBackgroundCollisionFlag(true);
        }
    }
}
