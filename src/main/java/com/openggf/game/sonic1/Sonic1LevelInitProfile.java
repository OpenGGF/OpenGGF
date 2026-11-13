package com.openggf.game.sonic1;

import static com.openggf.game.sonic1.Sonic1RomProfile.resolve;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.game.sonic1.objects.Sonic1StomperDoorObjectInstance;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.SkippedPresentationPlcLifecycle;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;

import java.util.List;
import java.io.IOException;

/**
 * Sonic 1 level initialization profile.
 * <p>
 * Aligned to the S1 {@code Level:} routine at {@code sonic.asm:2956} (44 steps
 * across phases A-L). The teardown steps undo the state set up by that routine.
 * <p>
 * S1-specific characteristics:
 * <ul>
 *   <li>Direct Nemesis decompression for title card art (no PLC queue)</li>
 *   <li>Unified collision model (single index, no dual-path switching)</li>
 *   <li>Single-phase data load ({@code LevelDataLoad} — all geometry in one call)</li>
 *   <li>Water only in LZ/SBZ3 ({@code LZWaterFeatures})</li>
 *   <li>Object placement BEFORE game state clear (phases J→K)</li>
 *   <li>Unique 4-frame VBla delay before fade-in (phase L, step 41)</li>
 *   <li>No sidekick (Tails) — step 19 (SpawnSidekick) omitted</li>
 * </ul>
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      Design doc: Sonic 1 Level Init Profile (44 steps)</a>
 */
