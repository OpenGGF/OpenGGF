package com.openggf.game.soniccd;

import com.openggf.game.InitStep;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.StaticFixup;
import com.openggf.game.GameServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit terrain-only development PLAYER assembly through the existing load owner.
 * Reuses the same module's STATIC terrain steps, then creates one native routine-0
 * actor. No Process_Sprites, pre-landing, generic player reset, animation prime,
 * title, executable placements, HUD timer or native driver is admitted here.
 */
public final class SonicCdPlayerInitProfile implements LevelInitProfile {
    private final SonicCdGameModule module;
    private final SonicCdLevelInitProfile terrain;
    private boolean admittedAttempt;

    SonicCdPlayerInitProfile(SonicCdGameModule module, SonicCdLevelInitProfile terrain) {
        this.module = Objects.requireNonNull(module);
        this.terrain = Objects.requireNonNull(terrain);
    }

    public static LevelLoadContext terrainPlayerContext() {
        return SonicCdLevelInitProfile.staticStageContext();
    }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        // A rejected replacement request must not cancel the already admitted actor.
        admittedAttempt = false;
        validateRequest(Objects.requireNonNull(context, "context"));
        module.validateTerrainPlayerRequest();
        var steps = new ArrayList<InitStep>();
        steps.add(new InitStep("AdmitTerrainPlayer", "Validate the complete player request before any owner is reset", () -> {
            validateRequest(context);
            module.validateTerrainPlayerRequest();
            admittedAttempt = true;
            module.closeTerrainPlayer();
        }));
        steps.addAll(terrain.levelLoadSteps(context));
        steps.add(new InitStep("InstallTerrainPlayer", "Stage the source frame and exact registered Sonic factory after terrain/adapters",
                module::installTerrainPlayer));
        return List.copyOf(steps);
    }

    private static void validateRequest(LevelLoadContext context) {
        if (context.getLevelIndex() != 0 || context.getLoadMode() != LevelLoadMode.FULL
                || context.getLoadCause() != LevelLoadCause.FULL_LEVEL_ASSEMBLY
                || context.getAssemblyKind() != LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                || !context.isIncludePostLoadAssembly() || context.hasCheckpoint()
                || context.getRom() != null || context.isShowTitleCard()
                || context.isTitleCardRequiredInHeadlessMode() || context.isQueueFreshLevelRuntimeArt()) {
            throw SonicCd.unsupported("terrain PLAYER requires the explicit fresh R11A request; no preview/title/checkpoint/restart/carry/warp");
        }
    }

    @Override public void cancelPendingLevelLoadWork() {
        if (admittedAttempt) {
            admittedAttempt = false;
            module.closeTerrainPlayer();
        }
    }

    @Override public List<InitStep> levelTeardownSteps() {
        var steps = new ArrayList<InitStep>();
        steps.add(new InitStep("RetireTerrainPlayer", "Close admission and detach only its actor/callback before graphics teardown", () -> {
            admittedAttempt = false;
            module.closeTerrainPlayer();
        }));
        steps.addAll(terrain.levelTeardownSteps());
        return List.copyOf(steps);
    }

    @Override public List<InitStep> perTestResetSteps() {
        var steps = new ArrayList<InitStep>();
        steps.add(new InitStep("RetireTerrainPlayer", "Fresh reset retires native actor history; rewind never calls this", () -> {
            admittedAttempt = false;
            module.closeTerrainPlayer();
            if (GameServices.hasRuntime() && GameServices.level().getCurrentLevel() != null) {
                // RestoreStaticView needs authenticated BG dimensions before actor installation.
                module.rebindTerrainPresentation();
            }
        }));
        steps.addAll(terrain.perTestResetSteps());
        steps.add(new InitStep("RebuildTerrainPlayer", "Recreate only a previously loaded terrain visit at the named cold cut", () -> {
            if (!GameServices.hasRuntime() || GameServices.level().getCurrentLevel() == null) return;
            GameServices.level().resetFrameCounter();
            module.installTerrainPlayer();
        }));
        return List.copyOf(steps);
    }

    @Override public List<StaticFixup> postTeardownFixups() { return terrain.postTeardownFixups(); }
}
