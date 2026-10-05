package com.openggf.mods.scene;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Engine-owned wrapper that remembers which mod owns a scene and routes every creator
 * callback through the mod fault boundary. Not part of the creator API.
 */
public final class OwnedSceneFactory implements ModSceneFactory {
    private final String ownerModId;
    private final ModSceneFactory delegate;
    private final BiConsumer<String, Runnable> boundary;

    /** {@code boundary} runs a callback for an owner (normally {@code ModFaultBoundary::run}). */
    public OwnedSceneFactory(String ownerModId, ModSceneFactory delegate, BiConsumer<String, Runnable> boundary) {
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
    }

    public String ownerModId() {
        return ownerModId;
    }

    @Override
    public ModScene create() {
        ModScene[] created = new ModScene[1];
        boundary.accept(ownerModId, () -> created[0] = delegate.create());
        ModScene scene = Objects.requireNonNull(created[0], "Scene factory returned null");
        return new ModScene() {
            @Override
            public void enter(SceneContext ctx) {
                boundary.accept(ownerModId, () -> scene.enter(ctx));
            }

            @Override
            public void update(SceneContext ctx) {
                boundary.accept(ownerModId, () -> scene.update(ctx));
            }

            @Override
            public void draw(SceneContext ctx, SceneCanvas canvas) {
                boundary.accept(ownerModId, () -> scene.draw(ctx, canvas));
            }

            @Override
            public void exit(SceneContext ctx) {
                boundary.accept(ownerModId, () -> scene.exit(ctx));
            }
        };
    }
}
