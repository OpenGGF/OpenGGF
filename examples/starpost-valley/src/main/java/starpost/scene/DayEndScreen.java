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
public final class DayEndScreen implements Screen {
    private static final int COUNT_TICKS = 6;

    private final boolean fainted;
    private final List<Item> items = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private int paid;
    private long started;
    private int phase;          // 0 tally, 1 title card
    private long phaseAt;

    public DayEndScreen(boolean fainted) {
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
        if (phase == 0) {
            long done = 60 + (items.size() + 1) * COUNT_TICKS * 4L;
            if (age == done) {
                shell.sfx(paid > 0 ? Sfx.PERFECT : Sfx.SWITCH);
            }
            if (age > done + 60 || skip && age > 20) {
                phase = 1;
                phaseAt = shell.ticks;
            }
        } else if (age > 150 || skip && age > 20) {
            shell.go(new PlayScreen(shell));
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        int w = canvas.width(), h = canvas.height();
        canvas.drawBackdrop(shell.art.season(game.calendar.season()).backdrop(2), 0, 0, w, h, 8, started * 0.5,
                shell.ticks);
        if (phase == 1) {
            drawTitleCard(shell, canvas, game);
            return;
        }
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
            int value = item.price() * counts.get(i);
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

    /** The morning's card, after Sonic 1's: the date on a blue band, the valley's name below. */
    private void drawTitleCard(Shell shell, SceneCanvas canvas, Game game) {
        int w = canvas.width();
        long age = age(shell);
        int slide = (int) Math.max(0, 200 - age * 12);
        canvas.fill(0, 0, w, canvas.height(), 0xFF000000);
        canvas.fill(w / 2 - 110 + slide, 78, 220, 22, 0xFF2449DB);
        canvas.fill(w / 2 - 110 + slide, 100, 220, 4, 0xFFFFDB00);
        String date = Calendar.seasonName(game.calendar.season()) + " " + game.calendar.day();
        Text.shadow(canvas, date, w / 2 - canvas.textWidth(date) / 2 + slide, 84, Text.WHITE);
        String name = game.farmName + " VALLEY";
        Text.shadow(canvas, name, w / 2 - canvas.textWidth(name) / 2 - slide, 112, Text.YELLOW);
        if (game.raining) {
            Text.centred(canvas, "RAIN TODAY", 136, Text.BLUE);
        }
    }
}
