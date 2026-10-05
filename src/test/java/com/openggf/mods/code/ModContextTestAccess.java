package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.scene.ModSceneFactory;

/** Lets tests outside this package drive the package-private registration transaction. */
public final class ModContextTestAccess {
    private ModContextTestAccess() {
    }

    public static ModRegistrationPlan freezeWithStartupScene(String owner, String baseGame, ModSceneFactory factory) {
        ModContext context = new ModContext(owner, baseGame, ModAssetRoot.forTests(owner));
        context.registerStartupScene(factory);
        return context.freeze();
    }

    public static ModRegistrationPlan freezeStandaloneWithStartupScene(String owner, ModSceneFactory factory) {
        ModContext context = new ModContext(owner, null, ModAssetRoot.forTests(owner), null, true);
        context.registerStartupScene(factory);
        return context.freeze();
    }
}
