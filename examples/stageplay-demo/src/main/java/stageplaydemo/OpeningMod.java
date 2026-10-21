package stageplaydemo;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.stageplay.StageplayPlayback;

/** The exact same saved scene opens in either editor, exports as a film, or plays inside an act. */
public final class OpeningMod implements GgfMod {
    @Override public void register(ModContext context) {
        try { StageplayPlayback.registerOnEntry(context, "opening", "scenes/opening.stageplay"); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot load the opening scene", failure); }
    }
}
