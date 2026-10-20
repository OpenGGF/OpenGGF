package sonicbros;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/**
 * Super Sonic Bros.: a platform fighter for up to four players (people or CPUs) on islands cut
 * from Sonic 3 &amp; Knuckles' zones, with Sonic, Tails, Knuckles, Robotnik, Mecha Sonic, Egg Robo
 * and Rhinobot, all drawn from the player's own ROM. Registers one startup scene, laid out for
 * the 400-pixel widescreen view.
 */
public final class SuperSonicBrosMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(BrosScene::new);
    }
}
