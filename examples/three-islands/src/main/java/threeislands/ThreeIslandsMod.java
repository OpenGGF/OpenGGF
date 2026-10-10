package threeislands;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.io.IOException;

/**
 * Three Islands: a story-driven, fully turn-based JRPG across South Island (Sonic 1), West Side
 * Island (Sonic 2) and Angel Island (Sonic 3 &amp; Knuckles). Mod files are only readable during
 * registration, so the font and the story script are read here and handed to the scene, which
 * opens instead of the Sonic 3 &amp; Knuckles title and asks for the 400-pixel (16:9) display.
 */
public final class ThreeIslandsMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        byte[] font = asset(context, "text/font.txt");
        byte[] story = asset(context, "text/story.txt");
        context.registerStartupScene(() -> new IslandsScene(font, story));
        context.requireDisplayWidth(IslandsScene.WIDTH);
    }

    private static byte[] asset(ModContext context, String path) {
        try {
            return context.modAssets().readBounded(path, 1 << 20);
        } catch (IOException e) {
            throw new IllegalStateException("Three Islands is missing its asset " + path, e);
        }
    }
}
