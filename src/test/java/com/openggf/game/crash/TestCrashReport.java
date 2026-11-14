package com.openggf.game.crash;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TestCrashReport {
    private static final String HOME = "/srv/player-home";

    private static RuntimeException thrownAt(RuntimeException failure, String... frames) {
        StackTraceElement[] trace = new StackTraceElement[frames.length];
        for (int i = 0; i < frames.length; i++) {
            int dot = frames[i].lastIndexOf('.');
            trace[i] = new StackTraceElement(frames[i].substring(0, dot), frames[i].substring(dot + 1),
                    frames[i].substring(frames[i].lastIndexOf('.', dot - 1) + 1, dot) + ".java", 10 + i);
        }
        failure.setStackTrace(trace);
        return failure;
    }

    private static CrashReport report(Throwable failure) {
        return new CrashReport(FaultKind.classify(failure), failure, Instant.parse("2026-11-14T12:00:00Z"),
                List.of(new CrashReport.Field("Version", "0.8-test")),
                List.of(new CrashReport.Field("Game", "s3k"), new CrashReport.Field("Mods", "none")), HOME);
    }

    @Test
    void faultsAreNamedByTheWorkOnTheStackNotTheMessage() {
        assertEquals(FaultKind.REWIND, FaultKind.classify(thrownAt(new IllegalStateException("level load"),
                "com.openggf.level.Foo.bar", "com.openggf.game.rewind.RewindController.seekTo", "com.openggf.Engine.tick")));
        assertEquals(FaultKind.LEVEL_LOAD, FaultKind.classify(thrownAt(new IllegalStateException("rewind"),
                "com.openggf.level.Pattern.decode", "com.openggf.level.LevelManager.loadZoneAndAct")));
        assertEquals(FaultKind.LEVEL_LOAD, FaultKind.classify(thrownAt(new IllegalStateException(),
                "com.openggf.level.LevelLoadPreparer.prepare")));
        assertEquals(FaultKind.ENGINE, FaultKind.classify(thrownAt(new IllegalStateException("rewind level load"),
                "com.openggf.level.LevelManager.update", "com.openggf.Engine.tick")));
    }

    @Test
    void aRewindReloadingTheLevelIsStillARewind() {
        var cause = thrownAt(new IllegalStateException("tiles"), "com.openggf.level.LevelManager.loadLevel");
        var wrapper = thrownAt(new RuntimeException("seek", cause), "com.openggf.game.rewind.Keyframes.restore");
        assertEquals(FaultKind.REWIND, FaultKind.classify(wrapper));
    }

    @Test
    void wrappedAndSuppressedFailuresAreClassifiedToo() {
        var inner = thrownAt(new IllegalStateException(), "com.openggf.game.rewind.SegmentCache.fill");
        var outer = thrownAt(new RuntimeException("frame", inner), "com.openggf.Engine.tick");
        assertEquals(FaultKind.REWIND, FaultKind.classify(outer));
        var suppressing = thrownAt(new RuntimeException("frame"), "com.openggf.Engine.tick");
        suppressing.addSuppressed(inner);
        assertEquals(FaultKind.REWIND, FaultKind.classify(suppressing));
    }

    @Test
    void theReportNamesTheErrorItsRootCauseAndWhereItWasRaised() {
        var root = thrownAt(new IllegalArgumentException("bad slot 7"),
                "java.util.Objects.checkIndex", "com.openggf.level.objects.ObjectManager.spawn");
        var outer = thrownAt(new IllegalStateException("frame failed", root), "com.openggf.Engine.tick");
        CrashReport report = report(outer);
        assertEquals("IllegalStateException: frame failed", report.error());
        assertEquals("IllegalArgumentException: bad slot 7", report.rootCause());
        assertEquals("ObjectManager.spawn(ObjectManager.java:11)", report.where());
        assertEquals("", report(root).rootCause());
    }

    @Test
    void textLeavingTheProcessNamesTheHomeFolderAsTilde() {
        var failure = thrownAt(new IllegalStateException("cannot read " + HOME + "/roms/s3k.gen"), "com.openggf.Engine.tick");
        CrashReport report = report(failure);
        assertFalse(report.fullText().contains(HOME));
        assertTrue(report.fullText().contains("~/roms/s3k.gen"));
        assertFalse(report.shortTrace(5).contains(HOME));
        assertFalse(decoded(CrashIssueLink.query(report, "crash.txt")).values().stream().anyMatch(v -> v.contains(HOME)));
    }

    @Test
    void theShortTraceLabelsCausesAndSuppressedFailuresAndCountsWhatItLeftOut() {
        var cause = thrownAt(new IllegalArgumentException("cause"), "com.openggf.A.a", "com.openggf.B.b", "com.openggf.C.c");
        var outer = thrownAt(new IllegalStateException("outer", cause), "com.openggf.Engine.tick");
        outer.addSuppressed(thrownAt(new IllegalStateException("rollback"), "com.openggf.D.d"));
        String trace = report(outer).shortTrace(2);
        assertTrue(trace.startsWith("IllegalStateException: outer\n    at Engine.tick(Engine.java:10)"), trace);
        assertTrue(trace.contains("Caused by: IllegalArgumentException: cause"), trace);
        assertTrue(trace.contains("Suppressed: IllegalStateException: rollback"), trace);
        assertTrue(trace.contains("    ... 1 more"), trace);
    }

    @Test
    void theIssueQueryFillsEveryFormFieldItNames() throws Exception {
        CrashReport report = report(thrownAt(new IllegalStateException("no keyframe"),
                "com.openggf.game.rewind.RewindController.seekTo"));
        Map<String, String> fields = decoded(CrashIssueLink.query(report, "crash-20261114.txt"));
        assertEquals("crash.yml", fields.get("template"));
        assertEquals("[Crash] IllegalStateException: no keyframe at RewindController.seekTo(RewindController.java:10)",
                fields.get("title"));
        assertEquals("Version: 0.8-test", fields.get("version"));
        assertEquals("Game: s3k\nMods: none", fields.get("session"));
        assertTrue(fields.get("error").startsWith("IllegalStateException: no keyframe"));
        assertEquals("crash-20261114.txt", fields.get("report-file"));
        Set<String> formIds = formIds();
        for (String name : fields.keySet()) {
            if (!name.equals("template") && !name.equals("title")) assertTrue(formIds.contains(name), name);
        }
        URI uri = URI.create(CrashIssueLink.address(report, "crash.txt"));
        assertEquals("github.com", uri.getHost());
        assertEquals("/OpenGGF/OpenGGF/issues/new", uri.getPath());
        assertNull(uri.getFragment());
    }

    @Test
    void aHugeTraceIsShortenedUntilTheLinkFits() {
        String[] frames = new String[400];
        for (int i = 0; i < frames.length; i++) frames[i] = "com.openggf.deep.Frame" + i + ".call";
        RuntimeException failure = thrownAt(new IllegalStateException("x".repeat(20_000)), frames);
        for (int i = 0; i < 5; i++) failure = thrownAt(new RuntimeException("wrap " + i, failure), frames);
        String query = CrashIssueLink.query(report(failure), "crash.txt");
        assertTrue(query.length() <= CrashIssueLink.MAX_QUERY_LENGTH, String.valueOf(query.length()));
        assertTrue(decoded(query).get("error").contains("shortened"));
    }

    @Test
    void reportsAreSavedWithTheFullTrace(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        CrashReport report = report(thrownAt(new IllegalStateException("saved"), "com.openggf.Engine.tick"));
        Optional<Path> file = CrashReportFile.write(report, directory.resolve("crash-reports"));
        String text = Files.readString(file.orElseThrow());
        assertTrue(text.contains("Kind: ENGINE"));
        assertTrue(text.contains("Version: 0.8-test"));
        assertTrue(text.contains("at com.openggf.Engine.tick(Engine.java:10)"));
    }

    @Test
    void anUnwritableFolderLosesOnlyTheFile(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path blocker = Files.writeString(directory.resolve("crash-reports"), "a file, not a folder");
        CrashReport report = report(thrownAt(new IllegalStateException("x"), "com.openggf.Engine.tick"));
        assertEquals(Optional.empty(), CrashReportFile.write(report, blocker));
    }

    @Test
    void everyFaultKindHasItsArtAndMessages() {
        for (FaultKind kind : FaultKind.values()) {
            assertNotNull(CrashReport.class.getClassLoader().getResource(kind.artResource()), kind.artResource());
            for (String text : List.of(CrashScreen.headline(kind), CrashScreen.caption(kind))) {
                assertFalse(text.isBlank() || text.contains("crash."), kind + ": " + text);
            }
        }
    }

    private static Map<String, String> decoded(String query) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            fields.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return fields;
    }

    private static Set<String> formIds() throws Exception {
        Path form = Path.of(".github/ISSUE_TEMPLATE", CrashIssueLink.TEMPLATE);
        var ids = new LinkedHashSet<String>();
        var matcher = Pattern.compile("(?m)^\\s+id:\\s*(\\S+)\\s*$").matcher(Files.readString(form));
        while (matcher.find()) ids.add(matcher.group(1));
        assertFalse(ids.isEmpty());
        return ids;
    }
}
