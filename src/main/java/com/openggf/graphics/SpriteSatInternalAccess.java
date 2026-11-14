package com.openggf.graphics;

import java.util.List;

/** Engine-only native raster/fragment bridge, outside the published GraphicsManager API. */
public final class SpriteSatInternalAccess {
    private SpriteSatInternalAccess() { }

    private static void engineCaller() {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(SpriteSatInternalAccess.class);
        if (caller.getClassLoader() != SpriteSatInternalAccess.class.getClassLoader()) {
            throw new SecurityException("Native SAT assembly belongs to the engine renderer");
        }
    }

    /**
     * One caller check for an engine renderer's lifetime instead of one stack walk per frame.
     * The check only admits a fixed engine call site, so the handle carries that verdict.
     * Hold it in engine-private state bound to its render host; never hand it to creator code.
     */
    public static Engine engine(GraphicsManager graphics) {
        engineCaller();
        return new Engine(java.util.Objects.requireNonNull(graphics, "graphics"));
    }

    /** Admitted SAT assembly for one render host; methods match the checked static bridges. */
    public static final class Engine {
        private final GraphicsManager graphics;
        private Engine(GraphicsManager graphics) { this.graphics = graphics; }
        public boolean boundTo(GraphicsManager graphics) { return this.graphics == graphics; }
        public void beginCollection(SpriteSatMaskPostProcessor.Raster raster) {
            graphics.beginSpriteSatCollection(raster);
        }
        public void beginLogicalCollection(SpriteSatMaskPostProcessor.LogicalViewport viewport) {
            graphics.beginLogicalSpriteSatCollection(viewport);
        }
        public void submitGroups(List<SpriteSatEntry> geometry, Runnable producer) {
            graphics.submitSpriteSatGroups(geometry, producer);
        }
        public void renderShadowTile(SpritePresentation.Tile tile, int cameraX, int cameraY, float[] quad,
                                     float opacity) {
            graphics.renderShadowTile(tile, cameraX, cameraY, quad, opacity);
        }
        public <T> void replayNativeLayers(List<T> frontToBack, java.util.function.Predicate<T> isGhost,
                                           java.util.function.Consumer<List<T>> emitter) {
            graphics.replayNativeLayers(frontToBack, isGhost, emitter);
        }
    }

    public static void beginCollection(GraphicsManager graphics, SpriteSatMaskPostProcessor.Raster raster) {
        engineCaller();
        graphics.beginSpriteSatCollection(raster);
    }

    /** Engine bootstrap supplies the live display preference; unbound renderers retain native limits. */
    public static void bindLineLimitSource(GraphicsManager graphics, java.util.function.BooleanSupplier source) {
        engineCaller();
        graphics.bindSpriteScanlineLimitSource(source);
    }

    public static void beginLogicalCollection(GraphicsManager graphics, SpriteSatMaskPostProcessor.LogicalViewport viewport) {
        engineCaller();
        graphics.beginLogicalSpriteSatCollection(viewport);
    }

    public static void submitGroups(GraphicsManager graphics, List<SpriteSatEntry> geometry, Runnable producer) {
        engineCaller();
        graphics.submitSpriteSatGroups(geometry, producer);
    }

    /**
     * Submits palette-3/color-15-only prepared pieces through the existing normal
     * SAT intake, with ordinary admission, clipping and frozen fragments. The
     * producer submits pieces synchronously; it must not render an unbudgeted prepass
     * or mutate the collection lifecycle. Begin/replay attempts fail before table
     * mutation. Lexically nested operator scopes restore their prior operation.
     */
    public static void submitShadowPieces(GraphicsManager graphics, Runnable preparedSubmissions) {
        engineCaller();
        graphics.submitShadowPieces(preparedSubmissions);
    }

    public static void submitShadowGroups(GraphicsManager graphics, List<SpriteSatEntry> geometry, Runnable producer) {
        engineCaller();
        graphics.submitShadowGroups(geometry, producer);
    }

    /**
     * Borrow a caller-owned, already shadow-intensity/profile-resolved image while
     * submitting a native table. Pixel origin and size use bottom-left GL window
     * coordinates of the draw viewport. The texture may have a different logical
     * resolution; its full image must cover that same projected scene region.
     * Its minification and magnification filters must both be GL_NEAREST.
     * It must be distinct from every color attachment of the executing draw target.
     * Commands capture this descriptor; CPU frames and rewind never retain it, so
     * a published-frame replay must enter a fresh live backdrop scope.
     */
    public static void withShadow15Backdrop(GraphicsManager graphics, int textureId, int originX, int originY,
                                             int width, int height, Runnable tableDraw) {
        engineCaller();
        graphics.withShadow15Backdrop(textureId, originX, originY, width, height, tableDraw);
    }

    /** Replays an immutable admitted operator, retaining its exact source rows and final quad. */
    public static void renderShadowTile(GraphicsManager graphics, SpritePresentation.Tile tile,
                                        int cameraX, int cameraY, float[] quad, float opacity) {
        engineCaller();
        graphics.renderShadowTile(tile, cameraX, cameraY, quad, opacity);
    }

    /** Producer emits already-admitted native source order; no creator API can open GPU coverage. */
    public static void withNativeOwnership(GraphicsManager graphics, Runnable producer) {
        engineCaller();
        graphics.withNativeSpriteOwnership(producer);
    }

    /** Replay admitted front-to-back layers, blending gameplay ghosts without claiming native coverage. */
    public static <T> void replayNativeLayers(GraphicsManager graphics, List<T> frontToBack,
                                               java.util.function.Predicate<T> isGhost,
                                               java.util.function.Consumer<List<T>> emitter) {
        engineCaller();
        graphics.replayNativeLayers(frontToBack, isGhost, emitter);
    }
}
