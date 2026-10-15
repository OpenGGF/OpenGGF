package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.HiddenPalaceDuelRecipe;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.HiddenPalaceFactory;

import java.util.List;

/**
 * Personal-duel ingredient for the authentic playable Hidden Palace scene only.
 * Locked-on World SpriteLocPtrs $1FCC7A: original record31 at $1FCD34 is
 * {@code 11F0 042C 82 28}. The Master Emerald (record37/$B0) is untouched.
 *
 * <p>Zone $16 also contains the LRZ boss act; the inventory matcher admits only
 * this complete original record. Sanctuary $1701 is a distinct scene. The
 * native duel owner retains Knuckles-player deletion and story progression.
 * This class stays outside the campaign provider until ordinary punish, visible
 * presentation, native ending and recovery/route qualification are complete.
 */
public final class HiddenPalaceCampaign {
    private HiddenPalaceCampaign() { }

    public static void register(ModContext context) {
        context.registerObject("hpz-personal-feint", (spawn, registry) ->
                HiddenPalaceFactory.duel(spawn, HiddenPalaceDuelRecipe.FEINT));
        context.decodedLevelPatch("hpz-personal-duel", LevelPatch.empty().nativePlacements()
                .forZone(0x16)
                .whenInventory(HiddenPalaceCampaign::hasPersonalDuel)
                .select(HiddenPalaceCampaign::isPersonalDuel)
                .bind("hpz-personal-feint"));
    }

    private static boolean hasPersonalDuel(List<ObjectSpawn> source) {
        return source.stream().anyMatch(HiddenPalaceCampaign::isPersonalDuel);
    }

    private static boolean isPersonalDuel(ObjectSpawn spawn) {
        return spawn.ownerModId() == null && spawn.objectKey() == null
                && spawn.layoutIndex() == 31 && spawn.objectId() == 0x82
                && spawn.x() == 0x11F0 && spawn.y() == 0x042C
                && spawn.subtype() == 0x28 && spawn.rawYWord() == 0x042C
                && spawn.renderFlags() == 0 && !spawn.respawnTracked();
    }
}
