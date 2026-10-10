package threeislands.screen;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneLevelKit;
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
 * Three ROM-backed island vistas, linked by the party and a quiet emerald motif.
 * Background planes are cropped into windows, never repeated vertically.
 */
public final class TitleScreen implements Screen {
    private final Zone[] skyZones = {Zone.GREEN_HILL, Zone.EMERALD_HILL, Zone.ANGEL_ISLAND};
    private final String[] islandNames = {"SOUTH ISLAND", "WEST SIDE ISLAND", "ANGEL ISLAND"};
    private final int[] accents = {0xFF70D8C0, 0xFFFFD078, 0xFFAC98E8};
    private final SceneBackdrop[] skies = new SceneBackdrop[3];
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
            SceneLevelKit kit = game.art.kit(skyZones[loaded]);
            if (kit != null) skies[loaded] = kit.backdrop();
            loaded++;
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
        c.clear(0x080E20);
        for (int y = 0; y < h; y += 4) {
            c.fill(0, y, w, Math.min(4, h - y), Ui.mix(0xFF182C48, 0xFF080C1C, y / (double) h));
        }
        // Small, quiet points of light keep the frame alive without obscuring menu text.
        for (int i = 0; i < 32; i++) {
            int x = Math.floorMod(i * 83 + 17, w);
            int y = Math.floorMod(i * 37 + 7, 64);
            int alpha = 40 + (int) (30 * (1 + Math.sin(ticks * 0.025 + i)));
            c.fill(x, y, 1, 1, alpha << 24 | 0xB8D8FF);
        }
        String title = "THREE ISLANDS";
        int scale = w >= 360 ? 3 : 2;
        int tw = game.font.width(title) * scale;
        int titleX = (w - tw) / 2;
        game.font.draw(c, title, titleX + 2, 23, 0xFF030812, scale);
        game.font.draw(c, title, titleX, 20, 0xFFB07838, scale);
        game.font.draw(c, title, titleX, 18, 0xFFFFE5A0, scale);
        game.font.centered(c, "Three shores. One adventure.", w / 2, 51, 0xFFB8CCE0);
        int panelWidth = Math.min(116, (w - 40) / 3);
        int left = (w - (panelWidth * 3 + 12)) / 2;
        String[] games = {"s1", "s2", "s3k"};
        HeroId[] heroes = {HeroId.SONIC, HeroId.TAILS, HeroId.KNUCKLES};
        for (int i = 0; i < skies.length; i++) {
            int x = left + i * (panelWidth + 6);
            int centre = x + panelWidth / 2;
            c.fill(x - 1, 70, panelWidth + 2, 80, 0xFF030812);
            c.fill(x, 71, panelWidth, 78, accents[i]);
            c.fill(x + 1, 72, panelWidth - 2, 76, 0xFF182840);
            SceneBackdrop sky = skies[i];
            if (sky != null) {
                // ROM-plane crops: GHZ/EHZ hills at row 64, AIZ horizon at row 352.
                // The decoded planes can contain the next vertical repeat below these
                // windows. Only horizontal parallax is allowed to move this crop.
                int top = Math.max(0, Math.min(i == 2 ? 352 : 64, sky.image().height() - 76));
                c.drawBackdrop(sky, x + 1, 72, panelWidth - 2, 76, top, ticks * 0.25, ticks);
            }
            c.fill(x + 1, 72, panelWidth - 2, 14, 0xC0081020);
            game.font.centered(c, islandNames[i], centre, 75, accents[i]);
            // An opaque plinth grounds the party and closes the scenery above the menu.
            c.fill(x + 1, 137, panelWidth - 2, 11, 0xFF101C30);
            c.fill(x + 8, 137, panelWidth - 16, 1, accents[i]);
            if (game.art.has(games[i])) {
                String g = game.heroes.gameFor(games[i], heroes[i]);
                game.heroes.draw(c, g, heroes[i], Heroes.IDLE, centre, 138, i == 2, ticks,
                        SceneDraw.plain(), -1);
            }
        }
        // Seven small ROM emeralds echo the quest; their motion stays clear of the text.
        var emeralds = game.art.sprites("s3k:emeralds");
        if (emeralds != null) {
            for (int i = 0; i < 7; i++) {
                int x = w / 2 + (i - 3) * 13;
                int y = 8 + (int) Math.round(Math.sin(ticks * 0.035 + i * 0.6));
                c.draw(emeralds.frame(i), x, y, SceneDraw.plain().withScale(0.5f));
            }
        }
        if (confirmNew) {
            game.ui.window(c, w / 2 - 116, 157, 232, 47);
            game.font.centered(c, "Start over? Your save will be replaced.", w / 2, 166, Ui.TEXT);
            game.font.draw(c, "Yes", w / 2 - 40, 182, selected == 0 ? Ui.GOLD : Ui.DIM);
            game.font.draw(c, "No", w / 2 + 24, 182, selected == 1 ? Ui.GOLD : Ui.DIM);
            game.ui.cursor(c, (selected == 0 ? w / 2 - 40 : w / 2 + 24) - 3, 182, ticks);
        } else {
            c.fill(w / 2 - 63, 156, 126, 48, 0x90050B19);
            c.fill(w / 2 - 44, 156, 88, 1, 0x806C8CA8);
            List<String> options = options();
            for (int i = 0; i < options.size(); i++) {
                boolean enabled = i != 1 || save.isPresent();
                int y = 161 + i * 14;
                String label = options.get(i);
                int x = w / 2 - game.font.width(label) / 2;
                if (i == selected) c.fill(w / 2 - 56, y - 1, 112, 12, 0xFF233650);
                game.font.shadowed(c, label, x, y, !enabled ? 0xFF606880 : i == selected ? Ui.GOLD : Ui.TEXT);
                if (i == selected) game.ui.cursor(c, x - 4, y, ticks);
            }
        }
        List<String> notices = game.notices();
        for (int i = 0; i < notices.size(); i++) game.font.draw(c, notices.get(i), 6, 4 + i * 11, Ui.BAD);
        game.font.draw(c, "Enter / A: select", 6, h - 11, 0x90FFFFFF);
        String credit = "A Sonic RPG adventure";
        game.font.draw(c, credit, w - game.font.width(credit) - 6, h - 11, 0x90FFFFFF);
    }
}
