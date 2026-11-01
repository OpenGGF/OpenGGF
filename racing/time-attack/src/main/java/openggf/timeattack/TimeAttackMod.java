package openggf.timeattack;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/**
 * Time Attack: race a stock act against your best ghost (and, in multiplayer, other players'
 * live ghosts). Bundled with OpenGGF and enabled by default; it adds an Extras entry and
 * launches every run as a stock, non-saving session through the gameplay run API.
 */
public final class TimeAttackMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        // The build's default master comes from the generated asset, read while registration is open.
        String buildDefaultMaster = NetworkDefaults.defaultMaster(context.modAssets());
        context.registerTitleEntry("Time Attack", () -> new TimeAttackScene(buildDefaultMaster));
    }
}
