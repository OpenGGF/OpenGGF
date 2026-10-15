package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.LavaReefHazardRecipe;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.LavaReefFactory;

import java.util.List;

/**
 * A single opening LRZ2 nozzle candidate, preserving its native axis and pause.
 * Locked-on World SpriteLocPtrs LRZ2 $1F7C9C: original record0 is
 * {@code 08D2 0770 29 10}. LRZ1 has no native $29 records.
 *
 * <p>No other nozzle, terrain, ring or monitor is changed. The provider call is
 * held until the visible cue, safe response and checkpoint approach are proved;
 * a bound actor alone does not qualify an industrial route or a harder act.
 */
public final class LavaReefCampaign {
    private LavaReefCampaign() { }

    public static void register(ModContext context) {
        context.registerObject("lrz-opening-echo", (spawn, registry) ->
                LavaReefFactory.flameThrower(spawn, LavaReefHazardRecipe.ECHO_JET));
        context.decodedLevelPatch("lrz-two-opening-echo", LevelPatch.empty().nativePlacements()
                .forZone(9)
                .whenInventory(LavaReefCampaign::hasOpeningNozzle)
                .select(LavaReefCampaign::isOpeningNozzle)
                .bind("lrz-opening-echo"));
    }

    private static boolean hasOpeningNozzle(List<ObjectSpawn> source) {
        return source.stream().anyMatch(LavaReefCampaign::isOpeningNozzle);
    }

    private static boolean isOpeningNozzle(ObjectSpawn spawn) {
        return spawn.ownerModId() == null && spawn.objectKey() == null
                && spawn.layoutIndex() == 0 && spawn.objectId() == 0x29
                && spawn.x() == 0x08D2 && spawn.y() == 0x0770
                && spawn.subtype() == 0x10 && spawn.rawYWord() == 0x0770
                && spawn.renderFlags() == 0 && !spawn.respawnTracked();
    }
}
