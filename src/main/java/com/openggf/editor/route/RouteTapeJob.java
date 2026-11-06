package com.openggf.editor.route;

import com.openggf.route.InputTape;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteCodec;
import com.openggf.route.RouteFiles;
import com.openggf.tools.RouteEditorProtocol;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One route tape job (replay or qualified save) in an isolated worker JVM. The engine route host
 * is process-global, so nothing here boots a world in the editor's session. Updates carry a
 * generation; a cancelled or superseded job can never publish a result.
 */
public final class RouteTapeJob implements AutoCloseable {
    public enum Phase { IDLE, RUNNING, PASSED, FAILED, UNSUPPORTED, ERROR, CANCELLED, SOLVED, STOPPED, SCANNED }

    /**
     * Replay only; replay and then write the route when it passes; repair it against a reference;
     * plan rows for its goal program after a kept prefix; or list the live objects at a row.
     */
    public enum Kind { REPLAY, SAVE, REPAIR, PLAN, SCAN }

    /**
     * A live object a scan saw: its type key, placement ({@code layoutIndex} -1 when spawned at
     * runtime), centre, capabilities, phases and hit points. Values only.
     */
    public record LiveObject(String typeKey, int zone, int act, int layoutIndex, int objectId, int subtype, int x, int y,
                             List<String> capabilities, List<String> phaseNames, String phase, int hitsRemaining,
                             boolean defeated) {
        public LiveObject {
            capabilities = List.copyOf(capabilities);
            phaseNames = List.copyOf(phaseNames);
        }

        public boolean placed() {
            return layoutIndex >= 0;
        }
    }

    /**
     * Repair bounds: rows {@code [0, firstEditable)} stay verbatim; the reference is followed from
     * its index {@code windowFrom} until {@code windowTo} ({@code -1}: its last row).
     */
    public record RepairBounds(int firstEditable, int maxAuthoredRows, int maxWindows, int windowFrom, int windowTo) {
        public RepairBounds {
            if (firstEditable < 0 || maxAuthoredRows < 1 || maxWindows < 1 || windowFrom < 0 || windowTo < -1) {
                throw new IllegalArgumentException("invalid repair bounds");
            }
        }
    }

    /** One output span of a repair, in output rows: kept original rows, followed reference rows or searched rows. */
    public record RepairSpan(RouteTapeTimeline.MarkerKind kind, int sourceStart, int outputStart, int length) { }

    /**
     * What a repair produced. A solved repair carries its qualified rows and spans; a stopped one
     * carries its furthest attempt, an unqualified diagnostic for inspection.
     *
     * @param originalRows the original route's row count (solved)
     */
    public record RepairOutcome(boolean solved, InputTape rows, List<RepairSpan> spans, int originalRows, String detail) {
        public RepairOutcome {
            spans = List.copyOf(spans);
        }

        public int keptRows() {
            return spans.stream().filter(span -> span.kind() == RouteTapeTimeline.MarkerKind.KEPT)
                    .mapToInt(RepairSpan::length).sum();
        }

        public int searchedRows() {
            return spans.stream().filter(span -> span.kind() == RouteTapeTimeline.MarkerKind.SEARCHED)
                    .mapToInt(RepairSpan::length).sum();
        }

        /** Kept original rows as a share of the original's length. */
        public double keptShare() {
            return originalRows == 0 ? 0 : keptRows() / (double) originalRows;
        }
    }

    /**
     * @param route   the working route (launch, goals, rows); it travels as codec transport text
     * @param target  for a save: the stem to write
     * @param replace for a save: replace the existing route rather than write a new one
     */
    /** A reference recorded by an earlier replay: the track file and the exact movie it was recorded from. */
    public record RecordedTrack(Path track, Path movie) {
        public RecordedTrack {
            Objects.requireNonNull(track, "track");
            Objects.requireNonNull(movie, "movie");
        }
    }

