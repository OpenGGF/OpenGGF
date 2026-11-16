package com.openggf.game;

/** Engine-only reads of the donation in force, deliberately outside the creator-facing Mod API. */
public final class CrossGameFeatureInternalAccess {
    private CrossGameFeatureInternalAccess() { }

    public static java.util.Map<Character, CharacterPresentation.Image> nameFont(CrossGameFeatureProvider donation)
            throws java.io.IOException { return donation.donorNameFont(); }

    public static com.openggf.level.Pattern[] loadLifeArt(CrossGameFeatureProvider donation, String character)
            throws java.io.IOException {
        return donation.loadLifeArt(character);
    }

    /**
     * The donor's own number for a pose of a built-in character it supplies.
     *
     * @return the donor's animation ID, or -1 when the donor has no such character or pose
     */
    public static int resolveDonorAnimation(CrossGameFeatureProvider donation, CharacterKey character,
                                            CanonicalAnimation animation) {
        return donation.resolveDonorAnimation(character, animation);
    }
}
