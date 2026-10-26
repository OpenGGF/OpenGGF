package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.StaticFixup;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.SonicCdLevel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * Ordered engine lifecycle for a bounded, static development entry, not a transcription
 * of the native Level routine. All actor, music, title, event and PLC steps are absent.
 * InitialProcessSpritesLifecycle.NONE is intentional: prepareSetup has no native work
 * to dispatch, and all terrain/camera/background prerequisites are installed here.
 */
public final class SonicCdLevelInitProfile implements LevelInitProfile {
    private final SonicCdGameModule module;

    SonicCdLevelInitProfile(SonicCdGameModule module) { this.module = Objects.requireNonNull(module); }

    /** Exact request for the development source-entry caller; GameLoop alone selects LEVEL. */
    public static LevelLoadContext staticStageContext() {
        var context = new LevelLoadContext();
        context.setLevelIndex(0);
        context.setLoadMode(LevelLoadMode.FULL);
        context.setLoadCause(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        context.setIncludePostLoadAssembly(true);
        context.setShowTitleCard(false);
        context.setQueueFreshLevelRuntimeArt(false);
        return context;
    }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        return levelLoadSteps(context, false);
    }

    /** Same source/terrain reconstruction, with no audio reset inside the already opened host audio row. */
    List<InitStep> restartLevelLoadSteps(LevelLoadContext context) {
        return levelLoadSteps(context, true);
    }

