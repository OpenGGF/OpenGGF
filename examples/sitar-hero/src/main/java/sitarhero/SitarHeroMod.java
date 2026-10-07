package sitarhero;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/** A whole-game behaviour replacement over any supplied stock Sonic ROM. */
public final class SitarHeroMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(SitarScene::new);
    }
}
