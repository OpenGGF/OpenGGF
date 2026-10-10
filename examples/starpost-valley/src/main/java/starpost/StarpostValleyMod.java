package starpost;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import starpost.realvalley.RealValley;
import starpost.scene.StarpostScene;

/**
 * Starpost Valley's entry point: one startup scene at 16:9, plus the valley as a real S3K act
 * ({@link RealValley}; reached through the farm gate).
 */
public final class StarpostValleyMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        // The valley is a real S3K act: Sonic 1 Green Hill re-encoded at load (RealValley), hosting
        // the town's objects and input (realtown); the scene starts it at the farm gate.
        var town = starpost.realtown.TownContent.register(context);
        RealValley.register(context);
        var destination = new com.openggf.game.ZoneKey.Mod("starpost-valley", RealValley.ZONE);
        starpost.realtown.TownContent.registerInput(context, destination, town);
        // S1 ROM-art TIME/day clock and wallet RINGS are drawn by the town HUD.
        context.registerHudProfile(new com.openggf.mods.code.ModHudProfileContribution(destination,
            new com.openggf.level.objects.HudProfile(java.util.List.of())));
        context.registerStartupScene(() -> new StarpostScene(town));
    }
}
