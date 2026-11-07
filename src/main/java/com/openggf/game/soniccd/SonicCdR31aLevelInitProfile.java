package com.openggf.game.soniccd;

import com.openggf.game.*;
import com.openggf.game.session.SessionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * Same-World R31A FULL host assembly, admitted only by the module's original applied selected-profile RunR3 publication.
 * Authored by OpenAI Codex GPT-6.1 Sol. Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Initialization.asm:56-93 then Level/Main.asm:19-165. This profile orders existing
 * host owners; the module's real loading visit owns every native startup/fade/PLC step.
 * Its private validator must reject another context/source/World before any retirement,
 * including when this immutable step list is later executed. There is no checkpoint,
 * restart, development-entry or pre-executed native-loop fallback here.
 */
final class SonicCdR31aLevelInitProfile implements LevelInitProfile {
    private final SonicCdGameModule module;
    private boolean admitted;
    SonicCdR31aLevelInitProfile(SonicCdGameModule module) { this.module = Objects.requireNonNull(module); }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        admitted = false;
        module.validateR31LoadRequest(Objects.requireNonNull(context));
        return List.of(
                new InitStep("AdmitActualR31aLoad", "Exact privately staged source/World before old owner retirement", () -> {
                    module.validateR31LoadRequest(context); admitted = true; module.closeTerrainPlayer();
                    context.setGameModule(module); context.setZone(1); context.setAct(0);
                }),
                new InitStep("ResetR31aHost", "Existing host reset, no source loop or palette/art readiness", SonicCdR31aLevelInitProfile::resetHost),
                io("InitR31aModule", () -> GameServices.level().initGameModule(module.getZoneRegistry().getLevelDataForZone(1).getFirst().levelIndex())),
                io("PrepareR31aGeometry", () -> context.setLevel(GameServices.level().loadLevelData(module.getZoneRegistry().getLevelDataForZone(1).getFirst().levelIndex()))),
                new InitStep("InstallR31aLoadingVisit", "Real Start and LevelStart prefix, ending before the first VSync", module::installR31LoadingVisit),
                new InitStep("OmitDuplicateHostHud", "Native ring/time stay solely in the loading capture; no host HUD partition", () ->
                        GameServices.level().resetLevelGamestate(module.createLevelState())),
                io("InitR31aFeatures", () -> GameServices.level().initZoneFeatures()),
                new InitStep("AllocateR31aBackgroundRenderer", "Host allocation only; selected render remains unavailable", () ->
                        GameServices.level().initBackgroundRenderer()),
                new InitStep("RegisterR31aAdapters", "Existing module/camera/tilemap keys in the new load epoch", () -> {
                    var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode());
                    mode.registerLevelAdapters(mode.getLevelManager()); mode.registerGameModuleRewindAdapters();
                    module.registerR31RestoreProjection();
                }));
    }

    private static void resetHost() {
        com.openggf.level.NativeLevelCourseReset.reset(GameServices.level(), 1, 0);
        GameServices.crossGameFeatures().resetState(); GameServices.sprites().resetState();
        GameServices.collision().resetState(); GameServices.water().reset(); GameServices.fade().resetState();
        GameServices.timers().resetState(); GameServices.gameState().resetForLevel(); GameServices.camera().resetState();
        GameServices.parallax().resetState();
        var graphics = GameServices.graphics();
        if (graphics.isHeadlessMode() || !graphics.isGlInitialized()) graphics.resetState();
        else graphics.resetPatternAndPaletteState();
        graphics.bindRuntimeManagedReferences(GameServices.camera(), GameServices.fade());
        // Audio's actual current row and PCM identity survive; no configure/reset/duplicate request.
    }

    @Override public boolean isLevelMusicPublicationPending() { return true; }
    @Override public void cancelPendingLevelLoadWork() { if (admitted) { module.closeTerrainPlayer(); admitted = false; } }
    @Override public List<InitStep> levelTeardownSteps() {
        return List.of(new InitStep("RetireR31aLoad", "Retire the exact loading Frame and presentation", module::closeTerrainPlayer));
    }
    @Override public List<InitStep> perTestResetSteps() {
        throw new UnsupportedOperationException("R31a loading resets require the same source-bound rewind snapshot");
    }
    @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }

    private interface Io { void run() throws IOException; }
    private static InitStep io(String name, Io action) {
        return new InitStep(name, "Existing World load owner", () -> {
            try { action.run(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
    }
}
