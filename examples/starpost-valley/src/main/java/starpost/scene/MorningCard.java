package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.ui.Text;

/**
 * The morning's card, after Sonic 3 &amp; Knuckles': the red banner drops in on the left while the
 * season and the valley's name slide in from the right, then the day in Sonic 1's HUD digits.
 * Then the next screen (usually the day's {@link PlayScreen}).
 */
final class MorningCard implements Screen {
    private final java.util.function.Supplier<Screen> next;
    private long started;

    MorningCard(java.util.function.Supplier<Screen> next) {
        this.next = next;
    }

    @Override
    public void enter(Shell shell) {
        started = shell.ticks;
        shell.music.stop();
    }

    @Override
    public void update(Shell shell) {
        long age = shell.ticks - started;
        if (age > 150 || (shell.in.confirm || shell.in.act) && age > 20) {
            shell.go(next.get());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        int w = canvas.width();
        long age = shell.ticks - started;
        canvas.fill(0, 0, w, canvas.height(), 0xFF000000);
        int in = (int) Math.max(0, 320 - age * 16);
        if (shell.art.cardBanner != null) {
            canvas.draw(shell.art.cardBanner, w / 2f - 120, -in * 0.7f, SceneDraw.plain());
        }
        var font = shell.art.cardFont;
        String season = Calendar.seasonName(game.calendar.season());
        font.draw(canvas, season, w / 2f - 40 + in, 76, SceneDraw.plain());
        String name = game.farmName + " VALLEY";
        font.draw(canvas, name, w / 2f - 40 + in * 1.4f, 108, SceneDraw.plain());
        String day = Integer.toString(game.calendar.day());
        float dx = w / 2f - 40 + font.width(season) + 12 + in * 1.2f;
        shell.art.hud.number(canvas, day, dx, 68, 2);
        if (game.raining && age > 30) {
            Text.centred(canvas, "RAIN TODAY", 150, Text.BLUE);
        }
    }
}
