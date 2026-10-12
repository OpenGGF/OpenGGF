package com.openggf;

import com.openggf.game.session.EngineContext;
import com.openggf.mods.code.ModRuntime;

/** Native friends-build control: exercise Engine's actual boot capability read,
 * development source, trust policy, loader and registration without a display.
 * Uses existing private boot seam, registered by this image's hosted feature.
 * No graphics/gameplay claim. Each check runs in its own disposable process.
 */
public final class ExperimentalEngineBootCheck {
    public static void verify(String owner) throws Exception {
        if (!com.openggf.mods.DevelopmentModSource.isConfigured()) {
            throw new IllegalArgumentException("Select the same development directory used by the shortcut");
        }
        EngineContext services = EngineContext.fromLegacySingletonsForBootstrap();
        services.configuration().ensureConfigFileExists();
        Engine engine = new Engine(services);
        var boot = Engine.class.getDeclaredMethod("initializeExternalContentAtBoot");
        boot.setAccessible(true);
        boot.invoke(engine);
        var field = Engine.class.getDeclaredField("modRuntime");
        field.setAccessible(true);
        try (ModRuntime runtime = (ModRuntime) field.get(engine)) {
            if (!runtime.owners().contains(owner) || !runtime.rejectedOwners().isEmpty()) {
                throw new AssertionError("Engine native capability/trust boot rejected " + owner + ": " + runtime.rejectedOwners());
            }
            runtime.newRegistrationPlan();
            if (!runtime.registrationFailures().isEmpty() || !runtime.registrationPlans().containsKey(owner)) {
                throw new AssertionError("Engine boot registration failed: " + runtime.registrationFailures());
            }
        }
        System.out.println("PASS engine native boot registration: " + owner);
    }
}
