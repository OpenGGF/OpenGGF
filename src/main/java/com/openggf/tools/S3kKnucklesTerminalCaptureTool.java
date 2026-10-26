package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.ending.S3kPostCreditsObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.ScreenshotCapture;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Cold Knuckles terminal capture, promoted from the ending-pal lane's
 * KnuxTerminalDiagDriver (2026-10-17, original blob 3c595210). Inputs: locked-on
 * ROM, legal cold LRZ/HPZ/SSZ2 route, canonical native controller suffix, its
 * terminal-init index and one observation offset. Native data supplies inputs
 * only; the sole declared gameplay inlet is seven Super Emeralds after Mecha.
 */
public final class S3kKnucklesTerminalCaptureTool {
    private static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";

    private S3kKnucklesTerminalCaptureTool() { }

    public static void main(String[] args) throws Exception {
        var options = Arguments.parse(args);
        MutatorGameplayCaptureTool.requireDisplayFreeContext();
        if (!ROM_SHA1.equals(hash(options.rom(), "SHA-1"))) {
            throw new IllegalArgumentException("Expected original locked-on S3K ROM");
        }
        Files.createDirectory(options.output());
        Path root = Path.of("").toAbsolutePath();
        String previousUserDir = System.getProperty("user.dir");
        inPreferenceDirectory(options.output(), () -> capture(options, root));
        var evidence = new Properties();
        try (var reader = Files.newBufferedReader(options.output().resolve("capture.properties"))) { evidence.load(reader); }
        evidence.setProperty("user.dir.restored", Boolean.toString(java.util.Objects.equals(previousUserDir, System.getProperty("user.dir"))));
        Path example = options.output().resolve("configuration/config.yaml.example");
        if (!Files.isRegularFile(example)) throw new IllegalStateException("Private configuration publication missing");
        evidence.setProperty("configuration.example.sha256", hash(example, "SHA-256"));
        try (var writer = Files.newBufferedWriter(options.output().resolve("capture.properties"))) { evidence.store(writer, "One cold Knuckles terminal observation"); }
        System.out.println("Knuckles observation captured at offset " + options.offset() + "; " + evidence.getProperty("engine.inputs") + " inputs");
    }

