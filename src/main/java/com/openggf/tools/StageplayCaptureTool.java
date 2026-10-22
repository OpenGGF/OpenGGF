package com.openggf.tools;

import com.openggf.capture.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.RomIdentity;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Renders a saved Stageplay scene to lossless video and synchronized final mixed audio.
 * Inputs: --scene file --out new-file.mkv [--rom file | --game s1|s2|s3k], optional
 * --s1-rom/--s2-rom/--s3k-rom, --scale 1..8, --ffmpeg executable. Without --rom the
 * existing logical-ROM catalogue resolves the set's game, including composite images.
 * Origin: OpenGGF Stageplay first authoring/runtime delivery, 2026-10-21.
 */
public final class StageplayCaptureTool {
    private StageplayCaptureTool() { }

    public record Arguments(Path scene, Path output, Path rom, String game, int scale, String ffmpeg,
                            Map<String, Path> suppliedRoms, Path stagingDirectory, Path romDescriptor) {
        public Arguments(Path scene, Path output, Path rom, String game, int scale, String ffmpeg,
                         Map<String, Path> suppliedRoms, Path stagingDirectory) {
            this(scene,output,rom,game,scale,ffmpeg,suppliedRoms,stagingDirectory,null);
        }
        public Arguments {
            Objects.requireNonNull(scene); Objects.requireNonNull(output); Objects.requireNonNull(ffmpeg);
            suppliedRoms = Map.copyOf(suppliedRoms);
            if (scale < 1 || scale > 8) throw new IllegalArgumentException("Scale must be 1..8");
            if (game != null && !List.of("s1", "s2", "s3k").contains(game)) throw new IllegalArgumentException("Unknown game");
            if (!output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mkv"))
                throw new IllegalArgumentException("Lossless Stageplay exports use .mkv");
            if (scene.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
                throw new IllegalArgumentException("Export must not replace its source scene");
            if (romDescriptor != null && (rom != null || !suppliedRoms.isEmpty()))
                throw new IllegalArgumentException("A ROM descriptor cannot be combined with ROM path overrides");
        }
        public static Arguments parse(String[] args) {
            Path scene = null, output = null, rom = null, staging = null, descriptor = null; String game = null, ffmpeg = "ffmpeg"; int scale = 1;
            var supplied = new LinkedHashMap<String, Path>();
            var seen = new HashSet<String>();
            for (int i = 0; i < args.length; i += 2) {
                String key = args[i];
                if (i + 1 == args.length || !seen.add(key)) throw new IllegalArgumentException("Missing or duplicate argument " + key);
                String value = args[i + 1];
                switch (key) {
                    case "--scene" -> scene = Path.of(value);
                    case "--out" -> output = Path.of(value);
                    case "--rom" -> rom = Path.of(value);
                    case "--staging-dir" -> staging = Path.of(value);
                    case "--rom-descriptor" -> descriptor = Path.of(value);
                    case "--game" -> game = value;
                    case "--scale" -> scale = Integer.parseInt(value);
                    case "--ffmpeg" -> ffmpeg = value;
                    case "--s1-rom", "--s2-rom", "--s3k-rom" -> supplied.put(key.substring(2, key.length() - 4), Path.of(value));
                    default -> throw new IllegalArgumentException("Unknown argument " + key);
                }
            }
            if (scene == null || output == null) throw new IllegalArgumentException("--scene and --out are required");
            return new Arguments(scene, output, rom, game, scale, ffmpeg, supplied, staging, descriptor);
        }
    }

    public static void main(String[] args) throws Exception {
        Path output = run(Arguments.parse(args));
        System.out.println("Stageplay export complete: " + output);
    }

    public static Path run(Arguments arguments) throws IOException {
        var document = StageplayStore.read(arguments.scene());
        Path output = arguments.output().toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IOException("Export already exists: " + output);
        Files.createDirectories(output.getParent());
        Path temporary = prepareStagingDirectory(output.getParent(), arguments.stagingDirectory());
        Path staged = temporary.resolve("film.mkv");
        try {
            var config = GameServices.configuration();
            for (var rom : arguments.suppliedRoms().entrySet()) config.setSessionOverride(romKey(rom.getKey()), rom.getValue().toAbsolutePath().toString());
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            String game = arguments.game() == null ? document.set().gameId() : arguments.game();
            Map<String,com.openggf.data.RomByteReader> transported = null;
            if (arguments.romDescriptor() != null) {
                transported = StageplayRomTransport.openFile(arguments.romDescriptor());
            }
            // Gameplay boot only supplies independent ROM/audio services; this tool never steps its level.
            try (var boot = new HeadlessGameBoot(document.width(), document.height(), document.width(), document.height(), 320, 224)) {
                boot.setBootToTitle(true);
                if (transported != null) boot.boot(Objects.requireNonNull(transported.get(game),"Supply the boot ROM"),
                        "Stageplay export " + game, 0, 0);
                else if (arguments.rom() == null) boot.boot(identity(game), 0, 0);
                else boot.boot(arguments.rom(), 0, 0);
                var audio = GameServices.audio();
                if (audio.presentationFrameRate() != StageplayDocument.FRAMES_PER_SECOND)
                    throw new IOException("Stageplay exports require 60 Hz audio; this ROM is using " + audio.presentationFrameRate());
                audio.stopMusic(); audio.stopAllSfx();
                try (var library = transported == null
                        ? new SceneRomLibrary(GameServices.module(), GameServices.rom().getRom(), GameServices.rom())
                        : SceneRomLibrary.openReaders(transported,game);
                     var resources = new StageplayResources(library, false);
                     var cues = new StageplayAudio(audio, library, arguments.scene().toAbsolutePath().getParent());
                     var surface = new SceneSurface();
                     var grabber = new GlReadPixelsGrabber(document.width(), document.height())) {
                    resources.setProjectRoot(arguments.scene().toAbsolutePath().getParent());
                    resources.validate(document);
                    if (StageplayTool.usesLiveWorld(document)) resources.setLiveWorld(StageplayLiveWorld.adoptOffline(
                            library, com.openggf.game.session.SessionManager.getCurrentGameplayMode()));
                    resources.resolveFrame(document, StageplayTimeline.sample(document, 0));
                    cues.prepare(document);
                    var renderer = new StageplayRenderer(resources);
                    var encoder = new FfmpegEncoder(arguments.ffmpeg(), arguments.scale());
                    int rate = audio.outputSampleRate();
                    audio.beginCaptureMode(rate, StageplayDocument.FRAMES_PER_SECOND);
                    try {
                        short[] pcm = new short[(rate / 60 + 2) * 2];
                        float[] projection = SceneSurface.projection(document.width(), document.height());
                        int[] viewport = {0, 0, document.width(), document.height()};
                        renderFrames(document, staged, rate, encoder, frame -> {
                            boolean boundary = frame > 0 && document.sceneChanges().stream().anyMatch(c -> c.frame() == frame);
                            if (boundary && StageplayTool.usesLiveWorld(document)) audio.endCaptureMode();
                            var actual = resources.resolveFrame(document, StageplayTimeline.sample(document, frame));
                            if (boundary && StageplayTool.usesLiveWorld(document)) {
                                cues.refreshAfterNativeBoundary(frame);
                                audio.beginCaptureMode(rate, StageplayDocument.FRAMES_PER_SECOND);
                            }
                            cues.playFrame(document, frame);
                            HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                            int frames = audio.drainCaptureFrame(pcm);
                            if (actual.world() != null) resources.drawLiveWorld(actual);
                            surface.record(document.width(), document.height(), canvas -> {
                                if (actual.world() != null) renderer.drawLiveOverlay(document, actual, canvas);
                                else renderer.draw(document, actual, canvas);
                            });
                            org.lwjgl.opengl.GL11.glViewport(0, 0, document.width(), document.height());
                            surface.render(projection, viewport);
                            return new CapturedFrame(grabber.grab(), document.width(), document.height(), pcm, frames, frame);
                        });
                    } finally { audio.endCaptureMode(); }
                }
            }
            // Same-filesystem hard-link publication is atomic and refuses an existing destination.
            Files.createLink(output, staged);
            return output;
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    /** Takes cleanup ownership only after verifying a dedicated empty directory. */
    static Path prepareStagingDirectory(Path parent, Path supplied) throws IOException {
        if (supplied == null) return Files.createTempDirectory(parent, ".stageplay-export-");
        Path normalized = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Staging path must be an existing dedicated directory");
        try (var entries = Files.list(normalized)) {
            if (entries.findAny().isPresent()) throw new IOException("Staging directory must be empty");
        }
        return normalized;
    }

    @FunctionalInterface public interface FrameSource { CapturedFrame frame(int frame) throws IOException; }

    /** Exact frame/sample contract shared by capture and tests; cancellation never finalizes a partial film. */
    public static void renderFrames(StageplayDocument document, Path output, int sampleRate,
                                    CaptureEncoder encoder, FrameSource frames) throws IOException {
        var sink = new EncoderSink(encoder, BackpressurePolicy.BLOCK, 4);
        long totalSamples = 0;
        try {
            sink.open(output, document.width(), document.height(), 60, sampleRate);
            for (int frame = 0; frame < document.durationFrames(); frame++) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Stageplay export cancelled");
                var packet = frames.frame(frame);
                if (packet.frameIndex() != frame || packet.width() != document.width() || packet.height() != document.height())
                    throw new IOException("Capture source returned the wrong frame");
                totalSamples += packet.sampleCount();
                long expected = (long) (frame + 1) * sampleRate / 60;
                if (totalSamples != expected) throw new IOException("Audio/video sample clocks diverged at frame " + frame);
                sink.submit(packet);
            }
            sink.stop();
        } catch (Error failure) {
            sink.abort(); throw failure;
        } catch (Exception failure) {
            sink.abort();
            if (failure instanceof IOException io) throw io;
            throw new IOException("Stageplay export failed: " + failure.getMessage(), failure);
        }
    }

    private static RomIdentity identity(String game) { return switch (game) { case "s1" -> RomIdentity.S1; case "s2" -> RomIdentity.S2; default -> RomIdentity.S3K; }; }
    private static SonicConfiguration romKey(String game) { return switch (game) { case "s1" -> SonicConfiguration.SONIC_1_ROM; case "s2" -> SonicConfiguration.SONIC_2_ROM; default -> SonicConfiguration.SONIC_3K_ROM; }; }
}
