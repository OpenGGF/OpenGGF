package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.openggf.mods.code.ForwardRails.*;

/**
 * Authors forward (format 2) Typing of the Hedgehog rails by bounded native-input search, the
 * rails v2 builder (GPT-6.1-Sol's rails design, section 2, with the user's 2026-10-10 decisions):
 * a cold stock act at 320 px under the mod's launch profile (S3K intros skipped), an authored
 * entry to a still, right-facing Sonic, the native countdown (spindash charge in S2/S3K, a crouch
 * in S1), then a beam search over small input macros (run, coast, jump with a held press, brake
 * to a stop, wait) checked on every native row: no hurt, death, level change or native badnik
 * kill (a score jump of 100 or more, by Sonic or the CPU sidekick), no backtracking (camera at
 * most 16 px behind its high water, Sonic at most 64 px behind his and back within 30 rows), no
 * 180-row stall. Fights go at braked stops whose floor samples are flat, whose 96 px strip walks
 * grounded and level, away from collapsing ground and objects, spaced through the act (the first
 * within 300 rows and 800 px of the release), each proved by a held soak of the course's native
 * fight cap from the stand's conversion through the mod's own {@code CombatHandoff}. After a fight
 * the rail charges a short native spindash (S2/S3K) and releases. The endpoint is a still stop
 * after the stock boss flag rises. A finished route is replayed cold (it must reproduce the
 * search's state at every stand and the endpoint) and under V_int_run_count start offsets
 * {@code +1..+128} in single bits (the same pools by class and layout, no fault), then written.
 *
 * <p>Budget per act: 5 million native rows, 10 minutes of search, 2 more for validation. Opt-in:
 * {@code -Dtypinghog.forward=ehz2[,ghz3,...]} with {@code -Dtypinghog.forward.out=<rails dir>}.
 * Prints {@code FWD|} lines. Origin: rails lane, rails v2 step 4, 2026-10-10.
 */
class TypingHedgehogForwardRailBuilder {
    private static final String SPEC = System.getProperty("typinghog.forward");
    private static final Path OUT = Path.of(System.getProperty("typinghog.forward.out", "target/typinghog-forward"));
    /**
     * Authored position waypoints, an authoring aid for a route the beam misses: {@code act:x,y;x,y|act:...}.
     * The beam is pulled to each in turn (the next one counts once Sonic passes within 32 px); the
     * route is still found by the search and held to every rule and gate.
     */
    private static final String WAYPOINTS = System.getProperty("typinghog.forward.waypoints", "");
    static final long ROW_BUDGET = 5_000_000L;
    static final long SEARCH_MS = 600_000L, VALIDATE_MS = 120_000L;
    static final int BEAM = 24, BIN_KEEP = 2, MAX_ROUTE = 18_000, ENTRY_MIN = 60, ENTRY_LIMIT = 1500;
    static final int FIGHT_SOAK = 3600, CLEANUP = 2, BURST_CHARGE = 36, STALL_ROWS = 180, HARD_STALL_ROWS = 600;
    /** Layers without a new best x before the search treats its frontier as collapsed. */
    static final int STAGNANT_LAYERS = 40;
    static final int FIRST_FIGHT_ROWS = 300, FIRST_FIGHT_PX = 800, FIGHT_SPACING_PX = 256, FIGHT_SPACING_ROWS = 120;
    static final double[] FIGHT_FRACTIONS = {0.0, 0.25, 0.45, 0.65, 0.82};
    static final int MAX_SOAKS_PER_LAYER = 3, FIGHT_VALUE = 3000, LOADED_VALUE = 300, LANE_KEEP = 4, HISTORY = 96;
    /** How far past where the next fight belonged a lineage may run before it is dropped. */
    static final int SCHEDULE_SLACK_PX = 2000;
    /** Open view counted per side (px), and its weight in a node's value. */
    static final int OPEN_CAP = 160;
    /** The recorded corridor's pull on the beam, and how near a lane must be to count. */
    static final int CORRIDOR_VALUE = 1500, CORRIDOR_PX = 96;
    /** Each authored waypoint reached outranks x progress made without it, but not a fight. */
    static final int TARGET_VALUE = 2_500;
    static final double OPEN_VALUE = 2.0;

    /** An act the builder authors: launch kind and output file. */
    /** An act the builder authors: launch kind, whether it is a boss-only course, and the output file. */
    record Target(Pilot pilot, boolean spindash, boolean bossOnly, String file) { }

    /** Boss-only courses: the act is its boss (S1 Final Zone, S2 Death Egg). */
    static final Set<String> BOSS_ONLY = Set.of("fz", "dez");

    /** The shipped acts (the legacy builder's targets that ship a rail), in its order. */
    static final List<Target> TARGETS = targets();