    public record Request(Kind kind, RouteArtifact route, Path target, boolean replace, RouteArtifact reference,
                          RepairBounds repair, int row, RecordedTrack recorded) {
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(route, "route");
            if (kind == Kind.SAVE && target == null) throw new IllegalArgumentException("a save names its target stem");
            if (kind == Kind.REPAIR && (repair == null || (reference == null) == (recorded == null))) {
                throw new IllegalArgumentException("a repair names its bounds and either a reference or a recorded track");
            }
            if (row < 0 || row > route.tape().size()) throw new IllegalArgumentException("row outside the route: " + row);
        }

        public Request(Kind kind, RouteArtifact route, Path target, boolean replace) {
            this(kind, route, target, replace, null, null, 0, null);
        }

        /** Repairs {@code route}, keeping its rows where play tracks {@code reference}. */
        public static Request repair(RouteArtifact route, RouteArtifact reference, RepairBounds bounds) {
            return new Request(Kind.REPAIR, route, null, false, reference, bounds, 0, null);
        }

        /** Repairs {@code route} along a track recorded by an earlier replay of its movie. */
        public static Request repair(RouteArtifact route, RecordedTrack recorded, RepairBounds bounds) {
            return new Request(Kind.REPAIR, route, null, false, null, bounds, 0, recorded);
        }

        /** Plans rows after {@code [0, row)} that complete {@code route}'s goal program. */
        public static Request plan(RouteArtifact route, int row) {
            return new Request(Kind.PLAN, route, null, false, null, null, row, null);
        }

