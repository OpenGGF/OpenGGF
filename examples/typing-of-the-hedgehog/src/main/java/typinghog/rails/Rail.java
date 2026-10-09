package typinghog.rails;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One act's rail: the pad program that drives Sonic from the act's cold start, how far it has
 * been validated, and the rows where stands may stop him. Read from {@code rails/*.rail} text:
 *
 * <pre>
 * game s1
 * zone 0
 * act 2
 * team sonic
 * validated 5412
 * engine 0.7.prerelease+8668a9012
 * rom AFE05EEE
 * program 120:0,34:8,12:48,...
 * stand 1400
 * boss 5300
 * </pre>
 *
 * {@code program} is run-length {@code frames:hexMask} pairs in {@code AbstractPlayableSprite}
 * input bits (Start is never part of a rail). Rows past {@code validated} are never played; the
 * act's boss stand sits at or before it. {@code engine}/{@code rom} name what the program was
 * validated against.
 */
public record Rail(String game, int zone, int act, String team, int validated, String engine, String rom,
                   int[] runFrames, int[] runMasks, List<Integer> stands, int bossRow) {
    public Rail {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(team, "team");
        if (runFrames.length != runMasks.length) throw new IllegalArgumentException("Unpaired program runs");
        stands = List.copyOf(stands);
        int length = 0;
        for (int frames : runFrames) {
            if (frames <= 0) throw new IllegalArgumentException("Program run must be positive");
            length += frames;
        }
        if (validated < 0 || validated > length) throw new IllegalArgumentException("Validated past the program");
        if (bossRow < 0 || bossRow > validated) throw new IllegalArgumentException("Boss stand past validation");
        int previous = -1;
        for (int stand : stands) {
            if (stand <= previous || stand >= bossRow) throw new IllegalArgumentException("Stands must ascend before the boss");
            previous = stand;
        }
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
        int zone = -1, act = -1, validated = -1, boss = -1;
        List<Integer> frames = new ArrayList<>(), masks = new ArrayList<>(), stands = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            String value = space < 0 ? "" : line.substring(space + 1).strip();
            switch (key) {
                case "game" -> game = value;
                case "zone" -> zone = Integer.parseInt(value);
                case "act" -> act = Integer.parseInt(value);
                case "team" -> team = value;
                case "validated" -> validated = Integer.parseInt(value);
                case "engine" -> engine = value;
                case "rom" -> rom = value;
                case "stand" -> stands.add(Integer.parseInt(value));
                case "boss" -> boss = Integer.parseInt(value);
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
        if (game == null || zone < 0 || act < 0) throw new IllegalArgumentException("Rail needs game, zone and act");
        int total = frames.stream().mapToInt(Integer::intValue).sum();
        if (validated < 0) validated = total;
        if (boss < 0) boss = validated;
        return new Rail(game, zone, act, team, validated, engine, rom,
                frames.stream().mapToInt(Integer::intValue).toArray(),
                masks.stream().mapToInt(Integer::intValue).toArray(), stands, boss);
    }
}
