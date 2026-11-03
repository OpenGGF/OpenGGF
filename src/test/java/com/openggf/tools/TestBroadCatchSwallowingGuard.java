package com.openggf.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Legacy broad catches may only shrink; new catch-and-fallback is never an engine repair. */
class TestBroadCatchSwallowingGuard {
    private static final String FIX = "Fix the engine/service cause and propagate failures. "
            + "Remove retired baseline rows; never add allowances for new catch-and-fallback. "
            + "Legitimate owning failure boundaries require explicit review, not a generic logger exemption.";

    @Test void mainSourcesHaveOnlyInventoriedLegacyHandlers() throws IOException {
        Path root = Path.of(System.getProperty("project.basedir", "."), "src/main/java");
        var sites = BroadCatchSourceScanner.scan(root);
        assertTrue(sites.stream().anyMatch(site -> site.source().contains("/objects/")),
                "the inventory must actually cover object sources");
        assertInventory(BroadCatchSourceScanner.inventory(sites), baseline());
    }

    @Test void syntheticSwallowingHandlersAreRejected() throws IOException {
        var sites = BroadCatchSourceScanner.snippet("""
                class Control {
                    void run() {
                        try { engine(); } catch (Exception ignored) { /* silent */ }
                        try { engine(); } catch (java.lang.RuntimeException e) { return; }
                        try { engine(); } catch (Throwable e) { LOG.fine(e.toString()); }
                        try { engine(); } catch (IllegalStateException | java.lang.Exception e) { fallback(); }
                        try { engine(); } catch (Exception e) { Runnable r = () -> { throw new Error(e); }; }
                        try { engine(); } catch (Exception e) { if (ready()) throw new Error(e); }
                        try { engine(); } catch (Exception e) { LOG.warning(e.toString()); }
                        try { engine(); } catch (Exception e) { if (ready()) return; throw e; }
                    }
                }
                """);
        assertEquals(8, sites.size(), "comments, qualification, multi-catch and nested throws cannot hide debt");
        var failure = assertThrows(AssertionError.class,
                () -> assertInventory(BroadCatchSourceScanner.inventory(sites), Map.of()));
        assertTrue(failure.getMessage().contains("Control.java#Control#run()"));
    }

    @Test void cleanupThenRethrowIsNotSwallowing() throws IOException {
        assertEquals(List.of(), BroadCatchSourceScanner.snippet("""
                class Control {
                    void run() {
                        try { engine(); } catch (Exception e) { cleanup(); throw e; }
                        try { engine(); } catch (RuntimeException | Error e) { throw new IllegalStateException(e); }
                        try { engine(); } catch (Throwable e) {
                            if (ready()) { throw e; } else { throw new Error(e); }
                        }
                        // A string and a comment are not a catch.
                        String text = "catch (Exception ignored) {}";
                        /* catch (Throwable ignored) {} */
                    }
                }
                """));
    }

    @Test void ratchetRejectsExtraMovedRewrittenAndRetiredAllowances() throws IOException {
        var first = BroadCatchSourceScanner.inventory(BroadCatchSourceScanner.snippet(
                "class Control { void run() { try { engine(); } catch (Exception e) {} } }"));
        String key = first.keySet().iterator().next();
        assertThrows(AssertionError.class, () -> assertInventory(Map.of(key, 2), first));
        assertThrows(AssertionError.class, () -> assertInventory(Map.of(), first),
                "retirement must delete the baseline entry, leaving no spare allowance");
        for (String replacement : List.of("other()", "run(int x)", "engine2();")) {
            String source = "class Control { void run() { try { engine(); } catch (Exception e) {} } }";
            source = replacement.equals("engine2();") ? source.replace("engine();", replacement)
                    : source.replace("run()", replacement);
            var moved = BroadCatchSourceScanner.inventory(BroadCatchSourceScanner.snippet(source));
            assertThrows(AssertionError.class, () -> assertInventory(moved, first));
        }
    }

    private static void assertInventory(Map<String, Integer> actual, Map<String, Integer> approved) {
        var keys = new TreeSet<>(actual.keySet());
        keys.addAll(approved.keySet());
        var differences = keys.stream()
                .filter(key -> !actual.getOrDefault(key, 0).equals(approved.getOrDefault(key, 0)))
                .map(key -> key + " observed=" + actual.getOrDefault(key, 0)
                        + " baseline=" + approved.getOrDefault(key, 0)).toList();
        assertEquals(List.of(), differences, FIX);
    }

    private static Map<String, Integer> baseline() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        try (var input = TestBroadCatchSwallowingGuard.class.getResourceAsStream(
                "/architecture/broad-catch-legacy.tsv")) {
            assertNotNull(input, "required broad-catch legacy inventory");
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                assertEquals(3, fields.length, "count, exact site and one-line reason required: " + line);
                int count = Integer.parseInt(fields[0]);
                assertTrue(count > 0);
                assertFalse(fields[2].isBlank(), "legacy debt/boundary needs a reason");
                assertNull(counts.put(fields[1], count), "duplicate baseline site: " + fields[1]);
            }
        }
        return counts;
    }
}
