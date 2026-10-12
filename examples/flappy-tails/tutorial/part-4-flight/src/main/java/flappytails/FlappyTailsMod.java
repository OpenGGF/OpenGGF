package flappytails;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/**
 * The entry point named in {@code META-INF/openggf-mod.yaml}. Flappy Tails is one startup
 * scene on a fixed 16:9 screen: the wider view shows more of the course ahead.
 */
public final class FlappyTailsMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(FlappyScene::new);
    }
}
