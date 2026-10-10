package typinghog.rails;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One act's rail: a forward course (format 2). An authored forward run from the act's cold start,
 * with a native countdown start, fights at measured stops whose loaded badniks join them, and a
 * genuine stock endpoint. Read from {@code rails/*.rail} text:
 *
 * <pre>
 * format 2
 * policy forward-v2
 * combat live-restore-v1
 * course regular
 * launch native-spindash
 * profile intros=skip width=400
 * game s2
 * zone 0
 * act 1
 * team sonic,tails
 * rom 7B905383
 * rom-sha1 8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9
 * engine 0.7.prerelease+...
 * countdown 60
 * stand 1 412 2 BuzzerBadnikInstance@18/L12
 * stand 2 900 2 -
 * endpoint boss-arena 3800
 * validated 3800
 * program ...
 * </pre>
 *
 * All rows are <em>before-row</em> boundaries: after the course has played row {@code r-1}, the
 * next row is {@code r}. {@code countdown r} plays the native countdown on rows {@code r} to
 * {@code r+179} (DOWN, with spindash charge pulses under {@code native-spindash}) and releases
 * on row {@code r+180}. {@code launch held-run} uses neutral countdown rows under object control
 * and a {@code burst 0x600} header for Sonic 1's running release. {@code stand id r cleanup pool} starts a fight before row {@code r}: the
 * listed loaded badniks ({@code Class@slot/Llayout}, or {@code -}) convert there, and rows
 * {@code r} to {@code r+cleanup-1} hold no direction or jump. {@code endpoint kind r} starts the
 * act's boss (or finale) before row {@code r}; {@code validated} equals the program length.
 * {@code waypoints ...} is provenance only: which rows replay inputs from the act's recorded run
 * (an authoring aid; those rows are validated like any others).
 *
 * <p>{@code program} is run-length {@code frames:hexMask} pairs in {@code AbstractPlayableSprite}
 * input bits (UP 01, DOWN 02, LEFT 04, RIGHT 08, JUMP 10; Start is never part of a rail).
 * {@code engine}/{@code rom}/{@code rom-sha1} name what the program was validated against. The
 * recording-derived format 1 rails were retired with the forward courses (2026-10-10).
 */
public record Rail(String game, int zone, int act, String team, int validated, String engine, String rom,
                   int[] runFrames, int[] runMasks, Plan plan) {
    public static final int UP = 0x01, DOWN = 0x02, LEFT = 0x04, RIGHT = 0x08, JUMP = 0x10, PAD_BITS = 0x1F;
    /** Native countdown rows before the release row. */
    public static final int COUNTDOWN_ROWS = 180;

    /** The forward course's semantics. */
    public record Plan(String policy, String combat, String kind, String launch, int burst, Map<String, String> profile,
                       String romSha1, int countdownRow, List<Stand> stands, Endpoint endpoint, String waypoints) {
        public Plan {
            profile = Map.copyOf(profile);
            stands = List.copyOf(stands);
        }

        /** The release row: the first row after the countdown. */
        public int releaseRow() { return countdownRow + COUNTDOWN_ROWS; }

        /** True when this plan has the fights a course of its kind needs (four or five regular, none boss-only). */
        public boolean qualifies() {
            return switch (kind) {
                case "regular" -> stands.size() >= 4 && stands.size() <= 5;
                case "boss-only" -> stands.isEmpty();
                default -> false;
            };
        }
    }

    /** A fight: before row {@code row}; its loaded badniks; {@code cleanup} quiet rows after it. */
    public record Stand(int id, int row, int cleanup, List<Body> pool) {
        public Stand { pool = List.copyOf(pool); }
    }

    /** A pooled native body as authored: class simple name, slot (diagnostic) and layout index (identity). */
    public record Body(String className, int slot, int layoutIndex) {
        public String key() { return className + "/L" + layoutIndex; }
        @Override public String toString() { return className + "@" + slot + "/L" + layoutIndex; }

        static Body parse(String token) {
            int at = token.indexOf('@'), l = token.indexOf("/L");
            if (at <= 0 || l < at) throw new IllegalArgumentException("Bad pool body: " + token);
            return new Body(token.substring(0, at), Integer.parseInt(token.substring(at + 1, l)),
                    Integer.parseInt(token.substring(l + 2)));
        }
    }

    /** The course's end: a stock boss arena, act end or pre-handover boundary, before row {@code row}. */
    public record Endpoint(String kind, int row) { }

    public Rail {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(team, "team");
        if (runFrames.length != runMasks.length) throw new IllegalArgumentException("Unpaired program runs");
        int length = 0;
        for (int i = 0; i < runFrames.length; i++) {
            if (runFrames[i] <= 0) throw new IllegalArgumentException("Program run must be positive");
            if ((runMasks[i] & ~PAD_BITS) != 0) throw new IllegalArgumentException("Program mask outside the pad bits: "
                    + Integer.toHexString(runMasks[i]));
            length = Math.addExact(length, runFrames[i]);
        }
        if (validated < 0 || validated > length) throw new IllegalArgumentException("Validated past the program");
    }

    /** Pad mask for program row {@code row}; neutral past the validated end. */
    public int maskAt(int row) {
        if (row < 0 || row >= validated) return 0;
        int at = 0;
        for (int i = 0; i < runFrames.length; i++) {
            at += runFrames[i];
            if (row < at) return runMasks[i];
        }
        return 0;
    }

    /** A cursor that walks the program one row at a time without re-scanning the runs. */
    public static final class Cursor {
        private final Rail rail;
        private int row;
        private int run;
        private int runEnd;

        public Cursor(Rail rail) {
            this.rail = rail;
            this.runEnd = rail.runFrames.length == 0 ? 0 : rail.runFrames[0];
        }

        public int row() { return row; }
        public boolean finished() { return row >= rail.validated; }

        /** The mask for the current row, then advances. */
        public int next() {
            if (finished()) return 0;
            while (row >= runEnd && run + 1 < rail.runFrames.length) runEnd += rail.runFrames[++run];
            int mask = rail.runMasks[run];
            row++;
            return mask;
        }

        /** Rewinds the cursor to {@code target} (a stand or retry restores its row). */
        public void seek(int target) {
            row = 0;
            run = 0;
            runEnd = rail.runFrames.length == 0 ? 0 : rail.runFrames[0];
            while (row < target && !finished()) next();
        }
    }

    public static Rail parse(String text) {
        String game = null, team = "sonic", engine = "", rom = "";
        String policy = null, combat = null, kind = null, launch = null, romSha1 = null, endpointKind = null, waypoints = null;
        int format = -1, zone = -1, act = -1, validated = -1, countdown = -1, endpointRow = -1, burst = 0;
        Map<String, String> profile = new LinkedHashMap<>();
        List<Integer> frames = new ArrayList<>(), masks = new ArrayList<>();
        List<Stand> stands = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            String value = space < 0 ? "" : line.substring(space + 1).strip();
            switch (key) {
                case "format" -> format = Integer.parseInt(value);
                case "policy" -> policy = value;
                case "combat" -> combat = value;
                case "course" -> kind = value;
                case "launch" -> launch = value;
                case "burst" -> burst = Integer.decode(value);
                case "profile" -> {
                    for (String pair : value.split("\\s+")) {
                        int eq = pair.indexOf('=');
                        if (eq <= 0) throw new IllegalArgumentException("Bad profile entry: " + pair);
                        profile.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                }
                case "game" -> game = value;
                case "zone" -> zone = Integer.parseInt(value);
                case "act" -> act = Integer.parseInt(value);
                case "team" -> team = value;
                case "validated" -> validated = Integer.parseInt(value);
                case "engine" -> engine = value;
                case "rom" -> rom = value;
                case "rom-sha1" -> romSha1 = value;
                case "waypoints" -> waypoints = value;
                case "countdown" -> countdown = Integer.parseInt(value);
                case "stand" -> {
                    String[] f = value.split("\\s+");
                    if (f.length < 4) throw new IllegalArgumentException("stand needs id, row, cleanup and pool: " + value);
                    List<Body> pool = new ArrayList<>();
                    for (int i = 3; i < f.length; i++) if (!f[i].equals("-")) pool.add(Body.parse(f[i]));
                    stands.add(new Stand(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), pool));
                }
                case "endpoint" -> {
                    String[] f = value.split("\\s+");
                    if (f.length != 2) throw new IllegalArgumentException("endpoint needs a kind and a row: " + value);
                    endpointKind = f[0];
                    endpointRow = Integer.parseInt(f[1]);
                }
                case "program" -> {
                    for (String pair : value.split(",")) {
                        String[] parts = pair.strip().split(":");
                        if (parts.length != 2) throw new IllegalArgumentException("Bad program run: " + pair);
                        frames.add(Integer.parseInt(parts[0].strip()));
                        masks.add(Integer.parseInt(parts[1].strip(), 16));
                    }
                }
                default -> throw new IllegalArgumentException("Unknown rail key: " + key);
            }
        }
        if (format != 2) throw new IllegalArgumentException("Unsupported rail format " + (format < 0 ? "(none)" : format)
                + ": only forward courses (format 2) are played");
        if (game == null || zone < 0 || act < 0) throw new IllegalArgumentException("Rail needs game, zone and act");
        int[] runFrames = frames.stream().mapToInt(Integer::intValue).toArray();
        int[] runMasks = masks.stream().mapToInt(Integer::intValue).toArray();
        int total = 0;
        for (int f : runFrames) total = Math.addExact(total, f);
        if (!Set.of("forward-v1", "forward-v2").contains(policy)) throw new IllegalArgumentException("Unsupported policy " + policy);
        if (!"live-restore-v1".equals(combat)) throw new IllegalArgumentException("Unsupported combat mode " + combat);
        if (!Set.of("regular", "boss-only", "pilot").contains(kind)) throw new IllegalArgumentException("Unknown course kind " + kind);
        if (!Set.of("native-spindash", "native-run-roll", "held-run").contains(launch)) throw new IllegalArgumentException("Unknown launch " + launch);
        // S1 _incObj/01 Sonic.asm: son_maxspeed = $600; held-run launches cannot exceed it.
        if (launch.equals("held-run") ? !game.equals("s1") || burst <= 0 || burst > 0x600 : burst != 0) {
            throw new IllegalArgumentException("Invalid launch burst " + burst);
        }
        if (romSha1 == null || romSha1.length() != 40) throw new IllegalArgumentException("A rail needs rom-sha1");
        if (endpointKind == null || !Set.of("boss-arena", "act-end", "pre-handover").contains(endpointKind)) {
            throw new IllegalArgumentException("A rail needs a boss-arena, act-end or pre-handover endpoint");
        }
        if (validated != total) throw new IllegalArgumentException("A rail validates its whole program");
        if (endpointRow < 0 || endpointRow > total) throw new IllegalArgumentException("Endpoint outside the program");
        Rail probe = new Rail(game, zone, act, team, validated, engine, rom, runFrames, runMasks, null);
        if (countdown < 0) throw new IllegalArgumentException("A rail needs a countdown row");
        if (countdown + COUNTDOWN_ROWS >= endpointRow) throw new IllegalArgumentException("Countdown runs past the endpoint");
        for (int i = 0; i < COUNTDOWN_ROWS; i++) {
            int m = probe.maskAt(countdown + i);
            int allowed = launch.equals("held-run") ? 0 : launch.equals("native-spindash") ? DOWN | JUMP : DOWN;
            if ((launch.equals("held-run") ? m != 0 : (m & DOWN) == 0) || (m & ~allowed) != 0) {
                throw new IllegalArgumentException("Countdown row " + (countdown + i) + " is " + Integer.toHexString(m)
                        + ", not held-run neutral/crouch" + (launch.equals("native-spindash") ? " or charge" : ""));
            }
        }
        int release = probe.maskAt(countdown + COUNTDOWN_ROWS);
        if ((release & RIGHT) == 0 || (release & DOWN) != 0) throw new IllegalArgumentException("The release row must hold right without down");
        int previousEnd = countdown + COUNTDOWN_ROWS;
        Set<Integer> ids = new HashSet<>();
        for (Stand stand : stands) {
            if (!ids.add(stand.id())) throw new IllegalArgumentException("Duplicate stand id " + stand.id());
            if (stand.cleanup() < 0 || stand.row() <= previousEnd) {
                throw new IllegalArgumentException("Stand " + stand.id() + " overlaps the countdown or the previous stand");
            }
            if (stand.row() + stand.cleanup() >= endpointRow) throw new IllegalArgumentException("Stand " + stand.id() + " past the endpoint");
            for (int i = 0; i < stand.cleanup(); i++) {
                if ((probe.maskAt(stand.row() + i) & ~DOWN) != 0) {
                    throw new IllegalArgumentException("Stand " + stand.id() + " cleanup row " + (stand.row() + i) + " moves");
                }
            }
            previousEnd = stand.row() + stand.cleanup();
        }
        Plan plan = new Plan(policy, combat, kind, launch, burst, profile, romSha1, countdown, stands,
                new Endpoint(endpointKind, endpointRow), waypoints);
        return new Rail(game, zone, act, team, validated, engine, rom, runFrames, runMasks, plan);
    }
}
