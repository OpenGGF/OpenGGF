package threeislands.core;

/**
 * The four chapters. {@code game} is the ROM whose art and zone music the chapter uses.
 * {@code mapMusic} is a Sonic 3 &amp; Knuckles driver song (Azure Lake, Balloon Park, Data Select,
 * Chrome Gadget) that starts instantly on the map and covers a Sonic 1 or Sonic 2 zone theme
 * while that is synthesised.
 */
public enum Island {
    SOUTH("South Island", "s1", "Flicky Village", 0x20, 0xFF40A0FF),
    WEST("West Side Island", "s2", "Tails' Workshop", 0x21, 0xFF60D060),
    ANGEL("Angel Island", "s3k", "Master Emerald Shrine", 0x2F, 0xFF40E080),
    DEATH_EGG("The Death Egg", "s3k", "Service Dock", 0x23, 0xFFC060FF);

    public final String label;
    public final String game;
    public final String village;
    public final int mapMusic;
    public final int color;

    Island(String label, String game, String village, int mapMusic, int color) {
        this.label = label;
        this.game = game;
        this.village = village;
        this.mapMusic = mapMusic;
        this.color = color;
    }

    /** Lower-case key used for story scene names. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
