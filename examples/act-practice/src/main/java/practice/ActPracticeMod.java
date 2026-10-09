package practice;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/** Registers the Act Practice master-title entry. */
public final class ActPracticeMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerTitleEntry("Act Practice", PracticeScene::new);
    }
}
