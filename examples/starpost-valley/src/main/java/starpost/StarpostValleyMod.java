package starpost;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import starpost.realvalley.RealValley;
import starpost.scene.StarpostScene;

/**
 * Starpost Valley's entry point: one startup scene at 16:9, plus the valley as a real S3K act
 * ({@link RealValley}; not yet reachable from the scene).
 */
public final class StarpostValleyMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(StarpostScene::new);
        RealValley.register(context);
    }
}
