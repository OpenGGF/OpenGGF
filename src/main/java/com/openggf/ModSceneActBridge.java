package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelSceneActAccess;
import com.openggf.mods.scene.ActLaunch;
import com.openggf.mods.scene.ActResult;
import java.io.IOException;

/** Owns the scene/act round trip, outside the size-ratcheted frame loop.
 * Creator callbacks remain in OwnedSceneFactory; aborts use Engine's existing safe-title path. */
final class ModSceneActBridge {
    @FunctionalInterface interface Bootstrap {
        GameplayModeContext load(GameModule root, GameModule effective, int zone, ActLaunch launch) throws IOException;
    }
    private final GameLoop loop;
    private final EngineContext engine;
    private final Bootstrap bootstrap;
    private ActLaunch active;
    private boolean transitioning;
    private String priorMain, priorSidekicks;

    ModSceneActBridge(GameLoop loop, EngineContext engine, Bootstrap bootstrap) {
        this.loop = loop; this.engine = engine; this.bootstrap = bootstrap;
    }

    void updateScene(com.openggf.control.InputHandler input) {
        if (transitioning) { input.update(); return; }
        loop.modSceneHost.update(input);
        admitLaunch();
    }

    /** Called after the scene update; admits the request outside creator code. */
    void admitLaunch() {
        if (transitioning || active != null) return;
        ActLaunch launch = loop.modSceneHost.consumeActLaunch();
        if (launch == null) return;
        var world = loop.resolveGameplayModeContext().getWorldSession();
        GameModule module = world.resolvedGameModule();
        int zone = module.getZoneRegistry().resolveZoneKey(launch.destination())
                .orElseThrow(() -> new IllegalArgumentException("Unregistered scene act: " + launch.destination()));
        if (launch.act() >= module.getZoneRegistry().getActCount(zone))
            throw new IllegalArgumentException("Unregistered scene act index: " + launch.act());
        loop.modSceneHost.suspend();
        active = launch;
        transitioning = true;
        engine.audio().fadeOutMusic();
        loop.resolveFadeManager().startFadeToBlack(() -> {
            try {
                loop.modSceneHost.releaseGpuResources();
                SonicConfigurationService config = engine.configuration();
                priorMain = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
                priorSidekicks = config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
                config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, launch.main().persisted());
                config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                        String.join(",", launch.sidekicks().stream().map(key -> key.persisted()).toList()));
                loop.resetModuleScopedProviders();
                var gameplay = bootstrap.load(world.rootGameModule(), module, zone, launch);
                gameplay.getLevelManager().getLevelGamestate().setRings(launch.rings());
                LevelSceneActAccess.arm(gameplay.getLevelManager());
                loop.setGameMode(GameMode.LEVEL);
                loop.resolveFadeManager().startFadeFromBlack(() -> transitioning = false);
            } catch (com.openggf.mods.code.ModFaultBoundary.CallbackAborted aborted) {
                throw aborted; // Engine retires the visit before unloading the failed owner.
            } catch (IOException | RuntimeException failure) {
                java.util.logging.Logger.getLogger(ModSceneActBridge.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Cannot load scene act", failure);
                loop.returnToMasterTitle();
            }
        });
    }

    /** First operation at the LEVEL boundary; freezes gameplay through both fades. */
    boolean consumeExitOrHold(com.openggf.control.InputHandler input) {
        if (transitioning) { if (input != null) input.update(); return true; }
        if (active == null || loop.resolveFadeManager().isActive()) return false;
        var level = loop.resolveGameplayModeContext().getLevelManager();
        var exit = LevelSceneActAccess.consume(level);
        if (exit == null) return false;
        ActResult result = new ActResult(active.destination(), exit.reason(), exit.rings(), exit.frames(), exit.state());
        transitioning = true;
        if (input != null) input.update();
        engine.audio().fadeOutMusic();
        loop.resolveFadeManager().startFadeToBlack(() -> {
            restoreTeam();
            active = null;
            loop.setGameMode(GameMode.MOD_SCENE); // reports MODE_EXIT_TO_NON_REWINDABLE
            ModSceneLauncher.prepareSceneAudio(loop.resolveGameplayModeContext().getWorldSession().resolvedGameModule());
            loop.modSceneHost.resume(result);
            loop.resolveFadeManager().startFadeFromBlack(() -> transitioning = false);
        });
        return true;
    }

    boolean isTransitioning() { return transitioning; }

    void retire() { reset(); loop.modSceneHost.close(); }

    void reset() {
        restoreTeam(); active = null; transitioning = false;
    }
    private void restoreTeam() {
        if (priorMain == null) return;
        engine.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, priorMain);
        engine.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, priorSidekicks);
        priorMain = priorSidekicks = null;
    }
}
