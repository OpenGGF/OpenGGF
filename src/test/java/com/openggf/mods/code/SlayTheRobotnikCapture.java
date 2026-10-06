package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

/**
 * Screenshot tool for the Slay the Robotnik example: boots S3K headless with GL, opens the
 * mod's startup scene, plays a short key script and saves PNG frames.
 *
 * <pre>
 * java -cp target/test-classes:target/classes:$(cat target/slay-test-classpath.txt) \
 *   com.openggf.mods.code.SlayTheRobotnikCapture s3k.gen out/ "60:enter 90:right 120:enter" 30
 * </pre>
 * Arguments: ROM path, output directory, key script ("tick:key ..." with keys enter, space,
 * up, down, left, right, back, e, d, m; {@code jump=<room>} to jump into a room such as
 * "sonic:42:fight:hcz:big_shaker", see SlayScene.debugJump; {@code mouse=x,y} to point at a
 * game pixel; or {@code wheel=n} to turn the wheel n notches), the capture interval in ticks,
 * the total ticks, and optionally a room to jump to before the first tick.
 * Origin: Slay the Robotnik example mod, 2026-10-05.
 */
public final class SlayTheRobotnikCapture {
    private SlayTheRobotnikCapture() {
    }

    public static void main(String[] args) throws Exception {
        Path rom = Path.of(args[0]);
        Path out = Files.createDirectories(Path.of(args[1]));
        String script = args.length > 2 ? args[2] : "";
        int every = args.length > 3 ? Integer.parseInt(args[3]) : 30;
        int width = 400;
        int height = 224;
        int scale = 2;
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, out.resolve("saves").toString());
        List<int[]> keys = new ArrayList<>();
        java.util.Map<Integer, String> jumps = new java.util.HashMap<>();
        java.util.Map<Integer, String> pointer = new java.util.HashMap<>();
        int lastTick = 0;
        for (String step : script.trim().isEmpty() ? new String[0] : script.trim().split("\\s+")) {
            String[] parts = step.split(":", 2);
            int tick = Integer.parseInt(parts[0]);
            if (parts[1].startsWith("jump=")) {
                jumps.put(tick, parts[1].substring(5));
            } else if (parts[1].startsWith("mouse=") || parts[1].startsWith("wheel=")) {
                pointer.put(tick, parts[1]);
            } else {
                keys.add(new int[] {tick, key(parts[1])});
            }
            lastTick = Math.max(lastTick, tick);
        }
        int total = args.length > 4 ? Integer.parseInt(args[4]) : lastTick + 120;
        try (HeadlessGameBoot boot = new HeadlessGameBoot(width * scale, height * scale, width, height)) {
            boot.boot(rom, 0, 0);
            try (SlayTheRobotnikHarness harness = SlayTheRobotnikHarness.build(out.resolve("build"))) {
                var effective = harness.apply(GameServices.module());
                GameModuleRegistry.setCurrent(effective);
                harness.open(effective, out.resolve("saves"), width, height);
                if (args.length > 5) {
                    // Jump straight into a room ("sonic:42:fight:hcz:big_shaker"); see SlayScene.debugJump.
                    harness.tick();
                    Object scene = harness.scene();
                    scene.getClass().getMethod("debugJump", String.class).invoke(scene, args[5]);
                }
                float[] projection = ortho(width, height);
                int[] viewport = {0, 0, width * scale, height * scale};
                org.lwjgl.opengl.GL11.glViewport(0, 0, width * scale, height * scale);
                for (int tick = 0; tick <= total; tick++) {
                    if (jumps.containsKey(tick)) {
                        Object scene = harness.scene();
                        scene.getClass().getMethod("debugJump", String.class).invoke(scene, jumps.get(tick));
                    }
                    String move = pointer.get(tick);
                    if (move != null && move.startsWith("mouse=")) {
                        String[] xy = move.substring(6).split(",");
                        harness.input().handleMouseMove(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                    } else if (move != null) {
                        harness.input().handleScroll(Double.parseDouble(move.substring(6)));
                    }
                    for (int[] k : keys) {
                        if (k[0] == tick) {
                            harness.input().handleKeyEvent(k[1], GLFW.GLFW_PRESS);
                        } else if (k[0] + 2 == tick) {
                            harness.input().handleKeyEvent(k[1], GLFW.GLFW_RELEASE);
                        }
                    }
                    harness.tick();
                    if (tick % every == 0 || tick == total) {
                        org.lwjgl.opengl.GL11.glClearColor(0, 0, 0, 1);
                        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
                        harness.host().draw(projection, viewport);
                        org.lwjgl.opengl.GL11.glFinish();
                        var image = ScreenshotCapture.captureFramebuffer(width * scale, height * scale);
                        ScreenshotCapture.savePNG(image, out.resolve(String.format(Locale.ROOT, "frame-%05d.png", tick)));
                        if (tick == total && System.getenv("SLAY_DUMP_OPS") != null) {
                            var m = com.openggf.mods.scene.ModSceneHost.class.getDeclaredMethod("lastFrame");
                            m.setAccessible(true);
                            for (Object op : (List<?>) m.invoke(harness.host())) {
                                System.out.println("OP " + op);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Frames written to " + out);
    }

    private static float[] ortho(int width, int height) {
        // Column-major ortho(0, width, 0, height, -1, 1).
        return new float[] {
                2f / width, 0, 0, 0,
                0, 2f / height, 0, 0,
                0, 0, -1, 0,
                -1, -1, 0, 1};
    }

    private static int key(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "back" -> GLFW.GLFW_KEY_BACKSPACE;
            case "e" -> GLFW.GLFW_KEY_E;
            case "d" -> GLFW.GLFW_KEY_D;
            case "m" -> GLFW.GLFW_KEY_M;
            default -> throw new IllegalArgumentException("Unknown key " + name);
        };
    }
}
