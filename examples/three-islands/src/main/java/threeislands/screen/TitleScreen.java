package threeislands.screen;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneLevelKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import threeislands.Game;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.HeroId;
import threeislands.core.Progress;
import threeislands.core.Zone;
import threeislands.view.Ui;

/**
 * The title: the three islands' skies (Green Hill, Emerald Hill and Angel Island backgrounds
 * from their own ROMs) cross-fading behind each game's own hero.
 */
public final class TitleScreen implements Screen {
    private final Zone[] skyZones = {Zone.GREEN_HILL, Zone.EMERALD_HILL, Zone.ANGEL_ISLAND};
    private static final int SKY_TICKS = 360;

    private final List<SceneBackdrop> skies = new ArrayList<>();
    private int loaded;
    private int selected;
    private long ticks;
    private boolean confirmNew;
    private Optional<Progress> save;

    public TitleScreen(Game game) {
        save = game.readSave();
        selected = save.isPresent() ? 1 : 0;
    }

    @Override
    public String name() {
        return confirmNew ? "TITLE_CONFIRM" : "TITLE";
    }

    private List<String> options() {
        return List.of("New Game", "Continue", "Quit");
    }

    @Override
    public void update(Game game) {
        ticks++;
        game.audio.music("s3k", Audio.MUS_TITLE);
        if (loaded < skyZones.length && ticks > 1) {
            Zone zone = skyZones[loaded++];
            SceneLevelKit kit = game.art.kit(zone);
            if (kit != null && kit.backdrop() != null) skies.add(kit.backdrop());
        }
        if (confirmNew) {
            if (game.controls.left() || game.controls.right() || game.controls.up() || game.controls.down()) {
                selected = 1 - selected;
                game.audio.sfx(Audio.SFX_CURSOR);
            }
            if (game.controls.back()) {
                confirmNew = false;
                selected = 0;
            } else if (game.controls.accept()) {
                confirmNew = false;
                if (selected == 0) {
                    game.audio.sfx(Audio.SFX_REGISTER);
                    game.newGame();
                } else {
                    selected = 0;
                }
            }
            return;
        }
        int count = options().size();
        if (game.controls.up()) {
            selected = (selected + count - 1) % count;
            if (selected == 1 && save.isEmpty()) selected = 0;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.down()) {
            selected = (selected + 1) % count;
            if (selected == 1 && save.isEmpty()) selected = 2;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.accept() || game.controls.menu()) {
            switch (selected) {
                case 0 -> {
                    if (save.isPresent()) {
                        confirmNew = true;
                        selected = 1;
                    } else {
                        game.audio.sfx(Audio.SFX_REGISTER);
                        game.newGame();
                    }
                }
                case 1 -> save.ifPresent(progress -> {
                    game.audio.sfx(Audio.SFX_REGISTER);
                    game.continueGame(progress);
                });
                default -> {
                    game.audio.fadeOut();
                    game.ctx.exitToMasterTitle();
                }
            }
        }
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        c.clear(0x0C1830);
        if (!skies.isEmpty()) {
            int index = (int) (ticks / SKY_TICKS % skies.size());
            long phase = ticks % SKY_TICKS;
            SceneBackdrop sky = skies.get(index);
            int top = Math.max(0, Math.min(sky.image().height() - h, 64));
            c.drawBackdrop(sky, top, ticks * 0.6, ticks);
            if (phase > SKY_TICKS - 40 && skies.size() > 1) {
                c.fill(0, 0, w, h, (int) ((phase - (SKY_TICKS - 40)) * 255 / 40) << 24);
            } else if (phase < 20) {
                c.fill(0, 0, w, h, (int) ((20 - phase) * 255 / 20) << 24);
            }
        }
        c.fill(0, 0, w, h, 0x50000020);
        String title = "THREE ISLANDS";
        int scale = 3;
        int tw = game.font.width(title) * scale;
        game.font.draw(c, title, (w - tw) / 2 + 3, 27, 0xFF101040, scale);
        game.font.draw(c, title, (w - tw) / 2, 24, Ui.GOLD, scale);
        game.font.centered(c, "A turn-based adventure across three islands", w / 2, 56, Ui.TEXT);
        // Each island's hero in its own game's art.
        String[] games = {"s1", "s2", "s3k"};
        HeroId[] heroes = {HeroId.SONIC, HeroId.TAILS, HeroId.KNUCKLES};
        int floor = 142;
        c.fill(w / 2 - 92, floor, 184, 3, 0x60FFFFFF);
        for (int i = 0; i < 3; i++) {
            if (!game.art.has(games[i])) continue;
            String g = game.heroes.gameFor(games[i], heroes[i]);
            game.heroes.draw(c, g, heroes[i], Heroes.IDLE, w / 2 - 56 + i * 56, floor, i == 2, ticks,
                    SceneDraw.plain(), -1);
            game.font.centered(c, Game.name(games[i]).replace("Sonic 3 & Knuckles", "Sonic 3&K"), w / 2 - 56 + i * 56,
                    floor + 6, Ui.DIM);
        }
        if (confirmNew) {
            game.ui.window(c, w / 2 - 90, 160, 180, 40);
            game.font.centered(c, "Start over? Your save will be replaced.", w / 2, 166, Ui.TEXT);
            game.font.draw(c, "Yes", w / 2 - 40, 182, selected == 0 ? Ui.GOLD : Ui.DIM);
            game.font.draw(c, "No", w / 2 + 24, 182, selected == 1 ? Ui.GOLD : Ui.DIM);
            game.ui.cursor(c, (selected == 0 ? w / 2 - 40 : w / 2 + 24) - 3, 182, ticks);
        } else {
            List<String> options = options();
            for (int i = 0; i < options.size(); i++) {
                boolean enabled = i != 1 || save.isPresent();
                int y = 160 + i * 13;
                String label = options.get(i);
                int x = w / 2 - game.font.width(label) / 2;
                game.font.shadowed(c, label, x, y, !enabled ? 0xFF606880 : i == selected ? Ui.GOLD : Ui.TEXT);
                if (i == selected) game.ui.cursor(c, x - 4, y, ticks);
            }
        }
        List<String> notices = game.notices();
        for (int i = 0; i < notices.size(); i++) game.font.draw(c, notices.get(i), 6, 4 + i * 11, Ui.BAD);
        game.font.draw(c, "Enter/A: select", 6, h - 11, 0x90FFFFFF);
        String credit = "Art and music from your own ROMs";
        game.font.draw(c, credit, w - game.font.width(credit) - 6, h - 11, 0x90FFFFFF);
    }
}
