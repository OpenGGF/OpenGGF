package starpost.barn;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Shell;

/**
 * A Flicky Roost's flock (design doc §6.1, §9.11): four of S3K's blue Flickies. In the morning
 * each flies out to a crop the roost picked overnight and carries it home to the basket; the rest
 * of the day they circle the roost in the formation S3K's Flickies keep around Super Tails
 * ({@code Obj_SuperTailsBirds}: four birds a quarter turn apart, the angle advancing 2 of 256 a
 * frame, each aiming at sine/8 across and cosine/16 down from a point $20 above, accelerating $20
 * a frame toward it and four times that to turn round, vertical speed capped at $1000; wings
 * flapping every second frame). At night and in bad weather they perch on the roost.
 */
final class FlickyFlock implements Actor {
    private static final int BIRDS = 4;
    private static final float ACCEL = 0x20 / 256f;
    private static final float Y_CAP = 0x1000 / 256f;
    /** The roost's roof above its feet: where the flock sits and circles. */
    private static final int PERCH = 58;

    private final BarnSystem sys;
    private final int row;
    private final int column;
    private final float[] bx = new float[BIRDS];
    private final float[] by = new float[BIRDS];
    private final float[] vx = new float[BIRDS];
    private final float[] vy = new float[BIRDS];
    private final int[] angle = new int[BIRDS];
    private final int[] task = new int[BIRDS];
    private final boolean[] carrying = new boolean[BIRDS];
    private final List<Barn.Harvest> tasks = new ArrayList<>();
    private int nextTask;
    private long ticks;

    FlickyFlock(BarnSystem sys, int row, int column) {
        this.sys = sys;
        this.row = row;
        this.column = column;
        for (Barn.Harvest h : sys.barn.harvested) {
            if (h.roostRow() == row && h.roostColumn() == column) {
                tasks.add(h);
            }
        }
        for (int i = 0; i < BIRDS; i++) {
            angle[i] = i * 0x40;
            bx[i] = roostX() - 9 + i * 6;
            by[i] = feet() - PERCH + 2;
            task[i] = nextTask < tasks.size() ? nextTask++ : -1;
        }
    }

    private float roostX() {
        return FarmView.FIELD_X + column * 16 + 8;
    }

    private float feet() {
        return FarmView.ROW_Y + row * FarmView.ROW_STEP - 2;
    }

    private boolean resting(Game game) {
        return game.calendar.light() == 2 || game.raining || game.weather == Game.SNOW;
    }

    @Override
    public int view() {
        return FARM;
    }

    @Override
    public float x() {
        return roostX();
    }

    @Override
    public float y() {
        return feet() + 1;
    }

    @Override
    public float reach() {
        return -1;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
        if (!BarnSystem.roostAt(shell.game, row, column)) {
            return;
        }
        ticks++;
        boolean resting = resting(shell.game) && allHome();
        for (int i = 0; i < BIRDS; i++) {
            angle[i] = (angle[i] + 2) & 0xFF;
            if (resting) {
                bx[i] = roostX() - 9 + i * 6;
                by[i] = feet() - PERCH + 2;
                vx[i] = 0;
                vy[i] = 0;
                continue;
            }
            float dx, dy;
            if (task[i] >= 0) {
                Barn.Harvest h = tasks.get(task[i]);
                if (!carrying[i]) {
                    dx = FarmView.FIELD_X + h.column() * 16 + 8;
                    dy = FarmView.ROW_Y + h.row() * FarmView.ROW_STEP - 10;
                    if (Math.abs(bx[i] - dx) < 6 && Math.abs(by[i] - dy) < 6) {
                        carrying[i] = true;
                    }
                } else {
                    dx = roostX();
                    dy = feet() - PERCH + 4;
                    if (Math.abs(bx[i] - dx) < 8 && Math.abs(by[i] - dy) < 8) {
                        carrying[i] = false;
                        task[i] = nextTask < tasks.size() ? nextTask++ : -1;
                    }
                }
            } else {
                double a = angle[i] * Math.PI * 2 / 256;
                dx = roostX() + (float) Math.sin(a) * 32;
                dy = feet() - PERCH - 0x20 + (float) Math.cos(a) * 16;
            }
            vx[i] += toward(dx - bx[i], vx[i]);
            vy[i] += toward(dy - by[i], vy[i]);
            vy[i] = Math.max(-Y_CAP, Math.min(Y_CAP, vy[i]));
            bx[i] += vx[i];
            by[i] += vy[i];
        }
    }

    private boolean allHome() {
        for (int i = 0; i < BIRDS; i++) {
            if (task[i] >= 0) {
                return false;
            }
        }
        return true;
    }

    /** Obj_SuperTailsBirds_Move: $20 toward the target, four times that when heading away. */
    private static float toward(float distance, float velocity) {
        float accel = distance >= 0 ? ACCEL : -ACCEL;
        if (velocity != 0 && Math.signum(velocity) != Math.signum(accel)) {
            accel *= 4;
        }
        return accel;
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!BarnSystem.roostAt(shell.game, row, column)) {
            return;
        }
        SceneSpriteSet set = shell.art.flicky;
        if (set == null || set.frameCount() < 3) {
            return;
        }
        boolean resting = resting(shell.game) && allHome();
        for (int i = 0; i < BIRDS; i++) {
            float x = bx[i] - cx, y = by[i];
            if (resting) {
                SceneSprite pose = set.frame(2);
                canvas.draw(pose, x, y - (pose.height() - pose.originY()), tint.withFlipX(i % 2 == 1));
                continue;
            }
            SceneSprite pose = set.frame((int) ((ticks + i) / 2 % 2));
            canvas.draw(pose, x, y, tint.withFlipX(vx[i] < 0));
            if (carrying[i] && task[i] >= 0) {
                shell.art.icons.draw(canvas, shell.game.item(tasks.get(task[i]).item()), x - 8, y + 4, tint);
            }
        }
    }
}
