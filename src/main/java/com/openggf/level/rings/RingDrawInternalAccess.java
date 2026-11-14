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
    /** Rebind the live external SST lookup after object-manager replacement. */
    public static void bindNativeSlots(RingManager manager) {
        engineCaller();
        manager.bindNativeSlots();
    }
    public static void drawPlaced(RingManager manager, int frameCounter) {
        engineCaller(); manager.drawPlaced(frameCounter);
    }
    public static void drawPlaced(RingManager manager, int frameCounter, int cameraY, int screenYWrapMask) {
        engineCaller(); manager.drawPlaced(frameCounter, cameraY, screenYWrapMask);
    }
    public static void drawAttracted(RingManager manager, int frameCounter) {
        engineCaller(); manager.drawAttracted(frameCounter);
    }
    public static void drawPlacedObject(RingManager manager, RingSpawn ring, int frameCounter) {
        engineCaller(); manager.drawPlacedObject(ring, frameCounter);
    }
    public static java.util.List<com.openggf.level.render.SpriteSlotEmission> slottedAttracted(
            RingManager manager, int bucket) {
        engineCaller(); return inBucket(manager.legacyAttractedRenderEntries(), bucket);
    }
    /**
     * One caller check for an engine renderer's or ring object's lifetime instead of one stack walk
     * per draw. Hold it in engine-private (rewind-transient) state bound to its ring manager;
     * never hand it to creator code.
     */
    public static Engine engine(RingManager manager) {
        engineCaller(); return new Engine(java.util.Objects.requireNonNull(manager));
    }
    /** Admitted ring drawing for one ring manager; methods match the checked static bridges. */
    public static final class Engine {
        private final RingManager manager;
        private Engine(RingManager manager) { this.manager = manager; }
        public boolean boundTo(RingManager manager) { return this.manager == manager; }
        public void drawPlaced(int frameCounter, int cameraY, int screenYWrapMask) {
            manager.drawPlaced(frameCounter, cameraY, screenYWrapMask);
        }
        public void drawPlacedObject(RingSpawn ring, int frameCounter) { manager.drawPlacedObject(ring, frameCounter); }
        public java.util.List<com.openggf.level.render.SpriteSlotEmission> slottedAttracted(int bucket) {
            return inBucket(manager.legacyAttractedRenderEntries(), bucket);
        }
    }
    /** Same unmodifiable result as a stream filter, without stream objects for the usual empty list. */
    private static java.util.List<com.openggf.level.render.SpriteSlotEmission> inBucket(
            java.util.List<com.openggf.level.render.SpriteSlotEmission> entries, int bucket) {
        if (entries.isEmpty()) return java.util.List.of();
        var matching = new java.util.ArrayList<com.openggf.level.render.SpriteSlotEmission>(entries.size());
        for (var entry : entries) if (entry.bucket() == bucket) matching.add(entry);
        return java.util.Collections.unmodifiableList(matching);
    }

}
