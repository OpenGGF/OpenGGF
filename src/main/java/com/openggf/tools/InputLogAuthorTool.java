package com.openggf.tools;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.game.recording.UserRecordingWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a BizHawk input log (or a minimal {@code .bk2}) from an
 * {@link InputScriptCompiler} script, then re-parses it with the production
 * {@link Bk2MovieLoader} so the file is proven loadable before anything drives it.
 *
 * <p>Inputs: {@code --script <file>} or {@code --inline "<script>"}; {@code --out <path>}
 * whose extension picks the container ({@code .bk2} zip, anything else plain
 * {@code Input Log.txt} text); optional {@code --game <id>} for the BK2 header.
 * Originating task: FBZ2 {@code $1DC0} squeeze capture, 2026-09-13.
 *
 * <pre>
 * mvn exec:java "-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool" \
 *   "-Dexec.args=--inline '60 R; 30 D+R; 1 A' --out target/capture/squeeze.txt"
 * </pre>
 */
public final class InputLogAuthorTool {

    private InputLogAuthorTool() {
    }

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        Result result = author(arguments.script(), arguments.out(), arguments.game());
        System.out.println("Wrote " + result.output().toAbsolutePath() + " (" + result.frameCount() + " frames)");
        System.out.println("Sequence: " + result.summary());
    }

    /** Compiles, writes, and verifies; returns the verified frame count and summary. */
    public static Result author(String script, Path out, String gameName) throws IOException {
        List<RecordedFrameInput> frames = InputScriptCompiler.compile(script);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("Script compiles to zero frames");
        }
        String inputLog = UserRecordingWriter.inputLogText(frames);
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (out.toString().toLowerCase(Locale.ROOT).endsWith(".bk2")) {
            writeBk2(out, gameName, frames.size(), inputLog);
        } else {
            Files.writeString(out, inputLog, StandardCharsets.UTF_8);
        }
        Bk2Movie verified = new Bk2MovieLoader().loadMovieOrInputLog(out);
        if (verified.getFrameCount() != frames.size()) {
            throw new IOException("Round-trip mismatch: wrote " + frames.size()
                    + " frames but the loader read " + verified.getFrameCount());
        }
        for (int i = 0; i < frames.size(); i++) {
            RecordedFrameInput expected = frames.get(i);
            var actual = verified.getFrame(i);
            if (actual.p1InputMask() != expected.p1InputMask()
                    || actual.p1ActionMask() != expected.p1ActionMask()
                    || actual.p1StartPressed() != expected.p1Start()
                    || actual.p2InputMask() != expected.p2InputMask()
                    || actual.p2ActionMask() != expected.p2ActionMask()
                    || actual.p2StartPressed() != expected.p2Start()) {
                throw new IOException("Round-trip mismatch at frame " + i + ": " + actual.rawLine());
            }
        }
        return new Result(out, frames.size(), InputScriptCompiler.summarize(frames));
    }

    private static void writeBk2(Path out, String gameName, int frameCount, String inputLog) throws IOException {
        String header = "MovieVersion BizHawk v2.0.0\n"
                + "Author OpenGGF InputLogAuthorTool\n"
                + "Core Genplus-gx\n"
                + "Platform GEN\n"
                + "GameName " + (gameName == null ? "" : gameName) + "\n"
                + "Frames " + frameCount + "\n";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            writeEntry(zip, "Header.txt", header);
            writeEntry(zip, "Input Log.txt", inputLog);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public record Result(Path output, int frameCount, String summary) {
    }

    record Arguments(String script, Path out, String game) {
        static Arguments parse(String[] argv) throws IOException {
            String script = null;
            Path out = null;
            String game = null;
            for (int i = 0; i < argv.length; i++) {
                switch (argv[i]) {
                    case "--script" -> script = Files.readString(
                            Path.of(CliArguments.requireValue(argv, ++i, "--script")), StandardCharsets.UTF_8);
                    case "--inline" -> script = CliArguments.requireValue(argv, ++i, "--inline");
                    case "--out" -> out = Path.of(CliArguments.requireValue(argv, ++i, "--out"));
                    case "--game" -> game = CliArguments.requireValue(argv, ++i, "--game");
                    default -> throw new IllegalArgumentException("Unknown argument: " + argv[i]);
                }
            }
            if (script == null) {
                throw new IllegalArgumentException("--script <file> or --inline <text> is required");
            }
            if (out == null) {
                throw new IllegalArgumentException("--out <path> is required");
            }
            return new Arguments(script, out, game);
        }
    }
}
