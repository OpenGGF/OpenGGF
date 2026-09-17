package com.openggf.game.sonic3k.runtime;

/**
 * Zone runtime owner of the ROM {@code Camera_stored_min_X_pos}, {@code Camera_stored_max_X_pos},
 * {@code Camera_stored_min_Y_pos} and {@code Camera_stored_max_Y_pos} words, which the gradual camera
 * boundary workers ({@code Obj_IncLevEndXGradual} family, sonic3k.asm:178159-178233) read. They have
 * no engine-wide owner; the zones whose events or bosses allocate those workers store them in
 * their runtime state.
 */
public interface S3kCameraStoredBounds {
    int cameraStoredMinX();
    int cameraStoredMaxX();
    int cameraStoredMinY();
    int cameraStoredMaxY();
}
