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
    /** sfx_SpikeHit: HurtCharacter's sound when spikes (sharp things) do the hurting. */
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
    /** sfx_SlotMachine: the reels' clatter, every 16 frames while they turn (the cursor tick is the same sound). */
    public static final int SFX_SLOT_MACHINE = 0xB7;
    public static final int SFX_RING_LOSS = 0xB9;
    public static final int SFX_SPINDASH = 0xAB;
    /** sfx_FireAttack: the Fire Shield's dash, a burst of flame. */
    public static final int SFX_FIRE_ATTACK = 0x43;
    /** sfx_Grab: Knuckles catching hold. */
    public static final int SFX_GRAB = 0x4A;
    /** sfx_Collapse: breakable rocks and walls bursting (AIZLRZEMZRock_PlayCollapseSfx). */
    public static final int SFX_COLLAPSE = 0x59;
    /** sfx_Rumble2: a short rumble (the continuous sfx_Rumble $CB would need stopping). */
    public static final int SFX_RUMBLE = 0x6F;
    /** sfx_SuperTransform: the Super transformation. */
    public static final int SFX_SUPER_TRANSFORM = 0x9F;
    /** sfx_Clank: metal knocking on metal. */
    public static final int SFX_CLANK = 0x9E;
    /** sfx_Signpost: the end-of-act signpost spinning through its faces. */
    public static final int SFX_SIGNPOST = 0xB8;
    /** sfx_RingLeft: Collect_Ring alternates it with sfx_RingRight for each ring. */
    public static final int SFX_RING_LEFT = 0x34;
    /** sfx_Bubble: an air bubble gulped underwater; the Lab's tubes gurgle as they drain. */
    public static final int SFX_BUBBLE = 0x38;
    /** sfx_ElectricAttack: the Lightning Shield's double jump, which throws its four sparks. */
    public static final int SFX_ELECTRIC_ATTACK = 0x45;
    /** sfx_MechaSpark: Mecha Sonic's sparks (Obj_MechaSonic_Sparks). */
    public static final int SFX_MECHA_SPARK = 0x5C;
    /** sfx_Transporter: the Sky Sanctuary / Hidden Palace teleporter beaming something away. */
    public static final int SFX_TRANSPORTER = 0x73;
    /** sfx_Alarm: Launch Base's alarm (Obj_LBZAlarm). */
    public static final int SFX_ALARM = 0x86;
    /** sfx_GhostAppear: a Hyudoro ghost fading in (Obj_Hyudoro). */
    public static final int SFX_GHOST_APPEAR = 0x92;
    /** sfx_BigRing: the swish of touching a Giant Ring (SSEntryRing loc_6170A). */
    public static final int SFX_BIG_RING = 0xB3;
    /** sfx_Splash: something landing in water. */
    public static final int SFX_SPLASH = 0x39;
    /** sfx_MushroomBounce: a Mushroom Hill cap springing a player (MHZMushroomCap_BounceCharacter). */
    public static final int SFX_MUSHROOM_BOUNCE = 0x87;
    /** sfx_BlueSphere: a blue sphere turning red underfoot. */
    public static final int SFX_BLUE_SPHERE = 0x65;
    /** mus_ExtraLife: the 1-Up jingle; the driver resumes the previous song after it (Monitor_Give_1up). */
    public static final int MUSIC_EXTRA_LIFE = 0x2A;

    private Sounds() {
    }
}
