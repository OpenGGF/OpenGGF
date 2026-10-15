package com.openggf.game.internal;

/** Internal zone policy: keep native gameplay bounds while centering their viewport. */
public interface NativeArenaCameraFraming {
    /** Resolve the active zone policy here so cameras do not depend on level orchestration. */
    static NativeArenaCameraFraming current() {
        var level = com.openggf.game.GameServices.levelOrNull();
        return level != null && level.getZoneFeatureProvider() instanceof NativeArenaCameraFraming framing
                ? framing : null;
    }

    boolean centerNativeArenaCamera();

    /** Optional fixed 320px camera origin. Wider views center on it; Y is independent.
     * The zone owns activation/retirement and captures the anchor for rewind.
     */
    default java.util.OptionalInt lockedNativeHorizontalCamera() { return java.util.OptionalInt.empty(); }

    /** Optional presentation origin for a native screen-coordinate scene.
     * Its 320px window no longer describes the gameplay world's camera bounds;
     * the bounds mask must use this origin without changing those native words.
     */
    default java.util.OptionalInt presentationNativeHorizontalOrigin() { return java.util.OptionalInt.empty(); }
}
