package towerdefense;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.io.IOException;

/** Registers a game scene without changing the base game's level behavior. */
public final class TowerDefenseMod implements GgfMod {
    @Override public void register(ModContext context) {
        byte[] font;
        try {
            font = context.modAssets().readBounded("art/font.txt", 32768);
        } catch (IOException e) {
            throw new IllegalStateException("Missing Robotnik Tower Defence font", e);
        }
        context.requireDisplayWidth(400);
        context.registerStartupScene(() -> new TowerDefenseScene(font));
    }
}
