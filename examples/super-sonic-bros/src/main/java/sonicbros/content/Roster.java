package sonicbros.content;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import sonicbros.sim.FighterDef;
import sonicbros.sim.FighterDef.Poses;
import sonicbros.sim.FighterDef.Slot;
import sonicbros.sim.FighterDef.Stats;
import sonicbros.sim.FighterDef.Throw;
import sonicbros.sim.Hit;
import sonicbros.sim.Move;
import sonicbros.sim.Pad;
import sonicbros.sim.Projectile;
import sonicbros.sim.Sfx;

/**
 * The seven fighters. Pose numbers for Sonic, Tails, Knuckles, Mecha Sonic and Rhinobot are
 * mapping frames of their Sonic 3 &amp; Knuckles sprites ({@code Map_Sonic}, {@code Map_Tails},
 * {@code Map_Knuckles}, {@code Map_MechaSonic}, {@code Map_Rhinobot}); Robotnik and Egg Robo are
 * assembled from several ROM pieces, so their poses are the view's own numbers
 * ({@link sonicbros.view.FighterArt}). {@link #FLIP} draws a pose mirrored (back attacks).
 *
 * <p>Damage is in percent, knockback in the platform-fighter units {@link sonicbros.sim.Knockback}
 * documents. Tuned with {@code BalanceProbe} so that level-9 CPUs win every pairing between
 * roughly 40% and 60% of the time.
 */
public final class Roster {
    /** Draw this pose mirrored. */
    public static final int FLIP = 0x10000;

    // Robotnik's assembled poses.
    public static final int EGG_IDLE = 0;
    public static final int EGG_LAUGH = 1;
    public static final int EGG_HURT = 2;
    public static final int EGG_ATTACK = 3;
    public static final int EGG_DIZZY = 4;
    public static final int EGG_THRUST = 5;
    // Egg Robo's assembled poses.
    public static final int ROBO_IDLE = 0;
    public static final int ROBO_WALK = 1;
    public static final int ROBO_THRUST = 2;
    public static final int ROBO_AIM = 3;
    public static final int ROBO_HURT = 4;
    public static final int ROBO_PUNCH = 5;
    public static final int ROBO_KICK = 6;
    public static final int ROBO_SPIN = 7;

    private final List<FighterDef> fighters = new ArrayList<>();

    public Roster() {
        fighters.add(sonic());
        fighters.add(tails());
        fighters.add(knuckles());
        fighters.add(robotnik());
        fighters.add(mecha());
        fighters.add(eggRobo());
        fighters.add(rhinobot());
    }

    public List<FighterDef> all() { return fighters; }

    public FighterDef get(int index) { return fighters.get(Math.floorMod(index, fighters.size())); }

    public FighterDef byId(String id) {
        for (FighterDef f : fighters) if (f.id.equals(id)) return f;
        return null;
    }

