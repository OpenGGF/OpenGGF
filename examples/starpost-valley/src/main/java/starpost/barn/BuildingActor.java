package starpost.barn;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.Map;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The Cucky Coop or the Pocky Pen on the farm's back wall, once built. Its sign is the animal it
 * houses (Sonic 1's own sprite); what its animals gave waits by the door. Up or the action button
 * at the door opens its menu.
 */
final class BuildingActor implements Actor {
    /** Screen row the facades stand on: the field's top edge. */
    private static final int GROUND = FarmView.FIELD_TOP + 2;

    final BarnSystem sys;
    final int home;

    BuildingActor(BarnSystem sys, int home) {
        this.sys = sys;
        this.home = home;
    }

    private boolean built() {
        return sys.barn.level(home) > 0;
    }

    private int doorX() {
        return home == Animals.COOP ? BarnSystem.COOP_X + 18 : BarnSystem.PEN_X;
    }

    @Override
    public int view() {
        return FARM;
    }

    @Override
    public float x() {
        return doorX();
    }

    /** Just behind the field's back edge: only the very back of the field reaches the door. */
    @Override
    public float y() {
        return FarmView.FIELD_TOP - 5;
    }

    @Override
    public float reach() {
        return built() ? 18 : -1;
    }

    private boolean atDoor(PlayScreen play) {
        FarmView farm = play.farm();
        return play.onFarm() && Math.abs(farm.runner.x - doorX()) < 18 && farm.runner.depth < 6;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
        if (built() && atDoor(play) && shell.in.upPressed && !play.folding()) {
            open(shell);
        }
    }

    @Override
    public boolean interact(Shell shell, PlayScreen play) {
        if (!built()) {
            return false;
        }
        open(shell);
        return true;
    }

    private void open(Shell shell) {
        shell.sfx(Sfx.DOOR_OPEN);
        shell.push(new BarnMenu(sys, home));
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!built()) {
            return;
        }
        float x = (home == Animals.COOP ? BarnSystem.COOP_X : BarnSystem.PEN_X) - cx;
        boolean big = sys.barn.level(home) == 2;
        SceneImage picture = home == Animals.COOP ? big ? sys.art.coopBig : sys.art.coop : big ? sys.art.penBig : sys.art.pen;
        if (home == Animals.PEN) {
            canvas.draw(sys.art.penFence, x - sys.art.penFence.width() / 2f, GROUND + 4 - sys.art.penFence.height(), tint);
        }
        canvas.draw(picture, x - picture.width() / 2f, GROUND - picture.height(), tint);
        drawSign(shell, canvas, x, GROUND - picture.height() + 4, tint);
        drawGoods(shell, canvas, doorX() - cx, GROUND + 2);
        if (shell.screen() instanceof PlayScreen play && atDoor(play)) {
            String label = "UP: " + (home == Animals.COOP ? (big ? "BIG COOP" : "CUCKY COOP") : big ? "BIG PEN" : "POCKY PEN");
            int w = canvas.textWidth(label);
            Text.shadow(canvas, label, Math.round(doorX() - cx) - w / 2, Math.round(play.farm().feetY()) - 58, Text.YELLOW);
        }
    }

    /** A plank sign with the house's animal on it: Sonic 1's Cucky or Pocky standing (Map_Animal frame 2). */
    private void drawSign(Shell shell, SceneCanvas canvas, float x, float y, SceneDraw tint) {
        SceneSpriteSet set = shell.art.animal(home == Animals.COOP ? "cucky" : "pocky");
        canvas.fill(Math.round(x) - 13, Math.round(y) - 26, 26, 28, 0xFF240000);
        canvas.fill(Math.round(x) - 12, Math.round(y) - 25, 24, 26, 0xFF924900);
        canvas.fill(Math.round(x) - 12, Math.round(y) - 25, 24, 1, 0xFFDB9249);
        if (set != null && set.frameCount() > 2) {
            SceneSprite pose = set.frame(2);
            canvas.draw(pose, x, y - 12, tint);
        }
    }

    /** What is waiting by the door: a few of the goods themselves. */
    private void drawGoods(Shell shell, SceneCanvas canvas, float x, float y) {
        Map<String, Integer> goods = sys.barn.goods(home);
        int shown = 0;
        for (Map.Entry<String, Integer> e : goods.entrySet()) {
            for (int i = 0; i < Math.min(3, e.getValue()) && shown < 4; i++, shown++) {
                shell.art.icons.draw(canvas, shell.game.item(e.getKey()), x - 22 - shown * 9, y - 12, SceneDraw.plain());
            }
        }
    }
}
