package threeislands.screen;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneLevelKit;
import java.util.List;
import threeislands.Game;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.Hero;
import threeislands.core.Zone;
import threeislands.view.Ui;

/** The credits roll over Angel Island's sky with the party running beneath. */
public final class EndingScreen implements Screen {
    private final List<String> credits = List.of(
            "THREE ISLANDS", "", "A story across three islands", "", "",
            "Starring", "Sonic the Hedgehog", "Miles \"Tails\" Prower", "Knuckles the Echidna", "", "",
            "Featuring", "South Island  (Sonic the Hedgehog)", "West Side Island  (Sonic the Hedgehog 2)",
            "Angel Island  (Sonic 3 & Knuckles)", "", "",
            "Every sprite, level and song", "came from your own game cartridges.", "", "",
            "Story, battles and code", "OpenGGF contributors", "", "",
            "Sonic the Hedgehog is a trademark of SEGA.", "This is an unofficial fan project.", "", "", "",
            "Thank you for playing!");
    private SceneBackdrop sky;
    private long ticks;
    private boolean loaded;

    public EndingScreen(Game game) {
    }

    @Override
    public String name() {
        return "ENDING";
    }

    private int length() {
        return 260 + credits.size() * 16;
    }

    @Override
    public void update(Game game) {
        ticks++;
        game.audio.music("s3k", Audio.MUS_ENDING);
        if (!loaded) {
            loaded = true;
            SceneLevelKit kit = game.art.kit(Zone.ANGEL_ISLAND);
            if (kit != null) sky = kit.backdrop();
        }
        if ((ticks > length() && game.controls.accept()) || (ticks > 120 && game.controls.menu())) game.title();
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        c.clear(0x0C1830);
        if (sky != null) c.drawBackdrop(sky, Math.max(0, Math.min(sky.image().height() - h, 64)), ticks * 1.5, ticks);
        c.fill(0, 0, w, h, 0x50000020);
        double scroll = ticks * 0.5;
        for (int i = 0; i < credits.size(); i++) {
            int y = (int) (h + i * 16 - scroll);
            if (y < -12 || y > h) continue;
            String line = credits.get(i);
            boolean heading = i == 0 || line.equals("Starring") || line.equals("Featuring")
                    || line.equals("Story, battles and code");
            game.font.centered(c, line, w / 2, y, heading ? Ui.GOLD : Ui.TEXT);
        }
        int x = 70;
        for (Hero hero : game.progress.party()) {
            game.heroes.draw(c, "s3k", hero.id, Heroes.RUN, x, h - 20, false, ticks, SceneDraw.plain(), -1);
            x -= 30;
        }
        if (ticks > length()) game.font.centered(c, "Press A", w / 2, h - 14, Ui.GOLD);
    }
}
