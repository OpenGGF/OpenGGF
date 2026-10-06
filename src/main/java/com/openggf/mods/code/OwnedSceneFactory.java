package com.openggf.mods.code;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.Objects;

/**
 * A registered startup scene as the engine runs it: the owning mod's id and its
 * {@link ModSceneFactory}, with every creator callback (including {@code create()}) routed
 * through that mod's {@link ModFaultBoundary}. Engine-internal and not part of the creator API.
 *
 * <p>This type is also the lookup key: the engine asks the effective module for
 * {@code getGameService(OwnedSceneFactory.class)}, which {@link ModBackedGamePatch} answers for
 * the mod that registered the scene. Only this package can construct one, so a game patch
 * cannot forge an owner or run a scene outside the fault boundary, and a creator
 * {@code ModSceneFactory} served under its own type is never opened.
 */
public final class OwnedSceneFactory {
    private final String ownerModId;
    private final ModSceneFactory delegate;
    private final ModFaultBoundary boundary;

    OwnedSceneFactory(String ownerModId, ModSceneFactory delegate, ModFaultBoundary boundary) {
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
    }

    /** The mod that registered the scene (its storage and findings use this id). */
    public String ownerModId() {
        return ownerModId;
    }

    /** A fresh scene from the creator's factory; every call into it runs inside the fault boundary. */
    public ModScene create() {
        ModScene scene = Objects.requireNonNull(boundary.call(ownerModId, delegate::create),
                "Scene factory returned null");
        return new Owned(scene);
    }

    /**
     * The creator's own scene object inside a scene returned by {@link #create}, for engine tests
     * and tools; {@code scene} itself when it is not such a wrapper.
     */
    public static ModScene unwrap(ModScene scene) {
        return scene instanceof Owned owned ? owned.scene : scene;
    }

    /**
     * Sends {@code command} to a scene from {@link #create} that implements
     * {@link com.openggf.mods.scene.DebuggableScene}, inside the fault boundary; false when the
     * scene has no debug entry point or did not understand the command.
     */
    public static boolean debugJump(ModScene scene, String command) {
        return scene instanceof Owned owned && owned.scene instanceof com.openggf.mods.scene.DebuggableScene debuggable
                && owned.boundary().call(owned.owner(), () -> debuggable.debugJump(command));
    }

    /** Runs each lifecycle call on the creator's scene inside the owner's fault boundary. */
    private final class Owned implements ModScene {
        private final ModScene scene;

        Owned(ModScene scene) {
            this.scene = scene;
        }

        ModFaultBoundary boundary() {
            return boundary;
        }

        String owner() {
            return ownerModId;
        }

        @Override
        public void enter(SceneContext ctx) {
            boundary.run(ownerModId, () -> scene.enter(ctx));
        }

        @Override
        public void update(SceneContext ctx) {
            boundary.run(ownerModId, () -> scene.update(ctx));
        }

        @Override
        public void draw(SceneContext ctx, SceneCanvas canvas) {
            boundary.run(ownerModId, () -> scene.draw(ctx, canvas));
        }

        @Override
        public void exit(SceneContext ctx) {
            boundary.run(ownerModId, () -> scene.exit(ctx));
        }
    }
}
