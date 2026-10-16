package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.IczStalagtiteRecipe;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorFactory;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.IceCapFactory;
import secondstrike.campaign.east.EastEconomy;

import java.util.List;
import java.util.Set;

/**
 * IceCap: unstable shelter over the native route.
 *
 * <p>Source: locked-on World CRC32 63522553, SpriteLocPtrs zone 5 act 1
 * ($1EE3CA, 249 records) and act 2 ($1EE9A6, 356 records). Authored here: all
 * nine original ICZ1 Stalagtites ($B5) become {@link IczStalagtiteRecipe#WARNED}
 * (trigger $70 = 112 px widened to $A0 = 160 px, shake $0F = 16 widened to $2F = 48 updates, native landed solid closes the
 * approach); ordinary non-speed-shoes monitors near them become Robotnik boxes.
 * ICZ2 has no Stalagtite records: its ring budget is authored, its enemy and
 * boss content is open. The ICZ1 snowboard descent, ice friction, the Sonic
 * snowboard, Tails skip and Knuckles teleporter objects are untouched. Not
 * authored (open): ICZ2 encounters, Penguinator/Star Pointer ledge defence,
 * miniboss and end-boss pressure.
 */
public final class IceCapCampaign {
    public static final String STALAGTITE = "icz-warned-stalagtite";
    public static final String MONITOR = "icz-robotnik-monitor";
    public static final int ZONE = 5;
    public static final int STALAGTITE_ID = 0xB5;

    private IceCapCampaign() { }

    // Recipe data lives in invocation-local literal methods: the creator validator
    // accepts only literal primitive/String static constants, so no static array,
    // set or <clinit> state is kept.

    /** Original ICZ1 Stalagtite placements ({x, y}); ROM-verified by the tests. */
    static int[][] stalagtites() {
        return new int[][]{
                {0x5460, 0x0190}, {0x54B0, 0x0190}, {0x5660, 0x0190}, {0x56D0, 0x0190},
                {0x5747, 0x0190}, {0x57B8, 0x0190}, {0x5A60, 0x0490}, {0x5AC0, 0x0490},
                {0x5B10, 0x0490}};
    }

    /**
     * Original ring placement identities kept per act. Act 1: 54 ($3B28,$6EE)
     * and 135 ($5650,$3F0) by the two checkpoints, 131 ($5568,$1B0) under the
     * first Stalagtite row.
     *
     * <p>Physical act 2 is two apparent acts. Carried ICZ1 (x &lt; $800: miniboss
     * approach, arena $5F0-$7F0, checkpoints $380/$442 numbered 3 and 4): rings 1
     * ($3C0,$400) and 7 ($440,$870) beside those checkpoints, so the carried
     * approach keeps its original two. True ICZ2 (x &gt;= $930, checkpoints 5 and
     * 6): 84 ($15D0,$8A8) closes the lower Penguinator ledge row, 164 ($1E00,$340)
     * rewards the upper crown line, 341 ($34D8,$808) sits in the late lower
     * cluster. Per apparent act: ICZ1 3 + 2 = 5, true ICZ2 3. None is playtested.
     */
    static Set<Integer> rings(Act act) {
        return act == Act.ONE ? Set.of(54, 135, 131) : Set.of(1, 7, 84, 164, 341);
    }

    public enum Act { ONE, TWO }

    public static void register(ModContext context) {
        context.registerObject(STALAGTITE, (spawn, registry) ->
                IceCapFactory.stalactite(spawn, IczStalagtiteRecipe.WARNED));
        context.registerObject(MONITOR, (spawn, registry) -> Sonic3kMonitorFactory.eggman(spawn));
        context.decodedLevelPatch("icz1", patch(Act.ONE));
        context.decodedLevelPatch("icz2", patch(Act.TWO));
    }

    /** Layout landmarks select the act, never the player's position or route. */
    public static Act act(List<ObjectSpawn> objects) {
        if (EastEconomy.contains(objects, 0x34, 0x5650, 0x042D)
                && EastEconomy.contains(objects, 0x34, 0x3BD0, 0x076F)) return Act.ONE;
        if (EastEconomy.contains(objects, 0xBD, 0x4490, 0x05B8)) return Act.TWO;
        return null;
    }

    static LevelPatch patch(Act act) {
        LevelPatch base = LevelPatch.empty().nativePlacements().forZone(ZONE)
                .whenInventory(objects -> act(objects) == act);
        if (act == Act.ONE) {
            return base
                    .select(s -> s.ownerModId() == null && s.objectId() == STALAGTITE_ID).bind(STALAGTITE)
                    .select(s -> EastEconomy.monitorBeside(s, stalagtites(), 0x100, 0x100)).bind(MONITOR)
                    .retainRings((source, objects) -> EastEconomy.keepRings(source, rings(act)));
        }
        return base.retainRings((source, objects) -> EastEconomy.keepRings(source, rings(act)));
    }
}
