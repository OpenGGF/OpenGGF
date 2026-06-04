package com.openggf.game.sonic2.objects;

/**
 * ROM-exact S2 object windowing math (docs/s2disasm/s2.asm).
 * Load base: camRounded = Camera_X_pos &amp; $FF80 (ObjectsManager_Main, s2.asm:33026).
 * Unload base: Camera_X_pos_coarse = (Camera_X_pos - $80) &amp; $FF80 (MarkObjGone, s2.asm:30209).
 * Live window (final boundaries): [camRounded - $80, camRounded + $280] (width $300).
 */
public final class S2ObjectWindowing {
    private S2ObjectWindowing() {}

    public static final int LOAD_AHEAD = 0x280;
    public static final int TRIM_BEHIND = 0x80;
    /** MarkObjGone native compare constant = $80 + roundToNextMultiple(320,$80)=$180 + $80 = $280. */
    public static final int UNLOAD_COMPARE = 0x280;

    public static int loadCoarse(int cameraX)   { return cameraX & 0xFF80; }
    public static int unloadCoarse(int cameraX) { return (cameraX - 0x80) & 0xFF80; }

    public static int forwardLoadEdge(int cameraX) { return loadCoarse(cameraX) + LOAD_AHEAD; }
    public static int leftTrimEdge(int cameraX)    { return loadCoarse(cameraX) - TRIM_BEHIND; }
    public static int backwardLoadEdge(int cameraX){ return loadCoarse(cameraX) - TRIM_BEHIND; }
    public static int rightTrimEdge(int cameraX)   { return loadCoarse(cameraX) + LOAD_AHEAD; }

    /** ROM MarkObjGone delete decision: (x_pos &amp; $FF80) - Camera_X_pos_coarse &gt; $280 (unsigned 16-bit). */
    public static boolean markObjGone(int objX, int cameraX) {
        int dist = ((objX & 0xFF80) - unloadCoarse(cameraX)) & 0xFFFF;
        return dist > UNLOAD_COMPARE;
    }
}
