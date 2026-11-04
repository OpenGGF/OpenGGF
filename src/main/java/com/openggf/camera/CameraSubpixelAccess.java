package com.openggf.camera;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

/**
 * The single {@code Camera_X_pos} low word, without adding members to the Camera mod API.
 *
 * <p>S3K keeps {@code Camera_X_pos} as a 16.16 long. Its low word survives every level
 * load ({@code Get_LevelSizeStart} writes only the word, sonic3k.asm:38241) and is
 * cleared only by {@code Title_Screen}'s {@code Camera_RAM} clear (sonic3k.asm:5448),
 * the data select's {@code clr.l} (sonic3k.asm:10071) and explicit producer writes.
 * Every 16.16 producer reads and writes this one value; a new gameplay session's
 * camera starts at zero as the title clear does. {@code MoveCameraX} writes only the
 * word, so ordinary following keeps it.
 */
public final class CameraSubpixelAccess {
    /** Rewind key of the low-word entry, captured beside {@code camera}. */
    public static final String REWIND_KEY = "camera-x-fraction";

    private CameraSubpixelAccess() { }

    public static int xFraction(Camera camera) {
        return camera.xFraction & 0xFFFF;
    }

    public static void setXFraction(Camera camera, int fraction) {
        camera.xFraction = fraction & 0xFFFF;
    }

    /** The whole {@code Camera_X_pos} long. */
    public static int xFixed(Camera camera) {
        return ((camera.getX() & 0xFFFF) << 16) | xFraction(camera);
    }

    /** A rewind entry for the low word; {@link Camera#capture()} remains the word owner. */
    public static RewindSnapshottable<Integer> rewindAdapter(Camera camera) {
        Objects.requireNonNull(camera, "camera");
        return new RewindSnapshottable<>() {
            @Override public String key() { return REWIND_KEY; }
            @Override public Integer capture() { return xFraction(camera); }
            @Override public void restore(Integer snapshot) { setXFraction(camera, snapshot); }
            @Override public void resetForMissingSnapshot() { setXFraction(camera, 0); }
        };
    }
}
