package openggf.racing.server.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.net.identity.PlayerIdentity;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import openggf.racing.server.master.MasterConfig;
import openggf.racing.server.master.MasterServer;
import openggf.racing.server.master.RelayTimingEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated, ROM-free relay capacity measurement, originating in the October 2026 scale study.
 * One production master JVM serves 1/2/4 rooms; each room's production clients and TCP proxy
 * run in a separate load JVM. Inputs are cosmetic poses, never engine physics or trace rows.
 * Run the prebuilt tool under tools/performance/run_isolated.py, outside builds and tests.
 */
public final class RacingScaleTool {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String FP = "relay-scale:synthetic-v1";

    public record Config(int rooms, int players, int durationSeconds, int warmupSeconds,
                         int timeoutSeconds, int browserIntervalMillis, int admissionIntervalSeconds) {
        public Config {
            if (!Set.of(1, 2, 4).contains(rooms) || players < 2 || players > 256
                    || durationSeconds < 1 || durationSeconds > 600
                    || warmupSeconds < 1 || warmupSeconds > 120
                    || timeoutSeconds < 5 || timeoutSeconds > 120
                    || browserIntervalMillis < 2_000 || browserIntervalMillis > 60000
                    || admissionIntervalSeconds < 1 || admissionIntervalSeconds > 60) {
                throw new IllegalArgumentException("rooms 1/2/4, players 2..256, duration 1..600, "
                        + "warmup 1..120, timeout 5..120, browser-ms 2000..60000, admission-seconds 1..60 required");
            }
        }
    }

    public record Window(long trafficStartEpochMillis, long measureStartEpochMillis,
                         long measureEndEpochMillis) { }
    public record RoomTiming(String roomId, RacingScaleMetrics.Latency tickDuration,
                             RacingScaleMetrics.Latency queueDelay) { }
    public record ServerReport(RacingScaleMetrics.ProcessReport process,
                               RacingScaleMetrics.Latency brokerDispatchLateness,
                               List<RoomTiming> roomTicks, int roomsAfterDeparture,
                               boolean portReleased, List<String> jvmArguments) { }
    public record Report(int schema, Config config, boolean success, boolean childrenTerminated,
                         String sourceCommit, boolean sourceDirty, String javaVersion,
                         String os, Window window, ServerReport server,
                         List<RacingScaleLoad.Report> loads, List<String> failures,
                         List<String> limits) { }

    private RacingScaleTool() { }

    public static Config parseArgs(String[] args) {
        Map<String, String> options = options(args);
        return new Config(number(options, "--rooms", 1), number(options, "--players", 4),
                number(options, "--duration", 10), number(options, "--warmup", 2),
                number(options, "--timeout", 30), number(options, "--browser-ms", 2000),
                number(options, "--admission-seconds", 5));
    }

    private static int number(Map<String, String> options, String key, int fallback) {
        return Integer.parseInt(options.getOrDefault(key, Integer.toString(fallback)));
    }

