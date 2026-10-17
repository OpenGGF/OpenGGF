package com.openggf.tools;

import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.graphics.ScreenshotCapture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Positioned ending sampler, promoted from EndingEnginePalProbeV2 (2026-10-17 S3K
 * remaining-parity task). Inputs: original locked-on ROM, explicit product/region/progress,
 * new output directory and a file of engine outer-step observation indices (one per line).
 * Uses the frozen title-owner ingress, not a cold campaign. Native data never supplies
 * gameplay state; poll-to-outer mapping belongs only in comparison tooling.
 */
public final class S3kEndingSamplerTool {
    static final int LIMIT = 60000;
    private static final String ROM_SHA1 = "CFBF98C36C776677290A872547AC47C53D2761D6";
    private static final String EMERALDS = "3333333";

    private S3kEndingSamplerTool() { }

    public static void main(String[] args) throws Exception {
        run(Arguments.parse(args));
    }

    record Arguments(Path rom, Path output, Path spots) {
        static Arguments parse(String[] args) {
            Set<String> allowed = Set.of("--rom", "--out-dir", "--spots", "--product", "--region", "--progress");
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (!allowed.contains(args[i]) || i + 1 >= args.length
                        || values.putIfAbsent(args[i], args[i + 1]) != null) {
                    throw new IllegalArgumentException("Expected unique --rom --out-dir --spots --product tails-solo --region ntsc --progress +2");
                }
            }
            if (!values.keySet().equals(allowed) || !"tails-solo".equals(values.get("--product"))
                    || !"ntsc".equals(values.get("--region")) || !"+2".equals(values.get("--progress"))) {
                throw new IllegalArgumentException("Supported declared product: tails-solo / ntsc / +2; all six arguments required");
            }
            return new Arguments(Path.of(values.get("--rom")), Path.of(values.get("--out-dir")), Path.of(values.get("--spots")));
        }
    }

    static Set<Integer> readSpots(Path file) throws IOException {
        Set<Integer> spots = new TreeSet<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid outer spot: " + line);
            int spot = Integer.parseInt(line);
            if (spot >= LIMIT || !spots.add(spot)) throw new IllegalArgumentException("Duplicate/out-of-range outer spot: " + spot);
        }
        if (spots.isEmpty()) throw new IllegalArgumentException("Empty observation plan");
        return spots;
    }

    static void run(Arguments args) throws Exception {
        MutatorGameplayCaptureTool.requireDisplayFreeContext();
        Set<Integer> requested = readSpots(args.spots());
        if (!hash(args.rom(), "SHA-1").equalsIgnoreCase(ROM_SHA1)) throw new IllegalArgumentException("Requires original S3&K locked-on ROM");
        Path root = Path.of("").toAbsolutePath();
        String head = git(root, "rev-parse", "HEAD");
        String sourceHash = hashTree(root.resolve("src/main/java"));
        Path classes = Path.of(S3kEndingSamplerTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String runtimeHash = Files.isDirectory(classes) ? hashTree(classes) : hash(classes, "SHA-256");
        Properties provenance = new Properties();
        provenance.setProperty("source.head", head);
        provenance.setProperty("source.status", git(root, "status", "--porcelain", "--", "src/main/java"));
        provenance.setProperty("source.java.sha256", sourceHash);
        provenance.setProperty("runtime.sha256", runtimeHash);
        provenance.setProperty("rom.sha1", ROM_SHA1);
        provenance.setProperty("spots.sha256", hash(args.spots(), "SHA-256"));
        provenance.setProperty("product", "tails-solo/+2/ntsc/320x224/LIMITED238/RAW_RGB");
        provenance.setProperty("ingress", "frozen-title-owner-v2; positioned, not cold campaign; title ACTIVE then real Start EXITING; no data select; fresh ending load; VInt=0/RNG=0 before initial dispatch only");
        provenance.setProperty("observation", "engine outer indices only; neutral controller stream; no native gameplay hydration");
        provenance.setProperty("started.utc", java.time.Instant.now().toString());
        // Atomic refusal of existing output: never overwrite a retained capture.
        Files.createDirectory(args.output());
        Files.copy(args.spots(), args.output().resolve("spots.txt"));
        writeProperties(args.output(), provenance);
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null,
                EMERALDS, false, false, null, null);
        Set<Integer> captured = new TreeSet<>();
        // Configuration resolves against user.dir. T3 may link config.yaml into another
        // checkout: use fresh preferences without migrating or writing that linked file.
        Path configurationDirectory = args.output().toAbsolutePath().resolve("configuration");
        Files.createDirectory(configurationDirectory);
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", configurationDirectory.toString());
        try (var session = new GameplayCaptureSession(settings);
             var state = Files.newBufferedWriter(args.output().resolve("state.csv"));
             var ledger = Files.newBufferedWriter(args.output().resolve("ledger.csv"))) {
            var configuration = GameServices.configuration();
            configuration.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
            configuration.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
            configuration.setSessionOverride(SonicConfiguration.REGION, "NTSC");
            if (FrameRateResolver.effective(configuration) != 60) throw new IllegalStateException("NTSC did not resolve to 60 Hz");
            provenance.setProperty("configuration.loadTimeSimulation", configuration.getString(SonicConfiguration.LOAD_TIME_SIMULATION));
            session.startAtTitle();
            session.boot(args.rom(), 13, 1, settings);
            var title = session.loop().getTitleScreenProvider();
            int startup = 0;
            while (title.getState() != TitleScreenProvider.State.ACTIVE) {
                if (startup >= 3000) throw new IllegalStateException("Actual title did not become active");
                session.step(neutral(startup++));
                session.renderFrame();
            }
            session.loop().getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
            title.update(session.loop().getInputHandler());
            if (title.getState() != TitleScreenProvider.State.EXITING) throw new IllegalStateException("Real title Start did not publish EXITING");
            // Exact external v2 protocol: real title ownership, then declared positioned ending.
            GameServices.gameState().restoreS3kEmeraldProgress(EMERALDS.chars().map(c -> c - '0').boxed().toList(), false);
            session.loop().setGameMode(GameMode.LEVEL);
            GameServices.level().loadZoneAndActForFreshRuntime(13, 1);
            GameServices.level().getObjectManager().initVblaCounter(0);
            GameServices.rng().setSeed(0);
            Files.writeString(args.output().resolve("startup-owner.txt"), "actual_title_neutral=" + startup
                    + " real_Start=1 exit=" + title.getState() + " data_select_initialized=false\n");
            state.write("outer,game_mode,level_frame,object_ordinal,vint,progress,fg,bg,p1_x,p1_y,p1_mapping,rng,camera_x,camera_y,copy_x,copy_y,credits_routine,credits_part,credits_row,credits_delay,credits_speed\n");
            ledger.write("event,outer,detail\n");
            boolean exited = false;
            boolean titleReached = false;
            for (int outer = 0; outer < LIMIT; outer++) {
                session.step(neutral(outer));
                var image = session.render();
                if (image.width() != 320 || image.height() != 224) throw new IllegalStateException("Expected native 320x224 image");
                var mode = session.loop().getCurrentGameMode();
                if (mode != GameMode.LEVEL || !(GameServices.zoneRuntimeState() instanceof EndingZoneRuntimeState zone)) {
                    state.write(outer + "," + mode + "\n");
                    if (!exited) {
                        exited = true;
                        ledger.write("ending_exit," + outer + "," + mode + "\n");
                        ScreenshotCapture.savePNG(image, args.output().resolve("event-ending-exit.png"));
                    }
                    if (mode == GameMode.TITLE_SCREEN) {
                        titleReached = true;
                        ledger.write("title_mode," + outer + ",TITLE_SCREEN\n");
                        ScreenshotCapture.savePNG(image, args.output().resolve("event-title-mode.png"));
                        break;
                    }
                    continue;
                }
                if (exited) throw new IllegalStateException("Ending re-entered after exit");
                var level = GameServices.level();
                var manager = level.getObjectManager();
                var player = session.player();
                var camera = GameServices.camera();
                var credits = zone.ending().credits();
                state.write(outer + "," + mode + "," + level.getFrameCounter() + "," + manager.getFrameCounter()
                        + "," + manager.getVblaCounter() + "," + zone.ending().progress() + "," + zone.screen().foregroundRoutine
                        + "," + zone.screen().backgroundRoutine + "," + player.getCentreX() + "," + player.getCentreY()
                        + "," + player.getMappingFrame() + "," + Long.toHexString(GameServices.rng().getSeed())
                        + "," + camera.getX() + "," + camera.getY() + "," + camera.getXCopy() + "," + camera.getYCopy()
                        + "," + credits.routine() + "," + credits.partIndex() + "," + credits.rowIndex()
                        + "," + credits.delay() + "," + Integer.toHexString(credits.speed()) + "\n");
                if (requested.contains(outer)) {
                    ScreenshotCapture.savePNG(image, args.output().resolve(String.format("outer-%05d.png", outer)));
                    captured.add(outer);
                    ledger.write("snapshot," + outer + ",320x224\n");
                }
            }
            if (!exited || !titleReached) throw new IllegalStateException("No ending exit and title mode within " + LIMIT + " steps");
            if (!captured.equals(requested)) throw new IllegalStateException("Requested spots missing before exit: " + difference(requested, captured));
            ledger.write("DONE,-1,requested=" + requested.size() + ";captured=" + captured.size() + ";title_mode=true\n");
        }
        finally {
            if (previousUserDir == null) System.clearProperty("user.dir");
            else System.setProperty("user.dir", previousUserDir);
        }
        if (!head.equals(git(root, "rev-parse", "HEAD")) || !sourceHash.equals(hashTree(root.resolve("src/main/java")))
                || !runtimeHash.equals(Files.isDirectory(classes) ? hashTree(classes) : hash(classes, "SHA-256"))) {
            throw new IllegalStateException("Source/runtime changed during capture");
        }
        provenance.setProperty("state.sha256", hash(args.output().resolve("state.csv"), "SHA-256"));
        provenance.setProperty("ledger.sha256", hash(args.output().resolve("ledger.csv"), "SHA-256"));
        provenance.setProperty("completed.utc", java.time.Instant.now().toString());
        provenance.setProperty("captured", Integer.toString(captured.size()));
        provenance.setProperty("terminal", "TITLE_SCREEN mode; capture host does not certify hub recovery");
        writeProperties(args.output(), provenance);
        System.out.println("Ending sampler completed: " + captured.size() + " requested pre-exit spots; TITLE_SCREEN mode");
    }

    private static Set<Integer> difference(Set<Integer> requested, Set<Integer> captured) {
        Set<Integer> missing = new TreeSet<>(requested);
        missing.removeAll(captured);
        return missing;
    }

    private static Bk2FrameInput neutral(int outer) {
        return new Bk2FrameInput(outer, 0, 0, false, 0, 0, false, "neutral");
    }

    private static void writeProperties(Path output, Properties properties) throws IOException {
        try (var writer = Files.newBufferedWriter(output.resolve("provenance.properties"))) {
            properties.store(writer, "S3K positioned ending sampler");
        }
    }

    private static String git(Path root, String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (process.waitFor() != 0) throw new IOException("Cannot establish source provenance: " + result);
        return result;
    }

    private static String hash(Path path, String algorithm) throws Exception {
        var digest = MessageDigest.getInstance(algorithm);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = new byte[65536];
            for (int count; (count = input.read(bytes)) != -1;) digest.update(bytes, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashTree(Path directory) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                digest.update(directory.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
                digest.update((byte) 0);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
