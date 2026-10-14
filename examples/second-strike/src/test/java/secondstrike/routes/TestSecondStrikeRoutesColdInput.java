package secondstrike.routes;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.AizMinibossInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.GameplayCaptureSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded cold-input research driver. Reads authentic pad rows only; never reads
 * trace physics/aux, seeds gameplay fields, or writes a native position.
 * Args: absolute ROM, movie/input log, new output dir, input-start, frames,
 * optional packaged mod. Its observed milestones distinguish physical AIZ2's
 * fire reload from real first-act boss/results/next-act control.
 * Use -Dsecondstrike.route.video=true for continuous FFV1/FLAC capture;
 * -Dsecondstrike.route.stills=6999,7004 selects extra native PNG witnesses.
 * The default retains lightweight milestone-only rendering.
 */
public final class TestSecondStrikeRoutesColdInput {
    public static void main(String[] args) throws Exception {
        if (args.length < 5 || args.length > 6)
            throw new IllegalArgumentException("ROM INPUT NEW_OUT INPUT_START FRAMES [MOD_JAR]");
        if (!"surfaceless-egl".equals(System.getProperty("openggf.headless.gl")))
            throw new IllegalStateException("Require explicit -Dopenggf.headless.gl=surfaceless-egl");
        if (System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Unset DISPLAY and WAYLAND_DISPLAY before cold authoring");
        Path output = Path.of(args[2]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output exists: " + output);
        Files.createDirectories(output);
        Logger.getLogger("").setLevel(Level.WARNING);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(args[1]));
        int inputStart = Integer.parseInt(args[3]);
        int frames = Integer.parseInt(args[4]);
        var settings = new GameplayCaptureSession.Settings(320,
                System.getProperty("secondstrike.route.main", "sonic"),
                System.getProperty("secondstrike.route.sidekick", "tails"), "off", null, null, null);
        boolean inputStarted = false, fireSeen = false, bossSeen = false, defeated = false, results = false;
        int held = 0, cursor = inputStart;
        String previousBoss = "";
        int previousCheckpoint = 0;
        int previousFollowup = -1;
        Object previousLevel = null;
        java.util.Set<String> observedEnemyClasses = new java.util.HashSet<>();
        var liveAuthor = new TestSecondStrikeRoutesLiveInput();
        int authorFrom = Integer.getInteger("secondstrike.route.author_from", Integer.MAX_VALUE);
        int authorUntil = Integer.getInteger("secondstrike.route.author_until", Integer.MAX_VALUE);
        var stills = java.util.Arrays.stream(System.getProperty("secondstrike.route.stills", "").split(","))
                .filter(value -> !value.isBlank()).map(Integer::parseInt)
                .collect(java.util.stream.Collectors.toSet());
        boolean video = Boolean.getBoolean("secondstrike.route.video");
        try (var session = new GameplayCaptureSession(settings);
             var state = Files.newBufferedWriter(output.resolve("state.csv"));
             var authored = Files.newBufferedWriter(output.resolve("admitted.script"))) {
            // The standard capture session omits intros by default. Keep the
            // native plane/Knuckles opening and its live input-release owner.
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            if (args.length == 6) session.applyMod(Path.of(args[5]));
            if (video) session.enableAudio();
            session.boot(Path.of(args[0]), 0, 0, settings);
            String graphics = "backend=surfaceless-egl vendor="
                    + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR)
                    + " renderer=" + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)
                    + " version=" + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);
            System.out.println("COLD gpu " + graphics);
            Files.writeString(output.resolve("graphics.txt"), graphics + "\n");
            state.write(GameplayCaptureSession.stateHeader() + ",actual_act,apparent_act,boss,results,checkpoint,bonus_stars,followup_timer,flares,breath_flames,enemies\n");
            authored.write("# Ordinary AIZ1 native intro through first-act boss/results and playable Act2\n");
            authored.write("# Source pad rows: " + Path.of(args[1]).getFileName() + ", input-start=" + inputStart + "\n");
            try (var film = video ? new NativeFilm(output, settings.width()) : null) {
                for (int frame = 0; frame < frames; frame++) {
                    Bk2FrameInput pad = cursor < movie.getFrameCount() ? movie.getFrame(cursor) : null;
                    if (!inputStarted && pad != null && pad.p1InputMask() != 0
                            && !GameServices.camera().isLevelStarted()) {
                        pad = null;
                        held++;
                    } else {
                        if (pad != null && pad.p1InputMask() != 0) inputStarted = true;
                        cursor++;
                    }
                    if (frame >= authorFrom && frame < authorUntil
                            && GameServices.camera().isLevelStarted()) {
                        pad = liveAuthor.next(session.player(), frame);
                    }
                    session.step(pad);
                    // Prepared load publication must be serviced even when the
                    // research driver does not draw every ordinary gameplay frame.
                    GameServices.graphics().runPendingRenderThreadTasks();
                    if (film != null) film.capture(session, frame);
                    var manager = GameServices.level();
                    var objects = manager.getObjectManager().getActiveObjects();
                    if (manager.getCurrentLevel() != previousLevel) {
                        previousLevel = manager.getCurrentLevel();
                        long authoredPlacements = manager.getCurrentLevel().getObjects().stream()
                                .filter(spawn -> "second-strike".equals(spawn.ownerModId())).count();
                        System.out.println("COLD load frame=" + frame + " act=" + manager.getCurrentAct()
                                + " nativeClass=" + previousLevel.getClass().getSimpleName()
                                + " rings=" + manager.getCurrentLevel().getRings().size() + " authoredPlacements=" + authoredPlacements);
                    }
                    var boss = objects.stream().filter(AizMinibossInstance.class::isInstance)
                            .map(AizMinibossInstance.class::cast).findFirst().orElse(null);
                    boolean resultNow = objects.stream().anyMatch(S3kResultsScreenObjectInstance.class::isInstance);
                    int checkpoint = manager.getCheckpointState().getStarPostActivationMark();
                    long bonusStars = objects.stream().filter(Sonic3kStarPostBonusStarChild.class::isInstance).count();
                    if (checkpoint != previousCheckpoint) {
                        System.out.println("COLD checkpoint frame=" + frame + " index=" + checkpoint
                                + " rings=" + session.player().getRingCount() + " bonusStars=" + bonusStars);
                        previousCheckpoint = checkpoint;
                    }
                    String bossDetails = boss == null ? "" : boss.traceDebugDetails();
                    int followup = readInt(boss, "difficultyFollowupTimer", -1);
                    long flares = objects.stream().filter(object -> object.getClass().getSimpleName()
                            .equals("AizMinibossBarrelShotFlareChild")).count();
                    long flames = objects.stream().filter(object -> object.getClass().getSimpleName()
                            .equals("AizMinibossFlameChild")).count();
                    if (followup > 0 && previousFollowup <= 0) {
                        System.out.println("COLD phase2-tell frame=" + frame + " timer=" + followup
                                + " flares=" + flares + " boss=" + bossDetails);
                        ScreenshotCapture.savePNG(session.render(), output.resolve("phase2-tell-" + frame + ".png"));
                    }
                    if (previousFollowup > 0 && followup < 0 && boss != null && !bossDetails.contains("hp=0")) {
                        System.out.println("COLD phase2-followup frame=" + frame + " flames=" + flames + " boss=" + bossDetails);
                        ScreenshotCapture.savePNG(session.render(), output.resolve("phase2-followup-" + frame + ".png"));
                    }
                    previousFollowup = followup;
                    int observationFrame = frame;
                    String enemies = objects.stream().filter(object -> object.getClass().getName().startsWith("secondstrike.enemies."))
                            .map(object -> {
                                String name = object.getClass().getSimpleName();
                                if (observedEnemyClasses.add(name)) System.out.println("COLD enemy-admitted frame=" + observationFrame + " class=" + name);
                                return name + "@" + object.getX() + ":" + object.getY()
                                        + "/" + readInt(object, "phase", -1)
                                        + "/hp=" + readInt(object, "remainingHits", -1);
                            }).collect(java.util.stream.Collectors.joining(";"));
                    if (manager.getCurrentAct() == 1 && !fireSeen) {
                        fireSeen = true;
                        System.out.println("COLD fire-reload frame=" + frame + " movie=" + cursor
                                + " apparent=" + manager.getApparentAct() + " rings=" + session.player().getRingCount());
                    }
                    if (boss != null) bossSeen = true;
                    if (bossDetails.contains("hp=0")) defeated = true;
                    if (resultNow) results = true;
                    state.write(session.stateLine(frame, pad) + "," + manager.getCurrentAct() + ","
                            + manager.getApparentAct() + ",\"" + bossDetails + "\"," + (resultNow ? 1 : 0)
                            + "," + checkpoint + "," + bonusStars + "," + followup + "," + flares + "," + flames
                            + ",\"" + enemies + "\"\n");
                    authored.write("1 " + buttons(pad, true) + " / " + buttons(pad, false) + "\n");
                    if (stills.contains(frame))
                        ScreenshotCapture.savePNG(session.render(), output.resolve("still-" + frame + ".png"));
                    if (frame % 1000 == 0 || resultNow && !previousBoss.equals("RESULTS")
                            || !bossDetails.isEmpty() && !bossDetails.equals(previousBoss) && frame % 60 == 0) {
                        System.out.println("COLD frame=" + frame + " movie=" + cursor + " p="
                                + session.player().getCentreX() + "," + session.player().getCentreY()
                                + " rings=" + session.player().getRingCount() + " act=" + manager.getCurrentAct()
                                + "/" + manager.getApparentAct() + " mode=" + session.loop().getCurrentGameMode()
                                + " boss=" + bossDetails + " results=" + resultNow);
                    }
                    previousBoss = resultNow ? "RESULTS" : bossDetails;
                    if (session.player().getDead()) {
                        ScreenshotCapture.savePNG(session.render(), output.resolve("death.png"));
                        System.out.println("COLD STOP death frame=" + frame + " movie=" + cursor);
                        break;
                    }
                    if (fireSeen && bossSeen && defeated && results && manager.getCurrentAct() == 1
                            && manager.getApparentAct() == 1 && session.loop().getCurrentGameMode() == GameMode.LEVEL
                            && !session.player().isObjectControlled() && !session.player().isControlLocked()
                            && session.player().getCentreX() > 0x18A0) {
                        ScreenshotCapture.savePNG(session.render(), output.resolve("next-act.png"));
                        System.out.println("COLD SUCCESS frame=" + frame + " movie=" + cursor + " introHeld=" + held
                                + " nativeAct2Playable=true");
                        if (film != null) film.finish();
                        return;
                    }
                }
                ScreenshotCapture.savePNG(session.render(), output.resolve("terminal.png"));
                System.out.println("COLD INCOMPLETE cursor=" + cursor + " held=" + held + " fire=" + fireSeen
                        + " boss=" + bossSeen + " defeated=" + defeated + " results=" + results);
                if (film != null) film.finish();
                throw new IllegalStateException("Cold input did not demonstrate the complete advertised first-act boundary");
            }
        }
    }

    /** Optional continuous native picture/PCM witness; no gameplay setup or writes. */
    private static final class NativeFilm implements AutoCloseable {
        private final Path output;
        private final com.openggf.audio.LiveCaptureAudioHandle audio;
        private final com.openggf.capture.GlReadPixelsGrabber grabber;
        private final com.openggf.capture.FfmpegEncoder encoder;
        private final short[] samples;
        private long frames, stereoFrames, nonzeroSamples;
        private int peak;
        private boolean finished;

        NativeFilm(Path output, int width) throws Exception {
            this.output = output;
            var manager = GameServices.audio();
            if (manager.presentationFrameRate() != 60)
                throw new IllegalStateException("Native audio presentation must be 60 Hz");
            var ffmpeg = com.openggf.capture.FfmpegEncoder.findFfmpeg()
                    .orElseThrow(() -> new IllegalStateException("Video requested without ffmpeg"));
            audio = manager.beginLiveCaptureAudio(60);
            grabber = new com.openggf.capture.GlReadPixelsGrabber(width, 224);
            encoder = new com.openggf.capture.FfmpegEncoder(ffmpeg.toString(), 3);
            try {
                if (audio.frameRate() != 60 || audio.sampleRate() != manager.outputSampleRate())
                    throw new IllegalStateException("Native capture audio clock changed");
                encoder.open(output.resolve("cold-campaign.mkv"), width, 224, 60, audio.sampleRate());
            } catch (Exception failure) {
                encoder.abort();
                grabber.close();
                audio.close();
                throw failure;
            }
            samples = new short[audio.maxStereoFramesPerPacket() * 2];
        }

        void capture(GameplayCaptureSession session, int frame) throws Exception {
            // Exactly one ordinary outer audio boundary and one drain. The
            // manager-owned handle survives the native fire producer rebuild.
            session.loop().presentOuterFrame(false, false);
            int count = audio.drainPresentationFrame(samples);
            session.renderFrame();
            encoder.encode(new com.openggf.capture.CapturedFrame(grabber.grab(), grabber.width(),
                    grabber.height(), samples, count, frame));
            frames++;
            stereoFrames += count;
            for (int i = 0; i < count * 2; i++) {
                if (samples[i] != 0) nonzeroSamples++;
                peak = Math.max(peak, Math.abs((int) samples[i]));
            }
        }

        void finish() throws Exception {
            Path file = encoder.finish();
            finished = true;
            String audit = "video=" + file + "\nframes=" + frames + "\naudioDrains=" + frames
                    + "\nsampleRate=" + audio.sampleRate() + "\nstereoFrames=" + stereoFrames
                    + "\nnonzeroSamples=" + nonzeroSamples + "\npeak=" + peak + "\n";
            Files.writeString(output.resolve("capture.txt"), audit);
            System.out.println("COLD VIDEO frames=" + frames + " stereoFrames=" + stereoFrames
                    + " nonzeroSamples=" + nonzeroSamples + " peak=" + peak + " file=" + file);
        }

        @Override public void close() {
            if (!finished) encoder.abort();
            grabber.close();
            audio.close();
        }
    }

    /** Trusted research observer only: never writes an engine or creator field. */
    private static int readInt(Object owner, String name, int fallback) {
        if (owner == null) return fallback;
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(owner);
            } catch (NoSuchFieldException missing) {
                // An older stock baseline may legitimately lack the optional policy state.
            } catch (IllegalAccessException inaccessible) {
                throw new IllegalStateException("Cannot inspect owned research field " + name, inaccessible);
            }
        }
        return fallback;
    }

    private static String buttons(Bk2FrameInput pad, boolean p1) {
        if (pad == null) return "-";
        int mask = p1 ? pad.p1InputMask() : pad.p2InputMask();
        int actions = p1 ? pad.p1ActionMask() : pad.p2ActionMask();
        boolean start = p1 ? pad.p1StartPressed() : pad.p2StartPressed();
        StringBuilder result = new StringBuilder();
        if ((mask & 1) != 0) result.append("U+");
        if ((mask & 2) != 0) result.append("D+");
        if ((mask & 4) != 0) result.append("L+");
        if ((mask & 8) != 0) result.append("R+");
        if ((actions & 1) != 0) result.append("A+");
        if ((actions & 2) != 0) result.append("B+");
        if ((actions & 4) != 0) result.append("C+");
        if (start) result.append("S+");
        return result.isEmpty() ? "-" : result.substring(0, result.length() - 1);
    }
}
