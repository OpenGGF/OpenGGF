package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.InputBindingFactory;

import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.mods.code.DevelopmentPatchLoader;
import com.openggf.mods.mutators.MutatorScope;
import com.openggf.mods.mutators.MutatorSessionState;
import com.openggf.mods.mutators.MutatorWorldAccess;
import com.openggf.mods.mutators.OwnedMutator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * Observe native Mutator Lab stage scheduling and offline PCM.
 * Origin: all-game expansion at 0e2167ce8c8671e0895eb4f2da60a5ce66cce786.
 * Inputs: game, stage, speed, audio-follow, denial index, bounded outer samples,
 * verified creator JAR, and fresh isolated output directory (also user.dir).
 * Maintained explicit packaged-mod HeadlessGameBoot supplies hidden GL and native
 * ROM/team/world/registration owners; direct-level solo setup and gated entry are
 * declared. Each outer frame uses interactive stepPresentationFrame plus exactly
 * one Engine audio boundary. No logical/movie override, fake policy/provider,
 * direct stage initialize, readiness mutation, or comparison-state hydration.
 * CSV and little-endian stereo PCM are observations only. This tool does not
 * render stages or certify Engine.loop/window/UI/natural approach/completion.
 * Run in a fresh bounded JVM; supply all original absolute ROM paths in the
 * output directory config.yaml. See tools/media/README.md for the contract.
 */
public final class MutatorStageProbeTool {
    private final EngineContext services;

    private MutatorStageProbeTool(EngineContext services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    private HeadlessGameBoot boot;
    private GameLoop loop;
    private InputHandler input;
    private SonicConfigurationService config;
    private LiveCaptureAudioHandle audio;
    private PrintWriter rows;
    private OutputStream pcm;
    private int outer;
    private int measured;
    private String phase = "boot";

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        String game = options.game(), stage = options.stage();
        int speed = options.speed(), denyAt = options.denyAt(), bound = options.samples();
        boolean follow = options.audioFollows();
        Path mod = options.mod().toRealPath(), out = options.out().toAbsolutePath().normalize();
        if (!Files.isRegularFile(mod) || !mod.getFileName().toString().endsWith(".jar"))
            throw new IllegalArgumentException("Verified development JAR required");
        if (!Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(out))
            throw new IllegalArgumentException("Launch with isolated user.dir equal to freshOut");
        Files.createDirectories(out);
        try (var entries = Files.list(out)) {
            if (entries.anyMatch(p -> !Set.of("config.yaml", "empty-roms").contains(p.getFileName().toString())))
                throw new IllegalArgumentException("Fresh owned output directory required");
        }
        new MutatorStageProbeTool(EngineServices.current())
                .run(game, stage, speed, follow, denyAt, bound, mod, out);
    }

