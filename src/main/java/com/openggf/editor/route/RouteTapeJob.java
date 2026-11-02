package com.openggf.editor.route;

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
    public enum Phase { IDLE, RUNNING, PASSED, FAILED, UNSUPPORTED, ERROR, CANCELLED }

    /** Replay only, or replay and then write the route when it passes. */
    public enum Kind { REPLAY, SAVE }

    /**
     * @param manifest the route manifest supplying launch, provenance and native facts
     * @param script   the working rows as route-script text
     * @param target   for a save: the stem to write
     * @param replace  for a save: replace the existing route rather than write a new one
     */
    public record Request(Kind kind, Path manifest, String script, Path target, boolean replace) {
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(script, "script");
            if (kind == Kind.SAVE && target == null) throw new IllegalArgumentException("a save names its target stem");
        }
    }

    /**
     * @param content the working content the job replayed; a result for other content is stale
     * @param row     rows replayed when PASSED, the first row that could not complete when FAILED
     * @param path    the observed path, once the job completed
     * @param saved   the written manifest, after a qualified save
     */
    public record Update(long generation, Phase phase, Kind kind, int pass, int passes, int done, int total,
                         String message, String content, int row, RouteTapeOverlay path, Path saved) {
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
            builder.environment().remove("DISPLAY");
            builder.environment().remove("WAYLAND_DISPLAY");
            return builder.start();
        });
    }

    RouteTapeJob(Supplier<Map<String, Path>> roms, Launcher launcher) {
        this.roms = Objects.requireNonNull(roms, "roms");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    public Update poll() {
        return update;
    }

    /** Starts {@code request} for working content {@code content}; one job runs at a time. */
    public synchronized void start(Request request, String content, int rows) throws IOException {
        if (task != null && task.isAlive()) throw new IOException("Cancel the current route job before starting another");
        var wire = new RouteEditorProtocol.Request(request.kind() == Kind.SAVE ? RouteEditorProtocol.Kind.SAVE
                : RouteEditorProtocol.Kind.REPLAY, request.manifest(), request.script(), request.target(),
                request.replace(), roms.get());
        long token = ++generation;
        update = new Update(token, Phase.RUNNING, request.kind(), 1, 2, 0, rows, "Starting the route worker", content,
                -1, null, null);
        task = Thread.ofVirtual().name("route-tape-job").start(() -> {
            try {
                run(wire, request.kind(), content, rows, token);
            } catch (Exception failure) {
                publish(token, new Update(token, Phase.ERROR, request.kind(), 0, 0, 0, rows,
                        Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()), content, -1, null,
                        null));
            }
        });
    }

    private void run(RouteEditorProtocol.Request request, Kind kind, String content, int rows, long token)
            throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-Xmx2g");
        command.add("-Djava.awt.headless=true");
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
            try (var input = new RouteEditorProtocol.LineReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = input.readLine(64 * 1024)) != null; ) {
                    var record = RouteEditorProtocol.decodeResponse(line);
                    if (result != null) throw new IOException("Route worker sent records after its result");
                    if (record.progress() != null) {
                        var progress = record.progress();
                        publish(token, new Update(token, Phase.RUNNING, kind, progress.pass(), progress.passes(),
                                progress.done(), progress.total(), progress.pass() == 1 ? "Replaying the route"
                                : "Qualifying the route", content, -1, null, null));
                    } else if (record.point() != null) {
                        var point = record.point();
                        if (p1.size() + p2.size() >= 2 * RouteEditorProtocol.MAX_PATH_POINTS + 2) {
                            throw new IOException("Route worker path exceeds its bound");
                        }
                        (point.slot() == 1 ? p1 : p2).add(new RouteTapeOverlay.Point(point.row(), point.epoch(),
                                point.x(), point.y(), point.air()));
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
            if (!result.content().equals(content)) throw new IOException("Route worker replayed other content");
            int stride = RouteEditorProtocol.pathStride(rows);
            int diverged = result.verdict() == RouteEditorProtocol.Verdict.FAILED ? result.row() : -1;
            var path = new RouteTapeOverlay(p1, p2, stride, diverged);
            Phase phase = switch (result.verdict()) {
                case PASSED -> Phase.PASSED;
                case FAILED -> Phase.FAILED;
                case UNSUPPORTED -> Phase.UNSUPPORTED;
            };
            publish(token, new Update(token, phase, kind, 2, 2, rows, rows, result.detail(), content, result.row(), path,
                    saved));
        } finally {
            retire(child);
            stderr.join(2000);
        }
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
