package com.openggf.game;

import java.util.Objects;

/**
 * Optional ending frames with their own sixteen-colour palette. Frame IDs retain the
 * native sequence selectors or mapping IDs; scenes keep their native timing.
 */
@ModApi
public record EndingSpriteSet(StageSpriteSet sprites, int[] rgb,
        java.util.Map<Integer, CharacterPresentation.Image> nativeClearMasks) {
    public EndingSpriteSet(StageSpriteSet sprites, int[] rgb) {
        this(sprites, rgb, java.util.Map.of());
    }
    @ModApi public enum Surface {
        S1_ENDING, S2_ENDING, S2_FLOAT, S2_SUPER,
        /** Active first sidekick supplies selectors 1..3; selector 0 always stays native. */
        S2_PHOTOS,
        /** First sidekick cockpit crops, native Tails pilot mapping IDs 1..4/16, 24x16. */
        S2_TORNADO_PILOT,
        /** Main-owned companion cockpit art when no sidekick is configured; native pilot selectors. */
        S2_SOLO_TORNADO_PILOT,
        /** Final Enigma plane cockpit, selector 0, relative to the 184x120 backdrop origin. */
        S2_FINAL_TORNADO_PILOT,
        /** Main-owned companion version of the final Enigma plane cockpit. */
        S2_SOLO_FINAL_TORNADO_PILOT,
        /**
         * First sidekick supplies the full-size Sonic-plane cockpit: selector 12 is the Tails seat
         * (Sonic- and Knuckles-role leads); selector 11 is the Sonic seat a Tails-role lead flies with.
         */
        S3K_TORNADO_PILOT,
        /** Main-owned companion cockpit art for a solo team; the same selectors 12 and 11. */
        S3K_SOLO_TORNADO_PILOT,
        S3K_NORMAL_POSE, S3K_POWERED_POSE,
        /**
         * The lead standing on the mid-size Tornado that reaches the island (and returns to Angel
         * Island): native Sonic-plane-ending rider selector 14 (Sonic seat, 16x24) or 15 (Tails
         * seat, 16x16). The plane and its exhaust flame stay native.
         */
        S3K_PASSENGER, S3K_EYECATCH,
        S3K_PLAYER, S3K_POWERED_PLAYER,
        /** Main-owned companion search photos when no sidekick is configured; selectors 1..3. */
        S2_SOLO_PHOTOS,
        /**
         * First sidekick supplies the mid-size Tornado's cockpit head, 16x8: native selector 12
         * (Tails seat) or 13 (the Sonic seat a Tails-role lead flies with).
         */
        S3K_MINI_TORNADO_PILOT,
        /** Main-owned companion cockpit head for a solo team; the same selectors 12 and 13. */
        S3K_SOLO_MINI_TORNADO_PILOT,
        /**
         * Main-owned companion figure for the other hero seat of the final S3 graphics card on a
         * solo team. Seats use {@link #S3K_EYECATCH}'s selectors: 1..4 for the Sonic seat, 10 and
         * 11 for the Tails seat body (its native tail, 5..9, is not drawn beside a custom body).
         * A lead supplies its own seat through {@link #S3K_EYECATCH}; a configured creator
         * sidekick supplies the other seat through its own {@link #S3K_EYECATCH}.
         */
        S3K_SOLO_EYECATCH
    }
    public EndingSpriteSet {
        Objects.requireNonNull(sprites, "sprites");
        if (rgb.length != 16) throw new IllegalArgumentException("Ending palette requires sixteen colours");
        rgb = rgb.clone();
        nativeClearMasks = java.util.Map.copyOf(nativeClearMasks);
        nativeClearMasks.forEach((selector, mask) -> {
            var frame = sprites.frames().get(selector);
            if (frame == null || frame.image().width() != mask.width()
                    || frame.image().height() != mask.height())
                throw new IllegalArgumentException("Clear mask must match an authored ending frame");
            for (byte value : mask.pixels()) if (value != 0 && value != 1)
                throw new IllegalArgumentException("Ending clear masks must be binary");
        });
    }
    @Override public int[] rgb() { return rgb.clone(); }
}
