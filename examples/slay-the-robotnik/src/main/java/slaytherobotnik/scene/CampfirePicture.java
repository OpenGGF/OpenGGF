package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.ui.Colors;

/**
 * The Flicky Campfire: the act's level at night, a campfire of Angel Island's burning-bridge
 * logs and flames (Map_AIZDrawBridgeFire: logs frame 0, flames 3-7 a step every 4 frames as
 * AIZCollapsingLogBridge_FirePieceAnimate runs them) with four Flickies (Map_Animals1) round it
 * and the hero warming himself. Shown the offering ("rarity|card id|upgraded|amount"), the card
 * drops into the fire and burns, then the Flickies answer by its rarity: a curse is pecked to
 * scraps and a gift glints down; a basic card gets their backs turned; a common one makes the
 * fire crackle; an uncommon one makes it roar while they fly up singing (the Flicky's own
 * flight: $18 gravity, wings beating every 2 frames, loc_2CA7C); a rare one turns it gold.
 */
final class CampfirePicture extends EventPicture {
    private static final int HERO_X = 10;
    private static final int FIRE_X = 66;
    /** AIZCollapsingLogBridge_FirePieceAnimate: flame frames 3-7, each held 4 frames. */
    private static final int FLAME_FIRST = 3;
    private static final int FLAME_FRAMES = 5;
    private static final int FLAME_HOLD = 4;
    /** loc_2CA7C: a flying animal falls with $18 gravity and beats its wings every 2 frames. */
    private static final int FLICKY_GRAVITY = 0x18;
    /** word_2C7EA: the Flicky's jump speed, -$300 (Map_Animals1). */
    private static final int FLICKY_JUMP = -0x300;
    /** The card's drop and burn, and the reactions, are timed by the mod (no ROM scene burns a card). */
    private static final int DROP_FRAMES = 20;
    private static final int BURN_FRAMES = 44;
    private static final int REACT_AT = DROP_FRAMES + BURN_FRAMES;

    /** Each Flicky's column, its spot to return to, and its flight. */
    private final int[] flickyX = {FIRE_X - 36, FIRE_X - 22, FIRE_X + 22, FIRE_X + 36};
    private final int[] flickyHome = {FIRE_X - 36, FIRE_X - 22, FIRE_X + 22, FIRE_X + 36};
    private final EventActors.Fling[] flights = new EventActors.Fling[4];
    private CardRenderer renderer;
    private Card card;
    private String rarity = "";
    private int amount;
    private int age = -1;
    /** What the fire stays as afterwards: 0 normal, 1 roaring, 2 gold. */
    private int mood;
    private boolean backsTurned;

    CampfirePicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        String[] parts = detail.split("\\|");
        rarity = parts[0];
        amount = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
        try {
            card = new Card(shell.catalog.card(parts[1]), parts.length > 2 && parts[2].equals("1"));
        } catch (RuntimeException e) {
            card = null;
        }
        age = 0;
    }

    @Override
    boolean busy() {
        return age >= 0 && age < REACT_AT + reactionFrames();
    }

    private int reactionFrames() {
        return switch (rarity) {
            case CardRarity.CURSE -> 90;
            case CardRarity.UNCOMMON -> 96;
            case CardRarity.RARE -> 84;
            default -> 50;
        };
    }

    private boolean curse() {
        return rarity.equals(CardRarity.CURSE);
    }

    @Override
    void tick(Shell shell) {
        long t = shell.ticks;
        if (age >= 0) {
            age++;
            react(shell);
        }
        for (int i = 0; i < 4; i++) {
            EventActors.Fling f = flights[i];
            if (f != null) {
                f.step();
                if (f.yVel > 0 && f.py() >= 0) {
                    flights[i] = null;
                }
            } else if (age < 0 && (t + i * 97) % 240 == 0) {
                // A little hop now and then while they wait (a mod flourish, a third of a real jump).
                flights[i] = new EventActors.Fling(0, 0, 0, FLICKY_JUMP / 3, FLICKY_GRAVITY);
            }
        }
    }

    private void react(Shell shell) {
        if (age == DROP_FRAMES && !curse()) {
            shell.sfx(Sounds.SFX_FIRE_ATTACK);       // the card catches
        }
        int r = age - REACT_AT;
        if (curse()) {
            // They hop over to the curse and peck it to scraps.
            if (age >= DROP_FRAMES && age < REACT_AT) {
                for (int i = 0; i < 4; i++) {
                    int target = FIRE_X + (i < 2 ? -14 + i * 6 : 8 + (i - 2) * 6);
                    flickyX[i] += Integer.signum(target - flickyX[i]);
                }
                if (age % 8 == 0) {
                    shell.sfx(Sounds.SFX_CLANK);
                }
            }
            if (r == 0) {
                shell.sfx(Sounds.SFX_BREAK);
            }
            if (r == 30) {
                shell.sfx(Sounds.SFX_RING);          // the gift glints down
            }
            if (r > 30) {
                for (int i = 0; i < 4; i++) {
                    flickyX[i] += Integer.signum(flickyHome[i] - flickyX[i]);
                }
            }
            return;
        }
        if (r != 0) {
            return;
        }
        switch (rarity) {
            case CardRarity.BASIC -> backsTurned = true;
            case CardRarity.UNCOMMON -> {
                mood = 1;
                shell.sfx(Sounds.SFX_FIRE_ATTACK);
                for (int i = 0; i < 4; i++) {
                    flights[i] = new EventActors.Fling(0, 0, 0, FLICKY_JUMP, FLICKY_GRAVITY);
                }
            }
            case CardRarity.RARE -> {
                mood = 2;
                shell.sfx(Sounds.SFX_SUPER_TRANSFORM);
            }
            default -> shell.sfx(Sounds.SFX_FIRE_SHIELD);
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        c.fill(x, y, w, h, 0xA0000820);                // night
        int fx = x + FIRE_X;
        int fireFloor = EventActors.floor(placement, x, y, fx, ground);
        boolean flaring = age >= 0 && age >= DROP_FRAMES && age < REACT_AT && !curse();
        int roar = mood == 1 ? 2 : flaring || commonFlare() ? 1 : 0;
        firelight(c, fx, fireFloor, roar, t);

        int heroFloor = EventActors.floor(placement, x, y, x + HERO_X, ground);
        EventActors.hero(shell, c, Poses.WAIT, t, x + HERO_X, heroFloor, false,
                SceneDraw.plain().withTint(0xFFFFE0C0));
        // An offering burns in the flames, so they are drawn over it; a curse lies in front, refused.
        if (!curse()) {
            drawCard(shell, c, x, y, w, h, fx, fireFloor, t);
        }
        fire(shell, c, fx, fireFloor, roar, t);
        if (curse()) {
            drawCard(shell, c, x, y, w, h, fx, fireFloor, t);
        }
        for (int i = 0; i < 4; i++) {
            drawFlicky(shell, c, x, y, ground, placement, i, t);
        }
        reactionExtras(shell, c, x, heroFloor, fx, fireFloor, t);
    }

    private boolean commonFlare() {
        return age >= REACT_AT && age < REACT_AT + 30 && !curse() && !rarity.equals(CardRarity.BASIC)
                && mood == 0;
    }

    /** The warm light round the fire: layered glows, gold once it turns. */
    private void firelight(SceneCanvas c, int fx, int floor, int roar, long t) {
        int colour = mood == 2 ? EventArt.GOLD & 0xFFFFFF : 0xFF9024;
        int flicker = (int) ((t / 3) % 3);
        for (int r = 4; r >= 1; r--) {
            int size = (10 + roar * 6) * r + flicker;
            int a = 0x14 + roar * 4;
            c.fill(fx - size, floor - size, size * 2, size + 4, (a << 24) | colour);
        }
    }

    private void fire(Shell shell, SceneCanvas c, int fx, int floor, int roar, long t) {
        SceneSprite logs = shell.art.romFrame("aiz_bridge_fire", 0);
        int frame = FLAME_FIRST + (int) ((t / FLAME_HOLD) % FLAME_FRAMES);
        SceneSprite flames = shell.art.romFrame("aiz_bridge_fire", frame);
        if (logs == null || flames == null) {
            EventArt.fire(c, fx, floor, t);
            return;
        }
        SceneDraw style = mood == 2 ? SceneDraw.plain().withFlash((t / 6) % 2 == 0 ? 0x90FFDA24 : 0x70FFF090)
                : SceneDraw.plain();
        if (roar >= 1) {
            // Roaring: a second, offset layer of flames, and a third above it.
            SceneSprite more = shell.art.romFrame("aiz_bridge_fire", FLAME_FIRST + (frame - FLAME_FIRST + 2)
                    % FLAME_FRAMES);
            Poses.stand(c, more, fx, floor - 14, style);
            if (roar == 2) {
                Poses.stand(c, flames, fx, floor - 26, style.withScale(0.75f));
            }
        }
        Poses.stand(c, flames, fx, floor - 6, style);
        Poses.stand(c, logs, fx, floor, SceneDraw.plain().withTint(0xFFC0A080));
    }

    /** The offered card: it drops into the fire and chars from the bottom up, or lies there being pecked. */
    private void drawCard(Shell shell, SceneCanvas c, int x, int y, int w, int h, int fx, int floor, long t) {
        if (card == null || age < 0 || age >= REACT_AT) {
            if (curse() && age >= REACT_AT && age < REACT_AT + 40) {
                scraps(c, fx, floor - 20, age - REACT_AT);
            }
            return;
        }
        if (renderer == null) {
            renderer = new CardRenderer(shell);
        }
        float k = Math.min(1f, age / (float) DROP_FRAMES);
        int cw = CardRenderer.SMALL_W;
        int chH = CardRenderer.SMALL_H;
        int cx = fx - cw / 2;
        int restTop = curse() ? floor - chH - 2 : floor - chH - 8;
        int top = Math.round(y + 4 + (restTop - (y + 4)) * k * k);
        int burning = age - DROP_FRAMES;
        if (!curse() && burning >= BURN_FRAMES - 10) {
            // The charred husk crumbles down into the fire.
            int left = Math.max(0, chH * (BURN_FRAMES - burning) / 10);
            c.fill(cx, top + chH - left, cw, left, 0xF0100808);
            embers(c, cx, cw, top + chH - left, t);
            return;
        }
        int shiver = curse() && age > DROP_FRAMES && (age / 2) % 2 == 0 ? 1 : 0;
        renderer.drawSmall(c, card, null, cx + shiver, top, true, false);
        c.clip(x, y, w, h);                            // the card's art clears the clip
        if (curse() || burning < 0) {
            return;
        }
        // Charring: black creeps up from the flames, its edge glowing.
        float burnt = Math.min(1f, burning / (float) (BURN_FRAMES - 10));
        int charH = Math.round(chH * burnt);
        int edge = top + chH - charH;
        c.fill(cx - 2, edge, cw + 4, top + chH + 2 - edge, 0xF0100808);
        c.fill(cx - 2, edge - 2, cw + 4, 2, (t / 2) % 2 == 0 ? 0xFFFFDA24 : 0xFFFF9024);
        embers(c, cx, cw, edge, t);
    }

    /** Sparks rising from a burning edge at row {@code edge}. */
    private static void embers(SceneCanvas c, int cx, int cw, int edge, long t) {
        for (int i = 0; i < 4; i++) {
            int ex = cx + (int) ((i * 13 + t * 3) % cw);
            int ey = edge - 4 - (int) ((t + i * 7) % 18);
            c.fill(ex, ey, 2, 2, (t + i) % 3 == 0 ? 0xFFFFDA24 : 0xFFFF6C24);
        }
    }

    /** The curse in scraps, flying apart from where the Flickies pecked it. */
    private static void scraps(SceneCanvas c, int fx, int cy, int age) {
        for (int i = 0; i < 8; i++) {
            float vx = (i - 3.5f) * 0.9f;
            float vy = -2.2f + (i % 3) * 0.5f;
            int px = Math.round(fx + vx * age);
            int py = Math.round(cy + vy * age + 0.09f * age * age);
            c.fill(px, py, 4, 3, i % 2 == 0 ? 0xFF6C2490 : 0xFF241024);
        }
    }

    private void drawFlicky(Shell shell, SceneCanvas c, int x, int y, int ground, LevelStages.Placement placement,
            int i, long t) {
        int px = x + flickyX[i];
        int floor = EventActors.floor(placement, x, y, px, ground);
        EventActors.Fling f = flights[i];
        boolean pecking = curse() && age >= DROP_FRAMES + 16 && age < REACT_AT;
        // Map_Animals1: frame 2 standing (as released), 0-1 the wingbeat, swapped every 2 frames in the air.
        int frame = f != null ? (int) ((t / 2) % 2) : pecking ? (int) (((t + i * 3) / 4) % 2) : 2;
        SceneSprite flicky = shell.art.romFrame("flicky", frame);
        boolean facesLeft = flickyX[i] > FIRE_X;
        if (backsTurned) {
            facesLeft = !facesLeft;
        }
        float lift = f != null ? f.py() : 0;
        // The art faces left; mirror it to face right.
        Poses.stand(c, flicky, px, floor + lift, SceneDraw.plain().withFlipX(!facesLeft)
                .withTint(mood == 2 ? 0xFFFFF0B0 : 0xFFFFE8D0));
    }

    private void reactionExtras(Shell shell, SceneCanvas c, int x, int heroFloor, int fx, int fireFloor, long t) {
        int r = age - REACT_AT;
        if (age < 0 || r < 0) {
            return;
        }
        switch (rarity) {
            case CardRarity.CURSE -> {
                // The gift: a glint drifting down to the hero.
                if (r >= 20 && r < 60) {
                    float k = (r - 20) / 30f;
                    float gx = fx + (x + HERO_X + 8 - fx) * Math.min(1f, k);
                    float gy = fireFloor - 50 + 30 * Math.min(1f, k);
                    EventActors.sparkle(shell, c, gx, gy, (r * 2) % 24);
                    EventArt.ring(shell, c, Math.round(gx), Math.round(gy), t);
                }
            }
            case CardRarity.RARE -> {
                for (int k = 0; k < 4; k++) {
                    double a = r * 0.1 + k * Math.PI / 2;
                    EventActors.sparkle(shell, c, fx + (float) Math.cos(a) * 20, fireFloor - 20
                            + (float) Math.sin(a) * 12, (r + k * 6) % 24);
                }
                EventActors.number(shell, c, "+" + amount, "MAX HP", Colors.TEXT_GOOD, x + HERO_X + 6,
                        heroFloor - 56, x, r - 10, 60);
            }
            case CardRarity.BASIC -> {
            }
            default -> {
                if (amount > 0) {
                    EventActors.number(shell, c, "+" + amount, null, Colors.TEXT_GOOD, x + HERO_X + 6,
                            heroFloor - 56, x, r - 10, 60);
                }
            }
        }
    }
}
