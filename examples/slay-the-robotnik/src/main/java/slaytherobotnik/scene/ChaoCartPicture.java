package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.ui.Colors;

/**
 * The Chao Cart event: a Chao's market cart on the act's level (the cart and the Chao are
 * drawn - neither is in Sonic 3 &amp; Knuckles) with its wares on the counter: item monitors,
 * the mod's potions, flickering to static as Ani_Monitor flickers them. Shown "buy:potion
 * ids", the hero pays - rings drawn over to the Chao as rings are pulled in ({@link
 * AttractedRings}), counted off with sfx_Switch every 4 frames like the act's score tally and
 * rung up with sfx_Register as the tally ends - then the Chao tosses the monitors bought over
 * one by one (sfx_GumballTab, the Gumball machine's dispenser; MoveSprite's $38 gravity) and the
 * hero catches each in a sparkle.
 */
final class ChaoCartPicture extends StagedPicture {
    private static final int HERO_COL = 18;
    private static final int CART_COL = 70;
    private static final int CHAO_COL = 115;
    /** sfx_GumballTab: the Gumball bonus stage's dispenser. */
    private static final int SFX_GUMBALL_TAB = 0xD2;
    private static final int PAY_RINGS = 6;
    /** A 3/4-size monitor's half height: its centre sits this far above the counter. */
    private static final int WARE_HALF = 12;
    /** Frames between monitors, their flight, and the wait after the last (the mod's choices). */
    private static final int TOSS_GAP = 16;
    private static final int TOSS_FLIGHT = 26;
    private static final int AFTER = 20;

    private final String hero;
    /** The wares on the counter, by potion id. */
    private final List<String> wares = new ArrayList<>();
    private List<String> bought;
    private AttractedRings payment;
    private int sincePaid = -1;
    private Motion[] tossed;
    private int caught;
    private int age;

    ChaoCartPicture(Shell shell) {
        hero = EventHero.id(shell);
        List<PotionDef> all = shell.catalog.allPotions();
        for (int i = 0; i < 3 && !all.isEmpty(); i++) {
            wares.add(all.get((int) ((shell.ticks / 7 + i * 5) % all.size())).id());
        }
    }

    @Override
    void show(Shell shell, String detail) {
        if (bought != null) {
            return;
        }
        bought = new ArrayList<>();
        if (detail.startsWith("buy:") && detail.length() > 4) {
            List<PotionDef> all = shell.catalog.allPotions();
            for (String id : detail.substring(4).split(",")) {
                for (PotionDef potion : all) {
                    if (potion.id().equals(id)) {
                        bought.add(id);
                    }
                }
            }
        }
        // The counter shows what is being bought, from the left.
        for (int i = 0; i < bought.size() && i < wares.size(); i++) {
            wares.set(i, bought.get(i));
        }
        float heroY = floor(HERO_COL) - EventHero.standRadius(hero);
        payment = new AttractedRings(PAY_RINGS, HERO_COL, heroY - 6, CHAO_COL, chaoTop() + 8, 4);
        tossed = new Motion[bought.size()];
    }

    @Override
    boolean busy() {
        return bought != null && !(caught == bought.size() && sincePaid >= tossEnd() + AFTER);
    }

    private int tossEnd() {
        return Math.max(0, bought.size() - 1) * TOSS_GAP + TOSS_FLIGHT;
    }

    @Override
    void tick(Shell shell) {
        age++;
        if (payment == null) {
            return;
        }
        if (sincePaid < 0) {
            payment.tick();
            if (age % 4 == 0) {
                shell.sfx(Sounds.SFX_SWITCH); // the tally's tick (every four frames)
            }
            if (payment.done()) {
                sincePaid = 0;
                shell.sfx(Sounds.SFX_REGISTER);
            }
            return;
        }
        sincePaid++;
        for (int i = 0; i < tossed.length; i++) {
            int start = 10 + i * TOSS_GAP;
            if (sincePaid == start) {
                tossed[i] = toss(i);
                shell.sfx(SFX_GUMBALL_TAB);
            }
            if (tossed[i] != null && sincePaid > start && sincePaid <= start + TOSS_FLIGHT) {
                tossed[i].step(Motion.PLAYER_GRAVITY);
                if (sincePaid == start + TOSS_FLIGHT) {
                    caught++;
                }
            }
        }
    }

    /** A monitor thrown from its spot on the counter to land in the hero's hands TOSS_FLIGHT frames later. */
    private Motion toss(int i) {
        float fromX = wareCol(i);
        float fromY = counterTop() - WARE_HALF;
        float toX = HERO_COL;
        float toY = floor(HERO_COL) - 34;
        Motion m = new Motion(fromX, fromY);
        m.xVel = Math.round((toX - fromX) * 256 / TOSS_FLIGHT);
        // y = fromY + v*n + g*n(n-1)/2 over n frames, solved for v.
        int n = TOSS_FLIGHT;
        m.yVel = Math.round(((toY - fromY) * 256 - Motion.PLAYER_GRAVITY * n * (n - 1) / 2f) / n);
        return m;
    }

