package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;

/**
 * The Strange Mushrooms event: two of Mushroom Hill's bouncy caps ({@link MushroomCap}, dark-
 * and light-spotted, swaying as the zone sways them) sprouted on the act's level, a Rhinobot
 * and a Bloominator - the fight's own badniks - lurking in the shade underneath and shuffling
 * now and then. Shown "stomp", the hero hops onto the dark cap (a jump cut short at -$400, as
 * Sonic_JumpHeight cuts a tapped jump), which squashes and springs
 * him off with sfx_MushroomBounce; the badniks wake as their routines do: the Rhinobot revs
 * with sfx_Blast for $20 frames then dashes at $400 (Rhinobot_StartChargePrep /
 * Rhinobot_StartDash), the Bloominator runs AniRaw_BloominatorAttack and lobs its two spore
 * balls (sfx_Projectile, $100 across and $500 up under gravity), and the fight begins. Shown
 * "eat", the hero ducks for a bite of the dark cap, which wobbles through its spring frames
 * (sfx_Bubble, the gulp of an air bubble),
 * hearts rise, Mushroom Hill pollen puffs round his head and he teeters on wobbly legs. The
 * stalks are drawn (in the zone they are level art) and the staging is the mod's own.
 */
final class MushroomsPicture extends StagedPicture {
    private static final int HERO_COL = 18;
    private static final int DARK_COL = 60;
    private static final int LIGHT_COL = 106;
    private static final int DARK_STALK = 14;
    private static final int LIGHT_STALK = 34;
    private static final int RHINO_COL = 52;
    private static final int BLOOM_COL = 96;
    /** sfx_Blast and sfx_Projectile, which only this picture uses. */
    private static final int SFX_BLAST = 0x48;
    private static final int SFX_PROJECTILE = 0x4D;
    /** sfx_Bubble: the gulp of an air bubble. */
    private static final int SFX_BUBBLE = 0x38;
    /** Sonic_JumpHeight's cap on a jump whose button is already released: y_vel -$400. */
    private static final int SHORT_HOP = 0x400;
    /** Rhinobot_StartChargePrep's $2E wait before the dash, and the dash speed. */
    private static final int RHINO_REV = 0x20;
    private static final int RHINO_DASH = -0x400;
    /** How long the stomp plays after the bounce, and the eating (the mod's choices). */
    private static final int STOMP_AFTER = 70;
    private static final int EAT_LENGTH = 110;

    private final String hero;
    private MushroomCap dark;
    private MushroomCap light;
    private byte[] attack;
    private long zoneFrames;
    private String showing;
    private int age;
    // The stomp.
    private Motion jumper;
    private boolean onCap;
    private boolean fullJump;
    private int landingSpeed;
    private int sinceBounce = -1;
    private float rhinoX = RHINO_COL;
    private int bloomStep = -1;
    private int bloomTimer;
    private int bloomFrame;
    private int shots;
    private Motion[] spores = new Motion[2];

    MushroomsPicture(Shell shell) {
        hero = EventHero.id(shell);
        byte[] ani = RomTables.read(shell, RomTables.ANI_MHZ_MUSHROOM_CAP, 0x24);
        byte[] positions = RomTables.read(shell, RomTables.MHZ_MUSHROOM_CAP_POSITIONS, 0x6C);
        attack = RomTables.read(shell, RomTables.ANIRAW_BLOOMINATOR_ATTACK, 19);
        if (ani != null && positions != null && attack != null && shell.art.romFrame("mhz_mushroom_cap", 0) != null) {
            dark = new MushroomCap(ani, positions, 0);
            light = new MushroomCap(ani, positions, MushroomCap.LIGHT_OFFSET);
        }
    }