public class Sonic1LevelInitProfile extends AbstractLevelInitProfile
        implements com.openggf.game.internal.LevelMotionPresentation, com.openggf.game.internal.BlockingLevelEntryFade,
        com.openggf.game.internal.NativeFreshLevelLoadProfile, com.openggf.game.internal.NativeTitlelessLevelLoad,
        com.openggf.game.internal.NativeEndingDemoLoadProfile {
    private final Sonic1LevelEventManager levelEventManager;
    private final Sonic1SwitchManager switchManager;
    private final Sonic1ConveyorState conveyorState;

    public Sonic1LevelInitProfile(Sonic1LevelEventManager levelEventManager,
                                  Sonic1SwitchManager switchManager,
                                  Sonic1ConveyorState conveyorState) {
        this.levelEventManager = levelEventManager;
        this.switchManager = switchManager;
        this.conveyorState = conveyorState;
    }

    private Sonic1EndingLoad endingLoad;
    private Sonic1CreditsDemoLoad creditsDemoLoad;
    private com.openggf.game.sonic1.credits.Sonic1EndingProvider demoCaller;
    private boolean nativeModeSwitchCaller;

    @Override
    public com.openggf.game.internal.NativeLevelLoadCallerScope beginModeSwitchLoad() {
        if (nativeModeSwitchCaller) throw new IllegalStateException("nested native mode-switch load scope");
        nativeModeSwitchCaller = true;
        return new com.openggf.game.internal.NativeLevelLoadCallerScope() {
            private boolean closed;
            @Override public void close() {
                if (!closed) { nativeModeSwitchCaller = false; closed = true; }
            }
        };
    }

    @Override
    public com.openggf.game.internal.NativeLevelLoadCallerScope beginEndingDemoLoad(com.openggf.game.EndingProvider owner) {
        if (!(owner instanceof com.openggf.game.sonic1.credits.Sonic1EndingProvider provider))
            throw new IllegalArgumentException("S1 demo load requires its actual ending owner");
        if (demoCaller != null) throw new IllegalStateException("nested ending demo load scope");
        demoCaller = provider;
        return new com.openggf.game.internal.NativeLevelLoadCallerScope() {
            private boolean closed;
            @Override public void close() {
                if (!closed) { demoCaller = null; closed = true; }
            }
        };
    }

    @Override
    public void completeDemoPresentationPlcs() {
        // Level_ClrCardArt (sonic.asm:2979-2989) appends these after the actual
        // palette waits; it neither drains the queue nor runs another fade.
        if (!com.openggf.game.resources.NativeLevelPlcPolicy.allowsCurrent()) return;
        var plc = GameServices.module().getGameService(Sonic1PlcService.class);
        if (plc == null) return;
        int zone = GameServices.level().getCurrentLevel().getZoneIndex();
        Runnable submit = () -> {
            try {
                plc.transact(Sonic1PlcService.appendOperation(2), Sonic1PlcService.appendOperation(0x15 + zone));
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        };
        // The final palette loop's RunPLC precedes Level_ClrCardArt. Do not
        // let its newly appended head borrow that outgoing preparation tail.
        var lifecycle = GameServices.plcFrameLifecycleOrNull();
        if (lifecycle == null) submit.run();
        else lifecycle.afterConsumedIteration(submit);
    }

    @Override
    public com.openggf.game.internal.NativeLevelLoadPlan freshLevelLoadPlan(LevelLoadContext context) {
        var manager = GameServices.level();
        // The production caller selects actual ending/demo work before timing
        // admission. With no recorded stream, the same phases resume immediately.
        if ((!com.openggf.level.NativeLevelLoadAccess.hasPhaseAdmission(manager)
                && !nativeModeSwitchCaller && demoCaller == null)
                || !context.isIncludePostLoadAssembly() || GameServices.plcFrameLifecycleOrNull() == null
                || context.getLoadMode() != com.openggf.game.LevelLoadMode.FULL
                || GameServices.module().getZoneRegistry().modZoneRuntimeContribution(context.getLevelIndex()) != null)
            return null;
        try {
            var rom = manager.getWorldSession().getDataSource().rom().orElseThrow();
            var selected = new Sonic1(rom).selectEndingLoad(context.getLevelIndex());
            if (selected != null) {
                endingLoad = new Sonic1EndingLoad(context, selected, levelEventManager, () -> endingLoad = null);
                return endingLoad.plan(levelLoadSteps(context));
            }
            var provider = demoCaller;
            if (provider == null) return null;
            var credits = provider.getCreditsManager();
            if (credits == null || credits.getState() != com.openggf.game.sonic1.credits.Sonic1CreditsManager.State.DEMO_LOADING)
                return null;
            var inputs = Sonic1CreditsDemoLoad.inputs(com.openggf.data.RomByteReader.fromRom(rom), credits.getCreditsNum());
            creditsDemoLoad = new Sonic1CreditsDemoLoad(context, inputs, provider, () -> creditsDemoLoad = null);
            return creditsDemoLoad.plan(levelLoadSteps(context));
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
    @Override public void serviceLoadZeroRoutineVInt() {
        var objects = GameServices.level().getObjectManager();
        if (objects != null) objects.advanceVblaCounter();
        else com.openggf.game.session.EngineTiming.vIntRunCounter().serviceRepresentedVBlank();
    }
    @Override public boolean switchesAtLoopTail(int zone, int act) {
        // BossFinal_Eggman_Escape.ending (85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:558-569)
        // writes id_Ending; Level_MainLoop returns to
        // MainGameLoop and enters GM_Ending before the next WaitForVBlank.
        return zone == Sonic1ZoneConstants.ZONE_ENDING;
    }
    @Override public boolean creditsSwitchesAtLoopTail() { return true; }
    @Override public boolean titlelessLoadSelected() { return endingLoad != null || creditsDemoLoad != null; }
    @Override public boolean readyForEntryVBlank() { return endingLoad != null && endingLoad.readyForEntryVBlank(); }
    @Override public void entryVBlankServiced() { endingLoad.entryVBlankServiced(); }
    @Override public boolean ownsEntryReveal() { return endingLoad != null; }
    @Override public void cancelPendingLevelLoadWork() {
        if (endingLoad != null) endingLoad.cancel();
        endingLoad = null;
        creditsDemoLoad = null;
    }

    @Override
    public int freshMainPlayablePreludeFrames() {
        // GM_Level runs ObjPosLoad + ExecuteObjects once after creating the
        // fresh Sonic slot, before clearing v_frame_counter and entering
        // Level_MainLoop. That pass initializes Sonic's native control,
        // floor/status, animation, and mapping state.
        return 1;
    }

    @Override
    public int preLevelFadeOutFrames() {
        // GM_Level runs ClearPLC then PaletteFadeOut before it decompresses
        // the title-card art and queues the level PLCs (sonic.asm:2710-2737).
        // PaletteFadeOut is an unconditional 22-frame WaitForVBlank loop
        // (move.w #22-1,d4 — docs/s1disasm/_inc/Palette Fading.asm:134-149)
        // whose RunPLC calls only ever see the just-cleared queue, so the
        // returning level's drain begins 22 rows after the game-mode handoff.
        return 22;
    }

    @Override
    public int preLevelMainLoopDelayFrames() {
        // Level_Delay runs 4 WaitForVBlank rows (move.w #4-1,d1 —
        // docs/s1disasm/sonic.asm:2957-2963) and the PalFadeIn_Alt call that
        // follows it (docs/s1disasm/sonic.asm:2966) runs 22 more
        // (move.w #22-1,d4 — docs/s1disasm/_inc/Palette Fading.asm:32-51),
        // with no further wait between the fade and Level_MainLoop. The
        // Level_LoadObj ExecuteObjects pass that stages Sonic's tiles
        // (docs/s1disasm/sonic.asm:2895-2897) sits immediately before the
        // delay loop, so the V-int that performs the transfer is the tail's
        // first row: the level's first main-loop row minus 26.
        return 4 + 22;
    }

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        List<InitStep> steps = buildCoreSteps(ctx);
        // GM_Level clears v_misc_variables before resource setup
        // (sonic.asm:2737-2740). v_random lives inside that block
        // (_Variables.asm:143), so deaths, restarts, and normal act loads all
        // begin with a zero RNG seed. Keep this S1-owned: S2/S3K have their
        // own RAM-clear and RNG lifetime rules.
        steps.add(1, new InitStep("ResetRng",
                "S1: clear v_random with v_misc_variables",
                () -> {
                    var rng = GameServices.rngOrNull();
                    if (rng != null) {
                        rng.setSeed(0L);
                    }
                }));
        steps.add(4, new InitStep("QueueInitialPlcs",
                "S1 Level: ClearPLC, level-header primary AddPLC, AddPLC Main2",
                () -> queueInitialPlcs(ctx)));
        // Post-load assembly: 6 steps (no SpawnSidekick — S1 has no Tails)
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(initCameraStep());
            steps.add(new InitStep("InitBackgroundScroll", "S1: BgScrollSpeed after LevSz_InitCameraPositions",
                    () -> {
                        var camera = GameServices.camera();
                        var parallax = GameServices.parallax();
                        com.openggf.level.ParallaxCheckpointAccess.initialize(parallax,
                                GameServices.level().getCurrentZone(), GameServices.level().getCurrentAct(),
                                camera.getX(), camera.getY(), ctx.hasCheckpoint()
                                        ? com.openggf.game.CheckpointBackgroundScrollAccess.saved(ctx) : null);
                    }));
            steps.add(initLevelEventsStep());
            if (!isPreviewCapture(ctx)) {
                steps.add(requestTitleCardStep(ctx));
            }
        }
        return List.copyOf(steps);
    }

    private void queueInitialPlcs(LevelLoadContext ctx) {
        // GM_Ending uses synchronous QuickPLC Ending, not GM_Level's Main2 queue.
        if (ctx.getLevel() == null || ctx.getLevel().getZoneIndex() == Sonic1Constants.ZONE_ENDZ
                || !com.openggf.game.resources.NativeLevelPlcPolicy.allows(ctx)) {
            return;
        }
        var credits = demoCaller == null ? null : demoCaller.getCreditsManager();
        // GM_Level (sonic.asm:2714-2737) skips primary/Main2 for f_demo<0.
        // They already drained while the credits page was visible.
        if (credits != null && credits.getState() == com.openggf.game.sonic1.credits.Sonic1CreditsManager.State.DEMO_LOADING) return;
        Sonic1PlcService plcService = GameServices.module().getGameService(Sonic1PlcService.class);
        if (plcService == null) {
            return;
        }
        try {
            int header = resolve(GameServices.rom().getRom(), Sonic1Constants.LEVEL_HEADERS_ADDR) + ctx.getLevel().getZoneIndex() * 16;
            int primary = (GameServices.rom().getRom().read32BitAddr(header) >>> 24) & 0xFF;
            plcService.transact(primary == 0
                    ? new Sonic1PlcService.Operation[] {Sonic1PlcService.clear(), Sonic1PlcService.appendOperation(1)}
                    : new Sonic1PlcService.Operation[] {Sonic1PlcService.clear(), Sonic1PlcService.appendOperation(primary), Sonic1PlcService.appendOperation(1)});
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to queue S1 initial PLCs", failure);
        }
    }

    @Override
    public void completeInitialPresentationPlcs() {
        if (!com.openggf.game.resources.NativeLevelPlcPolicy.allowsCurrent()) return;
        Sonic1PlcService plcService =
                GameServices.module().getGameService(Sonic1PlcService.class);
        var levelManager = GameServices.levelOrNull();
        if (plcService == null
                || levelManager == null
                || levelManager.getCurrentLevel() == null) {
            return;
        }
        try {
            completeInitialPresentationPlcs(
                    GameServices.rom().getRom(),
                    plcService,
                    levelManager.getCurrentLevel().getZoneIndex());
            // Level_StartGame bumps the fixed title-card elements out of their
            // move-in routine and enters Level_MainLoop with them still alive
            // (docs/s1disasm/sonic.asm:2969-2995). Their post-release tail
            // re-queues the explosion/animal art 69 ordinary level frames later
            // (docs/s1disasm/_incObj/34 Title Cards.asm:122-168), so arm it here
            // rather than from the card renderer, which a headless run omits.
            levelEventManager.armTitleCardArtReloadAtLevelStart(
                        levelManager.getCurrentZone(), levelManager.getCurrentAct(),
                        levelManager.getCurrentLevel().getZoneIndex());
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to access S1 ROM for initial presentation PLCs",
                    failure);
        }
    }

    static void completeInitialPresentationPlcs(
            com.openggf.data.Rom rom, Sonic1PlcService plcService, int zoneIndex) {
        SkippedPresentationPlcLifecycle.drain(
                plcService, PlcLifecyclePhase.LEVEL_TITLE_CARD,
                plcService::isBusy);
        try {
            int header = resolve(rom, Sonic1Constants.LEVEL_HEADERS_ADDR) + zoneIndex * 16;
            int secondary = (rom.read32BitAddr(header + 4) >>> 24) & 0xFF;
            if (secondary != 0) {
                plcService.append(secondary);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to queue S1 post-title-card PLC", failure);
        }
        SkippedPresentationPlcLifecycle.runIterations(
                plcService, PlcLifecyclePhase.PALETTE_FADE, 22);
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS1LevelEvents",
            "Undoes S1 per-zone event handlers (GHZ/MZ/SYZ/LZ/SLZ/SBZ/FZ events)",
            levelEventManager::resetState);
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS1LevelEvents",
            "Undoes S1 per-zone event handlers, switch state, conveyor state, SBZ3 stomper door",
            () -> {
                levelEventManager.resetState();
                switchManager.reset();
                conveyorState.reset();
                Sonic1StomperDoorObjectInstance.resetSbz3Flag();
            });
    }
}