    private static Map<String, String> options(String[] args) {
        Set<String> known = Set.of("--rooms", "--players", "--duration", "--warmup", "--timeout",
                "--browser-ms", "--admission-seconds", "--data", "--report");
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!known.contains(args[i]) || i + 1 == args.length
                    || result.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("unknown, duplicate or missing option: " + args[i]);
            }
        }
        return result;
    }

    /** Runs one matrix cell; only owned child processes and files are removed. */
    public static Report run(Config config, Path dataDir) throws InterruptedException {
        List<Process> children = new CopyOnWriteArrayList<>();
        AtomicBoolean stopping = new AtomicBoolean();
        Thread shutdownHook = new Thread(() -> {
            synchronized (children) { stopping.set(true); }
            stopChildren(children);
        }, "relay-scale-owned-child-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        List<String> failures = new ArrayList<>();
        List<RacingScaleLoad.Report> loads = new ArrayList<>();
        ServerReport server = null;
        Window window = null;
        Path run = null;
        boolean terminated = true;
        String commit = git("rev-parse", "HEAD").trim();
        boolean dirty = !git("status", "--porcelain").isBlank();
        try {
            Files.createDirectories(dataDir);
            run = Files.createTempDirectory(dataDir.toAbsolutePath(), "relay-scale-");
            write(run.resolve("config.json"), config);
            // Provisioning happens before server admission. Existing anti-abuse gates still run;
            // capacity identities represent established players, as in socket-stress.
            List<String> fingerprints = new ArrayList<>();
            for (int room = 0; room < config.rooms(); room++) {
                for (int player = 0; player < config.players() + 2; player++) {
                    fingerprints.add(PlayerIdentity.loadOrCreate(identityPath(run, room, player)).fingerprint());
                }
            }
            Files.write(run.resolve("fingerprints.txt"), fingerprints);
            child(run, "server", -1, children, stopping);
            await(run.resolve("server.ready"), children, deadline(config.timeoutSeconds() + 30));
            for (int room = 0; room < config.rooms(); room++) child(run, "load", room, children, stopping);
            long admissionDeadline = deadline(Math.max(60, config.players() * 2) + config.timeoutSeconds());
            for (int room = 0; room < config.rooms(); room++) {
                await(run.resolve("room-" + room + ".ready"), children, admissionDeadline);
            }
            long start = System.currentTimeMillis() + 4000;
            window = new Window(start, start + config.warmupSeconds() * 1000L,
                    start + (config.warmupSeconds() + config.durationSeconds()) * 1000L);
            write(run.resolve("window.json"), window);
            long finishDeadline = deadline(config.warmupSeconds() + config.durationSeconds()
                    + config.timeoutSeconds() * 5L + 10);
            for (int room = 0; room < config.rooms(); room++) {
                Path result = run.resolve("room-" + room + ".json");
                await(result, children, finishDeadline);
                loads.add(JSON.readValue(result.toFile(), RacingScaleLoad.Report.class));
            }
            Files.writeString(run.resolve("shutdown"), "owned load workers completed\n");
            await(run.resolve("server.json"), children, finishDeadline);
            server = JSON.readValue(run.resolve("server.json").toFile(), ServerReport.class);
            for (Process child : children) {
                if (!child.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                    throw new IllegalStateException("child shutdown timeout: " + child.pid());
                }
                check(child.exitValue() == 0, "child failed: " + child.pid());
            }
            check(loads.stream().allMatch(RacingScaleLoad.Report::success), "load/lifecycle contract failed");
            check(server.roomsAfterDeparture() == 0 && server.portReleased(), "server lifecycle contract failed");
            check(server.roomTicks().size() == config.rooms(), "missing room tick observations");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception failure) {
            failures.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
            if (run != null) collectChildFailures(run, failures);
        } finally {
            synchronized (children) { stopping.set(true); }
            terminated = stopChildren(children);
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException shuttingDown) { /* The registered hook owns cleanup now. */ }
            if (!terminated) failures.add("owned child shutdown incomplete; retained run directory: " + run);
            if (run != null && terminated) {
                try (var paths = Files.walk(run)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (IOException failure) {
                    failures.add("owned run cleanup failed: " + failure.getMessage());
                }
            }
        }
        return new Report(1, config, failures.isEmpty() && terminated, terminated, commit, dirty,
                System.getProperty("java.version"), System.getProperty("os.name") + "/" + System.getProperty("os.arch"),
                window, server, List.copyOf(loads), List.copyOf(failures), List.of(
                "One scenario per invocation; repeat cells independently to estimate run-to-run variance.",
                "Synthetic cosmetic ghosts exercise real authentication, browser, relay, coarse finish state and hidden casual standings; no engine simulation or verification replay.",
                "Plaintext loopback and established identities exclude Internet/TLS/first-time proof difficulty and shared-NAT behavior.",
                "Server metrics exclude load/proxy JVMs; loopback host CPU, memory bandwidth and scheduler contention remain shared.",
                "TCP bytes count stream octets at the load proxy, including HTTP/WebSocket framing; exclude IP/TCP headers and retransmission.",
                "JFR records room tick duration, room-executor queue delay and broker fixed-rate dispatch lateness; probes add overhead.",
                "Process CPU/GC are interval deltas; RSS/heap peaks are 50 ms samples; lifetimePeakRss is process-wide /proc VmHWM, -1 means unavailable.",
                "Latency percentiles use a deterministic bounded 16384-sample reservoir; sample count and exact maximum are separate.",
                "Generator lateness is the excess interval between generator polls above the requested 3 ms; browser/auth counts are completed operations started during the window.",
                "All children use -Xms128m -Xmx1g. JVM startup/admission/lifecycle are outside the shared traffic window; no absolute timing pass threshold."));
    }

    private static void child(Path run, String mode, int room, List<Process> children,
                              AtomicBoolean stopping) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        synchronized (children) {
            check(!stopping.get(), "coordinator is shutting down");
            children.add(new ProcessBuilder(java, "-Xms128m", "-Xmx1g", "--enable-native-access=ALL-UNNAMED",
                    "-cp", System.getProperty("java.class.path"), RacingScaleTool.class.getName(),
                    "--child", mode, run.toString(), Integer.toString(room))
                    .redirectOutput(run.resolve(mode + "-" + room + ".stdout").toFile())
                    .redirectError(run.resolve(mode + "-" + room + ".stderr").toFile()).start());
        }
    }

    private static boolean stopChildren(List<Process> children) {
        boolean interrupted = Thread.interrupted();
        for (Process child : children) if (child.isAlive()) child.destroy();
        long until = deadline(5);
        for (Process child : children) {
            while (child.isAlive() && System.nanoTime() < until) {
                try { child.waitFor(50, TimeUnit.MILLISECONDS); }
                catch (InterruptedException signal) { interrupted = true; }
            }
            if (child.isAlive()) child.destroyForcibly();
        }
        until = deadline(5);
        for (Process child : children) {
            while (child.isAlive() && System.nanoTime() < until) {
                try { child.waitFor(50, TimeUnit.MILLISECONDS); }
                catch (InterruptedException signal) { interrupted = true; }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return children.stream().noneMatch(Process::isAlive);
    }

    static Path identityPath(Path run, int room, int player) {
        return run.resolve("identities").resolve("room-" + room).resolve("player-" + player);
    }

    static void write(Path file, Object value) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        JSON.writeValue(temporary.toFile(), value);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
    }

    static long deadline(long seconds) { return System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds); }

    static void await(Path file, List<Process> children, long deadline) throws Exception {
        while (!Files.exists(file)) {
            for (Process child : children) {
                if (!child.isAlive() && child.exitValue() != 0) throw new IllegalStateException("child " + child.pid()
                        + " exited " + child.exitValue() + " before " + file.getFileName());
            }
            if (System.nanoTime() >= deadline) throw new IllegalStateException("timed out waiting for " + file.getFileName());
            Thread.sleep(20);
        }
    }

    static void waitUntil(long epochMillis) throws InterruptedException {
        while (System.currentTimeMillis() < epochMillis) {
            Thread.sleep(Math.min(20, Math.max(1, epochMillis - System.currentTimeMillis())));
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String git(String... args) {
        try {
            List<String> command = new ArrayList<>(List.of("git"));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? result : "unknown";
        } catch (Exception unavailable) {
            if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private static void collectChildFailures(Path run, List<String> failures) {
        try (var paths = Files.list(run)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().endsWith(".failure")).toList()) {
                String message = Files.readString(file);
                failures.add(file.getFileName() + ": " + message.substring(0, Math.min(2000, message.length())));
            }
        } catch (IOException ignored) { }
    }

    private static void server(Path run, Config config) throws Exception {
        MasterConfig masterConfig = new MasterConfig(0, null, null, true, "identities.db", 0,
                java.util.UUID.randomUUID().toString(), 48, 10, 14, 50, 8, 8, false,
                2, 4, 30, 20, 30, 10000, 60);
        MasterServer server = MasterServer.start(masterConfig, run.resolve("master"));
        int port = server.port();
        RacingScaleMetrics.ProcessReport process;
        List<RoomTiming> timings;
        RacingScaleMetrics.Latency dispatch;
        int roomsRemaining;
        try (server) {
            CompletableFuture<Void> provisioned = new CompletableFuture<>();
            List<String> fingerprints = Files.readAllLines(run.resolve("fingerprints.txt"));
            server.execute(() -> {
                try {
                    fingerprints.forEach(server::establishForTest);
                    provisioned.complete(null);
                } catch (Throwable failure) { provisioned.completeExceptionally(failure); }
            });
            provisioned.get(config.timeoutSeconds(), TimeUnit.SECONDS);
            write(run.resolve("server.ready"), Map.of("port", port, "pid", ProcessHandle.current().pid()));
            await(run.resolve("window.json"), List.of(), deadline(Math.max(60, config.players() * 2) + config.timeoutSeconds()));
            Window window = JSON.readValue(run.resolve("window.json").toFile(), Window.class);
            Path recording = run.resolve("server-timing.jfr");
            try (Recording jfr = new Recording()) {
                jfr.enable(RelayTimingEvents.RoomTick.class).withoutStackTrace().withThreshold(Duration.ZERO);
                jfr.enable(RelayTimingEvents.Dispatch.class).withoutStackTrace().withThreshold(Duration.ZERO);
                // Start recorder before the window to keep recorder startup out of process deltas.
                jfr.start();
                waitUntil(window.measureStartEpochMillis());
                try (RacingScaleMetrics.ProcessWindow measurement = new RacingScaleMetrics.ProcessWindow()) {
                    waitUntil(window.measureEndEpochMillis());
                    measurement.close();
                    process = measurement.report();
                }
                jfr.stop();
                jfr.dump(recording);
            }
            Map<String, RacingScaleMetrics.Latencies> durations = new LinkedHashMap<>();
            Map<String, RacingScaleMetrics.Latencies> queues = new LinkedHashMap<>();
            RacingScaleMetrics.Latencies dispatches = new RacingScaleMetrics.Latencies();
            try (RecordingFile events = new RecordingFile(recording)) {
                while (events.hasMoreEvents()) {
                    var event = events.readEvent();
                    long at = event.getStartTime().toEpochMilli();
                    if (at < window.measureStartEpochMillis() || at >= window.measureEndEpochMillis()) continue;
                    if (event.getEventType().getName().equals("openggf.RelayRoomTick")) {
                        String room = event.getString("roomId");
                        durations.computeIfAbsent(room, key -> new RacingScaleMetrics.Latencies()).add(event.getDuration().toNanos());
                        queues.computeIfAbsent(room, key -> new RacingScaleMetrics.Latencies()).add(event.getLong("queueDelayNanos"));
                    } else if (event.getEventType().getName().equals("openggf.RelayTickDispatch")) {
                        dispatches.add(event.getLong("latenessNanos"));
                    }
                }
            }
            timings = durations.entrySet().stream().map(entry -> new RoomTiming(entry.getKey(),
                    entry.getValue().report(), queues.get(entry.getKey()).report())).toList();
            dispatch = dispatches.report();
            await(run.resolve("shutdown"), List.of(), deadline(config.timeoutSeconds() * 5L));
            CompletableFuture<Integer> remaining = new CompletableFuture<>();
            server.execute(() -> remaining.complete(server.relays().roomCount()));
            roomsRemaining = remaining.get(config.timeoutSeconds(), TimeUnit.SECONDS);
        }
        boolean released;
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1000);
            released = false;
        } catch (java.net.ConnectException expected) {
            released = true;
        }
        write(run.resolve("server.json"), new ServerReport(process, dispatch, timings,
                roomsRemaining, released, java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("--child")) {
            Path run = Path.of(args[2]);
            Config config = JSON.readValue(run.resolve("config.json").toFile(), Config.class);
            try {
                if (args[1].equals("server")) server(run, config);
                else if (args[1].equals("load")) RacingScaleLoad.run(run, config, Integer.parseInt(args[3]));
                else throw new IllegalArgumentException("unknown owned child role");
            } catch (Throwable failure) {
                var trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                Files.writeString(run.resolve(args[1] + "-" + args[3] + ".failure"),
                        trace.toString());
                throw failure;
            }
            return;
        }
        Config config = parseArgs(args);
        Map<String, String> options = options(args);
        Report report = run(config, Path.of(options.getOrDefault("--data", "target/racing-scale")));
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        if (options.containsKey("--report")) {
            Path destination = Path.of(options.get("--report")).toAbsolutePath();
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, json + "\n");
        }
        System.out.println(json);
        if (!report.success()) throw new IllegalStateException("relay scale contract failed: " + report.failures());
    }
}
