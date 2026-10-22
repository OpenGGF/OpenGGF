import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded streaming attribution of real multiplayer JFR recordings. Inputs: recording,
 * JSON destination, optional window-event name. Origin: 2026-10-21 Time Attack capacity
 * component investigation. Compile before measurements; run the prebuilt class with
 * -Xmx256m. Never expand full recording stacks into JSON or infer nanoseconds from samples.
 */
public final class MultiplayerJfrSummary {
    private static final int MAX_KEYS = 4096;
    private static final int TOP = 60;
    private static final Set<String> DEFAULT_WINDOWS = Set.of(
            "openggf.RacingStressWindow", "openggf.MultiplayerCaptureWindow");
    private static final List<String> COMPONENTS = List.of("master broker/directory", "relay routing",
            "room actor/control", "round/standings", "ghost hub/relevance", "ghost ingress validation",
            "authentication/identity", "control JSON codec", "binary ghost codec", "crypto/provider",
            "identity SQLite/storage", "verification HTTP/blob/queue", "verification replay worker",
            "client connection/handshake", "client WebSocket assembly", "client pump/session", "ghost presentation/buffering",
            "ghost sprite rendering", "multiplayer HUD/menu", "graphics/renderer", "Netty transport",
            "JDK WebSocket host", "JDK HTTP client", "JDK socket IO", "bot workload/control",
            "source-address TCP proxy", "capture/readback/encoding", "other engine", "JDK/runtime/other");

    private record Window(Instant start, Instant end, String kind) {
        boolean includes(Instant instant) { return !instant.isBefore(start) && !instant.isAfter(end); }
    }

    private static final class Counters {
        final Map<String, Long> events = new LinkedHashMap<>();
        final Map<String, Long> javaLeaf = new LinkedHashMap<>(), javaInclusive = new LinkedHashMap<>();
        final Map<String, Long> nativeLeaf = new LinkedHashMap<>(), nativeInclusive = new LinkedHashMap<>();
        final Map<String, Long> allocations = new LinkedHashMap<>(), allocationInclusive = new LinkedHashMap<>();
        final Map<String, Long> allocationClasses = new LinkedHashMap<>(), javaMethods = new LinkedHashMap<>();
        final Map<String, Long> nativeMethods = new LinkedHashMap<>(), socket = new LinkedHashMap<>();
        final Map<String, Long> threadNames = new LinkedHashMap<>();
        long javaSamples, nativeSamples, allocationSamples, allocationWeight, noStackSamples;
        long gcEvents, gcDurationNanos, pauseEvents, pauseDurationNanos;
        Instant first, last;

        void accept(RecordedEvent event) {
            String type = event.getEventType().getName();
            add(events, type, 1);
            if (first == null || event.getStartTime().isBefore(first)) first = event.getStartTime();
            if (last == null || event.getEndTime().isAfter(last)) last = event.getEndTime();
            if (type.equals("jdk.GarbageCollection")) {
                gcEvents++;
                gcDurationNanos += event.getDuration().toNanos();
            }
            if (type.equals("jdk.GCPhasePause")) {
                pauseEvents++;
                pauseDurationNanos += event.getDuration().toNanos();
            }
            if (type.equals("jdk.SocketRead") || type.equals("jdk.SocketWrite")) {
                RecordedThread thread = event.getThread();
                String role = role(thread == null ? "unknown" : thread.getJavaName(), List.of());
                add(socket, role + " / " + type + " / recorded operations", 1);
                String field = type.equals("jdk.SocketRead") ? "bytesRead" : "bytesWritten";
                add(socket, role + " / " + type + " / recorded bytes", Math.max(0, event.getLong(field)));
                add(socket, role + " / " + type + " / recorded duration ns", event.getDuration().toNanos());
            }
            boolean java = type.equals("jdk.ExecutionSample");
            boolean nativeSample = type.equals("jdk.NativeMethodSample");
            boolean allocation = type.equals("jdk.ObjectAllocationSample");
            if (!java && !nativeSample && !allocation) return;
            RecordedThread thread = event.hasField("sampledThread")
                    ? event.getThread("sampledThread") : event.getThread();
            String threadName = thread == null ? "unknown" : thread.getJavaName();
            add(threadNames, type + " / " + threadName, 1);
            List<String> methods = new ArrayList<>();
            if (event.getStackTrace() != null) {
                for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                    methods.add(frame.getMethod().getType().getName() + "." + frame.getMethod().getName());
                }
            }
            String role = role(threadName, methods);
            String leaf = methods.isEmpty() ? "JDK/runtime/other" : component(methods.getFirst());
            // The first meaningful component outward from the observed leaf owns this sample.
            if (leaf.equals("JDK/runtime/other")) {
                for (String method : methods) {
                    String candidate = component(method);
                    if (!candidate.equals("JDK/runtime/other")) { leaf = candidate; break; }
                }
            }
            Set<String> inclusive = new LinkedHashSet<>();
            methods.forEach(method -> inclusive.add(component(method)));
            if (methods.isEmpty()) { inclusive.add("JDK/runtime/other"); noStackSamples++; }
            if (allocation) {
                long weight = event.getLong("weight");
                allocationSamples++;
                allocationWeight += weight;
                add(allocations, role + " / " + leaf, weight);
                inclusive.forEach(owner -> add(allocationInclusive, role + " / " + owner, weight));
                add(allocationClasses, role + " / " + event.getClass("objectClass").getName(), weight);
            } else {
                if (java) javaSamples++; else nativeSamples++;
                add(java ? javaLeaf : nativeLeaf, role + " / " + leaf, 1);
                inclusive.forEach(owner -> add(java ? javaInclusive : nativeInclusive, role + " / " + owner, 1));
                add(java ? javaMethods : nativeMethods,
                        role + " / " + (methods.isEmpty() ? "no stack" : methods.getFirst()), 1);
            }
        }

