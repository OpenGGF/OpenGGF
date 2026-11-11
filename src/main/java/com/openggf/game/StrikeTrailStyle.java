package com.openggf.game;

import java.util.List;
import java.util.Objects;

/**
 * Creator colours for a homing or divebomb strike's presentation: the streaming embers, the
 * heat front around the striking body, and the after-image echoes. Colours are 0xRRGGBB.
 * Presentation only; the strike's gameplay never reads it. A character supplies its normal
 * style from {@code AbstractPlayableSprite.strikeTrailStyle()} and each powered form from
 * {@link PoweredEffects#trail()}; supplying nothing keeps the stock look.
 *
 * @param embers   three ember colours, hottest first
 * @param front    heat-front layers drawn from the outer haze to the nose (1..8)
 * @param echoTint after-image tint as 0xRRGGBB, or -1 to keep the character's own colours
 * @param density  ember count as a percentage of the stock rate (25..400)
 * @param echoPhaseSlot index (1..15) on the character's palette line whose colour, sampled when each
 *                 after-image is spawned, tints that after-image; -1 for none. Echoes then keep the
 *                 palette-cycle phase they were spawned in, so a cycling form leaves a banded trail.
 *                 An authored powered form reports its running cycle row, beneath any
 *                 {@link PoweredEffects#strikePalette()}. It takes precedence over {@code echoTint}.
 */
@ModApi
public record StrikeTrailStyle(List<Integer> embers, List<FrontLayer> front, int echoTint, int density,
                               int echoPhaseSlot) {
    /** Compatibility constructor: no spawn-phase echo tint. */
    public StrikeTrailStyle(List<Integer> embers, List<FrontLayer> front, int echoTint, int density) {
        this(embers, front, echoTint, density, -1);
    }

    /**
     * One ring of the heat front.
     *
     * @param rgb    0xRRGGBB colour
     * @param radius distance from the body centre in pixels (4..40)
     * @param size   square size in pixels (1..8)
     * @param alpha  peak opacity (0..255)
     * @param arc    half-width of the arc in steps (0..16)
     */
    @ModApi
    public record FrontLayer(int rgb, int radius, int size, int alpha, int arc) {
        public FrontLayer {
            if ((rgb & ~0xFFFFFF) != 0 || radius < 4 || radius > 40 || size < 1 || size > 8
                    || alpha < 0 || alpha > 255 || arc < 0 || arc > 16)
                throw new IllegalArgumentException("Invalid strike front layer");
        }
    }

    public StrikeTrailStyle {
        embers = List.copyOf(embers);
        front = List.copyOf(front);
        if (embers.size() != 3 || embers.stream().anyMatch(colour -> (colour & ~0xFFFFFF) != 0))
            throw new IllegalArgumentException("Strike embers need three 0xRRGGBB colours, hottest first");
        if (front.isEmpty() || front.size() > 8) throw new IllegalArgumentException("Strike front needs 1..8 layers");
        front.forEach(layer -> Objects.requireNonNull(layer, "front layer"));
        if (echoTint != -1 && (echoTint & ~0xFFFFFF) != 0)
            throw new IllegalArgumentException("Echo tint is 0xRRGGBB or -1");
        if (density < 25 || density > 400) throw new IllegalArgumentException("Ember density is 25..400 percent");
        if (echoPhaseSlot != -1 && (echoPhaseSlot < 1 || echoPhaseSlot > 15))
            throw new IllegalArgumentException("Echo phase slot is 1..15 or -1");
    }
}
