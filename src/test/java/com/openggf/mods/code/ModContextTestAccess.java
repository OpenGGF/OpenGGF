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

    /** The engine's wrapper for a registered startup scene, as {@link ModBackedGamePatch} serves it. */
    public static OwnedSceneFactory ownedScene(String owner, ModSceneFactory factory, ModFaultBoundary boundary) {
        return new OwnedSceneFactory(owner, factory, boundary);
    }

    public static ModRegistrationPlan freezeWithDisplayWidth(String owner, String baseGame, int width) {
        ModContext context = new ModContext(owner, baseGame, ModAssetRoot.forTests(owner));
        context.requireDisplayWidth(width);
        return context.freeze();
    }

    public static ModRegistrationPlan freezeWithMutator(String owner, String baseGame,
            com.openggf.mods.mutators.MutatorDefinition definition) {
        ModContext context = new ModContext(owner, baseGame, ModAssetRoot.forTests(owner));
        context.registerMutator(definition);
        return context.freeze();
    }

    public static ModRegistrationPlan freezeStandaloneWithDisplayWidth(String owner, int width) {
        ModContext context = new ModContext(owner, null, ModAssetRoot.forTests(owner), null, true);
        context.requireDisplayWidth(width);
        return context.freeze();
    }

    public static ModRegistrationPlan freezeStandaloneWithStartupScene(String owner, ModSceneFactory factory) {
        ModContext context = new ModContext(owner, null, ModAssetRoot.forTests(owner), null, true);
        context.registerStartupScene(factory);
        return context.freeze();
    }
}
