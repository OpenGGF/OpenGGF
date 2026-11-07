package com.openggf.game.sonic1;

import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Phase;
import com.openggf.game.internal.NativeLevelLoadPlan;
import com.openggf.level.NativeLevelLoadAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;

/** Actual GM_Ending continuation; only its prepared phases accept timing admission. */
final class Sonic1EndingLoad {
    private final LevelLoadContext context;
    private final Sonic1.EndingLoadSelection selected;
    private final com.openggf.game.sonic1.events.Sonic1LevelEventManager events;
    private final Runnable finished;
    private final int sourceRings;
    private boolean entryWaitReady, entryWaitComplete, fadeComplete;
    private com.openggf.game.resources.NativeFadeLifecycle.NativeBlockingFade fadeScope;
    private Sonic1EndingAssembly assembly;

    Sonic1EndingLoad(LevelLoadContext context, Sonic1.EndingLoadSelection selected,
                     com.openggf.game.sonic1.events.Sonic1LevelEventManager events, Runnable finished) {
        this.context = context; this.selected = selected; this.events = events; this.finished = finished;
        var player = GameServices.sprites().getMainPlayable();
        sourceRings = player == null ? 0 : player.getRingCount();
    }

    NativeLevelLoadPlan plan(List<InitStep> steps) {
        var named = new LinkedHashMap<String, InitStep>();
        for (var step : steps) named.put(step.name(), step);
        java.util.function.Function<String, InitStep> step = name -> java.util.Objects.requireNonNull(named.get(name), name);
        var setup = new ArrayList<InitStep>();
        for (String name : List.of("InitGameModule", "ResetRng", "LoadLevelData")) setup.add(step.apply(name));
        setup.add(new InitStep("InstallEndingPrefix", "GM_Ending clears Object_RAM before synchronous setup", () ->
                NativeLevelLoadAccess.installPrefix(GameServices.level(), context.getLevel())));
        for (String name : List.of("InitAnimatedContent", "InitObjectSystem", "InitZoneFeatures", "InitWater", "InitBackgroundRenderer"))
            setup.add(step.apply(name));
        setup.add(new InitStep("PrepareEndingObjectArt", "QuickPLC Ending and Hud_Base", () ->
                NativeLevelLoadAccess.initializeObjectArt(GameServices.level())));
        var placement = new ArrayList<InitStep>();
        placement.add(new InitStep("PrepareEndingPlayableArt", "End_LoadSonic native player resources", () ->
                NativeLevelLoadAccess.initializePlayableArt(GameServices.level())));
        for (String name : List.of("InitPlayerAndCheckpoint", "RestoreCheckpoint", "SpawnPlayer", "ResetPlayerState", "InitCamera", "InitBackgroundScroll", "InitLevelEvents", "InitRings"))
            placement.add(step.apply(name));
        placement.add(new InitStep("InitializeEndingSonic", "End_LoadSonic (sonic.asm:3623-3634)", this::preparePlayer));
        placement.add(new InitStep("PlaceEndingObjects", "ObjPosLoad before initial ExecuteObjects", () ->
                NativeLevelLoadAccess.prepareInitialObjects(GameServices.level())));
        var presentation = List.of(new InitStep("BuildEndingSprites", "BuildSprites before post-pass global clear", () -> assembly.buildSprites()),
                step.apply("InitGameplayState"),
                new InitStep("ClearEndingPostPassCounters", "GM_Ending (sonic.asm:3636-3652)", this::clearPostPass));
        return new NativeLevelLoadPlan(List.of(
                section(Phase.LEVEL_SETUP, setup, List.of()),
                section(Phase.INITIAL_PLACEMENT, placement, List.of()),
                section(Phase.INITIAL_OBJECTS, List.of(), List.of(new InitStep("ExecuteEndingObjects",
                        "End_LoadSonic initial ExecuteObjects", () -> assembly = Sonic1EndingAssembly.executeObjects()))),
                section(Phase.INITIAL_PRESENTATION, presentation, List.of()),
                NativeLevelLoadPlan.Section.steps(List.of(new InitStep("ArmEndingEntryWait", "WaitForVBlank VBlank_Ending", this::armEntryWait))),
                NativeLevelLoadPlan.Section.waitFor(() -> entryWaitComplete),
                NativeLevelLoadPlan.Section.steps(List.of(new InitStep("FadeEndingIn", "PaletteFadeIn (sonic.asm:3651-3660)", this::startFade))),
                NativeLevelLoadPlan.Section.waitFor(() -> fadeComplete),
                NativeLevelLoadPlan.Section.steps(List.of(new InitStep("CompleteEndingLoad", "End_MainLoop", finished)))));
    }

    private NativeLevelLoadPlan.Section section(Phase phase, List<InitStep> prepare, List<InitStep> commit) {
        return new NativeLevelLoadPlan.Section(phase, selected::inputs, () -> true, prepare, commit);
    }

    private void preparePlayer() {
        var player = GameServices.sprites().getMainPlayable();
        if (player == null) throw new IllegalStateException("Ending assembly has no native player");
        // Object RAM is fresh; global rings survive until the post-pass clear.
        player.setRingCount(sourceRings);
        player.setAir(false); player.setAngle((byte) 0);
        player.setGroundMode(com.openggf.game.GroundMode.GROUND);
        player.setOnObject(false); player.setPushing(false); player.forceAnimationRestart();
        events.prepareEndingInitialPlayer(player);
    }

    private void clearPostPass() {
        var player = GameServices.sprites().getMainPlayable();
        if (player != null) player.setRingCount(0);
        GameServices.level().setFrameCounter(0);
        GameServices.sprites().setFrameCounter(0);
    }

    private void armEntryWait() {
        // A WaitForVBlank cannot spend the interrupt of the loop that just
        // reached it. The production iteration token, never a trace row, owns this boundary.
        GameServices.plcFrameLifecycleOrNull().afterConsumedIteration(() -> entryWaitReady = true);
    }
    boolean readyForEntryVBlank() { return entryWaitReady && !entryWaitComplete; }
    void entryVBlankServiced() { entryWaitComplete = true; }
    private void startFade() {
        fadeScope = GameServices.plcFrameLifecycleOrNull().beginNativeBlockingFade();
        // PalFadeIn_Alt (_inc/Palette Fading.asm:42-49): 22 waits,
        // including the final wait after the 21 RGB component changes.
        GameServices.fadeOrNull().startFadeFromBlack(fadeScope.wrapCompletion(() -> fadeComplete = true),
                com.openggf.graphics.PaletteFadePresentation.ROM_FADE_FRAMES
                        - com.openggf.graphics.PaletteFadePresentation.COLOR_STEPS);
    }
    void cancel() { if (fadeScope != null) fadeScope.close(); }
}
