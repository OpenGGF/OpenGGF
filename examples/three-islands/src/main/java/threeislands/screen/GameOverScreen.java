package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import threeislands.Game;
import threeislands.audio.Audio;
import threeislands.view.Ui;

/** The party fell: retry from the last save (a Starpost or a village) or return to the title. */
public final class GameOverScreen implements Screen {
    private int selected;
    private long ticks;

    public GameOverScreen(Game game) {
        game.audio.jingle(Audio.MUS_GAME_OVER);
    }

    @Override
    public String name() {
        return "GAME_OVER";
    }

    @Override
    public void update(Game game) {
        ticks++;
        if (ticks < 60) return;
        if (game.controls.up() || game.controls.down()) {
            selected = 1 - selected;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.accept()) {
            if (selected == 0) {
                var save = game.readSave();
                if (save.isPresent()) game.continueGame(save.get());
                else game.newGame();
            } else {
                game.title();
            }
        }
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        c.clear(0x000000);
        int drop = (int) Math.max(0, 60 - ticks * 2);
        int word = game.font.width("GAME") * 3;
        game.font.shadowed(c, "GAME", w / 2 - word - 6, 60 - drop, Ui.BAD, 3);
        game.font.shadowed(c, "OVER", w / 2 + 6, 60 + drop, Ui.BAD, 3);
        if (ticks < 60) return;
        List<String> options = List.of("Retry from last save", "Return to title");
        for (int i = 0; i < options.size(); i++) {
            int y = 130 + i * 14;
            String label = options.get(i);
            int x = w / 2 - game.font.width(label) / 2;
            game.font.draw(c, label, x, y, i == selected ? Ui.GOLD : Ui.TEXT);
            if (i == selected) game.ui.cursor(c, x - 4, y, ticks);
        }
    }
}
