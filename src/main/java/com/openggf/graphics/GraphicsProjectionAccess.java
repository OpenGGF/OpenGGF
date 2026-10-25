package com.openggf.graphics;

import java.util.Objects;

/** Engine bootstrap bridge; creators read the projection through GraphicsManager. */
public final class GraphicsProjectionAccess {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private GraphicsProjectionAccess() { }

    private static void engineProjection(RenderProjection projection) {
        ClassLoader caller=com.openggf.util.EngineCallerAccess
                .callerOutside(GraphicsProjectionAccess.class).getClassLoader();
        if (caller!=GraphicsProjectionAccess.class.getClassLoader()
                || projection!=null && projection.getClass().getClassLoader()!=GraphicsProjectionAccess.class.getClassLoader())
            throw new SecurityException("Rendering projection installation belongs to the engine host");
    }

    public static void install(GraphicsManager graphics, RenderProjection projection) {
        engineProjection(projection);
        Objects.requireNonNull(graphics,"graphics").setProjectionSource(projection);
    }

    /**
     * Temporarily installs an engine projection and clears the local matrix override.
     * This changes CPU projection state only: commands bake display height at obtain
     * and batches bake it at beginBatch, while their matrix is read at execution.
     * Before changing projection, callers must finish existing batches and drain
     * queued commands under their owning projection. Commands constructed here
     * must execute in a matching scope; batches must finish before it closes.
     * No batches, commands or framebuffer state are flushed or changed here.
     * Scopes close on their opening thread in LIFO order.
     */
    public static Scope openProjection(GraphicsManager graphics, RenderProjection projection) {
        engineProjection(projection);
        Scope scope = new Scope(Objects.requireNonNull(graphics,"graphics"));
        graphics.setProjectionSource(projection);
        graphics.setProjectionMatrixBuffer(null);
        ACTIVE.set(scope);
        return scope;
    }

    /** Engine-only lexical projection ownership; it does not own a GL context or FBO. */
    public static final class Scope implements AutoCloseable {
        private final GraphicsManager graphics;
        private final RenderProjection previousSource;
        private final float[] previousRawMatrix;
        private final Scope previous;
        private final Thread thread = Thread.currentThread();
        private boolean closed;

        private Scope(GraphicsManager graphics) {
            this.graphics = graphics;
            previousSource = graphics.getProjectionSource();
            previousRawMatrix = graphics.rawProjectionMatrixBuffer();
            previous = ACTIVE.get();
        }

        @Override public void close() {
            if (Thread.currentThread() != thread || ACTIVE.get() != this || closed)
                throw new IllegalStateException("Projection scopes must close on their opening thread in LIFO order");
            graphics.setProjectionSource(previousSource);
            graphics.setProjectionMatrixBuffer(previousRawMatrix);
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
            closed = true;
        }
    }
}
