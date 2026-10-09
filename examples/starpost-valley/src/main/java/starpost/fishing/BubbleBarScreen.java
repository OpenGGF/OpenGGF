package starpost.fishing;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.state.SnapshotRandom;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The Bubble Bar's overlay (design doc §6.6): a column of Labyrinth water beside the scene, the
 * ROM's air bubble (Map_Bub's full bubble, stretched through its middle row and shrinking through
 * its smaller frames as the line pulls taut), the hooked catch, and the reel meter. On a badnik the
 * drowning countdown's digits rise over the bubble as the catch slips away: pure theatre.
 */
final class BubbleBarScreen implements Screen {
    /** What happens after: landed (and whether perfectly), or lost. */
    interface Done {
        void finish(boolean landed, boolean perfect);
    }

    private static final int END_TICKS = 50;
    private static final int COLUMN_W = 40;
    // S3K sound effects (sonic3k.constants.asm).
    private static final int SFX_SPLASH = 0x39;
    private static final int SFX_AIR_DING = 0xA9;
    private static final int SFX_CHAIN_TENSION = 0x7C;
    private static final int SFX_BUBBLE = 0x38;

    private final FishArt art;
    private final FishDef def;
    private final BubbleBar bar;
    private final SnapshotRandom rng;
    private final Done done;
    private int result = BubbleBar.PLAYING;
    private int endTicks;
    private int digit = 6;
    private boolean wasInside = true;
    private boolean taut;

    BubbleBarScreen(FishArt art, FishDef def, BubbleBar bar, long seed, Done done) {
        this.art = art;
        this.def = def;
        this.bar = bar;
        this.rng = new SnapshotRandom(seed);
        this.done = done;
    }

