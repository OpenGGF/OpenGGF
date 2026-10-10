package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.List;
import java.util.function.Supplier;
import starpost.core.Skills;
import starpost.ui.Text;

/**
 * A skill's new level, announced at night: levels 5 and 10 ask the farmer to choose one of two
 * professions. Several level-ups are shown one after another, then the next screen.
 */
final class LevelUpScreen implements Screen {
    private final int skill;
    private final int level;
    private final Supplier<Screen> next;
    private int choice;
    private long opened;

    private LevelUpScreen(int skill, int level, Supplier<Screen> next) {
        this.skill = skill;
        this.level = level;
        this.next = next;
    }

    /** The pending level-ups as a chain of screens ending in {@code last}. */
    static Screen chain(Shell shell, Supplier<Screen> last) {
        Skills skills = shell.game.section(Skills.class);
        if (skills == null) {
            return last.get();
        }
        List<int[]> pending = skills.pendingLevels();
        Supplier<Screen> tail = last;
        for (int i = pending.size() - 1; i >= 0; i--) {
            int[] p = pending.get(i);
            Supplier<Screen> after = tail;
            tail = () -> new LevelUpScreen(p[0], p[1], after);
        }
        return tail.get();
    }

    private boolean choosing() {
        return level == 5 || level == 10;
    }

    @Override
    public void enter(Shell shell) {
        opened = shell.ticks;
        shell.sfx(Sfx.PERFECT);
    }

    @Override
    public void update(Shell shell) {
        Skills skills = shell.game.section(Skills.class);
        if (choosing() && (shell.in.leftPressed || shell.in.rightPressed || shell.in.upPressed || shell.in.downPressed)) {
            choice = 1 - choice;
            shell.sfx(Sfx.SWITCH);
        }
        if (shell.ticks - opened > 30 && shell.in.confirm) {
            String chosen = choosing() ? skills.choices(skill, level)[choice] : null;
            skills.announce(skill, level, chosen);
            shell.goNow(next.get());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width();
        canvas.fill(0, 0, w, canvas.height(), 0xFF000018);
        shell.art.cardFont.centred(canvas, Skills.name(skill), 40, SceneDraw.plain());
        Text.centred(canvas, "LEVEL " + level, 72, Text.YELLOW);
        if (!choosing()) {
            Text.centred(canvas, "YOU'RE GETTING THE HANG OF THIS.", 110, Text.WHITE);
            Text.centred(canvas, "CONFIRM", 180, Text.GREY);
            return;
        }
        Skills skills = shell.game.section(Skills.class);
        String[] options = skills.choices(skill, level);
        Text.centred(canvas, "CHOOSE A PROFESSION", 96, Text.WHITE);
        for (int i = 0; i < 2; i++) {
            int x = w / 2 - 170 + i * 175, y = 118;
            Text.panel(canvas, x, y, 165, 56);
            if (i == choice) {
                canvas.fill(x + 2, y + 2, 161, 52, 0x40FFDB00);
            }
            String name = options[i].replace('_', ' ').toUpperCase();
            Text.shadow(canvas, name, x + 82 - canvas.textWidth(name) / 2, y + 8, i == choice ? Text.YELLOW : Text.WHITE);
            List<String> lines = Text.wrap(canvas, Skills.describe(options[i]), 150);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                com.openggf.mods.ui.CompactFont.shadowed(canvas, lines.get(l), x + 8, y + 26 + l * 10, 1, 0xFFDBDBDB, 0xFF000000);
            }
        }
    }
}
