package com.openggf.game;

import java.util.List;
import java.util.Objects;

/**
 * Creator presentation for one powered form: its strike trail colours and small particle
 * sprites shed by emission rules. Particle pixels index the character's own live palette line,
 * so they follow the form's palette cycle. The engine draws particles with the character's
 * native priority, behind its body, in every host; they never feed gameplay and are discarded
 * on rewind and reload.
 *
 * @param trail     strike trail colours for this form, or null to keep the character's normal trail
 * @param particles particle sprites, indexed by {@link Emitter#particles()} (at most 16)
 * @param emitters  emission rules (at most 16)
 * @param strikePalette sixteen Mega Drive colour words shown on the character's palette line while a
 *                  homing or divebomb strike of this form is in flight, or empty to keep the form's
 *                  cycle. The cycle keeps its own clock underneath and resumes at its live phase.
 */
@ModApi
public record PoweredEffects(StrikeTrailStyle trail, List<Particle> particles, List<Emitter> emitters,
                             List<Integer> strikePalette) {
    /** Compatibility constructor: strikes keep the form's palette cycle. */
    public PoweredEffects(StrikeTrailStyle trail, List<Particle> particles, List<Emitter> emitters) {
        this(trail, particles, emitters, List.of());
    }

    /** Live particles shared by all of a character's emitters. */
    public static final int MAX_LIVE_PARTICLES = 48;

    /** What makes an emitter fire. Continuous triggers fire every {@code interval} ticks. */
    @ModApi
    public enum Trigger {
        /** Once, when the transformation completes and the form becomes active. */
        TRANSFORM,
        /** Continuous while moving at 6 px/frame or more, not rolling and not striking. */
        FAST,
        /** Continuous while rolling or in the jump ball at 2 px/frame or more, not striking. */
        ROLLING,
        /** Continuous while a homing or divebomb strike is in flight. */
        STRIKING,
        /** Continuous while flying. */
        FLYING,
        /** Once per flight flap. */
        FLAP,
        /** Once each time a homing or divebomb strike lands on its target. */
        STRIKE_HIT
    }

    /** How an emission places its particles. */
    @ModApi
    public enum Spread {
        /** Evenly spaced around a circle of {@code radius}, moving outward at {@code speedX}. */
        RING,
        /** Scattered inside a box of half-size {@code radius}, moving at ({@code speedX}, {@code speedY}). */
        SCATTER
    }

    /**
     * One particle sprite: frames play in order, {@code frameTicks} each. A cycle at least as long
     * as the emitter's life plays once, holding its last frame; a shorter one loops from a random
     * phase (a twinkle). Hotspots centre each frame on the particle's position.
     */
    @ModApi
    public record Particle(List<StageSpriteSet.Frame> frames, int frameTicks) {
        public Particle {
            frames = List.copyOf(frames);
            if (frames.isEmpty() || frames.size() > 8 || frameTicks < 1 || frameTicks > 60)
                throw new IllegalArgumentException("Particles need 1..8 frames of 1..60 ticks");
            for (var frame : frames) {
                Objects.requireNonNull(frame, "frame");
                if (frame.image().width() > 32 || frame.image().height() > 32)
                    throw new IllegalArgumentException("Particle frames are at most 32x32");
            }
        }
    }

    /**
     * One emission rule. Offsets and horizontal speeds are facing-relative: positive x is in
     * front of the character. Speeds, jitter and gravity are in 1/256 pixel per tick.
     *
     * @param trigger   when it fires
     * @param particles indices into {@link PoweredEffects#particles()}; each particle picks one
     * @param count     particles per emission (1..32)
     * @param interval  ticks between continuous emissions (1..60); ignored by one-shot triggers
     * @param spread    placement
     * @param offsetX   emission point from the body centre, in pixels (-64..64)
     * @param offsetY   emission point from the body centre, in pixels (-64..64)
     * @param radius    ring radius or scatter half-size, in pixels (0..48)
     * @param speedX    ring speed, or scatter forward speed (-0x800..0x800)
     * @param speedY    scatter downward speed (-0x800..0x800)
     * @param jitter    random speed added on each axis (0..0x400)
     * @param gravity   added to the vertical speed each tick (-0x80..0x80)
     * @param life      ticks each particle lives (1..120)
     */
    @ModApi
    public record Emitter(Trigger trigger, List<Integer> particles, int count, int interval, Spread spread,
                          int offsetX, int offsetY, int radius, int speedX, int speedY, int jitter, int gravity,
                          int life) {
        public Emitter {
            Objects.requireNonNull(trigger, "trigger");
            Objects.requireNonNull(spread, "spread");
            particles = List.copyOf(particles);
            if (particles.isEmpty() || count < 1 || count > 32 || interval < 1 || interval > 60
                    || Math.abs(offsetX) > 64 || Math.abs(offsetY) > 64 || radius < 0 || radius > 48
                    || Math.abs(speedX) > 0x800 || Math.abs(speedY) > 0x800 || jitter < 0 || jitter > 0x400
                    || Math.abs(gravity) > 0x80 || life < 1 || life > 120)
                throw new IllegalArgumentException("Invalid powered-effect emitter");
        }
    }

    public PoweredEffects {
        particles = List.copyOf(particles);
        emitters = List.copyOf(emitters);
        strikePalette = List.copyOf(strikePalette);
        if (!strikePalette.isEmpty() && (strikePalette.size() != 16
                || strikePalette.stream().anyMatch(word -> (word & ~0xEEE) != 0)))
            throw new IllegalArgumentException("A strike palette is sixteen Mega Drive colour words");
        if (particles.size() > 16 || emitters.size() > 16)
            throw new IllegalArgumentException("Powered effects allow 16 particles and 16 emitters");
        for (var emitter : emitters)
            for (int index : emitter.particles())
                if (index < 0 || index >= particles.size())
                    throw new IllegalArgumentException("Emitter names a missing particle");
    }
}
