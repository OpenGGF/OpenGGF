package com.openggf.game.sonic2;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.resources.Sonic2RuntimePlcPublisher;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.SkippedPresentationPlcLifecycle;
import com.openggf.data.Rom;

import java.util.List;
import java.io.IOException;

/**
 * Sonic 2 level initialization profile.
 * <p>
 * Aligned to the S2 {@code Level:} routine at {@code s2.asm:4753} (57 steps
 * across phases A-J). The teardown steps undo the state set up by that routine.
 * <p>
 * S2-specific characteristics:
 * <ul>
 *   <li>Single PLC queue ({@code LoadPLC}/{@code RunPLC_RAM})</li>
 *   <li>Dual-path collision model ({@code LoadCollisionIndexes} → PRIMARY + SECONDARY)</li>
 *   <li>Zone-specific setup: CPZ pylon ({@code ObjID_CPZPylon}), OOZ oil surface</li>
 *   <li>Player spawn BEFORE game state init (phases G→H)</li>
 *   <li>{@code Level_started_flag} set AFTER title card exit (phase J, step 56)</li>
 * </ul>
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      Design doc: Sonic 2 Level Init Profile (57 steps)</a>
 */
public class Sonic2LevelInitProfile extends AbstractLevelInitProfile {
    private final Sonic2LevelEventManager levelEventManager;
    private final Sonic2PlayerArtModeAuthority playerArtModeAuthority;

    public Sonic2LevelInitProfile(Sonic2LevelEventManager levelEventManager) {
        this(levelEventManager, () -> Sonic2PlayerArtModeAuthority.onePlayer(
                levelEventManager.getPlayerCharacter()).initialLifePlc());
    }

    public Sonic2LevelInitProfile(Sonic2LevelEventManager levelEventManager,
                                  Sonic2PlayerArtModeAuthority playerArtModeAuthority) {
        this.levelEventManager = levelEventManager;
        this.playerArtModeAuthority = playerArtModeAuthority;
    }

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        List<InitStep> steps = buildCoreSteps(ctx);
        steps.add(3, new InitStep("QueueInitialPlcs",
                "S2 Level: ClearPLC, level-header primary LoadPLC, LoadPLC Std2",
                () -> queueInitialPlcs(ctx)));
        if (ctx.isIncludePostLoadAssembly()) {
            steps.addAll(postLoadAssemblySteps(ctx));
        }
        return List.copyOf(steps);
    }

    private void queueInitialPlcs(LevelLoadContext ctx) {
        if (ctx.getLevel() == null) {
            return;
        }
        Sonic2PlcService plcService = GameServices.module().getGameService(Sonic2PlcService.class);
        if (plcService == null) {
            return;
        }
        try {
            int zone = ctx.getLevel().getZoneIndex();
            int offset = Sonic2Constants.LEVEL_DATA_DIR + zone * Sonic2Constants.LEVEL_DATA_DIR_ENTRY_SIZE;
            int primary = GameServices.rom().getRom().readByte(offset) & 0xFF;
            // Retail 1P bypasses this load unless Player_mode == 2. OpenGGF
            // currently has no separate two-player graphics flag owner, so the
            // represented Tails-alone path selects the native Tails life cue.
            java.util.OptionalInt lifePlc = playerArtModeAuthority.initialLifePlc();
            java.util.List<Sonic2PlcService.Operation> operations = new java.util.ArrayList<>();
            operations.add(Sonic2PlcService.clearOperation());
            if (primary != 0) operations.add(Sonic2PlcService.appendOperation(primary));
            operations.add(Sonic2PlcService.appendOperation(Sonic2Constants.PLC_STD2));
            lifePlc.ifPresent(id -> operations.add(Sonic2PlcService.appendOperation(id)));
            Sonic2PlcService.Operation[] transaction = operations.toArray(Sonic2PlcService.Operation[]::new);
            if (GameServices.module().getObjectArtProvider() instanceof Sonic2ObjectArtProvider artProvider
                    && GameServices.levelOrNull() != null
                    && GameServices.levelOrNull().getObjectRenderManager() != null) {
                Sonic2RuntimePlcPublisher.transact(artProvider, plcService,
                        GameServices.levelOrNull()::refreshObjectArtPatterns, transaction);
            } else {
                plcService.transact(transaction);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to queue S2 initial PLCs", failure);
        }
    }

    @Override
    public void completeInitialPresentationPlcs() {
        Sonic2PlcService plcService =
                GameServices.module().getGameService(Sonic2PlcService.class);
        if (plcService != null
                && GameServices.levelOrNull() != null
                && GameServices.levelOrNull().getCurrentLevel() != null) {
            try {
                completeInitialPresentationPlcs(
                        GameServices.rom().getRom(),
                        plcService,
                        GameServices.levelOrNull().getCurrentLevel().getZoneIndex());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Failed to access S2 ROM for initial presentation PLCs",
                        failure);
            }
        }
    }

    static void completeInitialPresentationPlcs(
            Rom rom, Sonic2PlcService plcService, int zoneIndex) {
        SkippedPresentationPlcLifecycle.drain(
                plcService, PlcLifecyclePhase.LEVEL_TITLE_CARD,
                plcService::isBusy);
        try {
            int secondary = Sonic2PlcLoader.getZonePlcIds(rom, zoneIndex)[1];
            if (secondary != 0) {
                plcService.append(secondary);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to append S2 post-title secondary PLC", failure);
        }
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS2LevelEvents",
            "Undoes S2 zone event handlers (HTZ earthquake, boss arenas, CPZ/ARZ/CNZ events)",
            levelEventManager::resetState);
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS2LevelEvents",
            "Undoes S2 zone event handlers and object-level static state for test isolation",
            () -> {
                levelEventManager.resetState();
                ButtonVineTriggerManager.reset();
            });
    }
}
