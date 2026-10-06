package com.openggf.mods.code;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;

import static com.openggf.control.InputActionMasks.ACTION_A;
import static org.junit.jupiter.api.Assertions.*;

/** Real creator GolfMode/room/scene bridge in independent JVMs; this is a configured fixture, not a production boot claim. */
@RequiresRom(SonicGame.SONIC_2)
class TestGolfOnlineIntegration {
    @TempDir Path temp;

    @Test @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void twoProcessesExchangeActualShotsScenesPausesAndConcession() throws Exception {
        File found = RomTestUtils.ensureSonic2RomAvailable();
        assertNotNull(found, "RequiresRom must provide the existing S2 ROM");
        Path rom = found.toPath().toRealPath();
        Path jar = compileAndPackage();
        int port;
        try (var reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (var host = new Peer("host", port, jar, rom, Files.createDirectory(temp.resolve("host")))) {
            host.step(1, 0, 0, false); // Allocate the listener before the guest starts its own engine.
            try (var guest = new Peer("guest", port, jar, rom, Files.createDirectory(temp.resolve("guest")))) {
                assertNotEquals(host.state.text("pid"), guest.state.text("pid"), "peers must own different JVM resources");
                pump(host, guest, (one, two) -> one.flag("ready") && two.flag("ready") && one.number("turn") >= 1 && two.number("turn") >= 1,
                        700, "first ready handshake and TurnOpened");
                assertEquals("sonic", host.state.text("hostCharacter")); assertEquals("tails", host.state.text("guestCharacter"));
                assertEquals("sonic", guest.state.text("hostCharacter")); assertEquals("tails", guest.state.text("guestCharacter"));
                compareViewAndScores(host, guest);

                long firstTurn = host.state.number("turn");
                shot(host);
                assertEquals(1, host.state.number("pending"), "host's real meter committed the first shot");
                assertEquals(1, host.state.number("strokes0"));
                pump(host, guest, (one, two) -> one.number("turn") > firstTurn && one.number("owner") == 1 && two.number("turn") == one.number("turn"),
                        700, "host shot resolves through native physics into guest turn");
                compareViewAndScores(host, guest);

                assertTrue(guest.state.flag("guideAvailable"),"guest guide has authoritative turn and accepted frame");
                assertEquals(host.state.number("ballX"),guest.state.number("guideCentreX"));
                assertEquals(host.state.number("ballY"),guest.state.number("guideCentreY"));
                assertEquals(host.state.number("ballAngle"),guest.state.number("guideAngle"));
                assertEquals(host.state.number("cameraX"),guest.state.number("guideCameraX"));
                assertEquals(host.state.number("cameraY"),guest.state.number("guideCameraY"));
                long guestTurn = host.state.number("turn"), gameplayBeforeGuest = host.state.number("gameplayRows");
                guest.step(60, 1, 0, false); guest.step(30, 1, 0, false);
                assertEquals(90, guest.state.number("elevation"), "guest can submit the vertical chip through its real meter");
                shot(guest,-100);
                assertEquals("FEEDBACK", guest.state.text("stage"), "guest locks spin and power");
                assertEquals(-100,guest.state.number("spin"));
                guest.step(1, 0, 0, true); // Cross the request/acceptance boundary with a real guest menu pause.
                pump(host, guest, (one, two) -> one.flag("held") && two.flag("held"), 80, "guest pause reaches authoritative host");
                host.step(1, 0, 0, true); host.step(1, 0, 0, false); // Add the host's own pause contribution.
                guest.step(1, 0, 0, false); guest.step(1, 0, 0, true); // Guest resumes; host menu must still hold.
                for (int row = 0; row < 8; row++) { host.step(1, 0, 0, false); guest.step(1, 0, 0, false); }
                assertTrue(host.state.flag("held"), "guest resume cannot clear the host menu");
                assertTrue(guest.state.flag("held"), "host publishes its remaining pause");
                host.step(1, 0, 0, true);
                pump(host, guest, (one, two) -> one.number("pending") == 2 && two.flag("accepted"), 80, "one accepted remote shot resumes");
                assertEquals(1, host.state.number("strokes1"), "remote request accepted exactly once across pause/retry");
                assertEquals(-100,host.state.number("spin"),"host accepted signed backspin");
                pump(host, guest, (one, two) -> one.number("turn") > guestTurn && one.number("owner") == 0 && two.number("turn") == one.number("turn"),
                        700, "guest shot resolves through host native physics into next turn");
                assertTrue(host.state.number("gameplayRows") > gameplayBeforeGuest, "host simulated the remote flight");
                assertEquals(1, host.state.number("strokes1"));
                compareViewAndScores(host, guest);
                assertEquals(0, guest.state.number("gameplayRows"), "guest never executes native gameplay");
                assertTrue(guest.state.number("heldRows") > 20, "every post-TurnOpened held guest row was checked");

                long thirdTurn=host.state.number("turn"); shot(host);
                assertEquals(1,host.state.number("chargeCues"),"local putt has one startup charge request");
                pump(host,guest,(one,two)->one.number("turn")>thirdTurn&&one.number("owner")==1
                        &&two.number("turn")==one.number("turn"),700,"second guest turn");
                long fourthTurn=host.state.number("turn"); shot(guest);
                pump(host,guest,(one,two)->one.number("pending")==4&&two.flag("accepted"),80,"remote putt accepted");
                host.step(3,0,0,false);
                assertEquals(1,host.state.number("chargeCues"),"remote putt uses the same startup native charge count");
                pump(host,guest,(one,two)->one.number("turn")>fourthTurn&&one.number("owner")==0
                        &&two.number("turn")==one.number("turn"),700,"remote putt resolves once");
                assertEquals(2,host.state.number("strokes0"));assertEquals(2,host.state.number("strokes1"));
                compareViewAndScores(host,guest);
                String score0 = guest.state.text("wire0"), score1 = guest.state.text("wire1");
                guest.step(1, 0, 0, true); guest.step(1, 0, 0, false);
                guest.step(1, 2, 0, false); guest.step(1, 0, 0, false); guest.step(1, 2, 0, false);
                guest.step(1, 0, 0, false); guest.step(1, 0, ACTION_A, false); // Menu: Concede, after Rewind.
                pump(host, guest, (one, two) -> one.flag("ended") && two.flag("ended"), 80, "explicit guest concession reaches host mode");
                assertEquals("CONCEDED", host.state.text("matchStatus")); assertTrue(host.state.flag("dnf1"));
                assertEquals(1, guest.state.number("concededOwner"));
                assertEquals(score0, guest.state.text("wire0")); assertEquals(score1, guest.state.text("wire1"));
                host.command("OVERLAY"); guest.command("OVERLAY"); // Terminal scorecard must remain drawable.
            }
        }
        try (var reused = new ServerSocket(port)) { assertEquals(port, reused.getLocalPort(), "closed host port is reusable"); }
    }

    @Test @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void bothOnlineGolfersCanRewindWithoutChangingTurnOrDuplicatingScore() throws Exception {
        Path rom = RomTestUtils.ensureSonic2RomAvailable().toPath().toRealPath(), jar = compileAndPackage();
        int port; try (var reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (var host = new Peer("host", port, jar, rom, Files.createDirectory(temp.resolve("host")), true)) {
            host.step(1, 0, 0, false);
            try (var guest = new Peer("guest", port, jar, rom, Files.createDirectory(temp.resolve("guest")), true)) {
                pump(host, guest, (a,b) -> a.flag("ready") && b.flag("ready") && a.number("turn") >= 1 && b.number("turn") >= 1,
                        700, "rewind room opens");
                long x = host.state.number("ballX"), y = host.state.number("ballY"), turn = host.state.number("turn"), id = host.state.number("shot");
                shot(host);
                pump(host, guest, (a,b) -> a.text("phase").equals("REVIEW") && b.text("phase").equals("REVIEW"), 700, "settled host shot remains reviewable");
                pauseRewind(host);
                pump(host, guest, (a,b) -> a.text("phase").equals("AIM") && b.text("phase").equals("AIM") && a.number("shot") > id && b.number("shot") == a.number("shot"), 200, "host rewind reaches origin on both peers");
                assertEquals(turn, host.state.number("turn")); assertEquals(0, host.state.number("strokes0"));
                assertEquals(x,host.state.number("ballX")); assertEquals(y,host.state.number("ballY"));
                assertEquals(2,guest.state.number("holeRewinds")); assertEquals(0,guest.state.number("turnRewinds")); compareViewAndScores(host,guest);
                shot(host);
                pump(host,guest,(a,b)->a.number("owner")==1 && b.number("owner")==1 && a.number("turn")>turn,700,"kept retry passes to guest");
                long guestTurn=host.state.number("turn"), guestId=host.state.number("shot"), guestX=host.state.number("ballX"), guestY=host.state.number("ballY");
                shot(guest);
                pump(host,guest,(a,b)->a.text("phase").equals("REVIEW") && b.text("phase").equals("REVIEW"),700,"guest shot reviewed by host");
                pauseRewind(guest);
                pump(host,guest,(a,b)->a.text("phase").equals("AIM") && b.text("phase").equals("AIM") && a.number("shot")>guestId && b.number("shot")==a.number("shot"),200,"guest rewind is host-owned");
                assertEquals(guestTurn,host.state.number("turn")); assertEquals(0,host.state.number("strokes1"));
                assertEquals(guestX,host.state.number("ballX")); assertEquals(guestY,host.state.number("ballY"));
                assertEquals(2,guest.state.number("holeRewinds")); assertEquals(0,guest.state.number("turnRewinds")); compareViewAndScores(host,guest);
                assertEquals(0,guest.state.number("gameplayRows"),"guest never simulates reverse or forward physics");
                shot(guest);
                pump(host,guest,(a,b)->a.number("owner")==0 && b.number("owner")==0 && a.number("turn")>guestTurn,700,"guest keeps retry");
                shot(host);
                pump(host,guest,(a,b)->a.text("phase").equals("REVIEW") && b.text("phase").equals("REVIEW"),700,"new host turn renews per-turn allowance");
                assertEquals(1,host.state.number("turnRewinds"));
                host.step(1,0,ACTION_A,false); host.step(1,0,0,false);
                pump(host,guest,(a,b)->a.number("owner")==1 && b.number("owner")==1,100,"A keeps review and passes turn online");
                compareViewAndScores(host,guest);
            }
        }
    }
    private static void pauseRewind(Peer peer) throws Exception {
        peer.step(1,0,0,true); peer.step(1,0,0,false); peer.step(1,2,0,false); peer.step(1,0,ACTION_A,false);
    }

    private Path compileAndPackage() throws Exception {
        Path project = Path.of(System.getProperty("golf.integration.project", "examples/putt-putt-paradise")).toAbsolutePath();
        Path classes = Files.createDirectory(temp.resolve("creator-classes"));
        var arguments = new ArrayList<>(java.util.List.of("--release", "21", "-classpath", System.getProperty("java.class.path"), "-d", classes.toString()));
        try (var files = Files.walk(project.resolve("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> arguments.add(path.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)), "actual external source must compile");
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"), classes.resolve("META-INF/openggf-mod.yaml"));
        Path jar = temp.resolve("validated-paradise.jar");
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", classes.toString(), "--out", jar.toString()}, System.out), "normal SDK validation is required");
        return jar;
    }
    private static void shot(Peer peer) throws Exception { shot(peer,0); }
    private static void shot(Peer peer,int spin) throws Exception {
        peer.step(1, 0, 0, false); peer.step(1, 0, ACTION_A, false);
        if (peer.state.text("stage").equals("SPIN")) {
            if(spin<0) { peer.step(60,0,0,false);peer.step(30,0,0,false); }
            else peer.step(60,0,0,false);
            peer.step(1,0,ACTION_A,false);
        }
        peer.step(2, 0, 0, false); peer.step(1, 0, ACTION_A, false);
    }
    private static void pump(Peer host, Peer guest, BiPredicate<State, State> condition, int maxRows, String message) throws Exception {
        for (int row = 0; row < maxRows && !condition.test(host.state, guest.state); row++) {
            host.step(1, 0, 0, false); guest.step(1, 0, 0, false); Thread.sleep(2);
        }
        assertTrue(condition.test(host.state, guest.state), () -> message + "\nhost=" + host.state + "\nguest=" + guest.state);
    }
    private static void compareViewAndScores(Peer host, Peer guest) throws Exception {
        host.step(6, 0, 0, false); long revision = host.state.number("viewRevision");
        assertTrue(revision >= 0, "host published a real typed scene");
        for (int row = 0; row < 100 && guest.state.number("viewRevision") != revision; row++) {
            guest.step(1, 0, 0, false); Thread.sleep(2);
        }
        assertEquals(revision, guest.state.number("viewRevision"), "guest consumed the same authoritative view revision");
        assertNotEquals("none", host.state.text("viewHash")); assertEquals(host.state.text("viewHash"), guest.state.text("viewHash"), "locally ROM-composed transmitted scenes match");
        assertEquals(host.state.text("wire0"), guest.state.text("wire0")); assertEquals(host.state.text("wire1"), guest.state.text("wire1"));
    }

    private record State(Map<String, String> values) {
        String text(String key) { return values.getOrDefault(key, "missing"); }
        long number(String key) { return Long.parseLong(text(key)); }
        boolean flag(String key) { return Boolean.parseBoolean(text(key)); }
        static State parse(String line) {
            Map<String, String> values = new HashMap<>();
            for (String item : line.substring(5).split("\\|")) { int equals = item.indexOf('='); values.put(item.substring(0, equals), item.substring(equals + 1)); }
            return new State(Map.copyOf(values));
        }
    }
    private static final class Peer implements AutoCloseable {
        final Process process;
        final BufferedWriter commands;
        final ArrayBlockingQueue<State> responses = new ArrayBlockingQueue<>(16);
        final ArrayDeque<String> diagnostic = new ArrayDeque<>();
        final AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        State state;
        Peer(String role, int port, Path jar, Path rom, Path directory) throws Exception {
            this(role, port, jar, rom, directory, false);
        }
        Peer(String role, int port, Path jar, Path rom, Path directory, boolean rewinds) throws Exception {
            Path peerTemp = Files.createDirectory(directory.resolve("tmp"));
            String classpath = Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")).split(File.pathSeparator))
                    .map(path -> Path.of(path).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(File.pathSeparator));
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx768m",
                    "-Duser.home=" + directory, "-Dsonic2.rom.path=" + rom,
                    "-Djava.io.tmpdir=" + peerTemp,
                    "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + directory.resolve("native"), "-classpath", classpath,
                    GolfOnlinePeerProbe.class.getName(), role, Integer.toString(port), jar.toString(), rewinds ? "rewinds" : "off")
                    .directory(directory.toFile()).redirectErrorStream(true).start();
            commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            Thread.ofVirtual().name("golf-integration-" + role).start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    for (String line; (line = lines.readLine()) != null;) {
                        if (line.length() > 32_768) throw new IllegalStateException("unbounded peer output");
                        if (line.startsWith("GOLF|")) {
                            if (!responses.offer(State.parse(line))) throw new IllegalStateException("peer response queue overflow");
                        } else synchronized (diagnostic) { if (diagnostic.size() == 24) diagnostic.removeFirst(); diagnostic.addLast(line); }
                    }
                } catch (Throwable failure) { readerFailure.set(failure); }
            });
            try { state = response(Duration.ofSeconds(30)); }
            catch (Throwable failure) { process.destroyForcibly(); throw failure; }
        }
        void step(int count, int held, int actions, boolean start) throws Exception { command("STEP " + count + " " + held + " " + actions + " " + start); }
        void command(String command) throws Exception {
            commands.write(command); commands.newLine(); commands.flush(); state = response(Duration.ofSeconds(15));
        }
        private State response(Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                State result = responses.poll(100, TimeUnit.MILLISECONDS);
                if (result != null) return result;
                if (!process.isAlive() || readerFailure.get() != null) break;
            }
            String output; synchronized (diagnostic) { output = String.join("\n", diagnostic); }
            fail("Peer did not answer; alive=" + process.isAlive() + ", reader=" + readerFailure.get() + "\n" + output);
            throw new AssertionError();
        }
        @Override public void close() throws Exception {
            try {
                if (process.isAlive()) {
                    command("QUIT"); assertTrue(state.flag("closed")); assertEquals(0, state.number("workers"));
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS), "peer exits after all room workers close");
                }
                assertEquals(0, process.exitValue(), "peer JVM failed");
            } finally {
                try { commands.close(); }
                finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
            }
        }
    }
}
