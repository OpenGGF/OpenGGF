package com.openggf.editor.route;

import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteManifest;
import com.openggf.tools.RouteEditorProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real disposable subprocesses exercise the route tape job protocol; the child stands in for the worker. */
@Timeout(30)
class TestRouteTapeJob {
    @TempDir
    Path dir;

    private RouteTapeJob.Request request(Path manifest, String script) {
        return new RouteTapeJob.Request(RouteTapeJob.Kind.REPLAY, manifest, script, null, false);
    }

    private static String content(Path manifest, String script) throws Exception {
        RouteArtifact source = RouteFiles.read(manifest);
        var tape = RouteFiles.tapeFromScript(script);
        RouteManifest m = source.manifest();
        return new RouteArtifact(new RouteManifest(m.launch(), tape.hash(), tape.size(), m.provenance(),
                m.qualification(), m.nativeFacts()), tape).content().toString();
    }

    @Test
    void workerCommandUsesTheHostClasspathAndAPassingReplayCarriesItsPathAndContent() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "4 R\n");
        String script = "3 R\n1 R+A\n";
        var command = new AtomicReference<List<String>>();
        var roms = Map.of("s2", dir.resolve("s2.gen"));
        try (var job = new RouteTapeJob(() -> roms, args -> {
            command.set(args);
            return child("pass");
        })) {
            String content = content(manifest, script);
            job.start(request(manifest, script), content, 4);
            await(() -> !job.poll().running());
            var update = job.poll();
            assertEquals(RouteTapeJob.Phase.PASSED, update.phase(), update.message());
            assertEquals(content, update.content());
            assertEquals(4, update.row());
            assertEquals(4, update.path().path(com.openggf.route.PlayerSlot.P1).size());
            assertEquals(4, update.path().path(com.openggf.route.PlayerSlot.P2).size());
            assertEquals(1f, update.fraction());
            int classpath = command.get().indexOf("-cp") + 1;
            assertEquals(System.getProperty("java.class.path"), command.get().get(classpath));
            assertEquals("com.openggf.tools.RouteEditorWorker", command.get().get(classpath + 1));
        }
    }

    @Test
    void mismatchedContentCrashesAndCancellationNeverPublishAVerdict() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "4 R\n");
        String script = "4 R\n";
        String content = content(manifest, script);
        try (var job = new RouteTapeJob(Map::of, args -> child("other"))) {
            job.start(request(manifest, script), content, 4);
            await(() -> !job.poll().running());
            assertEquals(RouteTapeJob.Phase.ERROR, job.poll().phase());
            assertTrue(job.poll().message().contains("other content"), job.poll().message());
        }
        try (var job = new RouteTapeJob(Map::of, args -> child("crash"))) {
            job.start(request(manifest, script), content, 4);
            await(() -> !job.poll().running());
            assertEquals(RouteTapeJob.Phase.ERROR, job.poll().phase());
            assertTrue(job.poll().message().contains("fixture crash"), job.poll().message());
        }
        var launched = new AtomicReference<Process>();
        try (var job = new RouteTapeJob(Map::of, args -> {
            var child = child("wait");
            launched.set(child);
            return child;
        })) {
            job.start(request(manifest, script), content, 4);
            await(() -> launched.get() != null && job.poll().total() == 4 && job.poll().pass() == 1
                    && job.poll().done() == 2);
            job.cancel();
            assertEquals(RouteTapeJob.Phase.CANCELLED, job.poll().phase());
            assertFalse(launched.get().isAlive(), "Cancel retires the owned worker");
            Thread.sleep(200);
            assertEquals(RouteTapeJob.Phase.CANCELLED, job.poll().phase(), "A cancelled job publishes nothing later");
            assertThrows(java.io.IOException.class, () -> {
                job.start(request(manifest, script), content, 4);
                job.start(request(manifest, script), content, 4);
            }, "One job at a time");
        }
    }

    private static Process child(String mode) throws java.io.IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx128m", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Child.class.getName(), mode).start();
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) fail("Route tape job did not reach the expected state");
            Thread.sleep(10);
        }
    }

    public static final class Child {
        public static void main(String[] args) throws Exception {
            try (var input = new RouteEditorProtocol.LineReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                var request = RouteEditorProtocol.decodeRequest(input);
                String content = content(request.manifest(), request.script());
                int rows = RouteFiles.tapeFromScript(request.script()).size();
                if (args[0].equals("crash")) {
                    System.err.println("fixture crash");
                    System.exit(3);
                }
                System.out.println(RouteEditorProtocol.progress(new RouteEditorProtocol.Progress(1, 2, rows / 2, rows)));
                System.out.flush();
                if (args[0].equals("wait")) {
                    Thread.sleep(60_000);
                    return;
                }
                for (int row = 0; row < rows; row++) {
                    System.out.println(RouteEditorProtocol.point(new RouteEditorProtocol.Point(1, row, 0, 10 * row, 20, false)));
                    System.out.println(RouteEditorProtocol.point(new RouteEditorProtocol.Point(2, row, 0, 10 * row - 30, 20, false)));
                }
                System.out.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                        RouteEditorProtocol.Verdict.PASSED, rows, "fixture", args[0].equals("other") ? "f".repeat(64) : content)));
            }
        }
    }
}
