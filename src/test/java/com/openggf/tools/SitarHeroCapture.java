package com.openggf.tools;

import com.openggf.mods.code.ExampleModHarness;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.tools.HeadlessGameBoot;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Production-ROM visual acceptance capture for the packaged Sitar Hero example. It boots
 * one supplied ROM, configures an explicit installed subset, checks the actual performer's
 * decoded pixels and records the real startup scene at 400x224. The all-ROM visit also
 * exercises cross-game songs, four instrument props, pause/resume and finite results.
 *
 * <p>Origin: Sitar Hero arcade proof of concept, 2026-10-06. Inputs: unmodified S1, S2 and
 * S3K ROMs under {@code --rom-root}, the example source under {@code --mod}, and an external
 * capture directory under {@code --out}. Run once per JVM with {@code --subset s1|s2|s3k|all}.
 * The headless backend uses real ROM synthesis with an offline sink; this proves artwork
 * and scene behavior, not speaker latency or parity with native hardware.
 */
public final class SitarHeroCapture {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 224;
    private static final List<String> ALL = List.of("s1", "s2", "s3k");
    private static final List<String> ROSTER = List.of("sonic", "robotnik", "tails", "silver-sonic",
            "knuckles", "mecha-sonic", "egg-robo");

    private SitarHeroCapture() { }

