package starpost.museum;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import starpost.core.Game;
import starpost.core.Plot;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;

/**
 * A glinting spot in the ground (see {@link DigSpots}): a little mound with the S3K ring's sparkle
 * (Map_Ring frames 4-7) over it. The action button digs it for {@link DigSpots#COST} Momentum; on
 * the farm the dug plot is left tilled. The cap's spot holds the museum's Star Post Cap and a
 * coconut Hazel buried with it.
 */
final class DigSpot implements Actor {
    private final MuseumSystem sys;
    private final String key;
    private final int view;
    private final float x;
    private final float y;
    private final int row;
    private final int column;
    private final boolean cap;

    DigSpot(MuseumSystem sys, String key, int view, float x, float y, int row, int column, boolean cap) {
        this.sys = sys;
        this.key = key;
        this.view = view;
        this.x = x;
        this.y = y;
        this.row = row;
        this.column = column;
        this.cap = cap;
    }

    /** Whether it still glints: not dug today, its plot still open grass, the cap still buried. */
    boolean live(Game game) {
        if (sys.museum.dugToday.contains(key)) {
            return false;
        }
        if (cap) {
            return sys.museum.capBuried(game);
        }
        return view != FARM || MuseumSystem.diggable(game.farm.plot(row, column));
    }

    @Override
    public int view() {
        return view;
    }

    @Override
    public float x() {
        return x;
    }

    @Override
    public float y() {
        return y;
    }

    @Override
    public float reach() {
        return live(sys.shell.game) ? (view == FARM ? 10 : 18) : -1;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
    }

    @Override
    public boolean interact(Shell shell, PlayScreen play) {
        Game game = shell.game;
        if (!live(game)) {
            return false;
        }
        if (!game.spend(DigSpots.COST)) {
            shell.toast("OUT OF MOMENTUM - TAKE A LAP!");
            shell.sfx(Sfx.ERROR);
            return true;
        }
        if (cap) {
            if (!game.inventory.fits(game.item(MuseumContent.CAP), 1)) {
                game.restore(DigSpots.COST);
                shell.toast("NO ROOM IN YOUR MONITORS");
                shell.sfx(Sfx.ERROR);
                return true;
            }
            sys.museum.dugToday.add(key);
            game.inventory.add(game.item(MuseumContent.CAP), 1);
            game.inventory.add(game.item("palm_coconut"), 1);
            shell.toast("STAR POST CAP FOUND! AND A COCONUT");
            shell.sfx(Sfx.STARPOST);
            return true;
        }
        sys.museum.dugToday.add(key);
        if (view == FARM) {
            Plot plot = game.farm.plot(row, column);
            if (plot != null) {
                plot.tilled = true;
            }
        }
        String[] find = Finds.spot(game).split(":");
        int count = Integer.parseInt(find[1]);
        if (find[0].equals("rings")) {
            game.rings += count;
            shell.toast("BURIED RINGS! +" + count);
            shell.sfx(Sfx.RING);
        } else if (game.inventory.add(game.item(find[0]), count) < count) {
            game.xp(starpost.core.Skills.RANGING, 4);
            shell.toast("DUG UP: " + (count > 1 ? count + " " : "") + game.item(find[0]).name());
            shell.sfx(Sfx.GRAB);
        } else {
            sys.museum.dugToday.remove(key);
            game.restore(DigSpots.COST);
            shell.toast("NO ROOM IN YOUR MONITORS");
            shell.sfx(Sfx.ERROR);
        }
        return true;
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!live(shell.game)) {
            return;
        }
        int sx = Math.round(x - cx), sy = Math.round(y - cy);
        // A mound of turned earth (Green Hill's soil browns).
        canvas.fill(sx - 5, sy - 3, 10, 3, 0xFF924900);
        canvas.fill(sx - 3, sy - 4, 6, 1, 0xFFB66D24);
        canvas.fill(sx - 6, sy, 12, 1, 0xFF6D2400);
        var ring = shell.art.ring;
        if (ring != null && ring.frameCount() > 7 && (shell.ticks / 24) % 3 != 2) {
            canvas.draw(ring.frame(4 + (int) (shell.ticks / 6 % 4)), sx - 8, sy - 18, SceneDraw.plain());
        }
    }
}