    @Override
    void show(Shell shell, String detail) {
        if (dark == null || showing != null) {
            return;
        }
        showing = detail;
        age = 0;
        if (detail.equals("stomp")) {
            float startY = floor(HERO_COL) - EventHero.standRadius(hero);
            float contactY = capY(dark, DARK_COL, DARK_STALK) - 0x12 - EventHero.rollRadius();
            int speed = Motion.jumpSpeed(hero);
            int frames = 1 + Motion.framesToLand(SHORT_HOP, contactY - startY + speed / 256f);
            fullJump = frames <= 0;
            if (fullJump) {
                // The cap stands too high for a hop: hold the button for the whole jump.
                frames = Motion.framesToLand(speed, contactY - startY);
            }
            jumper = new Motion(HERO_COL, startY);
            jumper.yVel = -speed;
            jumper.xVel = Math.round((DARK_COL - HERO_COL) * 256f / Math.max(1, frames));
            shell.sfx(Sounds.SFX_JUMP);
        }
    }

    @Override
    boolean busy() {
        if (showing == null) {
            return false;
        }
        return showing.equals("stomp") ? sinceBounce < STOMP_AFTER : age < EAT_LENGTH;
    }

    @Override
    void tick(Shell shell) {
        if (dark == null) {
            return;
        }
        zoneFrames++;
        dark.tick();
        light.tick();
        if (showing == null) {
            return;
        }
        age++;
        if (showing.equals("stomp")) {
            stomp(shell);
        } else if (age == 10) {
            dark.spring(); // the cap wobbles as a piece is broken off (nobody is on it to launch)
        } else if (age == 24) {
            shell.sfx(SFX_BUBBLE);
        }
    }

    private void stomp(Shell shell) {
        if (onCap) {
            // Standing on the cap while it squashes; launched on its spring-up frame.
            jumper.place(DARK_COL, capY(dark, DARK_COL, DARK_STALK) - dark.surface() - EventHero.standRadius(hero));
            if (dark.launching()) {
                onCap = false;
                jumper.yVel = MushroomCap.launchSpeed(landingSpeed);
                sinceBounce = 0;
                shell.sfx(Sounds.SFX_MUSHROOM_BOUNCE);
                shell.sfx(SFX_BLAST);
            }
        } else if (jumper != null) {
            jumper.step(Motion.PLAYER_GRAVITY);
            // A short hop onto the cap: Sonic_JumpHeight caps a released jump at -$400.
            if (sinceBounce < 0 && !fullJump && jumper.yVel < -SHORT_HOP) {
                jumper.yVel = -SHORT_HOP;
            }
            float top = capY(dark, DARK_COL, DARK_STALK) - 0x12 - EventHero.rollRadius();
            if (jumper.yVel > 0 && jumper.y() >= top) {
                // SolidObjectTop: the hero lands on the cap (the mod stops his run there).
                landingSpeed = jumper.yVel;
                jumper.xVel = 0;
                jumper.yVel = 0;
                onCap = true;
                dark.spring();
            }
        }
        if (sinceBounce < 0) {
            return;
        }
        sinceBounce++;
        if (sinceBounce > RHINO_REV) {
            rhinoX += RHINO_DASH / 256f;
        }
        bloominator(shell);
    }

    /** Animate_RawMultiDelay over AniRaw_BloominatorAttack, firing on its 4th and 8th steps. */
    private void bloominator(Shell shell) {
        for (Motion spore : spores) {
            if (spore != null) {
                spore.step(Motion.PLAYER_GRAVITY);
            }
        }
        if (bloomStep == -1) {
            bloomStep = 0;
            bloomTimer = 0;
        }
        if (--bloomTimer >= 0) {
            return;
        }
        int next = bloomStep + 2;
        int frame = attack[next] & 0xFF;
        if (frame >= 0x80) {
            return; // $F4: the attack is over
        }
        bloomStep = next;
        bloomFrame = frame;
        bloomTimer = attack[next + 1];
        if ((next == 6 || next == 0x0E) && shots < 2) {
            // S3KBadnikProjectile from (0, -$10): $100 across (every other one mirrored), $500 up.
            Motion spore = new Motion(BLOOM_COL, bloomCentre() - 0x10);
            spore.xVel = shots == 0 ? -0x100 : 0x100;
            spore.yVel = -0x500;
            spores[shots++] = spore;
            shell.sfx(SFX_PROJECTILE);
        }
    }

