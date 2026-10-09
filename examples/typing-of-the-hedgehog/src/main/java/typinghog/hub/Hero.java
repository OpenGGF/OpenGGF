package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.AnimationSampling;

/**
 * The hub's cast: Sonic from the running game's ROM wearing the shipped keyboard backpack,
 * and Tails (or, with no Sonic 2 or 3 &amp; Knuckles ROM, a Flicky) as the Typing School's
 * instructor. Every body pixel is the ROM's; only the backpack prop is original.
 */
final class Hero {
    /** Ticks per frame for the speed-driven run and walk scripts, which have no fixed rate. */
    private static final int RUN_TICKS = 3;
    private static final int WALK_TICKS = 6;

    private Hero() { }

    static SceneSprite frame(SceneSpriteSet set, int anim, long ticks) {
        if (set == null) return null;
        int speedTicks = anim == HubArt.ANIM_RUN || anim == HubArt.ANIM_ROLL ? RUN_TICKS : WALK_TICKS;
        try {
            return AnimationSampling.frame(set, anim, ticks, AnimationSampling.Timing.rom(speedTicks));
        } catch (RuntimeException unusable) {
            return null;
        }
    }

    /**
     * Sonic standing with his feet on {@code floorY} at {@code x}, wearing the backpack (behind
     * him, so the far strap hides under his arm), facing right unless {@code left}.
     */
    static void sonic(SceneCanvas c, HubArt art, String game, int anim, long ticks, int x, int floorY, boolean left,
                      float alpha) {
        SceneSpriteSet set = art.character(game, "sonic");
        SceneSprite sprite = frame(set, anim, ticks);
        SceneDraw style = SceneDraw.plain().withFlipX(left).withAlpha(alpha);
        int bob = anim == HubArt.ANIM_RUN ? (int) ((ticks / RUN_TICKS) % 2) : 0;
        boolean pack = anim != HubArt.ANIM_ROLL;
        SceneImage backpack = art.image("backpack");
        if (sprite == null) {
            if (backpack != null) c.draw(backpack, x - 8, floorY - 24, style);
            return;
        }
        int originY = floorY - (sprite.height() - sprite.originY());
        if (pack && backpack != null) {
            int bx = left ? x + 3 : x - 3 - backpack.width();
            c.draw(backpack, bx, originY - 14 + bob, style);
        }
        c.draw(sprite, x, originY, style);
        SceneImage strap = art.image("strap");
        if (pack && strap != null) c.draw(strap, left ? x - 4 : x + 2, originY - 11 + bob, style);
    }

    /** Tails (with his tails), or the Flicky stand-in, standing on {@code floorY}. */
    static void instructor(SceneCanvas c, HubArt art, String running, long ticks, int x, int floorY, boolean talking) {
        String tailsGame = art.tailsGame(running);
        if (tailsGame != null) {
            SceneSpriteSet tails = art.character(tailsGame, "tails");
            SceneSprite body = frame(tails, HubArt.ANIM_WAIT, ticks);
            if (body != null) {
                int originY = floorY - (body.height() - body.originY());
                SceneSpriteSet acc = art.tailsAccessory(tailsGame);
                if (acc != null) {
                    // The idle swish at 8 ticks a frame: Sonic 3 & Knuckles frames $22-$26;
                    // Sonic 2's Obj05Ani_Swish frames 9-$D (s2.asm).
                    int frame = ("s2".equals(tailsGame) ? 9 : 0x22) + (int) ((ticks / 8) % 5);
                    if (frame < acc.frameCount()) c.draw(acc.frame(frame), x, originY, SceneDraw.plain());
                }
                int hop = talking && (ticks / 6) % 2 == 0 ? -1 : 0;
                c.draw(body, x, originY + hop, SceneDraw.plain());
                return;
            }
        }
        SceneSpriteSet flicky = art.flicky(running);
        if (flicky != null) {
            SceneSprite bird = flicky.frame((int) ((ticks / 6) % 2));
            int hover = (int) Math.round(Math.sin(ticks * 0.1) * 3);
            c.draw(bird, x, floorY - 18 + hover, SceneDraw.plain().withScale(2));
            return;
        }
        SceneSprite sonic = frame(art.character(running, "sonic"), HubArt.ANIM_WAIT, ticks);
        if (sonic != null) c.draw(sonic, x, floorY - (sonic.height() - sonic.originY()), SceneDraw.plain());
    }

    /** A speech bubble with Tails' line, its tail pointing down-left at {@code (tailX, bottom)}. */
    static void speech(SceneCanvas c, HubFont font, String text, int x, int y, int w, int tailX, long shownTicks) {
        if (text == null || text.isEmpty()) return;
        int visible = (int) Math.min(text.length(), shownTicks * 2);
        var lines = font.wrap(text, w - 10, 1);
        int h = lines.size() * 11 + 8;
        c.fill(x + 1, y, w - 2, h, 0xF8FFFFFF);
        c.fill(x, y + 1, w, h - 2, 0xF8FFFFFF);
        Gfx.outline(c, x, y, w, h, 0xFF203060);
        for (int i = 0; i < 4; i++) c.fill(tailX + i, y + h + i - 1, 6 - i * 2 + 1, 1, i == 3 ? 0xFF203060 : 0xF8FFFFFF);
        int shown = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int take = Math.max(0, Math.min(line.length(), visible - shown));
            font.draw(c, line.substring(0, take), x + 5, y + 4 + i * 11, 0xFF182040, 1);
            shown += line.length() + 1;
        }
    }
}
