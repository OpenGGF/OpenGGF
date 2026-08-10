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
    /** Fixed length of the s2.asm:5060-5066 title-card leave loop. */
    private static final int TITLE_CARD_LEAVE_LOOP_FRAMES = 25;

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
        // The secondary PLC enters the queue from loadZoneBlockMaps
        // (docs/s2disasm/s2.asm:20103-20110), which Level: calls at s2.asm:4939 --
        // still inside the title-card sequence. The title-card leave loop
        // (s2.asm:5060-5066) then runs VintID_TitleCard + RunPLC_RAM every frame
        // until TitleCard_Background unloads, before Level_StartGame /
        // Level_MainLoop (s2.asm:5082-5087). That loop is exactly 25 frames long,
        // fixed by the Obj34 leave routines and their RAM slot order
        // (s2.asm:27368-27374): TitleCard_Left runs Obj34_LeftPartOut for 5 frames
        // from titlecard_location $A (s2.asm:5058, 27518-27540: $A -> 6 -> 2 -> 0
        // -> -4 -> delete) and hands TitleCard_Bottom routine $10; Bottom runs
        // Obj34_BottomPartOut for frames 6..16, stepping titlecard_location by 4
        // until it reaches $28 (s2.asm:27542-27551); Background sits earlier in
        // slot order so it first sees routine $12 on frame 17 and runs
        // Obj34_BackgroundOutInit/Out for 9 frames, $F0 stepping by -$20 to -$30
        // (s2.asm:27587-27604), deleting itself on frame 25. A headless load omits
        // that presentation, so replay its PLC service here.
        SkippedPresentationPlcLifecycle.runIterations(
                plcService, PlcLifecyclePhase.LEVEL_TITLE_CARD,
                TITLE_CARD_LEAVE_LOOP_FRAMES);
    }

    /**
     * The 25-frame title-card leave loop (s2.asm:5060-5066) runs after
     * InitPlayers (s2.asm:4945), so it is exactly the omitted presentation
     * window in which the player objects animate and load their DPLCs.
     */
    @Override
    public int skippedPresentationPlayableFrames() {
        return TITLE_CARD_LEAVE_LOOP_FRAMES;
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
