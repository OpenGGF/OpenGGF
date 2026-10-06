package hello;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/**
 * The mod's entry point, named by {@code entrypoint} in {@code META-INF/openggf-mod.yaml}. The
 * engine calls {@link #register} once when the mod loads. This mod registers one thing: a
 * startup scene, which the engine opens instead of Sonic 3 &amp; Knuckles' title screen.
 */
public final class HelloSceneMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerStartupScene(HelloScene::new);
    }
}
