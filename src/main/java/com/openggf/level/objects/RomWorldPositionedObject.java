package com.openggf.level.objects;

/**
 * Native object-position contract for ROM routines that edit SST
 * {@code x_pos}/{@code y_pos} words in place.
 *
 * <p>The position mutation and retained-anchor mutation are deliberately two
 * distinct operations. Implementations must change the native centre-position
 * words while preserving any subpixel low word/byte in
 * {@link #offsetNativePositionWordsPreserveSubpixel(int, int)}. Derived
 * origins, targets, or cached anchors belong in
 * {@link #afterRomWorldTransitionOffset(int, int)} and are invoked once after
 * the native words have moved.
 */
public interface RomWorldPositionedObject {
    void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY);

    default void afterRomWorldTransitionOffset(int offsetX, int offsetY) {
    }
}
