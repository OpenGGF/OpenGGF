package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import threeislands.Game;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.Hero;
import threeislands.core.HeroId;
import threeislands.core.Story;
import threeislands.core.Zone;
import threeislands.field.Field;
import threeislands.field.Stage;
import threeislands.view.Ui;

/** Free exploration, visible encounters and deliberate conversations in a small connected area. */
public final class FieldScreen implements Screen {
    public static final int ANCHOR_X = 200, ANCHOR_Y = 120;
    private final Stage stage;
    private final Field field;
    private final Zone zone;
    private long ticks;
    private String toast;
    private int toastTicks;
    private boolean entranceQueued;
    private int exitCooldown;
    private double cameraX, cameraY;

    public FieldScreen(Game game, Stage stage, Field field) {
        this.stage = stage;
        this.field = field;
        zone = stage.zone;
        entranceQueued = game.progress.seen(zone.key + "-enter") && game.progress.seen(zone.island().key() + "-arrive");
        field.restore(game.progress);
        camera(game);
    }
    public String name() { return "FIELD"; }
    public Field field() { return field; }
    public Stage stage() { return stage; }
    public Zone zone() { return zone; }

    public void update(Game game) {
        ticks++;
        game.progress.tick();
        game.audio.music(zone.game, zone.music, zone.island().mapMusic);
        if (toastTicks > 0) toastTicks--;
        if (game.transitioning()) return;
        if (game.controls.menu()) { game.swap(new MenuScreen(game, this, false)); return; }
        int dx = game.controls.horizontal();
        if (exitCooldown > 0) exitCooldown--;
        if (exitCooldown == 0 && ((dx < 0 && field.atStart())
                || (dx > 0 && field.x() >= 896 && Math.abs(field.y() - 336) < 44))) {
            if (!game.travel(this, dx > 0)) {
                say(dx > 0 ? "The passage is sealed. The island's anchors must be freed first." : "The trail ends here. Your friends are up ahead.");
                exitCooldown = 90;
            }
            return;
        }
        Field.Spot contact = field.step(dx, game.controls.vertical(), game.controls.holdRun(), ticks);
        camera(game);
        if (!entranceQueued && ticks > 20 && field.distance() > 24) {
            entranceQueued = true;
            game.fieldEntrance(this);
            return;
        }
        if (contact != null) { touch(game, contact); return; }
        if (game.controls.accept()) {
            Field.Spot nearby = field.nearby(ticks);
            if (nearby != null) touch(game, nearby);
        }
    }
    private void camera(Game game) {
        cameraX = Math.max(0, Math.min(Field.WIDTH - game.width(), field.x() - game.width() / 2.0));
        cameraY = Math.max(0, Math.min(Field.HEIGHT - game.height(), field.y() - game.height() / 2.0 - 16));
    }
    public int sx(double x) { return (int) Math.round(x - cameraX); }
    public int sy(double y) { return (int) Math.round(y - cameraY); }

    public double worldX(int screenX) { return screenX + cameraX; }
    public double worldY(int screenY) { return screenY + cameraY; }

    private void touch(Game game, Field.Spot spot) {
        switch (spot.kind) {
            case MERCHANT -> game.swap(new ShopScreen(game, this));
            case ENCOUNTER -> game.battle(this, spot, spot.group);
            case MONITOR -> {
                field.complete(game.progress, spot);
                game.progress.addItem(spot.item, 2);
                game.audio.sfx(Audio.SFX_BREAK);
                say("Found 2 " + spot.item.label + "!");
            }
            case STARPOST -> {
                field.complete(game.progress, spot);
                game.progress.restAll();
                game.progress.setResume(zone, spot.id.equals("sanctuary") ? 2 : 1);
                game.save();
                game.audio.sfx(Audio.SFX_STARPOST);
                game.playStory(zone.key + "-camp", this, () -> {
                    game.save();
                    say("Party restored. Journey saved.");
                    game.swap(this);
                });
            }
            case FRIEND -> {
                boolean helped = !spot.done;
                field.complete(game.progress, spot);
                if (helped) game.progress.addItem(threeislands.core.Item.SUPER_RING, 2);
                Runnable returnToField = () -> {
                    if (helped) say("Received 2 Super Rings. Check your journal in the menu.");
                    game.swap(this);
                };
                if (field.discoveries() > 0) {
                    game.swap(new StoryScreen(game, List.of(new Story.Line(null,
                            "The traveller listens as you describe what you found. Word of your discoveries is spreading back to camp."),
                            new Story.Line(null, field.objective())), this, returnToField));
                } else game.replayStory(zone.key + "-friend", this, returnToField);
            }
            case DISCOVERY -> game.replayStory(zone.key + "-" + spot.id, this, () -> {
                field.complete(game.progress, spot);
                // Discoveries carry progression so avoiding optional battles never requires grinding.
                for (Hero hero : game.progress.party()) {
                    int target = zone.level + (field.discoveries() == 2 ? 1 : 0);
                    if (hero.level() < target) hero.setLevel(target);
                }
                game.progress.restAll();
                game.progress.setResume(zone, spot.id.equals("signal") ? 2 : 1);
                game.save();
                say("Journal updated. The party is restored.");
                game.swap(this);
            });
            case MIDBOSS -> {
                game.playStory(zone.key + "-mid", this, () -> game.battle(this, spot, spot.group));
            }
            case BOSS -> {
                if (!field.bossReady()) { say(field.objective()); return; }
                if (!game.chapterBossReady(zone)) {
                    say("The island's other anchors still power this shield. Explore the earlier trails first.");
                    return;
                }
                game.playStory(zone.key + "-boss", this, () -> game.battle(this, spot, spot.group));
            }
        }
    }
    private void say(String text) { toast = text; toastTicks = 210; }
    public void afterBattle(Game game, Field.Spot spot, boolean won) {
        if (won) {
            field.complete(game.progress, spot);
            if (spot.kind == Field.Kind.BOSS) { game.zoneCleared(zone, this); return; }
            say("Caught your breath: recovered HP and EP.");
        } else field.retreat(spot);
        game.swap(this);
    }

