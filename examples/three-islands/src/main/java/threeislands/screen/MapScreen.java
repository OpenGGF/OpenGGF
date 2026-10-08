package threeislands.screen;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import threeislands.Game;
import threeislands.art.Font;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.EnemyKind;
import threeislands.core.Hero;
import threeislands.core.Island;
import threeislands.core.Progress;
import threeislands.core.Zone;
import threeislands.view.Ui;

/**
 * An island's map: the village and the island's zones as postcards cut from each act's own
 * level blocks, joined by a trail. The party leader runs between stops; A enters, Start opens
 * the party menu.
 */
public final class MapScreen implements Screen {
    private final Island island;
    private final List<Zone> zones;
    private final List<SceneImage> postcards = new ArrayList<>();
    private final List<Float> postcardScales = new ArrayList<>();
    private SceneBackdrop backdrop;
    private int loaded;
    private int selected;
    private double walkerX = -1;
    private long ticks;
    private String toast;
    private int toastTicks;

    public MapScreen(Game game) {
        this.island = game.progress.island();
        this.zones = Zone.of(island);
        // Start on the first zone still to clear.
        selected = 1;
        for (int i = 0; i < zones.size(); i++) {
            if (!game.progress.isCleared(zones.get(i))) {
                selected = i + 1;
                break;
            }
        }
    }

    @Override
    public String name() {
        return "MAP";
    }

    public Island island() {
        return island;
    }

    public int selected() {
        return selected;
    }

    private int stops() {
        return zones.size() + 1;
    }

    private int stopX(Game game, int index) {
        int w = game.width();
        int span = w - 120;
        return 60 + (stops() == 1 ? 0 : span * index / (stops() - 1));
    }

    private int stopY(int index) {
        return 70 + (index % 2 == 0 ? 0 : 14);
    }

    @Override
    public void update(Game game) {
        ticks++;
        game.audio.music("s3k", island.mapMusic);
        if (loadStep(game)) return;
        if (toastTicks > 0) toastTicks--;
        double target = stopX(game, selected);
        if (walkerX < 0) walkerX = target;
        walkerX += Math.max(-5, Math.min(5, target - walkerX));
        if (game.transitioning()) return;
        if (game.controls.left() && selected > 0) {
            selected--;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.right() && selected < stops() - 1) {
            selected++;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.menu()) {
            game.swap(new MenuScreen(game, this, true));
            return;
        }
        if (game.controls.accept() && Math.abs(walkerX - target) < 1) {
            if (selected == 0) {
                game.audio.sfx(Audio.SFX_REGISTER);
                game.swap(new VillageScreen(game, this));
                return;
            }
            Zone zone = zones.get(selected - 1);
            if (!game.progress.isOpen(zone)) {
                game.audio.sfx(Audio.SFX_ERROR);
                toast = "Clear " + zones.get(selected - 2).label + " first.";
                toastTicks = 120;
            } else {
                game.audio.sfx(Audio.SFX_WARP);
                game.enterZone(zone, 0);
            }
        }
    }

    /** Loads one more postcard; true while some are still pending. */
    public boolean loadStep(Game game) {
        if (loaded >= zones.size()) return false;
        loadPostcard(game, zones.get(loaded++));
        return true;
    }

    private void loadPostcard(Game game, Zone zone) {
        SceneLevelKit kit = game.art.kit(zone);
        if (kit == null) {
            postcards.add(null);
            postcardScales.add(1f);
            return;
        }
        if (backdrop == null) backdrop = kit.backdrop();
        int size = kit.blockSize();
        int column = zone.startX / size;
        int row = (zone.startY + 24) / size;
        int block = 0;
        // The block under the start position, or the nearest one with something in it.
        for (int d = 0; d < 6 && block == 0; d++) {
            block = kit.block(column + d, row);
            if (block == 0) block = kit.block(column + d, row + 1);
        }
        postcards.add(block == 0 ? null : kit.blockImage(block));
        postcardScales.add(64f / size);
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        c.clear(0x102040);
        if (backdrop != null) {
            int top = Math.max(0, Math.min(backdrop.image().height() - h, backdrop.bands().size() > 1 ? 128 : 48));
            c.drawBackdrop(backdrop, top, ticks * 0.35, ticks);
        }
        c.fill(0, 0, w, h, 0x60000018);
        Progress progress = game.progress;
        // Header.
        game.font.shadowed(c, island.label.toUpperCase(), 10, 8, Ui.GOLD, 2);
        game.font.shadowed(c, "Chapter " + (island.ordinal() + 1), 14 + game.font.width(island.label.toUpperCase()) * 2,
                14, Ui.TEXT);
        status(game, c, w);
        // Trail.
        for (int i = 0; i < stops() - 1; i++) {
            int x0 = stopX(game, i), x1 = stopX(game, i + 1);
            int y0 = stopY(i) + 24, y1 = stopY(i + 1) + 24;
            for (int s = 0; s <= 20; s++) {
                int x = x0 + (x1 - x0) * s / 20;
                int y = y0 + (y1 - y0) * s / 20;
                if (s % 2 == 0) c.fill(x - 1, y - 1, 3, 3, 0xC0FFFFFF);
            }
        }
        // Stops.
        for (int i = 0; i < stops(); i++) stop(game, c, i);
        // The leader runs between stops.
        Hero leader = progress.party().get(0);
        String g = game.heroes.gameFor(island.game, leader.id);
        double target = stopX(game, selected);
        int pose = Math.abs(walkerX - target) > 0.5 ? Heroes.RUN : Heroes.IDLE;
        int feet = stopY(selected) - 2;
        game.heroes.draw(c, g, leader.id, pose, walkerX, feet, walkerX > target, ticks, SceneDraw.plain(), -1);
        info(game, c, w, h);
        if (toastTicks > 0 && toast != null) {
            game.ui.window(c, w / 2 - 90, 142, 180, 18);
            game.font.centered(c, toast, w / 2, 147, Ui.TEXT);
        }
        if (loaded < zones.size()) game.font.centered(c, "Unfolding the map...", w / 2, h / 2, Ui.DIM);
    }

