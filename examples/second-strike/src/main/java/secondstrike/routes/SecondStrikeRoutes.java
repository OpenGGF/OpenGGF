package secondstrike.routes;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.game.BonusStageType;
import com.openggf.level.rings.RingSpawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authored AIZ encounters and both apparent acts over original ROM placements.
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

    private static List<RingGroup> actTwoGroups() {
        return List.of(
                group("burnt-fork", "One original upper-fork ring rewards the exposed crossing", 105),
                group("orchard-catch", "One lower orchard ring protects the recoverable drop route", 126),
                group("false-floor", "One ring rewards crossing the bridge rhythm without camping", 210),
                group("ladder-upper", "One final upper-branch ring requires the committed climb", 250),
                group("ladder-lower", "The lower crossing retains its own final protection choice", 253));
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
     * forest and two physical-fire records. True Act 2 has a separate five
     * original-record budget. Extra records cannot bypass either budget.
     * Applying the plan twice is harmless; native stage prizes are separate.
     */
    public static List<RingSpawn> rings(List<RingSpawn> source, List<ObjectSpawn> objects) {
        Objects.requireNonNull(source);
        Bank selected = bank(objects);
        Set<Integer> keep = identities(groups(selected));
        return switch (selected) {
            case FOREST -> source.stream().filter(ring -> keep.contains(ring.placementId())).toList();
            case FIRE_CONTINUATION -> source.stream().filter(ring -> keep.contains(ring.placementId())).toList();
            case UNCHANGED -> List.copyOf(source);
        };
    }

    public static List<RingGroup> groups(Bank bank) {
        return switch (Objects.requireNonNull(bank)) {
            case FOREST -> forestGroups();
            case FIRE_CONTINUATION -> java.util.stream.Stream.concat(fireGroups().stream(), actTwoGroups().stream()).toList();
            case UNCHANGED -> List.of();
        };
    }

    public static boolean inOpening(Bank bank, ObjectSpawn spawn) {
        return bank != Bank.UNCHANGED && spawn.ownerModId() == null;
    }
    private record Binding(int objectId, int x, int y, String factory) {
        boolean matches(ObjectSpawn spawn) {
            return spawn.objectId() == objectId && spawn.x() == x && spawn.y() == y;
        }
    }
    private static Binding enemy(int id, int x, int y, String factory) {
        return new Binding(id, x, y, factory);
    }
    // Original ROM record identity plus data prevents injected or moved records
    // from accidentally joining an authored group. One ordinary Monkey Dude
    // remains as the visual reference near the crown.
    // Invocation-local recipes obey creator static-state admission and avoid a
    // retained mutable graph. The literal record switches are the authored data.
    private static Binding forestEnemy(int record) {
        return switch (record) {
            case 7 -> enemy(0x8E, 0x1838, 0x410, "spotter");
            case 28 -> enemy(0x8D, 0x1C40, 0x3C0, "guard");
            case 40 -> enemy(0x8C, 0x1DC0, 0x27C, "artillery");
            case 44 -> enemy(0x8E, 0x1E58, 0x490, "spotter");
            case 54 -> enemy(0x8D, 0x1FC8, 0x240, "guard");
            case 60 -> enemy(0x8E, 0x2058, 0x204, "spotter");
            case 82 -> enemy(0x8C, 0x2370, 0x37C, "artillery");
            case 98 -> enemy(0x8E, 0x26D8, 0x308, "spotter");
            case 107 -> enemy(0x8D, 0x2820, 0x320, "guard");
            default -> null;
        };
    }
    private static Binding fireEnemy(int record) {
        return switch (record) {
            case 78 -> enemy(0x8D, 0x0810, 0x4A0, "guard");
            case 82 -> enemy(0x8F, 0x0850, 0x328, "caterkiller");
            case 95 -> enemy(0x8C, 0x09F0, 0x3FC, "artillery");
            case 100 -> enemy(0x8F, 0x0AE0, 0x560, "caterkiller");
            case 119 -> enemy(0x8F, 0x0C90, 0x620, "caterkiller");
            case 138 -> enemy(0x8C, 0x0E10, 0x33C, "artillery");
            case 238 -> enemy(0x8D, 0x1AAC, 0x760, "guard");
            case 244 -> enemy(0x8F, 0x1B00, 0x580, "caterkiller");
            case 247 -> enemy(0x8C, 0x1B40, 0x1FB, "artillery");
            case 296 -> enemy(0x8C, 0x1E58, 0x6D8, "artillery");
            case 518 -> enemy(0x8C, 0x2D88, 0x31B, "artillery");
            case 625 -> enemy(0x8F, 0x33B0, 0x5A0, "caterkiller");
            case 631 -> enemy(0x8D, 0x3440, 0x1FF, "guard");
            case 634 -> enemy(0x8D, 0x3480, 0x5C0, "guard");
            case 704 -> enemy(0x8C, 0x3890, 0x6BC, "artillery");
            case 716 -> enemy(0x8D, 0x3B30, 0x65F, "guard");
            default -> null;
        };
    }

    /** One selector owns the actual registered decoded enemy bindings. */
    public static String enemyFactory(Bank bank, ObjectSpawn spawn) {
        if (!inOpening(bank, spawn)) return null;
        Binding binding = switch (bank) {
            case FOREST -> forestEnemy(spawn.layoutIndex());
            case FIRE_CONTINUATION -> fireEnemy(spawn.layoutIndex());
            case UNCHANGED -> null;
        };
        return binding != null && binding.matches(spawn) ? binding.factory() : null;
    }

    /** Reaching these marked posts earns an opportunity, not an all-enemies-killed claim. */
    public static BonusStageType invitation(Bank bank, ObjectSpawn spawn) {
        if (spawn.ownerModId() != null || spawn.objectId() != 0x34) return BonusStageType.NONE;
        if (bank == Bank.FOREST && spawn.layoutIndex() == 102 && spawn.x() == 0x2778 && spawn.y() == 0x25C)
            return BonusStageType.GUMBALL;
        if (bank == Bank.FIRE_CONTINUATION && spawn.layoutIndex() == 476 && spawn.x() == 0x2A80 && spawn.y() == 0x57A)
            return BonusStageType.GLOWING_SPHERE;
        return BonusStageType.NONE;
    }

    /** Keep the upper ladder and final crown log stable; only the lower crossing commits. */
    public static boolean brittleLog(Bank bank, ObjectSpawn spawn) {
        if (bank != Bank.FIRE_CONTINUATION || spawn.ownerModId() != null || spawn.objectId() != 0x2C) return false;
        return switch (spawn.layoutIndex()) {
            case 66 -> spawn.x() == 0x06E6 && spawn.y() == 0x4F8;
            case 243 -> spawn.x() == 0x1AE5 && spawn.y() == 0x4F8;
            case 608 -> spawn.x() == 0x32E6 && spawn.y() == 0x758;
            default -> false;
        };
    }

    /** Authored gap fan locations; the remaining plants teach the wide fan. */
    public static boolean splitFan(Bank bank, ObjectSpawn spawn) {
        return "artillery".equals(enemyFactory(bank, spawn))
                && (bank == Bank.FOREST && spawn.layoutIndex() == 40
                    || bank == Bank.FIRE_CONTINUATION && java.util.Set.of(95, 247, 296, 518, 704).contains(spawn.layoutIndex()));
    }

    /** These are native capability opportunities, not assertions of certified route coverage. */
    public static List<Opportunity> opportunities() {
        return List.of(
                new Opportunity("sonic", "Native lower loop and slope below the $2200 ride vine",
                        "One lower-branch protection ring", "The high crown offers one separate ring but costs a detour past upper defenders"),
                new Opportunity("tails", "Native upper ledges and crown at $2754–$279C",
                        "One crown protection ring", "Flight avoids lower hazards but exposes the landing to upper defenders and native Robotnik boxes"),
                new Opportunity("knuckles", "Native rock interactions and lower fire-continuation route",
                        "Existing breakable passages and giant rings remain", "Lower detours preserve Knuckles' separate native boss arena and lower final-ring decision"));
    }
}
