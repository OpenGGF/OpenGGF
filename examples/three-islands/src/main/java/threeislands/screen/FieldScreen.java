package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.List;
import threeislands.Game;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.EnemyKind;
import threeislands.core.Hero;
import threeislands.core.Zone;
import threeislands.field.Field;
import threeislands.field.Stage;
import threeislands.view.Ui;

/**
 * Exploring a zone: the party walks the act's real terrain left and right (hold B to run).
 * Badniks wait on the path and a battle starts on contact; monitors break open into items;
 * Starposts heal and save; the zone boss bars the way at the end.
 */
public final class FieldScreen implements Screen {
    public static final int ANCHOR_X = 150;
    public static final int ANCHOR_Y = 140;

    private final Stage stage;
    private final Field field;
    private final Zone zone;
    private long ticks;
    private String toast;
    private int toastTicks;
    private boolean confirmLeave;
    private boolean snapped;
    private int brokenMonitorTicks;

    public FieldScreen(Game game, Stage stage, Field field) {
        this.stage = stage;
        this.field = field;
        this.zone = stage.zone;
    }

    @Override
    public String name() {
        return confirmLeave ? "FIELD_LEAVE" : "FIELD";
    }

    public Field field() { return field; }
    public Stage stage() { return stage; }
    public Zone zone() { return zone; }

    @Override
    public void update(Game game) {
        ticks++;
        game.progress.tick();
        game.audio.music(zone.game, zone.music, zone.island().mapMusic);
        if (toastTicks > 0) toastTicks--;
        if (brokenMonitorTicks > 0) brokenMonitorTicks--;
        follow(game);
        if (game.transitioning()) return;
        if (confirmLeave) {
            if (game.controls.accept()) {
                game.progress.setResume(null, 0);
                game.go(new MapScreen(game));
            } else if (game.controls.back() || game.controls.horizontal() > 0) {
                confirmLeave = false;
            }
            return;
        }
        if (game.controls.menu()) {
            game.swap(new MenuScreen(game, this, false));
            return;
        }
        int direction = game.controls.horizontal();
        if (direction < 0 && field.atStart()) {
            confirmLeave = true;
            return;
        }
        Field.Spot spot = field.step(direction, game.controls.holdRun(), ticks);
        if (spot != null) touch(game, spot);
    }

    private void follow(Game game) {
        double x = field.x();
        stage.look(x, stage.path.heightAt(x), ANCHOR_X, ANCHOR_Y, game.width(), game.height(), snapped ? 0.12 : 1);
        snapped = true;
    }

    private void touch(Game game, Field.Spot spot) {
        switch (spot.kind) {
            case ENCOUNTER -> {
                game.audio.sfx(Audio.SFX_DASH);
                game.battle(this, spot, spot.group);
            }
            case MONITOR -> {
                spot.done = true;
                brokenMonitorTicks = 30;
                game.audio.sfx(Audio.SFX_BREAK);
                if (spot.item != null) {
                    game.progress.addItem(spot.item, 1);
                    say("Got a " + spot.item.label + "!");
                } else {
                    game.progress.addRings(spot.rings);
                    game.audio.sfx(Audio.SFX_RING);
                    say("Got " + spot.rings + " rings!");
                }
            }
            case STARPOST -> {
                spot.done = true;
                game.progress.restAll();
                game.progress.setResume(zone, (int) spot.homeX);
                game.save();
                game.audio.sfx(Audio.SFX_STARPOST);
                say("Starpost! The party is restored and the game is saved.");
            }
            case MIDBOSS -> game.playStory(zone.key + "-mid", this, () -> game.battle(this, spot, spot.group));
            case BOSS -> game.playStory(zone.key + "-boss", this, () -> game.battle(this, spot, spot.group));
            default -> { }
        }
    }

    private void say(String text) {
        toast = text;
        toastTicks = 150;
    }

    /** Called by the battle when it ends in victory or escape. */
    public void afterBattle(Game game, Field.Spot spot, boolean won) {
        if (won) {
            spot.done = true;
            if (spot.kind == Field.Kind.BOSS) {
                game.zoneCleared(zone, this);
                return;
            }
        } else {
            field.retreat(spot);
        }
        snapped = false;
        game.swap(this);
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        stage.draw(c, ticks);
        drawSpots(game, c);
        drawParty(game, c);
        hud(game, c);
    }

