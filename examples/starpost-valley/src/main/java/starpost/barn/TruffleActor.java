package starpost.barn;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;

/** A Hill Truffle a Picky dug up, poking out of the grass until the farmer picks it. */
final class TruffleActor implements Actor {
    private final BarnSystem sys;
    private final int row;
    private final int column;

    TruffleActor(BarnSystem sys, int row, int column) {
        this.sys = sys;
        this.row = row;
        this.column = column;
    }

    private boolean here() {
        return sys.barn.truffles.contains(row + "." + column);
    }

    @Override
    public int view() {
        return FARM;
    }

    @Override
    public float x() {
        return FarmView.FIELD_X + column * 16 + 8;
    }

    @Override
    public float y() {
        return FarmView.ROW_Y + row * FarmView.ROW_STEP - 1;
    }

    @Override
    public float reach() {
        return here() ? 9 : -1;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
    }

    @Override
    public boolean interact(Shell shell, PlayScreen play) {
        if (!here()) {
            return false;
        }
        if (sys.barn.pickTruffle(shell.game, row, column)) {
            shell.toast("A HILL TRUFFLE!");
            shell.sfx(Sfx.GRAB);
        } else {
            shell.toast("NO ROOM FOR THE TRUFFLE");
            shell.sfx(Sfx.ERROR);
        }
        return true;
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!here()) {
            return;
        }
        float x = x() - cx, feet = y();
        shell.art.icons.draw(canvas, shell.game.item("hill_truffle"), x - 8, feet - 10, tint);
        canvas.fill(Math.round(x) - 6, Math.round(feet) - 2, 12, 2, 0xFF006D00);
        if ((shell.ticks + column * 31) % 120 < 24) {
            sys.sparkle(canvas, x + 5, feet - 10);
        }
    }
}
