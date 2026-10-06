package com.openggf.mods.code;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Screenshot, video and audio capture for an example mod's startup scene: boots the base game's
 * ROM headless with GL, builds the mod from source ({@link ExampleModHarness}), opens its scene,
 * plays a script of inputs and records what it draws and plays.
 *
 * <pre>
 * java -cp target/test-classes:target/classes:$(cat target/test-classpath.txt) \
 *   com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/slay-the-robotnik \
 *   --out /tmp/cap --script "60:enter 90:right +30:enter" --every 30
 * </pre>
 *
 * Options:
 * <ul>
 *   <li>{@code --rom}, {@code --mod}, {@code --out}: the base game ROM, the example's project
 *       directory, and where frames (and the scene's saves) go.</li>
 *   <li>{@code --script "step ..."} or {@code --script-file f} (one step per line, {@code #}
 *       comments): each step is {@code tick:action}, where {@code tick} is absolute or
 *       {@code +n} after the previous step. Actions: a key name ({@code enter}, {@code space},
 *       {@code up}, {@code down}, {@code left}, {@code right}, {@code back}, {@code escape},
 *       {@code tab}, a letter or a digit), pressed for two ticks; {@code hold=key} and
 *       {@code release=key}; {@code mouse=x,y} to point at a game pixel; {@code click=x,y} and
 *       {@code rclick=x,y} to point there and click; {@code wheel=n} to turn the wheel
 *       {@code n} notches; {@code jump=command} to send the scene a debug command (see the
 *       mod's scene, for example Slay the Robotnik's {@code SlayScene.debugJump}).</li>
 *   <li>{@code --every n}: save a PNG every {@code n} ticks (default 30; 0 for none);
 *       {@code --ticks n}: how long to run (default: the last step plus 120).</li>
 *   <li>{@code --width}, {@code --height}, {@code --scale}: the scene's size (default 400x224)
 *       and the integer window scale (default 2).</li>
 *   <li>{@code --audio out.wav}: record the scene's music and sound effects, rendered by the
 *       engine's SMPS driver through the offline capture lease, one packet per tick.</li>
 *   <li>{@code --video out.mp4}: encode every tick with ffmpeg (on the PATH), muxing the audio
 *       when {@code --audio} is given.</li>
 *   <li>{@code --jump command}: a debug command sent before the first tick.</li>
 * </ul>
 * Origin: the Slay the Robotnik screenshot tool (2026-10-05), generalised with audio and
 * video for the example's highlight reel on 2026-10-06.
 */
public final class ExampleModCapture {
    private ExampleModCapture() {
    }

    /** One scripted input at a tick. */
    private record Step(int tick, String action) {
    }

    public static void main(String[] args) throws Exception {
        Path rom = null;
        Path mod = null;
        Path out = null;
        String script = "";
        int every = 30;
        int total = -1;
        int width = 400;
        int height = 224;
        int scale = 2;
        Path audioOut = null;
        Path videoOut = null;
        String firstJump = null;
        for (int i = 0; i < args.length; i++) {
            String value = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--rom" -> rom = Path.of(value);
                case "--mod" -> mod = Path.of(value);
                case "--out" -> out = Path.of(value);
                case "--script" -> script = value;
                case "--script-file" -> script = String.join("\n", Files.readAllLines(Path.of(value)));
                case "--every" -> every = Integer.parseInt(value);
                case "--ticks" -> total = Integer.parseInt(value);
                case "--width" -> width = Integer.parseInt(value);
                case "--height" -> height = Integer.parseInt(value);
                case "--scale" -> scale = Integer.parseInt(value);
                case "--audio" -> audioOut = Path.of(value);
                case "--video" -> videoOut = Path.of(value);
                case "--jump" -> firstJump = value;
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
            i++;
        }
        if (rom == null || mod == null || out == null) {
            throw new IllegalArgumentException("--rom, --mod and --out are required");
        }
        Files.createDirectories(out);
        List<Step> steps = parse(script);
        if (total < 0) {
            total = (steps.isEmpty() ? 0 : steps.get(steps.size() - 1).tick()) + 120;
        }
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, out.resolve("saves").toString());
        try (HeadlessGameBoot boot = new HeadlessGameBoot(width * scale, height * scale, width, height)) {
            boot.boot(rom, 0, 0);
            try (ExampleModHarness harness = ExampleModHarness.build(mod, out.resolve("build"))) {
                var effective = harness.apply(GameServices.module());
                GameModuleRegistry.setCurrent(effective);
                harness.open(effective, out.resolve("saves"), width, height, audioOut != null);
                run(harness, steps, firstJump, out, every, total, width, height, scale, audioOut, videoOut);
            }
        }
        System.out.println("Capture written to " + out);
    }

    private static void run(ExampleModHarness harness, List<Step> steps, String firstJump, Path out, int every,
            int total, int width, int height, int scale, Path audioOut, Path videoOut)
            throws IOException, InterruptedException {
        var audio = GameServices.audio();
        int fps = audio.presentationFrameRate();
        WavWriter wav = null;
        short[] pcm = null;
        if (audioOut != null) {
            int rate = audio.outputSampleRate();
            audio.beginCaptureMode(rate, fps);
            wav = new WavWriter(audioOut, rate);
            // Generous: one presented packet is rate / fps stereo frames.
            pcm = new short[(rate / fps + 64) * 2];
        }
        Path silentVideo = videoOut == null ? null
                : (audioOut == null ? videoOut : out.resolve("video-only.mp4"));
        Process ffmpeg = silentVideo == null ? null : startEncoder(silentVideo, width * scale, height * scale, fps);
        try (OutputStream video = ffmpeg == null ? null : new BufferedOutputStream(ffmpeg.getOutputStream(), 1 << 20)) {
            if (firstJump != null) {
                harness.tick();
                jump(harness, firstJump);
            }
            float[] projection = ortho(width, height);
            int[] viewport = {0, 0, width * scale, height * scale};
            GL11.glViewport(0, 0, width * scale, height * scale);
            ByteBuffer frameBytes = ByteBuffer.allocate(width * scale * height * scale * 4).order(ByteOrder.LITTLE_ENDIAN);
            List<Step> releases = new ArrayList<>();
            int next = 0;
            for (int tick = 0; tick <= total; tick++) {
                while (next < steps.size() && steps.get(next).tick() == tick) {
                    apply(harness, steps.get(next++), releases, tick);
                }
                for (int r = releases.size() - 1; r >= 0; r--) {
                    if (releases.get(r).tick() == tick) {
                        release(harness, releases.remove(r).action());
                    }
                }
                harness.tick();
                if (wav != null) {
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                }
                boolean png = every > 0 && (tick % every == 0 || tick == total);
                if (png || video != null) {
                    GL11.glClearColor(0, 0, 0, 1);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    harness.host().draw(projection, viewport);
                    GL11.glFinish();
                    RgbaImage image = ScreenshotCapture.captureFramebuffer(width * scale, height * scale);
                    if (png) {
                        ScreenshotCapture.savePNG(image, out.resolve(String.format(Locale.ROOT, "frame-%05d.png", tick)));
                    }
                    if (video != null) {
                        frameBytes.clear();
                        for (int argb : image.pixels()) {
                            frameBytes.putInt(argb); // little-endian ARGB is BGRA bytes
                        }
                        video.write(frameBytes.array());
                    }
                }
                if (wav != null) {
                    int frames = audio.drainCaptureFrame(pcm);
                    wav.write(pcm, frames * 2);
                }
            }
        } finally {
            if (wav != null) {
                audio.endCaptureMode();
                wav.close();
                System.out.println("Audio: " + audioOut + " (peak " + wav.peak() + " of 32767)");
            }
        }
        if (ffmpeg != null) {
            if (ffmpeg.waitFor() != 0) {
                throw new IllegalStateException("ffmpeg failed encoding " + silentVideo);
            }
            if (audioOut != null) {
                mux(silentVideo, audioOut, videoOut);
                Files.deleteIfExists(silentVideo);
            }
            System.out.println("Video: " + videoOut);
        }
    }

    private static List<Step> parse(String script) {
        List<Step> steps = new ArrayList<>();
        int last = 0;
        for (String line : script.split("\n")) {
            int hash = line.indexOf('#');
            String body = hash >= 0 ? line.substring(0, hash) : line;
            for (String token : body.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                String[] parts = token.split(":", 2);
                int tick = parts[0].startsWith("+") ? last + Integer.parseInt(parts[0].substring(1))
                        : Integer.parseInt(parts[0]);
                if (tick < last) {
                    throw new IllegalArgumentException("Steps must not go back in time: " + token);
                }
                steps.add(new Step(tick, parts[1]));
                last = tick;
            }
        }
        return steps;
    }

    private static void apply(ExampleModHarness harness, Step step, List<Step> releases, int tick) {
        String action = step.action();
        var input = harness.input();
        if (action.startsWith("jump=")) {
            jump(harness, action.substring(5));
        } else if (action.startsWith("mouse=") || action.startsWith("click=") || action.startsWith("rclick=")) {
            String[] xy = action.substring(action.indexOf('=') + 1).split(",");
            input.handleMouseMove(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
            if (!action.startsWith("mouse=")) {
                int button = action.startsWith("rclick=") ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_LEFT;
                input.handleMouseButton(button, GLFW.GLFW_PRESS);
                releases.add(new Step(tick + 2, action.startsWith("rclick=") ? "button=right" : "button=left"));
            }
        } else if (action.startsWith("wheel=")) {
            com.openggf.control.MouseWheel.of(input).scroll(Double.parseDouble(action.substring(6)));
        } else if (action.startsWith("hold=")) {
            input.handleKeyEvent(key(action.substring(5)), GLFW.GLFW_PRESS);
        } else if (action.startsWith("release=")) {
            input.handleKeyEvent(key(action.substring(8)), GLFW.GLFW_RELEASE);
        } else {
            input.handleKeyEvent(key(action), GLFW.GLFW_PRESS);
            releases.add(new Step(tick + 2, "key=" + action));
        }
    }

    private static void jump(ExampleModHarness harness, String command) {
        if (!harness.debugJump(command)) {
            System.err.println("The scene did not take debug command '" + command + "'");
        }
    }

    private static void release(ExampleModHarness harness, String action) {
        var input = harness.input();
        switch (action) {
            case "button=left" -> input.handleMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE);
            case "button=right" -> input.handleMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE);
            default -> input.handleKeyEvent(key(action.substring(4)), GLFW.GLFW_RELEASE);
        }
    }

    private static int key(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.length() == 1 && n.charAt(0) >= 'a' && n.charAt(0) <= 'z') {
            return GLFW.GLFW_KEY_A + (n.charAt(0) - 'a');
        }
        if (n.length() == 1 && n.charAt(0) >= '0' && n.charAt(0) <= '9') {
            return GLFW.GLFW_KEY_0 + (n.charAt(0) - '0');
        }
        return switch (n) {
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "back" -> GLFW.GLFW_KEY_BACKSPACE;
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            default -> throw new IllegalArgumentException("Unknown key " + name);
        };
    }

    private static Process startEncoder(Path video, int width, int height, int fps) throws IOException {
        return new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgra", "-s", width + "x" + height, "-r", Integer.toString(fps),
                "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p",
                video.toString())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    private static void mux(Path video, Path audio, Path out) throws IOException, InterruptedException {
        Process mux = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-i", video.toString(),
                "-i", audio.toString(), "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", out.toString())
                .inheritIO().start();
        if (mux.waitFor() != 0) {
            throw new IllegalStateException("ffmpeg failed muxing " + out);
        }
    }

    private static float[] ortho(int width, int height) {
        // Column-major ortho(0, width, 0, height, -1, 1).
        return new float[] {
                2f / width, 0, 0, 0,
                0, 2f / height, 0, 0,
                0, 0, -1, 0,
                -1, -1, 0, 1};
    }

    /** A 16-bit stereo WAV written as it goes; the header's sizes are filled in on close. */
    private static final class WavWriter implements AutoCloseable {
        private final RandomAccessFile file;
        private final ByteBuffer buffer = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
        private long dataBytes;
        private int peak;

        WavWriter(Path path, int sampleRate) throws IOException {
            file = new RandomAccessFile(path.toFile(), "rw");
            file.setLength(0);
            ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes()).putInt(0).put("WAVE".getBytes());
            header.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2)
                    .putInt(sampleRate).putInt(sampleRate * 4).putShort((short) 4).putShort((short) 16);
            header.put("data".getBytes()).putInt(0);
            file.write(header.array());
        }

        void write(short[] samples, int count) throws IOException {
            buffer.clear();
            for (int i = 0; i < count; i++) {
                buffer.putShort(samples[i]);
                peak = Math.max(peak, Math.abs((int) samples[i]));
            }
            file.write(buffer.array(), 0, buffer.position());
            dataBytes += buffer.position();
        }

        int peak() {
            return peak;
        }

        @Override
        public void close() throws IOException {
            file.seek(4);
            file.write(le32(36 + dataBytes));
            file.seek(40);
            file.write(le32(dataBytes));
            file.close();
        }

        private static byte[] le32(long value) {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) value).array();
        }
    }
}
