package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * Robotnik's Offer, out on the act's level: a screen on a post (a drawn prop) with Robotnik in
 * his Egg Mobile on it - Map_RobotnikShip frame 5 with his head on top at (0, -$1C), as
 * Child1_MakeRoboHead places it, nodding through AniRaw_RobotnikHead (frames 0/1, delay 5).
 * [Accept]: two of Launch Base's Big Arm arms (Map_LBZFinalBoss2: forearm frame 2, claw frames
 * 4-7 animating as byte_75194 does) swing in from the sides and clamp on the hero (sfx_Grab);
 * sparks fly as the work is done (Mecha Sonic's spark hiss), then the forearms pull away and the
 * hero keeps the claws. [Refuse]: the screen clicks off (sfx_Switch), the picture squeezing to
 * a line and a dot as an old set does, and stays dark.
 */
final class RobotnikOfferPicture extends TimedPicture {
    /** Mod timing for the arms and the switch-off. */
    private static final int ARRIVE = 24;
    private static final int RETRACT = 72;
    private static final int ACCEPT_LENGTH = 100;
    private static final int OFF_LENGTH = 30;
    private final int[] sparksAt = {32, 50};
    /** byte_75194: the Big Arm's claw opens and closes, frames 7, 4, 5, 6, 5, 4 at delay 9; 7 is shut. */
    private final int[] claw = {7, 4, 5, 6, 5, 4};

    private RomBody[][] bursts = new RomBody[2][];
    private int heroX;
    private int heroFeet;

    RobotnikOfferPicture(Shell shell) {
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        bursts = new RomBody[2][];
        if (verb.equals("refuse")) {
            shell.sfx(Sounds.SFX_SWITCH);
        }
    }

    @Override
    boolean playing() {
        return verb().equals("accept") ? clock < ACCEPT_LENGTH : clock < OFF_LENGTH;
    }

    @Override
    void step(Shell shell) {
        if (!verb().equals("accept")) {
            return;
        }
        if (clock == ARRIVE) {
            shell.sfx(Sounds.SFX_GRAB);
        }
        for (int i = 0; i < sparksAt.length; i++) {
            if (clock == sparksAt[i]) {
                shell.sfx(Sounds.SFX_MECHA_SPARK);
                bursts[i] = sparkBurst(heroX + (i == 0 ? -14 : 14), heroFeet - 20);
            }
            if (bursts[i] != null && clock - sparksAt[i] < 20) {
                for (RomBody s : bursts[i]) {
                    moveSpark(s);
                }
            }
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        int cx = x + w / 2;
        // The post, the speaker grille and the screen's steel frame.
        int sx = x + 22;
        int sy = y + 12;
        int sw = 82;
        int sh = 50;
        int postFeet = feet(placement, x, y, cx, ground);
        c.fill(cx - 4, sy + sh, 8, postFeet - sy - sh, 0xFF384858);
        c.fill(cx - 3, sy + sh, 2, postFeet - sy - sh, 0xFF6C7C8C);
        c.fill(cx - 10, postFeet - 3, 20, 3, 0xFF384858);
        c.fill(sx - 4, sy - 4, sw + 8, sh + 18, 0xFF101820);
        c.fill(sx - 3, sy - 3, sw + 6, sh + 16, 0xFF6C7C8C);
        c.fill(sx - 3, sy - 3, sw + 6, 1, 0xFFB6C6D6);
        for (int i = 0; i < 9; i++) {
            c.fill(cx - 18 + i * 4, sy + sh + 4, 2, 6, 0xFF283440);
        }
        boolean speaking = detail == null || verb().equals("accept") || clock < 6;
        c.fill(sx + sw - 8, sy + sh + 5, 3, 3, speaking && (t / 10) % 2 == 0 ? 0xFFFF4848 : 0xFF481010);
        drawScreen(shell, c, sx, sy, sw, sh, t, x, y, w, h);

        heroX = x + 40;
        heroFeet = feet(placement, x, y, heroX, ground);
        boolean accepting = verb().equals("accept");
        boolean working = accepting && clock >= ARRIVE && clock < RETRACT;
        int flash = working && (clock / 3) % 2 == 0 ? 0x80FFFFFF : 0;
        // The claws close on the hero from behind, so the arms are drawn first.
        if (accepting) {
            drawArms(shell, c, x, w, t);
        }
        Poses.hero(shell, c, hero(shell), working ? Poses.HURT : Poses.WAIT, t, heroX, heroFeet,
                SceneDraw.plain().withFlash(flash));
        if (accepting) {
            for (int i = 0; i < sparksAt.length; i++) {
                if (bursts[i] != null) {
                    for (RomBody s : bursts[i]) {
                        spark(shell, c, s.px(), s.py(), clock - sparksAt[i]);
                    }
                }
            }
        }
    }

    /** Robotnik on the screen, or the screen switching off and staying dark. */
    private void drawScreen(Shell shell, SceneCanvas c, int sx, int sy, int sw, int sh, long t, int wx, int wy,
            int ww, int wh) {
        c.fill(sx, sy, sw, sh, 0xFF102040);
        boolean off = verb().equals("refuse");
        if (!off || clock < 10) {
            BaseInterior.clipped(c, sx, sy, sw, sh, wx, wy, ww, wh);
            SceneSprite mobile = shell.art.romFrame("robotnik_ship", 5);
            SceneSprite head = shell.art.romFrame("robotnik_ship", (int) ((t / 6) % 2));
            float ox = sx + sw / 2f;
            float oy = sy + 46;
            if (mobile != null) {
                c.draw(mobile, ox, oy, SceneDraw.plain());
            }
            if (head != null) {
                c.draw(head, ox, oy - 0x1C, SceneDraw.plain());
            }
            c.clip(wx, wy, ww, wh);
            for (int i = 0; i < sh; i += 2) {
                c.fill(sx, sy + i, sw, 1, 0x30000000);
            }
        }
        if (!off) {
            return;
        }
        // Switching off: the picture squeezes to a bright line (10 frames), the line to a dot (6),
        // and the dot fades (mod timing).
        int midY = sy + sh / 2;
        if (clock < 10) {
            int half = Math.max(1, Math.round(sh / 2f * (1f - clock / 10f)));
            c.fill(sx, sy, sw, midY - half - sy, Colors.BLACK);
            c.fill(sx, midY + half, sw, sy + sh - midY - half, Colors.BLACK);
            c.fill(sx, midY - half, sw, half * 2, Colors.alpha(Colors.WHITE, clock / 12f));
            return;
        }
        c.fill(sx, sy, sw, sh, 0xFF06080C);
        if (clock < 16) {
            int half = Math.max(1, Math.round(sw / 2f * (1f - (clock - 10) / 6f)));
            c.fill(sx + sw / 2 - half, midY - 1, half * 2, 2, Colors.WHITE);
        } else if (clock < OFF_LENGTH) {
            c.fill(sx + sw / 2 - 1, midY - 1, 2, 2, Colors.alpha(Colors.WHITE, 1f - (clock - 16) / 14f));
        }
        // The dark glass keeps a faint reflection.
        c.fill(sx + 4, sy + 3, 14, 2, 0x20FFFFFF);
        c.fill(sx + 4, sy + 5, 6, 2, 0x18FFFFFF);
    }

    /**
     * The two arms: each a Big Arm claw on the end of its forearm, sliding in from its side of
     * the window, clamping on the hero, then the forearm leaving the claw behind.
     */
    private void drawArms(Shell shell, SceneCanvas c, int x, int w, long t) {
        float in = Math.min(1f, clock / (float) ARRIVE);
        float ease = 1f - (1f - in) * (1f - in);
        float out = clock < RETRACT ? 0f : Math.min(1f, (clock - RETRACT) / 20f);
        int handY = heroFeet - 18;
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side > 0;
            float gripX = heroX + side * 24;
            float startX = right ? x + w + 40 : x - 40;
            float handX = startX + (gripX - startX) * ease;
            int frame = clock < ARRIVE ? claw[(clock / 10) % claw.length] : 7;
            SceneSprite hand = shell.art.romFrame("lbz_final_boss2", frame);
            SceneSprite arm = shell.art.romFrame("lbz_final_boss2", 2);
            // The claws point left; the left arm is mirrored. The forearm sits $2A behind the claw,
            // as the Big Arm's own children are placed (forearm at $14, claw at -$16).
            SceneDraw style = SceneDraw.plain().withFlipX(!right);
            if (arm != null && out < 1f) {
                float armX = handX + side * 0x2A + side * out * 90;
                c.draw(arm, armX, handY, style);
            }
            if (hand != null) {
                c.draw(hand, handX, handY, style);
            } else {
                c.fill(Math.round(handX) - 8, handY - 6, 16, 12, 0xFF8C8C9C);
            }
        }
    }
}
