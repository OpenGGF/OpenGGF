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

    public static void beginCollection(GraphicsManager graphics, SpriteSatMaskPostProcessor.Raster raster) {
        engineCaller();
        graphics.beginSpriteSatCollection(raster);
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
