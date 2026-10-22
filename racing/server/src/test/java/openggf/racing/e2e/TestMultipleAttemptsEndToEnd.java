package openggf.racing.e2e;

import openggf.racing.server.host.RaceHostImpl;

import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.RaceClient;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.host.ControlledRaceHost;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.hub.GhostStreamValidator;
import com.openggf.net.hub.HostRoundEngine;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-socket witness for repeated Time Attack attempts inside one round window: a faster
 * retry replaces the best, a slower retry changes nothing, and a mid-run reset followed by
 * a new attempt is accepted without a violation, on both the Netty and the JDK room host.
 */
@Timeout(120)
class TestMultipleAttemptsEndToEnd {
    private static final String FP = "0.6:cafe1234";
    private static final int WINDOW_SECONDS = 60;
    private static final int FRAMES_PER_ATTEMPT = 30;
    // RoomHost reports attempt and ghost-stream violations through this logger.
    private static final Logger ROOM_LOG = Logger.getLogger(RoomHost.class.getName());

    private final List<LogRecord> roomWarnings = new CopyOnWriteArrayList<>();
    private final Handler warningCapture = new Handler() {
        @Override public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                roomWarnings.add(record);
            }
        }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private RaceRoomHost server;
    private ControlledRaceHost clock;
    private RaceClient racer;
    private Inbox racerInbox;
    private GhostStreamPublisher publisher;
    private int attemptFrames;
    private long pingTag;

    @BeforeEach
    void captureRoomWarnings() {
        ROOM_LOG.addHandler(warningCapture);
    }

    @AfterEach
    void tearDown() {
        ROOM_LOG.removeHandler(warningCapture);
        if (server != null) server.close();
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void retriesInsideOneRoundKeepOnlyTheFastestValidFinish(RaceHostImpl impl,
                                                            @TempDir Path dir)
            throws Exception {
        clock = new ControlledRaceHost(impl);
        server = clock.start(0, new RoomHostConfig("Retries", "s3k", 0, 0,
                        "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                TrackValidationProfileSource.none());
        PlayerIdentity racerIdentity = PlayerIdentity.loadOrCreate(dir.resolve("racer"));
        racer = connect(racerIdentity, "RACER");
        racerInbox = new Inbox(racer);
        RaceClient watcher = connect(PlayerIdentity.loadOrCreate(dir.resolve("watcher")),
                "WATCHER");
        Inbox watcherInbox = new Inbox(watcher);
        assertWarningCaptureObservesRoomLog(racerIdentity.fingerprint());

        clock.startRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, WINDOW_SECONDS, "OPEN", null));
        racerInbox.until(control(ControlMessage.RoundStart.class));
        watcherInbox.until(control(ControlMessage.RoundStart.class));
        clock.advance(HostRoundEngine.COUNTDOWN_MILLIS);
        // One publisher for the whole run, as the client keeps across retries.
        publisher = new GhostStreamPublisher(racer::sendBinary);

        runAttempt(1, 26);
        assertBest(standingsAfterFinish(), 26);

        runAttempt(2, 20);
        assertBest(standingsAfterFinish(), 20);
        assertBest(watcherInbox.untilStandings(20), 20);

        runAttempt(3, 25);
        List<RaceClient.InboundEvent> afterSlower = sync(racer, racerInbox);
        assertTrue(afterSlower.stream().noneMatch(control(
                        ControlMessage.StandingsDelta.class)),
                "a slower finish must not broadcast standings");
        watcher.sendControl(new ControlMessage.Ping(++pingTag));
        // The watcher's ping is handled after the slower finish, so a broadcast for it
        // would already be queued ahead of this pong.
        assertTrue(watcherInbox.until(pong(pingTag)).stream().noneMatch(control(
                        ControlMessage.StandingsDelta.class)),
                "a slower finish must not reach other racers either");
        racer.sendControl(new ControlMessage.StandingsPageRequest(0));
        ControlMessage.StandingsPage page = (ControlMessage.StandingsPage) message(
                racerInbox.until(control(ControlMessage.StandingsPage.class)).getLast());
        assertEquals(1, page.rows().size());
        assertEquals(20, page.rows().getFirst().bestTimeFrames(),
                "the slower finish must leave the faster best in place");

        // Mid-run reset: abandon attempt 4 after some frames and start attempt 5 at once.
        beginAttempt(4);
        sendFrames(FRAMES_PER_ATTEMPT / 2);
        publisher.abandonAttempt();
        racer.sendControl(new ControlMessage.AttemptReset(4));
        runAttempt(5, 18);
        assertBest(standingsAfterFinish(), 18);
        assertBest(watcherInbox.untilStandings(18), 18);

        sync(racer, racerInbox);
        assertTrue(racer.isOpen() && watcher.isOpen(), "no racer may be kicked");
        List<LogRecord> racerViolations = roomWarnings.stream()
                .filter(record -> String.valueOf(record.getMessage())
                        .contains(racerIdentity.fingerprint()))
                .toList();
        assertTrue(racerViolations.isEmpty(), () -> "unexpected violations: "
                + racerViolations.stream().map(LogRecord::getMessage).toList());

        clock.advance(WINDOW_SECONDS * 1000L + HostRoundEngine.FINISH_GRACE_MILLIS + 1);
        ControlMessage.RoundEnd end = (ControlMessage.RoundEnd) message(
                racerInbox.until(control(ControlMessage.RoundEnd.class)).getLast());
        assertBest(end.finalStandings(), 18);
        watcherInbox.until(control(ControlMessage.RoundEnd.class));

        racer.close();
        watcher.close();
    }

    /**
     * Opens an attempt the way the client does: {@code AttemptStart} goes out directly
     * before the attempt's first frames, with no round trip in between.
     */
    private void beginAttempt(int attemptId) throws Exception {
        racer.sendControl(new ControlMessage.AttemptStart(attemptId));
        publisher.beginAttempt(attemptId);
        attemptFrames = 0;
        // The validator admits this much lead before any room time has elapsed.
        sendFrames(GhostStreamValidator.MAX_FRAME_LEAD);
        // Advance room time only once the host has anchored pacing at AttemptStart.
        sync(racer, racerInbox);
        clock.advance(FRAMES_PER_ATTEMPT * 1000L / 60);
    }

    private void runAttempt(int attemptId, int timeFrames) throws Exception {
        beginAttempt(attemptId);
        sendFrames(FRAMES_PER_ATTEMPT - attemptFrames);
        publisher.finishAttempt();
        int finishFrame = publisher.framesPublished() - 1;
        racer.sendControl(new ControlMessage.AttemptFinish(attemptId, timeFrames,
                finishFrame - timeFrames, finishFrame, "ab".repeat(32),
                HexFormat.of().formatHex(publisher.streamHashSha256()), null));
    }

    private void sendFrames(int count) {
        for (int frame = 0; frame < count; frame++) {
            publisher.onFrame(new GhostFrame(100 + attemptFrames++,
                    100, 1, false, false, false, 2, false));
        }
    }

    private List<ControlMessage.StandingsRow> standingsAfterFinish() throws Exception {
        return ((ControlMessage.StandingsDelta) message(
                racerInbox.until(control(ControlMessage.StandingsDelta.class)).getLast()))
                .rows();
    }

    private List<RaceClient.InboundEvent> sync(RaceClient client, Inbox inbox)
            throws Exception {
        client.sendControl(new ControlMessage.Ping(++pingTag));
        return inbox.until(pong(pingTag));
    }

    private RaceClient connect(PlayerIdentity identity, String name) throws Exception {
        return RaceClient.connect(URI.create("ws://127.0.0.1:" + server.port() + "/race"),
                identity, name, FP).get(10, TimeUnit.SECONDS);
    }

    /** Guards the zero-violation check against a log backend that bypasses this handler. */
    private void assertWarningCaptureObservesRoomLog(String fingerprint) {
        String probe = "capture probe fp=" + fingerprint;
        System.getLogger(RoomHost.class.getName()).log(System.Logger.Level.WARNING, probe);
        assertTrue(roomWarnings.removeIf(record -> probe.equals(record.getMessage())),
                "RoomHost warnings must reach the capture");
    }

    private static void assertBest(List<ControlMessage.StandingsRow> rows, int timeFrames) {
        assertEquals(1, rows.size());
        assertEquals("RACER", rows.getFirst().displayName());
        assertEquals(timeFrames, rows.getFirst().bestTimeFrames());
    }

    private static ControlMessage message(RaceClient.InboundEvent event) {
        return ((RaceClient.Control) event).message();
    }

    private static Predicate<RaceClient.InboundEvent> control(Class<?> type) {
        return event -> event instanceof RaceClient.Control control
                && type.isInstance(control.message());
    }

    private static Predicate<RaceClient.InboundEvent> pong(long tag) {
        return event -> event instanceof RaceClient.Control control
                && control.message() instanceof ControlMessage.Pong reply
                && reply.t0ClientMillis() == tag;
    }

    /** Ordered inbound events of one client; nothing drained past a match is lost. */
    private static final class Inbox {
        private final RaceClient client;
        private final Deque<RaceClient.InboundEvent> pending = new ArrayDeque<>();

        private Inbox(RaceClient client) {
            this.client = client;
        }

        /** Every event up to and including the first match, failing on a kick. */
        List<RaceClient.InboundEvent> until(Predicate<RaceClient.InboundEvent> match)
                throws InterruptedException {
            List<RaceClient.InboundEvent> seen = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                pending.addAll(client.drainInbound());
                while (!pending.isEmpty()) {
                    RaceClient.InboundEvent event = pending.removeFirst();
                    assertFalse(event instanceof RaceClient.Disconnected
                                    || control(ControlMessage.Kick.class).test(event),
                            () -> "racer removed from the room: " + event);
                    seen.add(event);
                    if (match.test(event)) {
                        return seen;
                    }
                }
                Thread.sleep(20);
            }
            throw new AssertionError("timed out waiting for room event");
        }

        List<ControlMessage.StandingsRow> untilStandings(int bestTimeFrames)
                throws InterruptedException {
            return ((ControlMessage.StandingsDelta) message(until(event ->
                    event instanceof RaceClient.Control control
                            && control.message() instanceof ControlMessage.StandingsDelta delta
                            && delta.rows().stream().anyMatch(row ->
                            row.bestTimeFrames() == bestTimeFrames)).getLast())).rows();
        }
    }
}
