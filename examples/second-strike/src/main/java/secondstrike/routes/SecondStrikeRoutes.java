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

    private static RingGroup group(String id, String decision, Integer... ids) {
        return new RingGroup(id, decision, Set.of(ids));
    }

    private static List<RingGroup> forestGroups() {
        return List.of(
                group("opening", "One native opening ring protects the first committed enemy encounter", 1),
                group("lower-vine", "One ring rewards taking the native lower vine branch", 35),
                group("high-crown", "One elevated crown ring rewards committing to the exposed upper platform", 74));
    }

    private static List<RingGroup> fireGroups() {
        return List.of(
                group("fire-spring-ledge", "One ring rewards reaching the native upper spring ledge after the fire handoff", 12),
                group("fire-boss-approach", "One final native slope ring protects the miniboss approach", 68));
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
     * Keeps five original ring records across the advertised first act: three
     * forest and two physical-fire records. Later physical Act 2 rings pass
     * through unchanged. Extra opening identities cannot bypass this budget.
     * Applying the plan twice is harmless; native stage prizes are separate.
     */
    public static List<RingSpawn> rings(List<RingSpawn> source, List<ObjectSpawn> objects) {
        Objects.requireNonNull(source);
        Bank selected = bank(objects);
        Set<Integer> keep = identities(groups(selected));
        return switch (selected) {
            case FOREST -> source.stream().filter(ring -> keep.contains(ring.placementId())).toList();
            case FIRE_CONTINUATION -> source.stream().filter(ring -> ring.x() >= 0x1000
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

    public static boolean inOpening(Bank bank, ObjectSpawn spawn) {
        return bank != Bank.UNCHANGED && spawn.ownerModId() == null
                && (bank == Bank.FOREST || spawn.x() < 0x1000);
    }
    /** One selector owns the actual registered decoded enemy bindings. */
    public static String enemyFactory(Bank bank, ObjectSpawn spawn) {
        if (!inOpening(bank, spawn)) return null;
        return switch (spawn.objectId()) {
            case 0x8C -> "artillery";
            case 0x8D -> "guard";
            case 0x8E -> "spotter";
            case 0x8F -> "caterkiller";
            default -> null;
        };
    }

    /** These are native capability opportunities, not assertions of certified route coverage. */
    public static List<Opportunity> opportunities() {
        return List.of(
                new Opportunity("sonic", "Native lower loop and slope below the $2200 ride vine",
                        "One lower-branch protection ring", "The high crown offers one separate ring but costs a detour past upper defenders"),
                new Opportunity("tails", "Native upper ledges and crown at $2754–$279C",
                        "One crown protection ring", "Flight avoids lower hazards but exposes the landing to upper defenders and native Robotnik boxes"),
                new Opportunity("knuckles", "Native rock interactions and lower fire-continuation route",
                        "Existing breakable passages and giant rings remain", "Lower detours preserve Knuckles' separate native boss arena; only two placed rings remain in the fire continuation"));
    }
}
