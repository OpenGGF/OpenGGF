package flappytails;

/**
 * Sonic 3 &amp; Knuckles sound driver ids, named as the disassembly's
 * {@code sonic3k.constants.asm} names them ({@code mus_*} and {@code sfx_*}). The mod plays
 * them with {@code SceneContext.audio()}; it ships no sound of its own.
 */
final class Sounds {
    private Sounds() { }

    static final int MUS_TITLE = 0x2F;          // mus_DataSelect: the menu theme
    static final int MUS_EXTRA_LIFE = 0x2A;     // mus_ExtraLife: the 1-up jingle, which resumes the zone music
    static final int MUS_GAME_OVER = 0x27;      // mus_GameOver
    static final int MUS_INVINCIBLE = 0x2C;     // mus_Invincibility
    static final int MUS_ACT_CLEAR = 0x29;      // mus_GotThroughAct

    static final int SFX_RING = 0x33;           // sfx_RingRight
    static final int SFX_DEATH = 0x35;          // sfx_Death
    static final int SFX_SPIKE = 0x37;          // sfx_SpikeHit
    static final int SFX_JUMP = 0x62;           // sfx_Jump
    static final int SFX_STARPOST = 0x63;       // sfx_Starpost
    static final int SFX_GATE = 0x65;           // sfx_BlueSphere: the special stage's ding
    static final int SFX_PERFECT = 0x68;        // sfx_Perfect
    static final int SFX_SWITCH = 0x5B;         // sfx_Switch: menu clicks
    static final int SFX_BIG_RING = 0xB3;       // sfx_BigRing
    static final int SFX_EXPLODE = 0xB4;        // sfx_Explode
    static final int SFX_RING_LOSS = 0xB9;      // sfx_RingLoss
    static final int SFX_FLYING = 0xBA;         // sfx_Flying: the rotor buzz
    static final int SFX_FLY_TIRED = 0xBB;      // sfx_FlyTired
    static final int SFX_REGISTER = 0xB0;       // sfx_Register: the results tally
    static final int SFX_ERROR = 0xB2;          // sfx_Error
    static final int SFX_SPRING = 0xB1;         // sfx_Spring
    static final int SFX_DASH = 0xB6;           // sfx_Dash
    static final int SFX_WHISTLE = 0x46;        // sfx_Whistle
    static final int SFX_SUPER = 0x9F;          // sfx_SuperTransform
}
