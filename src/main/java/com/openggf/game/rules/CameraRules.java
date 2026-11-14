package com.openggf.game.rules;

/**
 * Camera scroll, wrap, visibility and tracking rules.
 *
 * @param horizontalFocusWithoutDeadzone
 *        the horizontal follow centres on one focus column instead of the 16-pixel
 *        144-160 sweet spot. Sonic CD's {@code MoveScreenHoriz} computes
 *        {@code x_pos - cameraX - camXCenter} and scrolls on any non-zero result
 *        (scddisasm {@code src/Level/Palmtree Panic/Scroll (Act 1 Present).asm:565-573});
 *        Sonic 1/2/3&amp;K test {@code 144} and {@code 160} separately
 *        (docs/s1disasm/_inc/ScrollHoriz &amp; ScrollVertical.asm:34-55). Only Sonic CD sets it.
 * @param playerSpeedPansHorizontalFocus
 *        the player's routine moves that focus column 2 pixels per frame toward
 *        {@code 160 -/+ 64} while {@code |ground speed| >= $600} and back to 160 otherwise
 *        ({@code ObjSonic_ExtCamera}, scddisasm {@code src/Level/_Objects/Sonic/Main.asm:231-283}).
 *        Only Sonic CD sets it.
 */
@com.openggf.game.ModApi
public record CameraRules(
        short lookScrollDelay,
        boolean waterShimmerEnabled,
        int fastScrollCap,
        boolean uncappedLeftwardHorizontalScroll,
        boolean useScreenYWrapValueForVisibility,
        boolean playerControlAppliesVerticalWrapMask,
        boolean groundedRollRecentersCameraBias,
        boolean horizontalFocusWithoutDeadzone,
        boolean playerSpeedPansHorizontalFocus) {

    /** Preserve the existing recentering behavior for callers of the six-argument API. */
    public CameraRules(short lookScrollDelay, boolean waterShimmerEnabled, int fastScrollCap,
                       boolean uncappedLeftwardHorizontalScroll,
                       boolean useScreenYWrapValueForVisibility,
                       boolean playerControlAppliesVerticalWrapMask) {
        this(lookScrollDelay, waterShimmerEnabled, fastScrollCap,
                uncappedLeftwardHorizontalScroll, useScreenYWrapValueForVisibility,
                playerControlAppliesVerticalWrapMask, true);
    }

    /** Keeps the 144-160 sweet spot and a fixed focus for callers of the seven-argument API. */
    public CameraRules(short lookScrollDelay, boolean waterShimmerEnabled, int fastScrollCap,
                       boolean uncappedLeftwardHorizontalScroll,
                       boolean useScreenYWrapValueForVisibility,
                       boolean playerControlAppliesVerticalWrapMask,
                       boolean groundedRollRecentersCameraBias) {
        this(lookScrollDelay, waterShimmerEnabled, fastScrollCap,
                uncappedLeftwardHorizontalScroll, useScreenYWrapValueForVisibility,
                playerControlAppliesVerticalWrapMask, groundedRollRecentersCameraBias, false, false);
    }
}