        Map<String, Object> report() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("firstEventUtc", first == null ? null : first.toString());
            result.put("lastEventUtc", last == null ? null : last.toString());
            result.put("javaExecutionSamples", javaSamples);
            result.put("nativeMethodSamplesIncludingWaiting", nativeSamples);
            result.put("allocationSamples", allocationSamples);
            result.put("estimatedAllocationSampleWeightBytes", allocationWeight);
            result.put("samplesWithoutStack", noStackSamples);
            result.put("garbageCollectionEvents", gcEvents);
            result.put("gcEventDurationNanos", gcDurationNanos);
            result.put("topLevelGcPauseEvents", pauseEvents);
            result.put("topLevelGcPauseDurationNanos", pauseDurationNanos);
            result.put("eventCounts", sorted(events, MAX_KEYS));
            result.put("javaLeafOwnerSamples", sorted(javaLeaf, TOP));
            result.put("javaInclusiveStackPresenceSamples", sorted(javaInclusive, TOP));
            result.put("nativeLeafOwnerSamples", sorted(nativeLeaf, TOP));
            result.put("nativeInclusiveStackPresenceSamples", sorted(nativeInclusive, TOP));
            result.put("estimatedAllocationWeightByLeafOwnerBytes", sorted(allocations, TOP));
            result.put("estimatedAllocationWeightByInclusiveStackBytes", sorted(allocationInclusive, TOP));
            result.put("estimatedAllocationWeightByClassBytes", sorted(allocationClasses, TOP));
            result.put("javaObservedLeafMethods", sorted(javaMethods, TOP));
            result.put("nativeObservedLeafMethods", sorted(nativeMethods, TOP));
            result.put("socketEventsThresholdedNotTotalTraffic", sorted(socket, TOP));
            result.put("sampledThreadNames", sorted(threadNames, TOP));
            List<String> unobserved = new ArrayList<>();
            for (String component : COMPONENTS) {
                if (javaInclusive.keySet().stream().noneMatch(key -> key.endsWith(" / " + component)))
                    unobserved.add(component);
            }
            result.put("componentsWithoutJavaSamplesNotProofOfAbsence", unobserved);
            return result;
        }
    }

    private static String component(String method) {
        if (method.contains("LoopbackSourceProxy")) return "source-address TCP proxy";
        if (method.contains("RacingSocketStressTool") || method.contains("TimeAttackVideoPeers")
                || method.contains("BotClient")) return "bot workload/control";
        if (method.contains("GhostFrameCodec") || method.contains("GhostPackets")) return "binary ghost codec";
        if (method.contains("ControlJsonCodec") || method.contains("ControlCodec")
                || method.startsWith("com.fasterxml.jackson.")) return "control JSON codec";
        if (method.contains("GhostStreamValidator")) return "ghost ingress validation";
        if (method.contains("GhostHub") || method.contains("RelevanceClassifier")) return "ghost hub/relevance";
        if (method.contains("HostRoundEngine")) return "round/standings";
        if (method.contains("RoomHost") || method.contains("RoomRoundAccess")) return "room actor/control";
        if (method.contains("RoomBroker") || method.contains("SessionRegistry")
                || method.contains("MasterChannelHandler")) return "master broker/directory";
        if (method.contains("RelayRoomManager") || method.contains("GuestTunnelRouter")) return "relay routing";
        if (method.contains("Sqlite") || method.startsWith("org.sqlite.")
                || method.contains("TrustLadder") || method.contains("NewIdentityCache")) return "identity SQLite/storage";
        if (method.contains("VerifierWorker") || method.contains("AttemptReplayHarness")) return "verification replay worker";
        if (method.contains("MasterHttp") || method.contains("VerificationJobQueue")
                || method.contains("RecordingBlobStore") || method.contains("VerdictConsequences")
                || method.contains("VerifierRegistry")) return "verification HTTP/blob/queue";
        if (method.contains("PlayerIdentity") || method.contains("ProofOfWork")
                || method.contains("AuthenticationTranscript") || method.contains("HostHandshake")
                || method.contains("HostLinkAuthentication")) return "authentication/identity";
        if (method.startsWith("sun.security.") || method.startsWith("java.security.")
                || method.startsWith("javax.crypto.") || method.startsWith("com.sun.crypto.")) return "crypto/provider";
        if (method.contains("FrameAssembler")) return "client WebSocket assembly";
        if (method.contains("RemoteGhost")
                || method.contains("GhostRenderRegistry")) return "ghost presentation/buffering";
        if (method.contains("GhostRenderer") || method.startsWith("com.openggf.sprites.ghost.")) return "ghost sprite rendering";
        if (method.contains("ClientRaceSession") || method.contains("GhostStreamPublisher")
                || method.contains("MultiplayerRaceCoordinator") || method.contains(".mp.RaceSession")
                || method.contains(".mp.RaceTransport")) return "client pump/session";
        if (method.startsWith("com.openggf.net.client.") || method.contains("HostMasterLink")) return "client connection/handshake";
        if (method.contains("Multiplayer") || method.contains("RacingHud") || method.contains("RaceHud")
                || method.contains("TimeAttackMenu") || method.contains("TimeAttackHud")
                || method.contains("HudTextLayout") || method.contains("MinimapLayout")
                || method.contains("ServerBrowserView") || method.contains("RaceLobbyView")) return "multiplayer HUD/menu";
        if (method.contains("RecordingUploader") || method.contains("AttemptRecordingVault")) return "verification HTTP/blob/queue";
        if (method.startsWith("io.netty.")) return "Netty transport";
        if (method.startsWith("com.openggf.net.host.jdk.")) return "JDK WebSocket host";
        if (method.startsWith("jdk.internal.net.http.")) return "JDK HTTP client";
        if (method.startsWith("sun.nio.ch.") || method.startsWith("java.net.")) return "JDK socket IO";
        if (method.contains("Screenshot") || method.contains("ImageIO") || method.startsWith("com.pngencoder.")
                || method.startsWith("com.openggf.tools.")) return "capture/readback/encoding";
        if (method.startsWith("com.openggf.graphics.") || method.startsWith("org.lwjgl.")
                || method.contains("LevelRenderer")) return "graphics/renderer";
        if (method.startsWith("com.openggf.") || method.startsWith("openggf.timeattack.")) return "other engine";
        return "JDK/runtime/other";
    }

    private static String role(String name, List<String> methods) {
        if (name.startsWith("racing-stress-proxy")) return "load proxy";
        if (name.startsWith("nioEventLoopGroup")) return "Netty server transport/actor";
        if (name.startsWith("race-room-host") || name.startsWith("race-host-")
                || name.startsWith("race-ws-")) return "JDK server actor/IO";
        if (name.startsWith("HttpClient-")) return "JDK client transport";
        if (name.startsWith("master-pow") || name.startsWith("relay-room-handshake")) return "client authentication worker";
        if (name.contains("JFR") || name.contains("Flight Recorder")) return "recorder/control";
        if (methods.stream().anyMatch(m -> m.contains("RacingSocketStressTool"))) return "load generator/client drain";
        if (methods.stream().anyMatch(m -> m.startsWith("com.openggf.tools.TimeAttackVideoCaptureTool")))
            return "engine capture/gameplay/render";
        return "other/unresolved";
    }

    private static void add(Map<String, Long> counters, String key, long amount) {
        counters.merge(counters.containsKey(key) || counters.size() < MAX_KEYS ? key : "other (key limit)", amount, Long::sum);
    }

    private static Map<String, Long> sorted(Map<String, Long> counters, int limit) {
        Map<String, Long> result = new LinkedHashMap<>();
        counters.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey())).limit(limit).forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> values) {
            List<String> entries = new ArrayList<>();
            values.forEach(item -> entries.add(json(item)));
            return "[" + String.join(",", entries) + "]";
        }
        String text = value.toString();
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') escaped.append('\\').append(c);
            else if (c < 32) escaped.append(String.format("\\u%04x", (int) c));
            else escaped.append(c);
        }
        return escaped.append('"').toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException(
                "MultiplayerJfrSummary recording.jfr summary.json [window-event-name]");
        Path source = Path.of(args[0]), destination = Path.of(args[1]);
        Set<String> windowNames = args.length == 3 ? Set.of(args[2]) : DEFAULT_WINDOWS;
        List<Window> windows = new ArrayList<>();
        Map<Long, String> eventTypes = new LinkedHashMap<>();
        Map<String, String> relevantSettings = new LinkedHashMap<>();
        Set<String> settingsTypes = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample",
                "jdk.ObjectAllocationSample", "jdk.SocketRead", "jdk.SocketWrite");
        try (RecordingFile file = new RecordingFile(source)) {
            file.readEventTypes().forEach(type -> eventTypes.put(type.getId(), type.getName()));
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (event.getEventType().getName().equals("jdk.ActiveSetting")) {
                    String owner = eventTypes.get(event.getLong("id"));
                    if (owner != null && settingsTypes.contains(owner)) relevantSettings.put(owner + " / "
                            + event.getString("name"), event.getString("value"));
                }
                if (windowNames.contains(event.getEventType().getName())) {
                    if (windows.size() >= 1000) throw new IllegalArgumentException("too many diagnostic windows");
                    windows.add(new Window(event.getStartTime(), event.getEndTime(), event.getEventType().getName()));
                }
            }
        }
        Counters whole = new Counters(), measured = new Counters();
        try (RecordingFile file = new RecordingFile(source)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                whole.accept(event);
                if (windows.stream().anyMatch(window -> window.includes(event.getStartTime()))) measured.accept(event);
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", 1);
        report.put("recording", source.toAbsolutePath().toString());
        report.put("observedRecordingSettingsLastValue", relevantSettings);
        report.put("limits", List.of("Samples are observations, never component CPU nanoseconds or frame times.",
                "Java and native samples stay separate; native samples may include waiting, and periods differ.",
                "Leaf owner is first recognized component outward from observed leaf; inclusive stack rows overlap.",
                "Allocation weights estimate churn, not exact allocation, retained heap, or leaks.",
                "Socket events obey recording thresholds; their bytes are not complete protocol or wire totals.",
                "Thread role names distinguish colocated load/proxy/server where known; unresolved remains explicit.",
                "No sampled component does not establish zero cost or prove the path executed.",
                "Window admission uses event start timestamp; long events may cross a window boundary.",
                "Recording instrumentation changes workload; compare timings using independent unprofiled controls."));
        report.put("windows", windows.stream().map(window -> Map.of("kind", window.kind(),
                "startUtc", window.start().toString(), "endUtc", window.end().toString(),
                "durationSeconds", Duration.between(window.start(), window.end()).toNanos() / 1e9)).toList());
        report.put("wholeRecording", whole.report());
        report.put("measuredWindows", windows.isEmpty() ? null : measured.report());
        if (destination.toAbsolutePath().getParent() != null) Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.writeString(destination, json(report) + "\n");
        System.out.println("JFR summarized: java=" + whole.javaSamples + ", native=" + whole.nativeSamples
                + ", allocation=" + whole.allocationSamples + ", measured windows=" + windows.size());
    }
}
