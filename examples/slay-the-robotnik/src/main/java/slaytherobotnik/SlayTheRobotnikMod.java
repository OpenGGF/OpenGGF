package slaytherobotnik;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.io.IOException;
import slaytherobotnik.scene.SlayScene;

/**
 * Mod entry point. Registration does three things:
 * <ol>
 *   <li>reads the text-art assets (font and icons) while the mod's files are open,</li>
 *   <li>registers {@link SlayScene} as the startup scene, so choosing Sonic 3 &amp; Knuckles on
 *       the master title opens the card game instead of the stock title screen, and</li>
 *   <li>asks for the 16:9 (400-pixel) display the layout is drawn for, for the whole session (the
 *       player's own setting returns at the master title).</li>
 * </ol>
 * Everything else happens inside the scene.
 */
public final class SlayTheRobotnikMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        byte[] font = asset(context, "art/font.txt");
        byte[] icons = asset(context, "art/icons.txt");
        byte[] cards = asset(context, "art/cards.txt");
        byte[] relics = asset(context, "art/relics.txt");
        context.registerStartupScene(() -> new SlayScene(font, icons, cards, relics));
        context.requireDisplayWidth(400);
    }

    private static byte[] asset(ModContext context, String path) {
        try {
            return context.modAssets().readBounded(path, 1 << 20);
        } catch (IOException e) {
            throw new IllegalStateException("Slay the Robotnik is missing its asset " + path, e);
        }
    }
}
