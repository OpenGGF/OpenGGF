package eggsky.core;

import com.openggf.mods.scene.SceneContext;
import java.util.HashMap;

/**
 * Sonic 3 &amp; Knuckles music and sound effects by driver id, with each effect rate-limited so
 * a burst of pickups or hits does not machine-gun the same sample, and music only restarted
 * when the track actually changes.
 */
public final class Sound {
    // Sound effects (Sonic3kSfx).
    public static final int RING = 0x33;
    public static final int RING_LEFT = 0x34;
    public static final int DEATH = 0x35;
    public static final int SKID = 0x36;
    public static final int SPIKE_HIT = 0x37;
    public static final int SPLASH = 0x39;
    public static final int SHIELD = 0x3A;
    public static final int ROLL = 0x3C;
    public static final int BREAK = 0x3D;
    public static final int FIRE_SHIELD = 0x3E;
    public static final int LIGHTNING_SHIELD = 0x41;
    public static final int INSTA_SHIELD = 0x42;
    public static final int FIRE_ATTACK = 0x43;
    public static final int ELECTRIC_ATTACK = 0x45;
    public static final int BLAST = 0x48;
    public static final int THUMP = 0x49;
    public static final int GRAB = 0x4A;
    public static final int PROJECTILE = 0x4D;
    public static final int MISSILE_EXPLODE = 0x4E;
    public static final int BOSS_ACTIVATE = 0x50;
    public static final int CHARGING = 0x53;
    public static final int BOSS_LASER = 0x54;
    public static final int COLLAPSE = 0x59;
    public static final int SWITCH = 0x5B;
    public static final int MECHA_SPARK = 0x5C;
    public static final int FLOOR_THUMP = 0x5D;
    public static final int LASER = 0x5E;
    public static final int CRASH = 0x5F;
    public static final int BOSS_ZOOM = 0x60;
    public static final int JUMP = 0x62;
    public static final int STARPOST = 0x63;
    public static final int BLUE_SPHERE = 0x65;
    public static final int ALL_SPHERES = 0x66;
    public static final int PERFECT = 0x68;
    public static final int GOAL = 0x6A;
    public static final int BOSS_HIT = 0x6E;
    public static final int RUMBLE_2 = 0x6F;
    public static final int HOVERPAD = 0x72;
    public static final int TRANSPORTER = 0x73;
    public static final int GRAVITY_MACHINE = 0x78;
    public static final int LIGHTNING = 0x79;
    public static final int FROST_PUFF = 0x7F;
    public static final int ALARM = 0x86;
    public static final int WEATHER_MACHINE = 0x89;
    public static final int DOOR_OPEN = 0x8F;
    public static final int DOOR_CLOSE = 0x91;
    public static final int GHOST_APPEAR = 0x92;
    public static final int BOSS_PROJECTILE = 0x98;
    public static final int PLINK = 0x99;
    public static final int SUPER_EMERALD = 0x9C;
    public static final int TARGETING = 0x9D;
    public static final int CLANK = 0x9E;
    public static final int SUPER_TRANSFORM = 0x9F;
    public static final int MISSILE_SHOOT = 0xA0;
    public static final int OMINOUS = 0xA1;
    public static final int RISE = 0xA5;
    public static final int LAUNCH_READY = 0xA7;
    public static final int ENERGY_ZAP = 0xA8;
    public static final int AIR_DING = 0xA9;
    public static final int BUMPER = 0xAA;
    public static final int SPINDASH = 0xAB;
    public static final int LAUNCH_GO = 0xAD;
    public static final int ENTER_SS = 0xAF;
    public static final int REGISTER = 0xB0;
    public static final int SPRING = 0xB1;
    public static final int ERROR = 0xB2;
    public static final int BIG_RING = 0xB3;
    public static final int EXPLODE = 0xB4;
    public static final int DIAMONDS = 0xB5;
    public static final int DASH = 0xB6;
    public static final int SIGNPOST = 0xB8;
    public static final int RING_LOSS = 0xB9;
    public static final int FLYING = 0xBA;
    public static final int LARGE_SHIP = 0xBD;
    public static final int SIREN = 0xBE;
    public static final int RUMBLE = 0xCB;
    public static final int BIG_RUMBLE = 0xCC;
    public static final int DEATH_EGG_RISE = 0xCD;
    public static final int WIND = 0xCF;
    public static final int RISING = 0xD0;
    public static final int TURBINE = 0xD4;

    // Music (Sonic3kMusic).
    public static final int M_TITLE = 0x25;
    public static final int M_DATA_SELECT = 0x2F;
    public static final int M_SPECIAL = 0x1C;
    public static final int M_MINIBOSS = 0x18;
    public static final int M_BOSS = 0x19;
    public static final int M_KNUCKLES = 0x1F;
    public static final int M_EMERALD = 0x2B;
    public static final int M_INVINCIBLE = 0x2C;
    public static final int M_GAME_OVER = 0x27;
    public static final int M_CONTINUE = 0x28;
    public static final int M_ACT_CLEAR = 0x29;
    public static final int M_ENDING = 0x32;
    public static final int M_CREDITS = 0x33;
    public static final int M_FINAL_BOSS = 0x30;
    public static final int M_SLOTS = 0x1D;
    public static final int M_GUMBALL = 0x1E;
    public static final int M_PACHINKO = 0x1B;
    public static final int M_DDZ = 0x1A;
    public static final int M_SSZ = 0x15;
    public static final int M_COMPETITION = 0x2D;

    private SceneContext ctx;
    private final HashMap<Integer, Long> last = new HashMap<>();
    private long tick;
    private int music = -1;
    public boolean enabled = true;

    public void bind(SceneContext ctx, long tick) {
        this.ctx = ctx;
        this.tick = tick;
    }

    /** Plays an effect unless the same one played within {@code gap} ticks. */
    public void sfx(int id, int gap) {
        if (ctx == null || !enabled) {
            return;
        }
        Long when = last.get(id);
        if (when != null && tick - when < gap) {
            return;
        }
        last.put(id, tick);
        ctx.audio().playSfx(id);
    }

    public void sfx(int id) {
        sfx(id, 3);
    }

    public void music(int id) {
        if (ctx == null || id == music) {
            return;
        }
        music = id;
        ctx.audio().playMusic(id);
    }

    /** Forgets the current track so the next {@link #music} call restarts it. */
    public void resetMusic() {
        music = -1;
    }

    public int currentMusic() {
        return music;
    }

    public void fadeOut() {
        if (ctx != null) {
            ctx.audio().fadeOutMusic();
        }
        music = -1;
    }
}
