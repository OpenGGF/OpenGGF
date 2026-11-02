package com.openggf.tools;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Line protocol between the editor's route tape job and the isolated {@link RouteEditorWorker}.
 * Pure text: it holds no engine state. Free text travels as Base64 so a record is one line.
 *
 * <p>Request (stdin): {@link #MAGIC}, then {@code kind}, {@code manifest}, {@code script},
 * optional {@code target}/{@code replace}, one {@code rom <game> <path>} per game, then {@code end}.
 * Response (stdout): {@code progress}, {@code point} and finally one {@code result}, with
 * {@code saved} before it when a save wrote the route.
 */
public final class RouteEditorProtocol {
    public static final String MAGIC = "openggf-route-worker/1";
    /** Longest accepted record; a script record carries the whole tape. */
    public static final int MAX_RECORD_CHARS = 64 * 1024 * 1024;

    public enum Kind { REPLAY, SAVE }

    public enum Verdict { PASSED, FAILED, UNSUPPORTED }

    /**
     * @param manifest the route manifest that supplies launch, provenance and native facts
     * @param script   the working rows as route-script text
     * @param target   for {@link Kind#SAVE}: the stem to write
     * @param replace  for {@link Kind#SAVE}: replace an existing route rather than write a new one
     */
    public record Request(Kind kind, Path manifest, String script, Path target, boolean replace, Map<String, Path> roms) {
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(script, "script");
            roms = Map.copyOf(roms);
            if (kind == Kind.SAVE && target == null) throw new IllegalArgumentException("a save names its target stem");
        }
    }

    /** Pass {@code pass} of {@code passes} has played {@code done} of {@code total} rows. */
    public record Progress(int pass, int passes, int done, int total) { }

    /** A post-row centre for participant {@code slot} (1 or 2). */
    public record Point(int slot, int row, long epoch, int x, int y, boolean air) { }

    /**
     * @param row     rows completed (PASSED) or the first row that could not complete (FAILED)
     * @param detail  environment (PASSED) or reason
     * @param content content hash replayed
     */
    public record Result(Verdict verdict, int row, String detail, String content) { }

    private RouteEditorProtocol() {
    }

    public static String encode(Request request) {
        var out = new StringBuilder(MAGIC).append('\n');
        out.append("kind ").append(request.kind()).append('\n');
        out.append("manifest ").append(text(request.manifest().toString())).append('\n');
        out.append("script ").append(text(request.script())).append('\n');
        if (request.target() != null) out.append("target ").append(text(request.target().toString())).append('\n');
        out.append("replace ").append(request.replace()).append('\n');
        request.roms().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(rom ->
                out.append("rom ").append(rom.getKey()).append(' ').append(text(rom.getValue().toString())).append('\n'));
        return out.append("end\n").toString();
    }

    public static Request decodeRequest(LineReader input) throws IOException {
        if (!MAGIC.equals(input.readLine(MAGIC.length()))) throw new IOException("not a route worker request");
        Kind kind = null;
        Path manifest = null, target = null;
        String script = null;
        boolean replace = false;
        var roms = new LinkedHashMap<String, Path>();
        for (String line; (line = input.readLine(MAX_RECORD_CHARS)) != null; ) {
            if (line.equals("end")) {
                return new Request(kind, manifest, script, target, replace, roms);
            }
            String[] field = line.split(" ", 3);
            switch (field[0]) {
                case "kind" -> kind = Kind.valueOf(field[1]);
                case "manifest" -> manifest = Path.of(untext(field[1]));
                case "script" -> script = untext(field[1]);
                case "target" -> target = Path.of(untext(field[1]));
                case "replace" -> replace = Boolean.parseBoolean(field[1]);
                case "rom" -> {
                    if (field.length != 3 || roms.putIfAbsent(field[1], Path.of(untext(field[2]))) != null) {
                        throw new IOException("bad rom record");
                    }
                }
                default -> throw new IOException("unknown request record " + field[0]);
            }
        }
        throw new IOException("route worker request ended early");
    }

    public static String progress(Progress progress) {
        return "progress " + progress.pass() + ' ' + progress.passes() + ' ' + progress.done() + ' ' + progress.total();
    }

    public static String point(Point point) {
        return "point " + point.slot() + ' ' + point.row() + ' ' + point.epoch() + ' ' + point.x() + ' ' + point.y() + ' '
                + (point.air() ? 1 : 0);
    }

    public static String saved(Path manifest) {
        return "saved " + text(manifest.toString());
    }

    public static String result(Result result) {
        return "result " + result.verdict() + ' ' + result.row() + ' ' + text(result.detail()) + ' '
                + text(result.content());
    }

    /** One decoded response record: exactly one of the fields is set. */
    public record Response(Progress progress, Point point, Path saved, Result result) { }

    public static Response decodeResponse(String line) throws IOException {
        String[] field = line.split(" ");
        try {
            return switch (field[0]) {
                case "progress" -> new Response(new Progress(Integer.parseInt(field[1]), Integer.parseInt(field[2]),
                        Integer.parseInt(field[3]), Integer.parseInt(field[4])), null, null, null);
                case "point" -> new Response(null, new Point(Integer.parseInt(field[1]), Integer.parseInt(field[2]),
                        Long.parseLong(field[3]), Integer.parseInt(field[4]), Integer.parseInt(field[5]),
                        field[6].equals("1")), null, null);
                case "saved" -> new Response(null, null, Path.of(untext(field[1])), null);
                case "result" -> new Response(null, null, null, new Result(Verdict.valueOf(field[1]),
                        Integer.parseInt(field[2]), untext(field[3]), untext(field[4])));
                default -> throw new IOException("unknown route worker record " + field[0]);
            };
        } catch (RuntimeException malformed) {
            throw new IOException("malformed route worker record: " + field[0], malformed);
        }
    }

    private static String text(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untext(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    /** Bounded line input: limits apply while reading, before a whole record is allocated. */
    public static final class LineReader implements AutoCloseable {
        private final Reader reader;
        private final char[] buffer = new char[8192];
        private int position, limit;

        public LineReader(Reader reader) {
            this.reader = Objects.requireNonNull(reader, "reader");
        }

        /** The next LF-terminated record, without its terminator; null at end of input. */
        public String readLine(int maximumCharacters) throws IOException {
            var line = new StringBuilder(Math.min(8192, Math.max(16, maximumCharacters)));
            while (true) {
                if (position == limit) {
                    limit = reader.read(buffer);
                    position = 0;
                    if (limit < 0) return line.isEmpty() ? null : line.toString();
                }
                int start = position;
                while (position < limit) {
                    if (buffer[position++] == '\n') {
                        line.append(buffer, start, position - start - 1);
                        if (line.length() > maximumCharacters) throw new IOException("route worker record exceeds its bound");
                        return line.toString();
                    }
                }
                line.append(buffer, start, position - start);
                if (line.length() > maximumCharacters) throw new IOException("route worker record exceeds its bound");
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    /** Row spacing of streamed path points: every row up to {@link #MAX_PATH_POINTS} rows, then evenly spaced. */
    public static int pathStride(int rows) {
        return Math.max(1, (rows + MAX_PATH_POINTS - 1) / MAX_PATH_POINTS);
    }

    /** Points streamed per participant, bounding the editor's overlay memory and the pipe. */
    public static final int MAX_PATH_POINTS = 100_000;
}
