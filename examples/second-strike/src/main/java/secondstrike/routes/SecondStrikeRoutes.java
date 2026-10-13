package secondstrike.routes;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authored AIZ opening and fire-continuation economy over native ROM placements.
 *
 * <p>The host installs these lists before creating its native ring/object
 * managers. This class never wraps the native level: AIZ's fire handoff needs
 * that same concrete level for its ROM-backed art and collision overlays.
 * Rings retain their ROM record identity and position, so collection, checkpoint
 * respawn, stage return and rewind remain owned by the ordinary managers.
 *
 * <p>Survey: locked-on World CRC32 63522553; SpriteLocPtrs entry 0 points to
 * $1E44DA, RingLocPtrs entry 0 to $1E5A16. The physical Act 2 tables are
 * $1E4876/$1E5BBC. Native AIZ1BGE_Finish reloads physical Act 2 while keeping
 * Apparent_zone_and_act at AIZ1. Its real miniboss is owned by AIZ2_SonicResize2
 * / AIZ2_KnuxResize2; none of those owners or their placements are replaced.
 */
public final class SecondStrikeRoutes {
    private SecondStrikeRoutes() { }

    public enum Bank { FOREST, FIRE_CONTINUATION, UNCHANGED }

    /** A native route choice and the original ring record identities retained there. */
    public record RingGroup(String id, String decision, Set<Integer> ringIds) {
        public RingGroup {
            Objects.requireNonNull(id);
            Objects.requireNonNull(decision);
            ringIds = Set.copyOf(ringIds);
        }
    }

    /** Authoring intention; native traversal evidence is recorded separately. */
    public record Opportunity(String character, String route, String reward, String tradeoff) { }

    /** Binding request only. ModContext and the host supply verified factory ownership. */
    public record EnemyBinding(ObjectSpawn spawn, String localFactoryKey) { }

    private static RingGroup group(String id, String decision, Integer... ids) {
        return new RingGroup(id, decision, Set.of(ids));
    }

    private static List<RingGroup> forestGroups() {
        return List.of(
            group("opening", "Three rings before the first tell, then spaced slope crumbs; running costs protection",
                    1, 2, 3, 4, 6, 8, 9, 11, 12, 13, 14),
            group("upper-rock-ledge", "Leave the lower path to collect the native upper ledge bank",
                    15, 16, 17, 18, 20),
            group("lower-recovery", "A three-ring recovery foothold beyond the first physical post",
                    21, 22, 24),
            group("high-guard-approach", "Three rings lead onto the native guard and spotter plateau",
                    25, 26, 27),
            group("high-vine-bank", "Commit to the exposed long native upper vine line for a large bank",
                    28, 33, 36, 39, 40, 41, 42, 44, 47, 48, 49, 50, 51, 53, 58),
            group("low-vine-shortcut", "Take the faster lower vine branch for only three protection rings",
                    29, 35, 38),
            group("water-pocket", "Recover three rings in the native low pocket instead of committing to the upper line",
                    52, 55, 56),
            group("crown-approach", "Native elevated footholds lead toward the second physical post",
                    57, 59, 60, 61, 62, 63, 65),
            group("crown", "All seven crown rings reward reaching the native high platform",
                    71, 72, 73, 74, 75, 76, 77),
            group("crown-exit", "A short upper bank follows the crown; the native guard remains below it",
                    78, 79, 83),
            group("low-checkpoint-recovery", "The lower approach retains a three-ring recovery group",
                    87, 88, 89),
            group("fire-approach", "Five native slope rings leave a recoverable approach to the ordinary fire cutscene",
                    100, 101, 102, 103, 104));
    }

    private static List<RingGroup> fireGroups() {
        return List.of(
            group("fire-first-respite", "Three slope rings after the seamless reload's physical checkpoint",
                    1, 2, 7),
            group("fire-upper-columns", "Eight native column rings reward the upper approach beside the giant ring",
                    3, 4, 5, 6, 8, 9, 10, 11),
            group("fire-spring-ledge", "Three rings mark the native upper spring ledge",
                    12, 13, 14),
            group("fire-bridge-bank", "Four rings on the native collapsing bridge reward a dangerous lower shortcut",
                    15, 16, 17, 18),
            group("fire-ramp", "Three spaced ramp rings reward retained momentum",
                    19, 20, 22),
            group("fire-lower-guard", "The lower guard approach keeps three protection rings",
                    21, 23, 24),
            group("fire-upper-line", "The exposed upper line is sparse; use speed to cross it",
                    25, 27, 29),
            group("fire-loop-entrance", "Three rings preserve the readable native loop entrance",
                    30, 31, 33),
            group("fire-lower-pocket", "Four low rings offer a detour below the exposed artillery platform",
                    34, 35, 37, 38),
            group("fire-water-drop", "Three deep recovery rings reward the longer lower branch",
                    48, 49, 50),
            group("fire-last-low-pocket", "A two-ring foothold remains beyond the lower obstacles",
                    54, 55),
            group("fire-preboss-pocket", "Three native low rings reward keeping the lower approach alive",
                    58, 59, 60),
            group("fire-boss-approach", "Three final native slope rings protect the ordinary miniboss approach",
                    67, 68, 69));
    }