    /** Syntax-only validation: refuses unbounded/native-incompatible cells before startup. */
    record Arguments(String game, String stage, int speed, boolean audioFollows,
                     int denyAt, int samples, Path mod, Path out) {
        static Arguments parse(String[] args) {
            if (args.length != 8) throw new IllegalArgumentException(
                    "game stage speed audioFollow denyAt samples creatorJar freshOut");
            String game = args[0], stage = args[1];
            int speed = Integer.parseInt(args[2]);
            boolean follow = switch (args[3]) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new IllegalArgumentException("audioFollow must be true/false");
            };
            int denyAt = Integer.parseInt(args[4]), samples = Integer.parseInt(args[5]);
            if (!Set.of("s1", "s2", "s3k").contains(game) || speed < 25 || speed > 400
                    || speed % 25 != 0 || samples < 8 || samples > 600 || denyAt < -2
                    || denyAt >= samples)
                throw new IllegalArgumentException("Unbounded/invalid probe input");
            if (!Set.of("special", "GUMBALL", "GLOWING_SPHERE", "SLOT_MACHINE").contains(stage)
                    || (!stage.equals("special") && !game.equals("s3k")))
                throw new IllegalArgumentException("Unsupported native stage cell");
            return new Arguments(game, stage, speed, follow, denyAt, samples,
                    Path.of(args[6]), Path.of(args[7]));
        }
    }

    private void run(String game, String stage, int speed, boolean follow, int denyAt,
                     int bound, Path mod, Path out) throws Exception {
        Throwable primary = null;
        try {
            config = services.configuration();
            config.setConfigValue(SonicConfiguration.DEFAULT_ROM, game);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, false);
            config.setConfigValue(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, false);
            config.setConfigValue(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
            config.setConfigValue(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, false);
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, false);
            config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
            boot = new HeadlessGameBoot(320, 224, services);
            boot.setModuleDecorator(DevelopmentPatchLoader.fromJar(mod));
            var romKey = switch (game) {
                case "s1" -> SonicConfiguration.SONIC_1_ROM;
                case "s2" -> SonicConfiguration.SONIC_2_ROM;
                default -> SonicConfiguration.SONIC_3K_ROM;
            };
            loop = boot.boot(Path.of(config.getString(romKey)), 0, 0);
            input = new InputHandler(InputBindingFactory.supplier(config));
            loop.setInputHandler(input);
            if (loop.getInputHandler() != input
                    || !boot.hasInstalledHeadlessAudioBackend())
                throw new IllegalStateException("Maintained input/audio bootstrap identity missing");
            audio = services.audio().beginLiveCaptureAudio(60);
            rows = new PrintWriter(Files.newBufferedWriter(out.resolve("observations.csv")));
            pcm = Files.newOutputStream(out.resolve("offline-stereo-s16le.pcm"));
            row("outer", "phase", "measured", "mode", "world", "level", "provider",
                    "native_epoch", "interactive", "accepted_sample", "native_p1", "native_p2",
                    "p2_supported", "journal_tick", "revision", "speed", "audio_follows",
                    "remainder", "pending_p1", "pending_p2", "pending", "level_frame",
                    "vint", "initial_process", "title_requested", "title_overlay", "paused", "fade",
                    "player_x", "player_y", "player_xvel", "player_yvel", "native_state",
                    "pcm_frames", "pcm_clock", "logical_override", "external_owner");
            int waited = 0;
            while (!levelReady() && waited++ < 1800) step();
            if (!levelReady()) throw new IllegalStateException("Native direct-level boot did not release control");
            var world = SessionManager.getCurrentWorldSession();
            if (world == null || !world.getGameModule().getGameId().code().equals(game))
                throw new IllegalStateException("Wrong native game");
            var state = Objects.requireNonNull(MutatorWorldAccess.state(world), "Actual Lab owner missing");
            String speedKey = key(state, "game-speed"), denyKey = key(state,
                    stage.equals("special") ? "no-special-stages" : "no-bonus-stages");
            state.requestOption(speedKey, "percent", speed);
            state.requestOption(speedKey, "audio", follow);
            state.requestEnabled(speedKey, true);
            if (denyAt == -2) state.requestEnabled(denyKey, true);
            admit(state, world);
            phase = "declared-native-entry-request";
            if (stage.equals("special")) GameServices.level().requestSpecialStageEntry(SpecialStageEntryRequest.ordinary());
            else GameServices.level().requestBonusStageEntry(BonusStageType.valueOf(stage));
            if (denyAt == -2) {
                for (int i = 0; i < 120; i++) {
                    step();
                    if (loop.getCurrentGameMode() != GameMode.LEVEL)
                        throw new IllegalStateException("Denied request crossed into native stage mode");
                }
                if (loop.getCurrentGameMode() != GameMode.LEVEL)
                    throw new IllegalStateException("Denied new native request entered a stage");
                phase = "new-entry-denial-observed";
                observe();
                return;
            }
            waited = 0;
            while (!interactive(stage) && waited++ < 2400) step();
            if (!interactive(stage)) throw new IllegalStateException("Native stage did not reach interactive control");
            var expectedProvider = provider();
            var expectedWorld = SessionManager.getCurrentWorldSession();
            long expectedEpoch = epoch();
            phase = "interactive-before-outer";
            observe();
            while (measured < bound && interactive(stage)) {
                if (provider() != expectedProvider || SessionManager.getCurrentWorldSession() != expectedWorld
                        || epoch() != expectedEpoch) throw new IllegalStateException("Native owner boundary ended this cell");
                if (measured == denyAt) {
                    state.requestEnabled(denyKey, true);
                    admit(state, world);
                    phase = "already-admitted-late-denial";
                    observe();
                }
                // Configured P1 A tap spans exactly one presented outer frame, not a logical override.
                if (measured == 1) input.handleKeyEvent(config.getInt(SonicConfiguration.P1_A), GLFW_PRESS);
                if (measured == 2) input.handleKeyEvent(config.getInt(SonicConfiguration.P1_A), GLFW_RELEASE);
                phase = "interactive-after-outer";
                step();
                measured++;
            }
            phase = measured == bound ? "bounded-window-complete" : "native-owner-boundary-before-bound";
            observe();
            rows.flush();
            if (rows.checkError()) throw new IOException("Observation write failed");
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = primary;
            cleanup = close(cleanup, () -> { if (input != null) input.handleKeyEvent(config.getInt(SonicConfiguration.P1_A), GLFW_RELEASE); });
            cleanup = close(cleanup, () -> { if (rows != null) { rows.flush(); if (rows.checkError()) throw new IOException("Observation flush failed"); rows.close(); } });
            cleanup = close(cleanup, () -> { if (pcm != null) pcm.close(); });
            cleanup = close(cleanup, () -> { if (audio != null) audio.close(); });
            cleanup = close(cleanup, () -> WorldSessionPolicyAccess.closeScreens(SessionManager.getCurrentWorldSession()));
            cleanup = close(cleanup, () -> services.graphics().cleanup());
            cleanup = close(cleanup, () -> { if (boot != null) boot.close(); });
            cleanup = close(cleanup, () -> services.audio().destroy());
            cleanup = close(cleanup, Engine::clearGlobalInstance);
            if (primary == null && cleanup != null) {
                if (cleanup instanceof Error error) throw error;
                throw (Exception) cleanup;
            }
        }
    }

    private static void admit(MutatorSessionState state, WorldSession world) {
        if (!MutatorWorldAccess.save(world)) throw new IllegalStateException("Host save failed: " + MutatorWorldAccess.saveError(world));
        var result = state.boundary(MutatorScope.LIVE);
        if (!result.accepted()) throw new IllegalStateException("LIVE admission rejected: " + result.message());
    }
    private static String key(MutatorSessionState state, String local) {
        return state.definitions().stream().filter(o -> o.definition().localId().equals(local))
                .map(OwnedMutator::key).findFirst().orElseThrow();
    }
    private boolean levelReady() {
        var context = SessionManager.getCurrentGameplayMode();
        var level = GameServices.levelOrNull();
        var player = GameServices.camera().getFocusedSprite();
        return loop.getCurrentGameMode() == GameMode.LEVEL && context != null && context.isGameplayRuntimeReady()
                && level != null && level.getCurrentLevel() != null && !level.hasPendingInitialProcessSpritesPass()
                && !level.isTitleCardRequested() && !services.graphics().getFadeManager().isActive()
                && player != null && !player.getDead() && !player.isControlLocked()
                && !titleOverlay();
    }
    private Object provider() {
        return loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE
                ? loop.getActiveSpecialStageProvider()
                : SessionManager.getCurrentGameplayMode().getActiveBonusStageProvider();
    }
    private long epoch() {
        var value = provider(); var special = NativeStagePacingOwners.special(value);
        if (special != null) return special.pacingState().entryEpoch();
        var bonus = NativeStagePacingOwners.bonus(value); return bonus == null ? -1 : bonus.pacingEntryEpoch();
    }
    private boolean interactive(String stage) {
        if (loop.isPaused() || services.graphics().getFadeManager().isActive()) return false;
        if (stage.equals("special")) {
            if (loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE) return false;
            var owner = NativeStagePacingOwners.special(provider());
            return owner != null && owner.pacingState().interactive();
        }
        if (loop.getCurrentGameMode() != GameMode.BONUS_STAGE) return false;
        var value = (BonusStageProvider) provider(); var level = GameServices.levelOrNull();
        return NativeStagePacingOwners.bonus(value) != null && value.getActiveType() == BonusStageType.valueOf(stage)
                && !value.isStageComplete() && level != null && !level.hasPendingInitialProcessSpritesPass()
                && !level.isTitleCardRequested() && !titleOverlay();
    }
    private boolean titleOverlay() {
        var title = loop.getTitleCardProvider();
        return title != null && title.isOverlayActive();
    }
    private void step() throws IOException {
        if (input.hasLogicalOverride() || loop.externalFrameOrInputOwnerActive())
            throw new IllegalStateException("Canonical/external frame ownership invalidates interactive observations");
        loop.stepPresentationFrame();
        Engine.presentOuterAudioFrame(loop, false, false);
        short[] samples = new short[audio.maxStereoFramesPerPacket() * 2];
        int stereoFrames = audio.drainPresentationFrame(samples);
        for (int i = 0; i < stereoFrames * 2; i++) { pcm.write(samples[i] & 255); pcm.write((samples[i] >>> 8) & 255); }
        outer++;
        observe();
    }
    private void observe() {
        var world = SessionManager.getCurrentWorldSession(); var level = GameServices.levelOrNull();
        var state = world == null ? null : MutatorWorldAccess.state(world);
        var pacing = WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
        var snapshot = pacing == null ? null : pacing.capture();
        Object value = provider(); var owner = NativeStagePacingOwners.special(value);
        var nativeState = owner == null ? null : owner.pacingState();
        var player = GameServices.camera().getFocusedSprite();
        Object comparison = value instanceof Sonic1SpecialStageProvider p ? p.getManager().captureComparisonState()
                : value instanceof Sonic2SpecialStageProvider p ? p.getManager().captureComparisonState()
                : value instanceof Sonic3kSpecialStageProvider p ? p.getManager().captureComparisonState()
                : value instanceof BonusStageProvider p ? p.getActiveType() + "/" + p.isStageComplete() : "";
        row(outer, phase, measured, loop.getCurrentGameMode(), identity(world), identity(level == null ? null : level.getCurrentLevel()),
                identity(value), epoch(), nativeState == null ? "" : nativeState.interactive(),
                nativeState == null ? "" : nativeState.acceptedSampleOrdinal(), nativeState == null ? "" : nativeState.player1Held(),
                nativeState == null ? "" : nativeState.player2Held(), nativeState == null ? "" : nativeState.player2Supported(),
                state == null ? "" : state.snapshot().tick(), state == null ? "" : state.effective().revision(),
                pacing == null ? "" : pacing.policy().gameplaySpeedPercent(), pacing == null ? "" : pacing.policy().audioFollowsSpeed(),
                snapshot == null ? "" : snapshot.remainder(), snapshot == null ? "" : snapshot.player1(), snapshot == null ? "" : snapshot.player2(),
                snapshot == null ? "" : snapshot.pending(), level == null ? "" : level.getFrameCounter(),
                level == null || level.getObjectManager() == null ? "" : level.getObjectManager().getVblaCounter(),
                level == null ? "" : level.hasPendingInitialProcessSpritesPass(), level == null ? "" : level.isTitleCardRequested(), titleOverlay(),
                loop.isPaused(), services.graphics().getFadeManager().isActive(),
                player == null ? "" : player.getCentreX(), player == null ? "" : player.getCentreY(),
                player == null ? "" : player.getXSpeed(), player == null ? "" : player.getYSpeed(), comparison,
                audio.totalStereoFrames(), audio.clockSnapshot(), input.hasLogicalOverride(), loop.externalFrameOrInputOwnerActive());
    }
    private void row(Object... values) {
        for (int i = 0; i < values.length; i++) { if (i != 0) rows.print(','); rows.print('"');
            rows.print(String.valueOf(values[i]).replace("\"", "\"\"")); rows.print('"'); }
        rows.println();
    }
    private static String identity(Object value) { return value == null ? "" : value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value)); }
    @FunctionalInterface private interface Cleanup { void run() throws Exception; }
    private static Throwable close(Throwable previous, Cleanup action) {
        try { action.run(); } catch (Exception | Error failure) { if (previous == null) return failure; previous.addSuppressed(failure); }
        return previous;
    }
}
