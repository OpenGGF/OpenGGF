package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorFactory;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.LaunchBaseFactory;
import secondstrike.campaign.east.EastEconomy;

import java.util.List;
import java.util.Set;

/**
 * Launch Base: escalating security over the native route.
 *
 * <p>Source: locked-on World CRC32 63522553, SpriteLocPtrs zone 6 act 1
 * ($1EFC42, 430 records) and act 2 ($1F065C, 489 records). Authored here: all
 * nine original LBZ1 alarms ($22) become {@link LbzAlarmRecipe#BOUNDED}:
 * <ul>
 *   <li>one summon attempt per ALLOCATED alarm instance, latched before
 *       allocation (a failed allocation is not retried); ordinary native
 *       unload/reload allocates a new instance, a destructive rewind restore
 *       preserves the latch;</li>
 *   <li>at most one bounded escort is live in the world; a blocked alarm stays
 *       armed and silent and publishes no touch region, so it neither cues nor
 *       shadows other objects in the player's touch pass;</li>
 *   <li>the escort leaves after its first native attack cycle or when a native
 *       LBZ sequence flag (Robotnik intro, launch, Knuckles cutscene control
 *       lock) is set; ordinary object-controlled play does not retire it;</li>
 *   <li>delayed-spawn subtypes (bit 0, 64 siren ticks): an escort born from
 *       another alarm during the delay leaves this alarm's full native siren
 *       running, spends its single attempt and spawns no newcomer.</li>
 * </ul>
 * Ordinary non-speed-shoes monitors near alarms become Robotnik boxes. LBZ2 has
 * no alarm records: its ring budget is authored, its enemy and boss content is
 * open. Tubes, elevators, launch flow, the Knuckles corridor/pillars and all
 * scripted objects are untouched. Not authored (open): LBZ2 encounters,
 * sentry-opens-lane, miniboss and end-boss pressure.
 */
public final class LaunchBaseCampaign {
    public static final String ALARM = "lbz-bounded-alarm";
    public static final String MONITOR = "lbz-robotnik-monitor";
    public static final int ZONE = 6;
    public static final int ALARM_ID = 0x22;

    private LaunchBaseCampaign() { }

    // Recipe data lives in invocation-local literal methods: the creator validator
    // accepts only literal primitive/String static constants, so no static array,
    // set or <clinit> state is kept.

    /** Original LBZ1 alarm placements ({x, y}); ROM-verified by the tests. */
    static int[][] alarms() {
        return new int[][]{
                {0x0340, 0x0630}, {0x0C40, 0x0130}, {0x0EC0, 0x03B0}, {0x1C40, 0x01B0},
                {0x2940, 0x00B0}, {0x2C40, 0x02B0}, {0x2E40, 0x0130}, {0x3040, 0x0730},
                {0x3140, 0x0630}};
    }
    /**
     * Original ring placement identities kept per act. Act 1: 109 ($1B98,$1A0)
     * and 255 ($3040,$6F8) sit beside alarms, 171 ($2240,$5A8) and 293
     * ($3CC0,$1B0) beside checkpoints. Act 2: 61 ($ED8,$3A8), 210 ($2260,$4E8)
     * and 362 ($3778,$630) beside checkpoints.
     */
    static Set<Integer> rings(Act act) {
        return act == Act.ONE ? Set.of(109, 255, 171, 293) : Set.of(61, 210, 362);
    }

    public enum Act { ONE, TWO }

    public static void register(ModContext context) {
        context.registerObject(ALARM, (spawn, registry) ->
                LaunchBaseFactory.alarm(spawn, LbzAlarmRecipe.BOUNDED));
        context.registerObject(MONITOR, (spawn, registry) -> Sonic3kMonitorFactory.eggman(spawn));
        context.decodedLevelPatch("lbz1", patch(Act.ONE));
        context.decodedLevelPatch("lbz2", patch(Act.TWO));
    }

    /** Layout landmarks select the act, never the player's position or route. */
    public static Act act(List<ObjectSpawn> objects) {
        if (EastEconomy.contains(objects, 0xC3, 0x3BA0, 0x0500)) return Act.ONE;
        if (EastEconomy.contains(objects, 0xCB, 0x3B00, 0x05F8)) return Act.TWO;
        return null;
    }

    static LevelPatch patch(Act act) {
        LevelPatch base = LevelPatch.empty().nativePlacements().forZone(ZONE)
                .whenInventory(objects -> act(objects) == act);
        if (act == Act.ONE) {
            return base
                    .select(s -> s.ownerModId() == null && s.objectId() == ALARM_ID).bind(ALARM)
                    .select(s -> EastEconomy.monitorBeside(s, alarms(), 0x100, 0x100)).bind(MONITOR)
                    .retainRings((source, objects) -> EastEconomy.keepRings(source, rings(act)));
        }
        return base.retainRings((source, objects) -> EastEconomy.keepRings(source, rings(act)));
    }
}