    private static Set<Integer> identities(List<RingGroup> groups) {
        return groups.stream().flatMap(group -> group.ringIds().stream()).collect(Collectors.toUnmodifiableSet());
    }

    /** Layout landmarks select authored content, never the player's position or route. */
    public static Bank bank(List<ObjectSpawn> objects) {
        Objects.requireNonNull(objects);
        if (contains(objects, 0x03, 0x2D00, 0x3CC)
                && contains(objects, 0x90, 0x3020, 0x2A0)) return Bank.FOREST;
        if (contains(objects, 0x85, 0x0240, 0x3D0)
                && contains(objects, 0x85, 0x19B8, 0x450)) return Bank.FIRE_CONTINUATION;
        return Bank.UNCHANGED;
    }

    private static boolean contains(List<ObjectSpawn> objects, int id, int x, int y) {
        return objects.stream().anyMatch(spawn -> spawn.objectId() == id && spawn.x() == x && spawn.y() == y);
    }

    /**
     * Keeps authored native groups without moving any ring. Later physical Act 2
     * rings and unfamiliar added identities pass through, allowing the ordinary
     * next act to remain playable. Applying the plan twice is harmless.
     */
    public static List<RingSpawn> rings(List<RingSpawn> source, List<ObjectSpawn> objects) {
        Objects.requireNonNull(source);
        Bank selected = bank(objects);
        Set<Integer> keep = identities(groups(selected));
        return switch (selected) {
            case FOREST -> source.stream().filter(ring -> ring.placementId() < 1
                    || ring.placementId() > 104 || keep.contains(ring.placementId())).toList();
            case FIRE_CONTINUATION -> source.stream().filter(ring -> ring.x() >= 0x1000
                    || ring.placementId() < 1 || ring.placementId() > 69
                    || keep.contains(ring.placementId())).toList();
            case UNCHANGED -> List.copyOf(source);
        };
    }

    public static List<RingGroup> groups(Bank bank) {
        return switch (Objects.requireNonNull(bank)) {
            case FOREST -> forestGroups();
            case FIRE_CONTINUATION -> fireGroups();
            case UNCHANGED -> List.of();
        };
    }

    /** Native anchor positions avoid inventing ground, removing routes, or moving scripted actors. */
    public static List<EnemyBinding> enemyBindings(List<ObjectSpawn> objects) {
        Bank bank = bank(objects);
        if (bank == Bank.UNCHANGED) return List.of();
        List<EnemyBinding> result = new ArrayList<>();
        for (ObjectSpawn spawn : objects) {
            if (spawn.ownerModId() != null || (bank == Bank.FIRE_CONTINUATION && spawn.x() >= 0x1000)) continue;
            String key = switch (spawn.objectId()) {
                case 0x8C -> "artillery";
                case 0x8D -> "guard";
                case 0x8E -> "spotter";
                default -> null;
            };
            if (key != null) result.add(new EnemyBinding(spawn, key));
        }
        return List.copyOf(result);
    }

    /** These are native capability opportunities, not assertions of certified route coverage. */
    public static List<Opportunity> opportunities() {
        return List.of(
                new Opportunity("sonic", "Native lower loop and slope below the $2200 ride vine",
                        "A quick lower crossing with three protection rings", "The richer fifteen-ring upper vine bank costs a detour and passes upper defenders"),
                new Opportunity("tails", "Native upper ledges and crown at $2754–$279C",
                        "Seven crown rings; existing elevated ten-ring monitors remain", "Flight avoids lower hazards but exposes the landing to upper defenders"),
                new Opportunity("knuckles", "Native rock interactions and lower fire-continuation route",
                        "Existing breakable passages, giant rings and lower recovery banks remain", "Lower detours give protection while preserving Knuckles' separate native boss arena"));
    }
}
