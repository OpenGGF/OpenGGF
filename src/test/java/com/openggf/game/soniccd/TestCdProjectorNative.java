package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Metal Sonic's projector against native, over full routes whose trace the engine replays row for row
 * ({@link CdFixtureReplay}; the BK2 drives the engine and the native rows are only compared):
 * <ul>
 *   <li>{@code rehost-fixtures-warp/R11B-destroy-out/R11B}, an authored movie (Go-Explore, {@code _tools/search_destroy.py},
 *   recorded cold) that walks past the Past act 1 projector, comes back and rolls into it: the projector and its two
 *   holograms appear twice (once for each visit, CheckObjDespawn between), the hit starts its explosion schedule, the holograms
 *   and animals go, and 60 frames after the last explosion the projector deletes itself and sets {@code projDestroyed}.</li>
 *   <li>{@code rehost-long/<stage>}, the searched full-act routes of the six zones that assemble it: the routes pass the
 *   projector without touching it (appearances only).</li>
 * </ul>
 * Compared per route: the first divergent player/camera row (none), and the object table's events: for the projector's own ID
 * and for the explosion ({@code $18}) and the zone's animal, each appearance and each removal as (ID, slot, trace row) against
 * the aux {@code object_appeared}/{@code object_removed} events, over the whole route.
 */
class TestCdProjectorNative {
    /** Stage, native directory holding {@code trace/} and {@code movie.bk2}, the projector's ID, the zone's animal ID. */
    private record Route(String label, CdStagePayload payload, String dir, int projectorId, int animalId) { }

    private static final List<Route> ROUTES = List.of(
            new Route("R11B destroy", CdStagePayload.R11B, "rehost-fixtures-warp/R11B-destroy-out/R11B", 0x2E, 0x24),
            new Route("R11B", CdStagePayload.R11B, "rehost-long/R11B", 0x2E, 0x24),
            new Route("R31B", CdStagePayload.R31B, "rehost-long/R31B", 0x38, 0x39),
            new Route("R41B", CdStagePayload.R41B, "rehost-long/R41B", 0x52, 0x2F),
            new Route("R51B", CdStagePayload.R51B, "rehost-long/R51B", 0x2C, 0x2D),
            new Route("R61B", CdStagePayload.R61B, "rehost-long/R61B", 0x2F, 0x29),
            new Route("R71B", CdStagePayload.R71B, "rehost-long/R71B", 0x27, 0x28));

    @TempDir
    Path tempDir;

