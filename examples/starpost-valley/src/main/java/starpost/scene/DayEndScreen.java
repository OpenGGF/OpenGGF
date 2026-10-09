package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.core.Item;
import starpost.ui.Text;

/**
 * The night: the shipping signpost spins like the end of an act and the day's tally counts up
 * (each shipped item, then the total), then the next morning's title card. The game saves here.
 */
final class DayEndScreen implements Screen {
    private static final int COUNT_TICKS = 6;

    private final boolean fainted;
    private final List<Item> items = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private int paid;
    private long started;
    private long phaseAt;

    DayEndScreen(boolean fainted) {
        this.fainted = fainted;
    }

    @Override
    public void enter(Shell shell) {
        Game game = shell.game;
        for (Map.Entry<String, Integer> e : game.shipping.entrySet()) {
            items.add(game.item(e.getKey()));
            counts.add(e.getValue());
        }
        paid = game.sleep(fainted);
        shell.save();
        started = shell.ticks;
        phaseAt = shell.ticks;
        shell.music.stop();
        shell.sfx(Sfx.SIGNPOST);
    }

    private long age(Shell shell) {
        return shell.ticks - phaseAt;
    }

    @Override
    public void update(Shell shell) {
        long age = age(shell);
        boolean skip = shell.in.confirm || shell.in.act;
        long done = 60 + (items.size() + 1) * COUNT_TICKS * 4L;
        if (age == done) {
            shell.sfx(paid > 0 ? Sfx.PERFECT : Sfx.SWITCH);
        }
        if (age > done + 60 || skip && age > 20) {
            java.util.function.Supplier<Screen> morning = () -> new MorningCard(() -> new PlayScreen(shell));
            java.util.function.Supplier<Screen> afterLevels = YearEndScreen.due(shell.game)
                    ? () -> new YearEndScreen(morning) : morning;
            shell.go(LevelUpScreen.chain(shell, afterLevels));
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        int w = canvas.width(), h = canvas.height();
        canvas.drawBackdrop(shell.art.season(game.calendar.season()).backdrop(2), 0, 0, w, h, 8, started * 0.5,
                shell.ticks);
        canvas.fill(0, 0, w, h, 0x60000010);
        long age = age(shell);
        // The signpost spins, slowing to a stop on Sonic's face (Map_Sign frames 0-3).
        int frame = age < 60 ? (int) (age / Math.max(1, 2 + age / 12) % 4) : 3;
        if (frame < shell.art.signpost.frameCount()) {
            SceneSprite sign = shell.art.signpost.frame(frame);
            canvas.draw(sign, w / 2f, 64, SceneDraw.plain().withScale(1));
        }
        String head = fainted ? game.farmer.toUpperCase() + " PASSED OUT" : "DAY COMPLETE";
        Text.centred(canvas, head, 100, Text.YELLOW);
        int y = 118;
        int total = 0;
        for (int i = 0; i < items.size() && y < h - 30; i++) {
            long shown = age - 60 - i * COUNT_TICKS * 4L;
            if (shown < 0) {
                break;
            }
            Item item = items.get(i);
            int value = game.sellPrice(item) * counts.get(i);
            total += value;
            Text.shadow(canvas, item.name() + " X" + counts.get(i), w / 2 - 120, y, Text.WHITE);
            Text.right(canvas, Integer.toString(value), w / 2 + 120, y, Text.WHITE);
            y += 12;
        }
        if (!items.isEmpty() && age > 60 + items.size() * COUNT_TICKS * 4L) {
            Text.shadow(canvas, "TOTAL", w / 2 - 120, y + 6, Text.YELLOW);
            Text.right(canvas, paid + " RINGS", w / 2 + 120, y + 6, Text.YELLOW);
        }
        if (items.isEmpty() && age > 60) {
            Text.centred(canvas, "NOTHING SHIPPED TODAY", 118, Text.GREY);
        }
    }
}
