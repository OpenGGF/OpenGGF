package com.openggf.level;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1ZoneRegistry;
import com.openggf.game.sonic2.Sonic2ZoneRegistry;
import com.openggf.game.sonic3k.Sonic3kZoneRegistry;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audits unchanged-world terrain cache reuse with real rendering across stock acts.
 * Inputs: verified ROM properties and openggf.cacheAudit.output. Writes a JSON matrix.
 * Origin: cross-level follow-up to LRZ1/SOZ2 rewind performance, 2026-11-03.
 * Counts work rather than timing it. It does not certify complete routes or emulator parity.
 */
@Tag("performance-measurement")
class TestRenderedRewindCacheMatrix {
    record Entry(String game, String name, int zone, int act) { }
    record Plane(int bytes, Boolean wrapped, long descriptorRevision, boolean retained) {
        boolean reusable() { return bytes > 0 && Boolean.FALSE.equals(wrapped) && descriptorRevision == 0 && !retained; }
    }
    record Result(Entry entry, Plane foreground, Plane background, int foregroundRebuilds,
                  int backgroundRebuilds, int pictureMatches, String failure) { }

    public static void main(String[] args) throws Exception {
        new TestRenderedRewindCacheMatrix().unchangedWorldsDoNotRebuildFullLayoutTerrain();
    }

    @Test
    void unchangedWorldsDoNotRebuildFullLayoutTerrain() throws Exception {
        Path output = Path.of(System.getProperty("openggf.cacheAudit.output", "target/rewind-cache-matrix.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        List<Result> results = new ArrayList<>();
        for (Entry entry : entries()) {
            String selector=System.getProperty("openggf.cacheAudit.entry");
            if(selector!=null&&!selector.equals(entry.game()+":"+entry.zone()+":"+entry.act()))continue;
            Result result;
            try { result = check(entry); }
            catch (Exception | AssertionError failure) {
                result = new Result(entry, null, null, 0, 0, 0,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            results.add(result);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), results);
            System.out.println("CACHE_AUDIT " + entry + " " + result);
        }
        List<Result> failures = results.stream().filter(r -> r.failure() != null).toList();
        assertTrue(failures.isEmpty(), () -> "Unqualified or redundant restore work: " + failures);
    }

    private Result check(Entry entry) throws Exception {
        Path rom = switch (entry.game()) {
            case "s1" -> RomTestUtils.ensureSonic1RomAvailable().toPath();
            case "s2" -> RomTestUtils.ensureSonic2RomAvailable().toPath();
            default -> RomTestUtils.ensureSonic3kRomAvailable().toPath();
        };
        var settings = new GameplayCaptureSession.Settings(320, "sonic",
                entry.game().equals("s1") ? "" : "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, entry.zone(), entry.act(), settings);
            for (int frame = 0; frame < 6; frame++) session.step(null);
            int[] expected = session.render().pixels();
            var tilemaps = GameServices.level().getTilemapManager();
            Plane fg = plane(tilemaps, true), bg = plane(tilemaps, false);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var snapshot = registry.capture();
            int fgRebuilds = 0, bgBefore = tilemaps.bgFullRebuildCount, matches = 0;
            for (int restore = 0; restore < 5; restore++) {
                byte[] foreground = tilemaps.getForegroundTilemapData();
                registry.restore(snapshot);
                int[] restored = session.render().pixels();
                if (foreground != tilemaps.getForegroundTilemapData()) fgRebuilds++;
                if (Arrays.equals(expected, restored)) matches++;
            }
            int bgRebuilds = tilemaps.bgFullRebuildCount - bgBefore;
            String failure = null;
            if (fg.reusable() && fgRebuilds != 0) failure = "unchanged full-layout foreground rebuilt";
            if (bg.reusable() && bgRebuilds != 0) failure = "unchanged full-layout background rebuilt";
            if (fg.bytes() == 0 && bg.bytes() == 0) failure = "no terrain image exercised";
            return new Result(entry, fg, bg, fgRebuilds, bgRebuilds, matches, failure);
        } finally {
            Engine.clearGlobalInstance();
        }
    }


    private static Plane plane(LevelTilemapManager manager, boolean foreground) throws Exception {
        byte[] bytes = foreground ? manager.getForegroundTilemapData() : manager.getBackgroundTilemapData();
        Object[] retained = (Object[]) field(manager, "retainedPlanes");
        boolean owns = retained[foreground ? 0 : 1] != null;
        if (!foreground) owns |= (boolean) field(manager, "retainedBackgroundPlaneAuthoritative");
        return new Plane(bytes == null ? 0 : bytes.length,
                (Boolean) field(manager, foreground ? "lastForegroundWrap" : "lastBackgroundWrap"),
                (long) field(manager, foreground ? "foregroundDescriptorRevision" : "backgroundDescriptorRevision"), owns);
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        add(entries, "s1", new Sonic1ZoneRegistry(), 7);
        add(entries, "s2", new Sonic2ZoneRegistry(), 11);
        add(entries, "s3k", new Sonic3kZoneRegistry(), 12);
        var s3k = new Sonic3kZoneRegistry();
        entries.add(new Entry("s3k", "DOOMSDAY", 12, 0));
        entries.add(new Entry("s3k", "ENDING", 13, 1));
        for (int zone : new int[]{22, 23})
            for (int act = 0; act < (zone == 22 ? 2 : 1); act++)
                entries.add(new Entry("s3k", s3k.getZoneName(zone), zone, act));
        return entries;
    }

    private static void add(List<Entry> out, String game, ZoneRegistry registry, int zones) {
        for (int zone = 0; zone < zones; zone++)
            for (int act = 0; act < registry.getActCount(zone); act++)
                out.add(new Entry(game, registry.getZoneName(zone), zone, act));
    }
}
