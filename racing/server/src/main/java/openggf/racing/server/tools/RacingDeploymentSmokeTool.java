package openggf.racing.server.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.MasterClient;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import openggf.racing.server.master.MasterConfig;
import openggf.racing.server.master.SqliteIdentityStore;
import openggf.racing.server.verifier.AttemptReplayHarness;
import openggf.timeattack.AttemptInputRecording;
import openggf.timeattack.AttemptStartDescriptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Real TLS, relay, upload and external-worker witness for the October 2026 Docker deployment.
 * Inputs are a user-supplied S1 ROM and GHZ1 BK2 controller movie (default start frame 840).
 * Fresh fixture preparation seeds only account trust, never gameplay or verdicts. Run against
 * that isolated master with a separately running verifier; all poses and timing come from
 * normal input replay and the remote worker must independently return an accepted PASS.
 */
public final class RacingDeploymentSmokeTool {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MARKER = "deployment-smoke-fixture.txt";
    private static final String FIXTURE_VERSION = "OpenGGF deployment smoke fixture v1\n";
    private static final String DISPLAY_NAME = "DEPLOYMENT-SMOKE";

    private RacingDeploymentSmokeTool() { }

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        if (options.mode().equals("prepare")) {
            prepare(options.path("--data"));
            return;
        }
        PreparedAttempt attempt = replay(options.path("--rom"), options.path("--movie"),
                Integer.parseInt(options.values().getOrDefault("--movie-start", "840")));
        System.out.println(JSON.writeValueAsString(Map.of(
                "phase", "replayed", "fingerprint", attempt.recording().start().fingerprint(),
                "frames", attempt.frames().size(), "timeFrames", attempt.result().finalTimeFrames(),
                "ghostHash", attempt.result().ghostStreamHashHex())));
        if (options.mode().equals("run")) {
            run(options.path("--data"), masterUri(options.required("--master")), attempt);
        }
    }

    /** Creates a new, isolated master fixture before the master starts; refuses any existing dir. */
    private static void prepare(Path data) throws Exception {
        Files.createDirectory(data);
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(data.resolve("client"));
        MasterConfig defaults = MasterConfig.defaults();
        long now = System.currentTimeMillis();
        try (SqliteIdentityStore store = new SqliteIdentityStore(
                data.resolve("master/master-identities.db"))) {
            store.persistOnDurableEvent(identity.fingerprint(),
                    now - defaults.thresholds().trustedAgeMillis() - Duration.ofDays(1).toMillis(), now);
            for (int i = 0; i < defaults.trustedCleanRounds(); i++) {
                store.recordCleanRound(identity.fingerprint(), now);
            }
            store.setDisplayName(identity.fingerprint(), DISPLAY_NAME);
            store.setTier(identity.fingerprint(), "TRUSTED");
        }
        Files.writeString(data.resolve(MARKER), FIXTURE_VERSION);
        System.out.println(JSON.writeValueAsString(Map.of("phase", "prepared",
                "data", data.toAbsolutePath().toString(), "player", identity.fingerprint())));
    }

    private static PreparedAttempt replay(Path rom, Path moviePath, int movieStart) throws Exception {
        var movie = new Bk2MovieLoader().load(moviePath);
        if (movieStart < 0 || movieStart >= movie.getFrameCount()
                || movie.getFrameCount() - movieStart > AttemptInputRecording.MAX_FRAMES) {
            throw new IllegalArgumentException("movie start/length outside one bounded attempt");
        }
        String fingerprint = AttemptReplayHarness.fingerprintForRom(rom);
        AttemptInputRecording recording = new AttemptInputRecording(
                new AttemptStartDescriptor("s1", 0, 0, "sonic", fingerprint));
        for (int frame = movieStart; frame < movie.getFrameCount(); frame++) {
            var input = movie.getFrame(frame);
            recording.appendFrame(input.p1InputMask(), input.p1StartPressed());
        }
        List<GhostFrame> frames = new ArrayList<>();
        AttemptReplayHarness.Result result = AttemptReplayHarness.replay(recording, rom, frames::add);
        if (result.failureReason() != null || !result.finished() || result.finalTimeFrames() <= 0
                || result.framesSimulated() != frames.size()
                || result.finishFrame() + 1 != frames.size()) {
            throw new IllegalStateException("controller movie did not finish a replayable GHZ1 attempt: " + result);
        }
        // The real finish ends the recording. Never submit unused movie inputs after it.
        AttemptInputRecording bounded = new AttemptInputRecording(recording.start());
        for (int frame = 0; frame < result.framesSimulated(); frame++) {
            int mask = recording.heldMaskAt(frame);
            bounded.appendFrame(mask, (mask & AttemptInputRecording.START_HELD_BIT) != 0);
        }
        return new PreparedAttempt(bounded, List.copyOf(frames), result);
    }

    private static void run(Path data, URI base, PreparedAttempt attempt) throws Exception {
        if (!FIXTURE_VERSION.equals(Files.readString(data.resolve(MARKER)))) {
            throw new IllegalArgumentException("--data must name a deployment-smoke prepare fixture");
        }
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(data.resolve("client"));
        String fingerprint = attempt.recording().start().fingerprint();
        URI socket = new URI("wss", null, base.getHost(), base.getPort(), "/master", null, null);
        try (MasterClient master = MasterClient.connect(socket, identity, DISPLAY_NAME, fingerprint, null)
                .get(15, TimeUnit.SECONDS)) {
            String roomId = master.createRoom(new ControlMessage.RoomDescriptor(
                    "Deployment smoke", "s1", 0, 0, "LOCKED", "sonic", 2, true),
                    "RELAY", 0, fingerprint).get(10, TimeUnit.SECONDS).roomId();
            var browser = master.listRooms("s1", 0).get(10, TimeUnit.SECONDS);
            if (browser.rooms().stream().noneMatch(room -> room.roomId().equals(roomId) && room.verified())) {
                throw new IllegalStateException("master did not advertise the verified relay room");
            }
            RaceConnection race = master.joinRoom(roomId, identity, DISPLAY_NAME, fingerprint)
                    .get(10, TimeUnit.SECONDS);
            try {
                Events events = new Events(master, race, roomId);
                race.sendControl(new ControlMessage.SetReadyRule("AUTO"));
                int window = (attempt.frames().size() + 59) / 60 + 120;
                race.sendControl(new ControlMessage.RoundConfigure(
                        new ControlMessage.RoundConfig("s1", 0, 0, window, "LOCKED", "sonic")));
                var start = (ControlMessage.RoundStart) events.await(
                        message -> message instanceof ControlMessage.RoundStart, 10_000);
                race.sendControl(new ControlMessage.Ping(1));
                var clock = (ControlMessage.Pong) events.await(
                        message -> message instanceof ControlMessage.Pong pong && pong.t0ClientMillis() == 1,
                        10_000);
                long countdownRemaining = Math.max(0, start.countdownEndsAtHubMillis() - clock.hubMillis());
                if (countdownRemaining > 10_000) throw new IllegalStateException("unexpected countdown");
                events.waitUntil(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(countdownRemaining + 20));

                race.sendControl(new ControlMessage.AttemptStart(1));
                GhostStreamPublisher publisher = new GhostStreamPublisher(race::sendBinary);
                publisher.beginAttempt(1);
                long streamStart = System.nanoTime();
                for (int frame = 0; frame < attempt.frames().size(); frame++) {
                    events.waitUntil(streamStart + frame * 1_000_000_000L / 60);
                    publisher.onFrame(attempt.frames().get(frame));
                }
                publisher.finishAttempt();
                String ghostHash = HexFormat.of().formatHex(publisher.streamHashSha256());
                if (!ghostHash.equals(attempt.result().ghostStreamHashHex())) {
                    throw new IllegalStateException("published frames differ from replay output");
                }
                String inputHash = HexFormat.of().formatHex(attempt.recording().sha256());
                var result = attempt.result();
                race.sendControl(new ControlMessage.AttemptFinish(1, result.finalTimeFrames(),
                        result.firstInputFrame(), result.finishFrame(), inputHash, ghostHash, null));
                var request = (ControlMessage.RecordingRequest) events.await(
                        message -> message instanceof ControlMessage.RecordingRequest, 10_000);
                if (request.attemptId() != 1 || !inputHash.equals(request.expectedHashHex())) {
                    throw new IllegalStateException("master requested a different attempt or recording hash");
                }
                URI upload = base.resolve(request.uploadUrl());
                requireSameOriginUpload(base, upload, inputHash);
                try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                    HttpResponse<Void> response = http.send(HttpRequest.newBuilder(upload)
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Authorization", "Bearer " + race.uploadSessionToken())
                                    .PUT(HttpRequest.BodyPublishers.ofByteArray(attempt.recording().encode())).build(),
                            HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() != 204) {
                        throw new IllegalStateException("recording upload returned HTTP " + response.statusCode());
                    }
                }
                System.out.println(JSON.writeValueAsString(Map.of(
                        "phase", "uploaded", "room", roomId, "inputHash", inputHash)));
                events.await(message -> message instanceof ControlMessage.StandingsDelta delta
                        && delta.rows().stream().anyMatch(row -> row.slot() == race.playerSlot()
                        && row.verifyState().equals("VERIFIED")
                        && row.bestTimeFrames() == result.finalTimeFrames()), 90_000);
                System.out.println(JSON.writeValueAsString(Map.of("phase", "verified",
                        "room", roomId, "fingerprint", fingerprint, "timeFrames", result.finalTimeFrames(),
                        "frames", result.framesSimulated(), "inputHash", inputHash, "ghostHash", ghostHash)));
            } finally {
                race.close();
            }
        }
    }

    static URI masterUri(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("--master must be an HTTPS origin without credentials/path/query");
        }
        return uri;
    }

    static void requireSameOriginUpload(URI base, URI upload, String inputHash) {
        if (!"https".equals(upload.getScheme()) || !base.getHost().equalsIgnoreCase(upload.getHost())
                || effectivePort(base) != effectivePort(upload) || upload.getRawUserInfo() != null
                || upload.getRawQuery() != null || upload.getRawFragment() != null
                || !("/recordings/" + inputHash).equals(upload.getPath())) {
            throw new IllegalStateException("smoke upload must use the requested recording on the same HTTPS origin");
        }
    }

    private static int effectivePort(URI uri) { return uri.getPort() < 0 ? 443 : uri.getPort(); }

    private record PreparedAttempt(AttemptInputRecording recording, List<GhostFrame> frames,
                                   AttemptReplayHarness.Result result) { }

    private record Arguments(String mode, Map<String, String> values) {
        static Arguments parse(String[] args) {
            if (args.length == 0 || !Set.of("prepare", "replay", "run").contains(args[0])) {
                throw new IllegalArgumentException("deployment-smoke prepare --data NEW_DIR | "
                        + "replay --rom S1_ROM --movie GHZ1_BK2 [--movie-start 840] | "
                        + "run --data FIXTURE_DIR --master HTTPS_ORIGIN --rom S1_ROM --movie GHZ1_BK2");
            }
            Set<String> allowed = switch (args[0]) {
                case "prepare" -> Set.of("--data");
                case "replay" -> Set.of("--rom", "--movie", "--movie-start");
                default -> Set.of("--data", "--master", "--rom", "--movie", "--movie-start");
            };
            Map<String, String> values = new HashMap<>();
            for (int i = 1; i < args.length; i += 2) {
                if (!allowed.contains(args[i]) || i + 1 >= args.length
                        || values.putIfAbsent(args[i], args[i + 1]) != null) {
                    throw new IllegalArgumentException("unknown, duplicate or missing option: " + args[i]);
                }
            }
            Arguments result = new Arguments(args[0], Map.copyOf(values));
            if (!result.mode().equals("replay")) result.required("--data");
            if (!result.mode().equals("prepare")) {
                result.required("--rom");
                result.required("--movie");
            }
            if (result.mode().equals("run")) masterUri(result.required("--master"));
            return result;
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
            return value;
        }

        Path path(String key) { return Path.of(required(key)); }
    }

    /** Keeps the real room alive while pacing and waiting; never adjusts any server clock. */
    private static final class Events {
        private final MasterClient master;
        private final RaceConnection race;
        private final String roomId;
        private final List<ControlMessage> pending = new ArrayList<>();
        private long lastHeartbeat = System.nanoTime() - TimeUnit.SECONDS.toNanos(5);

        Events(MasterClient master, RaceConnection race, String roomId) {
            this.master = master;
            this.race = race;
            this.roomId = roomId;
        }

        private void poll() {
            long now = System.nanoTime();
            if (now - lastHeartbeat > TimeUnit.SECONDS.toNanos(5)) {
                master.heartbeat(roomId, 1);
                lastHeartbeat = now;
            }
            for (RaceClient.InboundEvent event : race.drainInbound()) {
                if (event instanceof RaceClient.Disconnected gone) {
                    throw new IllegalStateException("race disconnected: " + gone.reason());
                }
                if (event instanceof RaceClient.Control control) {
                    if (control.message() instanceof ControlMessage.Kick kick) {
                        throw new IllegalStateException("race rejected smoke attempt: " + kick.reason());
                    }
                    if (control.message() instanceof ControlMessage.RoundStart
                            || control.message() instanceof ControlMessage.Pong
                            || control.message() instanceof ControlMessage.RecordingRequest
                            || control.message() instanceof ControlMessage.StandingsDelta) {
                        pending.add(control.message());
                    }
                }
            }
            if (pending.size() > 128) throw new IllegalStateException("too many pending smoke events");
        }

        ControlMessage await(Predicate<ControlMessage> match, long timeoutMillis) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            do {
                poll();
                for (int i = 0; i < pending.size(); i++) {
                    if (match.test(pending.get(i))) return pending.remove(i);
                }
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            throw new IllegalStateException("timed out waiting for deployment smoke event; pending=" + pending);
        }

        void waitUntil(long deadline) throws InterruptedException {
            while (true) {
                poll();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return;
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)));
            }
        }
    }
}
