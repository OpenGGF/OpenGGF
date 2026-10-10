package starpost.barn;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.state.SnapshotRandom;
import starpost.core.Game;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * An animal on the farm in belt view: Sonic 1's freed animal at its native size (Map_Animal
 * frames 0-1 hopping or flapping, 2 standing). It potters about in front of its house on dry days
 * (Peckies love snow) and stays in at night and in the rain; Rocky swims the farm pond. The action
 * button nearby pets it once a day (hearts rise); Rocky hands over the fish he caught.
 */
final class AnimalActor implements Actor {
    private static final int PET_SHOW = 100;
    private static final int OUT_FROM = 7 * 60;
    private static final int OUT_UNTIL = 19 * 60 + 30;
    private static final int SFX_PET = 0x8A;          // sfx_Bouncy
    private static final int SFX_SPLASH = 0x6C;       // sfx_Splash2

    private final BarnSystem sys;
    final Animal animal;
    private final SnapshotRandom rng;
    private float x;
    private float depth;
    private float targetX;
    private float targetDepth;
    private int idle;
    private boolean facingLeft;
    private long walked;
    private long petAt = -1000;
    private long anim;

    AnimalActor(BarnSystem sys, Animal animal) {
        this.sys = sys;
        this.animal = animal;
        rng = new SnapshotRandom(animal.id * 7919L + sys.shell.game.calendar.dayNumber());
        float[] spot = pick();
        x = spot[0];
        depth = spot[1];
        targetX = x;
        targetDepth = depth;
        idle = 30 + rng.nextInt(120);
        facingLeft = rng.nextInt(2) == 0;
    }

    private int home() {
        return Animals.home(animal.kind);
    }

    private boolean pond() {
        return home() == Animals.POND;
    }

    /** Somewhere to wander to: before its house, or (Rocky) inside the pond's water. */
    private float[] pick() {
        if (pond()) {
            float cx = (FarmView.POND_X0 + FarmView.POND_X1) / 2f, cy = (FarmView.POND_TOP + FarmView.POND_BOTTOM) / 2f;
            double a = rng.nextInt(628) / 100.0;
            float r = rng.nextInt(70) / 100f;
            float fx = cx + (float) Math.cos(a) * r * (FarmView.POND_X1 - FarmView.POND_X0 - 30) / 2;
            float fy = cy + (float) Math.sin(a) * r * (FarmView.POND_BOTTOM - FarmView.POND_TOP - 10) / 2;
            return new float[] {fx, fy - (FarmView.FIELD_TOP + 4)};
        }
        int centre = home() == Animals.COOP ? BarnSystem.COOP_X : BarnSystem.PEN_X;
        int span = home() == Animals.COOP ? 70 : 96;
        return new float[] {centre - span + rng.nextInt(span * 2), 4 + rng.nextInt(32)};
    }

    private float speed() {
        return switch (animal.kind) {
            case "pocky" -> 0.7f;
            case "cucky" -> 0.5f;
            case "rocky" -> 0.4f;
            case "picky" -> 0.35f;
            default -> 0.3f;
        };
    }

    /** Whether it is out on the field now. */
    boolean visible(Game game) {
        if (!sys.barn.animals.contains(animal)) {
            return false;
        }
        if (pond()) {
            return true;
        }
        int minutes = game.calendar.minutes();
        return sys.barn.level(home()) > 0 && minutes >= OUT_FROM && minutes < OUT_UNTIL
                && Animals.outside(animal.kind, game.weather);
    }

    @Override
    public int view() {
        return FARM;
    }

    @Override
    public float x() {
        return x;
    }

    @Override
    public float y() {
        return FarmView.FIELD_TOP + 4 + depth;
    }

    @Override
    public float reach() {
        return visible(sys.shell.game) ? 12 : -1;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
        if (!visible(shell.game)) {
            return;
        }
        anim++;
        if (idle > 0) {
            idle--;
            if (idle == 0) {
                float[] spot = pick();
                targetX = spot[0];
                targetDepth = spot[1];
            }
            return;
        }
        float dx = targetX - x, dd = targetDepth - depth;
        float distance = (float) Math.sqrt(dx * dx + dd * dd);
        if (distance < 1) {
            idle = 60 + rng.nextInt(200);
            return;
        }
        float step = Math.min(distance, speed());
        x += dx / distance * step;
        depth += dd / distance * step;
        if (Math.abs(dx) > 0.5f) {
            facingLeft = dx < 0;
        }
        walked++;
    }

