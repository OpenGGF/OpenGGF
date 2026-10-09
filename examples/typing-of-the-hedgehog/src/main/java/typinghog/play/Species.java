package typinghog.play;

/**
 * A badnik as the course stages it: the stock art key, the mapping frames of its walk/fly cycle,
 * how it moves and how its words behave. Frames come from the art key's ROM mappings
 * (pick them from {@code ObjectArtContactSheetProbe} sheets); {@code facesRight} says which way
 * the unflipped art looks.
 */
public record Species(String name, String artKey, int[] frames, int frameDelay, Archetype archetype,
                      boolean facesRight, int halfWidth, int halfHeight) {

    /** How a badnik approaches and what typing it asks for. */
    public enum Archetype {
        /** Walks along the ground toward Sonic; one short word. */
        RUSHER,
        /** Flies in high, then swoops at Sonic; one medium word. */
        DIVER,
        /** Stops at range and fires shots that each carry one letter; body word. */
        SHOOTER,
        /** Segments each carry a word, head last. */
        SEGMENTED,
        /** Four single letters on its orbiting balls, then the body. */
        ORBITER,
        /** Its word shows only at the top of its jump. */
        SURFACER,
        /** A shell word, then a core word. */
        ARMOURED
    }

    public Species {
        if (frames.length == 0) throw new IllegalArgumentException(name + " has no frames");
        if (frameDelay < 1) throw new IllegalArgumentException(name + " frame delay must be positive");
    }

    public int frameAt(int tick) {
        return frames[(tick / frameDelay) % frames.length];
    }

    public static Species of(String name, String artKey, Archetype archetype, boolean facesRight,
                             int halfWidth, int halfHeight, int delay, int... frames) {
        return new Species(name, artKey, frames, delay, archetype, facesRight, halfWidth, halfHeight);
    }
}