    public void draw(Game game, SceneCanvas c) {
        drawWorld(game, c, null, true);
        if (game.screen() == this) hud(game, c);
    }

    /** The battle and dialogue keep this exact world and camera underneath their actors/UI. */
    public void drawWorld(Game game, SceneCanvas c, Field.Spot hidden, boolean showParty) {
        stage.fieldArt.draw(c, field, cameraX, cameraY, game.ticks());
        if (hidden == null) {
            Zone next = null;
            for (Zone candidate : Zone.values()) if (candidate.ordinal() > zone.ordinal() && game.art.has(candidate.game)) { next = candidate; break; }
            if (next != null) {
                String label = game.canTravelForward(zone) ? "To " + next.label + " ->" : "Rift-sealed trail";
                game.font.shadowed(c, label, sx(820), sy(304), Ui.GOLD);
                if (next.island() != zone.island() && game.canTravelForward(zone)) {
                    SceneSpriteSet ring = game.art.sprites("s3k:ring");
                    if (ring != null) c.draw(ring.frame((int) (game.ticks() / 8 % 4)), sx(896), sy(328), SceneDraw.plain().withScale(3));
                }
            }
        }
        // World objects and followers share a depth order; feet are the sorting point.
        List<double[]> order = new ArrayList<>();
        for (int i = 0; i < field.spots.size(); i++) if (field.spots.get(i) != hidden) order.add(new double[] {field.spots.get(i).homeY, i});
        List<Hero> party = game.progress.party();
        for (int i = 0; showParty && i < party.size(); i++) order.add(new double[] {i == 0 ? field.y() : field.followerY(i), -1 - i});
        order.sort((a, b) -> Double.compare(a[0], b[0]));
        for (double[] entry : order) {
            int i = (int) entry[1];
            if (i >= 0) drawSpot(game, c, field.spots.get(i));
            else {
                int n = -i - 1;
                Hero hero = party.get(n);
                double px = n == 0 ? field.x() : field.followerX(n);
                int x = sx(px), y = sy(entry[0]);
                c.fill(x - 9, y - 2, 18, 4, 0x50000000);
                game.heroes.draw(c, game.heroes.gameFor(zone.game, hero.id), hero.id,
                        (field.moving() && game.screen() == this) ? Heroes.WALK : Heroes.IDLE, x, y, field.facingLeft(), ticks + n * 7,
                        SceneDraw.plain(), -1);
            }
        }
    }