    /** The bar being played (the scene below draws the bent rod from it). */
    BubbleBar bar() {
        return bar;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    static boolean held(Shell shell) {
        return shell.ctx.buttonDown(SceneButtons.A | SceneButtons.B | SceneButtons.C) || shell.ctx.keyDown(SceneKeys.X)
                || shell.ctx.keyDown(SceneKeys.Z);
    }

    @Override
    public void update(Shell shell) {
        if (result != BubbleBar.PLAYING) {
            if (++endTicks >= END_TICKS) {
                shell.pop();
                done.finish(result == BubbleBar.CAUGHT, bar.perfect);
            }
            return;
        }
        result = bar.step(held(shell), rng);
        boolean inside = bar.inside();
        if (inside && !wasInside) {
            shell.sfx(SFX_BUBBLE);
        }
        wasInside = inside;
        if (bar.tension > 0.8f && !taut) {
            shell.sfx(SFX_CHAIN_TENSION);
        }
        taut = bar.tension > 0.6f;
        if (def.isBadnik()) {
            int now = countdown();
            if (now < digit && now <= 5) {
                shell.sfx(SFX_AIR_DING);
            }
            digit = now;
        }
        if (result == BubbleBar.CAUGHT) {
            shell.sfx(bar.perfect ? Sfx.PERFECT : Sfx.RING);
        } else if (result == BubbleBar.ESCAPED) {
            shell.sfx(SFX_SPLASH);
        }
    }

    /** The countdown digit (5..1) while the catch is slipping, 6 when it is safe. */
    private int countdown() {
        if (bar.progress >= 0.5f || bar.ticks < BubbleBar.GRACE) {
            return 6;
        }
        return Math.max(1, Math.min(5, (int) Math.ceil(bar.progress * 10)));
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int h = (int) BubbleBar.HEIGHT;
        int pw = COLUMN_W + 46, ph = h + 46;
        int px = canvas.width() - pw - 22, py = 30;
        Text.panel(canvas, px, py, pw, ph);
        String title = def.badnik() == null ? "FISH ON!" : def.id().equals(Fishing.RED_CHOPPER) ? "RED CHOPPER!" : "BADNIK!";
        int tw = canvas.textWidth(title);
        Text.shadow(canvas, title, px + (pw - tw) / 2, py + 5, def.isBadnik() ? Text.RED : Text.YELLOW);
        int cx = px + 10, top = py + 18;
        drawWater(shell, canvas, cx, top, h);
        canvas.clip(cx, top, COLUMN_W, h);
        drawBubble(canvas, cx + COLUMN_W / 2f, top + bar.bubble, bar.half(), !bar.inside());
        drawCatch(shell, canvas, cx + COLUMN_W / 2f, top + bar.fish);
        canvas.unclip();
        if (def.isBadnik() && digit <= 5) {
            SceneSprite number = art.bubble(FishArt.DIGIT_FIVE + 5 - digit);
            if (number != null) {
                float bob = (float) Math.sin(shell.ticks / 5.0) * 2;
                canvas.draw(number, cx + COLUMN_W / 2f, top + bar.bubble - bar.half() - 14 + bob, SceneDraw.plain());
            }
        }
        drawMeter(canvas, cx + COLUMN_W + 8, top, h);
        String hint = result == BubbleBar.CAUGHT ? "LANDED!" : result == BubbleBar.ESCAPED ? "IT GOT AWAY" : "HOLD: RISE";
        int hw = canvas.textWidth(hint);
        Text.shadow(canvas, hint, px + (pw - hw) / 2, py + ph - 13,
                result == BubbleBar.ESCAPED ? Text.RED : result == BubbleBar.CAUGHT ? Text.GREEN : Text.GREY);
    }

    /** Labyrinth-blue water darkening with depth, with the ROM's small bubbles drifting up. */
    private void drawWater(Shell shell, SceneCanvas canvas, int x, int top, int h) {
        canvas.fill(x - 2, top - 2, COLUMN_W + 4, h + 4, 0xFF240000);
        for (int y = 0; y < h; y += 8) {
            int band = y * 6 / h;
            int colour = switch (band) {
                case 0 -> 0xFF4992DB;
                case 1 -> 0xFF2472B6;
                case 2 -> 0xFF2449B6;
                case 3 -> 0xFF244992;
                case 4 -> 0xFF00246D;
                default -> 0xFF002449;
            };
            canvas.fill(x, top + y, COLUMN_W, Math.min(8, h - y), colour);
        }
        canvas.fill(x, top, COLUMN_W, 1, 0xFFB6DBFF);
        for (int i = 0; i < 6; i++) {
            long t = shell.ticks + i * 53L;
            int y = h - (int) (t * (1 + i % 3) / 2 % (h + 8));
            int bx = x + 4 + (i * 13) % (COLUMN_W - 8) + (int) (Math.sin(t / 9.0) * 2);
            SceneSprite small = art.bubble(i % 3);
            if (small != null && y > 2) {
                canvas.draw(small, bx, top + y, SceneDraw.plain());
            }
        }
    }

    /**
     * The air bubble at any height: the frame's top and bottom halves at their own size and its
     * middle row repeated between them, so ROM pixels are never scaled. Smaller frames take over
     * as it shrinks (Map_Bub 6 is 32 pixels, 5 is 24, 4 is 16).
     */
    private void drawBubble(SceneCanvas canvas, float x, float centre, float half, boolean slipping) {
        int frame = half >= 16 ? FishArt.BUBBLE_FULL : half >= 12 ? 5 : 4;
        SceneSprite sprite = art.bubble(frame);
        if (sprite == null) {
            canvas.fill(Math.round(x - 12), Math.round(centre - half), 24, Math.round(half * 2), 0x80FFFFFF);
            return;
        }
        SceneImage image = sprite.image();
        int size = image.height(), cap = size / 2;
        float left = x - image.width() / 2f, topY = centre - half;
        SceneDraw style = slipping ? SceneDraw.plain().withTint(0xFFFFB6B6) : SceneDraw.plain();
        float middle = Math.max(0, half * 2 - size);
        canvas.drawRegion(image, 0, 0, image.width(), cap, left, topY, image.width(), cap, style);
        if (middle > 0) {
            canvas.drawRegion(image, 0, cap - 1, image.width(), 1, left, topY + cap, image.width(), middle, style);
        }
        canvas.drawRegion(image, 0, cap, image.width(), size - cap, left, topY + cap + middle, image.width(), size - cap,
                style);
    }

    private void drawCatch(Shell shell, SceneCanvas canvas, float x, float y) {
        SceneImage picture = def.isBadnik()
                ? art.badnikFrame(def.badnik(), FishArt.frameAt(def.badnik(), shell.ticks, bar.firing))
                : art.picture(def.id());
        if (picture == null) {
            canvas.fill(Math.round(x) - 6, Math.round(y) - 3, 12, 6, 0xFFFFDB00);
            return;
        }
        float wiggle = bar.inside() ? 0 : (float) Math.sin(shell.ticks / 2.0);
        // Only Jaws turns round (Jaws_Swim flips it on its timer); the rest face the line.
        SceneDraw style = SceneDraw.plain().withFlipX(def.motion() == FishDef.JAWS && !bar.facingLeft);
        canvas.draw(picture, x - picture.width() / 2f + wiggle, y - picture.height() / 2f, style);
    }

    /** The reel meter: red when it is nearly gone, yellow, then green. */
    private void drawMeter(SceneCanvas canvas, int x, int top, int h) {
        canvas.fill(x - 1, top - 1, 10, h + 2, 0xFF240000);
        canvas.fill(x, top, 8, h, 0xFF101838);
        int fill = Math.round(h * bar.progress);
        int colour = bar.progress < 0.3f ? 0xFFFF4924 : bar.progress < 0.65f ? 0xFFFFDB00 : 0xFF49DB49;
        canvas.fill(x, top + h - fill, 8, fill, colour);
        canvas.fill(x, top + h - fill, 2, fill, 0x60FFFFFF);
        for (int i = 1; i < 4; i++) {
            canvas.fill(x, top + h * i / 4, 8, 1, 0x80000000);
        }
    }
}
