package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * The Hyper Remote, in Robotnik's control room: a wall screen showing the Robotnik monitor's
 * face, and the three-button remote (a drawn prop) on a pedestal. Each button lights as it is
 * pressed and the screen answers like a giant monitor.
 * <ul>
 * <li>[Rematch]: static, then the chosen boss's own ROM sprites on the screen while Launch
 * Base's alarm sounds every 32 frames (Obj_LBZAlarm loc_294E8) and the room flashes red.</li>
 * <li>[Jackpot]: the rings icon, then rings rain from the ceiling as spilled rings fall
 * (Obj_Bouncing_Ring: $18 gravity, a floor test every eighth frame, three-quarter bounces,
 * the shared spill spin slowing as Ring_spill_anim_counter runs down); the ones that land on
 * the hero sparkle with the ring sound, the rest heap up on the floor and stay.</li>
 * <li>[Overclock]: the lightning icon, a Lightning Shield (Ani_LightningShield script 0) round
 * the hero throwing its four sparks three times (sfx_ElectricAttack, as its double jump does)
 * through a fan of cards, then Robotnik laughing on the screen (his head's AniRaw_RobotnikHead).</li>
 * </ul>
 */
final class HyperRemotePicture extends TimedPicture {
    private static final int RED = 0;
    private static final int YELLOW = 1;
    private static final int BLUE = 2;
    /** Mod timing for the screen's static and the scenes. */
    private static final int STATIC = 16;
    private static final int REMATCH_LENGTH = STATIC + 64;
    private static final int RAIN_RINGS = 32;
    private static final int RAIN_GAP = 2;
    private static final int OVERCLOCK_LENGTH = 120;
    private static final int LAUGH = 72;
    private static final int SHIELD_ON = 10;
    /** Ani_LightningShield script 0: delay 1 (two frames each), looping. */
    private final int[] shieldScript = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 0xA, 0xB,
            0x16, 0x16, 0x15, 0x15, 0x14, 0x14, 0x13, 0x13, 0x12, 0x12, 0x11, 0x11, 0x10, 0x10, 0xF, 0xF, 0xE, 0xE,
            9, 0xA, 0xB};
    private final int[] burstAt = {24, 44, 64};

    private int x = 12;
    private int y = 36;
    private int heroX;
    private int ground;

    private String boss;
    private RomBody[] rings = new RomBody[0];
    /** Per ring: 0 falling, 1 resting on the heap, 2 collected (then its sparkle's age in {@link #sparkleAge}). */
    private int[] ringState = new int[0];
    private int[] sparkleAge = new int[0];
    private int[] heap = new int[16];
    private int spillCounter;
    private int spillAccum;
    private int lastRingSound = -1;
    private RomBody[][] bursts = new RomBody[3][];

    HyperRemotePicture(Shell shell) {
        heroX = x + 18;
        ground = y + EventPicture.HEIGHT - 22;
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        shell.sfx(Sounds.SFX_SWITCH);
        boss = verb.equals("rematch") ? argument : null;
        if (verb.equals("jackpot")) {
            rings = new RomBody[RAIN_RINGS];
            ringState = new int[RAIN_RINGS];
            sparkleAge = new int[RAIN_RINGS];
            heap = new int[16];
            spillCounter = 0xFF;
            spillAccum = 0;
        }
        bursts = new RomBody[3][];
    }

    @Override
    boolean playing() {
        return switch (verb()) {
            case "rematch" -> clock < REMATCH_LENGTH;
            case "jackpot" -> clock < 280 && !rainOver();
            default -> clock < OVERCLOCK_LENGTH;
        };
    }

    private boolean rainOver() {
        if (clock < RAIN_RINGS * RAIN_GAP + STATIC) {
            return false;
        }
        for (int i = 0; i < rings.length; i++) {
            if (ringState[i] == 0 || ringState[i] == 2 && sparkleAge[i] < 24) {
                return false;
            }
        }
        return true;
    }

    private int pressed() {
        return switch (verb()) {
            case "rematch" -> RED;
            case "jackpot" -> YELLOW;
            case "overclock" -> BLUE;
            default -> -1;
        };
    }

    @Override
    void step(Shell shell) {
        if (detail == null) {
            return;
        }
        switch (verb()) {
            case "rematch" -> {
                // Obj_LBZAlarm loc_294E8: the alarm sounds whenever its timer's low five bits are 0.
                if (clock >= STATIC && clock < REMATCH_LENGTH && ((clock - STATIC) & 0x1F) == 0) {
                    shell.sfx(Sounds.SFX_ALARM);
                }
            }
            case "jackpot" -> stepRain(shell);
            case "overclock" -> {
                if (clock == SHIELD_ON) {
                    shell.sfx(Sounds.SFX_LIGHTNING_SHIELD);
                }
                for (int i = 0; i < burstAt.length; i++) {
                    if (clock == burstAt[i]) {
                        shell.sfx(Sounds.SFX_ELECTRIC_ATTACK);
                        bursts[i] = sparkBurst(heroX, shieldY(shell));
                    }
                    if (bursts[i] != null && clock - burstAt[i] < 20) {
                        for (RomBody s : bursts[i]) {
                            moveSpark(s);
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private void stepRain(Shell shell) {
        if (clock < STATIC) {
            return;
        }
        // ChangeRingFrame: the spill spin accumulates the falling counter; its bits 9-10 pick the frame.
        if (spillCounter > 0) {
            spillAccum = (spillAccum + spillCounter) & 0xFFFF;
            spillCounter--;
        }
        int t = clock - STATIC;
        for (int i = 0; i < rings.length; i++) {
            if (t == i * RAIN_GAP && rings[i] == null) {
                // Spread across the ceiling by the ring's number (mod placement and drift).
                float rx = x + 8 + ((i * 37 + 11) % 23) * 5f;
                rings[i] = new RomBody(rx, y - 8, (((i * 53) % 9) - 4) * 0x20, 0);
            }
            RomBody r = rings[i];
            if (r == null) {
                continue;
            }
            if (ringState[i] == 2) {
                sparkleAge[i]++;
                continue;
            }
            if (ringState[i] == 1) {
                continue;
            }
            int bucket = bucket(r.px());
            // The ROM tests a spilled ring against the floor every eighth frame and lets it sink into
            // the ground in between; here the heap is tested every frame so rings land on top of it.
            boolean bounced = r.fallRing(true, ground - heap[bucket] * 5 - 7);
            if (r.px() < x + 6 || r.px() > x + EventPicture.WIDTH - 6) {
                r.xVel = -r.xVel;
            }
            if (Math.abs(r.px() - heroX) < 11 && r.py() > ground - 40 && r.py() < ground - 4) {
                ringState[i] = 2;
                if (lastRingSound != clock) {
                    shell.sfx(Sounds.SFX_RING);
                    lastRingSound = clock;
                }
            } else if (bounced && r.yVel > -0x400) {
                // A ring bounced back up at under 4 pixels a frame joins the heap instead (mod: spilled
                // rings bounce on until Ring_spill_anim_counter runs out, then vanish).
                ringState[i] = 1;
                r.yVel = 0;
                r.xVel = 0;
                heap[bucket] = Math.min(3, heap[bucket] + 1);
            }
        }
    }

    private int bucket(float px) {
        return Math.max(0, Math.min(15, (int) ((px - x) / 8)));
    }

    private int shieldY(Shell shell) {
        // The player's y_pos is its centre: y_radius above the feet ($13, or $F for Tails).
        return ground - (hero(shell).equals("tails") ? 0xF : 0x13);
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int wx, int wy, int w, int h) {
        x = wx;
        y = wy;
        ground = wy + h - 22;
        heroX = wx + 18;
        long t = shell.ticks;
        BaseInterior.draw(c, wx, wy, w, h, ground, 0xFF200C18, 0xFF3C1428);
        boolean alarm = verb().equals("rematch") && clock >= STATIC;

        // A beacon on the pipe, turning red while the alarm sounds.
        int bx = wx + 12;
        c.fill(bx - 5, wy + 13, 10, 3, 0xFF485868);
        c.fill(bx - 4, wy + 16, 8, 5, alarm && (t / 8) % 2 == 0 ? 0xFFFF2424 : 0xFF6C1C1C);
        c.fill(bx - 2, wy + 17, 2, 2, 0x80FFFFFF);

        drawScreen(shell, c, wx + 32, wy + 18, 86, 44, t, wx, wy, w, h);
        drawRemote(c, wx + 108, ground, t);

        String hero = hero(shell);
        boolean overclock = verb().equals("overclock") && clock >= SHIELD_ON;
        int frame = overclock ? shieldScript[((clock - SHIELD_ON) / 2) % shieldScript.length] : -1;
        SceneSprite shield = frame >= 0 ? shell.art.romFrame("shield_lightning", frame) : null;
        if (shield != null && frame >= 0xE) {
            c.draw(shield, heroX, shieldY(shell), SceneDraw.plain());
        }
        boolean raining = verb().equals("jackpot") && clock >= STATIC && clock < STATIC + RAIN_RINGS * RAIN_GAP + 40;
        Poses.hero(shell, c, hero, raining ? Poses.LOOK_UP : Poses.WAIT, t, heroX, ground, SceneDraw.plain());
        if (shield != null && frame < 0xE) {
            c.draw(shield, heroX, shieldY(shell), SceneDraw.plain());
        }
        if (verb().equals("overclock")) {
            drawDeck(shell, c, wx + 60, ground - 44);
            for (int i = 0; i < burstAt.length; i++) {
                if (bursts[i] != null) {
                    for (RomBody s : bursts[i]) {
                        spark(shell, c, s.px(), s.py(), clock - burstAt[i]);
                    }
                }
            }
        }
        if (verb().equals("jackpot")) {
            drawRings(shell, c, t);
        }
        if (alarm && (t / 8) % 2 == 0) {
            c.fill(wx, wy, w, h, 0x30FF0000);
        }
    }

    /** The wall screen: Robotnik's face idle; static, then what the pressed button brings up. */
    private void drawScreen(Shell shell, SceneCanvas c, int sx, int sy, int sw, int sh, long t, int wx, int wy,
            int ww, int wh) {
        c.fill(sx - 4, sy - 4, sw + 8, sh + 8, 0xFF101820);
        c.fill(sx - 3, sy - 3, sw + 6, sh + 6, 0xFF6C7C8C);
        c.fill(sx - 2, sy - 2, sw + 4, 1, 0xFFB6C6D6);
        c.fill(sx, sy, sw, sh, 0xFF080C18);
        int scx = sx + sw / 2;
        int scy = sy + sh / 2;
        if (detail != null && clock < STATIC) {
            BaseInterior.screenStatic(shell, c, sx, sy, sw, sh, t, wx, wy, ww, wh);
        } else {
            switch (verb()) {
                case "rematch" -> {
                    if (boss == null || !EnemyVisuals.drawFitted(shell, c, boss, scx, sy + sh - 2, sw - 6, sh - 4, 1f,
                            t, 0)) {
                        BaseInterior.robotnikIcon(shell, c, scx, scy, 2f, wx, wy, ww, wh);
                    }
                }
                case "jackpot" -> BaseInterior.monitorIcon(shell, c, 4, scx, scy, 2f, wx, wy, ww, wh);
                case "overclock" -> {
                    if (clock < LAUGH) {
                        BaseInterior.monitorIcon(shell, c, 7, scx, scy, 2f, wx, wy, ww, wh);
                    } else {
                        // AniRaw_RobotnikHead: frames 0 and 1, delay 5 (six frames each).
                        SceneSprite head = shell.art.romFrame("robotnik_ship", (int) ((t / 6) % 2));
                        if (head != null) {
                            Poses.centre(c, head, scx, scy + 2, SceneDraw.plain().withScale(2));
                        }
                    }
                }
                default -> {
                    BaseInterior.robotnikIcon(shell, c, scx, scy - 2, 2f, wx, wy, ww, wh);
                    shell.font.draw(c, "HYPER", scx - shell.font.width("HYPER") / 2, sy + sh - 8,
                            (t / 30) % 2 == 0 ? 0xFFFF4848 : 0xFF902424);
                }
            }
        }
        for (int i = 0; i < sh; i += 2) {
            c.fill(sx, sy + i, sw, 1, 0x30000000);
        }
    }

    /** The remote on its pedestal (the games have no such thing): red, yellow and blue buttons. */
    private void drawRemote(SceneCanvas c, int cx, int feet, long t) {
        c.fill(cx - 5, feet - 30, 10, 25, 0xFF384858);
        c.fill(cx - 4, feet - 30, 2, 25, 0xFF6C7C8C);
        c.fill(cx - 8, feet - 32, 16, 3, 0xFF8C9CB0);
        int top = feet - 66;
        c.fill(cx - 9, top, 18, 34, Colors.BLACK);
        c.fill(cx - 8, top + 1, 16, 32, 0xFF7C7C84);
        c.fill(cx - 8, top + 1, 16, 1, 0xFFB6B6BC);
        c.fill(cx - 5, top + 4, 10, 6, 0xFF241C1C);
        c.fill(cx - 4, top + 6, 2, 2, (t / 15) % 2 == 0 ? 0xFFFF2424 : 0xFF481010);
        int[] colours = {0xFFDA2424, 0xFFFFDA24, 0xFF2490FF};
        int lit = detail == null ? (int) ((t / 15) % 3) : -1;
        for (int i = 0; i < 3; i++) {
            int by = top + 13 + i * 6;
            boolean down = i == pressed() && clock < 12;
            boolean glow = i == lit || i == pressed() && clock >= 12;
            c.fill(cx - 5, by, 10, 5, Colors.BLACK);
            c.fill(cx - 4, by + (down ? 2 : 1), 8, down ? 2 : 3,
                    down ? Colors.WHITE : glow ? Colors.mix(colours[i], Colors.WHITE, 0.4f) : colours[i]);
        }
    }

    /** The deck (card backs, drawn) fanned before the hero; each burst of sparks lights it. */
    private void drawDeck(Shell shell, SceneCanvas c, int cx, int cy) {
        if (clock < SHIELD_ON) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            int px = cx - 26 + i * 11;
            int py = cy + Math.abs(i - 2) * 3;
            boolean struck = false;
            for (int b : burstAt) {
                int age = clock - b - i * 2;
                struck |= age >= 0 && age < 6;
            }
            c.fill(px, py, 12, 16, Colors.BLACK);
            c.fill(px + 1, py + 1, 10, 14, struck ? Colors.WHITE : 0xFF102048);
            c.fill(px + 2, py + 2, 8, 12, struck ? 0xFFFFFFB6 : 0xFF24489C);
            c.fill(px + 4, py + 6, 4, 4, EventArt.GOLD);
            if (clock >= burstAt[burstAt.length - 1] + 8) {
                shell.font.drawOutlined(c, "+", px + 3, py - 4, Colors.TEXT_GOOD, 1);
            }
        }
    }

    private void drawRings(Shell shell, SceneCanvas c, long t) {
        int spill = (spillAccum >> 9) & 3;
        int frame = spillCounter > 0 ? spill : (int) ((t / 8) % 4);
        SceneSprite ring = shell.art.romFrame("ring", frame);
        for (int i = 0; i < rings.length; i++) {
            RomBody r = rings[i];
            if (r == null) {
                continue;
            }
            if (ringState[i] == 2) {
                sparkle(shell, c, r.px(), r.py(), sparkleAge[i]);
            } else if (ring != null) {
                c.draw(ring, r.px(), r.py(), SceneDraw.plain());
            } else {
                c.fill(Math.round(r.px()) - 4, Math.round(r.py()) - 4, 8, 8, EventArt.GOLD);
            }
        }
    }
}
