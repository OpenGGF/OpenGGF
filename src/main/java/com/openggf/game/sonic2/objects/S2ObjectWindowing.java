package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ViewportAwareObjectWindowingStrategy;

/**
 * ROM-exact S2 object windowing math (docs/s2disasm/s2.asm).
 * Load base: camRounded = Camera_X_pos &amp; $FF80 (ObjectsManager_Main, s2.asm:33026).
 * Unload base: Camera_X_pos_coarse = (Camera_X_pos - $80) &amp; $FF80 (MarkObjGone, s2.asm:30209).
 * Native live window (final boundaries): [camRounded - $80, camRounded + $280]
 * (width $300). Widescreen extends the right cursor by the shared capped
 * viewport lead and extends object-side unload by the viewport-width delta.
 *
 * <p>Implements the shared {@link ObjectWindowingStrategy} so the game-agnostic
 * {@code com.openggf.level.objects.ObjectManager} consumes S2 windowing through
 * the interface (injected via {@code Sonic2ObjectRegistry}) rather than importing
 * this class directly. The static methods remain the ROM-math source of truth and
 * are reused by the instance overrides and by unit tests.
 */
public final class S2ObjectWindowing implements ViewportAwareObjectWindowingStrategy {

    /** Shared stateless S2 windowing strategy instance. */
    public static final S2ObjectWindowing INSTANCE = new S2ObjectWindowing();

    private S2ObjectWindowing() {}

    @Override
    public boolean overridesLoadWindow() {
        return true;
    }

    @Override
    public int loadWindowForwardEdge(int cameraX) {
        return forwardLoadEdge(cameraX);
    }

    @Override
    public int loadWindowForwardEdge(int cameraX, int viewportWidth) {
        return forwardLoadEdge(cameraX, viewportWidth);
    }

    @Override
    public int loadWindowLeftTrimEdge(int cameraX) {
        return leftTrimEdge(cameraX);
    }

    @Override
    public int loadWindowLeftTrimEdge(int cameraX, int viewportWidth) {
        return leftTrimEdge(cameraX);
    }

    @Override
    public boolean overridesUnloadWindow() {
        return true;
    }

    @Override
    public boolean isOutsideUnloadWindow(int objX, int cameraX) {
        return markObjGone(objX, cameraX);
    }

    @Override
    public boolean isOutsideUnloadWindow(int objX, int cameraX, int viewportWidth) {
        return markObjGone(objX, cameraX, viewportWidth);
    }

    public static final int LOAD_AHEAD = 0x280;
    public static final int TRIM_BEHIND = 0x80;
    /** MarkObjGone native compare constant = $80 + roundToNextMultiple(320,$80)=$180 + $80 = $280. */
    public static final int UNLOAD_COMPARE = 0x280;
    private static final int NATIVE_VIEWPORT_WIDTH = 320;
    private static final int WIDESCREEN_LOAD_LEAD = 0x80;
    private static final int UNLOAD_MARGIN = 0x140;

    public static int loadCoarse(int cameraX)   { return cameraX & 0xFF80; }
    public static int unloadCoarse(int cameraX) { return (cameraX - 0x80) & 0xFF80; }

    public static int forwardLoadEdge(int cameraX) { return loadCoarse(cameraX) + LOAD_AHEAD; }
    public static int forwardLoadEdge(int cameraX, int viewportWidth) {
        return loadCoarse(cameraX) + loadAheadFor(viewportWidth);
    }
    public static int leftTrimEdge(int cameraX)    { return loadCoarse(cameraX) - TRIM_BEHIND; }
    public static int backwardLoadEdge(int cameraX){ return loadCoarse(cameraX) - TRIM_BEHIND; }
    public static int rightTrimEdge(int cameraX)   { return loadCoarse(cameraX) + LOAD_AHEAD; }
    public static int rightTrimEdge(int cameraX, int viewportWidth) {
        return forwardLoadEdge(cameraX, viewportWidth);
    }

    /**
     * Shared widescreen placement policy: retain the native $280 window, then
     * grow only enough to keep one $80 lead beyond a wider visible edge.
     */
    public static int loadAheadFor(int viewportWidth) {
        return Math.max(LOAD_AHEAD, Math.max(NATIVE_VIEWPORT_WIDTH, viewportWidth) + WIDESCREEN_LOAD_LEAD);
    }

    /** Native $280 compare plus exactly the viewport-width delta. */
    public static int unloadCompareFor(int viewportWidth) {
        return Math.max(NATIVE_VIEWPORT_WIDTH, viewportWidth) + UNLOAD_MARGIN;
    }

    /** ROM MarkObjGone delete decision: (x_pos &amp; $FF80) - Camera_X_pos_coarse &gt; $280 (unsigned 16-bit). */
    public static boolean markObjGone(int objX, int cameraX) {
        return markObjGone(objX, cameraX, NATIVE_VIEWPORT_WIDTH);
    }

    /** Widescreen-aware MarkObjGone using the active viewport width. */
    public static boolean markObjGone(int objX, int cameraX, int viewportWidth) {
        int dist = ((objX & 0xFF80) - unloadCoarse(cameraX)) & 0xFFFF;
        return dist > unloadCompareFor(viewportWidth);
    }

    public enum LoadOutcome { LOADED, ALREADY_LOADED_CONTINUE, SST_FULL_STOP }

    /**
     * ROM ChkLoadObj (s2.asm:33592): bset #7 tests-and-sets the respawn entry.
     * If bit 7 was already set, the object is already loaded → advance the list
     * pointer and CONTINUE scanning (success). Otherwise allocate; only a full SST
     * (no allocatable slot) STOPS the scan.
     */
    public static LoadOutcome chkLoadObj(boolean respawnBitAlreadySet, boolean slotAllocatable) {
        if (respawnBitAlreadySet) {
            return LoadOutcome.ALREADY_LOADED_CONTINUE;
        }
        return slotAllocatable ? LoadOutcome.LOADED : LoadOutcome.SST_FULL_STOP;
    }
}
