package com.openggf.game.internal;

/** Internal zone policy: keep native gameplay bounds while centering their viewport. */
public interface NativeArenaCameraFraming {
    boolean centerNativeArenaCamera();

    /** Optional fixed 320px camera origin. Wider views center on it; Y is independent.
     * The zone owns activation/retirement and captures the anchor for rewind.
     */
    default java.util.OptionalInt lockedNativeHorizontalCamera() { return java.util.OptionalInt.empty(); }
}
