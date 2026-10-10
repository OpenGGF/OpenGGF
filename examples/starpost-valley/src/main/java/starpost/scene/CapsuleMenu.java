package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.ui.CompactFont;
import java.util.List;
import java.util.Map;
import starpost.core.Capsule;
import starpost.core.Game;
import starpost.ui.Text;

/**
 * Inside the Great Capsule: the chambers down the left, the chosen chamber's bundles on the
 * right. Confirm on a bundle gives everything the farmer carries toward it.
 */
final class CapsuleMenu implements Screen {
    private int chamber;
    private int bundle;
    private boolean inBundles;

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        if (shell.game.flags.contains("robo_member")) {
            if (shell.in.back || shell.in.confirm || shell.in.menu) {
                shell.pop();
            }
            return;
        }
        Capsule capsule = shell.game.section(Capsule.class);
        List<Capsule.Chamber> chambers = capsule.chambers(shell.catalog);
        if (shell.in.back && !inBundles || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        if (shell.in.back) {
            inBundles = false;
            return;
        }
        Capsule.Chamber current = chambers.get(chamber);
        if (!inBundles) {
            if (shell.in.downPressed) {
                chamber = (chamber + 1) % chambers.size();
                shell.sfx(Sfx.SWITCH);
            } else if (shell.in.upPressed) {
                chamber = (chamber + chambers.size() - 1) % chambers.size();
                shell.sfx(Sfx.SWITCH);
            } else if (shell.in.confirm || shell.in.rightPressed) {
                inBundles = true;
                bundle = 0;
            }
            return;
        }
        int n = current.bundles().size();
        if (shell.in.downPressed) {
            bundle = (bundle + 1) % n;
        } else if (shell.in.upPressed) {
            bundle = (bundle + n - 1) % n;
        } else if (shell.in.leftPressed) {
            inBundles = false;
        } else if (shell.in.confirm) {
            Capsule.Bundle b = current.bundles().get(bundle);
            boolean wasComplete = capsule.complete(current);
            int given = capsule.deliver(shell.game, current, b);
            if (given == 0) {
                shell.toast(capsule.complete(b) ? "THIS BUNDLE IS FULL" : "YOU HAVE NOTHING IT NEEDS");
                shell.sfx(Sfx.ERROR);
            } else if (!wasComplete && capsule.complete(current)) {
                shell.sfx(Sfx.PERFECT);
                shell.toast(current.name() + " RESTORED!");
            } else {
                shell.sfx(Sfx.RING);
            }
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        if (game.flags.contains("robo_member")) {
            Text.panel(canvas, 60, 80, canvas.width() - 120, 50);
            Text.centred(canvas, "THE CAPSULE IS SEALED.", 92, Text.RED);
            Text.centred(canvas, "A ROBOMART PADLOCK HANGS ON THE DOOR.", 108, Text.GREY);
            return;
        }
        Capsule capsule = game.section(Capsule.class);
        List<Capsule.Chamber> chambers = capsule.chambers(shell.catalog);
        int w = 360, h = 190, x = (canvas.width() - w) / 2, y = 16;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "THE GREAT CAPSULE", x + 10, y + 8, Text.YELLOW);
        for (int i = 0; i < chambers.size(); i++) {
            Capsule.Chamber c = chambers.get(i);
            int cy = y + 26 + i * 20;
            boolean done = capsule.complete(c);
            if (i == chamber) {
                canvas.fill(x + 6, cy - 3, 104, 18, inBundles ? 0x30B66D24 : 0x60B66D24);
            }
            Text.shadow(canvas, c.name(), x + 12, cy, done ? Text.GREEN : i == chamber ? Text.YELLOW : Text.WHITE);
            if (done) {
                Text.shadow(canvas, "*", x + 100, cy, Text.YELLOW);
            }
        }
        Capsule.Chamber c = chambers.get(chamber);
        int bx = x + 118;
        canvas.fill(bx - 4, y + 22, 1, h - 30, 0xFFB66D24);
        int by = y + 24;
        for (int i = 0; i < c.bundles().size(); i++) {
            Capsule.Bundle b = c.bundles().get(i);
            boolean done = capsule.complete(b);
            if (inBundles && i == bundle) {
                canvas.fill(bx, by - 2, w - 128, 38, 0x60B66D24);
            }
            Text.shadow(canvas, b.name(), bx + 4, by, done ? Text.GREEN : Text.WHITE);
            int ix = bx + 4;
            if (b.rings() > 0) {
                int have = capsule.delivered(b, "rings");
                CompactFont.shadowed(canvas, Math.min(have, b.rings()) + " / " + b.rings() + " RINGS", ix, by + 14, 1,
                        done ? 0xFF92FF49 : 0xFFDBDBDB, 0xFF000000);
            } else {
                for (Map.Entry<String, Integer> want : b.wants().entrySet()) {
                    int have = capsule.delivered(b, want.getKey());
                    SceneDraw style = have >= want.getValue() ? SceneDraw.plain() : SceneDraw.plain().withAlpha(0.45f);
                    shell.art.icons.draw(canvas, game.item(want.getKey()), ix, by + 12, style);
                    if (want.getValue() > 1) {
                        CompactFont.shadowed(canvas, have + "/" + want.getValue(), ix + 2, by + 29, 1, 0xFFFFFFFF, 0xFF000000);
                    }
                    ix += 22;
                }
            }
            by += 42;
        }
        String reward = capsule.complete(c) ? "RESTORED: " + c.rewardText() : "REWARD: " + c.rewardText();
        CompactFont.shadowed(canvas, reward, x + 10, y + h - 14, 1, 0xFFFFDB00, 0xFF000000);
    }
}