    private List<InitStep> levelLoadSteps(LevelLoadContext context, boolean restart) {
        Objects.requireNonNull(context, "context");
        validateRequest(context, restart);
        var steps = new java.util.ArrayList<InitStep>();
        steps.addAll(List.of(
                new InitStep("AdmitStaticStage", "Authenticated R11A development admission, before side effects", () -> {
                    validateRequest(context, restart);
                    SonicCd.requireLevel(context.getLevelIndex());
                    module.requireRuntimeSource();
                    context.setGameModule(module);
                    context.setZone(0);
                    context.setAct(0);
                }),
                new InitStep("ResetStaticRuntime", "Retire old render/actor/donor state through existing owners",
                        () -> resetRuntime(true, true)),
                ioStep("InitGameModule", "Existing source factory and zone/scroll attachment",
                        () -> GameServices.level().initGameModule(context.getLevelIndex()))
        ));
        if (!restart) steps.addAll(List.of(
                new InitStep("RetirePriorAudio", "Explicitly retire previous source, donor and playback ownership",
                        module::retirePriorAudio),
                ioStep("ConfigureStaticAudio", "Install CD-owned silence, no SMPS/PCM/CDDA requests",
                        () -> GameServices.level().configureAudio())
        ));
        steps.addAll(List.of(
                ioStep("LoadLevelData", "Authenticated decoded palettes/patterns, then derived terrain/tilemaps",
                        () -> context.setLevel(GameServices.level().loadLevelData(context.getLevelIndex()))),
                new InitStep("InitStaticLevelState", "Read-only absent timer/rings, no native player initialization",
                        () -> GameServices.level().resetLevelGamestate(module.createLevelState())),
                new InitStep("InitStaticCamera", "Load-plan bounds and centre-derived origin, no focused actor",
                        () -> SonicCdStaticPresentation.initializeCamera(currentLevel(), GameServices.camera())),
                new InitStep("InitStaticBackground", "Fixed decoded BG origin; no native parallax/animation",
                        SonicCdLevelInitProfile::initializeBackground),
                ioStep("InitZoneFeatures", "Clear zone-scoped render registries; no native features are installed",
                        () -> GameServices.level().initZoneFeatures()),
                new InitStep("InitBackgroundRenderer", "Existing terrain renderer's normal BG allocation",
                        () -> GameServices.level().initBackgroundRenderer()),
                new InitStep("RegisterStaticLevelAdapters", "Existing level/tilemap/camera owners only", () -> {
                    var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode(), "gameplay mode");
                    mode.registerLevelAdapters(GameServices.level());
                })
        ));
        return List.copyOf(steps);
    }

    private void validateRequest(LevelLoadContext context, boolean restart) {
        if (restart && !module.ownsRestartContext(context)) {
            throw new IllegalStateException("CD restart context has no staged source carry");
        }
        if (context.getLevelIndex() != -1) SonicCd.requireLevel(context.getLevelIndex());
        if (context.getLoadMode() != LevelLoadMode.FULL) throw SonicCd.unsupported("preview/editor capture loads");
        if (context.getRom() != null) throw SonicCd.unsupported("cartridge load contexts");
        if (context.getAssemblyKind() == LevelAssemblyKind.STATE_RESTORATION
                || context.hasCheckpoint()
                || (restart ? context.getLoadCause() != LevelLoadCause.FULL_DEATH_RELOAD
                    : context.getLoadCause() != LevelLoadCause.DECODE_ONLY
                            && context.getLoadCause() != LevelLoadCause.FULL_LEVEL_ASSEMBLY)) {
            throw SonicCd.unsupported("checkpoint/editor/native transition assembly; restore the same world's registry instead");
        }
        if ((context.isIncludePostLoadAssembly() && context.isShowTitleCard())
                || context.isTitleCardRequiredInHeadlessMode()) {
            throw SonicCd.unsupported("native title-card entry");
        }
        if (context.isQueueFreshLevelRuntimeArt()) throw SonicCd.unsupported("native runtime-art handoffs");
    }

    private static SonicCdLevel currentLevel() {
        if (!(GameServices.level().getCurrentLevel() instanceof SonicCdLevel level)) {
            throw new IllegalStateException("CD static presentation requires its admitted terrain view");
        }
        return level;
    }

    private static void initializeBackground() {
        GameServices.parallax().update(0, 0, GameServices.camera(), GameServices.level().getFrameCounter());
    }

    private static void resetRuntime(boolean clearLevel, boolean clearGraphics) {
        if (!GameServices.hasRuntime()) return;
        var level = GameServices.level();
        if (clearLevel) level.resetState();
        else level.discardPendingInitialProcessSpritesForStateRestoration();
        GameServices.crossGameFeatures().resetState();
        GameServices.sprites().resetState();
        GameServices.collision().resetState();
        GameServices.water().reset();
        GameServices.fade().resetState();
        GameServices.timers().resetState();
        GameServices.gameState().resetForLevel();
        GameServices.camera().resetState();
        GameServices.parallax().resetState();
        if (clearGraphics) {
            var graphics = GameServices.graphics();
            if (graphics.isHeadlessMode() || !graphics.isGlInitialized()) {
                graphics.resetState();
            } else {
                // resetState also destroys the initialized tilemap renderer; preserve that
                // existing renderer and discard only old pattern/palette authority here.
                graphics.resetPatternAndPaletteState();
            }
            graphics.bindRuntimeManagedReferences(GameServices.camera(), GameServices.fade());
        }
    }

    @Override public List<InitStep> levelTeardownSteps() {
        return List.of(
                new InitStep("RetireStaticAudio", "Retire all prior playback/source ownership", module::retirePriorAudio),
                new InitStep("ResetStaticRuntime", "Clear level and all installed transient owners", () -> resetRuntime(true, false)),
                new InitStep("ResetStaticGraphics", "Release CD palettes/patterns even after runtime detachment",
                        () -> GameServices.graphics().resetState())
        );
    }

    @Override public List<InitStep> perTestResetSteps() {
        return List.of(
                new InitStep("ResetStaticTransientOwners", "Preserve decoded level and graphics; clear transient state",
                        () -> resetRuntime(false, false)),
                new InitStep("RestoreStaticView", "Restore the admitted static camera/background without native dispatch", () -> {
                    if (!GameServices.hasRuntime() || GameServices.level().getCurrentLevel() == null) return;
                    module.requireRuntimeSource();
                    GameServices.level().resetLevelGamestate(module.createLevelState());
                    SonicCdStaticPresentation.initializeCamera(currentLevel(), GameServices.camera());
                    GameServices.parallax().load(null);
                    initializeBackground();
                })
        );
    }

    @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static InitStep ioStep(String name, String purpose, IoAction action) {
        return new InitStep(name, purpose, () -> {
            try { action.run(); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
    }
}
