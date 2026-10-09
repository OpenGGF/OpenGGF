package starpost.fishing;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import starpost.core.Game;
import starpost.farm.BeltRunner;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * Fishing the farm pond in belt view. With the rod in hand, the action button at the pond's
 * edge casts (the pond's own hook in {@link FarmView}); the bobber arcs into the water and bobs.
 * Walking off reels in. When it goes under, the action button strikes and the Bubble Bar opens.
 * A landed catch is held up overhead; a badnik pops and its animal hops free.
 */
final class PondLine implements Actor {
    private static final int SHOW_TICKS = 110;
    private static final int SFX_PLOP = 0x6C;        // sfx_Splash2
    private static final int SFX_CAST = 0x3C;        // sfx_Roll: the line whipping out
    private static final int SFX_STRIKE = 0x4A;      // sfx_Grab

    private final FishingSystem sys;
    private final PlayScreen play;
    final Line line = new Line();
    private float castX;
    private float castFeet;
    private String hooked;
    private long landedAt = -1000;
    private String landedId;
    private boolean landedFreed;
    private float splashX;
    private float splashY;
    private long splashAt = -1000;
    private long biteAt = -1000;
    private long reeledAt = -1000;
    /** Debug: what the next bite is. */
    private String forced;

    PondLine(FishingSystem sys, PlayScreen play) {
        this.sys = sys;
        this.play = play;
    }

    FishingSystem system() {
        return sys;
    }

    /** Debug: lands a catch as if just reeled in (shown over the farmer's head). */
    void debugLand(Shell shell, String id) {
        line.toX = play.farm().runner.x - 20;
        line.toY = play.farm().feetY() + 10;
        show(shell, Fishing.land(shell.game, id, false));
    }

    /** Debug: the line out bites now (on {@code id}, or the waters' own choice). */
    boolean debugBite(String id) {
        if (line.state != Line.WAITING) {
            return false;
        }
        line.timer = 1;
        forced = id;
        return true;
    }

    /** Debug: straight into the Bubble Bar on {@code id} (casting first if the line is in). */
    boolean debugFight(Shell shell, String id) {
        if (!line.out() && !cast(shell)) {
            return false;
        }
        line.state = Line.BITE;
        line.timer = Line.BITE_WINDOW;
        line.strike();
        strike(shell, id);
        return true;
    }

    @Override
    public int view() {
        return FARM;
    }

    @Override
    public float x() {
        return line.out() ? castX : play.farm().runner.x;
    }

    @Override
    public float y() {
        return line.out() ? castFeet : play.farm().feetY();
    }

    @Override
    public float reach() {
        return line.out() ? 64 : -1;
    }

    /** The pond's hook: the action button at its edge. True when the rod was used. */
    boolean cast(Shell shell) {
        Game game = shell.game;
        if (!Fishing.holdingRod(game) || line.out()) {
            return false;
        }
        if (shell.ticks - reeledAt < 30) {
            return true;              // the button still held from the Bubble Bar: no accidental recast
        }
        FarmView farm = play.farm();
        BeltRunner runner = farm.runner;
        float feet = farm.feetY();
        float cx = (FarmView.POND_X0 + FarmView.POND_X1) / 2f, cy = (FarmView.POND_TOP + FarmView.POND_BOTTOM) / 2f;
        float tx = runner.x + (cx - runner.x) * 0.72f, ty = feet + (cy - feet) * 0.72f;
        tx = Math.max(FarmView.POND_X0 + 18, Math.min(FarmView.POND_X1 - 18, tx));
        ty = Math.max(FarmView.POND_TOP + 6, Math.min(FarmView.POND_BOTTOM - 6, ty));
        runner.facingLeft = tx < runner.x;
        runner.speed = 0;
        runner.depthSpeed = 0;
        castX = runner.x;
        castFeet = feet;
        float dir = runner.facingLeft ? -1 : 1;
        line.cast(runner.x + dir * 19, feet - 33, tx, ty, Fishing.POND_DEPTH, Fishing.level(game));
        hooked = null;
        shell.sfx(SFX_CAST);
        return true;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
        if (!line.out()) {
            return;
        }
        if (line.state == Line.FIGHT) {
            return;
        }
        FarmView farm = play.farm();
        if (!play.onFarm() || Math.abs(farm.runner.x - castX) > 2 || Math.abs(farm.feetY() - castFeet) > 2
                || farm.runner.height > 0) {
            line.reelIn();
            return;
        }
        Game game = shell.game;
        switch (line.step(game.rng)) {
            case Line.SPLASH -> {
                splash(line.toX, line.toY, shell.ticks);
                shell.sfx(SFX_PLOP);
            }
            case Line.BITE_NOW -> {
                FishingSection section = Fishing.section(game);
                hooked = forced != null ? forced : sys.table.choose(FishTable.Waters.of(game, FishDef.POND,
                        Fishing.POND_DEPTH, section.landedOnce()), game.rng);
                forced = null;
                biteAt = shell.ticks;
                splash(line.toX, line.toY, shell.ticks);
                shell.sfx(SFX_PLOP);
            }
            case Line.MISSED -> {
                hooked = null;
                shell.toast("IT GOT AWAY...");
            }
            default -> {
            }
        }
    }

    private void splash(float x, float y, long now) {
        splashX = x;
        splashY = y;
        splashAt = now;
    }

    @Override
    public boolean interact(Shell shell, PlayScreen play) {
        if (!line.out()) {
            return false;
        }
        if (line.state == Line.BITE && hooked != null && line.strike()) {
            shell.sfx(SFX_STRIKE);
            strike(shell, hooked);
            return true;
        }
        if (line.state != Line.FIGHT) {
            line.reelIn();
        }
        return true;
    }

    private void strike(Shell shell, String id) {
        sys.strike(shell, id, landed -> show(shell, landed), () -> {
            line.reelIn();
            reeledAt = shell.ticks;
        });
    }

    private void show(Shell shell, Fishing.Landed landed) {
        landedAt = shell.ticks;
        landedId = landed.id();
        landedFreed = landed.freed();
        shell.toast(landed.message());
        if (landed.freed()) {
            splash(line.toX, line.toY, shell.ticks);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        FarmView farm = play.farm();
        float fx = (line.out() ? castX : farm.runner.x) - cx;
        float feet = line.out() ? castFeet : farm.feetY();
        boolean left = farm.runner.facingLeft;
        float dir = left ? -1 : 1;
        drawSplash(shell, canvas, cx);
        if (line.out()) {
            boolean pulling = line.state == Line.BITE || line.state == Line.FIGHT;
            float bend = pulling ? (float) Math.sin(shell.ticks / 2.0) * 2 + 6 : 0;
            float tipX = fx + dir * (19 - bend / 2), tipY = feet - 33 + bend;
            drawLine(canvas, fx + dir * 6, feet - 17, tipX, tipY, 0xFF924900, 0);
            float bx = line.bobberX() - cx, by = line.bobberY();
            drawLine(canvas, tipX, tipY, bx, by, 0xC0FFFFFF, pulling ? 0 : 6);
            if (line.state == Line.WAITING || line.state == Line.FLYING) {
                drawBobber(canvas, bx, by);
            }
            if (line.state != Line.FLYING) {
                drawRipples(shell, canvas, bx, line.toY, pulling);
            }
            if (line.state == Line.BITE) {
                float hop = Math.abs((float) Math.sin((shell.ticks - biteAt) / 4.0)) * 4;
                Text.shadow(canvas, "!", Math.round(fx) - 2, Math.round(feet - 58 - hop), Text.YELLOW);
            }
        }
        if (shell.ticks - landedAt < SHOW_TICKS && landedId != null) {
            drawLanded(shell, canvas, fx, feet, cx);
        }
    }

    private void drawSplash(Shell shell, SceneCanvas canvas, int cx) {
        long age = shell.ticks - splashAt;
        SceneSpriteSet set = sys.art.splash;
        if (age < 18 && set != null && set.frameCount() > 0) {
            int frame = Math.min(set.frameCount() - 1, (int) (age / 6));
            canvas.draw(set.frame(age < 6 ? 0 : frame), splashX - cx, splashY + 2, SceneDraw.plain());
        }
    }

    /** The bobber: an original red-and-white float. */
    private static void drawBobber(SceneCanvas canvas, float x, float y) {
        int bx = Math.round(x) - 2, by = Math.round(y) - 5;
        canvas.fill(bx, by, 5, 6, 0xFF240000);
        canvas.fill(bx + 1, by + 1, 3, 2, 0xFFDB2400);
        canvas.fill(bx + 1, by + 3, 3, 2, 0xFFFFFFFF);
        canvas.fill(bx + 2, by - 2, 1, 2, 0xFF240000);
    }

    /** Rings spreading on the water, faster while something pulls. */
    private static void drawRipples(Shell shell, SceneCanvas canvas, float x, float y, boolean pulling) {
        int period = pulling ? 14 : 48;
        for (int k = 0; k < 2; k++) {
            int age = (int) ((shell.ticks + k * period / 2) % period);
            float r = 3 + age * (pulling ? 0.8f : 0.3f);
            int alpha = Math.max(0, 0xA0 - age * 0xA0 / period);
            int colour = alpha << 24 | 0xFFFFFF;
            canvas.fill(Math.round(x - r), Math.round(y), Math.round(r * 2), 1, colour);
            canvas.fill(Math.round(x - r * 0.7f), Math.round(y - 1), Math.round(r * 1.4f), 1, colour & 0x80FFFFFF);
        }
    }

    /** A thin line with a little sag (a slack fishing line), one pixel per step. */
    static void drawLine(SceneCanvas canvas, float x0, float y0, float x1, float y1, int argb, float sag) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t + (float) Math.sin(t * Math.PI) * sag;
            canvas.fill(Math.round(x), Math.round(y), 1, 1, argb);
        }
    }

    /** The catch held up overhead, or the popped badnik's animal hopping off along the bank. */
    private void drawLanded(Shell shell, SceneCanvas canvas, float fx, float feet, int cx) {
        long age = shell.ticks - landedAt;
        FishDef def = sys.table.get(landedId);
        SceneImage picture = def != null && def.isBadnik() ? sys.art.badnikFrame(def.badnik(), (int) (age / 4 % 2))
                : def != null ? sys.art.picture(landedId) : null;
        if (def != null && def.isBadnik() && age < 20 && shell.art.explosion.frameCount() > 0) {
            int frame = Math.min(shell.art.explosion.frameCount() - 1, (int) (age / 4));
            canvas.draw(shell.art.explosion.frame(frame), line.toX - cx, line.toY - 10, SceneDraw.plain());
        }
        float rise = Math.min(1, age / 10f);
        if (picture != null && !(def.isBadnik() && age < 20)) {
            canvas.draw(picture, fx - picture.width() / 2f, feet - 44 - picture.height() * rise, SceneDraw.plain());
        } else if (def == null && shell.art.icons != null) {
            shell.art.icons.draw(canvas, shell.game.item(landedId), fx - 8, feet - 60, SceneDraw.plain());
        }
        if (landedFreed && age >= 12) {
            // Sonic 1's Green Hill frees rabbits and Flickies (Anml_VarIndex): one hops off along the bank.
            SceneSpriteSet animal = shell.art.animal(landedAt % 2 == 0 ? "pocky" : "flicky");
            if (animal != null && animal.frameCount() > 1) {
                float ax = fx + 12 + (age - 12) * 1.3f, hop = -Math.abs((float) Math.sin((age - 12) / 6.0)) * 10;
                SceneSprite a = animal.frame(hop < -4 ? 1 : 0);
                canvas.draw(a, ax, feet + 2 + hop - (a.height() - a.originY()), SceneDraw.plain().withFlipX(true));
            }
        }
    }
}
