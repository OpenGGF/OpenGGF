package com.openggf.tools;

import com.openggf.capture.FfmpegEncoder;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.media.CdHostedMedia;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capture tools film Sonic CD from the user's disc ({@code -Dsoniccd.media.root}) and its native fixtures
 * ({@code rehost-fixtures/<payload>/}: v5 trace and {@code movie.bk2}, user-local, beside the media root or
 * {@code -Dsoniccd.native.root}). Each filmed frame's player position is compared with the native trace's row:
 * the engine's state is checked against native, not against itself. Media-gated: skipped without the disc or
 * the fixture; the GL checks need {@code -Dopenggf.headless.gl=surfaceless-egl}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestSonicCdCaptureTools {
    @TempDir
    Path temp;
    private String callerGl;

    /**
     * The capture tools render offscreen; display-free surfaceless EGL, as the Sonic CD picture tests use. Every check
     * that boots draws, each on its own HeadlessGameBoot in one JVM (the graphics owner forgets a closed context's GL
     * objects at {@code GraphicsManager.init}).
     */
    @BeforeEach
    void selectSurfacelessGl() {
        callerGl = System.getProperty("openggf.headless.gl");
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
    }

    @AfterEach
    void restoreCallerGl() {
        if (callerGl == null) System.clearProperty("openggf.headless.gl");
        else System.setProperty("openggf.headless.gl", callerGl);
    }

    private static Path nativeFile(String relative) throws java.io.IOException {
        CdTestMedia.usaCue();
        Path file = CdTestMedia.nativePath(relative).orElse(null);
        Assumptions.assumeTrue(file != null, "Set soniccd.native.root (or soniccd.media.root) for " + relative);
        Assumptions.assumeTrue(Files.exists(file), "Native fixture absent: " + file);
        return file;
    }

    /** TraceCaptureTool films a native trace directory named by path; every full row's player equals native. */
    @Test
    @Order(1)
    void traceCaptureFilmsAFixtureWhoseFramesFollowTheNativeTrace() throws Exception {
        Path traceDir = nativeFile("rehost-fixtures/R11A/trace");
        Path out = temp.resolve("trace");
        HeadlessGameBoot boot = runTraceCapture(new String[] {"--trace", traceDir.toString(), "--clip", "frames:0:299",
                "--width", "320", "--no-ghosts", "--no-sprite-limits", "--scale", "1", "--codec", "ffv1",
                "--out-dir", out.toString()});
        boot.close();
        try (Stream<Path> files = Files.list(out)) {
            assertEquals(1, files.filter(p -> p.toString().endsWith(".mov") || p.toString().endsWith(".mkv")).count());
        }
        TraceData trace = TraceData.load(traceDir);
        List<String> state = Files.readAllLines(out.resolve(TraceCaptureStateWriter.FILE_NAME), StandardCharsets.UTF_8);
        assertEquals(TraceCaptureStateWriter.HEADER, state.getFirst());
        assertEquals(301, state.size(), "one state row per filmed frame");
        List<String> columns = List.of(state.getFirst().split(","));
        int x = columns.indexOf("x"), y = columns.indexOf("y"), row = columns.indexOf("trace_row");
        int compared = 0;
        for (String line : state.subList(1, state.size())) {
            String[] c = line.split(",", -1);
            int r = Integer.parseInt(c[row]);
            TraceFrame expected = trace.getFrame(r);
            if (r > 0 && expected.gameplayFrameCounter() == trace.getFrame(r - 1).gameplayFrameCounter()) continue;
            assertEquals(expected.x() & 0xFFFF, Integer.parseInt(c[x]), "x at trace row " + r);
            assertEquals(expected.y() & 0xFFFF, Integer.parseInt(c[y]), "y at trace row " + r);
            compared++;
        }
        assertTrue(compared > 250, "rows compared: " + compared);
    }

    @Test
    void traceCaptureSkipsNativeLagRowsAndTheirAudio() throws Exception {
        Path traceDir = nativeFile("rehost-long/R31A/trace");
        Path out = temp.resolve("lag-free");
        HeadlessGameBoot boot = runTraceCapture(new String[] {"--trace", traceDir.toString(),
                "--clip", "frames:2884:2982", "--skip-lag-frames", "--width", "320",
                "--no-ghosts", "--scale", "1", "--codec", "ffv1", "--out-dir", out.toString()});
        boot.close();

        List<String> state = Files.readAllLines(out.resolve(TraceCaptureStateWriter.FILE_NAME));
        int rowColumn = List.of(state.getFirst().split(",")).indexOf("trace_row");
        List<Integer> filmed = state.stream().skip(1)
                .map(line -> Integer.parseInt(line.split(",", -1)[rowColumn])).toList();
        List<Integer> lagRows = List.of(2906, 2911, 2938, 2962, 2965);
        List<Integer> expected = IntStream.rangeClosed(2884, 2982).boxed()
                .filter(row -> !lagRows.contains(row)).toList();

        Path video;
        try (Stream<Path> files = Files.list(out)) {
            video = files.filter(path -> path.toString().endsWith(".mkv") || path.toString().endsWith(".mov"))
                    .findFirst().orElseThrow();
        }
        String ffmpeg = FfmpegEncoder.findFfmpeg().map(Path::toString).orElse("ffmpeg");
        Process decode = new ProcessBuilder(ffmpeg, "-v", "error", "-i", video.toString(),
                "-map", "0:a:0", "-f", "s16le", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] pcm = decode.getInputStream().readAllBytes();
        assertEquals(0, decode.waitFor(), "decode the exported audio");
        int bytesPerFrame = (48_000 / 60) * 2 * Short.BYTES;
        assertAll(
                () -> assertEquals(expected, filmed, "the clip omits only native lag rows"),
                () -> assertEquals(expected.size() * bytesPerFrame, pcm.length,
                        "the clip has one stereo audio frame per video frame"));
    }

    @Test
    void traceCaptureKeepsGameplayWhenTheRecorderLagFlagIsLate() throws Exception {
        Path traceDir = nativeFile("rehost-long/R51B/trace");
        Path out = temp.resolve("late-lag-flag");
        HeadlessGameBoot boot = runTraceCapture(new String[] {"--trace", traceDir.toString(),
                "--clip", "frames:1576:1592", "--skip-lag-frames", "--width", "320",
                "--no-ghosts", "--scale", "1", "--codec", "ffv1", "--out-dir", out.toString()});
        boot.close();

        List<String> state = Files.readAllLines(out.resolve(TraceCaptureStateWriter.FILE_NAME));
        int rowColumn = List.of(state.getFirst().split(",")).indexOf("trace_row");
        List<Integer> filmed = state.stream().skip(1)
                .map(line -> Integer.parseInt(line.split(",", -1)[rowColumn])).toList();

        Path video;
        try (Stream<Path> files = Files.list(out)) {
            video = files.filter(path -> path.toString().endsWith(".mkv") || path.toString().endsWith(".mov"))
                    .findFirst().orElseThrow();
        }
        String ffmpeg = FfmpegEncoder.findFfmpeg().map(Path::toString).orElse("ffmpeg");
        Process decode = new ProcessBuilder(ffmpeg, "-v", "error", "-i", video.toString(),
                "-map", "0:v:0", "-f", "framemd5", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        List<String> hashes = new String(decode.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines().filter(line -> !line.startsWith("#"))
                .map(line -> line.substring(line.lastIndexOf(',') + 1).strip()).toList();
        assertEquals(0, decode.waitFor(), "decode the exported video");
        assertEquals(filmed.size(), hashes.size(), "one picture per exported state row");

        TraceData trace = TraceData.load(traceDir);
        List<Integer> gameplayRows = IntStream.rangeClosed(1576, 1592)
                .filter(row -> trace.getFrame(row).gameplayFrameCounter()
                        != trace.getFrame(row - 1).gameplayFrameCounter()).boxed().toList();
        List<Integer> duplicateRows = IntStream.range(1, hashes.size())
                .filter(frame -> hashes.get(frame).equals(hashes.get(frame - 1)))
                .mapToObj(filmed::get).toList();
        assertAll(
                () -> assertEquals(gameplayRows, filmed, "film every gameplay row in the clip"),
                () -> assertEquals(List.of(), duplicateRows, "no consecutive pictures repeat"));
    }

    /**
     * A clip that runs past the warp fixture's last row hands the game to its production loop: the stage is left
     * (fade, title host) and the warp's program screen runs, with no player on it. The first rows still equal the
     * trace, and the picture of the last trace row is the level's.
     */
    @Test
    void traceCaptureContinuesPastTheTraceThroughTheWarp() throws Exception {
        Path traceDir = nativeFile("rehost-fixtures-warp/R11A/trace");
        int rows = TraceData.load(traceDir).frameCount();
        Path out = temp.resolve("warp");
        HeadlessGameBoot boot = runTraceCapture(new String[] {"--trace", traceDir.toString(), "--clip",
                "frames:" + (rows - 40) + ":" + (rows + 449), "--width", "400", "--no-ghosts", "--no-sprite-limits",
                "--scale", "1", "--codec", "ffv1", "--out-dir", out.toString()});
        boot.close();
        List<String> state = Files.readAllLines(out.resolve(TraceCaptureStateWriter.FILE_NAME), StandardCharsets.UTF_8);
        List<String> columns = List.of(state.getFirst().split(","));
        int mode = columns.indexOf("mode"), x = columns.indexOf("x"), row = columns.indexOf("trace_row");
        assertEquals(491, state.size(), "one state row per filmed frame, past the trace as well");
        List<String> modes = new ArrayList<>();
        for (String line : state.subList(1, state.size())) {
            String[] c = line.split(",", -1);
            if (modes.isEmpty() || !modes.getLast().equals(c[mode])) modes.add(c[mode]);
            if (!c[mode].equals("LEVEL")) {
                assertEquals("", c[x], "a screen with no player has empty player columns (row " + c[row] + ")");
            }
        }
        assertEquals(List.of("LEVEL", "TITLE_SCREEN", "OPTIONS"), modes.subList(0, 3),
                "the warp leaves the stage through the title host into its program screen");
    }

    /** Only a stage with a declared entry can run past its trace: the others are refused, naming the stage. */
    @Test
    void traceCaptureRefusesToRunPastATraceFromAStageWithNoEntry() throws Exception {
        Path traceDir = nativeFile("rehost-fixtures/R11B/trace");
        int rows = TraceData.load(traceDir).frameCount();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> runTraceCapture(new String[] {
                "--trace", traceDir.toString(), "--clip", "frames:" + (rows - 3) + ":" + (rows + 2),
                "--width", "320", "--no-ghosts", "--scale", "1", "--codec", "ffv1",
                "--out-dir", temp.resolve("no-entry").toString()}));
        assertTrue(failure.getMessage().contains("Palmtree Panic act 1 Present"), failure.getMessage());
    }

    /** A clip may not run more than the tail limit past the trace. */
    @Test
    void traceCaptureRefusesAClipFarPastTheTrace() throws Exception {
        Path traceDir = nativeFile("rehost-fixtures/R11A/trace");
        int rows = TraceData.load(traceDir).frameCount();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> runTraceCapture(new String[] {
                "--trace", traceDir.toString(), "--clip", "frames:0:" + (rows + 2000), "--width", "320",
                "--no-ghosts", "--scale", "1", "--codec", "ffv1", "--out-dir", temp.resolve("far").toString()}));
        assertTrue(failure.getMessage().contains("clip end exceeds the trace row count"), failure.getMessage());
    }

    /** A movie that does not drive the trace stops the capture with the row and both inputs. */
    @Test
    void traceCaptureRejectsAMovieThatDoesNotDriveTheTrace() throws Exception {
        Path traceDir = nativeFile("rehost-fixtures/R11A/trace");
        Path otherMovie = nativeFile("rehost-fixtures/R11B/movie.bk2");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> runTraceCapture(new String[] {
                "--trace", traceDir.toString(), "--movie", otherMovie.toString(), "--clip", "frames:0:599",
                "--width", "320", "--no-ghosts", "--scale", "1", "--codec", "ffv1",
                "--out-dir", temp.resolve("misaligned").toString()}));
        assertTrue(failure.getMessage().startsWith("Input alignment error at trace row"), failure.getMessage());
    }

    /** A trace naming a game no stock module hosts is refused with that game's name, before any boot. */
    @Test
    void traceCaptureNamesAnUnknownTraceGame() throws Exception {
        Path traceDir = nativeFile("rehost-fixtures/R11A/trace");
        Path copy = Files.createDirectories(temp.resolve("unknown-game/trace"));
        Files.writeString(copy.resolve("metadata.json"),
                Files.readString(traceDir.resolve("metadata.json")).replace("\"game\": \"scd\"", "\"game\": \"xyz\""));
        Files.copy(traceDir.resolve("physics.csv"), copy.resolve("physics.csv"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> HostedTraceCapture.resolve(copy.toString(), null));
        assertTrue(failure.getMessage().contains("names game 'xyz'"), failure.getMessage());
    }

    /**
     * GameplayCaptureTool boots a Sonic CD stage from the disc and plays an input log. Fed the native movie's rows
     * with the trace's lag rows dropped (one engine frame per native logic frame), every frame's player equals the
     * native row it stands for.
     */
    @Test
    void gameplayCapturePlaysAStageFromTheDiscAsNative() throws Exception {
        Path take = nativeFile("rehost-fixtures/R11A/trace").getParent();
        TraceData trace = TraceData.load(take.resolve("trace"));
        List<Integer> logicRows = new ArrayList<>();
        for (int r = 1; r < trace.frameCount(); r++) {
            if (trace.getFrame(r).gameplayFrameCounter() != trace.getFrame(r - 1).gameplayFrameCounter()) logicRows.add(r);
        }
        Path input = temp.resolve("r11a.txt");
        try (ZipFile movie = new ZipFile(take.resolve("movie.bk2").toFile())) {
            List<String> log = new String(movie.getInputStream(movie.getEntry("Input Log.txt")).readAllBytes(),
                    StandardCharsets.UTF_8).lines().toList();
            List<String> rows = log.stream().filter(l -> l.startsWith("|")).toList();
            List<String> out = new ArrayList<>(log.stream().filter(l -> !l.startsWith("|") && !l.equals("[/Input]")).toList());
            int offset = trace.metadata().bk2FrameOffset();
            for (int r : logicRows) out.add(rows.get(offset + r));
            out.add("[/Input]");
            Files.write(input, out, StandardCharsets.UTF_8);
        }
        int frames = 600;
        GameplayCaptureTool.Report report = GameplayCaptureTool.run(GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "scd", "--zone", "R11A", "--act", "1", "--input", input.toString(), "--frames",
                String.valueOf(frames), "--capture-from", "0", "--every", "30",
                "--out-dir", temp.resolve("gameplay").toString()}));
        assertEquals(frames, report.framesStepped());
        assertEquals(frames / 30, report.pngCount(), "one picture every 30 frames");
        List<String> state = Files.readAllLines(report.stateCsv(), StandardCharsets.UTF_8);
        for (int k = 0; k < frames; k++) {
            String[] c = state.get(k + 1).split(",", -1);
            TraceFrame expected = trace.getFrame(logicRows.get(k));
            assertEquals(expected.x() & 0xFFFF, Integer.parseInt(c[1]), "x at frame " + k + " (native row " + logicRows.get(k) + ")");
            assertEquals(expected.y() & 0xFFFF, Integer.parseInt(c[2]), "y at frame " + k + " (native row " + logicRows.get(k) + ")");
        }
        assertTrue(Integer.parseInt(state.get(frames).split(",")[1]) > 0x200, "the route ran right");
    }

    /** Stage names the capture command takes: a stage program, or a zone's initials with a one-based stage index. */
    @Test
    void sonicCdStagesAreNamedByProgramOrZoneAndIndex() {
        HostedGameMedia media = new CdHostedMedia();
        assertEquals(new HostedGameMedia.Level(0, 0), media.levelNamed("R11A", 1));
        assertEquals(new HostedGameMedia.Level(0, 1), media.levelNamed("r11b", 1));
        assertEquals(new HostedGameMedia.Level(0, 1), media.levelNamed("ppz", 2));
        assertEquals(media.levelNamed("R73D", 1), media.levelNamed("ssz", 10));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> media.levelNamed("ghz", 1)).getMessage()
                .contains("Unknown Sonic CD zone"));
        assertThrows(IllegalArgumentException.class, () -> media.levelNamed("ppz", 11));
    }

    private static HeadlessGameBoot runTraceCapture(String[] argv) throws Exception {
        TraceCaptureTool.Args args = TraceCaptureTool.Args.parse(argv);
        HostedTraceCapture hosted = HostedTraceCapture.resolve(args.trace(), args.movie());
        assertTrue(hosted != null, "a Sonic CD trace directory resolves to the hosted path");
        return hosted.run(args, TraceCaptureTool.captureDimensions(args));
    }
}
