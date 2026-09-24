package com.openggf.tools;

import com.openggf.capture.FfmpegEncoder;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Pictures or films any section of gameplay: boots a zone/act on the production
 * path, optionally teleports the leader, drives it with a BizHawk input log (an
 * authored {@code Input Log.txt} from {@link InputLogAuthorTool} or a recorded
 * {@code .bk2}), and writes PNG frames, a per-frame state CSV, and an MP4 when
 * {@code ffmpeg} is on {@code PATH}.
 *
 * <p>The state CSV is the agent-facing product: it names the frame where the
 * interesting thing happened so only that PNG needs viewing. Originating task:
 * FBZ2 {@code $1DC0} squeeze capture, 2026-09-13.
 *
 * <pre>
 * mvn exec:java "-Dexec.mainClass=com.openggf.tools.GameplayCaptureTool" \
 *   "-Dexec.args=--game s3k --zone fbz --act 2 --x 0x1CF0 --y 0x76C \
 *     --input target/capture/squeeze.txt --out-dir target/capture/squeeze"
 * </pre>
 */
public final class GameplayCaptureTool {

    private GameplayCaptureTool() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        Report report = run(arguments);
        System.out.println(report.summary());
    }

    public static Report run(Arguments arguments) throws IOException {
        Bk2Movie movie = arguments.input() == null ? null
                : new Bk2MovieLoader().loadMovieOrInputLog(arguments.input());
        int scriptFrames = movie == null ? 0 : Math.max(0, movie.getFrameCount() - arguments.inputStart());
        int totalFrames = arguments.frames() != null ? arguments.frames() : arguments.settle() + scriptFrames;
        if (totalFrames <= 0) {
            throw new IllegalArgumentException("Nothing to capture: supply --input or --frames");
        }
        Path outDir = arguments.outDir();
        Path framesDir = outDir.resolve("frames");
        Files.createDirectories(framesDir);
        Path romPath = arguments.rom() != null ? arguments.rom()
                : TraceToolRomLocations.resolve(arguments.game(), GameServices.configuration(), Path.of(""));
        boolean donorActive = !"off".equals(arguments.donor());
        Path donorRom = !donorActive ? null : arguments.donorRom() != null ? arguments.donorRom()
                : TraceToolRomLocations.resolve(arguments.donor(), GameServices.configuration(), Path.of(""));
        GameplayCaptureSession.Settings settings = new GameplayCaptureSession.Settings(
                arguments.width(), arguments.mainCharacter(), arguments.sidekickCharacter(),
                arguments.donor(), donorRom, arguments.startX(), arguments.startY(), arguments.emeralds(), arguments.titleCard(),
                arguments.completeSpecialStage(), arguments.vIntRunCount(), arguments.cameraXSub(),
                arguments.reverseGravity());
        int zone = ZoneIds.resolve(arguments.game(), arguments.zone());
        int act = arguments.act();

        List<Path> pngs = new ArrayList<>();
        int deathFrame = -1;
        int lastFrame = -1;
        String stopReason = "frame budget reached";
        try (GameplayCaptureSession session = new GameplayCaptureSession(settings);
             BufferedWriter state = Files.newBufferedWriter(outDir.resolve("state.csv"), StandardCharsets.UTF_8)) {
            session.boot(romPath, zone, act, settings);
            state.write(GameplayCaptureSession.stateHeader());
            state.newLine();
            for (int frame = 0; frame < totalFrames; frame++) {
                int scriptIndex = frame - arguments.settle();
                int movieIndex = scriptIndex + arguments.inputStart();
                Bk2FrameInput input = movie != null && scriptIndex >= 0 && scriptIndex < scriptFrames
                        ? movie.getFrame(movieIndex) : null;
                session.step(input);
                lastFrame = frame;
                state.write(session.stateLine(frame, input));
                state.newLine();
                boolean wanted = frame >= arguments.captureFrom()
                        && ((frame - arguments.captureFrom()) % arguments.every() == 0
                        || arguments.stills().contains(frame));
                if (wanted) {
                    RgbaImage image = session.render();
                    Path png = framesDir.resolve(String.format(Locale.ROOT, "%05d.png", frame));
                    ScreenshotCapture.savePNG(image, png);
                    pngs.add(png);
                    if (arguments.stills().contains(frame)) {
                        ScreenshotCapture.savePNG(image, outDir.resolve(String.format(Locale.ROOT, "still-%05d.png", frame)));
                    }
                }
                if (session.player().getDead()) {
                    if (deathFrame < 0) {
                        deathFrame = frame;
                    }
                    if (arguments.stopOnDeath() && frame >= deathFrame + arguments.deathGrace()) {
                        stopReason = "leader died at frame " + deathFrame;
                        break;
                    }
                }
            }
        }
        Path video = null;
        String videoNote = "video skipped (--no-video)";
        if (arguments.video()) {
            Optional<Path> ffmpeg = FfmpegEncoder.findFfmpeg();
            if (ffmpeg.isEmpty()) {
                videoNote = "video skipped (ffmpeg not on PATH)";
            } else if (pngs.size() < 2) {
                videoNote = "video skipped (fewer than two frames captured)";
            } else {
                video = encodeVideo(ffmpeg.get(), framesDir, outDir.resolve("capture.mp4"), arguments);
                videoNote = video == null ? "video failed (see ffmpeg output above)" : "video " + video;
            }
        }
        return new Report(outDir, pngs.size(), lastFrame + 1, deathFrame, stopReason, video, videoNote,
                outDir.resolve("state.csv"));
    }

    private static Path encodeVideo(Path ffmpeg, Path framesDir, Path out, Arguments arguments) throws IOException {
        int scale = Math.max(1, arguments.scale());
        int fps = Math.max(1, arguments.fps() / Math.max(1, arguments.every()));
        List<String> command = new ArrayList<>(List.of(
                ffmpeg.toString(), "-y", "-loglevel", "error",
                "-framerate", Integer.toString(fps),
                "-pattern_type", "glob", "-i", framesDir.resolve("*.png").toString(),
                "-vf", "scale=iw*" + scale + ":ih*" + scale + ":flags=neighbor",
                "-pix_fmt", "yuv420p", "-crf", "18", out.toString()));
        Process process = new ProcessBuilder(command).inheritIO().start();
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return null;
        }
        return process.exitValue() == 0 && Files.exists(out) ? out : null;
    }

    /** What was captured and where; {@code summary()} is the console report. */
    public record Report(Path outDir, int pngCount, int framesStepped, int deathFrame, String stopReason,
                         Path video, String videoNote, Path stateCsv) {
        public String summary() {
            return "Captured " + pngCount + " PNG frame(s) over " + framesStepped + " stepped frame(s) -> "
                    + outDir.toAbsolutePath() + "\n"
                    + "Stop: " + stopReason + (deathFrame >= 0 ? " (death frame " + deathFrame + ")" : "") + "\n"
                    + "State: " + stateCsv.toAbsolutePath() + "\n"
                    + videoNote;
        }
    }

    /** Parsed CLI. Numbers accept decimal or {@code 0x} hex. */
    public record Arguments(String game, Path rom, String zone, int act, Integer startX, Integer startY, int width,
                            String mainCharacter, String sidekickCharacter, String donor, Path donorRom, Path input,
                            int settle, int inputStart, Integer frames, int captureFrom, int every, Set<Integer> stills,
                            boolean stopOnDeath, int deathGrace, boolean video, int scale, int fps,
                            Path outDir, String emeralds, boolean titleCard, boolean completeSpecialStage,
                            Integer vIntRunCount, Integer cameraXSub, boolean reverseGravity) {

        public static Arguments parse(String[] argv) {
            String game = "s3k";
            Path rom = null;
            String zone = null;
            Integer act = null;
            Integer startX = null;
            Integer startY = null;
            int width = 320;
            String main = "sonic";
            String sidekick = "";
            String donor = "off";
            Path donorRom = null;
            Path input = null;
            int settle = 0;
            int inputStart = 0;
            Integer frames = null;
            int captureFrom = 0;
            int every = 1;
            Set<Integer> stills = new LinkedHashSet<>();
            boolean stopOnDeath = true;
            int deathGrace = 60;
            boolean video = true;
            int scale = 3;
            int fps = 60;
            Path outDir = null;
            String emeralds = null;
            boolean reverseGravity = false;
            Integer vIntRunCount = null;
            Integer cameraXSub = null;
            boolean titleCard = false;
            boolean completeSpecialStage = false;
            for (int i = 0; i < argv.length; i++) {
                String flag = argv[i];
                switch (flag) {
                    case "--game" -> game = value(argv, ++i, flag).toLowerCase(Locale.ROOT);
                    case "--rom" -> rom = Path.of(value(argv, ++i, flag));
                    case "--zone" -> zone = value(argv, ++i, flag);
                    case "--act" -> act = number(value(argv, ++i, flag), flag);
                    case "--x" -> startX = number(value(argv, ++i, flag), flag);
                    case "--y" -> startY = number(value(argv, ++i, flag), flag);
                    case "--width" -> width = number(value(argv, ++i, flag), flag);
                    case "--main" -> main = value(argv, ++i, flag);
                    case "--sidekick" -> {
                        String raw = value(argv, ++i, flag);
                        sidekick = raw.equalsIgnoreCase("none") ? "" : raw;
                    }
                    case "--donor" -> donor = value(argv, ++i, flag).toLowerCase(Locale.ROOT);
                    case "--donor-rom" -> donorRom = Path.of(value(argv, ++i, flag));
                    case "--input" -> input = Path.of(value(argv, ++i, flag));
                    case "--settle" -> settle = number(value(argv, ++i, flag), flag);
                    case "--input-start" -> inputStart = number(value(argv, ++i, flag), flag);
                    case "--frames" -> frames = number(value(argv, ++i, flag), flag);
                    case "--capture-from" -> captureFrom = number(value(argv, ++i, flag), flag);
                    case "--every" -> every = number(value(argv, ++i, flag), flag);
                    case "--stills" -> {
                        for (String token : value(argv, ++i, flag).split(",")) {
                            if (!token.isBlank()) {
                                stills.add(number(token.trim(), flag));
                            }
                        }
                    }
                    case "--stop-on-death" -> stopOnDeath = Boolean.parseBoolean(value(argv, ++i, flag));
                    case "--death-grace" -> deathGrace = number(value(argv, ++i, flag), flag);
                    case "--no-video" -> video = false;
                    case "--scale" -> scale = number(value(argv, ++i, flag), flag);
                    case "--fps" -> fps = number(value(argv, ++i, flag), flag);
                    case "--out-dir" -> outDir = Path.of(value(argv, ++i, flag));
                    case "--emeralds" -> emeralds = value(argv, ++i, flag);
                    case "--reverse-gravity" -> reverseGravity = true;
                    case "--title-card" -> titleCard = true;
                    case "--vint-run-count" -> vIntRunCount = number(value(argv, ++i, flag), flag);
                    case "--camera-x-sub" -> cameraXSub = number(value(argv, ++i, flag), flag);
                    case "--complete-special-stage" -> completeSpecialStage = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + flag);
                }
            }
            if (zone == null || act == null) {
                throw new IllegalArgumentException("--zone and --act are required");
            }
            if (outDir == null) {
                throw new IllegalArgumentException("--out-dir is required");
            }
            if (every <= 0) {
                throw new IllegalArgumentException("--every must be positive");
            }
            if (act < 1) {
                throw new IllegalArgumentException("--act is one-based (1 or 2)");
            }
            return new Arguments(game, rom, zone, act - 1, startX, startY, width, main, sidekick, donor, donorRom, input,
                    settle, inputStart, frames, captureFrom, every, Set.copyOf(stills), stopOnDeath, deathGrace,
                    video, scale, fps, outDir, emeralds, titleCard, completeSpecialStage,
                    vIntRunCount, cameraXSub, reverseGravity);
        }

        private static String value(String[] argv, int index, String flag) {
            return CliArguments.requireValue(argv, index, flag);
        }

        static int number(String raw, String flag) {
            String text = raw.trim().toLowerCase(Locale.ROOT);
            try {
                if (text.startsWith("0x")) {
                    return Integer.parseInt(text.substring(2), 16);
                }
                if (text.startsWith("$")) {
                    return Integer.parseInt(text.substring(1), 16);
                }
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number for " + flag + ": " + raw);
            }
        }
    }

    /** Zone ids by number or by the constant names of the per-game id classes. */
    static final class ZoneIds {
        private ZoneIds() {
        }

        static int resolve(String game, String zone) {
            String text = zone.trim();
            if (text.matches("(?i)(0x|\\$)?[0-9a-f]+") && (text.matches("\\d+") || text.matches("(?i)(0x|\\$).*"))) {
                return Arguments.number(text, "--zone");
            }
            String className = switch (game) {
                case "s3k" -> "com.openggf.game.sonic3k.constants.Sonic3kZoneIds";
                case "s1" -> "com.openggf.game.sonic1.constants.Sonic1Constants";
                case "s2" -> "com.openggf.game.sonic2.Sonic2LevelEventManager";
                default -> throw new IllegalArgumentException("Unknown game: " + game);
            };
            String constant = "ZONE_" + text.toUpperCase(Locale.ROOT);
            try {
                return Class.forName(className).getField(constant).getInt(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Unknown zone '" + zone + "' for " + game
                        + " (use a number or a name such as fbz)");
            }
        }
    }
}