    public int indexOf(String id) {
        for (int i = 0; i < fighters.size(); i++) if (fighters.get(i).id.equals(id)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ helpers

    private static int[] a(int... v) { return v; }

    private static Hit h(int start, int end, double x, double y, double r, double dmg, int angle, double bkb, double kbg) {
        return Hit.of(start, end, x, y, r, dmg, angle, bkb, kbg);
    }

    private static Throw throwOf(int frame, int duration, double dmg, int angle, double bkb, double kbg, int pose) {
        return new Throw(frame, duration, dmg, angle, bkb, kbg, pose, 22, -6);
    }

    private static Projectile.Def shot(int kind, double vx, double vy, double gravity, int life, double r, Hit hit,
            Hit blast, int bounces, boolean mine, boolean pierce, int max, boolean ground) {
        return new Projectile.Def(kind, vx, vy, gravity, life, r, hit, blast, bounces, mine, pierce, max, ground);
    }

    /** The ball-spin poses all three heroes share in their sprite sets. */
    private static final int BALL_A = 0x96;

    // ------------------------------------------------------------------ Sonic

    private FighterDef sonic() {
        Stats s = new Stats(85, 1.6, 3.7, 0.32, 0.22, 2.1, 0.12, 0.03, 0.30, 3.9, 6.0, 6.8, 4.4, 6.4, 1, 18, 34, 0, 3);
        int[] ball = a(0x96, 0x97, 0x96, 0x98, 0x96, 0x99, 0x96, 0x9A);
        Poses p = new Poses(a(0xBA), a(7, 8, 1, 2, 3, 4, 5, 6), a(0x21, 0x22, 0x23, 0x24), ball, ball, ball, a(0x9C),
                a(0x9B), a(0x8C), a(0x8C, 0x8D), a(0x9C), a(0x80), ball, a(0xA4, 0xA5, 0xA6),
                a(0xB0, 0xB1, 0xB2, 0xB3), a(0x8E), a(0x9F), a(0x9D, 0x9E, 0x9F, 0xA0), a(0xCC), 4);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("sonic.jab", "Quick Jab", 16).pose(0x9E, 3).pose(0x9F, 6).pose(0x9E, 7)
                .hit(h(3, 5, 14, -4, 9, 3, Hit.SAKURAI, 18, 25)).sound(2, Sfx.SKID).build());
        m.put(Slot.FTILT, Move.of("sonic.ftilt", "Side Kick", 22).pose(0x9D, 4).pose(0xAC, 9).pose(0x9D, 9)
                .hit(h(5, 8, 16, 4, 10, 7, Hit.SAKURAI, 22, 85)).sound(4, Sfx.ROLL).build());
        m.put(Slot.UTILT, Move.of("sonic.utilt", "Spin Kick", 24).pose(0x9B, 4).cycle(12, 2, 0x96, 0x97, 0x98).pose(0x8E, 8)
                .hit(h(5, 12, 2, -20, 12, 6, 88, 25, 90).group(0)).addV(4, 0, -1.5).build());
        m.put(Slot.DTILT, Move.of("sonic.dtilt", "Spin Sweep", 20).pose(0x9C, 3).cycle(10, 2, 0x86, 0x87, 0x88).pose(0x9C, 7)
                .hit(h(4, 9, 14, 12, 10, 6, 75, 30, 50).groundOnly()).build());
        m.put(Slot.DASH, Move.of("sonic.dash", "Rolling Tackle", 30).pose(0x9B, 3).cycle(18, 2, ball).pose(0x9B, 9)
                .hit(h(4, 9, 8, 2, 12, 8, 50, 40, 70)).hit(h(10, 20, 8, 2, 11, 5, 60, 35, 60).group(1))
                .setV(3, 4.2, Double.NaN).slide().sound(3, Sfx.ROLL).build());
        m.put(Slot.FSMASH, Move.of("sonic.fsmash", "Wind-up Punch", 44).pose(0xA7, 14).pose(0x9F, 10).pose(0x9E, 20)
                .hit(h(15, 18, 18, -4, 11, 14, Hit.SAKURAI, 30, 98).fx(Hit.FX_HEAVY)).charge(10)
                .addV(14, 2.2, Double.NaN).sound(14, Sfx.DASH).build());
        m.put(Slot.USMASH, Move.of("sonic.usmash", "Updraft Spin", 46).pose(0x9B, 12).cycle(16, 2, ball).pose(0x8E, 18)
                .hit(h(12, 14, 0, -22, 13, 4, 100, 50, 30).group(0)).hit(h(15, 26, 0, -26, 14, 11, 88, 34, 100).group(1))
                .charge(8).sound(12, Sfx.ROLL).build());
        m.put(Slot.DSMASH, Move.of("sonic.dsmash", "Breakdance", 42).pose(0x9C, 9).cycle(18, 2, 0xC8, 0xC9, 0xCA, 0xCB, 0xCC, 0xCD, 0xCE, 0xCF).pose(0x9C, 15)
                .hit(h(10, 13, 15, 10, 11, 12, 30, 28, 96).groundOnly()).hit(h(14, 18, -15, 10, 11, 11, 30, 28, 96).group(1).groundOnly())
                .charge(6).sound(10, Sfx.ROLL).build());
        m.put(Slot.NAIR, Move.of("sonic.nair", "Spin Attack", 32).cycle(32, 2, ball)
                .hit(h(3, 6, 0, 0, 13, 8, 361, 25, 85)).hit(h(7, 22, 0, 0, 12, 5, 361, 15, 70).group(1))
                .landingLag(8).build());
        m.put(Slot.FAIR, Move.of("sonic.fair", "Somersault Kick", 34).pose(0x9D, 5).pose(0xAC, 12).pose(0x96, 17)
                .hit(h(6, 10, 14, 6, 11, 10, 45, 25, 98)).landingLag(12).build());
        m.put(Slot.BAIR, Move.of("sonic.bair", "Back Kick", 32).pose(0xAC | FLIP, 4).pose(0xAC | FLIP, 10).pose(0x96, 18)
                .hit(h(6, 9, -16, 2, 11, 11, 40, 28, 96)).landingLag(10).build());
        m.put(Slot.UAIR, Move.of("sonic.uair", "Rising Twirl", 30).cycle(30, 2, 0xC8, 0xC9, 0xCA, 0xCB)
                .hit(h(4, 8, 0, -18, 13, 8, 85, 28, 95)).landingLag(9).build());
        m.put(Slot.DAIR, Move.of("sonic.dair", "Stomp", 38).pose(0x9B, 8).cycle(30, 2, ball)
                .hit(h(9, 11, 0, 14, 10, 11, 270, 20, 80)).hit(h(12, 24, 0, 12, 10, 7, 60, 25, 70).group(1))
                .setV(8, 0, 6.5).landingLag(16).sound(8, Sfx.ROLL).build());
        m.put(Slot.NSPEC, Move.of("sonic.homing", "Homing Attack", 40).pose(0x9B, 4).cycle(36, 2, ball)
                .hit(h(10, 30, 0, 0, 13, 9, 50, 40, 70).fx(Hit.FX_SPIN)).custom(Move.Custom.HOMING)
                .gravity(0, 9, 0.2).sound(2, Sfx.ROLL).landingLag(10).build());
        m.put(Slot.SSPEC, Move.of("sonic.spindash", "Spin Dash", 40).cycle(14, 2, 0x86, 0x87, 0x86, 0x88, 0x86, 0x89, 0x86, 0x8A)
                .cycle(26, 2, ball)
                .hit(h(14, 39, 6, 4, 12, 10, 40, 42, 72).fx(Hit.FX_SPIN))
                .holdLoop(Pad.SPECIAL, 4, 14, 60).custom(Move.Custom.SPIN_DASH).slide()
                .sound(0, Sfx.SPINDASH).landingLag(8).build());
        m.put(Slot.USPEC, Move.of("sonic.spring", "Spring Jump", 26).pose(0x9B, 2).pose(0x8E, 24)
                .custom(Move.Custom.SPRING).ledgeGrab().landingLag(10).build());
        m.put(Slot.DSPEC, Move.of("sonic.insta", "Insta-Shield", 22).pose(0x96, 2).cycle(20, 2, ball)
                .hit(h(2, 6, 0, 0, 22, 5, 70, 55, 40).fx(Hit.FX_ELECTRIC)).intangible(1, 6).reflect(1, 8)
                .sound(1, Sfx.INSTA).landingLag(6).build());
        addCommon(m, "sonic", 0x9F, ball);
        return new FighterDef("sonic", "SONIC", "Fast, light and slippery. Spin Dash in, Homing Attack after, Insta-Shield reflects shots.",
                s, p, m, throwOf(8, 22, 8, 45, 60, 55, 0x9F), throwOf(10, 26, 10, 45, 65, 68, 0x9F),
                throwOf(8, 24, 6, 90, 72, 55, 0x8E), throwOf(10, 26, 5, 80, 60, 38, 0x9C), 190, 60);
    }

    // ------------------------------------------------------------------ Tails

    private FighterDef tails() {
        Stats s = new Stats(78, 1.5, 3.1, 0.26, 0.2, 2.2, 0.13, 0.02, 0.22, 3.0, 5.0, 6.2, 4.0, 5.9, 2, 18, 30, 0, 3);
        int[] ball = a(0x96, 0x97, 0x98);
        int[] fly = a(0xBD, 0xBE, 0xBF, 0xC0, 0xC1);
        int[] swipe = a(0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA);
        Poses p = new Poses(a(0xAD), a(7, 8, 1, 2, 3, 4, 5, 6), a(0x21, 0x22, 0x23, 0x24), ball, a(0xA7, 0xA8), fly, a(0x99),
                a(0x99), a(0x9C), a(0x9C, 0x9F), a(0x99), a(0x80), ball, a(0x9A, 0x9B),
                a(0xB0, 0xB1, 0xB2, 0xB3), a(0xC2, 0xCD, 0xCE), a(0xA4), a(0x8E, 0x8F), a(0xB6), 4);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("tails.jab", "Tail Swipe", 18).cycle(12, 2, swipe).pose(0xAD, 6)
                .hit(h(2, 9, 12, 2, 12, 1.5, 361, 10, 15).rehit(4)).hit(h(10, 11, 14, 0, 13, 2.5, 361, 30, 60).group(1))
                .sound(1, Sfx.ROLL).build());
        m.put(Slot.FTILT, Move.of("tails.ftilt", "Tail Lash", 22).pose(0xC5, 3).cycle(10, 2, swipe).pose(0xAD, 9)
                .hit(h(4, 8, 16, 2, 12, 7, Hit.SAKURAI, 25, 80)).build());
        m.put(Slot.UTILT, Move.of("tails.utilt", "Rotor Tail", 22).cycle(16, 2, 0xC3, 0xC4).pose(0xAD, 6)
                .hit(h(3, 13, 0, -20, 14, 2, 90, 30, 30).rehit(5)).hit(h(14, 15, 0, -22, 15, 3, 88, 40, 90).group(1)).build());
        m.put(Slot.DTILT, Move.of("tails.dtilt", "Low Sweep", 16).pose(0x99, 3).cycle(8, 2, swipe).pose(0x99, 5)
                .hit(h(3, 7, 14, 12, 10, 5, 80, 30, 40).groundOnly()).build());
        m.put(Slot.DASH, Move.of("tails.dash", "Tail Spin Dash", 30).cycle(20, 2, ball).pose(0x99, 10)
                .hit(h(3, 18, 4, 2, 13, 2, 361, 18, 20).rehit(5)).hit(h(19, 20, 6, 0, 13, 4, 50, 45, 60).group(1))
                .setV(2, 3.6, Double.NaN).slide().sound(2, Sfx.ROLL).build());
        m.put(Slot.FSMASH, Move.of("tails.fsmash", "Twin Tail Strike", 44).pose(0x99, 14).cycle(14, 2, swipe).pose(0xAD, 16)
                .hit(h(14, 16, 16, 0, 12, 6, 361, 20, 30)).hit(h(19, 22, 20, 0, 13, 12, Hit.SAKURAI, 34, 106).group(1))
                .charge(10).sound(14, Sfx.ROLL).build());
        m.put(Slot.USMASH, Move.of("tails.usmash", "Propeller Uppercut", 42).pose(0x99, 10).cycle(20, 2, 0xC3, 0xC4).pose(0xAD, 12)
                .hit(h(10, 18, 0, -20, 15, 2, 95, 20, 20).rehit(4)).hit(h(19, 21, 0, -26, 16, 10, 88, 42, 108).group(1))
                .charge(7).build());
        m.put(Slot.DSMASH, Move.of("tails.dsmash", "Whirlwind", 40).pose(0x99, 9).cycle(18, 2, swipe).pose(0x99, 13)
                .hit(h(9, 12, 16, 10, 12, 11, 30, 26, 92).groundOnly()).hit(h(13, 17, -16, 10, 12, 10, 30, 26, 92).group(1).groundOnly())
                .charge(6).build());
        m.put(Slot.NAIR, Move.of("tails.nair", "Spin Ball", 30).cycle(30, 2, ball)
                .hit(h(3, 18, 0, 0, 12, 1.6, 361, 8, 15).rehit(4)).hit(h(19, 20, 0, 0, 13, 4, 361, 40, 70).group(1))
                .landingLag(7).build());
        m.put(Slot.FAIR, Move.of("tails.fair", "Tail Fan", 30).cycle(14, 2, swipe).pose(0xA7, 16)
                .hit(h(4, 9, 14, 0, 13, 9, 45, 28, 98)).landingLag(10).build());
        m.put(Slot.BAIR, Move.of("tails.bair", "Back Lash", 28).cycle(12, 2, 0xC5 | FLIP, 0xC6 | FLIP, 0xC7 | FLIP).pose(0xA7, 16)
                .hit(h(5, 8, -15, 0, 12, 10, 40, 28, 98)).landingLag(9).build());
        m.put(Slot.UAIR, Move.of("tails.uair", "Rotor", 30).cycle(22, 2, 0xC3, 0xC4).pose(0xA7, 8)
                .hit(h(3, 16, 0, -18, 14, 1.5, 90, 20, 15).rehit(4)).hit(h(17, 18, 0, -20, 15, 6, 85, 44, 104).group(1))
                .landingLag(8).build());
        m.put(Slot.DAIR, Move.of("tails.dair", "Drill Tail", 32).cycle(22, 2, ball).pose(0xA7, 10)
                .hit(h(4, 16, 0, 14, 10, 1.4, 280, 10, 10).rehit(4)).hit(h(17, 18, 0, 14, 11, 4, 280, 30, 60).group(1))
                .landingLag(14).build());
        Projectile.Def ringBomb = shot(Projectile.K_RING, 2.6, -2.8, 0.18, 110, 7, h(0, 0, 0, 0, 7, 3, 60, 20, 20),
                h(0, 0, 0, 0, 22, 7, 55, 35, 75).fx(Hit.FX_EXPLOSION), 0, false, false, 2, false);
        m.put(Slot.NSPEC, Move.of("tails.ringbomb", "Dummy Ring Bomb", 34).pose(0xA4, 10).pose(0xAD, 24)
                .spawn(10, ringBomb, 10, -6).sound(10, Sfx.MISSILE_THROW).landingLag(8).build());
        m.put(Slot.SSPEC, Move.of("tails.propdash", "Propeller Dash", 36).cycle(36, 2, 0xC3, 0xC4)
                .hit(h(4, 26, 8, 0, 13, 1.6, 361, 12, 10).rehit(4)).hit(h(27, 28, 10, 0, 14, 5, 40, 45, 70).group(1))
                .setV(3, 4.2, -1.2).gravity(0, 30, 0.25).airControl(0.2).slide().sound(3, Sfx.FLYING).landingLag(10).build());
        m.put(Slot.USPEC, Move.of("tails.flight", "Flight", 40).pose(0x99, 4).cycle(36, 3, fly)
                .hit(h(8, 40, 0, -18, 12, 2, 85, 30, 40).rehit(12)).setV(6, Double.NaN, -4.2)
                .custom(Move.Custom.FLIGHT).ledgeGrab().landingLag(8).sound(6, Sfx.FLYING).build());
        m.put(Slot.DSPEC, Move.of("tails.tailspin", "Tail Tornado", 36).cycle(30, 2, swipe).pose(0xAD, 6)
                .hit(h(4, 24, 0, 2, 18, 1.5, 361, 10, 15).rehit(5)).hit(h(25, 26, 0, 2, 19, 4, 70, 45, 70).group(1))
                .gravity(0, 30, 0.3).sound(4, Sfx.ROLL).landingLag(8).build());
        addCommon(m, "tails", 0xA4, ball);
        return new FighterDef("tails", "TAILS", "Featherweight flyer: three jumps, a long Flight recovery and Dummy Ring Bombs.",
                s, p, m, throwOf(8, 22, 7, 45, 55, 55, 0xA4), throwOf(10, 26, 9, 45, 60, 65, 0xA4),
                throwOf(8, 24, 6, 90, 70, 55, 0xA7), throwOf(10, 26, 5, 75, 55, 35, 0x99), 230, 120);
    }