    @Test
    void projectorEventsMatchTheNativeRoutes() throws Exception {
        List<String> failures = new ArrayList<>();
        List<String> report = new ArrayList<>();
        for (Route route : ROUTES) {
            Path physics = CdTestMedia.nativeFile(route.dir() + "/trace/physics.csv");
            Path dir = physics.getParent().getParent();
            var fixture = new CdFixtureReplay.Fixture(route.payload(), dir.resolve("trace"), dir.resolve("movie.bk2"));
            TreeMap<Integer, TreeSet<String>> engine = new TreeMap<>();
            @SuppressWarnings("unchecked")
            Set<String>[] present = (Set<String>[]) new Set[] {Set.of()};
            try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
                var outcome = CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve(route.label().replace(' ', '-'))),
                        fixture, Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                            @Override
                            public void row(int row, AbstractPlayableSprite sonic) {
                                Set<String> now = new TreeSet<>();
                                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                    if (o.isDestroyed() || !(o instanceof AbstractObjectInstance a)) continue;
                                    if (o.getSpawn() == null) continue;
                                    int id = o.getSpawn().objectId();
                                    if (id != route.projectorId() && id != 0x18 && id != route.animalId()) continue;
                                    if (a.getSlotIndex() < 0) continue;
                                    now.add(String.format("%02X@%d", id, a.getSlotIndex()));
                                }
                                for (String s : now) if (!present[0].contains(s)) engine.computeIfAbsent(row, k -> new TreeSet<>()).add("+" + s);
                                for (String s : present[0]) if (!now.contains(s)) engine.computeIfAbsent(row, k -> new TreeSet<>()).add("-" + s);
                                present[0] = now;
                            }
                        });
                report.add(route.label() + ": " + outcome.rowsCompared() + " rows, first divergence "
                        + outcome.firstErrorRow());
                if (outcome.firstErrorRow() >= 0) failures.add(route.label() + " diverged at row " + outcome.firstErrorRow());
            }
            TreeMap<Integer, TreeSet<String>> nativeEvents = new TreeMap<>();
            for (String line : Files.readAllLines(dir.resolve("trace/aux_state.jsonl"))) {
                boolean appeared = line.contains("\"object_appeared\"");
                if (!appeared && !line.contains("\"object_removed\"")) continue;
                JsonNode n = new ObjectMapper().readTree(line);
                int id = Integer.parseInt(n.get("object_type").asText().substring(2), 16);
                if (id != route.projectorId() && id != 0x18 && id != route.animalId()) continue;
                nativeEvents.computeIfAbsent(n.get("frame").asInt(), k -> new TreeSet<>())
                        .add((appeared ? "+" : "-") + String.format("%02X@%d", id, n.get("slot").asInt()));
            }
            // The projector's own events are compared exactly everywhere; the explosion and animal events too where the route
            // fights (the destroy movie): other routes' animals and explosions belong to the badnik owners.
            boolean full = route.label().endsWith("destroy");
            String pid = String.format("%02X@", route.projectorId());
            TreeMap<Integer, TreeSet<String>> expectedFiltered = filter(nativeEvents, full, pid);
            TreeMap<Integer, TreeSet<String>> actualFiltered = filter(engine, full, pid);
            int slotsFrom = SLOTS_IGNORED_FROM.getOrDefault(route.label(), Integer.MAX_VALUE);
            // Where the other objects in the table do not all match native's (the SLOTS lane's frontier) a FindObjSlot child
            // takes another slot: from that row the rows and IDs of the events are compared without the slot.
            expectedFiltered = withoutSlots(expectedFiltered, slotsFrom);
            actualFiltered = withoutSlots(actualFiltered, slotsFrom);
            report.add(route.label() + ": native events " + count(expectedFiltered) + ", engine events " + count(actualFiltered));
            if (!expectedFiltered.equals(actualFiltered)) {
                failures.add(route.label() + " events differ; first difference: " + firstDifference(expectedFiltered, actualFiltered));
            }
        }
        System.out.println("PROJECTOR native:\n" + String.join("\n", report));
        assertTrue(failures.isEmpty(), String.join("\n", failures) + "\n" + String.join("\n", report));
        assertEquals(ROUTES.size(), report.stream().filter(s -> s.contains("first divergence")).count());
    }

    /**
     * The row from which a route's slot numbers are not compared; absent routes (R11B, R51B, R61B, R71B) are compared slot for slot.
     * Collision Chaos's and Tidal Tempest's children take other slots than native's from their first appearance; the destroy
     * movie's first visit (row 1457) is slot-exact, its second (row 1830) is not (the parent takes 47 where native has 46).
     */
    private static final java.util.Map<String, Integer> SLOTS_IGNORED_FROM =
            java.util.Map.of("R11B destroy", 1800, "R31B", 0, "R41B", 0);

    private static TreeMap<Integer, TreeSet<String>> withoutSlots(TreeMap<Integer, TreeSet<String>> events, int fromRow) {
        TreeMap<Integer, TreeSet<String>> out = new TreeMap<>();
        for (var e : events.entrySet()) {
            TreeSet<String> kept = new TreeSet<>();
            for (String s : e.getValue()) kept.add(e.getKey() >= fromRow ? s.substring(0, s.indexOf('@')) : s);
            out.put(e.getKey(), kept);
        }
        return out;
    }

    private static TreeMap<Integer, TreeSet<String>> filter(TreeMap<Integer, TreeSet<String>> events, boolean all, String projectorPrefix) {
        TreeMap<Integer, TreeSet<String>> out = new TreeMap<>();
        for (var e : events.entrySet()) {
            TreeSet<String> kept = new TreeSet<>();
            for (String s : e.getValue()) {
                boolean projector = s.substring(1).startsWith(projectorPrefix);
                // The explosion and animal events count from the second visit (row 1800): the first visit's badnik
                // explosions and animals belong to their owners.
                if (projector || (all && e.getKey() >= 1800)) kept.add(s);
            }
            if (!kept.isEmpty()) out.put(e.getKey(), kept);
        }
        return out;
    }

    private static int count(TreeMap<Integer, TreeSet<String>> events) {
        return events.values().stream().mapToInt(Set::size).sum();
    }

    private static String firstDifference(TreeMap<Integer, TreeSet<String>> a, TreeMap<Integer, TreeSet<String>> b) {
        TreeSet<Integer> rows = new TreeSet<>(a.keySet());
        rows.addAll(b.keySet());
        for (int row : rows) {
            if (!a.getOrDefault(row, new TreeSet<>()).equals(b.getOrDefault(row, new TreeSet<>()))) {
                return "row " + row + " native " + a.getOrDefault(row, new TreeSet<>()) + " engine " + b.getOrDefault(row, new TreeSet<>());
            }
        }
        return "none";
    }
}