    private void drawSpots(Game game, SceneCanvas c) {
        int w = c.width();
        for (Field.Spot spot : field.spots) {
            double x = spot.x(ticks);
            int sx = stage.sx(x);
            if (sx < -80 || sx > w + 80) continue;
            int floor = stage.sy(stage.path.floorAt(x));
            switch (spot.kind) {
                case ENCOUNTER -> {
                    if (spot.done) continue;
                    EnemyKind lead = spot.group.get(0);
                    game.enemies.draw(c, lead, sx, floor, ticks, SceneDraw.plain());
                    if (spot.group.size() > 1) {
                        game.font.shadowed(c, "x" + spot.group.size(), sx + 10, floor - 44, Ui.GOLD);
                    }
                }
                case MONITOR -> {
                    SceneSpriteSet set = game.art.sprites("s3k:monitor");
                    if (set == null) continue;
                    int frame = spot.done ? 11 : spot.item == null ? 3 : Math.max(0, spot.item.icon);
                    if (!spot.done && ticks / 4 % 8 == 0) frame = 0;
                    c.draw(set.frame(frame), sx, floor - 15, SceneDraw.plain());
                }
                case STARPOST -> {
                    SceneSpriteSet set = game.art.sprites("s3k:starpost");
                    if (set == null) continue;
                    c.draw(set.frame(0), sx, floor - 0x20, SceneDraw.plain());
                    // Map_StarPost: the lamp (1 unlit blue, 2 lit red) sits $1C above the post.
                    c.draw(set.frame(spot.done ? 2 : 1), sx, floor - 0x20 - 0x1C + 4, SceneDraw.plain());
                }
                case MIDBOSS, BOSS -> {
                    if (spot.done) continue;
                    EnemyKind boss = spot.group.get(0);
                    game.enemies.draw(c, boss, sx, floor, ticks, SceneDraw.plain());
                    if (Math.abs(field.x() - x) < 220 && ticks / 20 % 2 == 0) {
                        game.font.shadowed(c, "!", sx - 2, floor - 90, Ui.BAD, 2);
                    }
                }
                default -> { }
            }
        }
    }

    private void drawParty(Game game, SceneCanvas c) {
        List<Hero> party = game.progress.party();
        for (int i = party.size() - 1; i >= 0; i--) {
            Hero hero = party.get(i);
            double x = i == 0 ? field.x() : field.followerX(i);
            boolean air = stage.path.airborneAt(x);
            int pose;
            if (air) pose = Heroes.ROLL;
            else if (!field.moving() && (i == 0 || Math.abs(x - field.x()) < Field.FOLLOW_GAP * i + 4)) pose = Heroes.IDLE;
            else pose = field.running() ? Heroes.RUN : Heroes.WALK;
            if (i > 0 && field.moving() && Math.abs(field.followerX(i) - field.followerX(i - 1)) < 0.5 && !air) {
                pose = Heroes.IDLE;
            }
            String g = game.heroes.gameFor(zone.game, hero.id);
            game.heroes.draw(c, g, hero.id, pose, stage.sx(x), stage.sy(stage.path.heightAt(x)), field.facingLeft(),
                    ticks + i * 7, SceneDraw.plain(), -1);
        }
    }

    private void hud(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        game.font.shadowed(c, zone.label.toUpperCase() + " ZONE", 8, 6, Ui.GOLD);
        game.font.shadowed(c, "~ " + game.progress.rings(), 8, 18, Ui.GOLD);
        List<Hero> party = game.progress.party();
        int x = w - 8 - party.size() * 62;
        for (int i = 0; i < party.size(); i++) {
            Hero hero = party.get(i);
            int px = x + i * 62;
            game.font.shadowed(c, hero.id.label.substring(0, Math.min(5, hero.id.label.length())), px, 6, Ui.TEXT);
            game.font.shadowed(c, "" + hero.level(), px + 46, 6, Ui.DIM);
            game.ui.bar(c, px, 17, 56, 3, hero.hp(), hero.maxHp(), 0);
            game.ui.bar(c, px, 21, 56, 2, hero.ep(), hero.maxEp(), Ui.EP);
        }
        // Progress through the act.
        double done = (field.x() - stage.path.startX()) / Math.max(1.0, stage.path.endX() - stage.path.startX());
        c.fill(8, h - 8, w - 16, 2, 0x60FFFFFF);
        for (Field.Spot spot : field.spots) {
            if (spot.done) continue;
            double f = (spot.homeX - stage.path.startX()) / Math.max(1.0, stage.path.endX() - stage.path.startX());
            int colour = switch (spot.kind) {
                case BOSS, MIDBOSS -> Ui.BAD;
                case STARPOST -> 0xFF60A0FF;
                case MONITOR -> Ui.GOOD;
                default -> 0xFFFF9040;
            };
            c.fill(8 + (int) ((w - 16) * f) - 1, h - 9, 2, 4, colour);
        }
        c.fill(8 + (int) ((w - 16) * done) - 2, h - 11, 4, 8, Ui.GOLD);
        if (toastTicks > 0 && toast != null) {
            int tw = game.font.width(toast) + 16;
            game.ui.window(c, (w - tw) / 2, 34, tw, 20);
            game.font.draw(c, toast, (w - tw) / 2 + 8, 40, Ui.TEXT);
        }
        if (confirmLeave) {
            game.ui.window(c, w / 2 - 100, 70, 200, 38);
            game.font.centered(c, "Return to the island map?", w / 2, 76, Ui.TEXT);
            game.font.centered(c, "A: leave    B: stay", w / 2, 91, Ui.GOLD);
        } else if (ticks < 360) {
            game.font.shadowed(c, "Left/Right: walk   Hold B/Shift: run   Start/M: menu", 8, h - 22, 0xD0FFFFFF);
        }
    }
}
