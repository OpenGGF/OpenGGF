package starpost;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import starpost.scene.StarpostScene;

/** Starpost Valley's entry point: one startup scene at 16:9. */
public final class StarpostValleyMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(StarpostScene::new);
    }
}
