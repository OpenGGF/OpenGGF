package com.openggf.tools;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Line protocol between the editor's route tape job and the isolated {@link RouteEditorWorker}.
 * Pure text: it holds no engine state. Free text travels as Base64 so a record is one line.
 *
 * <p>Request (stdin): {@link #MAGIC}, then {@code kind}, {@code route} (the {@code RouteCodec}
 * transport text, rows inline), optional {@code target}/{@code replace} for a save, optional
 * {@code reference} and {@code repair} for a repair, one {@code rom <game> <path>} per game, then
 * {@code end}. Response (stdout): {@code progress}, {@code plan}, {@code point} and {@code span}
 * records, {@code saved}/{@code rows} where they apply, then exactly one {@code result}.
 */
public final class RouteEditorProtocol {
    public static final String MAGIC = "openggf-route-worker/2";
    /** Longest accepted record; a route record carries the whole tape. */
    public static final int MAX_RECORD_CHARS = 64 * 1024 * 1024;

    public enum Kind { REPLAY, SAVE, REPAIR, PLAN, SCAN }

    public enum Verdict { PASSED, FAILED, UNSUPPORTED, SOLVED, STOPPED }

    /**
     * Repair bounds: rows {@code [0, firstEditable)} stay verbatim; the reference is followed from
     * its index {@code windowFrom} until {@code windowTo} ({@code -1}: its last row).
     */
    public record Repair(int firstEditable, int maxAuthoredRows, int maxWindows, int windowFrom, int windowTo) { }

    /**
     * @param route     the working route as {@code RouteCodec} transport text (launch, goals, rows)
     * @param target    for {@link Kind#SAVE}: the stem to write
     * @param replace   for {@link Kind#SAVE}: replace an existing route rather than write a new one
     * @param reference for {@link Kind#REPAIR}: the reference route (launch and rows) as transport text
     */
    /**
     * @param row for {@link Kind#PLAN}: rows {@code [0, row)} are kept as the prefix the goals
     *            continue from; for {@link Kind#SCAN}: the row after which live objects are listed
     */
    public record Request(Kind kind, String route, Path target, boolean replace, String reference, Repair repair,
                          int row, Map<String, Path> roms) {
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(route, "route");
            roms = Map.copyOf(roms);
            if (kind == Kind.SAVE && target == null) throw new IllegalArgumentException("a save names its target stem");
            if (kind == Kind.REPAIR && (reference == null || repair == null)) {
                throw new IllegalArgumentException("a repair names its reference and bounds");
            }
            if ((kind == Kind.PLAN || kind == Kind.SCAN) && row < 0) throw new IllegalArgumentException("negative row");
        }

        public Request(Kind kind, String route, Path target, boolean replace, String reference, Repair repair,
                       Map<String, Path> roms) {
            this(kind, route, target, replace, reference, repair, 0, roms);
        }

