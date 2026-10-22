package com.openggf.level.rings;

/** Draw-only engine bridge; independent of placement, dual-window collection and streaming state. */
public final class RingDrawInternalAccess {
    private RingDrawInternalAccess() { }
    private static void engineCaller() {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(RingDrawInternalAccess.class);
        if (caller.getClassLoader() != RingDrawInternalAccess.class.getClassLoader()) {
            throw new SecurityException("Native ring emission belongs to the engine renderer");
        }
    }
    public static void drawPlaced(RingManager manager, int frameCounter) {
        engineCaller(); manager.drawPlaced(frameCounter);
    }
    public static void drawAttracted(RingManager manager, int frameCounter) {
        engineCaller(); manager.drawAttracted(frameCounter);
    }
    public static void drawPlacedObject(RingManager manager, RingSpawn ring, int frameCounter) {
        engineCaller(); manager.drawPlacedObject(ring, frameCounter);
    }
    public static java.util.List<com.openggf.level.render.SpriteSlotEmission> slottedAttracted(
            RingManager manager, int bucket) {
        engineCaller(); return manager.attractedRenderEntries().stream().filter(e -> e.bucket() == bucket).toList();
    }

}