    private void stop(Game game, SceneCanvas c, int i) {
        int x = stopX(game, i);
        int y = stopY(i);
        boolean chosen = i == selected;
        int box = 68;
        int bx = x - box / 2;
        int by = y + 4;
        c.fill(bx - 2, by - 2, box + 4, 56 + 4, chosen ? 0xFFFFD860 : 0xFF203050);
        c.fill(bx, by, box, 56, 0xFF0A1428);
        if (i == 0) {
            village(game, c, x, by + 46);
            game.font.centered(c, "Village", x, by + 60, chosen ? Ui.GOLD : Ui.TEXT);
            return;
        }
        Zone zone = zones.get(i - 1);
        int index = i - 1;
        SceneImage card = index < postcards.size() ? postcards.get(index) : null;
        if (card != null) {
            float scale = postcardScales.get(index);
            c.clip(bx, by, box, 56);
            c.draw(card, (float) (bx + 2), (float) (by - 4), SceneDraw.plain().withScale(scale));
            c.unclip();
        }
        Progress progress = game.progress;
        boolean open = progress.isOpen(zone);
        boolean cleared = progress.isCleared(zone);
        if (!open) {
            c.fill(bx, by, box, 56, 0xB0000000);
            game.font.centered(c, "Locked", x, by + 24, Ui.DIM);
        }
        if (cleared) {
            game.font.shadowed(c, "Clear!", bx + 3, by + 3, Ui.GOOD);
            if (zone.emerald >= 0) emerald(game, c, bx + box - 8, by + 10, zone.emerald);
        }
        game.font.centered(c, zone.label, x, by + 60, chosen ? Ui.GOLD : Ui.TEXT);
    }

    private void village(Game game, SceneCanvas c, int x, int feet) {
        String[] folk = island.game.equals("s2") ? new String[] {"flicky", "pocky", "tocky"}
                : island.game.equals("s1") ? new String[] {"flicky", "pocky", "rocky"} : new String[] {"flicky", "pocky", "ricky"};
        for (int k = 0; k < folk.length; k++) {
            SceneSpriteSet set = game.art.sprites(island.game + ":" + folk[k]);
            if (set == null) continue;
            double hop = Math.abs(Math.sin((ticks + k * 20) / 12.0)) * 6;
            c.draw(set.frame((int) ((ticks / 8 + k) % 2)), x - 20 + k * 20, (float) (feet - 10 - hop), SceneDraw.plain());
        }
        c.fill(x - 30, feet, 60, 2, 0xFF50A040);
    }

    private void emerald(Game game, SceneCanvas c, int x, int y, int index) {
        SceneSpriteSet set = game.art.sprites("s3k:emeralds");
        if (set != null && index < set.frameCount()) c.draw(set.frame(index), x, y, SceneDraw.plain());
        else c.fill(x - 3, y - 3, 6, 6, 0xFF40FF80);
    }

    private void status(Game game, SceneCanvas c, int w) {
        Progress progress = game.progress;
        int x = w - 150;
        game.ui.window(c, x, 6, 144, 44);
        int ry = 11;
        for (Hero hero : progress.party()) {
            game.font.draw(c, hero.id.label.substring(0, 1) + " Lv" + hero.level(), x + 6, ry, Ui.TEXT);
            game.ui.bar(c, x + 52, ry + 3, 50, 4, hero.hp(), hero.maxHp(), 0);
            game.font.draw(c, hero.hp() + "", x + 106, ry, Ui.DIM);
            ry += 10;
        }
        game.font.draw(c, "~ " + progress.rings(), x + 6, 6 + 44 - 12, Ui.GOLD);
        for (int e = 0; e < 7; e++) {
            int ex = x + 60 + e * 11;
            int ey = 6 + 44 - 8;
            if ((progress.emeralds() & (1 << e)) != 0) emerald(game, c, ex, ey, e);
            else c.fill(ex - 2, ey - 2, 5, 5, 0xFF303850);
        }
    }

    private void info(Game game, SceneCanvas c, int w, int h) {
        int y = h - 58;
        game.ui.window(c, 6, y, w - 12, 52);
        Progress progress = game.progress;
        if (selected == 0) {
            game.font.shadowed(c, island.village, 14, y + 6, Ui.GOLD);
            game.ui.paragraph(c, "Rest to recover and save, buy monitors with your rings, and hear the latest news.",
                    14, y + 19, w - 40, Ui.TEXT, 2);
        } else {
            Zone zone = zones.get(selected - 1);
            game.font.shadowed(c, zone.label + " Zone", 14, y + 6, Ui.GOLD);
            String state = progress.isCleared(zone) ? "Cleared" : progress.isOpen(zone) ? "Open" : "Locked";
            game.font.draw(c, state + "   Suggested level " + zone.level, 120, y + 6, Ui.DIM);
            StringBuilder foes = new StringBuilder("Foes: ");
            List<EnemyKind> kinds = zone.enemyKinds();
            for (int i = 0; i < kinds.size(); i++) foes.append(i > 0 ? ", " : "").append(kinds.get(i).label);
            game.ui.paragraph(c, foes.toString(), 14, y + 19, w - 40, Ui.TEXT, 2);
        }
        String hint = "Left/Right: move   A: enter   Start/M: menu";
        game.font.draw(c, hint, 14, y + 52 - Font.LINE - 1, 0xB0A0B0D0);
    }
}
