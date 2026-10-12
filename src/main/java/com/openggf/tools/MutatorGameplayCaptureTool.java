package com.openggf.tools;

/*
 * MutatorGameplayCaptureTool — Mutator Lab gameplay-promo capture driver (task e0603654, opus_gameplay_promo_r1).
 * Purpose: run the production GameplayCaptureSession exactly as GameplayCaptureTool.run does
 * (same arguments, one native tick per input frame, PNG every frame, offline PCM per outer frame,
 * identical state.csv), and add observe.csv with semantic values read from native owners:
 * score, live/slotless spilled-ring objects, admitted head scale/gravity, admitted gameplay
 * mutator policy (ringfall/rebound) and live badnik count. Inputs: GameplayCaptureTool CLI flags.
 * Default capture remains canonical. The opt-in showcase route, preserved from the
 * all-eleven film (2026-10-10, source 379b6641323853f61851943fb7aadd5e7a389e01), adds
 * native special-stage drawing and session observations. --interactive-pacing uses
 * configured keyboard events, GameLoop.stepPresentationFrame and one Engine outer
 * audio boundary; it never retimes media or changes the production pacing owner.
 * Inputs: original ROM, packaged mod, input log and declared capture setup. Requires
 * surfaceless EGL; all audio is offline. Observations never write gameplay state.
 */
