package eggsky;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/**
 * Eggman's Sky: registers the full-screen startup scene that replaces the Sonic 3 &amp; Knuckles
 * title, laid out for 16:9 (400x224).
 */
public final class EggmansSkyMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(SkyScene::new);
    }
}