    static List<Target> targets() {
        Set<String> shipped = new HashSet<>();
        try {
            for (String line : Files.readAllLines(SOURCE.resolve("src/main/resources/rails/index.txt"))) {
                if (!line.isBlank() && !line.startsWith("#")) shipped.add(line.strip());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<Target> out = new ArrayList<>();
        for (SonicGame game : List.of(SonicGame.SONIC_1, SonicGame.SONIC_2, SonicGame.SONIC_3K)) {
            for (var t : TypingHedgehogRailBuilderProbe.targets(game)) {
                if (!shipped.contains(t.file())) continue;
                out.add(new Target(new Pilot(t.name(), game, t.code(), t.zone(), t.act(), t.sidekick(), t.file()),
                        game != SonicGame.SONIC_1, BOSS_ONLY.contains(t.name()), t.file()));
            }
        }
        return List.copyOf(out);
    }

    @TempDir static Path temp;
    static ExampleModHarness harness;

    @AfterAll static void close() throws Exception {
        if (harness != null) harness.close();
        GameServices.configuration().clearSessionOverrides();
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void s1() throws Exception { drive(SonicGame.SONIC_1); }
    @Test @RequiresRom(SonicGame.SONIC_2) void s2() throws Exception { drive(SonicGame.SONIC_2); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void s3k() throws Exception { drive(SonicGame.SONIC_3K); }

    /** Diagnostic: the act's legacy rail between two x positions ({@code -Dtypinghog.forward.trace=x0:x1}). */
    static void traceLegacy(Target t, int x0, int x1) throws Exception {
        String text = Files.readString(SOURCE.resolve("src/main/resources/rails").resolve(t.pilot().rail()));
        List<Integer> masks = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.startsWith("program ")) continue;
            for (String pair : line.substring(8).split(",")) {
                String[] f = pair.strip().split(":");
                for (int i = 0; i < Integer.parseInt(f[0]); i++) masks.add(Integer.parseInt(f[1], 16));
            }
        }
        try (World w = new World(t.pilot(), Map.of())) {
            w.driver.program = row -> row < masks.size() ? masks.get(row) : 0;
            StringBuilder out = new StringBuilder();
            while (w.driver.row < masks.size()) {
                w.advance();
                var s = sonic();
                if (s.getCentreX() >= x0 && s.getCentreX() <= x1 && w.driver.row % 3 == 0) {
                    out.append(String.format(" [r%d m%X x%d y%d cam%d a%d gs%d %s%s]", w.driver.row, masks.get(w.driver.row - 1),
                            s.getCentreX(), s.getCentreY(), GameServices.camera().getX(), s.getAngle() & 0xFF, s.getGSpeed(),
                            s.getAir() ? "air" : "gnd", s.getRolling() ? " roll" : ""));
                }
                if (s.getCentreX() > x1 + 200) break;
            }
            out(t, "legacy rail through x " + x0 + ".." + x1 + ":" + out);
        }
    }

    private void drive(SonicGame game) throws Exception {
        String trace = System.getProperty("typinghog.forward.trace");
        if (trace != null) {
            String[] f = trace.split(":");
            boolean traced = false;
            for (Target t : TARGETS) {
                if (t.pilot().game() == game && t.pilot().name().equals(f[0])) {
                    traceLegacy(t, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                    traced = true;
                }
            }
            if (traced) return;
        }
        Assumptions.assumeTrue(SPEC != null, "opt-in: -Dtypinghog.forward=<act,...>");
        Set<String> wanted = Set.of(SPEC.split(","));
        for (Target t : TARGETS) {
            if (t.pilot().game() != game || !(wanted.contains(t.pilot().name()) || wanted.contains("all"))) continue;
            if (harness == null) harness = buildHarness(temp, "forward", Map.of());
            long started = System.nanoTime();
            try {
                String result = new Search(t, new Handoff(harness, t.pilot().code())).build();
                out(t, result);
            } catch (Throwable e) {
                Throwable cause = e;
                while (cause.getCause() != null) cause = cause.getCause();
                out(t, "ABORTED " + cause);
                cause.printStackTrace(System.out);
            }
            out(t, String.format("took %.1f s", (System.nanoTime() - started) / 1e9));
        }
    }

    static void out(Target t, String text) {
        System.out.println("FWD|" + t.pilot().name() + "|" + text);
    }

    // ------------------------------------------------------------------ search

    /** A recorded fight: its before-row, the pooled bodies (mod records) and the state there. */
    record Stand(int row, List<?> pool, Row at, int x) { }

    /** A search state at a macro boundary. */
    static final class Node {
        Node parent;
        CourseCheckpoint checkpoint;
        int row, previous;
        int[] masks;
        int cmax, pmax, reverse, lastProgressRow, gameScore, x, y, ymin = Integer.MAX_VALUE, ymax = Integer.MIN_VALUE;
        boolean riding;
        /** The row Sonic last set a new rightmost x: a lift may stall it for a while, never for long. */
        int lastPmaxRow;
        int fights, lastFightRow = -1, lastFightX;
        List<Stand> stands = List.of();
        boolean stopped, boss, ended;
        int bossRow = -1;
        String how = "";
        /** Converts on this node's first row (a fight child): the pool to convert there. */
        List<?> convertAtStart;

        /** Loaded badniks the fights so far took (fights with stock badniks on screen are preferred). */
        int loaded;
        /** Open view the fights so far had (pixels each side, capped): open sites rank ahead of tunnels. */
        int openness;

        /** On the recorded run's corridor at this x (a comparison-only steering hint, never gameplay). */
        boolean onCorridor;
        boolean air;
        /** Rows that replay the recorded run: {railStart, railEnd, recordedStart}. */
        List<int[]> waypoints = List.of();
        /** The next authored position waypoint, and how far from it this node is. */
        int nextTarget;
        int targetDistance;

        double value() {
            return pmax + fights * (double) FIGHT_VALUE + loaded * (double) LOADED_VALUE + openness * (double) OPEN_VALUE
                    + (onCorridor ? CORRIDOR_VALUE : 0) + nextTarget * (double) TARGET_VALUE - targetDistance
                    + (boss ? 50_000 : 0) - row * 0.05;
        }

        long key() {
            return ((long) (x >> 3) << 40) ^ ((long) (y >> 3) << 24) ^ ((long) fights << 16) ^ (stopped ? 1 : 0) ^ ((long) row >> 4);
        }
    }

    /** A macro's mask for its next row, decided from the state before the row. */
    interface Macro {
        /** The mask for macro row {@code i}, or -1 when the macro is done. */
        int next(int i, AbstractPlayableSprite s);
    }

    static Macro hold(int mask, int rows) { return (i, s) -> i < rows ? mask : -1; }

    /** Runs right for {@code run} rows, then rolls (DOWN, the native roll at speed) for {@code roll} rows. */
    static Macro runRoll(int run, int roll) { return (i, s) -> i < run ? RIGHT : i < run + roll ? DOWN : -1; }

    static Macro jump(int press, int land) {
        return (i, s) -> i < press ? RIGHT | JUMP : i < press + 1 ? RIGHT : i < press + 1 + land ? RIGHT : -1;
    }

    /** Brakes to a stop, then waits for 12 still rows (or gives up after 240 rows). */
    static Macro stop() {
        int[] still = {0};
        return (i, s) -> {
            if (i >= 240) return -1;
            boolean isStill = s.getGSpeed() == 0 && s.getXSpeed() == 0 && !s.getAir();
            still[0] = isStill ? still[0] + 1 : 0;
            if (still[0] >= 12) return -1;
            if (s.getAir()) return 0;
            if (s.getXSpeed() > 0 || s.getGSpeed() > 0) return LEFT;
            if (s.getXSpeed() < 0 || s.getGSpeed() < 0) return RIGHT;
            return 0;
        };
    }

    /**
     * A native spindash from wherever Sonic is: brake to a stop, crouch, {@code charge} rows of
     * charge pulses, release holding right for 8 rows. For a steep ramp a slow run cannot climb.
     */
    static Macro stopAndDash(int charge) {
        Macro stop = stop();
        int[] start = {-1};
        return (i, s) -> {
            if (start[0] < 0) {
                int m = stop.next(i, s);
                if (m >= 0) return m;
                if (s.getAir() || s.getGSpeed() != 0) return -1;
                start[0] = i;
            }
            int j = i - start[0];
            if (j < charge) return j >= 2 && (j - 2) % 6 == 0 ? DOWN | JUMP : DOWN;
            return j < charge + 8 ? RIGHT : -1;
        };
    }

    /** After a fight: quiet cleanup rows, then a short spindash charge and release (S2/S3K), or a run (S1). */
    static Macro burst(boolean spindash) { return burst(spindash, BURST_CHARGE, 0); }

    /**
     * After a fight: the quiet cleanup rows, {@code wait} more still rows, then a spindash of
     * {@code charge} rows released holding right (S2/S3K; {@code charge} 0 walks off instead), or a
     * run (S1). Several are tried after each fight: the right strength (or a pause) clears what
     * the strongest dash would slam into.
     */
    static Macro burst(boolean spindash, int charge, int wait) {
        return (i, s) -> {
            if (i < CLEANUP + wait) return 0;
            int j = i - CLEANUP - wait;
            if (!spindash || charge == 0) return j < 24 ? RIGHT : -1;
            if (j < charge) return j >= 2 && (j - 2) % 6 == 0 ? DOWN | JUMP : DOWN;
            return j < charge + 8 ? RIGHT : -1;
        };
    }

    /** The post-fight continuations tried from each fight: {charge, wait}. */
    static final int[][] BURSTS = {{BURST_CHARGE, 0}, {20, 0}, {50, 0}, {BURST_CHARGE, 40}, {0, 0}};

    final class Search {
        final Target target;
        final Handoff handoff;
        final World w;
        final long started = System.nanoTime();
        long rows;
        int goalX, releaseRow, releaseX, countdownRow;
        int soaks, sites, siteRejects, recordedChildren, recordedBestX = -1;
        /** Diagnostics: the furthest lineage that has fought, and the most waypoints any lineage reached. */
        int foughtBestX = -1, foughtBestFights, targetsReached;
        String bestRejected;
        int bestRejectedScore = Integer.MAX_VALUE;
        final Map<String, Integer> deaths = new HashMap<>();
        final Map<String, Integer> layerDeaths = new java.util.TreeMap<>();
        /** Layers left to search with the wide macro set and a doubled beam after a collapse. */
        int widened;
        Node best;

        /** The recorded run's heights by 32 px column (comparison-only: it scores lanes, never drives play). */
        final Map<Integer, List<Integer>> corridor = new HashMap<>();
        /** This act's authored position waypoints ({x, y}), in order. */
        final List<int[]> targets = new ArrayList<>();

        /** The recorded run itself (pad rows and states), for recorded-input waypoint macros. */
        TypingHedgehogRailBuilderProbe.Source recorded;
        String recordedName = "";

        Search(Target target, Handoff handoff) throws IOException {
            this.target = target;
            this.handoff = handoff;
            loadCorridor();
            for (String act : WAYPOINTS.split("\\|")) {
                if (!act.startsWith(target.pilot().name() + ":")) continue;
                for (String xy : act.substring(act.indexOf(':') + 1).split(";")) {
                    String[] f = xy.split(",");
                    targets.add(new int[] {Integer.parseInt(f[0].strip()), Integer.parseInt(f[1].strip())});
                }
            }
            if (!targets.isEmpty()) out(target, "authored waypoints " + targets.stream().map(t -> t[0] + "," + t[1]).toList());
            this.w = new World(target.pilot(), Map.of());
        }

        /**
         * GPT-6.1-Sol's design lets recordings suggest rough corridors: the act's recorded run (the
         * legacy rail builder's trace source) gives the heights it took at each x. A lane on that
         * corridor scores higher, so the beam keeps the upper route a human chose where a faster
         * lower one dead-ends. The search plays only its own pad rows.
         */
        void loadCorridor() {
            try {
                var legacy = TypingHedgehogRailBuilderProbe.targets(target.pilot().game()).stream()
                        .filter(t -> t.name().equals(target.pilot().name())).findFirst().orElse(null);
                if (legacy == null) return;
                var source = TypingHedgehogRailBuilderProbe.load(legacy);
                if (!source.hasRef) return;
                recorded = source;
                recordedName = legacy.source().getFileName().toString();
                for (int i = 0; i < source.refX.length; i++) {
                    List<Integer> ys = corridor.computeIfAbsent(source.refX[i] >> 5, k -> new ArrayList<>());
                    int y = source.refY[i];
                    if (ys.stream().noneMatch(v -> Math.abs(v - y) < 16)) ys.add(y);
                }
                out(target, "corridor from the recorded run: " + corridor.size() + " columns");
            } catch (Exception unavailable) {
                out(target, "no corridor: " + unavailable);
            }
        }

        boolean onCorridor(int x, int y) {
            if (corridor.isEmpty()) return false;
            for (int b = (x >> 5) - 1; b <= (x >> 5) + 1; b++) {
                for (int v : corridor.getOrDefault(b, List.of())) if (Math.abs(v - y) <= CORRIDOR_PX) return true;
            }
            return false;
        }

        long elapsedMs() { return (System.nanoTime() - started) / 1_000_000; }

        String build() throws Exception {
            try {
                Node root = entry();
                if (root == null) return "FAILED entry: no ready, still, right-facing Sonic by row " + ENTRY_LIMIT;
                goalX = w.course.courseMaxX();
                out(target, "entry ready at row " + countdownRow + ", release row " + releaseRow + " at x " + releaseX
                        + ", goal x " + goalX);
                Node done = search(root);
                if (done == null) return "FAILED search: " + summary();
                out(target, "route found: " + done.row + " rows, " + done.fights + " fights at "
                        + done.stands.stream().map(s -> s.row() + "@x" + s.x() + "(" + s.pool().size() + ")").toList()
                        + ", " + summary());
                String validation = validate(done);
                if (validation != null) return "FAILED validation: " + validation;
                Path file = write(done);
                return "QUALIFIED " + file + ", " + done.row + " rows, " + done.fights + " fights";
            } finally {
                w.close();
            }
        }

        String summary() {
            return rows + " native rows, " + elapsedMs() / 1000 + " s, sites " + sites + " (rejected " + siteRejects
                    + "), soaks " + soaks + ", furthest x " + (best == null ? -1 : best.pmax) + " with "
                    + (best == null ? 0 : best.fights) + " fights, recorded-row children " + recordedChildren
                    + " (furthest x " + recordedBestX + "), furthest fought lineage x " + foughtBestX + " with "
                    + foughtBestFights + " fights, waypoints reached " + targetsReached + "/" + targets.size()
                    + ", flattest rejected early site " + bestRejected
                    + ", dead ends " + deaths;
        }

        // ---- entry and countdown

        Node entry() {
            w.driver.program = row -> 0;
            int[] mask = {0};
            w.driver.program = row -> mask[0];
            boolean tapped = false;
            while (w.driver.row < ENTRY_LIMIT) {
                var s = sonic();
                boolean still = !s.getAir() && !s.isObjectControlled() && !s.isControlLocked() && s.getGSpeed() == 0
                        && s.getXSpeed() == 0 && !s.isOnObject();
                if (w.driver.row >= ENTRY_MIN && w.course.presentationReady() && still) {
                    if (s.getDirection() == com.openggf.physics.Direction.RIGHT) break;
                    if (!tapped) {
                        mask[0] = RIGHT;
                        w.advance();
                        rows++;
                        mask[0] = 0;
                        tapped = true;
                        continue;
                    }
                }
                mask[0] = 0;
                w.advance();
                rows++;
            }
            if (w.driver.row >= ENTRY_LIMIT) return null;
            countdownRow = w.driver.row;
            int start = countdownRow;
            w.driver.program = row -> countdownMask(row - start, target.spindash());
            for (int i = 0; i <= COUNTDOWN_ROWS; i++) {
                w.advance();
                rows++;
                if (sonic().isHurt() || sonic().getDead()) return null;
            }
            releaseRow = countdownRow + COUNTDOWN_ROWS;
            releaseX = sonic().getCentreX();
            Node root = new Node();
            root.row = w.driver.row;
            root.previous = w.driver.previousMask;
            root.masks = w.driver.played(root.row);
            root.checkpoint = w.course.capture();
            root.cmax = GameServices.camera().getX();
            root.pmax = sonic().getCentreX();
            root.lastProgressRow = root.row;
            root.lastPmaxRow = root.row;
            root.gameScore = GameServices.gameState().getScore();
            root.x = sonic().getCentreX();
            root.y = sonic().getCentreY();
            return root;
        }

        // ---- the beam

        Node search(Node root) {
            List<Node> beam = new ArrayList<>(List.of(root));
            java.util.ArrayDeque<List<Node>> history = new java.util.ArrayDeque<>();
            int layer = 0, collapses = 0, frontierX = -1, bestX = -1, bestLayer = 0;
            while (true) {
                if (best != null && best.pmax > bestX) {
                    bestX = best.pmax;
                    bestLayer = layer;
                }
                if (!beam.isEmpty() && layer - bestLayer > STAGNANT_LAYERS) {
                    // The beam lives but goes nowhere: treat the frontier as collapsed.
                    out(target, "stagnant for " + STAGNANT_LAYERS + " layers at x " + bestX);
                    if (collapses == 0) traceFrontier(beam);
                    bestLayer = layer;
                    beam = List.of();
                }
                if (rows > ROW_BUDGET || elapsedMs() > SEARCH_MS) {
                    deaths.merge("budget", 1, Integer::sum);
                    return null;
                }
                if (beam.isEmpty()) {
                    // A collapse: every child died. Rewind further each time at the same frontier,
                    // and search wider (more macros, a doubled beam) for a few layers.
                    int x = best == null ? -1 : best.pmax;
                    collapses = x == frontierX ? collapses + 1 : 1;
                    frontierX = x;
                    out(target, "collapse " + collapses + " at layer " + layer + " near x " + x + "; that layer's dead ends " + layerDeaths);
                    if (collapses == 1 && best != null && history.peekLast() != null) traceFrontier(history.peekLast());
                    if (collapses > 6 || history.isEmpty()) {
                        deaths.merge("frontier at x " + x, 1, Integer::sum);
                        return null;
                    }
                    int depth = 1 << collapses;
                    List<Node> back = null;
                    for (int i = 0; i < depth && !history.isEmpty(); i++) back = history.pollLast();
                    beam = back;
                    widened = 3 + depth;
                    continue;
                }
                history.addLast(beam);
                while (history.size() > HISTORY) history.pollFirst();
                layerDeaths.clear();
                List<Node> children = new ArrayList<>();
                int soaksThisLayer = 0;
                List<Node> stopCandidates = new ArrayList<>();
                for (Node node : beam) {
                    for (var named : macros(node)) {
                        Node child = run(node, named.getValue(), named.getKey(), null);
                        if (child == null) continue;
                        if (child.ended) return child;
                        children.add(child);
                        if (child.stopped && !child.boss && fightAllowed(child)) stopCandidates.add(child);
                    }
                }
                // Fights: the most promising stops get the expensive site checks and soak.
                stopCandidates.sort(Comparator.comparingDouble(Node::value).reversed());
                for (Node stop : stopCandidates) {
                    if (soaksThisLayer >= MAX_SOAKS_PER_LAYER) break;
                    materialise(stop);
                    List<Node> fights = fight(stop);
                    if (fights.isEmpty()) continue;
                    soaksThisLayer++;
                    children.addAll(fights);
                }
                beam = select(children);
                if (widened > 0) widened--;
                for (Node n : beam) {
                    if (best == null || n.value() > best.value()) best = n;
                }
                if (++layer % 10 == 0) {
                    out(target, "layer " + layer + ": beam " + beam.size() + ", best x " + (best == null ? -1 : best.pmax)
                            + " fights " + (best == null ? 0 : best.fights) + " row " + (best == null ? -1 : best.row)
                            + ", " + rows + " rows, " + elapsedMs() / 1000 + " s");
                }
                for (Node n : beam) {
                    materialise(n);
                    n.parent = null; // its checkpoint stands alone: ancestors need not stay in memory
                }
            }
        }

        /** Diagnostic: the furthest beam node's state, and 160 RIGHT rows from it (x, y, camera, angle, air). */
        void traceFrontier(List<Node> beam) {
            Node far = beam.stream().max(Comparator.comparingInt(n -> n.pmax)).orElse(null);
            if (far == null) return;
            materialise(far);
            restore(far);
            StringBuilder t = new StringBuilder();
            w.driver.program = row -> RIGHT;
            for (int i = 0; i < 160; i++) {
                w.advance();
                var s = sonic();
                if (i % 4 == 0) t.append(String.format(" [%d x%d y%d cam%d a%d %s%s]", i, s.getCentreX(), s.getCentreY(),
                        GameServices.camera().getX(), s.getAngle() & 0xFF, s.getAir() ? "air" : "gnd", s.getRolling() ? " roll" : ""));
                if (s.isHurt() || s.getDead()) { t.append(" HURT"); break; }
            }
            out(target, "frontier node row " + far.row + " x " + far.x + " y " + far.y + " via " + far.how + "; RIGHT from it:" + t);
            // What stands in the way: nearby objects, and the terrain wall distance ahead by height.
            restore(far);
            StringBuilder objects = new StringBuilder();
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                int ox, oy;
                try {
                    ox = o.getX();
                    oy = o.getY();
                } catch (RuntimeException spawnless) {
                    continue;
                }
                if (Math.abs(ox - far.x) < 256 && Math.abs(oy - far.y) < 160) {
                    objects.append(' ').append(o.getClass().getSimpleName()).append('@').append(ox - far.x)
                            .append(',').append(oy - far.y);
                }
            }
            StringBuilder walls = new StringBuilder();
            for (int dy = -160; dy <= 32; dy += 16) {
                var wall = com.openggf.physics.ObjectTerrainUtils.checkRightWallDist(far.x, far.y + dy);
                walls.append(String.format(" [dy%d %s]", dy, wall.foundSurface() ? Integer.toString(wall.distance()) : "-"));
            }
            out(target, "  objects near (dx,dy):" + objects + "; right wall distance by height:" + walls);
        }

        /**
         * Waypoints: the recorded run's own pad rows from where it was in this node's place (within
         * 12 px, same air state), for 24, 48 or 96 rows. They give the beam the human's timed jumps
         * and platform rides where a route needs them; the result is validated like any other row,
         * and the rail names these rows in its {@code waypoints} line.
         */
        List<Map.Entry<String, Macro>> recordedMacros(Node node) {
            if (recorded == null || recorded.refAir == null) return List.of();
            List<Integer> starts = new ArrayList<>();
            for (int i = 0; i < recorded.refX.length && starts.size() < 3; i++) {
                if (Math.abs(recorded.refX[i] - node.x) <= 12 && Math.abs(recorded.refY[i] - node.y) <= 12
                        && recorded.refAir[i] == node.air && (starts.isEmpty() || i - starts.getLast() > 30)) {
                    starts.add(i);
                }
            }
            List<Map.Entry<String, Macro>> out = new ArrayList<>();
            int[] masks = recorded.masks;
            for (int start : starts) {
                for (int n : new int[] {24, 48, 96}) {
                    out.add(Map.entry("REC" + start + "+" + n, (j, s) -> j < n && start + j < masks.length ? masks[start + j] : -1));
                }
            }
            return out;
        }

        List<Map.Entry<String, Macro>> macros(Node node) {
            List<Map.Entry<String, Macro>> out = new ArrayList<>(recordedMacros(node));
            if (node.boss) {
                out.add(Map.entry("stop", stop()));
                out.add(Map.entry("R8", hold(RIGHT, 8)));
                out.add(Map.entry("C30", hold(0, 30)));
                return out;
            }
            // Between authored waypoints (a route the beam alone missed) the wide macro set applies.
            if (widened > 0 || node.nextTarget < targets.size()) {
                for (int n : new int[] {4, 8, 12, 16, 24, 32, 48, 64, 96}) out.add(Map.entry("R" + n, hold(RIGHT, n)));
                for (int n : new int[] {8, 16, 32, 48}) out.add(Map.entry("C" + n, hold(0, n)));
                for (int p : new int[] {1, 2, 4, 6, 8, 12, 16, 20, 24}) {
                    out.add(Map.entry("J" + p, jump(p, 8)));
                    out.add(Map.entry("J" + p + "L", jump(p, 20)));
                }
                for (int n : new int[] {4, 8, 16}) out.add(Map.entry("B" + n, (i, s) -> i < n ? (s.getXSpeed() > 0 ? LEFT : 0) : -1));
                for (int n : new int[] {15, 60, 120}) out.add(Map.entry("W" + n, hold(0, n)));
                out.add(Map.entry("stop", stop()));
                for (int r : new int[] {16, 32, 64}) out.add(Map.entry("RR" + r, runRoll(r, 24)));
                if (target.spindash()) for (int c : new int[] {14, 26, 38}) out.add(Map.entry("SD" + c, stopAndDash(c)));
                return out;
            }
            for (int n : new int[] {8, 16, 32, 64}) out.add(Map.entry("R" + n, hold(RIGHT, n)));
            for (int n : new int[] {16, 32}) out.add(Map.entry("C" + n, hold(0, n)));
            for (int p : new int[] {4, 12, 20}) out.add(Map.entry("J" + p, jump(p, 12)));
            out.add(Map.entry("stop", stop()));
            out.add(Map.entry("W30", hold(0, 30)));
            out.add(Map.entry("RR32", runRoll(32, 24)));
            if (target.spindash()) out.add(Map.entry("SD26", stopAndDash(26)));
            return out;
        }

        /** Plays {@code macro} from {@code node}; the child, or null on a dead end. */
        Node run(Node node, Macro macro, String how, List<?> convert) {
            materialise(node);
            restore(node);
            if (convert != null) w.driver.before = () -> handoff.convert(convert);
            Node c = new Node();
            c.parent = node;
            c.how = how;
            c.convertAtStart = convert;
            c.cmax = node.cmax;
            c.pmax = node.pmax;
            c.reverse = node.reverse;
            c.lastProgressRow = node.lastProgressRow;
            c.lastPmaxRow = node.lastPmaxRow;
            c.ymin = node.ymin;
            c.ymax = node.ymax;
            c.fights = node.fights;
            c.lastFightRow = node.lastFightRow;
            c.lastFightX = node.lastFightX;
            c.stands = node.stands;
            c.waypoints = node.waypoints;
            c.nextTarget = node.nextTarget;
            c.loaded = node.loaded;
            c.openness = node.openness;
            c.boss = node.boss;
            c.bossRow = node.bossRow;
            int score = node.gameScore;
            int[] mask = {0};
            w.driver.program = row -> mask[0];
            for (int i = 0; ; i++) {
                var s = sonic();
                int m = macro.next(i, s);
                if (m < 0) break;
                mask[0] = m;
                w.advance();
                rows++;
                s = sonic();
                int row = w.driver.row;
                String death = null;
                if (s.getDead()) death = "death";
                else if (s.isHurt() || s.getHurtAtFrameStart()) death = "hurt";
                else if (GameServices.level().getCurrentZone() != target.pilot().zone()
                        || GameServices.level().getCurrentAct() != target.pilot().act()) death = "level change";
                else if (GameServices.gameState().getScore() - score >= 100) death = "native kill";
                else if (row >= MAX_ROUTE) death = "route cap";
                if (death == null) {
                    score = GameServices.gameState().getScore();
                    int camX = GameServices.camera().getX(), x = s.getCentreX();
                    if (camX < c.cmax - 16) death = "camera backtrack";
                    else if (x < c.pmax - 64) death = "player backtrack";
                    c.reverse = x < c.pmax ? c.reverse + 1 : 0;
                    if (death == null && c.reverse > 30) death = "reverse excursion";
                    c.cmax = Math.max(c.cmax, camX);
                    // Progress: further right, a new height (lifts, shafts) by 24 px, or riding an object.
                    int y = s.getCentreY();
                    if (x > c.pmax || y < c.ymin - 24 || y > c.ymax + 24 || s.isOnObject()) {
                        c.lastProgressRow = row;
                        c.ymin = Math.min(c.ymin, y);
                        c.ymax = Math.max(c.ymax, y);
                    }
                    if (x > c.pmax) {
                        c.pmax = x;
                        c.ymin = c.ymax = y;
                        c.lastPmaxRow = row;
                    }
                    if (c.nextTarget < targets.size()) {
                        int[] t = targets.get(c.nextTarget);
                        if (Math.abs(x - t[0]) <= 32 && Math.abs(y - t[1]) <= 32) c.nextTarget++;
                    }
                    if (death == null && !c.boss && !s.isObjectControlled() && row - c.lastProgressRow > STALL_ROWS) death = "stall";
                    if (death == null && !c.boss && row - c.lastPmaxRow > HARD_STALL_ROWS) death = "stall (no new x)";
                    if (death == null && !c.boss) death = scheduleMiss(c.fights, row, x);
                }
                if (death != null) {
                    deaths.merge(death, 1, Integer::sum);
                    layerDeaths.merge(death, 1, Integer::sum);
                    w.driver.before = null;
                    return null;
                }
                if (!c.boss && GameServices.gameState().isBossFightActive()) {
                    c.boss = true;
                    c.bossRow = row;
                    if (c.fights < 4 && !target.bossOnly()) {
                        deaths.merge("boss before four fights", 1, Integer::sum);
                        return null;
                    }
                    break;
                }
            }
            w.driver.before = null;
            var s = sonic();
            c.row = w.driver.row;
            c.previous = w.driver.previousMask;
            c.masks = w.driver.played(c.row);
            c.gameScore = score;
            c.x = s.getCentreX();
            c.y = s.getCentreY();
            c.riding = s.isOnObject();
            c.air = s.getAir();
            targetsReached = Math.max(targetsReached, c.nextTarget);
            if (c.fights > 0 && (c.fights > foughtBestFights || (c.fights == foughtBestFights && c.pmax > foughtBestX))) {
                foughtBestFights = c.fights;
                foughtBestX = c.pmax;
            }
            if (c.nextTarget < targets.size()) {
                int[] t = targets.get(c.nextTarget);
                c.targetDistance = Math.min(4000, Math.abs(c.x - t[0]) + 2 * Math.abs(c.y - t[1]));
            } else {
                c.targetDistance = 0;
            }
            c.onCorridor = onCorridor(c.x, c.y);
            if (how.startsWith("REC")) {
                recordedChildren++;
                recordedBestX = Math.max(recordedBestX, c.x);
                List<int[]> spans = new ArrayList<>(node.waypoints);
                spans.add(new int[] {node.row, c.row, Integer.parseInt(how.substring(3, how.indexOf('+')))});
                c.waypoints = List.copyOf(spans);
            }
            c.stopped = how.equals("stop") && s.getGSpeed() == 0 && s.getXSpeed() == 0 && !s.getAir();
            if (c.boss && c.stopped && !s.isHurt()) c.ended = endpointHolds(c);
            if (c.ended) c.checkpoint = w.course.capture();
            return c;
        }

        void restore(Node node) {
            w.course.restore(node.checkpoint);
            w.driver.row = node.row;
            w.driver.previousMask = node.previous;
            w.driver.hold = false;
            w.driver.before = null;
            // The mask log keeps this node's program: later rows are overwritten as they play.
            if (w.driver.log.length < node.masks.length) w.driver.log = Arrays.copyOf(w.driver.log, node.masks.length * 2);
            System.arraycopy(node.masks, 0, w.driver.log, 0, node.masks.length);
        }

        /** Captures a node's checkpoint by replaying its rows (and its fight's conversion) from its parent. */
        void materialise(Node node) {
            if (node.checkpoint != null) return;
            materialise(node.parent);
            restore(node.parent);
            if (node.convertAtStart != null) {
                List<?> pool = node.convertAtStart;
                w.driver.before = () -> handoff.convert(pool);
            }
            int[] masks = node.masks;
            w.driver.program = row -> masks[row];
            while (w.driver.row < node.row) {
                w.advance();
                rows++;
            }
            w.driver.before = null;
            node.checkpoint = w.course.capture();
        }

        // ---- fights

        /**
         * A lineage that can no longer meet the fight schedule is a dead end, not a long-lived beam
         * member: no first fight by its window, or too few fights by where the next one belonged.
         */
        String scheduleMiss(int fights, int row, int x) {
            if (target.bossOnly()) return null;
            if (fights == 0) {
                return row - releaseRow > FIRST_FIGHT_ROWS + 120 || x - releaseX > FIRST_FIGHT_PX + 200 ? "missed the first fight" : null;
            }
            if (fights >= 4) return null;
            double due = releaseX + (goalX - releaseX) * FIGHT_FRACTIONS[fights];
            return x > due + SCHEDULE_SLACK_PX ? "fight " + (fights + 1) + " overdue" : null;
        }

        boolean fightAllowed(Node n) {
            if (target.bossOnly()) return false;
            if (n.fights >= 5) return false;
            if (n.fights == 0) return n.row - releaseRow <= FIRST_FIGHT_ROWS + 120 && n.x - releaseX <= FIRST_FIGHT_PX;
            if (n.x - n.lastFightX < FIGHT_SPACING_PX || n.row - n.lastFightRow < FIGHT_SPACING_ROWS) return false;
            double fraction = FIGHT_FRACTIONS[Math.min(n.fights, FIGHT_FRACTIONS.length - 1)];
            return n.x >= releaseX + (goalX - releaseX) * fraction - 600;
        }

        /** The stop as a fight site: checks, the held soak, then the fight children (conversion, cleanup, a burst each). */
        List<Node> fight(Node stop) {
            sites++;
            restore(stop);
            String reject = siteReject(stop);
            if (reject != null) {
                siteRejects++;
                deaths.merge("site: " + reject, 1, Integer::sum);
                return List.of();
            }
            List<?> pool = handoff.pool();
            Row at = Row.observe(w.course);
            int open = Math.min(OPEN_CAP, openRun(-1)) + Math.min(OPEN_CAP, openRun(1));
            // The held soak: the fight's living world for the course's native cap, from the conversion.
            restore(stop);
            w.driver.before = () -> handoff.convert(pool);
            w.driver.hold = true;
            soaks++;
            for (int i = 0; i < FIGHT_SOAK; i++) {
                w.advance();
                rows++;
                // The held Sonic must come through untouched: the course restores at once on a native
                // hit mid-fight, which would show as a jump.
                if (sonic().getDead() || sonic().isHurt() || GameServices.level().getCurrentAct() != target.pilot().act()) {
                    w.driver.hold = false;
                    deaths.merge("soak", 1, Integer::sum);
                    return List.of();
                }
            }
            w.driver.hold = false;
            List<Stand> stands = new ArrayList<>(stop.stands);
            stands.add(new Stand(stop.row, pool, at, stop.x));
            List<Node> out = new ArrayList<>();
            for (int[] b : BURSTS) {
                if (!target.spindash() && b[0] != BURST_CHARGE) continue;
                Node fight = run(stop, burst(target.spindash(), b[0], b[1]), "fight", pool);
                if (fight == null) continue;
                fight.stands = List.copyOf(stands);
                fight.fights = stop.fights + 1;
                fight.loaded = stop.loaded + pool.size();
                fight.openness = stop.openness + open;
                fight.lastFightRow = stop.row;
                fight.lastFightX = stop.x;
                fight.checkpoint = w.course.capture();
                out.add(fight);
            }
            if (!out.isEmpty()) {
                out(target, String.format("fight %d accepted at row %d x %d y %d: %d loaded, open %d px, %d continuations",
                        stop.fights + 1, stop.row, stop.x, stop.y, pool.size(), open, out.size()));
            }
            return out;
        }

        /** Why a stop cannot hold a fight, or null. */
        String siteReject(Node stop) {
            var s = sonic();
            if (s.isOnObject() || s.isObjectControlled() || s.getAir()) return "support";
            if (Math.abs(signedAngle(s.getAngle())) > 4) return "slope";
            if (s.isInWater()) return "water";
            int screenX = s.getCentreX() - GameServices.camera().getX();
            if (screenX < 64 || screenX > WIDTH - 128) return "screen position";
            Geometry g = geometry();
            if (!g.flat()) {
                // Diagnostics: the flattest rejected strip in the first-fight window.
                if (stop.fights == 0 && (bestRejected == null || g.range() + 8 * g.missing() < bestRejectedScore)) {
                    bestRejected = "row " + stop.row + " x " + stop.x + " y " + stop.y + " range " + g.range() + " missing "
                            + g.missing() + " [" + g.profile() + "]";
                    bestRejectedScore = g.range() + 8 * g.missing();
                }
                return "geometry";
            }
            // The player must see him fight: not behind high-priority foreground, and room for badniks to approach.
            if (highPriorityCover() > 0.25) return "behind foreground";
            if (openRun(-1) < 48 || openRun(1) < 96) return "walled in";
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                String name = o.getClass().getSimpleName();
                if (!(name.contains("Collaps") || name.contains("Crumbl") || name.contains("Breakaway"))) continue;
                try {
                    if (Math.abs(o.getX() - s.getCentreX()) < 160 && Math.abs(o.getY() - s.getCentreY()) < 96) return "collapsing ground";
                } catch (RuntimeException spawnless) {
                    return "collapsing ground";
                }
            }
            // Walk the strip: 96 px right, grounded and level, with no wall or drag.
            int x0 = s.getCentreX();
            w.driver.program = row -> RIGHT;
            int minA = 999, maxA = -999;
            boolean ok = true;
            for (int i = 0; i < 120 && sonic().getCentreX() - x0 < 96; i++) {
                w.advance();
                rows++;
                var t = sonic();
                if (t.getAir() || t.isHurt() || t.getDead()) {
                    ok = false;
                    break;
                }
                int a = signedAngle(t.getAngle());
                minA = Math.min(minA, a);
                maxA = Math.max(maxA, a);
            }
            if (!ok) return "strip walk leaves the ground";
            if (sonic().getCentreX() - x0 < 96) return "strip walk blocked";
            if (maxA - minA > 8) return "strip not level";
            restore(stop);
            return null;
        }

        /**
         * The share of Sonic's sprite box (centre +-8 x, +-16 y) drawn over by high-priority plane-A
         * pixels, which hide him: sampled every 4 px through the chunk, pattern and pixel.
         */
        double highPriorityCover() {
            var lm = GameServices.level();
            var level = lm.getCurrentLevel();
            var s = sonic();
            int covered = 0, total = 0;
            for (int y = s.getCentreY() - 16; y < s.getCentreY() + 16; y += 4) {
                for (int x = s.getCentreX() - 8; x < s.getCentreX() + 8; x += 4) {
                    total++;
                    var cd = lm.getChunkDescAt((byte) 0, x, y);
                    if (cd == null) continue;
                    int index = cd.getChunkIndex();
                    if (index < 0 || index >= level.getChunkCount()) continue;
                    var chunk = level.getChunk(index);
                    if (chunk == null) continue;
                    int cx = x & 15, cy = y & 15;
                    if (cd.getHFlip()) cx = 15 - cx;
                    if (cd.getVFlip()) cy = 15 - cy;
                    var desc = chunk.getPatternDesc(cx >> 3, cy >> 3);
                    if (desc == null || desc == com.openggf.level.PatternDesc.EMPTY || !desc.getPriority()) continue;
                    int px = cx & 7, py = cy & 7;
                    if (desc.getHFlip()) px = 7 - px;
                    if (desc.getVFlip()) py = 7 - py;
                    int pattern = desc.getPatternIndex();
                    if (pattern < 0 || pattern >= level.getPatternCount()) continue;
                    if (level.getPattern(pattern).getPixel(px, py) != 0) covered++;
                }
            }
            return total == 0 ? 0 : covered / (double) total;
        }

        /**
         * How far the view is open beside Sonic ({@code direction} -1 left, 1 right): 8 px steps at
         * his centre and head height that are not inside solid ground, up to the camera edge.
         */
        int openRun(int direction) {
            var s = sonic();
            int x = s.getCentreX(), cam = GameServices.camera().getX();
            int edge = direction < 0 ? cam : cam + WIDTH;
            int run = 0;
            for (int at = x + direction * 8; direction < 0 ? at >= edge : at <= edge; at += direction * 8) {
                boolean solid = false;
                for (int y : new int[] {s.getCentreY(), s.getCentreY() - 16}) {
                    var floor = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(at, y);
                    if (floor.foundSurface() && floor.distance() < 0) solid = true;
                }
                if (solid) break;
                run += 8;
            }
            return run;
        }

        /** At the endpoint: the stock boss retired and two held rows, as the course plays it, with Sonic untouched. */
        boolean endpointHolds(Node end) {
            var check = w.course.capture();
            int row = w.driver.row, previous = w.driver.previousMask;
            try {
                w.driver.before = () -> retireBoss();
                w.driver.hold = true;
                for (int i = 0; i < 2; i++) {
                    w.advance();
                    rows++;
                    if (sonic().isHurt() || sonic().getDead()) return false;
                }
                return true;
            } finally {
                w.driver.hold = false;
                w.driver.before = null;
                w.course.restore(check);
                w.driver.row = row;
                w.driver.previousMask = previous;
            }
        }

        void retireBoss() {
            try {
                harness.loader().loadClass("typinghog.rails.CombatHandoff").getMethod("retireBoss").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * The next beam: best first, at most {@link #BIN_KEEP} per 32x16 px cell, but first the best
         * {@link #LANE_KEEP} of each vertical lane (128 px bands, riding or not), so an upper route
         * survives a faster lower one long enough to show whether the lower one dead-ends.
         */
        List<Node> select(List<Node> children) {
            children.sort(Comparator.comparingDouble(Node::value).reversed());
            List<Node> out = new ArrayList<>();
            Map<Long, Integer> bins = new HashMap<>();
            Set<Long> seen = new HashSet<>();
            int limit = widened > 0 ? 2 * BEAM : BEAM;
            Map<Integer, Integer> lanes = new HashMap<>();
            for (Node c : children) {
                int lane = (c.y >> 7) * 2 + (c.riding ? 1 : 0);
                if (lanes.getOrDefault(lane, 0) >= LANE_KEEP) continue;
                long exact = ((long) c.x << 32) ^ ((long) c.y << 16) ^ c.fights ^ ((long) c.row << 48);
                if (!seen.add(exact)) continue;
                lanes.merge(lane, 1, Integer::sum);
                bins.merge(((long) (c.x >> 5) << 32) ^ ((long) (c.y >> 4) << 8) ^ c.fights ^ (c.stopped ? 1L << 63 : 0), 1, Integer::sum);
                out.add(c);
                if (out.size() >= limit) return out;
            }
            for (Node c : children) {
                long exact = ((long) c.x << 32) ^ ((long) c.y << 16) ^ c.fights ^ ((long) c.row << 48);
                if (!seen.add(exact)) continue;
                long bin = ((long) (c.x >> 5) << 32) ^ ((long) (c.y >> 4) << 8) ^ c.fights ^ (c.stopped ? 1L << 63 : 0);
                if (bins.merge(bin, 1, Integer::sum) > BIN_KEEP) continue;
                out.add(c);
                if (out.size() >= limit) break;
            }
            return out;
        }

        // ---- validation and output

        /** Cold replay (state at each stand and the endpoint), then V_int_run_count phase offsets. */
        String validate(Node done) throws IOException {
            long validationStart = System.nanoTime();
            String cold = replay(done, 0, true);
            if (cold != null) return "cold replay: " + cold;
            for (int bit = 0; bit < 8; bit++) {
                if ((System.nanoTime() - validationStart) / 1_000_000 > VALIDATE_MS) return "validation time";
                String phase = replay(done, 1 << bit, false);
                if (phase != null) return "V_int_run_count +" + (1 << bit) + ": " + phase;
            }
            return null;
        }

        String replay(Node done, int vbla, boolean exact) throws IOException {
            int[] masks = done.masks;
            Map<Integer, Stand> standsByRow = new HashMap<>();
            for (Stand s : done.stands) standsByRow.put(s.row(), s);
            try (World v = new World(target.pilot(), Map.of(), vbla)) {
                v.driver.program = row -> masks[row];
                int score = GameServices.gameState().getScore();
                while (v.driver.row < done.row) {
                    Stand stand = standsByRow.get(v.driver.row);
                    if (stand != null) {
                        if (exact) {
                            List<String> d = stand.at().differences(Row.observe(v.course));
                            if (!d.isEmpty()) return "stand " + stand.row() + " differs: " + d;
                        }
                        List<?> pool = handoff.pool();
                        String mismatch = poolKeys(pool).equals(poolKeys(stand.pool())) ? null
                                : "pool " + poolKeys(pool) + " vs " + poolKeys(stand.pool());
                        if (mismatch != null) return "stand " + stand.row() + ": " + mismatch;
                        v.driver.before = () -> handoff.convert(pool);
                    }
                    v.advance();
                    var s = sonic();
                    if (s.getDead() || s.isHurt()) return "native hurt at row " + v.driver.row;
                    if (GameServices.gameState().getScore() - score >= 100) return "native kill at row " + v.driver.row;
                    score = GameServices.gameState().getScore();
                }
                if (!GameServices.gameState().isBossFightActive()) return "no boss flag at the endpoint";
                if (sonic().getGSpeed() != 0 || sonic().getAir()) return "Sonic not still at the endpoint";
            }
            return null;
        }

        List<String> poolKeys(List<?> pool) {
            return pool.stream().map(b -> component(b, "className") + "/L" + component(b, "layoutIndex")).sorted().toList();
        }

        Path write(Node done) throws IOException {
            StringBuilder stands = new StringBuilder();
            int id = 1;
            for (Stand s : done.stands) {
                stands.append("stand ").append(id++).append(' ').append(s.row()).append(' ').append(CLEANUP);
                if (s.pool().isEmpty()) stands.append(" -");
                for (Object b : s.pool()) stands.append(' ').append(token(b));
                stands.append('\n');
            }
            String sha1 = w.course.romSha1();
            String crc = TypingHedgehogRailBuilderProbe.romCrc();
            String profile = "width=" + WIDTH + (target.pilot().code().equals("s3k") ? " intros=skip" : "");
            StringBuilder waypoints = new StringBuilder();
            for (int[] span : done.waypoints) {
                waypoints.append(' ').append(span[0]).append('-').append(span[1] - 1).append('@').append(span[2]);
            }
            String authored = targets.isEmpty() ? "" : " authored-targets " + String.join(";",
                    targets.stream().map(t -> t[0] + "," + t[1]).toList());
            String waypointLine = waypoints.length() == 0 && authored.isEmpty() ? ""
                    : "waypoints" + (waypoints.length() == 0 ? "" : " recorded " + recordedName + waypoints) + authored + "\n";
            String text = "# " + target.pilot().code() + " " + target.pilot().name() + ": authored by TypingHedgehogForwardRailBuilder ("
                    + summary() + ")\nformat 2\npolicy forward-v1\ncombat live-restore-v1\ncourse "
                    + (target.bossOnly() ? "boss-only" : "regular") + "\nlaunch "
                    + (target.spindash() ? "native-spindash" : "native-run-roll") + "\nprofile " + profile + "\ngame "
                    + target.pilot().code() + "\nzone " + target.pilot().zone() + "\nact " + target.pilot().act()
                    + "\nteam " + (target.pilot().sidekick().isEmpty() ? "sonic" : "sonic," + target.pilot().sidekick())
                    + "\nrom " + crc + "\nrom-sha1 " + sha1 + "\nengine " + com.openggf.version.AppVersion.get()
                    + "\ncountdown " + countdownRow + "\n" + waypointLine + stands + "endpoint boss-arena " + done.row + "\nvalidated "
                    + done.row + "\nprogram " + TypingHedgehogRailBuilderProbe.rle(done.masks, done.row) + "\n";
            Files.createDirectories(OUT);
            Path file = OUT.resolve(target.file());
            Files.writeString(file, text);
            return file;
        }
    }

}
