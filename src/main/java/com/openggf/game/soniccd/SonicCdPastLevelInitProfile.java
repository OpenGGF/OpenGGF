package com.openggf.game.soniccd;

import com.openggf.game.*;
import com.openggf.game.session.SessionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/** Same World FULL load, admitted only by the module's actual completed WARP visit. */
final class SonicCdPastLevelInitProfile implements LevelInitProfile {
    private final SonicCdGameModule module;
    private boolean admitted;
    SonicCdPastLevelInitProfile(SonicCdGameModule module) { this.module = Objects.requireNonNull(module); }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        admitted = false;
        module.validatePastLoadRequest(Objects.requireNonNull(context));
        return List.of(
                new InitStep("AdmitActualPastLoad", "Exact privately staged source/World before old owner retirement", () -> {
                    module.validatePastLoadRequest(context); admitted = true; module.closeTerrainPlayer();
                    context.setGameModule(module); context.setZone(0); context.setAct(0);
                }),
                new InitStep("ResetPastHost", "Existing host reset, no source loop or palette/art readiness", SonicCdPastLevelInitProfile::resetHost),
                io("InitPastModule", () -> GameServices.level().initGameModule(0)),
                io("PreparePastGeometry", () -> context.setLevel(GameServices.level().loadLevelData(0))),
                new InitStep("InstallPastLoadingVisit", "Real Start and LevelStart prefix, ending before the first VSync", module::installPastLoadingVisit),
                new InitStep("OmitDuplicateHostHud", "Native ring/time stay solely in the loading capture; no host HUD partition", () ->
                        GameServices.level().resetLevelGamestate(module.createLevelState())),
                io("InitPastFeatures", () -> GameServices.level().initZoneFeatures()),
                new InitStep("AllocatePastBackgroundRenderer", "Host allocation only; selected render remains unavailable", () ->
                        GameServices.level().initBackgroundRenderer()),
                new InitStep("RegisterPastAdapters", "Existing module/camera/tilemap keys in the new load epoch", () -> {
                    var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode());
                    mode.registerLevelAdapters(mode.getLevelManager()); mode.registerGameModuleRewindAdapters();
                    module.registerPastRestoreProjection();
                }));
    }

    private static void resetHost() {
        GameServices.level().resetState();
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
    @Override public void cancelPendingLevelLoadWork() { if (admitted) { admitted = false; module.closeTerrainPlayer(); } }
    @Override public List<InitStep> levelTeardownSteps() {
        return List.of(new InitStep("RetirePastLoad", "Retire the exact loading Frame and presentation", module::closeTerrainPlayer));
    }
    @Override public List<InitStep> perTestResetSteps() {
        throw new UnsupportedOperationException("Past loading resets require the same source-bound rewind snapshot");
    }
    @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }

    private interface Io { void run() throws IOException; }
    private static InitStep io(String name, Io action) {
        return new InitStep(name, "Existing World load owner", () -> {
            try { action.run(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
    }
}