    public static void main(String[] args) throws Exception {
        Path romRoot = null;
        Path out = null;
        Path mod = Path.of("examples/sitar-hero");
        String subset = "all";
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 == args.length) throw new IllegalArgumentException("Missing option value");
            switch (args[index]) {
                case "--rom-root" -> romRoot = Path.of(args[index + 1]).toAbsolutePath();
                case "--mod" -> mod = Path.of(args[index + 1]);
                case "--out" -> out = Path.of(args[index + 1]).toAbsolutePath();
                case "--subset" -> subset = args[index + 1];
                default -> throw new IllegalArgumentException("Unknown option: " + args[index]);
            }
        }
        if (romRoot == null || out == null) throw new IllegalArgumentException("--rom-root and --out are required");
        List<String> games = subset.equals("all") ? ALL : List.of(subset);
        if (!ALL.containsAll(games)) throw new IllegalArgumentException("Unknown subset: " + subset);
        Path visit = Files.createDirectories(out.resolve(subset));
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, visit.resolve("saves").toString());
        String active = games.getFirst();
        try (HeadlessGameBoot boot = new HeadlessGameBoot(WIDTH, HEIGHT, WIDTH, HEIGHT)) {
            boot.boot(romRoot.resolve(active + ".gen"), 0, 0);
            configureSubset(romRoot, visit, games);
            try (ExampleModHarness harness = ExampleModHarness.build(mod, visit.resolve("build"))) {
                var effective = harness.apply(GameServices.module());
                GameModuleRegistry.setCurrent(effective);
                harness.open(effective, visit.resolve("saves"), WIDTH, HEIGHT, true);
                try {
                    tick(harness);
                    requireScreen(harness, "CHARACTERS");
                    List<String> performers = strings(harness, "availablePerformers");
                    List<String> expected = ROSTER.stream().filter(id -> available(id, games)).toList();
                    require(performers.equals(expected), "Roster " + performers + " differs from " + expected);
                    require(strings(harness, "availableSongs").size() == games.size(), "Wrong song subset");
                    verifyStage(harness);
                    for (String performer : performers) {
                        capture(harness, visit, "character-" + performer);
                        verifyActor(harness, performer);
                        press(harness, GLFW.GLFW_KEY_DOWN);
                    }
                    press(harness, GLFW.GLFW_KEY_ENTER);
                    requireScreen(harness, "ROLES");
                    for (String role : List.of("sitar", "bongos", "synth", "harp")) {
                        capture(harness, visit, "role-" + role);
                        press(harness, GLFW.GLFW_KEY_DOWN);
                    }
                    press(harness, GLFW.GLFW_KEY_ENTER);
                    requireScreen(harness, "SONGS");
                    capture(harness, visit, "songs");
                    if (subset.equals("all")) {
                        performances(harness, visit);
                        settings(harness, visit);
                    }
                    require(harness.findings().isEmpty(), "Mod fault findings: " + harness.findings());
                    System.out.println("VISUAL ACCEPTANCE: " + subset + " " + performers + "; captures " + visit);
                } finally {
                    harness.host().cleanup();
                }
            }
        }
    }

    private static void configureSubset(Path romRoot, Path visit, List<String> games) throws Exception {
        var configuration = GameServices.configuration();
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY,
                Files.createDirectories(visit.resolve("empty-rom-directory")).toString());
        var keys = List.of(SonicConfiguration.SONIC_1_ROM, SonicConfiguration.SONIC_2_ROM,
                SonicConfiguration.SONIC_3K_ROM);
        for (int index = 0; index < ALL.size(); index++) {
            String game = ALL.get(index);
            // Explicit missing paths fail closed in the catalogue; no ROM files or links are created.
            Path path = games.contains(game) ? romRoot.resolve(game + ".gen") : visit.resolve("absent-" + game + ".gen");
            configuration.setConfigValue(keys.get(index), path.toString());
        }
        GameServices.rom().reloadCatalogue();
    }

    private static boolean available(String performer, List<String> games) {
        return switch (performer) {
            case "silver-sonic" -> games.contains("s2");
            case "knuckles", "mecha-sonic", "egg-robo" -> games.contains("s3k");
            case "tails" -> games.contains("s2") || games.contains("s3k");
            default -> true;
        };
    }

    private static void performances(ExampleModHarness harness, Path visit) throws Exception {
        performance(harness, visit, "green-hill", "SITAR", "knuckles", true);
        performance(harness, visit, "chemical-plant", "SYNTH", "egg-robo", false);
        performance(harness, visit, "angel-island-1", "BONGOS", "silver-sonic", false);
        performance(harness, visit, "green-hill", "HARP", "mecha-sonic", false);
    }

    /** Visit remapping and calibration through the same visible controls a player uses. */
    private static void settings(ExampleModHarness harness, Path visit) throws Exception {
        press(harness, GLFW.GLFW_KEY_TAB);
        requireScreen(harness, "SETTINGS");
        capture(harness, visit, "settings-keyboard");
        press(harness, GLFW.GLFW_KEY_ENTER);
        capture(harness, visit, "settings-awaiting-binding");
        press(harness, GLFW.GLFW_KEY_ESCAPE);
        press(harness, GLFW.GLFW_KEY_TAB);
        capture(harness, visit, "settings-gamepad");
        press(harness, GLFW.GLFW_KEY_TAB);
        for (int row = 0; row < 10; row++) press(harness, GLFW.GLFW_KEY_DOWN);
        capture(harness, visit, "settings-offsets");
        for (int row = 0; row < 3; row++) press(harness, GLFW.GLFW_KEY_DOWN);
        press(harness, GLFW.GLFW_KEY_ENTER);
        while (screen(harness).equals("LOADING")) tick(harness);
        requireScreen(harness, "CALIBRATION");
        capture(harness, visit, "calibration-start");
        var prepared = (com.openggf.mods.scene.ScenePreparedMusic) field(harness.scene(), "prepared");
        var player = (com.openggf.mods.scene.SceneMusicPlayer) field(harness.scene(), "player");
        var onsets = prepared.notes().stream().filter(note -> note.kind() == com.openggf.mods.scene.SceneNoteEvent.Kind.DAC)
                .mapToLong(com.openggf.mods.scene.SceneNoteEvent::onsetSamples).distinct().sorted().limit(12).toArray();
        for (long onset : onsets) {
            while (player.samplePosition() < onset) tick(harness);
            press(harness, GLFW.GLFW_KEY_SPACE);
            if (((List<?>) field(harness.scene(), "calibration")).size() >= 8) break;
        }
        require(((List<?>) field(harness.scene(), "calibration")).size() >= 8, "Calibration did not accept eight real tap inputs");
        capture(harness, visit, "calibration-ready");
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "SETTINGS");
        capture(harness, visit, "settings-calibrated");
        press(harness, GLFW.GLFW_KEY_ESCAPE);
        requireScreen(harness, "CHARACTERS");
    }

    private static void performance(ExampleModHarness harness, Path visit, String song, String role,
            String performer, boolean finish) throws Exception {
        jump(harness, "perform:" + song + ":" + role + ":" + performer);
        capture(harness, visit, "loading-" + song + "-" + role.toLowerCase(java.util.Locale.ROOT));
        while (screen(harness).equals("LOADING")) tick(harness);
        requireScreen(harness, "PLAY");
        require(((Integer) invoke(harness, "chartNotes")) > 0, "Empty production-ROM chart");
        verifyStage(harness);
        verifyActor(harness, performer);
        jump(harness, "autoplay");
        for (int tick = 0; tick < 330; tick++) tick(harness);
        requireScreen(harness, "PLAY");
        String name = song + "-" + role.toLowerCase(java.util.Locale.ROOT) + "-" + performer;
        capture(harness, visit, "play-" + name);
        jump(harness, "pause");
        requireScreen(harness, "PAUSED");
        capture(harness, visit, "paused-" + name);
        for (int tick = 0; tick < 3; tick++) tick(harness);
        jump(harness, "resume");
        requireScreen(harness, "PLAY");
        if (finish) {
            long deadline = System.nanoTime() + 30_000_000_000L;
            int steps = 0;
            while (!screen(harness).equals("RESULTS") && steps++ < 6_500 && System.nanoTime() < deadline) {
                tick(harness);
                if (((Long) field(harness.scene(), "eofNanos")) != Long.MIN_VALUE) Thread.sleep(10);
            }
            requireScreen(harness, "RESULTS");
            require(((Long) invoke(harness, "score")) > 0, "Autoplay score remained zero");
            capture(harness, visit, "results-" + name);
            // Exercise the visible Retry item rather than forcing a result or a new session.
            press(harness, GLFW.GLFW_KEY_ENTER);
            while (screen(harness).equals("LOADING")) tick(harness);
            requireScreen(harness, "PLAY");
            capture(harness, visit, "retry-" + name);
        }
        jump(harness, "menu");
        tick(harness);
        requireScreen(harness, "CHARACTERS");
    }

    private static void tick(ExampleModHarness harness) throws Exception {
        harness.tick();
        HeadlessGameBoot.presentHeadlessOuterAudioFrame();
        require(!screen(harness).equals("ERROR"), "Scene error: " + invoke(harness, "error"));
        require(harness.findings().isEmpty(), "Mod fault findings: " + harness.findings());
    }

    private static void press(ExampleModHarness harness, int key) throws Exception {
        harness.input().handleKeyEvent(key, GLFW.GLFW_PRESS);
        tick(harness);
        harness.input().handleKeyEvent(key, GLFW.GLFW_RELEASE);
        tick(harness);
    }

    private static void jump(ExampleModHarness harness, String command) {
        require(harness.debugJump(command), "Rejected debug capture command: " + command);
    }

    private static void capture(ExampleModHarness harness, Path visit, String name) throws Exception {
        GL11.glViewport(0, 0, WIDTH, HEIGHT);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        float[] projection = {2f / WIDTH, 0, 0, 0, 0, 2f / HEIGHT, 0, 0, 0, 0, -1, 0, -1, -1, 0, 1};
        harness.host().draw(projection, new int[] {0, 0, WIDTH, HEIGHT});
        GL11.glFinish();
        var image = ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
        require(java.util.Arrays.stream(image.pixels()).distinct().count() > 16, "Blank capture: " + name);
        ScreenshotCapture.savePNG(image, visit.resolve(name + ".png"));
        require(harness.findings().isEmpty(), "Draw fault findings: " + harness.findings());
    }

    private static void verifyStage(ExampleModHarness harness) throws Exception {
        SceneBackdrop backdrop = (SceneBackdrop) field(harness.scene(), "backdrop");
        SceneImage foreground = (SceneImage) field(harness.scene(), "foreground");
        require(backdrop != null, "Missing production-ROM backdrop");
        require(nonblackColors(backdrop.image()) > 8, "Backdrop palette/art is empty");
        require(foreground != null && opaquePixels(foreground) > 1000, "Missing production-ROM foreground");
        System.out.println("STAGE " + field(harness.scene(), "song") + ": backdrop "
                + backdrop.image().width() + "x" + backdrop.image().height() + ", foreground " + opaquePixels(foreground));
    }

    private static void verifyActor(ExampleModHarness harness, String performer) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> actors = (Map<String, Object>) field(harness.scene(), "actors");
        Object actor = actors.get(performer);
        require(actor != null, "Performer was not decoded: " + performer);
        SceneSpriteSet character = (SceneSpriteSet) field(actor, "character");
        int pixels = 0;
        int colors = 0;
        if (character != null) {
            int[] frames = character.animationFrames(5);
            if (frames.length == 0) frames = new int[] {0};
            for (int frame : java.util.Arrays.stream(frames).distinct().toArray()) {
                SceneImage image = character.frame(frame).image();
                require(opaquePixels(image) > 50 && nonblackColors(image) >= 3,
                        performer + " animation frame " + frame + " is invisible or has no palette");
                pixels = Math.max(pixels, opaquePixels(image));
                colors = Math.max(colors, nonblackColors(image));
            }
        } else {
            for (Object layer : (List<?>) field(actor, "layers")) {
                SceneSpriteSet set = (SceneSpriteSet) method(layer, "set");
                int frame = (Integer) method(layer, "frame");
                SceneImage image = set.frame(frame).image();
                pixels += opaquePixels(image);
                colors = Math.max(colors, nonblackColors(image));
            }
        }
        require(pixels > 50 && colors >= 3, "Invisible or uncoloured performer: " + performer);
        SceneSpriteSet tail = (SceneSpriteSet) field(actor, "tails");
        System.out.println("ACTOR " + performer + ": opaque=" + pixels + " palette-colors=" + colors
                + (tail == null ? "" : " tail-frame-0=" + opaquePixels(tail.frame(0).image())));
    }

    private static int opaquePixels(SceneImage image) {
        int count = 0;
        for (int pixel : image.pixels()) if ((pixel >>> 24) != 0) count++;
        return count;
    }

    private static int nonblackColors(SceneImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int pixel : image.pixels()) if ((pixel >>> 24) != 0 && (pixel & 0xFFFFFF) != 0) colors.add(pixel);
        return colors.size();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object method(Object owner, String name) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }

    private static Object invoke(ExampleModHarness harness, String name) throws Exception {
        return harness.scene().getClass().getMethod(name).invoke(harness.scene());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(ExampleModHarness harness, String name) throws Exception {
        return (List<String>) invoke(harness, name);
    }

    private static String screen(ExampleModHarness harness) throws Exception { return (String) invoke(harness, "screen"); }
    private static void requireScreen(ExampleModHarness harness, String expected) throws Exception {
        require(screen(harness).equals(expected), "Expected " + expected + ", got " + screen(harness));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
