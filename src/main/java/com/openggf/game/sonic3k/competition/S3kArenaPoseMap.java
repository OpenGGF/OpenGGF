package com.openggf.game.sonic3k.competition;

import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Presentation-only map from a Competition miniature pose to the full-size main-game pose
 * of the same character. Both sides are the ROM's own animation tables: the miniature
 * scripts (AniSonic2P/AniKnuckles2P/AniTails2P) and the main scripts (AniSonic,
 * AniTails, AniKnuckles) share animation ids, so each miniature script step is paired
 * with the main script step that the same animation shows at that point.
 *
 * <p>Walk and run frames carry the slope angle. Both animators quantise it identically
 * (angle+$10, groups of $20, bit 7 selecting the 180 degree flips), and add a group
 * stride to the script byte: the miniature adds 3 (walk) or 2 (run) per group
 * (Animate_SonicKnuckles_2P loc_12E38/loc_12E4E, sonic3k.asm:25423-25443; Animate_Tails2P
 * loc_15DC6, 29830-29838), the main animators 8 or 4 (Animate_Sonic loc_1270A/loc_12724,
 * 24886-24899; Animate_Tails loc_15960, 29510-29521). The group is therefore recovered
 * from the miniature frame alone, and ghosts keep the angle. Tumbles likewise divide the
 * flip angle by $2C from frame $15 (loc_12E8E, 25460-25473) against $16 from frame $31
 * (Anim_Tumble/loc_1280A, 24968-24998): miniature tumble frame f is main frame $31+2f.
 *
 * <p>Residual ambiguity, documented rather than guessed: a streamed ghost frame carries
 * no animation id, so a miniature frame shared by several animations takes the first
 * Competition animation in {@link #PREFERENCE} (frame $15: spring, not the upright tumble;
 * the waiting frames: WAIT, not LOOK_UP or BALANCE, which reuse the waiting script in the
 * miniature table). Miniature scripts that are shorter than the main ones show a subset
 * of the main frames (walk shows four of eight steps). Tails' main animator has a third,
 * faster run tier at $700 (AniTails1F); the miniature has none, so the run frames map to
 * AniTails01.
 */
public final class S3kArenaPoseMap {
    static final int MINI_WALK_STRIDE = 3, MINI_RUN_STRIDE = 2, FULL_WALK_STRIDE = 8, FULL_RUN_STRIDE = 4;
    static final int MINI_TUMBLE_BASE = 0x15, MINI_TUMBLE_FRAMES = 6, FULL_TUMBLE_BASE = 0x31;
    static final int WALK = 0, RUN = 1, WAIT = 5, SPINDASH = 9;
    /** Animations a Competition actor actually selects, most common first; the rest follow by id. */
    static final List<Integer> PREFERENCE = List.of(WALK, RUN, 2, 3, 4, 9, 0xD, 0x10, 0x1A, 0x18, 8, WAIT, 7, 0x20);

    private record Table(int[] byFrame, Map<Integer, Map<Integer, Integer>> byAnimation, int[] unmapped,
                         int[] groups, boolean[] balls) { }
    private final Map<S3kCompetitionPlayer.Character, Table> tables = new EnumMap<>(S3kCompetitionPlayer.Character.class);

    /**
     * @param miniFrameCounts miniature mapping-frame counts (Map_Sonic2P and its siblings)
     * @param full            main animation scripts per character, decoded from the ROM
     */
    public S3kArenaPoseMap(S3kMiniatureAnimation.Bank mini,
                           Map<S3kCompetitionPlayer.Character, SpriteAnimationSet> full,
                           Map<S3kCompetitionPlayer.Character, Integer> miniFrameCounts) {
        Objects.requireNonNull(mini);
        for (var character : S3kCompetitionPlayer.Character.values()) {
            var scripts = Objects.requireNonNull(full.get(character), "Missing main animation scripts");
            tables.put(character, build(mini, character, scripts, miniFrameCounts.get(character)));
        }
    }

    /** Full-size frame for a streamed miniature frame, as ghosts carry it. */
    public int frame(S3kCompetitionPlayer.Character character, int miniFrame) {
        int[] table = tables.get(character).byFrame();
        return miniFrame >= 0 && miniFrame < table.length ? table[miniFrame] : table[0];
    }

    /**
     * Full-size frame for the local actor, whose animation id and tumble state are known.
     * The animation resolves frames that several miniature animations share.
     */
    public int frame(S3kCompetitionPlayer.Character character, int animation, boolean tumbling, int miniFrame) {
        int tumble = miniFrame - MINI_TUMBLE_BASE;
        if (tumbling && tumble >= 0 && tumble < MINI_TUMBLE_FRAMES) return FULL_TUMBLE_BASE + 2 * tumble;
        var steps = tables.get(character).byAnimation().get(animation);
        Integer exact = steps == null ? null : steps.get(miniFrame);
        return exact != null ? exact : frame(character, miniFrame);
    }

    /** Slope-angle group (0-3) a walk/run frame encodes, or -1 for frames without one. */
    public int angleGroup(S3kCompetitionPlayer.Character character, int miniFrame) {
        int[] groups = tables.get(character).groups();
        return miniFrame >= 0 && miniFrame < groups.length ? groups[miniFrame] : -1;
    }

    /** Whether a miniature frame belongs to the roll or spin dash scripts (a curled body). */
    public boolean ball(S3kCompetitionPlayer.Character character, int miniFrame) {
        boolean[] balls = tables.get(character).balls();
        return miniFrame >= 0 && miniFrame < balls.length && balls[miniFrame];
    }