    // ------------------------------------------------------------------ Knuckles

    private FighterDef knuckles() {
        Stats s = new Stats(108, 1.4, 2.7, 0.24, 0.24, 1.8, 0.1, 0.03, 0.33, 4.3, 6.4, 6.9, 4.4, 6.4, 1, 20, 36, 0, 4);
        int[] ball = a(0x9A, 0x96, 0x9A, 0x97, 0x9A, 0x98, 0x9A, 0x99);
        int[] glide = a(0xC0);
        Poses p = new Poses(a(0x56), a(7, 8, 1, 2, 3, 4, 5, 6), a(0x21, 0x22, 0x23, 0x24), ball, ball, ball, a(0x9C),
                a(0x9B), a(0x8C), a(0x8C, 0x8D), a(0x9C), a(0xB5), ball, a(0xA1, 0xA2, 0xA3),
                a(0xE4, 0xE5), a(0xCA, 0xCB), a(0xDD), a(0x9D, 0x9E), a(0xCC), 4);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("knuckles.jab", "One-Two", 20).pose(0xDC, 3).pose(0xDE, 6).pose(0xDD, 3).pose(0xDE, 4).pose(0x56, 4)
                .hit(h(3, 5, 15, -4, 10, 3, Hit.SAKURAI, 15, 25)).hit(h(10, 12, 17, -4, 11, 4, Hit.SAKURAI, 30, 50).group(1))
                .build());
        m.put(Slot.FTILT, Move.of("knuckles.ftilt", "Hook", 26).pose(0xDC, 7).pose(0xDE, 8).pose(0xDD, 11)
                .hit(h(7, 10, 18, -4, 12, 10, Hit.SAKURAI, 25, 88).fx(Hit.FX_HEAVY)).build());
        m.put(Slot.UTILT, Move.of("knuckles.utilt", "Uppercut", 28).pose(0x9C, 6).pose(0xE4, 10).pose(0x56, 12)
                .hit(h(6, 11, 6, -26, 13, 9, 90, 30, 92)).build());
        m.put(Slot.DTILT, Move.of("knuckles.dtilt", "Low Jab", 18).pose(0x9C, 4).pose(0xCC, 7).pose(0x9C, 7)
                .hit(h(4, 7, 18, 12, 10, 6, 72, 35, 45).groundOnly()).build());
        m.put(Slot.DASH, Move.of("knuckles.dash", "Belly Slide", 34).pose(0xCB, 4).pose(0xCC, 18).pose(0xCD, 12)
                .hit(h(4, 12, 12, 10, 12, 10, 40, 40, 70)).hit(h(13, 21, 12, 10, 11, 6, 45, 30, 60).group(1))
                .setV(3, 4.0, Double.NaN).slide().sound(3, Sfx.SLIDE_SKID).build());
        m.put(Slot.FSMASH, Move.of("knuckles.fsmash", "Hammer Punch", 50).pose(0xDB, 18).pose(0xDE, 12).pose(0xDD, 20)
                .hit(h(18, 21, 22, -4, 13, 17, Hit.SAKURAI, 32, 97).fx(Hit.FX_HEAVY)).charge(12)
                .addV(17, 2.0, Double.NaN).sound(17, Sfx.BOSS_ZOOM).build());
        m.put(Slot.USMASH, Move.of("knuckles.usmash", "Rising Fist", 48).pose(0x9C, 14).pose(0xE4, 18).pose(0x56, 16)
                .hit(h(14, 19, 4, -30, 14, 16, 90, 36, 96).fx(Hit.FX_HEAVY)).charge(9).build());
        m.put(Slot.DSMASH, Move.of("knuckles.dsmash", "Ground Smash", 48).pose(0xE4, 16).pose(0x9C, 32)
                .hit(h(16, 20, 18, 12, 14, 15, 35, 30, 92).groundOnly().fx(Hit.FX_HEAVY))
                .hit(h(16, 20, -18, 12, 14, 15, 35, 30, 92).group(1).groundOnly().fx(Hit.FX_HEAVY))
                .charge(10).sound(16, Sfx.FLOOR_THUMP).build());
        m.put(Slot.NAIR, Move.of("knuckles.nair", "Spin Fist", 34).cycle(34, 2, ball)
                .hit(h(4, 8, 0, 0, 14, 10, 361, 28, 85)).hit(h(9, 22, 0, 0, 13, 6, 361, 20, 70).group(1)).landingLag(10).build());
        m.put(Slot.FAIR, Move.of("knuckles.fair", "Hammer Down", 40).pose(0xE4, 10).pose(0xDE, 12).pose(0x96, 18)
                .hit(h(11, 14, 16, 4, 11, 14, 300, 30, 80).fx(Hit.FX_HEAVY)).hit(h(11, 14, 10, -6, 9, 10, 45, 30, 80).group(1))
                .landingLag(16).build());
        m.put(Slot.BAIR, Move.of("knuckles.bair", "Back Fist", 32).pose(0xDE | FLIP, 6).pose(0xDE | FLIP, 10).pose(0x96, 16)
                .hit(h(6, 9, -18, -2, 11, 13, 40, 30, 100)).landingLag(11).build());
        m.put(Slot.UAIR, Move.of("knuckles.uair", "Sky Uppercut", 32).pose(0x96, 4).pose(0xE4, 12).pose(0x96, 16)
                .hit(h(5, 10, 2, -22, 13, 10, 85, 28, 93)).landingLag(10).build());
        m.put(Slot.DAIR, Move.of("knuckles.dair", "Drill Dive", 42).pose(0x9B, 8).cycle(34, 2, ball)
                .hit(h(9, 12, 0, 14, 12, 15, 270, 25, 85).fx(Hit.FX_HEAVY)).hit(h(13, 26, 0, 12, 11, 9, 60, 25, 70).group(1))
                .setV(8, 0, 6.8).landingLag(18).build());
        m.put(Slot.NSPEC, Move.of("knuckles.maxheat", "Maximum Heat", 52).pose(0xDB, 14).pose(0xDE, 18).pose(0xDD, 20)
                .hit(h(30, 34, 20, -4, 15, 14, Hit.SAKURAI, 40, 85).fx(Hit.FX_FIRE))
                .holdLoop(Pad.SPECIAL, 6, 14, 90).armor(14, 32).chargedV(30, 2.6, Double.NaN)
                .sound(6, Sfx.CHARGING).sound(30, Sfx.FIRE_ATTACK).landingLag(12).build());
        m.put(Slot.SSPEC, Move.of("knuckles.glide", "Glide", 40).pose(0xC0, 40)
                .hit(h(6, 39, 14, -2, 12, 9, 45, 40, 70).group(0)).custom(Move.Custom.GLIDE).ledgeGrab()
                .setV(5, 3.0, Double.NaN).landingLag(12).build());
        m.put(Slot.USPEC, Move.of("knuckles.spiral", "Spiral Upper", 40).pose(0x9B, 5).cycle(20, 2, ball).pose(0xE4, 15)
                .hit(h(5, 7, 0, -4, 14, 4, 90, 60, 30)).hit(h(8, 22, 0, -10, 14, 1.5, 90, 30, 10).group(1).rehit(4))
                .hit(h(23, 25, 2, -20, 15, 6, 80, 50, 90).group(2))
                .setV(5, Double.NaN, -8.6).airControl(0.6).helpless().ledgeGrab().landingLag(16).sound(5, Sfx.ROLL).build());
        m.put(Slot.DSPEC, Move.of("knuckles.quake", "Earthquake", 48).pose(0xE4, 10).cycle(12, 2, ball).pose(0x9C, 26)
                .hit(h(10, 21, 0, 14, 12, 10, 270, 30, 70)).hit(h(23, 28, 0, 10, 36, 9, 80, 45, 70).group(1).groundOnly().fx(Hit.FX_HEAVY))
                .setV(10, 0, 7.5).untilLand(10, 22, 22).landingLag(16).sound(23, Sfx.BIG_RUMBLE).build());
        addCommon(m, "knuckles", 0xDD, ball);
        return new FighterDef("knuckles", "KNUCKLES", "Heavy hitter. Glides to recover, charges Maximum Heat through attacks.",
                s, p, m, throwOf(10, 26, 9, 45, 60, 65, 0xDD), throwOf(12, 28, 12, 45, 66, 72, 0xDD),
                throwOf(10, 26, 8, 90, 70, 62, 0xE4), throwOf(10, 28, 7, 75, 60, 40, 0x9C), 175, 110);
    }

    // ------------------------------------------------------------------ Robotnik

    private FighterDef robotnik() {
        Stats s = new Stats(128, 1.3, 2.4, 0.14, 0.12, 1.85, 0.09, 0.02, 0.20, 2.8, 4.6, 5.8, 3.8, 4.8, 3, 32, 40, 3, 5);
        Poses p = new Poses(a(EGG_IDLE), a(EGG_IDLE), a(EGG_THRUST), a(EGG_IDLE), a(EGG_IDLE), a(EGG_THRUST), a(EGG_IDLE),
                a(EGG_IDLE), a(EGG_HURT), a(EGG_HURT), a(EGG_IDLE), a(EGG_HURT), a(EGG_THRUST), a(EGG_DIZZY),
                a(EGG_LAUGH), a(EGG_HURT), a(EGG_ATTACK), a(EGG_IDLE), a(EGG_HURT), 6);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("robotnik.jab", "Pod Bump", 22).pose(EGG_ATTACK, 22)
                .hit(h(5, 8, 16, 0, 12, 4, Hit.SAKURAI, 25, 40)).addV(4, 1.2, Double.NaN).build());
        m.put(Slot.FTILT, Move.of("robotnik.ftilt", "Piston Punch", 26).pose(EGG_ATTACK, 26)
                .hit(h(7, 10, 30, 0, 11, 10, Hit.SAKURAI, 25, 90)).sound(8, Sfx.MISSILE_THROW).build());
        m.put(Slot.UTILT, Move.of("robotnik.utilt", "Exhaust Flare", 28).pose(EGG_ATTACK, 28)
                .hit(h(6, 14, 0, -24, 14, 8, 90, 30, 85).fx(Hit.FX_FIRE)).sound(6, Sfx.FLAMETHROWER).build());
        m.put(Slot.DTILT, Move.of("robotnik.dtilt", "Underside Grinder", 24).pose(EGG_ATTACK, 24)
                .hit(h(5, 12, 10, 12, 12, 2, 361, 12, 10).rehit(4)).hit(h(13, 14, 12, 12, 13, 4, 70, 40, 50).group(1)).build());
        m.put(Slot.DASH, Move.of("robotnik.dash", "Egg Ram", 36).pose(EGG_THRUST, 36)
                .hit(h(5, 18, 14, 0, 14, 11, 45, 40, 72).fx(Hit.FX_HEAVY)).setV(4, 3.6, Double.NaN).slide().sound(4, Sfx.BOSS_ZOOM).build());
        m.put(Slot.FSMASH, Move.of("robotnik.fsmash", "Wrecking Ball", 58).pose(EGG_ATTACK, 58)
                .hit(h(18, 24, 40, 4, 16, 20, Hit.SAKURAI, 36, 96).fx(Hit.FX_HEAVY)).charge(11).sound(18, Sfx.BOSS_ZOOM).build());
        m.put(Slot.USMASH, Move.of("robotnik.usmash", "Wrecking Arc", 54).pose(EGG_ATTACK, 54)
                .hit(h(16, 20, 28, -12, 15, 14, 75, 35, 95).fx(Hit.FX_HEAVY)).hit(h(21, 26, 0, -38, 16, 17, 88, 36, 96).group(1).fx(Hit.FX_HEAVY))
                .charge(10).sound(16, Sfx.BOSS_ZOOM).build());
        m.put(Slot.DSMASH, Move.of("robotnik.dsmash", "Egg Slam", 52).pose(EGG_ATTACK, 52)
                .hit(h(17, 21, 0, 14, 34, 16, 40, 34, 92).groundOnly().fx(Hit.FX_HEAVY)).charge(10)
                .sound(17, Sfx.BOSS_HIT_FLOOR).build());
        m.put(Slot.NAIR, Move.of("robotnik.nair", "Pod Spin", 34).pose(EGG_ATTACK, 34)
                .hit(h(5, 18, 0, 0, 18, 9, 361, 28, 80)).landingLag(10).build());
        m.put(Slot.FAIR, Move.of("robotnik.fair", "Ball Swing", 44).pose(EGG_ATTACK, 44)
                .hit(h(12, 17, 34, 4, 15, 14, 40, 32, 92).fx(Hit.FX_HEAVY)).landingLag(16).build());
        m.put(Slot.BAIR, Move.of("robotnik.bair", "Afterburner", 32).pose(EGG_THRUST, 32)
                .hit(h(5, 13, -22, 2, 15, 12, 40, 32, 98).fx(Hit.FX_FIRE)).sound(5, Sfx.FLAMETHROWER).landingLag(12).build());
        m.put(Slot.UAIR, Move.of("robotnik.uair", "Dome Flare", 32).pose(EGG_ATTACK, 32)
                .hit(h(5, 12, 0, -22, 16, 11, 85, 30, 96).fx(Hit.FX_FIRE)).landingLag(10).build());
        m.put(Slot.DAIR, Move.of("robotnik.dair", "Bomb Drop", 36).pose(EGG_ATTACK, 36)
                .spawn(10, shot(Projectile.K_BOMB, 0, 2.0, 0.25, 70, 8, h(0, 0, 0, 0, 8, 4, 270, 20, 40),
                        h(0, 0, 0, 0, 24, 10, 280, 30, 70).fx(Hit.FX_EXPLOSION), 0, false, false, 2, false), 0, 14)
                .sound(10, Sfx.MISSILE_THROW).landingLag(10).build());
        m.put(Slot.NSPEC, Move.of("robotnik.bomb", "Egg Bomb", 40).pose(EGG_LAUGH, 40)
                .spawn(12, shot(Projectile.K_BOMB, 2.4, -4.2, 0.2, 140, 8, h(0, 0, 0, 0, 8, 4, 60, 20, 30),
                        h(0, 0, 0, 0, 28, 12, 55, 36, 78).fx(Hit.FX_EXPLOSION), 1, false, false, 2, false), 10, -10)
                .sound(12, Sfx.MISSILE_THROW).landingLag(10).build());
        m.put(Slot.SSPEC, Move.of("robotnik.missile", "Egg Missile", 44).pose(EGG_ATTACK, 44)
                .spawn(14, shot(Projectile.K_MISSILE, 3.6, 0, 0, 90, 7, h(0, 0, 0, 0, 7, 6, 40, 30, 60),
                        h(0, 0, 0, 0, 20, 8, 45, 35, 65).fx(Hit.FX_EXPLOSION), 0, false, false, 1, false), 18, 2)
                .sound(14, Sfx.MISSILE_SHOOT).landingLag(10).build());
        m.put(Slot.USPEC, Move.of("robotnik.thrusters", "Overdrive Thrusters", 50).pose(EGG_THRUST, 50)
                .hit(h(4, 30, 0, 20, 14, 2, 270, 15, 10).rehit(6).fx(Hit.FX_FIRE))
                .setV(4, Double.NaN, -5.6).addV(16, Double.NaN, -1.5).gravity(4, 36, 0.35).airControl(0.9)
                .custom(Move.Custom.HOVER_ROLL).helpless().ledgeGrab().sound(4, Sfx.FLAMETHROWER).landingLag(14).build());
        m.put(Slot.DSPEC, Move.of("robotnik.mine", "Spike Mine", 34).pose(EGG_LAUGH, 34)
                .spawn(10, shot(Projectile.K_MINE, 0.6, 1, 0.3, 420, 9, h(0, 0, 0, 0, 9, 6, 80, 40, 40),
                        h(0, 0, 0, 0, 26, 12, 80, 45, 70).fx(Hit.FX_EXPLOSION), 0, true, false, 1, false), 0, 12)
                .sound(10, Sfx.CLANK).landingLag(10).build());
        m.put(Slot.LEDGE_ATTACK, Move.of("robotnik.ledge", "Ledge Bump", 30).pose(EGG_ATTACK, 30)
                .hit(h(8, 12, 18, 0, 16, 8, 45, 40, 30)).intangible(0, 10).build());
        m.put(Slot.GETUP_ATTACK, Move.of("robotnik.getup", "Pod Sweep", 30).pose(EGG_ATTACK, 30)
                .hit(h(8, 14, 0, 6, 26, 7, 361, 40, 30)).intangible(0, 12).build());
        m.put(Slot.GRAB, Move.of("robotnik.grab", "Claw Grab", 30).pose(EGG_ATTACK, 30).hit(h(8, 10, 26, 0, 12, 0, 0, 0, 0)).build());
        m.put(Slot.PUMMEL, Move.of("robotnik.pummel", "Squeeze", 14).pose(EGG_ATTACK, 14).hit(h(2, 3, 0, 0, 1, 1.6, 0, 0, 0)).build());
        return new FighterDef("robotnik", "ROBOTNIK", "The heaviest fighter. Floats in the Egg Mobile with bombs, missiles and a wrecking ball.",
                s, p, m, throwOf(12, 28, 10, 45, 60, 68, EGG_ATTACK), throwOf(14, 30, 11, 45, 64, 70, EGG_ATTACK),
                throwOf(12, 28, 9, 90, 70, 66, EGG_ATTACK), throwOf(12, 30, 8, 75, 60, 44, EGG_ATTACK), 150, 100);
    }

    // ------------------------------------------------------------------ Mecha Sonic

    private FighterDef mecha() {
        Stats s = new Stats(102, 1.5, 3.4, 0.26, 0.2, 1.9, 0.1, 0.02, 0.32, 4.1, 6.2, 6.7, 4.3, 6.3, 1, 22, 40, 0, 4);
        int[] ball = a(3, 4, 5);
        Poses p = new Poses(a(0, 1), a(8, 9), a(16), ball, a(17), ball, a(2), a(2), a(12), a(12, 13), a(2), a(17), ball,
                a(12, 13), a(10, 11), a(17), a(20), a(15), a(7), 6);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("mecha.jab", "Claw Jab", 18).pose(19, 3).pose(18, 7).pose(0, 8)
                .hit(h(3, 6, 16, -4, 10, 4, Hit.SAKURAI, 20, 30).fx(Hit.FX_SLASH)).build());
        m.put(Slot.FTILT, Move.of("mecha.ftilt", "Steel Slash", 24).pose(19, 6).pose(18, 8).pose(0, 10)
                .hit(h(6, 9, 18, -2, 11, 9, Hit.SAKURAI, 24, 88).fx(Hit.FX_SLASH)).build());
        m.put(Slot.UTILT, Move.of("mecha.utilt", "Spike Crown", 26).pose(2, 5).cycle(12, 2, ball).pose(0, 9)
                .hit(h(5, 12, 0, -22, 14, 8, 88, 28, 92)).addV(5, 0, -1.2).build());
        m.put(Slot.DTILT, Move.of("mecha.dtilt", "Sweep Claw", 20).pose(14, 4).pose(15, 8).pose(2, 8)
                .hit(h(4, 8, 18, 12, 10, 6, 75, 32, 48).groundOnly()).build());
        m.put(Slot.DASH, Move.of("mecha.dash", "Boost Tackle", 32).pose(16, 4).cycle(18, 2, ball).pose(2, 10)
                .hit(h(4, 18, 8, 0, 14, 9, 45, 42, 72)).setV(3, 4.6, Double.NaN).slide().sound(3, Sfx.DASH).build());
        m.put(Slot.FSMASH, Move.of("mecha.fsmash", "Overclocked Slash", 48).pose(12, 16).pose(18, 12).pose(19, 20)
                .hit(h(16, 19, 22, -4, 13, 15, Hit.SAKURAI, 32, 98).fx(Hit.FX_SLASH)).charge(10)
                .addV(15, 2.2, Double.NaN).sound(15, Sfx.MECHA_SPARK).build());
        m.put(Slot.USMASH, Move.of("mecha.usmash", "Spike Geyser", 46).pose(2, 12).cycle(18, 2, ball).pose(17, 16)
                .hit(h(12, 22, 0, -24, 16, 15, 88, 36, 100)).charge(8).build());
        m.put(Slot.DSMASH, Move.of("mecha.dsmash", "Spike Ring", 46).pose(2, 12).cycle(16, 2, ball).pose(2, 18)
                .hit(h(12, 18, 0, 6, 26, 14, 32, 30, 94).groundOnly()).charge(8).sound(12, Sfx.MECHA_SPARK).build());
        m.put(Slot.NAIR, Move.of("mecha.nair", "Spike Ball", 34).cycle(34, 2, ball)
                .hit(h(4, 8, 0, 0, 15, 10, 361, 28, 85)).hit(h(9, 22, 0, 0, 14, 6, 361, 20, 70).group(1)).landingLag(10).build());
        m.put(Slot.FAIR, Move.of("mecha.fair", "Diving Claw", 36).pose(19, 7).pose(18, 12).pose(17, 17)
                .hit(h(7, 11, 16, 4, 12, 12, 45, 28, 96).fx(Hit.FX_SLASH)).landingLag(12).build());
        m.put(Slot.BAIR, Move.of("mecha.bair", "Jet Kick", 32).pose(18 | FLIP, 6).pose(18 | FLIP, 10).pose(17, 16)
                .hit(h(6, 9, -17, 2, 12, 12, 40, 30, 100)).landingLag(11).build());
        m.put(Slot.UAIR, Move.of("mecha.uair", "Spike Flip", 30).cycle(30, 2, ball)
                .hit(h(4, 9, 0, -20, 14, 10, 85, 28, 96)).landingLag(10).build());
        m.put(Slot.DAIR, Move.of("mecha.dair", "Meteor Drop", 40).pose(2, 8).cycle(32, 2, ball)
                .hit(h(9, 12, 0, 14, 12, 13, 270, 22, 82)).hit(h(13, 26, 0, 12, 11, 8, 60, 25, 70).group(1))
                .setV(8, 0, 7.0).landingLag(18).build());
        m.put(Slot.NSPEC, Move.of("mecha.laser", "Chest Laser", 38).pose(20, 10).pose(21, 28)
                .spawn(10, shot(Projectile.K_LASER, 6.2, 0, 0, 50, 6, h(0, 0, 0, 0, 6, 6, 30, 30, 30).fx(Hit.FX_ELECTRIC),
                        null, 0, false, false, 1, false), 16, -6)
                .sound(10, Sfx.LASER).landingLag(10).build());
        m.put(Slot.SSPEC, Move.of("mecha.blitz", "Mecha Blitz", 44).cycle(12, 2, 6, 7).cycle(32, 2, ball)
                .hit(h(12, 43, 4, 0, 14, 11, 40, 40, 74).fx(Hit.FX_SPIN))
                .holdLoop(Pad.SPECIAL, 4, 12, 40).custom(Move.Custom.ROLLING).slide()
                .sound(0, Sfx.SPINDASH).landingLag(10).build());
        m.put(Slot.USPEC, Move.of("mecha.jet", "Jet Burst", 40).pose(2, 6).pose(17, 34)
                .hit(h(6, 14, 0, -6, 16, 8, 85, 40, 70)).setV(6, Double.NaN, -9.0).addV(6, 1.4, Double.NaN).airControl(0.7)
                .helpless().ledgeGrab().landingLag(14).sound(6, Sfx.BOSS_ZOOM).build());
        m.put(Slot.DSPEC, Move.of("mecha.counter", "Reflector Field", 30).cycle(30, 2, ball)
                .hit(h(3, 8, 0, 0, 20, 6, 60, 45, 50).fx(Hit.FX_ELECTRIC)).reflect(2, 16).intangible(2, 5)
                .gravity(0, 20, 0.2).sound(2, Sfx.MECHA_SPARK).landingLag(8).build());
        m.put(Slot.LEDGE_ATTACK, Move.of("mecha.ledge", "Ledge Slash", 30).pose(18, 30)
                .hit(h(8, 12, 18, -2, 14, 8, 45, 40, 30)).intangible(0, 10).build());
        m.put(Slot.GETUP_ATTACK, Move.of("mecha.getup", "Spin Up", 30).cycle(30, 2, ball)
                .hit(h(8, 14, 0, 0, 22, 7, 361, 40, 30)).intangible(0, 12).build());
        m.put(Slot.GRAB, Move.of("mecha.grab", "Clamp", 28).pose(20, 28).hit(h(7, 9, 18, -4, 11, 0, 0, 0, 0)).build());
        m.put(Slot.PUMMEL, Move.of("mecha.pummel", "Shock", 14).pose(20, 14).hit(h(2, 3, 0, 0, 1, 1.5, 0, 0, 0)).build());
        return new FighterDef("mecha", "MECHA SONIC", "Sonic's steel rival: heavier, harder hitting, with a laser and a reflector field.",
                s, p, m, throwOf(9, 24, 9, 45, 60, 62, 20), throwOf(11, 26, 11, 45, 64, 70, 20),
                throwOf(9, 24, 7, 90, 70, 60, 17), throwOf(10, 26, 6, 75, 58, 40, 2), 210, 60);
    }

    // ------------------------------------------------------------------ Egg Robo

    private FighterDef eggRobo() {
        Stats s = new Stats(104, 1.3, 2.5, 0.2, 0.18, 1.9, 0.1, 0.02, 0.27, 3.6, 5.6, 6.4, 4.2, 6.0, 1, 22, 44, 0, 4);
        Poses p = new Poses(a(ROBO_IDLE), a(ROBO_WALK), a(ROBO_WALK), a(ROBO_THRUST), a(ROBO_IDLE), a(ROBO_THRUST), a(ROBO_IDLE),
                a(ROBO_IDLE), a(ROBO_HURT), a(ROBO_HURT), a(ROBO_IDLE), a(ROBO_HURT), a(ROBO_SPIN), a(ROBO_HURT),
                a(ROBO_AIM), a(ROBO_HURT), a(ROBO_PUNCH), a(ROBO_IDLE), a(ROBO_HURT), 6);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("robo.jab", "Gun Butt", 20).pose(ROBO_PUNCH, 20)
                .hit(h(4, 7, 18, -6, 11, 4, Hit.SAKURAI, 22, 32)).build());
        m.put(Slot.FTILT, Move.of("robo.ftilt", "Robo Kick", 24).pose(ROBO_KICK, 24)
                .hit(h(6, 9, 18, 8, 11, 9, Hit.SAKURAI, 25, 88)).build());
        m.put(Slot.UTILT, Move.of("robo.utilt", "Antenna Zap", 26).pose(ROBO_AIM, 26)
                .hit(h(6, 12, 0, -30, 14, 8, 90, 30, 88).fx(Hit.FX_ELECTRIC)).sound(6, Sfx.ENERGY_ZAP).build());
        m.put(Slot.DTILT, Move.of("robo.dtilt", "Leg Sweep", 20).pose(ROBO_KICK, 20)
                .hit(h(4, 8, 18, 18, 10, 6, 75, 32, 48).groundOnly()).build());
        m.put(Slot.DASH, Move.of("robo.dash", "Jet Charge", 34).pose(ROBO_THRUST, 34)
                .hit(h(4, 18, 12, 0, 14, 10, 45, 40, 72)).setV(3, 4.2, Double.NaN).slide().sound(3, Sfx.BOSS_ZOOM).build());
        m.put(Slot.FSMASH, Move.of("robo.fsmash", "Point-Blank Blast", 48).pose(ROBO_AIM, 48)
                .hit(h(17, 21, 26, -6, 14, 15, Hit.SAKURAI, 30, 95).fx(Hit.FX_EXPLOSION)).charge(10).sound(17, Sfx.MISSILE_EXPLODE).build());
        m.put(Slot.USMASH, Move.of("robo.usmash", "Jet Upward", 46).pose(ROBO_THRUST, 46)
                .hit(h(12, 20, 0, -28, 16, 15, 88, 34, 95).fx(Hit.FX_FIRE)).charge(8).sound(12, Sfx.FLAMETHROWER).build());
        m.put(Slot.DSMASH, Move.of("robo.dsmash", "Ground Pound", 48).pose(ROBO_HURT, 14).pose(ROBO_IDLE, 34)
                .hit(h(14, 18, 0, 20, 30, 15, 35, 30, 92).groundOnly().fx(Hit.FX_HEAVY)).charge(10).sound(14, Sfx.BOSS_HIT_FLOOR).build());
        m.put(Slot.NAIR, Move.of("robo.nair", "Spin Bot", 34).pose(ROBO_SPIN, 34)
                .hit(h(4, 18, 0, 0, 18, 9, 361, 26, 82)).landingLag(10).build());
        m.put(Slot.FAIR, Move.of("robo.fair", "Air Blast", 36).pose(ROBO_AIM, 36)
                .hit(h(8, 12, 22, -4, 13, 12, 45, 28, 95).fx(Hit.FX_EXPLOSION)).landingLag(12).build());
        m.put(Slot.BAIR, Move.of("robo.bair", "Rocket Back", 32).pose(ROBO_THRUST, 32)
                .hit(h(6, 12, -20, 4, 14, 12, 40, 30, 98).fx(Hit.FX_FIRE)).sound(6, Sfx.FLAMETHROWER).landingLag(11).build());
        m.put(Slot.UAIR, Move.of("robo.uair", "Sky Zap", 30).pose(ROBO_AIM, 30)
                .hit(h(5, 11, 0, -28, 14, 9, 85, 27, 92).fx(Hit.FX_ELECTRIC)).landingLag(10).build());
        m.put(Slot.DAIR, Move.of("robo.dair", "Stomp Drop", 40).pose(ROBO_IDLE, 40)
                .hit(h(8, 12, 0, 22, 13, 13, 270, 22, 82).fx(Hit.FX_HEAVY)).hit(h(13, 26, 0, 20, 12, 8, 60, 25, 70).group(1))
                .setV(8, 0, 7.0).landingLag(18).build());
        m.put(Slot.NSPEC, Move.of("robo.blaster", "Laser Blaster", 34).pose(ROBO_AIM, 34)
                .spawn(8, shot(Projectile.K_LASER, 7.0, 0, 0, 44, 5, h(0, 0, 0, 0, 5, 5, 25, 25, 20).fx(Hit.FX_ELECTRIC),
                        null, 0, false, true, 2, false), 22, -10)
                .sound(8, Sfx.LASER).landingLag(8).build());
        m.put(Slot.SSPEC, Move.of("robo.rocket", "Rocket Punch", 40).pose(ROBO_PUNCH, 40)
                .spawn(12, shot(Projectile.K_FIREBALL, 4.4, 0, 0, 60, 9, h(0, 0, 0, 0, 9, 9, 40, 35, 70).fx(Hit.FX_FIRE),
                        null, 0, false, false, 1, false), 20, -6)
                .sound(12, Sfx.MISSILE_SHOOT).landingLag(10).build());
        m.put(Slot.USPEC, Move.of("robo.jetpack", "Jetpack", 54).pose(ROBO_THRUST, 54)
                .hit(h(4, 40, 0, 26, 14, 2, 270, 12, 10).rehit(6).fx(Hit.FX_FIRE))
                .setV(4, Double.NaN, -4.4).addV(14, Double.NaN, -2.4).addV(24, Double.NaN, -1.5).gravity(4, 40, 0.3)
                .airControl(1.0).custom(Move.Custom.HOVER_ROLL).helpless().ledgeGrab().sound(4, Sfx.FLAMETHROWER).landingLag(14).build());
        m.put(Slot.DSPEC, Move.of("robo.overload", "Overload", 54).pose(ROBO_HURT, 23).pose(ROBO_SPIN, 31)
                .hit(h(23, 27, 0, 0, 31, 12.5, 60, 40, 84).fx(Hit.FX_ELECTRIC)).armor(2, 22).sound(4, Sfx.CHARGING)
                .sound(23, Sfx.LIGHTNING).landingLag(12).build());
        m.put(Slot.LEDGE_ATTACK, Move.of("robo.ledge", "Ledge Kick", 30).pose(ROBO_KICK, 30)
                .hit(h(8, 12, 18, 4, 14, 8, 45, 40, 30)).intangible(0, 10).build());
        m.put(Slot.GETUP_ATTACK, Move.of("robo.getup", "Spin Up", 30).pose(ROBO_SPIN, 30)
                .hit(h(8, 14, 0, 0, 22, 7, 361, 40, 30)).intangible(0, 12).build());
        m.put(Slot.GRAB, Move.of("robo.grab", "Grab", 28).pose(ROBO_PUNCH, 28).hit(h(7, 9, 20, -6, 11, 0, 0, 0, 0)).build());
        m.put(Slot.PUMMEL, Move.of("robo.pummel", "Zap", 14).pose(ROBO_PUNCH, 14).hit(h(2, 3, 0, 0, 1, 1.6, 0, 0, 0)).build());
        return new FighterDef("eggrobo", "EGG ROBO", "Robotnik's robot trooper: blaster at range, Rocket Punch, and an armoured Overload.",
                s, p, m, throwOf(10, 24, 9, 45, 60, 64, ROBO_PUNCH), throwOf(12, 26, 11, 45, 64, 70, ROBO_PUNCH),
                throwOf(10, 24, 8, 90, 70, 62, ROBO_AIM), throwOf(10, 26, 7, 75, 58, 42, ROBO_HURT), 170, 90);
    }

    // ------------------------------------------------------------------ Rhinobot

    private FighterDef rhinobot() {
        Stats s = new Stats(92, 1.7, 3.6, 0.30, 0.2, 1.7, 0.09, 0.02, 0.30, 3.8, 5.8, 6.4, 4.2, 6.0, 1, 30, 28, 0, 3);
        Poses p = new Poses(a(0), a(0, 3), a(0, 3), a(0), a(0), a(1, 2), a(0), a(0), a(1), a(1, 2), a(0), a(1), a(1, 2),
                a(1, 2), a(0, 3), a(1), a(0), a(1), a(1), 5);
        Map<Slot, Move> m = new EnumMap<>(Slot.class);
        m.put(Slot.JAB, Move.of("rhino.jab", "Horn Poke", 16).pose(0, 16)
                .hit(h(3, 5, 18, -2, 10, 3.5, Hit.SAKURAI, 20, 30)).addV(2, 1.4, Double.NaN).build());
        m.put(Slot.FTILT, Move.of("rhino.ftilt", "Horn Thrust", 22).pose(0, 22)
                .hit(h(5, 8, 20, -2, 11, 8, Hit.SAKURAI, 24, 86)).addV(4, 2.4, Double.NaN).build());
        m.put(Slot.UTILT, Move.of("rhino.utilt", "Horn Toss", 24).pose(3, 24)
                .hit(h(5, 10, 10, -16, 13, 7, 95, 32, 85)).build());
        m.put(Slot.DTILT, Move.of("rhino.dtilt", "Tail Swipe", 18).pose(0 | FLIP, 18)
                .hit(h(4, 7, -16, 4, 10, 6, 70, 32, 45).groundOnly()).build());
        m.put(Slot.DASH, Move.of("rhino.dash", "Stampede", 34).pose(0, 34)
                .hit(h(3, 20, 16, 0, 14, 10, 40, 42, 72)).setV(2, 5.2, Double.NaN).slide().sound(2, Sfx.DASH).build());
        m.put(Slot.FSMASH, Move.of("rhino.fsmash", "Rhino Rush", 46).pose(3, 14).pose(0, 32)
                .hit(h(14, 20, 22, -2, 14, 14, Hit.SAKURAI, 30, 96).fx(Hit.FX_HEAVY)).charge(10)
                .setV(13, 4.2, Double.NaN).slide().sound(13, Sfx.DASH).build());
        m.put(Slot.USMASH, Move.of("rhino.usmash", "Horn Launcher", 44).pose(0, 10).pose(3, 34)
                .hit(h(10, 16, 6, -22, 15, 13, 85, 34, 92)).charge(8).sound(10, Sfx.SPRING).build());
        m.put(Slot.DSMASH, Move.of("rhino.dsmash", "Spin Out", 42).pose(1, 8).cycle(20, 4, 1, 2).pose(0, 14)
                .hit(h(8, 18, 0, 4, 28, 13, 30, 30, 90).groundOnly()).charge(6).sound(8, Sfx.SKID).build());
        m.put(Slot.NAIR, Move.of("rhino.nair", "Barrel Roll", 32).cycle(32, 4, 1, 2)
                .hit(h(4, 18, 0, 0, 17, 10, 361, 28, 85)).landingLag(9).build());
        m.put(Slot.FAIR, Move.of("rhino.fair", "Flying Horn", 34).pose(0, 34)
                .hit(h(6, 11, 20, 0, 12, 11, 45, 28, 96)).addV(6, 1.2, Double.NaN).landingLag(11).build());
        m.put(Slot.BAIR, Move.of("rhino.bair", "Mule Kick", 30).pose(0 | FLIP, 30)
                .hit(h(5, 8, -20, 2, 12, 11, 40, 30, 96)).landingLag(10).build());
        m.put(Slot.UAIR, Move.of("rhino.uair", "Horn Flip", 30).cycle(30, 4, 3, 1)
                .hit(h(5, 10, 6, -18, 13, 7, 85, 26, 90)).landingLag(10).build());
        m.put(Slot.DAIR, Move.of("rhino.dair", "Belly Flop", 38).pose(1, 38)
                .hit(h(8, 11, 0, 12, 15, 12, 275, 22, 80)).hit(h(12, 24, 0, 10, 14, 7, 60, 25, 70).group(1))
                .setV(8, 0, 6.8).landingLag(16).build());
        m.put(Slot.NSPEC, Move.of("rhino.charge", "Horn Charge", 50).cycle(12, 2, 0, 3).pose(0, 38)
                .hit(h(12, 49, 18, 0, 14, 13, 40, 44, 78).fx(Hit.FX_HEAVY))
                .holdLoop(Pad.SPECIAL, 2, 12, 60).custom(Move.Custom.ROLLING).slide()
                .sound(0, Sfx.SKID).landingLag(10).build());
        m.put(Slot.SSPEC, Move.of("rhino.zigzag", "Zig-Zag Dash", 36).pose(0, 36)
                .hit(h(4, 20, 16, 0, 13, 8, 50, 40, 66)).setV(3, 5.6, -2.4).addV(16, -0.0, Double.NaN)
                .gravity(3, 20, 0.5).airControl(0.3).slide().sound(3, Sfx.DASH).landingLag(10).build());
        m.put(Slot.USPEC, Move.of("rhino.leap", "Spring Horn", 40).pose(3, 40)
                .hit(h(4, 14, 8, -14, 14, 9, 85, 40, 75)).setV(4, Double.NaN, -9.4).addV(4, 1.8, Double.NaN)
                .airControl(0.7).helpless().ledgeGrab().landingLag(14).sound(4, Sfx.SPRING).build());
        m.put(Slot.DSPEC, Move.of("rhino.dig", "Dust Kick", 30).pose(0 | FLIP, 30)
                .spawn(8, shot(Projectile.K_SHOCKWAVE, -3.6, 0, 0, 40, 8, h(0, 0, 0, 0, 8, 6, 60, 35, 40),
                        null, 0, false, true, 2, true), -16, 8)
                .sound(8, Sfx.SKID).landingLag(8).build());
        m.put(Slot.LEDGE_ATTACK, Move.of("rhino.ledge", "Ledge Horn", 30).pose(0, 30)
                .hit(h(8, 12, 18, 0, 14, 8, 45, 40, 30)).intangible(0, 10).build());
        m.put(Slot.GETUP_ATTACK, Move.of("rhino.getup", "Roll Up", 30).cycle(30, 4, 1, 2)
                .hit(h(8, 14, 0, 0, 22, 7, 361, 40, 30)).intangible(0, 12).build());
        m.put(Slot.GRAB, Move.of("rhino.grab", "Horn Hook", 26).pose(0, 26).hit(h(6, 8, 20, -2, 10, 0, 0, 0, 0)).build());
        m.put(Slot.PUMMEL, Move.of("rhino.pummel", "Gore", 14).pose(0, 14).hit(h(2, 3, 0, 0, 1, 1.5, 0, 0, 0)).build());
        return new FighterDef("rhinobot", "RHINOBOT", "Angel Island's charging badnik: the fastest runner, low to the ground, all horn.",
                s, p, m, throwOf(8, 22, 8, 40, 62, 60, 0), throwOf(10, 24, 10, 45, 64, 68, 0),
                throwOf(8, 22, 7, 90, 70, 58, 3), throwOf(10, 26, 6, 75, 58, 40, 1), 175, 70);
    }

    /** Ledge and getup attacks, grab and pummel shared in shape by the three heroes. */
    private void addCommon(Map<Slot, Move> m, String id, int armPose, int[] ball) {
        m.put(Slot.LEDGE_ATTACK, Move.of(id + ".ledge", "Ledge Attack", 30).cycle(30, 2, ball)
                .hit(h(8, 12, 16, 0, 14, 8, 45, 40, 30)).intangible(0, 10).build());
        m.put(Slot.GETUP_ATTACK, Move.of(id + ".getup", "Getup Attack", 30).cycle(30, 2, ball)
                .hit(h(8, 14, 0, 0, 22, 7, 361, 40, 30)).intangible(0, 12).build());
        m.put(Slot.GRAB, Move.of(id + ".grab", "Grab", 26).pose(armPose, 26).hit(h(6, 8, 16, -4, 10, 0, 0, 0, 0)).build());
        m.put(Slot.PUMMEL, Move.of(id + ".pummel", "Pummel", 14).pose(armPose, 14).hit(h(2, 3, 0, 0, 1, 1.5, 0, 0, 0)).build());
    }
}
