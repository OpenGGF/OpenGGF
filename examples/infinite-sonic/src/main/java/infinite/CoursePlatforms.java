package infinite;

import com.openggf.level.objects.ObjectSpawn;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Stock S1 platform objects an act lends the course, chosen from that act's own placement so
 * the zone has loaded their art. The course spawns the shipped objects themselves: behavior,
 * art, solidity and riding come from the engine's ROM-backed implementations. Which subtypes
 * appear is a mod design choice: only platforms that hold still until Sonic arrives, so the
 * scrolling edge never makes him wait for one.
 * (Mod classes may not hold static state, so this is constants and switches rather than tables.)
 */
public final class CoursePlatforms {
    /** Obj18 Platforms (GHZ, SYZ, SLZ). */
    static final int PLATFORM = 0x18;
    /** Obj52 Moving Blocks (MZ, LZ, SBZ). */
    static final int MOVING_BLOCK = 0x52;
    /** Obj59 SLZ Elevators: subtype 0 is Elev_Var1 width $28 with action 1, which waits for Sonic,
     * then rises $10 units and stops (type 2), as the stock elevator does. */
    static final int ELEVATOR = 0x59;
    /** Obj18 type $03: stationary until stood on for 30 frames, then falls. */
    static final int PLATFORM_FALLS = 0x03;
    /** Both objects stand Sonic on obY-9 (MvSonicOnPtfm2), so a centre sits 9px below the surface. */
    public static final int SURFACE_OFFSET = 9;
    private static final int SBZ = 5;
    /** Narrowest stepping stone: 64px, the Obj18 platform. Mod design. */
    static final int MIN_HALF_WIDTH = 0x20;

    /** @param subtype the stock subtype spawned; {@code falls} marks Obj18 type $03 */
    public record Kind(int objectId, int subtype, int halfWidth, boolean falls) { }

    /**
     * Platform kinds for an act. Obj18 contributes its stationary and falling types; Obj52
     * contributes the stationary type ($x0) of every appearance (high nybble) the act places;
     * Obj59 contributes the 80px elevator (subtype 0).
     */
    public static List<Kind> lineUp(List<ObjectSpawn> stock, int romZone) {
        var kinds = new ArrayList<Kind>();
        var blockLooks = new TreeSet<Integer>();
        boolean platform = false;
        boolean elevator = false;
        for (ObjectSpawn spawn : stock) {
            if (spawn.objectId() == PLATFORM) platform = true;
            if (spawn.objectId() == ELEVATOR) elevator = true;
            if (spawn.objectId() == MOVING_BLOCK) blockLooks.add((spawn.subtype() >> 4) & 0x0f);
        }
        if (platform) {
            // Plat_Main obActWid $20.
            kinds.add(new Kind(PLATFORM, 0x00, 0x20, false));
            kinds.add(new Kind(PLATFORM, PLATFORM_FALLS, 0x20, true));
        }
        for (int look : blockLooks) {
            // In SBZ only the full subtype $28 selects the stomper art for look 2, so a
            // stationary $20 would draw frame 2 with the long-block art.
            if (look == 2 && romZone == SBZ) continue;
            // A 32px block leaves Sonic about five frames at running speed: too narrow to land and re-jump.
            int halfWidth = blockHalfWidth(look);
            if (halfWidth >= MIN_HALF_WIDTH) kinds.add(new Kind(MOVING_BLOCK, look << 4, halfWidth, false));
        }
        // Elev_Var1 entry 0: obActWid $28.
        if (elevator) kinds.add(new Kind(ELEVATOR, 0x00, 0x28, false));
        return List.copyOf(kinds);
    }

    /** True for the stock platform objects the course spawns (which keep their own coordinates). */
    static boolean isCoursePlatform(int objectId) {
        return objectId == PLATFORM || objectId == MOVING_BLOCK || objectId == ELEVATOR;
    }

    /** MBlock_Var obActWid by appearance, or 0 for looks the table does not define. */
    static int blockHalfWidth(int look) {
        return switch (look) {
            case 0 -> 0x10;
            case 1, 2 -> 0x20;
            case 3 -> 0x40;
            case 4 -> 0x30;
            default -> 0;
        };
    }

    private CoursePlatforms() { }
}
