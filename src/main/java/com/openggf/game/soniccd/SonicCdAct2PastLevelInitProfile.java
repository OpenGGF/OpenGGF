package com.openggf.game.soniccd;

import com.openggf.game.*;
import com.openggf.game.session.SessionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * Same-World R12B FULL host assembly, admitted only by the module's completed WARP return.
 * Authored by OpenAI Codex GPT-6.1 Sol. Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Initialization.asm:56-93 then Level/Main.asm:19-165. This profile orders existing
 * host owners; the module's real loading visit owns every native startup/fade/PLC step.
 * Its private validator must reject another context/source/World before any retirement,
 * including when this immutable step list is later executed. There is no checkpoint,
 * restart, development-entry or pre-executed native-loop fallback here.
 */
final class SonicCdAct2PastLevelInitProfile implements LevelInitProfile {
    private final SonicCdGameModule module;
    private boolean admitted;
    SonicCdAct2PastLevelInitProfile(SonicCdGameModule module) { this.module = Objects.requireNonNull(module); }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        admitted = false;
        module.validateAct2PastLoadRequest(Objects.requireNonNull(context));
        return List.of(
                new InitStep("AdmitActualAct2PastLoad", "Exact privately staged source/World before old owner retirement", () -> {
                    module.validateAct2PastLoadRequest(context); admitted = true; module.closeTerrainPlayer();
                    context.setGameModule(module); context.setZone(0); context.setAct(1);
                }),
                new InitStep("ResetAct2PastHost", "Existing host reset, no source loop or palette/art readiness", SonicCdAct2PastLevelInitProfile::resetHost),
                io("InitAct2PastModule", () -> GameServices.level().initGameModule(1)),
                io("PrepareAct2PastGeometry", () -> context.setLevel(GameServices.level().loadLevelData(1))),
                new InitStep("InstallAct2PastLoadingVisit", "Real Start and LevelStart prefix, ending before the first VSync", module::installAct2PastLoadingVisit),
                new InitStep("OmitDuplicateHostHud", "Native ring/time stay solely in the loading capture; no host HUD partition", () ->
                        GameServices.level().resetLevelGamestate(module.createLevelState())),
                io("InitAct2PastFeatures", () -> GameServices.level().initZoneFeatures()),
                new InitStep("AllocateAct2PastBackgroundRenderer", "Host allocation only; selected render remains unavailable", () ->
                        GameServices.level().initBackgroundRenderer()),
                new InitStep("RegisterAct2PastAdapters", "Existing module/camera/tilemap keys in the new load epoch", () -> {
                    var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode());
                    mode.registerLevelAdapters(mode.getLevelManager()); mode.registerGameModuleRewindAdapters();
                    module.registerAct2PastRestoreProjection();
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
        return List.of(new InitStep("RetireAct2PastLoad", "Retire the exact loading Frame and presentation", module::closeTerrainPlayer));
    }
    @Override public List<InitStep> perTestResetSteps() {
        throw new UnsupportedOperationException("Act2Past loading resets require the same source-bound rewind snapshot");
    }
    @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }

    private interface Io { void run() throws IOException; }
    private static InitStep io(String name, Io action) {
        return new InitStep(name, "Existing World load owner", () -> {
            try { action.run(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
    }
}
