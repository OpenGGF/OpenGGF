package flappytails;

/**
 * The two rule sets. An enum is allowed in a mod only when its fields are primitives or
 * strings, as here; the validator rejects enums that carry mutable state.
 */
enum Mode {
    /** One touch ends the flight, and Tails never tires: the native Flappy sample's rules. */
    CLASSIC("CLASSIC", "ONE TOUCH AND YOU'RE DONE", false),
    /**
     * Sonic's rules: rings protect you, as in the games, and Tails tires after the ROM's eight
     * seconds of flight unless rings top him up.
     */
    SONIC("SONIC RULES", "RINGS SAVE YOU. FLIGHT TIRES", true);

    final String label;
    final String blurb;
    final boolean sonicRules;

    Mode(String label, String blurb, boolean sonicRules) {
        this.label = label;
        this.blurb = blurb;
        this.sonicRules = sonicRules;
    }
}