        /** Lists the live objects with capabilities after {@code row} rows of {@code route}. */
        public static Request scan(RouteArtifact route, int row) {
            return new Request(Kind.SCAN, route, null, false, null, null, row, null);
        }
    }

    /**
     * @param content the working content the job replayed; a result for other content is stale
     * @param row     rows replayed when PASSED, the first row that could not complete when FAILED
     * @param path    the observed path, once the job completed
     * @param saved   the written manifest, after a qualified save
     */
    public record Update(long generation, Phase phase, Kind kind, int pass, int passes, int done, int total,
                         String message, String content, int row, RouteTapeOverlay path, Path saved,
                         RepairOutcome repair, RouteArtifact planned, List<LiveObject> objects) {
        public Update {
            objects = objects == null ? List.of() : List.copyOf(objects);
        }

        public Update(long generation, Phase phase, Kind kind, int pass, int passes, int done, int total,
                      String message, String content, int row, RouteTapeOverlay path, Path saved, RepairOutcome repair) {
            this(generation, phase, kind, pass, passes, done, total, message, content, row, path, saved, repair, null,
                    List.of());
        }

        public Update(long generation, Phase phase, Kind kind, int pass, int passes, int done, int total,
                      String message, String content, int row, RouteTapeOverlay path, Path saved) {
            this(generation, phase, kind, pass, passes, done, total, message, content, row, path, saved, null);
        }

        static Update idle() {
            return new Update(0, Phase.IDLE, null, 0, 0, 0, 0, "", null, -1, null, null);
        }

        public boolean running() {
            return phase == Phase.RUNNING;
        }

        /** Played fraction across passes, 0..1. */
        public float fraction() {
            if (passes == 0 || total == 0) return phase == Phase.RUNNING ? 0 : 1;
            return Math.min(1, ((pass - 1) + done / (float) total) / passes);
        }
    }

    @FunctionalInterface
    interface Launcher {
        Process start(List<String> command) throws IOException;
    }

    private static final int STDERR_LINES = 12;
    private static final long WALL_LIMIT_MINUTES = 60;

    private final Launcher launcher;
    private final Supplier<Map<String, Path>> roms;
    private volatile Update update = Update.idle();
    private long generation;
    private Thread task;
    private Process process;

    /** @param roms the configured ROM files by game id; the worker verifies each against the launch hash */
    public RouteTapeJob(Supplier<Map<String, Path>> roms) {
        this(roms, command -> {
            var builder = new ProcessBuilder(command);
            // A display-free GL context: the worker never connects to the editor's desktop.
            if (command.contains(SURFACELESS)) {
                builder.environment().remove("DISPLAY");
                builder.environment().remove("WAYLAND_DISPLAY");
            }
            return builder.start();
        });
    }

    private static final String SURFACELESS = "-Dopenggf.headless.gl=surfaceless-egl";

    /**
     * The worker's capture session needs GL. Linux uses display-free surfaceless EGL (an explicit
     * parent selection is passed through); other platforms keep the engine's hidden-window default.
     */
    static List<String> graphicsArguments() {
        String selected = System.getProperty("openggf.headless.gl");
        var arguments = new ArrayList<String>();
        if (selected != null && !selected.isBlank()) arguments.add("-Dopenggf.headless.gl=" + selected);
        else if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
            arguments.add(SURFACELESS);
        }
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            arguments.add("-XstartOnFirstThread");
        }
        return arguments;
    }

    RouteTapeJob(Supplier<Map<String, Path>> roms, Launcher launcher) {
        this.roms = Objects.requireNonNull(roms, "roms");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    public Update poll() {
        return update;
    }

    /** Starts {@code request}; one job runs at a time. Its result names the content it replayed. */
    public synchronized void start(Request request) throws IOException {
        String content = request.route().content().toString();
        int rows = request.route().tape().size();
        if (task != null && task.isAlive()) throw new IOException("Cancel the current route job before starting another");
        var bounds = request.repair();
        var wire = new RouteEditorProtocol.Request(switch (request.kind()) {
            case REPLAY -> RouteEditorProtocol.Kind.REPLAY;
            case SAVE -> RouteEditorProtocol.Kind.SAVE;
            case REPAIR -> RouteEditorProtocol.Kind.REPAIR;
            case PLAN -> RouteEditorProtocol.Kind.PLAN;
            case SCAN -> RouteEditorProtocol.Kind.SCAN;
        }, RouteCodec.formatTransport(request.route()), request.target(), request.replace(),
                request.reference() == null ? null : RouteCodec.formatTransport(request.reference()),
                bounds == null ? null : new RouteEditorProtocol.Repair(bounds.firstEditable(), bounds.maxAuthoredRows(),
                        bounds.maxWindows(), bounds.windowFrom(), bounds.windowTo()), request.row(),
                request.recorded() == null ? null : request.recorded().track().toAbsolutePath(),
                request.recorded() == null ? null : request.recorded().movie().toAbsolutePath(), roms.get());
        long token = ++generation;
        update = new Update(token, Phase.RUNNING, request.kind(), 1, 1, 0, rows, "Starting the route worker", content,
                -1, null, null);
        task = Thread.ofVirtual().name("route-tape-job").start(() -> {
            try {
                run(wire, request.kind(), content, rows, token);
            } catch (IOException | InterruptedException failure) {
                publish(token, new Update(token, Phase.ERROR, request.kind(), 0, 0, 0, rows,
                        Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()), content, -1, null,
                        null));
            }
        });
    }

    private void run(RouteEditorProtocol.Request request, Kind kind, String content, int rows, long token)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-Xmx2g");
        command.add("-Djava.awt.headless=true");
        command.addAll(graphicsArguments());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.openggf.tools.RouteEditorWorker");
        command.add("--worker");
        Process child = launcher.start(List.copyOf(command));
        synchronized (this) {
            process = child;
            if (token != generation) {
                retire(child);
                return;
            }
        }
        var tail = new ArrayDeque<String>();
        Thread stderr = Thread.ofVirtual().name("route-tape-diagnostics").start(() -> {
            try (var input = new RouteEditorProtocol.LineReader(new InputStreamReader(child.getErrorStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = readTail(input)) != null; ) synchronized (tail) {
                    if (tail.size() == STDERR_LINES) tail.removeFirst();
                    tail.addLast(line);
                }
            } catch (IOException ignored) {
                // The worker's exit status reports the failure; a broken diagnostic pipe adds nothing.
            }
        });
        try {
            try (var output = new BufferedWriter(new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8))) {
                output.write(RouteEditorProtocol.encode(request));
            }
            var p1 = new ArrayList<RouteTapeOverlay.Point>();
            var p2 = new ArrayList<RouteTapeOverlay.Point>();
            RouteEditorProtocol.Result result = null;
            Path saved = null;
            String rowsText = null;
            String plannedText = null;
            var spans = new ArrayList<RepairSpan>();
            var objects = new ArrayList<LiveObject>();
            try (var input = new RouteEditorProtocol.LineReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = input.readLine(64 * 1024)) != null; ) {
                    var record = RouteEditorProtocol.decodeResponse(line);
                    if (result != null) throw new IOException("Route worker sent records after its result");
                    if (record.progress() != null) {
                        var progress = record.progress();
                        publish(token, new Update(token, Phase.RUNNING, kind, progress.pass(), progress.passes(),
                                progress.done(), progress.total(), "Replaying the route", content, -1, null, null));
                    } else if (record.point() != null) {
                        var point = record.point();
                        if (p1.size() + p2.size() >= 2 * RouteEditorProtocol.MAX_PATH_POINTS + 2) {
                            throw new IOException("Route worker path exceeds its bound");
                        }
                        (point.slot() == 1 ? p1 : p2).add(new RouteTapeOverlay.Point(point.row(), point.epoch(),
                                point.x(), point.y(), point.air()));
                    } else if (record.plan() != null) {
                        var plan = record.plan();
                        publish(token, new Update(token, Phase.RUNNING, kind, plan.phase().equals("qualify") ? 2 : 1, 2,
                                Math.max(0, plan.bestProgress()), Math.max(1, plan.target()), plan.phase().equals("qualify")
                                ? "Qualifying the repaired rows" : "Searching: " + plan.expansions() + " expansions",
                                content, -1, null, null));
                    } else if (record.span() != null) {
                        var span = record.span();
                        spans.add(new RepairSpan(switch (span.kind()) {
                            case "KEPT" -> RouteTapeTimeline.MarkerKind.KEPT;
                            case "REFERENCE" -> RouteTapeTimeline.MarkerKind.REFERENCE;
                            case "SEARCHED" -> RouteTapeTimeline.MarkerKind.SEARCHED;
                            default -> throw new IOException("unknown repair span " + span.kind());
                        }, span.sourceStart(), span.outputStart(), span.length()));
                    } else if (record.rows() != null) {
                        rowsText = record.rows();
                    } else if (record.planned() != null) {
                        plannedText = record.planned();
                    } else if (record.object() != null) {
                        var o = record.object();
                        if (objects.size() >= 4096) throw new IOException("Route worker scan exceeds its bound");
                        objects.add(new LiveObject(o.typeKey(), o.zone(), o.act(), o.layoutIndex(), o.objectId(), o.subtype(),
                                o.x(), o.y(),
                                o.capabilities(), o.phaseNames(), o.phase(), o.hitsRemaining(), o.defeated()));
                    } else if (record.saved() != null) {
                        saved = record.saved();
                    } else {
                        result = record.result();
                    }
                }
            }
            if (!child.waitFor(WALL_LIMIT_MINUTES, TimeUnit.MINUTES)) throw new IOException("Route worker exceeded its wall-clock limit");
            stderr.join(2000);
            if (child.exitValue() != 0 || result == null) {
                String diagnostics;
                synchronized (tail) { diagnostics = String.join(" | ", tail); }
                throw new IOException("Route worker failed (exit " + child.exitValue() + "): " + diagnostics);
            }
            if (kind == Kind.REPAIR) {
                publish(token, repairUpdate(token, result, content, rowsText, spans));
                return;
            }
            if (kind == Kind.SCAN) {
                publish(token, result.verdict() == RouteEditorProtocol.Verdict.PASSED
                        ? new Update(token, Phase.SCANNED, kind, 1, 1, 1, 1, "scan", content, result.row(), null, null,
                                null, null, objects)
                        : new Update(token, Phase.UNSUPPORTED, kind, 0, 0, 0, 0, result.detail(), content, -1, null, null));
                return;
            }
            if (kind == Kind.PLAN) {
                var stridePath = new RouteTapeOverlay(p1, p2, 1, -1);
                publish(token, switch (result.verdict()) {
                    case SOLVED -> {
                        if (plannedText == null) throw new IOException("Route worker sent no planned route");
                        RouteArtifact planned = RouteCodec.parseTransport(plannedText);
                        if (!planned.content().toString().equals(result.content())) {
                            throw new IOException("Route worker's planned route does not match its result");
                        }
                        yield new Update(token, Phase.SOLVED, kind, 2, 2, 1, 1, result.detail(), result.content(),
                                planned.tape().size(), stridePath, null, null, planned, List.of());
                    }
                    case STOPPED -> new Update(token, Phase.STOPPED, kind, 2, 2, 1, 1, result.detail(), content,
                            result.row(), null, null, new RepairOutcome(false, rowsText == null ? InputTape.empty()
                                    : RouteFiles.tapeFromScript(rowsText), List.of(), 0, result.detail()));
                    case UNSUPPORTED -> new Update(token, Phase.UNSUPPORTED, kind, 0, 0, 0, 0, result.detail(), content,
                            -1, null, null);
                    default -> throw new IOException("Route worker sent a replay result to a plan");
                });
                return;
            }
            if (!result.content().equals(content)) throw new IOException("Route worker replayed other content");
            int stride = RouteEditorProtocol.pathStride(rows);
            int diverged = result.verdict() == RouteEditorProtocol.Verdict.FAILED ? result.row() : -1;
            var path = new RouteTapeOverlay(p1, p2, stride, diverged);
            Phase phase = switch (result.verdict()) {
                case PASSED -> Phase.PASSED;
                case FAILED -> Phase.FAILED;
                case UNSUPPORTED -> Phase.UNSUPPORTED;
                case SOLVED, STOPPED -> throw new IOException("Route worker sent a repair result to a replay");
            };
            publish(token, new Update(token, phase, kind, 1, 1, rows, rows, result.detail(), content, result.row(), path,
                    saved));
        } finally {
            retire(child);
            stderr.join(2000);
        }
    }

    /** A repair's update; the solved rows' content names what the planner qualified. */
    private static Update repairUpdate(long token, RouteEditorProtocol.Result result, String original, String rowsText,
                                       List<RepairSpan> spans) throws IOException {
        return switch (result.verdict()) {
            case SOLVED, STOPPED -> {
                if (rowsText == null) throw new IOException("Route worker sent no repair rows");
                boolean solved = result.verdict() == RouteEditorProtocol.Verdict.SOLVED;
                var outcome = new RepairOutcome(solved, RouteFiles.tapeFromScript(rowsText), spans,
                        solved ? result.row() : 0, result.detail());
                yield new Update(token, solved ? Phase.SOLVED : Phase.STOPPED, Kind.REPAIR, 2, 2, 1, 1, result.detail(),
                        solved ? result.content() : original, solved ? outcome.rows().size() : result.row(), null, null,
                        outcome);
            }
            case UNSUPPORTED -> new Update(token, Phase.UNSUPPORTED, Kind.REPAIR, 0, 0, 0, 0, result.detail(), original,
                    -1, null, null);
            default -> throw new IOException("Route worker sent a replay result to a repair");
        };
    }

    private static String readTail(RouteEditorProtocol.LineReader input) throws IOException {
        String line = input.readLine(64 * 1024);
        return line == null ? null : line.substring(0, Math.min(512, line.length()));
    }

    private synchronized void publish(long token, Update next) {
        if (token == generation) update = next;
    }

    public synchronized void cancel() {
        long token = ++generation;
        if (task != null) task.interrupt();
        if (process != null) retire(process);
        update = new Update(token, Phase.CANCELLED, update.kind(), 0, 0, 0, 0, "Route job cancelled", update.content(),
                -1, null, null);
    }

    private static void retire(Process child) {
        // Hold descendant handles before the parent exits: orphans are no longer discoverable.
        var owned = new LinkedHashMap<Long, ProcessHandle>();
        child.descendants().forEach(handle -> owned.put(handle.pid(), handle));
        owned.values().forEach(ProcessHandle::destroy);
        child.destroy();
        try {
            if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly();
            owned.values().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if (!child.waitFor(2, TimeUnit.SECONDS)) throw new IllegalStateException("Owned route worker did not stop: " + child.pid());
        } catch (InterruptedException interrupted) {
            child.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        Thread running;
        synchronized (this) {
            running = task;
            cancel();
        }
        if (running != null) {
            try {
                running.join(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
