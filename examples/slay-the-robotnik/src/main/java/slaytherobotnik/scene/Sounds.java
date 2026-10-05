package slaytherobotnik.scene;

/**
 * Sonic 3 &amp; Knuckles sound driver IDs used by the game (from the engine's
 * {@code Sonic3kMusic} / {@code Sonic3kSfx} tables).
 */
public final class Sounds {
    // Music.
    public static final int MUSIC_TITLE = 0x2F;          // Data Select
    public static final int MUSIC_CHARACTER = 0x2D;      // Competition menu
    public static final int MUSIC_MINIBOSS = 0x18;
    public static final int MUSIC_BOSS = 0x19;
    public static final int MUSIC_FINAL_BOSS = 0x30;
    public static final int MUSIC_SHOP = 0x1E;           // Gumball bonus stage
    public static final int MUSIC_REST = 0x1D;           // Slot bonus stage
    public static final int MUSIC_GAME_OVER = 0x27;
    public static final int MUSIC_ACT_CLEAR = 0x29;
    public static final int MUSIC_EMERALD = 0x2B;
    public static final int MUSIC_CREDITS = 0x26;
    public static final int MUSIC_TORNADO = 0x1C;        // Special Stage (Blue Spheres) for the flight

    // Sound effects.
    public static final int SFX_RING = 0x33;
    public static final int SFX_HURT = 0x35;
    public static final int SFX_SPIKES = 0x37;
    public static final int SFX_SHIELD = 0x3A;
    public static final int SFX_ROLL = 0x3C;
    public static final int SFX_BREAK = 0x3D;
    public static final int SFX_FIRE_SHIELD = 0x3E;
    public static final int SFX_BUBBLE_SHIELD = 0x3F;
    public static final int SFX_LIGHTNING_SHIELD = 0x41;
    public static final int SFX_INSTA_SHIELD = 0x42;
    public static final int SFX_SWITCH = 0x5B;
    public static final int SFX_JUMP = 0x62;
    public static final int SFX_STARPOST = 0x63;
    public static final int SFX_PERFECT = 0x68;
    public static final int SFX_BOSS_HIT = 0x6E;
    public static final int SFX_SUPER_EMERALD = 0x9C;
    public static final int SFX_ENTER_SPECIAL = 0xAF;
    public static final int SFX_REGISTER = 0xB0;
    public static final int SFX_SPRING = 0xB1;
    public static final int SFX_ERROR = 0xB2;
    public static final int SFX_EXPLODE = 0xB4;
    public static final int SFX_DASH = 0xB6;
    public static final int SFX_CURSOR = 0xB7;
    public static final int SFX_RING_LOSS = 0xB9;
    public static final int SFX_SPINDASH = 0xAB;

    private Sounds() {
    }
}
