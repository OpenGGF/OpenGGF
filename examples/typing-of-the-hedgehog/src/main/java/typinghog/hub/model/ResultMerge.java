package typinghog.hub.model;

import typinghog.save.ActResult;
import typinghog.save.LaunchIntent;

/**
 * Folds a returned {@link ActResult} into the {@link Profile}: records, story progress,
 * emeralds, Boss Rush stars, lifetime totals and per-key statistics. Engine-free.
 */
public final class ResultMerge {
    private ResultMerge() { }

    /**
     * What merging changed, for the results screen.
     *
     * @param place      the result's 1-based place in its table, 0 when it did not place
     * @param previousBest the table's best score before this result (0 when empty)
     * @param firstClear true on a first story clear (the next stop opens)
     * @param newEmerald true when this clear won the act's emerald
     * @param stars      the boss's star level after a Boss Rush result (0 otherwise)
     */
    public record Outcome(ActResult result, LaunchIntent.Difficulty difficulty, String table, int place,
                          long previousBest, boolean firstClear, boolean newEmerald, int stars) { }

    /**
     * Merges {@code result}. {@code launched} is the hub's copy of the intent it started the
     * act with (null when unknown, for example after a crash); its difficulty files the
     * record when it names the same act, otherwise the profile's current difficulty does.
     */
    public static Outcome merge(Profile p, ActResult result, LaunchIntent launched) {
        LaunchIntent.Difficulty difficulty = launched != null && launched.game().equals(result.game())
                && launched.zone() == result.zone() && launched.act() == result.act()
                ? launched.difficulty() : p.difficulty();
        String game = result.game();
        int zone = result.zone(), act = result.act();

        KeyTally hits = new KeyTally().addEncoded(result.keyHits());
        KeyTally misses = new KeyTally().addEncoded(result.keyMisses());
        p.keyHits().addAll(hits);
        p.keyMisses().addAll(misses);
        long chars = hits.total();
        double minutes = result.wpm() > 0 ? chars / 5.0 / result.wpm() : 0;
        p.addTyping(chars, misses.total(), result.kills(), minutes);
        p.countAct(result.cleared());

        boolean firstClear = false, newEmerald = false;
        int stars = 0;
        String table = switch (result.mode()) {
            case ENDLESS -> Profile.endlessTable(game, zone, act, difficulty);
            case BOSS_RUSH -> null;
            default -> Profile.actTable(game, zone, act, difficulty);
        };
        if (result.mode() == LaunchIntent.Mode.STORY && result.cleared()) {
            firstClear = p.clear(game, zone, act);
            if (result.emerald()) newEmerald = p.awardEmerald(game, zone, act);
        }
        if (result.mode() == LaunchIntent.Mode.BOSS_RUSH) {
            stars = result.cleared() ? p.raiseStars(game, zone, act) : p.stars(game, zone, act);
        }
        int place = 0;
        long previous = 0;
        boolean filed = table != null && (result.cleared() || result.mode() == LaunchIntent.Mode.ENDLESS);
        if (table != null) {
            var list = p.records(table);
            previous = list.isEmpty() ? 0 : list.getFirst().score();
            if (filed) place = p.file(table, new Profile.Entry(result.score(), result.grade(), result.wpm()));
        }
        return new Outcome(result, difficulty, table, place, previous, firstClear, newEmerald, stars);
    }
}
