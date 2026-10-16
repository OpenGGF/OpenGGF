package secondstrike.campaign;

import com.openggf.level.LevelPatch;
import com.openggf.mods.objects.CollapsingBridgeFactory;
import com.openggf.mods.objects.HydrocityFactory;
import com.openggf.game.sonic3k.difficulty.CollapsingBridgeRecipe;
import com.openggf.game.sonic3k.difficulty.HydrocityEnemyRecipe;
import com.openggf.game.sonic3k.difficulty.HydrocityBossRecipe;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.code.ModContext;

import java.util.List;
import java.util.Set;

/**
 * HCZ's authored water/air decisions, selected from the locked-on World ROM.
 * SpriteLocPtrs: $1E6090/$1E6990; RingLocPtrs: $1E758A/$1E7C70.
 * Bubble generators, launchers, checkpoints, water and character arenas retain
 * their original records. A ring is protection, never a substitute for air.
 */
public final class HydrocityCampaign {
    private HydrocityCampaign() { }

    public static void register(ModContext context) {
        context.registerObject("hcz-brittle", (spawn, registry) -> CollapsingBridgeFactory.create(spawn, CollapsingBridgeRecipe.BRITTLE));
        context.registerObject("hcz-alert", (spawn, registry) -> HydrocityFactory.turboSpiker(spawn, HydrocityEnemyRecipe.ALERT));
        context.registerObject("hcz-miniboss", (spawn, registry) -> HydrocityFactory.miniboss(spawn, HydrocityBossRecipe.COUNTER_CADENCE));
        context.registerObject("hcz-endboss", (spawn, registry) -> HydrocityFactory.endBoss(spawn, HydrocityBossRecipe.COUNTER_CADENCE));
        context.decodedLevelPatch("hcz-one", plan(0));
        context.decodedLevelPatch("hcz-two", plan(1));
    }

    private static LevelPatch plan(int act) {
        return LevelPatch.empty().nativePlacements().forZone(1)
                .whenInventory(objects -> bank(objects) == act)
                .select(s -> s.ownerModId() == null && s.objectId() == 0x0F && act == 0
                        && (at(s, 0x05C0, 0x110) || at(s, 0x0AC0, 0x110))).bind("hcz-brittle")
                .select(s -> s.ownerModId() == null && s.objectId() == 0x96 && alert(act, s)).bind("hcz-alert")
                .select(s -> s.ownerModId() == null && s.objectId() == 0x99).bind("hcz-miniboss")
                .select(s -> s.ownerModId() == null && s.objectId() == 0x9A).bind("hcz-endboss")
                .select(s -> s.ownerModId() == null && s.objectId() == 1 && (s.subtype() & 15) != 4).bind("robotnik-monitor")
                .retainRings((rings, objects) -> rings(rings, objects));
    }

    private static boolean at(ObjectSpawn s, int x, int y) { return s.x() == x && s.y() == y; }

    /** Defend the native dry ledge, water exit and return transfer; no bubble record is rebound. */
    private static boolean alert(int act, ObjectSpawn s) {
        return act == 0 ? at(s, 0x1210, 0x0F0) || at(s, 0x2780, 0x870)
                || at(s, 0x29D0, 0x570) || at(s, 0x30E0, 0x270)
                : at(s, 0x0CC0, 0x0D0) || at(s, 0x1900, 0xAC2)
                || at(s, 0x1CB0, 0x4F0) || at(s, 0x1F48, 0x770)
                || at(s, 0x24CC, 0x2F0) || at(s, 0x3300, 0x4B0)
                || at(s, 0x3520, 0xAC2) || at(s, 0x3BB0, 0x670);
    }

    /** Native inventory selection; never inferred from a player's position. */
    public static int bank(List<ObjectSpawn> objects) {
        if (contains(objects, 0x99, 0x3720, 0x580)
                && contains(objects, 0x54, 0x1130, 0x819)) return 0;
        if (contains(objects, 0x9A, 0x40C8, 0x6E0)
                && contains(objects, 0x9A, 0x4608, 0x268)) return 1;
        return -1;
    }

    private static boolean contains(List<ObjectSpawn> objects, int id, int x, int y) {
        return objects.stream().anyMatch(s -> s.objectId() == id && s.x() == x && s.y() == y);
    }

    /** Exact original ring identities: upper/dry and lower/wet choices share one act budget. */
    public static List<RingSpawn> rings(List<RingSpawn> source, List<ObjectSpawn> objects) {
        Set<Integer> keep = switch (bank(objects)) {
            case 0 -> Set.of(1, 109, 145, 217, 433);
            case 1 -> Set.of(1, 64, 127, 232, 253);
            default -> null;
        };
        return keep == null ? List.copyOf(source)
                : source.stream().filter(ring -> keep.contains(ring.placementId())).toList();
    }
}
