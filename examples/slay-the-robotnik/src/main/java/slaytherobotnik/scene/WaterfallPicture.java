package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import slaytherobotnik.core.Card;

/**
 * The Purifying Waterfall event: Angel Island's great waterfall - a strip of its level art,
 * its water colours cycling as AnPal_AIZ1 cycles them (line 2 colours 11-14 through
 * AnPal_PalAIZ1_1, a step every 8 frames) - pouring down over the act's level into the
 * churning foot of a Hydrocity waterfall ({@code Obj_HCZWaterSplash}, four frames of 8). Shown
 * "wash:card id" (a trailing "+" for an upgraded card), the card the player gave up drifts
 * into the stream and is carried down it like Angel Island's falling logs
 * ({@code AIZFallingLog_Log}: a pixel a frame, the log's spray churning round it), then
 * blinks away in the foam ({@code AIZFallingLog_LogInWater}: 60 frames, toggling every 4),
 * with sfx_Splash as it goes in.
 */
final class WaterfallPicture extends StagedPicture {
    private static final int HERO_COL = 26;
    private static final int FALL_COL = 86;
    private static final int FALL_WIDTH = 52;
    /** Where the strip comes from: AIZ act 1's great waterfall, above its water line. */
    private static final int STRIP_X = 0x24E0;
    private static final int STRIP_Y = 0x2D0;
    private static final int STRIP_HEIGHT = 150;
    /** Pal_AIZ line 2 colours 11-14 as the act loads them, and AnPal_PalAIZ1_1's four steps. */
    private static final int LOAD_COLOURS = 0xA8BB2;
    private static final int CYCLE = 0x2AF6;
    /** The card's drift into the stream (the mod's staging), then AIZFallingLog_LogInWater's blink. */
    private static final int DRIFT = 20;
    private static final int BLINK = 60;

    private final String hero;
    private SceneImage[] strip;
    private boolean triedStrip;
    private CardRenderer cards;
    private Card card;
    private int age = -1;
    private float cardX;
    private float cardY;
    private int inWater = -1;

    WaterfallPicture(Shell shell) {
        hero = EventHero.id(shell);
    }

    @Override
    void show(Shell shell, String detail) {
        if (card != null) {
            return;
        }
        String id = detail.startsWith("wash:") ? detail.substring(5) : "";
        boolean upgraded = id.endsWith("+");
        if (upgraded) {
            id = id.substring(0, id.length() - 1);
        }
        if (!shell.catalog.hasCard(id) || waterfall(shell) == null) {
            return;
        }
        card = new Card(shell.catalog.card(id), upgraded);
        cards = new CardRenderer(shell);
        age = 0;
    }

    @Override
    boolean busy() {
        return card != null && inWater < BLINK;
    }

    @Override
    void tick(Shell shell) {
        if (card == null || inWater >= BLINK) {
            return;
        }
        age++;
        float startX = HERO_COL + 10 - CardRenderer.SMALL_W / 2f;
        float startY = floor(HERO_COL) - 50;
        float streamX = FALL_COL - CardRenderer.SMALL_W / 2f;
        float streamY = 6;
        if (age <= DRIFT) {
            float f = age / (float) DRIFT;
            cardX = startX + (streamX - startX) * f;
            cardY = startY + (streamY - startY) * f - (float) Math.sin(f * Math.PI) * 16;
            return;
        }
        if (inWater < 0) {
            cardY += 1; // addq.w #1,y_pos
            if (cardY + CardRenderer.SMALL_H >= floor(FALL_COL) - 4) {
                inWater = 0;
                shell.sfx(Sounds.SFX_SPLASH);
            }
            return;
        }
        inWater++;
    }

    /** The waterfall strip in its four colour steps, or null without the level art. */
    private SceneImage[] waterfall(Shell shell) {
        if (!triedStrip) {
            triedStrip = true;
            SceneImage art = shell.art.levelForeground(0, 0, STRIP_X, STRIP_Y, FALL_WIDTH, STRIP_HEIGHT);
            if (art != null && shell.art.hasRom()) {
                int[] load = shell.art.rom().palette(LOAD_COLOURS, 4);
                int[] cycle = shell.art.rom().palette(CYCLE, 16);
                int[] pixels = art.pixels();
                strip = new SceneImage[4];
                for (int step = 0; step < 4; step++) {
                    int[] out = new int[pixels.length];
                    for (int i = 0; i < pixels.length; i++) {
                        out[i] = pixels[i];
                        for (int k = 0; k < 4; k++) {
                            if (pixels[i] == load[k]) {
                                out[i] = cycle[step * 4 + k];
                            }
                        }
                    }
                    strip[step] = new SceneImage(art.width(), art.height(), out);
                }
            }
        }
        return strip;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        drawLevel(shell, c, x, y, w, h);
        Poses.hero(shell, c, hero, Poses.WAIT, t, x + HERO_COL, y + floor(HERO_COL), SceneDraw.plain());
        SceneImage[] fall = waterfall(shell);
        int base = floor(FALL_COL);
        int left = x + FALL_COL - FALL_WIDTH / 2;
        if (fall == null) {
            c.fill(left, y, FALL_WIDTH, base, 0xFF2448B6);
            return;
        }
        // The strip, tiled down from the window's top to the floor; AnPal_AIZ1 steps every 8 frames.
        SceneImage image = fall[(int) ((t / 8) % 4)];
        for (int top = 0; top < base; top += STRIP_HEIGHT) {
            int rows = Math.min(STRIP_HEIGHT, base - top);
            c.drawRegion(image, 0, 0, FALL_WIDTH, rows, left, y + top, FALL_WIDTH, rows, SceneDraw.plain());
        }
        if (card != null && inWater < BLINK && (inWater < 0 || ((BLINK - 1 - inWater) & 4) == 0)) {
            cards.drawSmall(c, card, null, Math.round(x + cardX), Math.round(y + cardY), false, false);
            if (age > DRIFT) {
                // AIZFallingLog_Splash: the log's spray, a frame every 4.
                int spray = (age / 4) % 4;
                c.draw(shell.art.romFrame("aiz_log_splash", spray), x + FALL_COL,
                        y + cardY + CardRenderer.SMALL_H / 2f, SceneDraw.plain());
            }
        }
        // HCZWaterSplash_SplashMain: four frames, 8 each, where the water lands.
        int splash = (int) ((t / 8) % 4);
        c.draw(shell.art.romFrame("hcz_water_splash" + splash, splash), x + FALL_COL, y + base - 12,
                SceneDraw.plain());
        // A glint in the falling water now and then.
        if ((t / 6) % 23 < 4) {
            c.draw(shell.art.romFrame("ring", 4 + (int) ((t / 6) % 4)), left + 10 + (t / 23) % 32,
                    y + 20 + (t / 7) % 60, SceneDraw.plain());
        }
    }
}