    private int wareCol(int i) {
        return CART_COL - 22 + i * 22;
    }

    private int counterTop() {
        return floor(CART_COL) - 30;
    }

    /** The Chao stands beside its cart: its head's top row (EventArt.chao draws 25 rows from 8 above it). */
    private int chaoTop() {
        return floor(CHAO_COL) - 17;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        drawLevel(shell, c, x, y, w, h);
        boolean happy = payment != null;
        EventArt.chao(c, x + CHAO_COL, y + chaoTop() + EventArt.bob(t * (happy ? 3 : 1), 2), t);
        drawCart(c, x + CART_COL, y + floor(CART_COL), t);
        for (int i = 0; i < wares.size(); i++) {
            boolean gone = tossed != null && i < tossed.length && tossed[i] != null;
            if (!gone) {
                drawWare(shell, c, wares.get(i), x + wareCol(i), y + counterTop() - WARE_HALF, t + i * 5);
            }
        }
        Poses.hero(shell, c, hero, Poses.WAIT, t, x + HERO_COL, y + floor(HERO_COL), SceneDraw.plain());
        if (payment != null && sincePaid < 0) {
            for (int i = 0; i < payment.count(); i++) {
                int frame = payment.frame(i);
                if (payment.out(i) && frame >= 0) {
                    SceneSprite ring = shell.art.romFrame("ring", frame);
                    if (ring != null) {
                        c.draw(ring, x + payment.x(i), y + payment.y(i), SceneDraw.plain());
                    }
                }
            }
        }
        if (tossed != null) {
            for (int i = 0; i < tossed.length; i++) {
                Motion m = tossed[i];
                if (m == null) {
                    continue;
                }
                int start = 10 + i * TOSS_GAP;
                if (sincePaid < start + TOSS_FLIGHT) {
                    drawWare(shell, c, bought.get(i), x + m.x(), y + m.y(), t);
                } else if (sincePaid < start + TOSS_FLIGHT + 24) {
                    // Caught: the collect sparkle, Map_Ring 4-7 at 6 frames each.
                    int sparkle = 4 + Math.min(3, (sincePaid - start - TOSS_FLIGHT) / 6);
                    SceneSprite s = shell.art.romFrame("ring", sparkle);
                    if (s != null) {
                        c.draw(s, x + m.x(), y + m.y(), SceneDraw.plain());
                    }
                }
            }
        }
    }

    /**
     * An item monitor (3/4 size) with its origin at ({@code x}, {@code y}); its face shows static
     * a third of the time, on Ani_Monitor's frames 0 and 1 (two frames each in six).
     */
    private static void drawWare(Shell shell, SceneCanvas c, String potionId, float x, float y, long t) {
        long phase = (t / 2) % 6;
        String face = phase == 0 || phase == 3 ? "static" : HudIcons.monitorFace(potionId);
        HudIcons.monitor(shell, c, face, x, y, 0.75f, t);
    }

    /** The cart: a striped awning on two posts over a wooden counter on wheels. */
    private static void drawCart(SceneCanvas c, int cx, int ground, long t) {
        int body = 0xFFB46C24;
        int dark = 0xFF6C4018;
        int light = 0xFFDA9048;
        // Wheels.
        for (int side = -1; side <= 1; side += 2) {
            int wx = cx + side * 24;
            c.fill(wx - 6, ground - 12, 12, 12, Colors.BLACK);
            c.fill(wx - 4, ground - 10, 8, 8, 0xFF6C6C6C);
            c.fill(wx - 1, ground - 7, 2, 2, Colors.BLACK);
        }
        // Box and counter.
        c.fill(cx - 36, ground - 30, 72, 22, Colors.BLACK);
        c.fill(cx - 35, ground - 29, 70, 20, body);
        for (int i = 0; i < 4; i++) {
            c.fill(cx - 35, ground - 25 + i * 5, 70, 1, dark);
        }
        c.fill(cx - 35, ground - 29, 70, 2, light);
        // Posts and the awning, its scalloped edge swaying a little.
        c.fill(cx - 34, ground - 66, 3, 36, dark);
        c.fill(cx + 31, ground - 66, 3, 36, dark);
        int top = ground - 72;
        c.fill(cx - 40, top - 1, 80, 12, Colors.BLACK);
        for (int i = 0; i < 8; i++) {
            int colour = i % 2 == 0 ? 0xFFDA2424 : Colors.WHITE;
            c.fill(cx - 39 + i * 10, top, 10, 10, colour);
            int sway = (int) ((t / 20 + i) % 2);
            c.fill(cx - 37 + i * 10, top + 10, 6, 2 + sway, colour);
        }
    }
}