    private void drawSpot(Game game, SceneCanvas c, Field.Spot spot) {
        if (spot.done && spot.hostile()) return;
        int x = sx(spot.x(ticks)), y = sy(spot.homeY);
        if (x < -80 || y < -80 || x > c.width() + 80 || y > c.height() + 80) return;
        c.fill(x - 10, y - 2, 20, 4, 0x50000000);
        switch (spot.kind) {
            case ENCOUNTER, MIDBOSS, BOSS -> {
                game.enemies.draw(c, spot.group.get(0), x, y, ticks, SceneDraw.plain());
                if (spot.kind == Field.Kind.ENCOUNTER) game.font.shadowed(c, "x" + spot.group.size(), x + 10, y - 26, Ui.DIM);
            }
            case MONITOR -> sprite(game, c, "s3k:monitor", spot.done ? 11 : Math.max(0, spot.item.icon), x, y - 15);
            case STARPOST -> {
                sprite(game, c, "s3k:starpost", 0, x, y - 32);
                sprite(game, c, "s3k:starpost", spot.done ? 2 : 1, x, y - 56);
            }
            case MERCHANT -> {
                sprite(game, c, zone.game + ":pocky", 0, x, y - 8);
                game.font.shadowed(c, "Shop", x - 10, y - 30, Ui.GOLD);
            }
            case FRIEND -> {
                if (zone == Zone.EMERALD_HILL && !game.progress.hasJoined(HeroId.TAILS)) {
                    game.heroes.draw(c, "s2", HeroId.TAILS, Heroes.IDLE, x, y, true, ticks, SceneDraw.plain(), -1);
                } else sprite(game, c, zone.game + ":flicky", 0, x, y - 8);
                game.font.shadowed(c, spot.done ? "..." : "!", x - 2, y - 30, Ui.GOLD);
            }
            case DISCOVERY -> {
                sprite(game, c, "s3k:ring", (int) (ticks / 8 % 4), x, y - 18);
                game.font.shadowed(c, spot.done ? "Recorded" : spot.label,
                        x - game.font.width(spot.done ? "Recorded" : spot.label) / 2, y - 38, spot.done ? Ui.DIM : Ui.GOLD);
            }
        }
    }
    private void sprite(Game game, SceneCanvas c, String key, int frame, int x, int y) {
        SceneSpriteSet set = game.art.sprites(key);
        if (set != null) c.draw(set.frame(frame), x, y, SceneDraw.plain());
    }
    private void hud(Game game, SceneCanvas c) {
        int w = c.width(), h = c.height();
        c.fill(0, 0, w, 32, 0xE0102030);
        game.font.draw(c, zone.label.toUpperCase(), 8, 5, Ui.GOLD);
        game.font.draw(c, entranceQueued ? field.objective() : "Someone is calling from the trail ahead...", 8, 18, Ui.TEXT);
        miniMap(c, w - 82, 40);
        int py = 95;
        for (Hero hero : game.progress.party()) {
            c.fill(w - 82, py, 76, 22, 0xC0102030);
            game.font.draw(c, hero.id.label + " L" + hero.level(), w - 78, py + 2, Ui.TEXT);
            game.ui.bar(c, w - 78, py + 13, 66, 3, hero.hp(), hero.maxHp(), 0);
            game.ui.bar(c, w - 78, py + 18, 66, 2, hero.ep(), hero.maxEp(), Ui.EP);
            py += 25;
        }
        Field.Spot nearby = field.nearby(ticks);
        String prompt = nearby != null ? "A / Enter: " + nearby.label : "Arrows: explore   Shift: run   M: menu / journal";
        c.fill(0, h - 17, w, 17, 0xE0102030);
        game.font.centered(c, prompt, w / 2, h - 12, nearby != null ? Ui.GOLD : Ui.TEXT);
        if (toastTicks > 0 && toast != null) {
            List<String> lines = game.font.wrap(toast, w - 40);
            game.ui.window(c, 12, 35, w - 24, lines.size() * 11 + 12);
            for (int i = 0; i < lines.size(); i++) game.font.draw(c, lines.get(i), 20, 41 + i * 11, Ui.TEXT);
        }
    }
    private void miniMap(SceneCanvas c, int x, int y) {
        c.fill(x, y, 76, 51, 0xE0102030);
        for (int my = 0; my < 40; my++) for (int mx = 0; mx < 60; mx++) {
            if (field.walkable(mx * 16 + 8, my * 16 + 8)) c.fill(x + 8 + mx, y + 5 + my, 1, 1, 0xFF4B776D);
        }
        for (Field.Spot spot : field.spots) {
            if (spot.done && spot.kind != Field.Kind.STARPOST) continue;
            int colour = spot.kind == Field.Kind.DISCOVERY ? Ui.GOLD : spot.hostile() ? Ui.BAD : Ui.GOOD;
            c.fill(x + 7 + (int) spot.homeX / 16, y + 4 + (int) spot.homeY / 16, 2, 2, colour);
        }
        c.fill(x + 7 + (int) field.x() / 16, y + 4 + (int) field.y() / 16, 3, 3, Ui.TEXT);
    }

    public void journal(Game game) {
        List<Story.Line> lines = new ArrayList<>();
        lines.add(new Story.Line(null, zone.label + ": " + field.objective()));
        for (Field.Spot spot : field.spots) {
            if (spot.kind != Field.Kind.DISCOVERY) continue;
            if (spot.done) lines.addAll(game.story.scene(zone.key + "-" + spot.id));
            else lines.add(new Story.Line(null, spot.id.equals("memory")
                    ? "An echo waits at the northwest ruins. Take the path north of the trail camp."
                    : "An anchor relay is calling from the southeast. The bridge and the lakeside paths all lead there."));
        }
        lines.add(new Story.Line(null, "Starposts restore HP and EP for free. Gold marks are story discoveries; red marks are visible foes. Walk around patrols or use Flee."));
        game.swap(new StoryScreen(game, lines, this, () -> game.swap(new MenuScreen(game, this, false))));
    }
}