import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.InputBindingFactory;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.*;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicySource;
import com.openggf.game.mutators.LevelMutatorPolicySource;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static com.openggf.control.InputActionMasks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public final class MutatorGameplayCaptureTool {
    public static void main(String[] argv) throws Exception {
        Report report = run(Options.parse(argv));
        System.out.println("done frames=" + report.framesStepped() + " death=" + report.deathFrame()
                + " out=" + report.outDir());
    }

    record Options(GameplayCaptureTool.Arguments capture, boolean showcase, boolean interactivePacing) {
        static final int MAX_FRAMES = 36_000;
        private static final Set<String> VALUE_FLAGS = Set.of("--game", "--rom", "--mod", "--zone", "--act",
                "--x", "--y", "--width", "--main", "--sidekick", "--donor", "--input", "--settle",
                "--input-start", "--frames", "--capture-from", "--every", "--stop-on-death", "--death-grace",
                "--fps", "--out-dir", "--emeralds", "--vint-run-count", "--camera-x-sub", "--rings");

        static Options parse(String[] argv) {
            boolean showcase = false, interactive = false;
            List<String> base = new ArrayList<>();
            for (int i = 0; i < argv.length; i++) {
                String flag = argv[i];
                switch (flag) {
                    case "--showcase" -> showcase = true;
                    case "--interactive-pacing" -> { interactive = true; showcase = true; }
                    case "--donor-rom", "--stills", "--scale" -> throw new IllegalArgumentException(
                            flag + " is unsupported by MutatorGameplayCaptureTool");
                    default -> {
                        base.add(flag);
                        if (VALUE_FLAGS.contains(flag)) {
                            String value = CliArguments.requireValue(argv, ++i, flag);
                            if (value.startsWith("--")) throw new IllegalArgumentException("Missing value for " + flag);
                            if (flag.equals("--stop-on-death") && !Set.of("true", "false").contains(value))
                                throw new IllegalArgumentException("--stop-on-death must be true or false");
                            base.add(value);
                        }
                    }
                }
            }
            var a = GameplayCaptureTool.Arguments.parse(base.toArray(String[]::new));
            if (!Set.of("s1", "s2", "s3k").contains(a.game()))
                throw new IllegalArgumentException("--game must be s1, s2 or s3k");
            if (a.settle() < 0 || a.inputStart() < 0 || a.captureFrom() < 0 || a.deathGrace() < 0)
                throw new IllegalArgumentException("frame offsets and death grace must be nonnegative");
            if (a.fps() != 60) throw new IllegalArgumentException("Mutator capture requires --fps 60");
            if (a.frames() != null) boundedFrames(a.frames());
            if (interactive && (a.captureFrom() != 0 || a.every() != 1))
                throw new IllegalArgumentException("--interactive-pacing requires capture-from 0 and every 1");
            if (interactive && (a.titleScreen() || a.completeSpecialStage() || !a.donor().equals("off")
                    || !a.sidekickCharacter().isBlank()))
                throw new IllegalArgumentException("--interactive-pacing supports solo native direct-level capture; "
                        + "--title-screen, --complete-special-stage, sidekicks and donors are unsupported");
            return new Options(a, showcase, interactive);
        }

        static int boundedFrames(long total) {
            if (total < 1 || total > MAX_FRAMES)
                throw new IllegalArgumentException("capture must contain 1.." + MAX_FRAMES + " frames");
            return (int) total;
        }
    }

    record Report(int framesStepped, int deathFrame, Path outDir) { }

    static Report run(Options options) throws Exception {
        var a = options.capture();
        requireDisplayFreeContext();
        Bk2Movie movie = a.input() == null ? null : new Bk2MovieLoader().loadMovieOrInputLog(a.input());
        int scriptFrames = movie == null ? 0 : Math.max(0, movie.getFrameCount() - a.inputStart());
        int total = Options.boundedFrames(a.frames() != null ? a.frames() : (long) a.settle() + scriptFrames);
        if (options.interactivePacing() && movie != null) {
            for (int i = 0; i < Math.min(scriptFrames, total - a.settle()); i++)
                PresentationInput.validate(movie.getFrame(i + a.inputStart()));
        }
        Path out = a.outDir(), frames = out.resolve("frames");
        Files.createDirectories(frames);
        var settings = new GameplayCaptureSession.Settings(a.width(), a.mainCharacter(), a.sidekickCharacter(),
                a.donor(), null, a.startX(), a.startY(), a.emeralds(), a.titleCard(), a.completeSpecialStage(),
                a.vIntRunCount(), a.cameraXSub(), a.starPost(), a.rings(), a.reverseGravity());
        int zone = GameplayCaptureTool.ZoneIds.resolve(a.game(), a.zone());
        int dead = -1, stepped = 0;
        try (var session = new GameplayCaptureSession(settings);
             var state = Files.newBufferedWriter(out.resolve("state.csv"), StandardCharsets.UTF_8);
             var obs = Files.newBufferedWriter(out.resolve("observe.csv"), StandardCharsets.UTF_8);
             var bads = Files.newBufferedWriter(out.resolve("badniks.csv"), StandardCharsets.UTF_8);
             var details = options.showcase() ? Files.newBufferedWriter(out.resolve("observe2.csv"), StandardCharsets.UTF_8) : null;
             var monitors = options.showcase() ? Files.newBufferedWriter(out.resolve("monitors.csv"), StandardCharsets.UTF_8) : null) {
            bads.write("frame,class,x,y"); bads.newLine();
            if (details != null) {
                details.write(SESSION_HEADER); details.newLine();
                monitors.write("frame,class,x,y"); monitors.newLine();
            }
            if (a.mod() != null) session.applyMod(a.mod());
            if (a.titleScreen()) session.startAtTitle();
            if (a.audio()) session.enableAudio();
            session.boot(a.rom(), zone, a.act(), settings);
            var audio = GameServices.audio();
            try (var input = options.interactivePacing() ? PresentationInput.install(session.loop()) : null;
                 var capture = a.audio() ? audio.beginLiveCaptureAudio(a.fps()) : null;
                 var pcm = a.audio() ? new BufferedOutputStream(Files.newOutputStream(out.resolve("audio.pcm"))) : OutputStream.nullOutputStream()) {
                short[] samples = capture == null ? new short[0] : new short[capture.maxStereoFramesPerPacket() * 2];
                state.write(a.titleScreen() ? GameplayCaptureSession.stateHeaderWithHostState() : GameplayCaptureSession.stateHeader());
                state.newLine();
                obs.write("frame,mode,rings,score,lost_rings,lost_rings_slotless,lost_rings_collectible,head_scale,gravity_pct,"
                        + "ringfall_pct,ringfall_cap,ringfall_full,rebound_pct,rebound_cap,badniks,x,y,xvel,yvel,air,hurt,dead,cam_x,cam_y,near_badnik,near_dx,near_dy");
                obs.newLine();
                for (int f = 0; f < total; f++) {
                    int si = f - a.settle();
                    Bk2FrameInput in = movie != null && si >= 0 && si < scriptFrames ? movie.getFrame(si + a.inputStart()) : null;
                    if (input == null) session.step(in);
                    else input.step(session.loop(), in, a.titleCard());
                    stepped++;
                    int pcmFrames = 0;
                    if (a.audio()) {
                        if (input == null) session.loop().presentOuterFrame(false, false);
                        else Engine.presentOuterAudioFrame(session.loop(), false, false);
                        pcmFrames = capture.drainPresentationFrame(samples);
                        for (int i = 0; i < pcmFrames * 2; i++) { pcm.write(samples[i] & 255); pcm.write((samples[i] >>> 8) & 255); }
                    }
                    state.write(a.titleScreen() ? session.stateLineWithHostState(f, in) : session.stateLine(f, in));
                    state.newLine();
                    obs.write(observe(session, f)); obs.newLine();
                    if (details != null) { details.write(observeSession(session, f, pcmFrames, input)); details.newLine(); }
                    var lmb = GameServices.level();
                    var omb = lmb == null ? null : lmb.getObjectManager();
                    if (omb != null) for (var b : omb.activeObjectsOfType(AbstractBadnikInstance.class))
                        if (!b.isDestroyed()) { bads.write(f + "," + b.getClass().getSimpleName() + "," + b.getX() + "," + b.getY()); bads.newLine(); }
                    if (monitors != null && omb != null) for (var m : omb.activeObjectsOfType(AbstractMonitorObjectInstance.class))
                        if (!m.isDestroyed()) { monitors.write(f + "," + m.getClass().getSimpleName() + "," + m.getX() + "," + m.getY()); monitors.newLine(); }
                    if (f >= a.captureFrom() && (f - a.captureFrom()) % a.every() == 0)
                        ScreenshotCapture.savePNG(options.showcase() ? renderShowcase(session) : session.render(),
                                frames.resolve(String.format(Locale.ROOT, "%05d.png", f)));
                    if (session.player().getDead()) {
                        if (dead < 0) dead = f;
                        if (a.stopOnDeath() && f >= dead + a.deathGrace()) break;
                    }
                }
            }
            if (a.audio()) {
                Path raw = out.resolve("audio.pcm");
                var fmt = new javax.sound.sampled.AudioFormat(audio.outputSampleRate(), 16, 2, true, false);
                try (var ais = new javax.sound.sampled.AudioInputStream(Files.newInputStream(raw), fmt, Files.size(raw) / 4)) {
                    javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, out.resolve("audio.wav").toFile());
                }
                Files.delete(raw);
            }
        }
        return new Report(stepped, dead, out);
    }

    /** Events go only to this headless handler; no GLFW polling, desktop input or logical override. */
    static final class PresentationInput implements AutoCloseable {
        private final InputHandler input;
        private final int[] keys;
        private final boolean[] held = new boolean[8];

        PresentationInput(InputHandler input, int[] keys) {
            this.input = Objects.requireNonNull(input);
            this.keys = keys.clone();
            long assigned = Arrays.stream(keys).filter(k -> k >= 0).count();
            if (keys.length != held.length || Arrays.stream(keys).filter(k -> k >= 0).distinct().count() != assigned
                    || Arrays.stream(keys).anyMatch(k -> k < -1 || k > GLFW_KEY_LAST))
                throw new IllegalArgumentException("interactive capture requires valid, distinct assigned P1 keys");
        }

        static PresentationInput install(GameLoop loop) {
            var config = GameServices.configuration();
            var bindings = InputBindingFactory.fromConfig(config);
            var driver = new PresentationInput(new InputHandler(InputBindingFactory.supplier(config)),
                    new int[] { bindings.p1Up(), bindings.p1Down(), bindings.p1Left(), bindings.p1Right(),
                            bindings.p1A(), bindings.p1B(), bindings.p1C(), bindings.p1Start() });
            loop.setInputHandler(driver.input);
            driver.requireOwnership(loop);
            return driver;
        }

        static void validate(Bk2FrameInput frame) {
            if (frame != null && (frame.p2InputMask() != 0 || frame.p2ActionMask() != 0 || frame.p2StartPressed()
                    || frame.debugModeTogglePressed() || frame.debugShiftDown() || frame.debugControlDown()
                    || frame.debugAltDown() || frame.debugSuperDown()))
                throw new IllegalArgumentException("--interactive-pacing accepts P1 controller input only; frame " + frame.frameIndex());
        }

        void accept(Bk2FrameInput frame) {
            validate(frame);
            int mask = frame == null ? 0 : frame.p1InputMask(), actions = frame == null ? 0 : frame.p1ActionMask();
            boolean[] desired = { (mask & AbstractPlayableSprite.INPUT_UP) != 0,
                    (mask & AbstractPlayableSprite.INPUT_DOWN) != 0, (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0, (actions & ACTION_A) != 0,
                    (actions & ACTION_B) != 0, (actions & ACTION_C) != 0, frame != null && frame.p1StartPressed() };
            for (int k = 0; k < keys.length; k++) if (desired[k] && keys[k] < 0)
                throw new IllegalArgumentException("Requested P1 " + List.of("Up", "Down", "Left", "Right", "A", "B", "C", "Start").get(k)
                        + " is unbound at input frame " + frame.frameIndex());
            for (int k = 0; k < keys.length; k++) if (desired[k] != held[k]) {
                held[k] = desired[k]; // Retain ownership even when an event fails, so close still releases it.
                input.handleKeyEvent(keys[k], held[k] ? GLFW_PRESS : GLFW_RELEASE);
            }
        }

        void requireOwnership(GameLoop loop) {
            if (loop.getInputHandler() != input || input.hasLogicalOverride() || loop.externalFrameOrInputOwnerActive())
                throw new IllegalStateException("interactive capture requires exclusive native input ownership");
        }

        void step(GameLoop loop, Bk2FrameInput frame, boolean showTitleCard) {
            requireOwnership(loop);
            accept(frame);
            if (!showTitleCard) GameServices.level().skipPendingInitialTitleCardPresentation();
            var ui = GameServices.graphics().getUiRenderPipeline();
            if (ui != null && !loop.ownsGameplayFadeLifecycle()) ui.updateFade();
            loop.stepPresentationFrame();
            requireOwnership(loop);
        }

        @Override public void close() {
            RuntimeException failure = null;
            for (int k = 0; k < keys.length; k++) if (held[k]) {
                held[k] = false;
                try { input.handleKeyEvent(keys[k], GLFW_RELEASE); }
                catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
            }
            if (failure != null) throw failure;
        }
    }

    /** Engine.drawSpecialStage ordering; leave the ordinary level visible during entry fade-to-white. */
    static RgbaImage renderShowcase(GameplayCaptureSession session) {
        var loop = session.loop();
        var stage = loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE ? loop.getActiveSpecialStageProvider() : null;
        if (stage == null || stage.isEntryFadeToWhiteActive()) return session.render();
        var graphics = GameServices.graphics();
        graphics.runPendingRenderThreadTasks();
        stage.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);
        stage.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(session.width()));
        stage.draw();
        graphics.flush();
        var ui = graphics.getUiRenderPipeline();
        if (ui != null) ui.renderFadePass();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(session.width(), GameplayCaptureSession.HEIGHT);
    }

    private static final String SESSION_HEADER = "frame,mode,paused,lives,level_frame,vint,stealth_body,stealth_appendage,stealth_effects,head_scale,"
            + "no_rings,no_checkpoints,no_special,no_bonus,removed_monitors,speed_pct,audio_follows,checkpoint_idx,"
            + "monitors,bonus_type,special_provider,rings,x,y,dead,pcm_frames,logical_override,pacing_remainder,external_owner";

    private static String observeSession(GameplayCaptureSession s, int frame, int pcmFrames, PresentationInput input) {
        var player = s.player();
        var loop = s.loop();
        var levelManager = GameServices.levelOrNull();
        var objects = levelManager == null ? null : levelManager.getObjectManager();
        var world = SessionManager.getCurrentWorldSession();
        var levels = world == null ? null : WorldSessionPolicyAccess.getService(world, LevelMutatorPolicySource.class);
        var level = levels == null ? null : levels.policy();
        var gameplay = world == null ? null : WorldSessionPolicyAccess.getService(world, GameplayMutatorPolicySource.class);
        var speed = gameplay == null ? null : gameplay.policy();
        var pacing = world == null ? null : WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
        var policy = PlayableSpriteInternalAccess.mutatorPolicy(player);
        var state = GameServices.gameStateOrNull();
        var context = SessionManager.getCurrentGameplayMode();
        var bonus = context == null ? null : context.getActiveBonusStageProvider();
        long monitors = objects == null ? 0 : objects.activeObjectsOfType(AbstractMonitorObjectInstance.class)
                .stream().filter(m -> !m.isDestroyed()).count();
        String checkpoint = levelManager != null && levelManager.getCheckpointState() instanceof CheckpointState c
                ? String.valueOf(c.getLastCheckpointIndex()) : "";
        String removed = level == null ? "" : String.join("|", level.removedMonitorContents().stream().map(Enum::name).sorted().toList());
        return String.join(",", String.valueOf(frame), String.valueOf(loop.getCurrentGameMode()), String.valueOf(loop.isPaused()),
                state == null ? "" : String.valueOf(state.getLives()), levelManager == null ? "" : String.valueOf(levelManager.getFrameCounter()),
                objects == null ? "" : String.valueOf(objects.getVblaCounter()),
                policy == null ? "" : String.valueOf(policy.suppressBody()), policy == null ? "" : String.valueOf(policy.suppressAppendage()),
                policy == null ? "" : String.valueOf(policy.suppressAttachedEffects()), policy == null ? "" : String.valueOf(policy.headScalePercent()),
                level == null ? "" : String.valueOf(level.noRings()), level == null ? "" : String.valueOf(level.noCheckpoints()),
                level == null ? "" : String.valueOf(level.noSpecialStages()), level == null ? "" : String.valueOf(level.noBonusStages()), removed,
                speed == null ? "" : String.valueOf(speed.gameplaySpeedPercent()), speed == null ? "" : String.valueOf(speed.audioFollowsSpeed()),
                checkpoint, String.valueOf(monitors), bonus == null ? "" : String.valueOf(bonus.getActiveType()),
                loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE && loop.getActiveSpecialStageProvider() != null
                        ? loop.getActiveSpecialStageProvider().getClass().getSimpleName() : "",
                String.valueOf(player.getRingCount()), String.valueOf(player.getCentreX()), String.valueOf(player.getCentreY()),
                String.valueOf(player.getDead() ? 1 : 0), String.valueOf(pcmFrames),
                input == null ? "canonical" : String.valueOf(input.input.hasLogicalOverride()),
                pacing == null ? "" : String.valueOf(pacing.capture().remainder()), String.valueOf(loop.externalFrameOrInputOwnerActive()));
    }

    private static String observe(GameplayCaptureSession s, int f) {
        var p = s.player();
        var lm = GameServices.level();
        var om = lm == null ? null : lm.getObjectManager();
        int lost = 0, slotless = 0, collectible = 0, badniks = 0, nearDx = 0, nearDy = 0;
        String near = null;
        String ringfall = ",,", rebound = ",";
        if (om != null) {
            for (var r : om.activeObjectsOfType(LostRingObjectInstance.class)) {
                if (r.isDestroyed()) continue;
                lost++; if (r.getSlotIndex() < 0) slotless++; if (r.isLostRingCollectible()) collectible++;
            }
            for (var b : om.activeObjectsOfType(AbstractBadnikInstance.class)) {
                if (b.isDestroyed()) continue;
                badniks++;
                if (p != null) {
                    int dx = b.getX() - p.getCentreX(), dy = b.getY() - p.getCentreY();
                    if (near == null || Math.abs(dx) + Math.abs(dy) < Math.abs(nearDx) + Math.abs(nearDy)) {
                        near = b.getClass().getSimpleName(); nearDx = dx; nearDy = dy;
                    }
                }
            }
            var svc = om.getObjectServices();
            var src = svc == null ? null : WorldSessionPolicyAccess.getService(svc.worldSession(), GameplayMutatorPolicySource.class);
            if (src != null) {
                var g = src.policy();
                ringfall = g.ringfallPercent() + "," + g.ringfallCap() + "," + (g.ringfallFullInventory() ? 1 : 0);
                rebound = g.defeatReboundPercent() + "," + g.defeatVerticalCap();
            }
        }
        var pol = p == null ? null : PlayableSpriteInternalAccess.mutatorPolicy(p);
        var gs = GameServices.gameStateOrNull();
        var cam = GameServices.camera();
        String mode = String.valueOf(s.loop().getCurrentGameMode());
        return f + "," + mode + "," + (p == null ? "" : p.getRingCount()) + "," + (gs == null ? "" : gs.getScore()) + ","
                + lost + "," + slotless + "," + collectible + "," + (pol == null ? "" : pol.headScalePercent()) + ","
                + (pol == null ? "" : pol.dryAirGravityPercent()) + "," + ringfall + "," + rebound + "," + badniks + ","
                + (p == null ? ",,,,,," : p.getCentreX() + "," + p.getCentreY() + "," + p.getXSpeed() + "," + p.getYSpeed() + ","
                + (p.getAir() ? 1 : 0) + "," + (p.isHurt() ? 1 : 0) + "," + (p.getDead() ? 1 : 0)) + ","
                + (cam == null ? "," : cam.getX() + "," + cam.getY()) + ","
                + (near == null ? ",," : near + "," + nearDx + "," + nearDy);
    }

    static void requireDisplayFreeContext() {
        if (!SurfacelessEglContext.requested()) {
            throw new IllegalArgumentException("Mutator observations require -Dopenggf.headless.gl=surfaceless-egl; no desktop fallback");
        }
    }
}