    record Arguments(Path rom, Path route, Path nativeInput, int nativeInit, int offset, Path output) {
        static Arguments parse(String[] args) {
            var values = new LinkedHashMap<String, String>();
            var keys = java.util.Set.of("--rom", "--route", "--native-input", "--native-init", "--offset", "--out-dir");
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length || !keys.contains(args[i])
                        || values.putIfAbsent(args[i], args[i + 1]) != null) {
                    throw new IllegalArgumentException("Expected six unique capture arguments");
                }
            }
            if (!values.keySet().equals(keys)) throw new IllegalArgumentException("All six capture arguments required");
            int init = Integer.parseInt(values.get("--native-init"));
            int offset = Integer.parseInt(values.get("--offset"));
            if (init < 0 || offset < 1 || offset > 10000) throw new IllegalArgumentException("Invalid controller/observation index");
            return new Arguments(path(values, "--rom"), path(values, "--route"), path(values, "--native-input"),
                    init, offset, path(values, "--out-dir"));
        }

        private static Path path(Map<String, String> values, String key) {
            return Path.of(values.get(key)).toAbsolutePath().normalize();
        }
    }

    @FunctionalInterface
    interface CaptureAction { void run() throws Exception; }

    static void inPreferenceDirectory(Path output, CaptureAction action) throws Exception {
        Path preferences = Files.createDirectory(output.resolve("configuration"));
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", preferences.toString());
        try { action.run(); }
        finally {
            if (previous == null) System.clearProperty("user.dir");
            else System.setProperty("user.dir", previous);
        }
    }

    private static void capture(Arguments options, Path root) throws Exception {
        var route = new Bk2MovieLoader().loadMovieOrInputLog(options.route());
        var suffix = new Bk2MovieLoader().loadMovieOrInputLog(options.nativeInput());
        if ((long) options.nativeInit() + options.offset() >= suffix.getFrameCount()) {
            throw new IllegalArgumentException("Native controller suffix does not cover the requested offset");
        }
        var evidence = new Properties();
        evidence.setProperty("started.utc", java.time.Instant.now().toString());
        var git = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD").start();
        evidence.setProperty("source.head", new String(git.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim());
        if (git.waitFor() != 0) throw new IllegalStateException("Cannot identify source checkout");
        evidence.setProperty("source.java.sha256", hashTree(root.resolve("src/main/java")));
        Path classes = Path.of(S3kKnucklesTerminalCaptureTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        evidence.setProperty("runtime.sha256", Files.isDirectory(classes) ? hashTree(classes) : hash(classes, "SHA-256"));
        evidence.setProperty("product", "knuckles/+2/NTSC/320x224/LIMITED238/RAW_RGB");
        evidence.setProperty("ingress", "cold LRZ to HPZ to shipped SSZ2; ordinary Mecha defeat; seven Super Emeralds after defeat only");
        evidence.setProperty("rom.sha1", hash(options.rom(), "SHA-1"));
        evidence.setProperty("route.sha256", hash(options.route(), "SHA-256"));
        evidence.setProperty("nativeInput.sha256", hash(options.nativeInput(), "SHA-256"));
        evidence.setProperty("native.init", Integer.toString(options.nativeInit()));
        evidence.setProperty("observation.offset", Integer.toString(options.offset()));
        evidence.setProperty("configuration.directory", System.getProperty("user.dir"));
        evidence.setProperty("java.version", System.getProperty("java.version"));
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        int phase = 1, health = 8, init = -1;
        boolean seenBoss = false, seeded = false;
        long deadline = System.nanoTime() + 1500L * 1_000_000_000L;
        try (var session = new GameplayCaptureSession(settings);
             var log = new PrintWriter(Files.newBufferedWriter(options.output().resolve("frames.csv")))) {
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
            config.setSessionOverride(SonicConfiguration.REGION, "NTSC");
            session.boot(options.rom(), 9, 0, settings);
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
            evidence.setProperty("configuration.loadTimeSimulation", config.getString(SonicConfiguration.LOAD_TIME_SIMULATION));
            log.println("frame,offset,mode,post_phase,input_source");
            for (int frame = 0; ; frame++) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("Capture deadline reached at frame " + frame);
                int offset = init < 0 ? -1 : frame - init;
                Bk2FrameInput input;
                String source;
                if (offset > 0) {
                    input = suffix.getFrame(options.nativeInit() + offset);
                    source = "native" + (options.nativeInit() + offset);
                } else if (frame < route.getFrameCount()) {
                    input = route.getFrame(frame);
                    source = "route" + frame;
                } else throw new IllegalStateException("Cold route ended before Obj_5EF68 init at " + frame);
                session.step(input);
                GameMode mode = session.loop().getCurrentGameMode();
                S3kPostCreditsObjectInstance post = null;
                if (mode == GameMode.LEVEL && GameServices.level().getCurrentZone() == 10) {
                    var objects = GameServices.level().getObjectManager();
                    var ssz = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
                    var bosses = objects.activeObjectsOfType(SszMechaSonicObjectInstance.class);
                    if (!bosses.isEmpty()) {
                        seenBoss = true;
                        int hp = bosses.getFirst().getCollisionProperty();
                        if (hp > health) phase = 2;
                        health = hp;
                    } else if (seenBoss && phase == 2 && !seeded && !ssz.ending().initialized()) {
                        GameServices.gameState().restoreS3kEmeraldProgress(java.util.List.of(3, 3, 3, 3, 3, 3, 3), true);
                        seeded = true;
                        evidence.setProperty("inlet.superEmeralds.frame", Integer.toString(frame));
                    }
                    post = objects.activeObjectsOfType(S3kPostCreditsObjectInstance.class).stream().findFirst().orElse(null);
                    if (init < 0 && post != null && post.phase() >= 1) {
                        if (!seeded) throw new IllegalStateException("Terminal initialized before the declared Mecha-defeat inlet");
                        init = frame;
                        offset = 0;
                        evidence.setProperty("engine.init", Integer.toString(frame));
                    }
                }
                if (offset < 0) continue;
                log.println(frame + "," + offset + "," + mode + "," + (post == null ? -1 : post.phase()) + "," + source);
                if (offset == options.offset()) {
                    ScreenshotCapture.savePNG(session.render(), options.output().resolve("eyecatch.png"));
                    evidence.setProperty("engine.observation", Integer.toString(frame));
                    evidence.setProperty("engine.inputs", Integer.toString(frame + 1));
                    evidence.setProperty("route.inputs", Integer.toString(init + 1));
                    evidence.setProperty("nativeSuffix.inputs", Integer.toString(offset));
                    evidence.setProperty("mode", mode.name());
                    evidence.setProperty("post.phase", Integer.toString(post == null ? -1 : post.phase()));
                    evidence.setProperty("result", "captured");
                    break;
                }
            }
        } finally {
            evidence.setProperty("ended.utc", java.time.Instant.now().toString());
            try (var writer = Files.newBufferedWriter(options.output().resolve("capture.properties"))) {
                evidence.store(writer, "One cold Knuckles terminal observation; no full-sequence qualification");
            }
        }
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

    private static String hash(Path path, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(path)));
    }
}
