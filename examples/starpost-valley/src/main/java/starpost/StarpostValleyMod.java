package starpost;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import starpost.scene.StarpostScene;

/** Starpost Valley's entry point: one startup scene at 16:9. */
public final class StarpostValleyMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        var town = starpost.realtown.TownContent.register(context);
        var destination = new com.openggf.game.ZoneKey.Mod("starpost-valley", "valley");
        context.registerZone(com.openggf.mods.code.ModZoneContribution.singleAct("valley",
            new com.openggf.mods.code.BakedLevelRef("levels/town-placeholder/level.json"), null, null, false));
        starpost.realtown.TownContent.registerInput(context, destination, town);
        context.registerStartupScene(() -> new StarpostScene(town));
    }
}
