package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.MushroomHillWindRecipe;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.MushroomHillFactory;

import java.util.List;

/**
 * Opening MHZ2 wind candidate, using the authenticated original Cluckoid only.
 * Locked-on World SpriteLocPtrs: MHZ1 $1F2296, MHZ2 $1F306A. Original MHZ2
 * record34 at $1F3136 is {@code 06A8 0518 90 00}. MHZ1 has no Cluckoid records.
 *
 * <p>This ingredient does not establish a harder encounter, season/chase remix,
 * safe pocket, character route or whole-act completion. Its provider call stays
 * separate until native presentation and ordinary counterplay are qualified.
 */
public final class MushroomHillCampaign {
    private MushroomHillCampaign() { }

    public static void register(ModContext context) {
        context.registerObject("mhz-opening-wind", (spawn, registry) ->
                MushroomHillFactory.cluckoid(spawn, MushroomHillWindRecipe.PULSED));
        context.decodedLevelPatch("mhz-two-opening-wind", LevelPatch.empty().nativePlacements()
                .forZone(7)
                .whenInventory(MushroomHillCampaign::hasOpeningWind)
                .select(MushroomHillCampaign::isOpeningWind)
                .bind("mhz-opening-wind"));
    }

    private static boolean hasOpeningWind(List<ObjectSpawn> source) {
        return source.stream().anyMatch(MushroomHillCampaign::isOpeningWind);
    }

    private static boolean isOpeningWind(ObjectSpawn spawn) {
        return spawn.ownerModId() == null && spawn.objectKey() == null
                && spawn.layoutIndex() == 34 && spawn.objectId() == 0x90
                && spawn.x() == 0x06A8 && spawn.y() == 0x0518
                && spawn.subtype() == 0 && spawn.rawYWord() == 0x0518
                && spawn.renderFlags() == 0 && !spawn.respawnTracked();
    }
}
