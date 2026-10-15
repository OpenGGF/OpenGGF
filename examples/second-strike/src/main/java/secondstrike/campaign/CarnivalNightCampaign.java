package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.ClamerRecipe;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorFactory;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.CarnivalNightFactory;
import secondstrike.campaign.east.EastEconomy;

import java.util.List;
import java.util.Set;

/**
 * Carnival Night: malicious rhythm over the native route.
 *
 * <p>Source: locked-on World CRC32 63522553, SpriteLocPtrs zone 3 act 1
 * ($1EA0C2, 488 records) and act 2 ($1EAC38, 708 records). Authored here:
 * every original Clamer ({@code $A3}) gets the native {@link ClamerRecipe#CADENCE}
 * preset (range $60 = 96 px widened to $90 = 144 px). Where two original Clamers
 * stand together (act 2 records 165/180 and the stacked 455/456), the creator
 * gives the second record {@link ClamerRecipe#STAGGERED_CADENCE} (tell held $20 =
 * 32 updates longer) so the pair releases alternately. The creator chooses those
 * records by explicit original identity; the native owner infers nothing from
 * identity. Ordinary non-speed-shoes monitors
 * within {@code $100}x{@code $80} of a Clamer become native Robotnik boxes.
 * Clamer springs, barrels, balloons, bumpers, platforms, cannons, bosses and
 * Knuckles' cutscene objects are untouched. Not authored (open): balloon,
 * bumper and platform stagger, timed shortcuts, boss pressure.
 */
public final class CarnivalNightCampaign {
    public static final String CLAMER = "cnz-cadence-clamer";
    public static final String STAGGERED_CLAMER = "cnz-staggered-clamer";
    public static final String MONITOR = "cnz-robotnik-monitor";
    public static final int ZONE = 3;
    public static final int CLAMER_ID = 0xA3;

    private CarnivalNightCampaign() { }

    // Recipe data lives in invocation-local literal methods: the creator validator
    // accepts only literal primitive/String static constants, so no static array,
    // set or <clinit> state is kept.

    /** Original Clamer placements ({x, y}); ROM-verified by the tests. */
    static int[][] clamers(Act act) {
        if (act == Act.ONE) {
            return new int[][]{
                    {0x01A0, 0x0850}, {0x0456, 0x0890}, {0x0578, 0x0690}, {0x0680, 0x0A30},
                    {0x0C98, 0x0470}, {0x0DE8, 0x03B0}, {0x1190, 0x0330}, {0x1980, 0x02B0},
                    {0x1D88, 0x0890}, {0x2460, 0x00F0}, {0x26A0, 0x00D0}, {0x2818, 0x0610},
                    {0x2860, 0x0370}, {0x28C0, 0x0270}, {0x28D8, 0x0A30}, {0x2D20, 0x04D0}};
        }
        // Two share {$27E8,$7B0}: records 455 and 456.
        return new int[][]{
                {0x0CE8, 0x0950}, {0x0DA8, 0x0430}, {0x16A0, 0x0230}, {0x16F0, 0x0410},
                {0x1770, 0x0430}, {0x1804, 0x0930}, {0x1BA8, 0x0B30}, {0x24F0, 0x08F0},
                {0x27E8, 0x07B0}, {0x27E8, 0x07B0}, {0x2B20, 0x0530}};
    }

    /**
     * Second member of each original adjacent Clamer pair in act 2: record 180
     * ($1770,$430, beside 165 at $16F0,$410) and 456 ($27E8,$7B0, stacked on 455).
     * Act 1 has no adjacent pair, so it has no staggered record.
     */
    static Set<Integer> staggeredRecords(Act act) {
        return act == Act.TWO ? Set.of(180, 456) : Set.of();
    }

    /**
     * Original ring placement identities kept per act. Act 1: 77 ($E70,$324)
     * rewards the high Clamer line, 123 ($11E8,$D5) and 308 ($2D27,$2AE) sit by
     * the first and third checkpoint. Act 2: 12 ($CD0,$3F8) by the opening
     * Clamer ledge, 117 ($1B0C,$298) and 239 ($2C30,$8EA) by the checkpoints.
     */
    static Set<Integer> rings(Act act) {
        return act == Act.ONE ? Set.of(77, 123, 308) : Set.of(12, 117, 239);
    }

    public enum Act { ONE, TWO }

    public static void register(ModContext context) {
        context.registerObject(CLAMER, (spawn, registry) ->
                CarnivalNightFactory.clamer(spawn, ClamerRecipe.CADENCE));
        context.registerObject(STAGGERED_CLAMER, (spawn, registry) ->
                CarnivalNightFactory.clamer(spawn, ClamerRecipe.STAGGERED_CADENCE));
        context.registerObject(MONITOR, (spawn, registry) -> Sonic3kMonitorFactory.eggman(spawn));
        context.decodedLevelPatch("cnz1", patch(Act.ONE));
        context.decodedLevelPatch("cnz2", patch(Act.TWO));
    }

    /** Layout landmarks select the act, never the player's position or route. */
    public static Act act(List<ObjectSpawn> objects) {
        if (EastEconomy.contains(objects, 0xA6, 0x32C0, 0x020C)) return Act.ONE;
        if (EastEconomy.contains(objects, 0xA7, 0x4940, 0x0270)) return Act.TWO;
        return null;
    }

    static LevelPatch patch(Act act) {
        int[][] clamers = clamers(act);
        Set<Integer> rings = rings(act);
        Set<Integer> staggered = staggeredRecords(act);
        return LevelPatch.empty().nativePlacements().forZone(ZONE)
                .whenInventory(objects -> act(objects) == act)
                .select(spawn -> spawn.ownerModId() == null && spawn.objectId() == CLAMER_ID
                        && staggered.contains(spawn.layoutIndex())).bind(STAGGERED_CLAMER)
                .select(spawn -> spawn.ownerModId() == null && spawn.objectId() == CLAMER_ID).bind(CLAMER)
                .select(spawn -> EastEconomy.monitorBeside(spawn, clamers, 0x100, 0x80)).bind(MONITOR)
                .retainRings((source, objects) -> EastEconomy.keepRings(source, rings));
    }
}
