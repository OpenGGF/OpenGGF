package typinghog.rails;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import typinghog.play.TypingIds;

/**
 * One game's patch. Stock badniks stay in every level: removing them changes the recorded runs
 * the rails come from (rails kill test, 2026-10-09), and Sonic 3 &amp; Knuckles reuses badnik
 * object ids for bosses in other zones. The optional {@code badniks/<game>.txt} table
 * (hexadecimal object ids, {@code 0x..}, one per line) can still strip placements for an
 * experiment; the shipped mod leaves it empty, so levels load unchanged.
 */
public final class TypingPatch implements GamePatch {
    private final String game;
    private final Set<Integer> badnikIds;

    public TypingPatch(String game, String badnikTable) {
        this.game = game;
        this.badnikIds = parseIds(badnikTable);
    }

    static Set<Integer> parseIds(String table) {
        Set<Integer> ids = new HashSet<>();
        for (String raw : table.split("\n")) {
            String line = raw.strip();
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash).strip();
            if (!line.isEmpty()) ids.add(Integer.decode(line));
        }
        return Set.copyOf(ids);
    }

    @Override public String id() { return "rails-" + game; }
    @Override public String displayName() { return "Typing of the Hedgehog (" + game + ")"; }
    @Override public String baseGameId() { return game; }
    @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals(game); }
    @Override public Set<LogicalRom> romPrerequisites() {
        return Set.of(switch (game) {
            case "s1" -> LogicalRom.S1;
            case "s2" -> LogicalRom.S2;
            default -> LogicalRom.S3K;
        });
    }
    @Override public List<String> providedMainCharacters() { return List.of(); }

    @Override public GameModule apply(GameModule base, PatchContext context) {
        return new DelegatingGameModule(base, TypingIds.MOD + ":rails-" + game) {
            @Override public Level transformDecodedLevel(Level source) {
                return withoutBadniks(super.transformDecodedLevel(source), badnikIds);
            }
        };
    }

    /** The level without its stock badnik placements (unchanged when it has none). */
    static Level withoutBadniks(Level source, Set<Integer> badnikIds) {
        List<ObjectSpawn> kept = source.getObjects().stream()
                .filter(spawn -> spawn.objectKey() != null || !badnikIds.contains(spawn.objectId()))
                .toList();
        if (kept.size() == source.getObjects().size()) return source;
        MutableLevel level = MutableLevel.snapshot(source);
        level.replaceObjectSpawnsPersisted(kept);
        return level;
    }
}