    @Override
    public boolean interact(Shell shell, PlayScreen play) {
        Game game = shell.game;
        if (!visible(game)) {
            return false;
        }
        petAt = shell.ticks;
        idle = Math.max(idle, 90);
        if (pond() && animal.holding != null) {
            String id = sys.barn.takeCatch(game, animal);
            if (id != null) {
                shell.toast(animal.name + " BRINGS YOU A " + game.item(id).name() + "!");
                shell.sfx(SFX_SPLASH);
                return true;
            }
            shell.toast("NO ROOM FOR " + animal.name + "'S FISH");
            shell.sfx(Sfx.ERROR);
            return true;
        }
        boolean first = sys.barn.pet(game, animal);
        shell.toast(animal.name + (first ? " LOVES THAT!" : animal.fed ? " IS HAPPY" : " IS HUNGRY..."));
        shell.sfx(first ? SFX_PET : Sfx.SWITCH);
        return true;
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!visible(shell.game)) {
            return;
        }
        SceneSpriteSet set = shell.art.animal(animal.kind);
        if (set == null || set.frameCount() < 3) {
            return;
        }
        boolean walking = idle == 0;
        float feet = y();
        float sx = x - cx;
        float hop = walking ? -Math.abs((float) Math.sin(walked / 5.0)) * (animal.kind.equals("pocky") ? 5 : 2) : 0;
        int frame = walking ? (int) (anim / 8 % 2) : animal.kind.equals("cucky") && anim / 50 % 4 == 3 ? 0 : 2;
        if (pond()) {
            drawSwimming(canvas, set, sx, feet, tint, frame == 2 ? 0 : frame);
        } else {
            canvas.fill(Math.round(sx) - 6, Math.round(feet) - 1, 12, 3, 0x50000000);
            SceneSprite pose = set.frame(frame);
            canvas.draw(pose, sx, feet + hop - (pose.height() - pose.originY()), tint.withFlipX(frame != 2 && facingLeft));
        }
        if (pond() && animal.holding != null && anim / 30 % 2 == 0) {
            Text.shadow(canvas, "!", Math.round(sx) - 2, Math.round(feet) - 26, Text.YELLOW);
        }
        long since = shell.ticks - petAt;
        if (since < PET_SHOW) {
            drawHearts(shell, canvas, sx, feet - 30 - Math.min(8, since / 4f));
        }
    }

    /** Rocky in the water: only his top half shows, with a ripple at the waterline. */
    private void drawSwimming(SceneCanvas canvas, SceneSpriteSet set, float sx, float feet, SceneDraw tint, int frame) {
        SceneSprite pose = set.frame(frame);
        SceneImage image = pose.image();
        int shown = Math.max(1, image.height() * 3 / 5);
        float top = feet - shown - 1 + (float) Math.sin(anim / 12.0);
        float left = sx - pose.originX();
        canvas.drawRegion(image, 0, 0, image.width(), shown, left, top, image.width(), shown, tint.withFlipX(facingLeft));
        int ripple = (int) (anim / 10 % 4);
        canvas.fill(Math.round(sx) - 8 - ripple, Math.round(feet) - 1, 16 + ripple * 2, 1, 0x90FFFFFF);
    }

    /** Five hearts (filled by affection) and the name, after a pet. */
    private void drawHearts(Shell shell, SceneCanvas canvas, float sx, float y) {
        int hearts = animal.hearts();
        SceneImage full = sys.art.heart, empty = sys.art.heartEmpty;
        int w = full.width() + 1;
        float left = sx - w * 5 / 2f;
        canvas.fill(Math.round(left) - 2, Math.round(y) - 2, w * 5 + 3, full.height() + 4, 0xA0000818);
        for (int i = 0; i < 5; i++) {
            canvas.draw(i < hearts ? full : empty, left + i * w, y, SceneDraw.plain());
        }
        String name = animal.name;
        int tw = canvas.textWidth(name);
        Text.shadow(canvas, name, Math.round(sx) - tw / 2, Math.round(y) - 11, Text.WHITE);
    }
}
