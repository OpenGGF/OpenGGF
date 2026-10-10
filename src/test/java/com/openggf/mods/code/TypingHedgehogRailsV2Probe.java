package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import static com.openggf.mods.code.ForwardRails.*;
import com.openggf.mods.code.ForwardRails.Driver;
import com.openggf.mods.code.ForwardRails.Geometry;
import com.openggf.mods.code.ForwardRails.Handoff;
import com.openggf.mods.code.ForwardRails.Pilot;
import com.openggf.mods.code.ForwardRails.Row;
import com.openggf.mods.code.ForwardRails.World;

/**
 * The rails v2 cheap probes (GPT-6.1-Sol's rails design, section 8: probes 1, 2 and 4 to 7) on one
 * pilot act per game: S1 GHZ3, S2 EHZ2 and S3K SOZ1. Each answers a question the forward course
 * depends on before any rail is authored against it:
 *
 * <ol>
 *   <li><b>Entry contract</b>: 500 cold controlled rows (300 neutral, then RIGHT) under the
 *       reference launch profile, against each host preference that could differ
 *       (load-time simulation, S3K intros, cross-game features), and against the production boot
 *       path (HeadlessGameBoot, the hub's {@code startLevelFromTitleScreenImmediate}, GameLoop
 *       steps; part {@code 1p}). Reports the readiness rows and the first row and field where the
 *       native state differs.</li>
 *   <li><b>Native countdown</b>: from the first grounded, ready row, 180 rows of DOWN with a
 *       DOWN+JUMP pulse every sixth row, then RIGHT; the spindash flag, charge, speed and facing on
 *       each pulse and around the release.</li>
 *   <li><b>Conversion</b> (4): the mod's real {@code CombatHandoff} at loaded pools along the act's
 *       existing rail: slot, reservation, child and render state through four held rows, then 120
 *       rows of the rail.</li>
 *   <li><b>Respawn</b> (5): a converted body's placement after the camera leaves and returns.</li>
 *   <li><b>Restore fidelity</b> (6), the option (c) victory path: a 600-row live fight with Sonic
 *       held and homing-style native writes, twenty search-style restores, then restore + reconvert
 *       and the rail's next 300 rows, compared with a fresh zero-length fight row by row (cheap
 *       state every row, full course snapshots every 30 rows).</li>
 *   <li><b>Plateau queries</b> (7): floor/ceiling geometry samples at candidate stops against what
 *       Sonic actually does from there (coast, brake, walk the strip), with a gap and a slope as
 *       negative controls.</li>
 * </ol>
 *
 * Opt-in: {@code -Dtypinghog.v2probe=all} (or a list of {@code ghz3,ehz2,soz1}) with optional
 * {@code -Dtypinghog.v2probe.parts=1,1p,2,4,5,6,7}. Prints {@code V2|} lines. Origin: rails lane,
 * rails v2, 2026-10-10.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TypingHedgehogRailsV2Probe {
    private static final String ACTS = System.getProperty("typinghog.v2probe");
    private static final Set<String> PARTS = Set.of(System.getProperty("typinghog.v2probe.parts", "1,1p,2,4,5,6,7")
            .split(","));
    static final int ENTRY_ROWS = 500, ENTRY_NEUTRAL = 300;

    static final List<Pilot> PILOTS = List.of(
            new Pilot("ghz3", SonicGame.SONIC_1, "s1", 0, 2, "", "s1-ghz3.rail"),
            new Pilot("ehz2", SonicGame.SONIC_2, "s2", 0, 1, "tails", "s2-ehz2.rail"),
            new Pilot("soz1", SonicGame.SONIC_3K, "s3k", 8, 0, "tails", "s3k-soz1.rail"));

    @TempDir static Path temp;
    static ExampleModHarness harness;
    /** Part 1's reference entry trace per pilot, for part 1p. */
    static final Map<String, List<Row>> REFERENCE_ENTRY = new LinkedHashMap<>();

    @AfterAll static void closeHarness() throws Exception {
        if (harness != null) harness.close();
        harness = null;
    }

    static boolean wanted(Pilot p) {
        return ACTS != null && (ACTS.equals("all") || Arrays.asList(ACTS.split(",")).contains(p.name()));
    }

    static void out(String part, Pilot p, String text) {
        System.out.println("V2|" + part + "|" + p.name() + "|" + text);
    }

    static int[] railMasks(Pilot p) throws IOException {
        String text = Files.readString(SOURCE.resolve("src/main/resources/rails").resolve(p.rail()));
        List<Integer> masks = new ArrayList<>();
        int validated = -1;
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.startsWith("validated ")) validated = Integer.parseInt(line.substring(10).strip());
            if (!line.startsWith("program ")) continue;
            for (String pair : line.substring(8).split(",")) {
                String[] f = pair.strip().split(":");
                int frames = Integer.parseInt(f[0]), mask = Integer.parseInt(f[1], 16);
                for (int i = 0; i < frames; i++) masks.add(mask);
            }
        }
        int length = validated < 0 ? masks.size() : Math.min(validated, masks.size());
        return masks.subList(0, length).stream().mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ tests

    @Test @Order(1) @RequiresRom(SonicGame.SONIC_1) void s1() throws Exception { pilot(PILOTS.get(0)); }

    @Test @Order(2) @RequiresRom(SonicGame.SONIC_2) void s2() throws Exception { pilot(PILOTS.get(1)); }

    @Test @Order(3) @RequiresRom(SonicGame.SONIC_3K) void s3k() throws Exception { pilot(PILOTS.get(2)); }

    private void pilot(Pilot p) throws Exception {
        Assumptions.assumeTrue(wanted(p), "opt-in: -Dtypinghog.v2probe=all");
        if (harness == null) harness = buildHarness(temp, "v2probe", Map.of());
        Handoff handoff = new Handoff(harness, p.code());
        List<Integer> boundaries = new ArrayList<>();
        for (String part : List.of("1", "2", "4", "5", "6", "7")) {
            if (!PARTS.contains(part)) continue;
            long started = System.nanoTime();
            try {
                switch (part) {
                    case "1" -> entry(p);
                    case "2" -> countdown(p);
                    case "4" -> boundaries.addAll(conversion(p, handoff));
                    case "5" -> respawn(p, handoff, boundaries);
                    case "6" -> restoreFidelity(p, handoff, boundaries);
                    case "7" -> plateaus(p);
                    default -> { }
                }
            } catch (Throwable t) {
                Throwable cause = t;
                while (cause.getCause() != null) cause = cause.getCause();
                out(part, p, "ABORTED " + cause);
                cause.printStackTrace(System.out);
            }
            out(part, p, String.format("took %.1f s", (System.nanoTime() - started) / 1e9));
        }
    }

    // ---- 1: entry contract ------------------------------------------------------------------

    static IntUnaryOperator entryProgram() {
        return row -> row < ENTRY_NEUTRAL ? 0 : RIGHT;
    }

    static List<Row> entryTrace(World w) {
        w.driver.program = entryProgram();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 20_000 && rows.size() < ENTRY_ROWS; i++) {
            int before = w.driver.row;
            w.step();
            if (w.driver.row != before) rows.add(Row.observe(w.course));
        }
        return rows;
    }

    static String markers(List<Row> rows) {
        int ready = -1, controllable = -1, grounded = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (ready < 0 && rows.get(i).ready()) ready = i;
            if (controllable < 0 && rows.get(i).controllable()) controllable = i;
            if (grounded < 0 && !rows.get(i).p1air()) grounded = i;
        }
        Row first = rows.getFirst();
        return "rows " + rows.size() + ", presentation ready from row " + ready + ", controllable from " + controllable
                + ", grounded from " + grounded + ", row 0 at (" + first.p1x() + "," + first.p1y() + ") camera ("
                + first.camX() + "," + first.camY() + ") frame " + first.frame() + " vbla " + first.vbla()
                + ", row " + (ENTRY_ROWS - 1) + " x " + rows.getLast().p1x();
    }

    static String compare(List<Row> reference, List<Row> other) { return compare(reference, other, true); }

    static String compare(List<Row> reference, List<Row> other, boolean withClock) {
        int n = Math.min(reference.size(), other.size());
        for (int i = 0; i < n; i++) {
            List<String> d = other.get(i).differences(reference.get(i), withClock);
            if (!d.isEmpty()) return "first differs at row " + i + ": " + d;
        }
        return reference.size() == other.size() ? "identical over " + n + " rows" : "row counts differ";
    }

    private void entry(Pilot p) throws Exception {
        List<Row> reference;
        try (World w = new World(p, Map.of())) {
            reference = entryTrace(w);
        }
        REFERENCE_ENTRY.put(p.name(), reference);
        out("1", p, "reference profile (FAST load, S3K intros skipped, cross-game off, " + WIDTH + " px): " + markers(reference));
        Map<String, Map<SonicConfiguration, Object>> variations = new LinkedHashMap<>();
        for (String mode : List.of("NONE", "PROFILED", "REALISTIC")) {
            variations.put("load " + mode, Map.of(SonicConfiguration.LOAD_TIME_SIMULATION, mode));
        }
        if (p.game() == SonicGame.SONIC_3K) variations.put("intros played", Map.of(SonicConfiguration.S3K_SKIP_INTROS, false));
        variations.put("cross-game on", Map.of(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true));
        variations.put("live rewind on", Map.of(SonicConfiguration.LIVE_REWIND_ENABLED, true));
        for (var v : variations.entrySet()) {
            try (World w = new World(p, v.getValue())) {
                List<Row> rows = entryTrace(w);
                out("1", p, v.getKey() + ": " + compare(reference, rows) + "; " + markers(rows));
            } catch (Throwable t) {
                out("1", p, v.getKey() + ": aborted " + t);
            }
        }
        // A second cold run under the reference profile: the fixture path must reproduce itself.
        try (World w = new World(p, Map.of())) {
            out("1", p, "reference repeat: " + compare(reference, entryTrace(w)));
        }
        // Does a held row (beforeTick false) advance V_int_run_count?
        try (World w = new World(p, Map.of())) {
            int before = GameServices.level().getObjectManager().getVblaCounter();
            w.driver.freeze = true;
            for (int i = 0; i < 37; i++) w.step();
            w.driver.freeze = false;
            int after = GameServices.level().getObjectManager().getVblaCounter();
            out("1", p, "37 held rows (beforeTick false): V_int_run_count " + before + " -> " + after);
        }
        // How sensitive is the act's existing rail to the act-start phase of V_int_run_count?
        int[] masks = railMasks(p);
        int rows = Math.min(masks.length, 4000);
        List<Row> base = railTrace(p, masks, rows, 0);
        for (int offset : new int[] {1, 2, 3, 4, 8, 16, 64, 128, 1000}) {
            out("1", p, "rail under V_int_run_count start +" + offset + " (" + rows + " rows): "
                    + compare(base, railTrace(p, masks, rows, offset), false));
        }
        if (p.game() == SonicGame.SONIC_3K) {
            try (World w = new World(p, Map.of())) {
                StringBuilder dump = new StringBuilder();
                w.driver.program = row -> 0;
                for (int i = 0; i < 400; i++) {
                    w.advance();
                    if (i % 25 == 0) {
                        var s = sonic();
                        dump.append(String.format(" [%d (%d,%d) air=%s ctl=%s lock=%s roll=%s ang=%d obj=%s]", i,
                                s.getCentreX(), s.getCentreY(), s.getAir(), s.isObjectControlled(), s.isControlLocked(),
                                s.getRolling(), s.getAngle() & 0xFF, s.isOnObject()));
                    }
                }
                out("1", p, "neutral entry, every 25 rows:" + dump);
            }
        }
    }

    static List<Row> railTrace(Pilot p, int[] masks, int rows, int vbla) throws IOException {
        List<Row> out = new ArrayList<>();
        try (World w = new World(p, Map.of(), vbla)) {
            w.driver.program = row -> row < masks.length ? masks[row] : 0;
            while (w.driver.row < rows) {
                w.advance();
                out.add(Row.observe(w.course));
                if (sonic().getDead()) break;
            }
        }
        return out;
    }

    // ---- 2: native countdown ----------------------------------------------------------------

    private void countdown(Pilot p) throws Exception {
        try (World w = new World(p, Map.of())) {
            // Neutral until presentation is ready and Sonic stands still on the ground, facing right.
            int readyRow = -1;
            for (int i = 0; i < 3000 && readyRow < 0; i++) {
                w.advance();
                var s = sonic();
                if (w.course.presentationReady() && !s.getAir() && !s.isObjectControlled() && !s.isControlLocked()
                        && s.getGSpeed() == 0 && s.getXSpeed() == 0) readyRow = w.driver.row;
            }
            out("2", p, "first ready grounded still row " + readyRow + " at (" + sonic().getCentreX() + ","
                    + sonic().getCentreY() + ") facing " + sonic().getDirection());
            if (readyRow < 0) {
                w.close();
                countdownViaRail(p);
                return;
            }
            for (boolean pulses : p.game() == SonicGame.SONIC_1 ? List.of(false, true) : List.of(true)) {
                var mark = w.course.capture();
                int start = w.driver.row;
                int previous = w.driver.previousMask;
                w.driver.program = row -> row < start ? 0 : countdownMask(row - start, pulses);
                int x0 = sonic().getCentreX();
                int firstDash = -1, maxCounter = 0;
                StringBuilder pulsesSeen = new StringBuilder();
                StringBuilder release = new StringBuilder();
                for (int i = 0; i < COUNTDOWN_ROWS + 60; i++) {
                    w.advance();
                    var s = sonic();
                    if (s.getSpindash() && firstDash < 0) firstDash = i;
                    maxCounter = Math.max(maxCounter, s.getSpindashCounter());
                    if (pulses && i >= 2 && i < COUNTDOWN_ROWS && (i - 2) % 6 == 0 && (i < 20 || i > 160)) {
                        pulsesSeen.append(String.format(" [%d dash=%s ctr=%d crouch=%s]", i, s.getSpindash(),
                                s.getSpindashCounter(), s.getCrouching()));
                    }
                    if (i >= COUNTDOWN_ROWS - 4 && i <= COUNTDOWN_ROWS + 5) {
                        release.append(String.format(" [%d dash=%s gs=%d xs=%d roll=%s dir=%s x=%d]", i, s.getSpindash(),
                                s.getGSpeed(), s.getXSpeed(), s.getRolling(), s.getDirection(), s.getCentreX()));
                    }
                }
                out("2", p, (pulses ? "pulsed" : "DOWN-only") + " countdown from row " + start + ": first spindash row "
                        + firstDash + ", max charge " + maxCounter + ", 60 rows after release x +"
                        + (sonic().getCentreX() - x0) + ", hurt " + sonic().isHurt());
                out("2", p, "  pulses" + pulsesSeen);
                out("2", p, "  release" + release);
                w.course.restore(mark);
                w.driver.row = start;
                w.driver.previousMask = previous;
            }
        }
    }

    /** The ready point found by the act's own rail, braked to a stop, when neutral input never readies. */
    private void countdownViaRail(Pilot p) throws Exception {
        int[] masks = railMasks(p);
        try (World w = new World(p, Map.of())) {
            w.driver.program = row -> row < masks.length ? masks[row] : 0;
            while (w.driver.row < Math.min(masks.length, 3000)) {
                w.advance();
                var s = sonic();
                if (w.course.presentationReady() && !s.getAir() && !s.isObjectControlled() && !s.isControlLocked()
                        && !s.isOnObject() && Math.abs(signedAngle(s.getAngle())) <= 4) break;
            }
            int found = w.driver.row;
            int from = w.driver.row;
            w.driver.program = row -> sonic().getXSpeed() > 0 ? LEFT : sonic().getXSpeed() < 0 ? RIGHT : 0;
            for (int i = 0; i < 120 && (sonic().getGSpeed() != 0 || sonic().getXSpeed() != 0); i++) w.advance();
            out("2", p, "rail ready point row " + found + ", braked still by row " + w.driver.row + " at ("
                    + sonic().getCentreX() + "," + sonic().getCentreY() + ") facing " + sonic().getDirection()
                    + " still " + (sonic().getGSpeed() == 0));
            int start = w.driver.row;
            w.driver.program = row -> countdownMask(row - start, true);
            int x0 = sonic().getCentreX(), firstDash = -1, maxCounter = 0;
            StringBuilder release = new StringBuilder();
            for (int i = 0; i < COUNTDOWN_ROWS + 60; i++) {
                w.advance();
                var s = sonic();
                if (s.getSpindash() && firstDash < 0) firstDash = i;
                maxCounter = Math.max(maxCounter, s.getSpindashCounter());
                if (i >= COUNTDOWN_ROWS - 2 && i <= COUNTDOWN_ROWS + 3) {
                    release.append(String.format(" [%d dash=%s gs=%d dir=%s x=%d]", i, s.getSpindash(), s.getGSpeed(),
                            s.getDirection(), s.getCentreX()));
                }
            }
            out("2", p, "pulsed countdown from row " + start + ": first spindash row " + firstDash + ", max charge "
                    + maxCounter + ", 60 rows after release x +" + (sonic().getCentreX() - x0) + "; release" + release
                    + (from == start ? "" : ""));
        }
    }

    // ---- 4: conversion -------------------------------------------------------------------------

    /** Live objects (class@slot) that look like members of a pooled body's family. */
    static List<String> family(Set<String> classes) {
        List<String> out = new ArrayList<>();
        for (ObjectInstance o : List.copyOf(GameServices.level().getObjectManager().getActiveObjects())) {
            String name = o.getClass().getName();
            String simple = o.getClass().getSimpleName();
            for (String c : classes) {
                String stem = c.replace("BadnikInstance", "");
                boolean member = name.contains(c + "$") || (simple.startsWith(stem) && !simple.equals(c));
                if (member && !o.isDestroyed()) {
                    int slot = o instanceof AbstractObjectInstance a ? a.getSlotIndex() : -9;
                    out.add(simple + "@" + slot);
                }
            }
        }
        out.sort(null);
        return out;
    }

    /** Scans the act's existing rail for loaded pools; picks up to three boundaries to test. */
    private List<Integer> conversion(Pilot p, Handoff handoff) throws Exception {
        out("4", p, "eligibility: Tunnelbot " + handoff.eligible.test("TunnelbotBadnikInstance")
                + ", a projectile class " + handoff.eligible.test("BuzzBomberMissileInstance"));
        int[] masks = railMasks(p);
        record Seen(int row, List<?> pool, boolean grounded) { }
        List<Seen> seen = new ArrayList<>();
        try (World w = new World(p, Map.of())) {
            w.driver.program = row -> row < masks.length ? masks[row] : 0;
            while (w.driver.row < Math.min(masks.length, 7000)) {
                if (w.driver.row % 10 == 0 && w.course.presentationReady()) {
                    List<?> pool = handoff.pool();
                    if (!pool.isEmpty()) seen.add(new Seen(w.driver.row, pool, !sonic().getAir()));
                }
                w.advance();
                if (sonic().getDead() || GameServices.level().getCurrentAct() != p.act()) break;
            }
        }
        out("4", p, "rows with a visible eligible pool (every 10th row scanned): " + seen.size());
        List<Integer> chosen = new ArrayList<>();
        Set<String> classesChosen = new java.util.HashSet<>();
        for (Seen s : seen) {
            if (!s.grounded()) continue;
            Set<String> classes = s.pool().stream().map(b -> (String) component(b, "className")).collect(Collectors.toSet());
            boolean multipart = classes.stream().anyMatch(c -> c.matches("(Skorp|Sandworm|Sonic1Caterkiller|SnaleBlaster|Buzzer|Sonic1BuzzBomber|Rhinobot)BadnikInstance"));
            boolean novel = !classesChosen.containsAll(classes);
            if (chosen.isEmpty() || (novel && (multipart || chosen.size() < 2))) {
                if (!chosen.isEmpty() && s.row() - chosen.getLast() < 200) continue;
                chosen.add(s.row());
                classesChosen.addAll(classes);
                out("4", p, "boundary row " + s.row() + ": pool " + describe(s.pool()));
            }
            if (chosen.size() >= 3) break;
        }
        for (int boundary : chosen) convertAt(p, handoff, masks, boundary);
        return chosen;
    }

    private void convertAt(Pilot p, Handoff handoff, int[] masks, int boundary) throws Exception {
        try (World w = new World(p, Map.of())) {
            w.driver.program = row -> row < masks.length ? masks[row] : 0;
            while (w.driver.row < boundary) w.advance();
            var om = GameServices.level().getObjectManager();
            List<?> pool = handoff.pool();
            Set<String> classes = pool.stream().map(b -> (String) component(b, "className")).collect(Collectors.toSet());
            Map<Integer, Integer> before = new TreeMap<>(om.occupiedDynamicSlotIdsWithReservations());
            List<String> familyBefore = family(classes);
            List<AbstractObjectInstance> bodies = new ArrayList<>();
            for (Object b : pool) {
                int slot = (int) component(b, "slot");
                for (ObjectInstance o : om.getActiveObjects()) {
                    if (o instanceof AbstractObjectInstance a && a.getSlotIndex() == slot) bodies.add(a);
                }
            }
            out("4", p, "@" + boundary + " pool " + describe(pool) + "; family before " + familyBefore
                    + "; first free slot " + om.firstFreeDynamicSlot());
            w.driver.before = () -> handoff.convert(pool);
            w.driver.hold = true;
            for (int i = 1; i <= 4; i++) {
                w.advance();
                Map<Integer, Integer> now = new TreeMap<>(om.occupiedDynamicSlotIdsWithReservations());
                List<String> pooled = new ArrayList<>();
                for (Object b : pool) {
                    int slot = (int) component(b, "slot");
                    pooled.add(slot + "=" + (now.containsKey(slot) ? Integer.toHexString(now.get(slot)) : "free"));
                }
                int renders = 0;
                for (AbstractObjectInstance body : bodies) {
                    var commands = new ArrayList<com.openggf.graphics.GLCommand>();
                    try {
                        body.appendRenderCommands(commands);
                    } catch (RuntimeException ignored) {
                        // a retired body may have released its renderer
                    }
                    renders += commands.size();
                }
                Set<Integer> freed = new java.util.TreeSet<>(before.keySet());
                freed.removeAll(now.keySet());
                Set<Integer> taken = new java.util.TreeSet<>(now.keySet());
                taken.removeAll(before.keySet());
                out("4", p, "  held row " + i + ": pooled slots " + pooled + ", slots freed " + freed + " taken " + taken
                        + ", family " + family(classes) + ", retired bodies still emit " + renders
                        + " render commands, sonic hurt " + sonic().isHurt());
            }
            w.driver.hold = false;
            Set<Integer> layouts = new java.util.HashSet<>();
            for (Object b : pool) layouts.add((int) component(b, "layoutIndex"));
            int hurtRow = -1, back = -1;
            for (int i = 0; i < 120; i++) {
                w.advance();
                if (hurtRow < 0 && (sonic().isHurt() || sonic().getDead())) hurtRow = i;
                for (ObjectInstance o : om.getActiveObjects()) {
                    if (back < 0 && o instanceof AbstractObjectInstance a && !a.isDestroyed() && a.getSpawn() != null
                            && layouts.contains(a.getSpawn().layoutIndex()) && classes.contains(a.getClass().getSimpleName())) {
                        back = i;
                    }
                }
            }
            out("4", p, "  120 rail rows after: first hurt row " + hurtRow + ", a converted placement live again on row "
                    + back + ", eligible pool now " + describe(handoff.pool()));
        }
    }

    // ---- 5: respawn ---------------------------------------------------------------------------

    private void respawn(Pilot p, Handoff handoff, List<Integer> boundaries) throws Exception {
        if (boundaries.isEmpty()) {
            out("5", p, "no boundary from part 4");
            return;
        }
        int[] masks = railMasks(p);
        int boundary = boundaries.getFirst();
        try (World w = new World(p, Map.of())) {
            w.driver.program = row -> row < masks.length ? masks[row] : 0;
            while (w.driver.row < boundary) w.advance();
            var om = GameServices.level().getObjectManager();
            List<?> pool = handoff.pool();
            Map<Integer, Boolean> tracked = new TreeMap<>();
            for (ObjectInstance o : om.getActiveObjects()) {
                for (Object b : pool) {
                    if (o instanceof AbstractObjectInstance a && a.getSlotIndex() == (int) component(b, "slot")) {
                        tracked.put(a.getSpawn().layoutIndex(), a.getSpawn().respawnTracked());
                    }
                }
            }
            out("5", p, "@" + boundary + " converting " + describe(pool) + " (layout -> respawnTracked " + tracked + ")");
            w.driver.before = () -> handoff.convert(pool);
            w.driver.hold = true;
            for (int i = 0; i < 4; i++) w.advance();
            int homeX = sonic().getCentreX(), homeY = sonic().getCentreY();
            boolean vertical = p.game() == SonicGame.SONIC_3K;
            int awayX = vertical ? homeX : homeX + 0x600, awayY = vertical ? Math.max(0x40, homeY - 0x300) : homeY;
            String away = excursion(w, awayX, awayY, tracked.keySet());
            String home = excursion(w, homeX, homeY, tracked.keySet());
            out("5", p, (vertical ? "Y-window" : "X") + " excursion: away " + away + "; back home " + home);
        }
    }

    /** Pins Sonic at (x, y) for 160 held rows; reports converted placements live there. */
    private static String excursion(World w, int x, int y, Set<Integer> layouts) {
        int seen = 0;
        for (int i = 0; i < 160; i++) {
            NativePositionOps.writeXPosPreserveSubpixel(sonic(), x);
            NativePositionOps.writeYPosPreserveSubpixel(sonic(), y);
            w.advance();
        }
        List<String> live = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof AbstractObjectInstance a && a.getSpawn() != null && layouts.contains(a.getSpawn().layoutIndex())
                    && o.getClass().getSimpleName().endsWith("BadnikInstance")) {
                live.add(a.getClass().getSimpleName() + "/L" + a.getSpawn().layoutIndex() + (a.isDestroyed() ? " destroyed" : " live"));
                seen++;
            }
        }
        return "camera (" + GameServices.camera().getX() + "," + GameServices.camera().getY() + "), converted placements present "
                + seen + " " + live;
    }

    // ---- 6: restore fidelity (option c) -------------------------------------------------------

    static final int FIGHT_ROWS = 600, SUFFIX_ROWS = 300, SEARCH_TRIALS = 20, TRIAL_ROWS = 50, SNAPSHOT_EVERY = 30;

    private void restoreFidelity(Pilot p, Handoff handoff, List<Integer> boundaries) throws Exception {
        if (boundaries.isEmpty()) {
            out("6", p, "no boundary from part 4");
            return;
        }
        int[] masks = railMasks(p);
        int boundary = boundaries.getFirst();
        IntUnaryOperator rail = row -> row < masks.length ? masks[row] : 0;
        // Reference: the builder's zero-length fight. Convert at the boundary, then the rail's next rows.
        List<Row> reference = new ArrayList<>();
        List<CompositeSnapshot> referenceSnaps = new ArrayList<>();
        try (World w = new World(p, Map.of())) {
            w.driver.program = rail;
            while (w.driver.row < boundary) w.advance();
            List<?> pool = handoff.pool();
            w.driver.before = () -> handoff.convert(pool);
            suffix(w, reference, referenceSnaps);
            out("6", p, "reference: converted " + describe(pool) + " at row " + boundary + ", ran " + reference.size() + " rows");
        }
        // The course's live fight: capture S, convert, the world lives with Sonic held (and homing
        // writes) for FIGHT_ROWS, search-style restores, then the victory restore + reconversion.
        List<Row> fight = new ArrayList<>();
        List<CompositeSnapshot> fightSnaps = new ArrayList<>();
        try (World w = new World(p, Map.of())) {
            w.driver.program = rail;
            while (w.driver.row < boundary) w.advance();
            CourseCheckpoint[] s = new CourseCheckpoint[1];
            boolean[] frozen = new boolean[1];
            List<?> pool = handoff.pool();
            int previousMask = w.driver.previousMask;
            w.driver.before = () -> {
                s[0] = w.course.capture();
                frozen[0] = GameServices.camera().getFrozen();
                handoff.convert(pool);
            };
            w.driver.hold = true;
            int hurt = -1;
            for (int i = 0; i < FIGHT_ROWS; i++) {
                if (i % 40 >= 10 && i % 40 < 30) {
                    // Homing-style native writes while the course holds Sonic.
                    var sonic = sonic();
                    NativePositionOps.writeXPosPreserveSubpixel(sonic, sonic.getCentreX() + ((i % 40) < 20 ? 3 : -3));
                    sonic.setRolling(true);
                    sonic.setAir(true);
                    GameServices.camera().setFrozen(true);
                }
                w.advance();
                if (hurt < 0 && (sonic().isHurt() || sonic().getDead())) hurt = i;
            }
            out("6", p, "live fight " + FIGHT_ROWS + " rows: first native hurt row " + hurt);
            SplittableRandom random = new SplittableRandom(7);
            String trialFault = null;
            for (int t = 0; t < SEARCH_TRIALS && trialFault == null; t++) {
                int[] noise = random.ints(TRIAL_ROWS, 0, 0x20).toArray();
                try {
                    w.driver.before = () -> {
                        w.course.restore(s[0]);
                        GameServices.camera().setFrozen(frozen[0]);
                        handoff.convert(pool);
                    };
                    w.driver.hold = false;
                    w.driver.row = boundary;
                    w.driver.previousMask = previousMask;
                    w.driver.program = row -> row - boundary < noise.length ? noise[row - boundary] & 0x1F : 0;
                    for (int i = 0; i < TRIAL_ROWS; i++) w.advance();
                } catch (Throwable e) {
                    trialFault = "trial " + t + ": " + e;
                }
            }
            out("6", p, SEARCH_TRIALS + " search restores of " + TRIAL_ROWS + " random rows: "
                    + (trialFault == null ? "no fault" : trialFault));
            w.driver.before = () -> {
                w.course.restore(s[0]);
                GameServices.camera().setFrozen(frozen[0]);
                handoff.convert(pool);
            };
            w.driver.hold = false;
            w.driver.row = boundary;
            w.driver.previousMask = previousMask;
            w.driver.program = rail;
            suffix(w, fight, fightSnaps);
        }
        out("6", p, "victory path vs zero-length fight: " + compare(reference, fight));
        int snapDiffs = 0;
        for (int i = 0; i < Math.min(referenceSnaps.size(), fightSnaps.size()); i++) {
            String d = snapshotDiff(referenceSnaps.get(i), fightSnaps.get(i));
            if (!d.equals("identical")) {
                if (snapDiffs++ < 3) out("6", p, "  snapshot after row +" + (i * SNAPSHOT_EVERY) + ": " + d);
            }
        }
        out("6", p, "  full course snapshots compared: " + Math.min(referenceSnaps.size(), fightSnaps.size())
                + ", differing " + snapDiffs);
    }

    private static void suffix(World w, List<Row> rows, List<CompositeSnapshot> snaps) {
        for (int i = 0; i < SUFFIX_ROWS; i++) {
            w.advance();
            rows.add(Row.observe(w.course));
            if (i % SNAPSHOT_EVERY == 0) snaps.add(w.snapshot());
        }
    }

    // ---- 7: plateau queries ------------------------------------------------------------------

    record Outcome(boolean groundedThroughout, int angleSpread, int finalGs, int dx, boolean hurt, boolean stillAtEnd) { }

    private static Outcome fork(World w, CourseCheckpoint mark, int row, int previous, IntUnaryOperator program, int rows,
                                 java.util.function.IntPredicate stop) {
        w.course.restore(mark);
        w.driver.row = row;
        w.driver.previousMask = previous;
        w.driver.program = program;
        int x0 = sonic().getCentreX();
        boolean grounded = true, hurt = false;
        int minA = 999, maxA = -999, still = 0;
        for (int i = 0; i < rows; i++) {
            w.advance();
            var s = sonic();
            grounded &= !s.getAir();
            hurt |= s.isHurt() || s.getDead();
            int a = signedAngle(s.getAngle());
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            still = s.getGSpeed() == 0 && s.getXSpeed() == 0 ? still + 1 : 0;
            if (stop.test(s.getCentreX() - x0)) break;
        }
        return new Outcome(grounded, maxA - minA, sonic().getGSpeed(), sonic().getCentreX() - x0, hurt, still >= 12);
    }

    private void plateaus(Pilot p) throws Exception {
        int[] masks = railMasks(p);
        IntUnaryOperator rail = row -> row < masks.length ? masks[row] : 0;
        record Site(String kind, int row, CourseCheckpoint mark, int previous, Geometry geometry, int angle, int gs) { }
        List<Site> sites = new ArrayList<>();
        try (World w = new World(p, Map.of())) {
            w.driver.program = rail;
            int lastSite = -1000;
            boolean gap = false, slope = false;
            while (w.driver.row < Math.min(masks.length, 7000) && (sites.size() < 12 || !gap || !slope)) {
                w.advance();
                var s = sonic();
                if (!w.course.presentationReady() || s.getAir() || s.isOnObject() || s.isObjectControlled() || s.getDead()) continue;
                int angle = signedAngle(s.getAngle());
                Geometry g = null;
                String kind = null;
                if (Math.abs(angle) <= 4 && Math.abs(s.getGSpeed()) <= 0x300 && w.driver.row - lastSite >= 300 && sites.stream()
                        .filter(x -> x.kind().equals("candidate")).count() < 10) {
                    g = geometry();
                    kind = "candidate";
                } else if (!slope && Math.abs(angle) >= 12 && Math.abs(s.getGSpeed()) <= 0x400) {
                    g = geometry();
                    kind = "slope";
                    slope = true;
                } else if (!gap && Math.abs(angle) <= 4 && w.driver.row % 15 == 0) {
                    Geometry probe = geometry();
                    if (probe.missing() > 0 && probe.missing() < probe.samples()) {
                        g = probe;
                        kind = "gap";
                        gap = true;
                    }
                }
                if (kind == null) continue;
                if (kind.equals("candidate")) lastSite = w.driver.row;
                sites.add(new Site(kind, w.driver.row, w.course.capture(), w.driver.previousMask, g, angle, s.getGSpeed()));
            }
            out("7", p, "sites: " + sites.size());
            for (Site site : sites) {
                IntUnaryOperator neutral = row -> 0;
                Outcome coast = fork(w, site.mark(), site.row(), site.previous(), neutral, 120, dx -> false);
                Outcome brake = fork(w, site.mark(), site.row(), site.previous(),
                        row -> sonic().getXSpeed() > 0 ? LEFT : 0, 120, dx -> false);
                // Walk the strip: stop first (brake), then RIGHT until 96 px past the stop.
                w.course.restore(site.mark());
                w.driver.row = site.row();
                w.driver.previousMask = site.previous();
                w.driver.program = row -> sonic().getXSpeed() > 0 ? LEFT : 0;
                for (int i = 0; i < 90 && (sonic().getGSpeed() != 0 || sonic().getXSpeed() != 0); i++) w.advance();
                CourseCheckpoint stopped = w.course.capture();
                int stoppedRow = w.driver.row, stoppedPrevious = w.driver.previousMask;
                Geometry atStop = geometry();
                Outcome walk = fork(w, stopped, stoppedRow, stoppedPrevious, row -> RIGHT, 120, dx -> dx >= 96);
                out("7", p, String.format("%s row %d angle %d gs %d: geometry at the row %s (missing %d, range %d, low ceilings %d) "
                                + "| at the brake stop %s [%s] | coast %s | brake %s | walk 96px %s",
                        site.kind(), site.row(), site.angle(), site.gs(), site.geometry().flat() ? "FLAT" : "not flat",
                        site.geometry().missing(), site.geometry().range(), site.geometry().lowCeilings(),
                        atStop.flat() ? "FLAT" : "not flat", atStop.profile(), coast, brake, walk));
            }
        }
    }

    // ---- 1p: production boot path -------------------------------------------------------------

    @Test @Order(4) void productionEntry() throws Exception {
        Assumptions.assumeTrue(ACTS != null && PARTS.contains("1p"), "opt-in: -Dtypinghog.v2probe=all (part 1p)");
        for (Pilot p : PILOTS) {
            if (!wanted(p)) continue;
            String romProperty = switch (p.code()) {
                case "s1" -> "sonic1.rom.path";
                case "s2" -> "sonic2.rom.path";
                default -> "s3k.rom.path";
            };
            String rom = System.getProperty(romProperty);
            if (rom == null) {
                out("1p", p, "no ROM property " + romProperty);
                continue;
            }
            try {
                List<Row> rows = productionTrace(p, Path.of(rom));
                List<Row> reference = REFERENCE_ENTRY.get(p.name());
                out("1p", p, "production boot + hub start: " + markers(rows)
                        + (reference == null ? "" : "; against the fixture reference: " + compare(reference, rows)));
            } catch (Throwable t) {
                Throwable cause = t;
                while (cause.getCause() != null) cause = cause.getCause();
                out("1p", p, "ABORTED " + cause);
                cause.printStackTrace(System.out);
            }
        }
    }

    private static List<Row> productionTrace(Pilot p, Path rom) throws Exception {
        Driver driver = new Driver();
        driver.program = entryProgram();
        try (var boot = new com.openggf.tools.HeadlessGameBoot(WIDTH, 224)) {
            var config = GameServices.configuration();
            config.clearSessionOverrides();
            applyProfile(config, p, Map.of());
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
            boot.setModuleDecorator(module -> patch(module, driver));
            var loop = boot.boot(rom, p.zone(), p.act());
            // The hub's stock-act start: SceneNavigator.prepareStockAct's fade target.
            Method start = com.openggf.GameLoop.class.getDeclaredMethod("startLevelFromTitleScreenImmediate", int.class, int.class);
            start.setAccessible(true);
            // Let the session's clock run first, as the hub's frames would, before the hub starts the act.
            for (int i = 0; i < 333; i++) loop.step();
            int clockBefore = GameServices.level().getObjectManager().getVblaCounter();
            driver.row = 0;
            driver.previousMask = 0;
            start.invoke(loop, p.zone(), p.act());
            out("1p", p, "V_int_run_count before the hub start " + clockBefore + ", after the fresh act load "
                    + GameServices.level().getObjectManager().getVblaCounter());
            CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
            List<Row> rows = new ArrayList<>();
            int steps = 0;
            for (; steps < 20_000 && rows.size() < ENTRY_ROWS; steps++) {
                int before = driver.row;
                loop.step();
                if (driver.row != before) rows.add(Row.observe(course));
            }
            out("1p", p, "loop steps for " + rows.size() + " controlled rows: " + steps);
            return rows;
        }
    }

    static String brief(Object o) { return Objects.toString(o); }
}