    /** Miniature frames that no ROM script or tumble reaches; they fall back to the standing pose. */
    public int[] unmappedFrames(S3kCompetitionPlayer.Character character) {
        return tables.get(character).unmapped().clone();
    }

    private static Table build(S3kMiniatureAnimation.Bank mini, S3kCompetitionPlayer.Character character,
                               SpriteAnimationSet full, int miniFrameCount) {
        var byAnimation = new HashMap<Integer, Map<Integer, Integer>>();
        int count = mini.scriptCount(character);
        for (int animation = 0; animation < count; animation++) {
            SpriteAnimationScript main = full.getScript(animation);
            if (main == null || main.frames().isEmpty()) continue;
            int[] steps = miniSteps(mini.rawScript(character, animation), character);
            var pairs = new HashMap<Integer, Integer>();
            if (animation == WALK || animation == RUN) {
                // Both speeds share the $FF handler; anim 0 shows run frames above $600.
                angled(pairs, miniSteps(mini.rawScript(character, WALK), character), full.getScript(WALK),
                        MINI_WALK_STRIDE, FULL_WALK_STRIDE);
                angled(pairs, miniSteps(mini.rawScript(character, RUN), character), full.getScript(RUN),
                        MINI_RUN_STRIDE, FULL_RUN_STRIDE);
            } else if (steps.length > 0) {
                int[] mainSteps = main.frames().stream().mapToInt(Integer::intValue).toArray();
                for (int step = 0; step < steps.length; step++)
                    pairs.putIfAbsent(steps[step], mainSteps[align(step, steps, mainSteps)]);
            }
            if (!pairs.isEmpty()) byAnimation.put(animation, Map.copyOf(pairs));
        }
        int[] byFrame = new int[miniFrameCount];
        Arrays.fill(byFrame, -1);
        var order = new java.util.ArrayList<>(PREFERENCE);
        for (int animation = 0; animation < count; animation++) if (!order.contains(animation)) order.add(animation);
        for (int animation : order) {
            var pairs = byAnimation.get(animation);
            if (pairs == null) continue;
            pairs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(pair -> {
                if (pair.getKey() < byFrame.length && byFrame[pair.getKey()] < 0) byFrame[pair.getKey()] = pair.getValue();
            });
        }
        for (int tumble = 0; tumble < MINI_TUMBLE_FRAMES; tumble++) {
            int frame = MINI_TUMBLE_BASE + tumble;
            if (frame < byFrame.length && byFrame[frame] < 0) byFrame[frame] = FULL_TUMBLE_BASE + 2 * tumble;
        }
        // The first waiting step is the standing pose the main WAIT script also opens with.
        int standing = byAnimation.get(WAIT).get(miniSteps(mini.rawScript(character, WAIT), character)[0]);
        int[] unmapped = java.util.stream.IntStream.range(0, byFrame.length).filter(f -> byFrame[f] < 0).toArray();
        for (int frame : unmapped) byFrame[frame] = standing;
        int[] groups = new int[byFrame.length];
        Arrays.fill(groups, -1);
        int[][] strides = {{WALK, MINI_WALK_STRIDE}, {RUN, MINI_RUN_STRIDE}};
        for (int[] stride : strides)
            for (int step : miniSteps(mini.rawScript(character, stride[0]), character))
                for (int group = 0; group < 4; group++)
                    if (step + group * stride[1] < groups.length) groups[step + group * stride[1]] = group;
        boolean[] balls = new boolean[byFrame.length];
        for (int animation : new int[] {2, 3, SPINDASH})
            for (int step : miniSteps(mini.rawScript(character, animation), character))
                if (step < balls.length) balls[step] = true;
        return new Table(byFrame, Map.copyOf(byAnimation), unmapped, groups, balls);
    }

    /** Walk/run: script byte plus the angle-group stride, for each of the four groups. */
    private static void angled(Map<Integer, Integer> pairs, int[] miniSteps, SpriteAnimationScript main,
                               int miniStride, int fullStride) {
        int[] mainSteps = main.frames().stream().mapToInt(Integer::intValue).toArray();
        for (int group = 0; group < 4; group++)
            for (int step = 0; step < miniSteps.length; step++)
                pairs.putIfAbsent(miniSteps[step] + group * miniStride,
                        mainSteps[align(step, miniSteps, mainSteps)] + group * fullStride);
    }

    /**
     * Pairs miniature step {@code step} with the main step at the same point of the
     * script. When the scripts differ in length, a frame the miniature repeats (the
     * roll's ball) pairs with a frame the main script repeats, and a unique pose with a
     * unique pose, so the two cycles stay in phase.
     */
    static int align(int step, int[] mini, int[] main) {
        if (mini.length == main.length) return step;
        int base = step * main.length / mini.length;
        boolean repeated = repeated(mini, mini[step]);
        if (base + 1 < main.length && repeated(main, main[base]) != repeated
                && repeated(main, main[base + 1]) == repeated) return base + 1;
        return base;
    }

    private static boolean repeated(int[] steps, int value) {
        return Arrays.stream(steps).filter(step -> step == value).count() > 1;
    }

    /** Script frame bytes after the delay byte, up to the animator's command threshold. */
    private static int[] miniSteps(byte[] script, S3kCompetitionPlayer.Character character) {
        // Animate_Tails2P treats bytes >= $F0 as commands; the Sonic/Knuckles animator >= $FC.
        int limit = character == S3kCompetitionPlayer.Character.TAILS ? 0xF0 : 0xFC;
        int end = 1;
        while (end < script.length && (script[end] & 255) < limit) end++;
        int[] steps = new int[end - 1];
        for (int i = 1; i < end; i++) steps[i - 1] = script[i] & 255;
        return steps;
    }
}
