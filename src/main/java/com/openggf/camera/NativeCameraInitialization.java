package com.openggf.camera;

/** Internal native Level entry; does not add a Camera mod API or retained mode. */
public final class NativeCameraInitialization {
    private NativeCameraInitialization() { }

    /**
     * MoveCameraX/Y in Level loc_6310 (sonic3k.asm:7789-7795), after
     * Get_LevelSizeStart and before LevelSetup. Get_LevelSizeStart sets
     * Screen_Y_wrap_value to $FFFF (sonic3k.asm:38132-38133), regardless of the
     * destination's later looping geometry. In loc_1C202 (sonic3k.asm:38601-38613),
     * adding one in word width yields zero: subtraction cannot borrow, retains
     * the candidate Y and subtracts zero from the old camera. The upward mask
     * likewise retains the word. This is shipped FixBugs=0 code, with no alternate
     * fixed branch at these sites.
     *
     * <p>The admitted stock Level assembly owns this call. It respects scroll lock,
     * uses the ordinary movement math and leaves bounds, copy words and X fraction
     * intact. Actorless resource loads have no native Player_1 to follow and
     * retain their camera state. No pending permission can leak into a gameplay
     * frame or rewind.
     */
    public static void moveBeforeLevelSetup(Camera camera) {
        if (camera.getFocusedSprite() == null) return;
        camera.moveBeforeLevelSetup();
    }
}
