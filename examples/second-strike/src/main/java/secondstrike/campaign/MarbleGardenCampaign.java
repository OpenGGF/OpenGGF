package secondstrike.campaign;

import com.openggf.level.LevelPatch;
import com.openggf.mods.objects.CollapsingBridgeFactory;
import com.openggf.mods.objects.MarbleGardenFactory;
import com.openggf.game.sonic3k.difficulty.CollapsingBridgeRecipe;
import com.openggf.game.sonic3k.difficulty.MarbleGardenEnemyRecipe;
import com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.code.ModContext;

import java.util.List;
import java.util.Set;

/**
 * MGZ's upper/perch and lower/recovery decisions over the original native level.
 * SpriteLocPtrs: $1E8088/$1E932C; RingLocPtrs: $1E8B38/$1E9B36.
 * Tops retain native momentum; the Act 1 handoff and both Act 2 boss branches
 * remain owned by their original native event graphs.
 */
public final class MarbleGardenCampaign {
    private MarbleGardenCampaign() { }

    public static void register(ModContext context) {
        context.registerObject("mgz-brittle", (spawn, registry) -> CollapsingBridgeFactory.create(spawn, CollapsingBridgeRecipe.BRITTLE));
        context.registerObject("mgz-spiker", (spawn, registry) -> MarbleGardenFactory.spiker(spawn, MarbleGardenEnemyRecipe.LANDING_GUARD));
        context.registerObject("mgz-mantis", (spawn, registry) -> MarbleGardenFactory.mantis(spawn, MarbleGardenEnemyRecipe.LANDING_GUARD));
        context.registerObject("mgz-miniboss", (spawn, registry) -> MarbleGardenFactory.miniboss(spawn, MarbleGardenBossRecipe.COUNTER_CADENCE));
        context.registerService("mgz-encounter", MarbleGardenBossRecipe.class, MarbleGardenBossRecipe.COUNTER_CADENCE);
        context.decodedLevelPatch("mgz-one", plan(0));
        context.decodedLevelPatch("mgz-two", plan(1));
    }

    private static LevelPatch plan(int act) {
        return LevelPatch.empty().nativePlacements().forZone(2)
                .whenInventory(objects -> bank(objects) == act)
                .select(s -> s.ownerModId() == null && s.objectId() == 0x0F && brittle(act, s)).bind("mgz-brittle")
                .select(s -> s.ownerModId() == null && s.objectId() == 0x9C && spiker(act, s)).bind("mgz-spiker")
                .select(s -> s.ownerModId() == null && s.objectId() == 0x9D && mantis(s)).bind("mgz-mantis")
                .select(s -> s.ownerModId() == null && s.objectId() == 0x9F).bind("mgz-miniboss")
                .select(s -> s.ownerModId() == null && s.objectId() == 1 && (s.subtype() & 15) != 4).bind("robotnik-monitor")
                .retainRings((rings, objects) -> rings(rings, objects));
    }

    private static boolean at(ObjectSpawn s, int x, int y) { return s.x() == x && s.y() == y; }
    private static boolean brittle(int act, ObjectSpawn s) {
        return act == 0 ? at(s, 0x04F0, 0x834) || at(s, 0x0B80, 0x960)
                || at(s, 0x1280, 0x424) || at(s, 0x1CF0, 0xAA0) || at(s, 0x26AF, 0x3A9)
                : at(s, 0x0510, 0x3B8) || at(s, 0x1900, 0x340)
                || at(s, 0x2100, 0x5E0) || at(s, 0x2800, 0x700) || at(s, 0x3000, 0x3A0);
    }
    private static boolean spiker(int act, ObjectSpawn s) {
        return act == 0 ? at(s, 0x0B18, 0xC64) || at(s, 0x1120, 0x5E4)
                || at(s, 0x1D00, 0xB24) || at(s, 0x2410, 0x1C3)
                : at(s, 0x0A50, 0x608) || at(s, 0x1120, 0x424)
                || at(s, 0x1DF0, 0x7C8) || at(s, 0x29B0, 0x3C8);
    }
    private static boolean mantis(ObjectSpawn s) {
        return at(s, 0x0F20, 0x398) || at(s, 0x0F60, 0x398)
                || at(s, 0x20F0, 0x138) || at(s, 0x2700, 0x838)
                || at(s, 0x3C10, 0x8E0) || at(s, 0x3C40, 0x8E0);
    }

    public static int bank(List<ObjectSpawn> objects) {
        if (contains(objects, 0x9F, 0x2F00, 0xE60)
                && contains(objects, 0x5C, 0x2970, 0x838)) return 0;
        if (contains(objects, 0xA2, 0x3E78, 0)
                && contains(objects, 0x5C, 0x2A60, 0x178)) return 1;
        return -1;
    }

    private static boolean contains(List<ObjectSpawn> objects, int id, int x, int y) {
        return objects.stream().anyMatch(s -> s.objectId() == id && s.x() == x && s.y() == y);
    }

    public static List<RingSpawn> rings(List<RingSpawn> source, List<ObjectSpawn> objects) {
        Set<Integer> keep = switch (bank(objects)) {
            case 0 -> Set.of(1, 85, 169, 337, 505);
            case 1 -> Set.of(1, 88, 146, 233, 349);
            default -> null;
        };
        return keep == null ? List.copyOf(source)
                : source.stream().filter(ring -> keep.contains(ring.placementId())).toList();
    }
}