        public Request(Kind kind, String route, Path target, boolean replace, Map<String, Path> roms) {
            this(kind, route, target, replace, null, null, 0, roms);
        }
    }

    /**
     * A live object a scan observed: its type key, placement (layout index or -1), centre,
     * capabilities, phases and hit points, copied from the replay sample.
     */
    public record ScannedObject(String typeKey, int zone, int act, int layoutIndex, int objectId, int subtype, int x, int y,
                                List<String> capabilities, List<String> phaseNames, String phase, int hitsRemaining,
                                boolean defeated) {
        public ScannedObject {
            capabilities = List.copyOf(capabilities);
            phaseNames = List.copyOf(phaseNames);
        }
    }

    /** Pass {@code pass} of {@code passes} has played {@code done} of {@code total} rows. */
    public record Progress(int pass, int passes, int done, int total) { }

    /** Planner counts: {@code bestProgress} of {@code target} reference indices; best prefix {@code bestRow} rows. */
    public record Plan(String phase, long expansions, int bestRow, int bestProgress, int target) { }

    /** A post-row centre for participant {@code slot} (1 or 2). */
    public record Point(int slot, int row, long epoch, int x, int y, boolean air) { }

    /** A repair output span: {@code KEPT} original rows, {@code REFERENCE} rows or {@code SEARCHED} rows. */
    public record Span(String kind, int sourceStart, int outputStart, int length) { }

    /**
     * @param row     rows completed (PASSED/SOLVED), the first row that could not complete
     *                (FAILED) or the furthest row a stopped repair held (STOPPED)
     * @param detail  environment (PASSED) or reason
     * @param content content hash replayed or produced
     */
    public record Result(Verdict verdict, int row, String detail, String content) { }

    private RouteEditorProtocol() {
    }

    public static String encode(Request request) {
        var out = new StringBuilder(MAGIC).append('\n');
        out.append("kind ").append(request.kind()).append('\n');
        out.append("route ").append(text(request.route())).append('\n');
        if (request.target() != null) out.append("target ").append(text(request.target().toString())).append('\n');
        out.append("replace ").append(request.replace()).append('\n');
        out.append("row ").append(request.row()).append('\n');
        if (request.reference() != null) out.append("reference ").append(text(request.reference())).append('\n');
        if (request.repair() != null) {
            var r = request.repair();
            out.append("repair ").append(r.firstEditable()).append(' ').append(r.maxAuthoredRows()).append(' ')
                    .append(r.maxWindows()).append(' ').append(r.windowFrom()).append(' ').append(r.windowTo()).append('\n');
        }
        request.roms().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(rom ->
                out.append("rom ").append(rom.getKey()).append(' ').append(text(rom.getValue().toString())).append('\n'));
        return out.append("end\n").toString();
    }

    public static Request decodeRequest(LineReader input) throws IOException {
        if (!MAGIC.equals(input.readLine(MAGIC.length()))) throw new IOException("not a route worker request");
        Kind kind = null;
        Path target = null;
        String route = null, reference = null;
        Repair repair = null;
        boolean replace = false;
        int row = 0;
        var roms = new LinkedHashMap<String, Path>();
        for (String line; (line = input.readLine(MAX_RECORD_CHARS)) != null; ) {
            if (line.equals("end")) {
                return new Request(kind, route, target, replace, reference, repair, row, roms);
            }
            String[] field = line.split(" ", 3);
            try {
                switch (field[0]) {
                    case "kind" -> kind = Kind.valueOf(field[1]);
                    case "route" -> route = untext(field[1]);
                    case "target" -> target = Path.of(untext(field[1]));
                    case "replace" -> replace = Boolean.parseBoolean(field[1]);
                    case "row" -> row = Integer.parseInt(field[1]);
                    case "reference" -> reference = untext(field[1]);
                    case "repair" -> {
                        String[] n = line.split(" ");
                        repair = new Repair(Integer.parseInt(n[1]), Integer.parseInt(n[2]), Integer.parseInt(n[3]),
                                Integer.parseInt(n[4]), Integer.parseInt(n[5]));
                    }
                    case "rom" -> {
                        if (field.length != 3 || roms.putIfAbsent(field[1], Path.of(untext(field[2]))) != null) {
                            throw new IOException("bad rom record");
                        }
                    }
                    default -> throw new IOException("unknown request record " + field[0]);
                }
            } catch (RuntimeException malformed) {
                throw new IOException("malformed request record " + field[0], malformed);
            }
        }
        throw new IOException("route worker request ended early");
    }

    public static String progress(Progress progress) {
        return "progress " + progress.pass() + ' ' + progress.passes() + ' ' + progress.done() + ' ' + progress.total();
    }

    public static String plan(Plan plan) {
        return "plan " + text(plan.phase()) + ' ' + plan.expansions() + ' ' + plan.bestRow() + ' ' + plan.bestProgress()
                + ' ' + plan.target();
    }

    public static String point(Point point) {
        return "point " + point.slot() + ' ' + point.row() + ' ' + point.epoch() + ' ' + point.x() + ' ' + point.y() + ' '
                + (point.air() ? 1 : 0);
    }

    public static String span(Span span) {
        return "span " + span.kind() + ' ' + span.sourceStart() + ' ' + span.outputStart() + ' ' + span.length();
    }

    public static String saved(Path manifest) {
        return "saved " + text(manifest.toString());
    }

    /** A planned route (launch, goals and rows) as {@code RouteCodec} transport text. */
    public static String plannedRoute(String transport) {
        return "planned " + text(transport);
    }

    public static String object(ScannedObject o) {
        String body = String.join("\u001f", o.typeKey(), String.valueOf(o.zone()), String.valueOf(o.act()),
                String.valueOf(o.layoutIndex()), String.valueOf(o.objectId()),
                String.valueOf(o.subtype()), String.valueOf(o.x()), String.valueOf(o.y()), String.join(",", o.capabilities()),
                String.join(",", o.phaseNames()), o.phase(), String.valueOf(o.hitsRemaining()), String.valueOf(o.defeated()));
        return "object " + text(body);
    }

    /** The rows a repair produced (solved) or the furthest attempt it held (stopped), as route-script text. */
    public static String rows(String script) {
        return "rows " + text(script);
    }

    public static String result(Result result) {
        return "result " + result.verdict() + ' ' + result.row() + ' ' + text(result.detail()) + ' '
                + text(result.content());
    }

    /** One decoded response record: exactly one of the fields is set. */
    public record Response(Progress progress, Plan plan, Point point, Span span, Path saved, String rows,
                           Result result, String planned, ScannedObject object) {
        Response(Progress progress, Plan plan, Point point, Span span, Path saved, String rows, Result result) {
            this(progress, plan, point, span, saved, rows, result, null, null);
        }
    }

    public static Response decodeResponse(String line) throws IOException {
        String[] field = line.split(" ");
        try {
            return switch (field[0]) {
                case "progress" -> new Response(new Progress(Integer.parseInt(field[1]), Integer.parseInt(field[2]),
                        Integer.parseInt(field[3]), Integer.parseInt(field[4])), null, null, null, null, null, null);
                case "plan" -> new Response(null, new Plan(untext(field[1]), Long.parseLong(field[2]),
                        Integer.parseInt(field[3]), Integer.parseInt(field[4]), Integer.parseInt(field[5])),
                        null, null, null, null, null);
                case "point" -> new Response(null, null, new Point(Integer.parseInt(field[1]), Integer.parseInt(field[2]),
                        Long.parseLong(field[3]), Integer.parseInt(field[4]), Integer.parseInt(field[5]),
                        field[6].equals("1")), null, null, null, null);
                case "span" -> new Response(null, null, null, new Span(field[1], Integer.parseInt(field[2]),
                        Integer.parseInt(field[3]), Integer.parseInt(field[4])), null, null, null);
                case "saved" -> new Response(null, null, null, null, Path.of(untext(field[1])), null, null);
                case "rows" -> new Response(null, null, null, null, null, untext(field[1]), null);
                case "result" -> new Response(null, null, null, null, null, null, new Result(Verdict.valueOf(field[1]),
                        Integer.parseInt(field[2]), untext(field[3]), untext(field[4])));
                case "planned" -> new Response(null, null, null, null, null, null, null, untext(field[1]), null);
                case "object" -> {
                    String[] o = untext(field[1]).split("\u001f", -1);
                    yield new Response(null, null, null, null, null, null, null, null, new ScannedObject(o[0],
                            Integer.parseInt(o[1]), Integer.parseInt(o[2]), Integer.parseInt(o[3]), Integer.parseInt(o[4]),
                            Integer.parseInt(o[5]), Integer.parseInt(o[6]), Integer.parseInt(o[7]),
                            o[8].isEmpty() ? List.of() : List.of(o[8].split(",")),
                            o[9].isEmpty() ? List.of() : List.of(o[9].split(",")), o[10], Integer.parseInt(o[11]),
                            Boolean.parseBoolean(o[12])));
                }
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

    /** Row spacing of streamed path points: every row up to {@link #MAX_PATH_POINTS} rows, then evenly spaced. */
    public static int pathStride(int rows) {
        return Math.max(1, (rows + MAX_PATH_POINTS - 1) / MAX_PATH_POINTS);
    }

    /** Points streamed per participant, bounding the editor's overlay memory and the pipe. */
    public static final int MAX_PATH_POINTS = 100_000;

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
}