    /** A cap's centre row: on its stalk, nudged by the sway. */
    private int capY(MushroomCap cap, int col, int stalk) {
        return floor(col) - stalk - 3 + cap.dy(MushroomCap.counter(zoneFrames));
    }

    private int bloomCentre() {
        return floor(BLOOM_COL) - 0x18;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        drawLevel(shell, c, x, y, w, h);
        if (dark == null) {
            return;
        }
        int counter = MushroomCap.counter(zoneFrames);
        boolean awake = sinceBounce >= 0;
        // The lurkers, in the caps' shade until they wake.
        SceneDraw shade = awake ? SceneDraw.plain() : SceneDraw.plain().withTint(0xFF585060);
        boolean shuffle = !awake && (t / 10) % 9 == 0;
        float rx = x + rhinoX + (shuffle ? 1 : 0);
        Poses.stand(c, shell.art.romFrame("rhinobot", shuffle ? 1 : 0), rx, y + floor(RHINO_COL), shade);
        int bloom = awake ? bloomFrame : ((t / 14) % 7 == 0 ? 1 : 0);
        c.draw(shell.art.romFrame("bloominator", bloom), x + BLOOM_COL, y + bloomCentre(), shade);
        drawCap(shell, c, "mhz_mushroom_cap_light", light, x, y, LIGHT_COL, LIGHT_STALK, counter);
        drawCap(shell, c, "mhz_mushroom_cap", dark, x, y, DARK_COL, DARK_STALK, counter);
        for (Motion spore : spores) {
            if (spore != null) {
                c.draw(shell.art.romFrame("bloominator", 4), x + spore.x(), y + spore.y(), SceneDraw.plain());
            }
        }
        drawHero(shell, c, x, y, t);
    }

    private void drawCap(Shell shell, SceneCanvas c, String key, MushroomCap cap, int x, int y, int col, int stalk,
            int counter) {
        int cx = x + col;
        int top = y + floor(col) - stalk;
        // The stalk: drawn, as Mushroom Hill's stalks are level art.
        c.fill(cx - 5, top, 10, stalk, 0xFF6C4824);
        c.fill(cx - 4, top, 8, stalk, 0xFFDAB46C);
        c.fill(cx + 1, top, 3, stalk, 0xFFB4904C);
        c.draw(shell.art.romFrame(key, cap.frame()), cx + cap.dx(counter), top - 3 + cap.dy(counter),
                SceneDraw.plain());
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, long t) {
        if (showing == null) {
            Poses.hero(shell, c, hero, Poses.WAIT, t, x + HERO_COL, y + floor(HERO_COL), SceneDraw.plain());
            return;
        }
        if (showing.equals("stomp")) {
            int anim = onCap ? Poses.WAIT : sinceBounce >= 0 ? Poses.SPRING : Poses.ROLL;
            EventHero.at(shell, c, hero, anim, t, x + jumper.x(), y + jumper.y(), SceneDraw.plain());
            return;
        }
        int feet = y + floor(HERO_COL);
        if (age < 24) {
            Poses.hero(shell, c, hero, Poses.DUCK, t, x + HERO_COL, feet, SceneDraw.plain());
            return;
        }
        Poses.hero(shell, c, hero, age < 40 ? Poses.WAIT : EventHero.BALANCE, t, x + HERO_COL, feet,
                SceneDraw.plain());
        if (age < 70) {
            EventArt.hearts(c, x + HERO_COL - 2, feet - 40, t);
        }
        if (age >= 36) {
            // Spores puffing round his head.
            for (int i = 0; i < 6; i++) {
                double a = (age + i * 13) * 0.08;
                int px = x + HERO_COL + (int) Math.round(Math.cos(a) * 14);
                int py = feet - 44 + (int) Math.round(Math.sin(a * 1.3) * 6) - ((age + i * 9) % 30) / 5;
                c.draw(shell.art.romFrame("mhz_pollen", i % 4), px, py, SceneDraw.plain());
            }
        }
    }
}
