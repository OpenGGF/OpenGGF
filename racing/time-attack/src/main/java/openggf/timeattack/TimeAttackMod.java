package openggf.timeattack;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/**
 * Time Attack: race a stock act against your best ghost (and, in multiplayer, other players'
 * live ghosts). Bundled with OpenGGF and enabled by default; it adds a master-title entry and
 * launches every run as a stock, non-saving session through the gameplay run API.
 */
public final class TimeAttackMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerTitleEntry("Time Attack", TimeAttackScene::new);
    }
}
