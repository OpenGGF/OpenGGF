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
import threeislands.field.DungeonArt;
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
    private final DungeonArt dungeonArt;
    private final FieldScreen overworld;

    public FieldScreen(Game game, Stage stage, Field field) {
        this(game, stage, field, null, null);
    }

    public FieldScreen(Game game, Stage stage, Field field, DungeonArt dungeonArt, FieldScreen overworld) {
        this.stage = stage;
        this.field = field;
        this.dungeonArt = dungeonArt;
        this.overworld = overworld;
        zone = stage.zone;
        entranceQueued = field.dungeon != null || (game.progress.seen(zone.key + "-enter") && game.progress.seen(zone.island().key() + "-arrive"));
        field.restore(game.progress);
        camera(game);
    }
    public String name() { return field.dungeon == null ? "FIELD" : "DUNGEON"; }
    public Field field() { return field; }
    public Stage stage() { return stage; }
    public Zone zone() { return zone; }
    public FieldScreen overworld() { return overworld; }

    public void playMusic(Game game) {
        game.audio.music(zone.game, field.dungeon == null ? zone.music : field.dungeon.music(), zone.island().mapMusic);
    }

    public void update(Game game) {
        ticks++;
        game.progress.tick();
        playMusic(game);
        if (toastTicks > 0) toastTicks--;
        if (game.transitioning()) return;
        if (game.controls.menu()) { game.swap(new MenuScreen(game, this, false)); return; }
        int dx = game.controls.horizontal();
        if (exitCooldown > 0) exitCooldown--;
        if (field.dungeon != null && dx < 0 && field.atStart()) { game.leaveDungeon(this); return; }
        if (field.dungeon == null && exitCooldown == 0 && ((dx < 0 && field.atStart())
                || (dx > 0 && field.x() >= field.exitX() - (game.canTravelForward(zone) ? 0 : 18) && Math.abs(field.y() - 336) < 44))) {
            if (!game.travel(this, dx > 0)) {
                if (dx > 0) speak(game, "Sonic", "The barrier's still powered. I can't get through here yet.");
                else say("The trail disappears into the surf.");
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
        cameraX = Math.max(0, Math.min(field.width() - game.width(), field.x() - game.width() / 2.0));
        double entranceLook = 0;
        if (field.dungeon == null) {
            var door = field.entrance();
            double distance = Math.hypot(field.x() - door.homeX, field.y() - door.homeY);
            entranceLook = 56 * Math.max(0, 1 - distance / 192);
        }
        cameraY = Math.max(0, Math.min(field.height() - game.height(), field.y() - game.height() / 2.0 - 16 - entranceLook));
    }
    public int sx(double x) { return (int) Math.round(x - cameraX); }
    public int sy(double y) { return (int) Math.round(y - cameraY); }

    public double worldX(int screenX) { return screenX + cameraX; }
    public double worldY(int screenY) { return screenY + cameraY; }

    private void touch(Game game, Field.Spot spot) {
        switch (spot.kind) {
            case MECHANISM -> {
                String response = field.mechanism(game.progress, spot);
                game.audio.sfx(Audio.SFX_STARPOST);
                game.save();
                game.swap(new StoryScreen(game, List.of(new Story.Line(null, response)), this, () -> game.swap(this)));
            }
            case CLUE -> {
                if (spot.id.equals("route-note") || spot.id.equals("dungeon-note")) {
                    field.complete(game.progress, spot);
                    game.save();
                    speak(game, null, field.puzzle.instructions());
                    return;
                }
                game.replayStory(zone.key + "-" + spot.id, this, () -> {
                if (!spot.done && spot.id.equals("orchard-letter")) game.progress.addItem(threeislands.core.Item.ONE_UP, 1);
                field.complete(game.progress, spot);
                game.save();
                game.swap(this);
            });
            }
            case DUNGEON -> game.enterDungeon(this);
            case GUARDIAN -> game.battle(this, spot, spot.group);
            case RELIC -> {
                if (!field.relicReady()) {
                    say("The inner chamber needs its sentries defeated and its mechanisms restored.");
                    return;
                }
                game.replayStory(zone.key + "-memory", this, () -> {
                    game.completeDungeon(this, spot);
                    say("The party catches its breath.");
                });
            }
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
                game.progress.setResumeDungeon(field.dungeon != null);
                game.save();
                game.audio.sfx(Audio.SFX_STARPOST);
                if (field.dungeon != null) {
                    say("Party restored. Journey saved inside the dungeon.");
                    return;
                }
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
                game.save();
                Runnable returnToField = () -> {
                    if (helped) say("Received 2 Super Rings.");
                    game.swap(this);
                };
                boolean followUp = zone == Zone.GREEN_HILL ? game.progress.seen("ghz-rescue")
                        : game.progress.seen(zone.key + "-field-memory");
                game.replayStory(zone.key + (followUp ? "-friend-after" : "-friend"), this, returnToField);
            }
            case DISCOVERY -> {
                if (!field.relayReady()) {
                    game.replayStory(zone.key + "-relay-sealed", this, () -> game.swap(this));
                    return;
                }
                game.replayStory(zone.key + "-" + spot.id, this, () -> {
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
            }
            case MIDBOSS -> {
                if (!field.anchorExposed()) { shielded(game); return; }
                game.playStory(zone.key + "-mid", this, () -> game.battle(this, spot, spot.group));
            }
            case BOSS -> {
                if (!field.anchorExposed()) { shielded(game); return; }
                if (!field.bossReady()) { speak(game, "Sonic", "That other machine's still covering it. I can't get close yet."); return; }
                if (!game.chapterBossReady(zone)) {
                    say("The shield pulses in time with the island's other anchors.");
                    return;
                }
                game.playStory(zone.key + "-boss", this, () -> game.battle(this, spot, spot.group));
            }
        }
    }
    private void speak(Game game, String speaker, String text) {
        game.swap(new StoryScreen(game, List.of(new Story.Line(speaker, text)), this, () -> game.swap(this)));
    }
    private void shielded(Game game) {
        speak(game, "Sonic", "That light throws me back before I can touch it. Something else is feeding its shield.");
    }
    private void say(String text) { toast = text; toastTicks = 210; }
    public void afterBattle(Game game, Field.Spot spot, boolean won) {
        if (won) {
            field.complete(game.progress, spot);
            if (field.dungeon != null) {
                game.progress.setResume(zone, 1);
                game.progress.setResumeDungeon(true);
                game.save();
                say(spot.kind == Field.Kind.GUARDIAN ? "The sentries fall. The gate opens." : "Caught your breath: recovered HP and EP.");
                game.swap(this);
                return;
            }
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
        if (dungeonArt != null) dungeonArt.draw(c, field, cameraX, cameraY, game.ticks());
        else stage.fieldArt.draw(c, field, cameraX, cameraY, game.ticks());
        if (field.dungeon == null && field.sealed(field.exitX(), 336)) {
            int edge = sx(field.exitX() - 8);
            c.fill(edge, sy(88), 24, field.height() - 136, 0x604D60C0);
            for (int i = 0; i < 3; i++) {
                c.fill(edge + i * 6, sy(88), 2, field.height() - 136, 0xB0ACCFFF);
            }
        }
        if (field.dungeon != null) game.font.shadowed(c, "< Exit", sx(60), sy(312), Ui.GOLD);
        if (hidden == null && field.dungeon == null) {
            Zone next = null;
            for (Zone candidate : Zone.values()) if (candidate.ordinal() > zone.ordinal() && game.art.has(candidate.game)) { next = candidate; break; }
            if (next != null) {
                String label = game.canTravelForward(zone) ? "To " + next.label + " ->" : "Rift-sealed trail";
                game.font.shadowed(c, label, sx(field.exitX()) - game.font.width(label) - 54, sy(288), Ui.GOLD);
                if (next.island() != zone.island() && game.canTravelForward(zone)) {
                    SceneSpriteSet ring = game.art.sprites("s3k:ring");
                    if (ring != null) c.draw(ring.frame((int) (game.ticks() / 8 % 4)), sx(field.exitX()), sy(328), SceneDraw.plain().withScale(3));
                }
            }
        }
        if (showParty && field.dungeon != null && zone == Zone.GREEN_HILL && field.dungeonComplete()) {
            sprite(game, c, "s1:flicky", (int) (ticks / 8 % 2), sx(field.followerX(1)), sy(field.followerY(1)) - 8);
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
            case ENCOUNTER, MIDBOSS, BOSS, GUARDIAN -> {
                if ((spot.kind == Field.Kind.BOSS || spot.kind == Field.Kind.MIDBOSS) && !field.anchorExposed()) {
                    c.fill(x - 28, y - 54, 56, 56, 0x504D60C0);
                    c.fill(x - 29, y - 55, 2, 58, 0xC0ACCFFF);
                    c.fill(x + 27, y - 55, 2, 58, 0xC0ACCFFF);
                    c.fill(x - 29, y - 55, 58, 2, 0xC0ACCFFF);
                }
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
                if (zone == Zone.GREEN_HILL && game.progress.seen("ghz-dungeon-complete")) {
                    sprite(game, c, "s1:flicky", 1, x + 18, y - 8);
                }

            }
            case CLUE -> {
                // Small stone plinths: a local invitation, not a map-wide quest marker.
                c.fill(x - 9, y - 12, 18, 12, 0xFF777E89);
                c.fill(x - 7, y - 12, 14, 3, spot.done ? 0xFFAAA894 : 0xFFDED5AF);
            }
            case MECHANISM -> {
                sprite(game, c, "s3k:starpost", spot.done ? 2 : 1, x, y - 16);
                String mark = spot.id.contains("-switch-") ? spot.done ? "Set" : spot.label : spot.id.startsWith("bell-") ? switch (spot.id) {
                    case "bell-dawn" -> "Sunrise"; case "bell-noon" -> "High sun"; default -> "Sunset";
                } : spot.done ? "Closed" : "Open";
                game.font.shadowed(c, mark, x - game.font.width(mark) / 2, y - 42, Ui.DIM);
            }
            case DUNGEON -> {
                game.font.shadowed(c, spot.label, x - game.font.width(spot.label) / 2, y - 124, Ui.GOLD);
            }
            case RELIC -> {
                if (zone == Zone.GREEN_HILL) {
                    if (!spot.done) sprite(game, c, "s1:flicky", (int) (ticks / 8 % 2), x, y - 8);
                } else sprite(game, c, "s3k:ring", (int) (ticks / 8 % 4), x, y - 18);
                game.font.shadowed(c, spot.done ? "Recorded" : spot.label,
                        x - game.font.width(spot.done ? "Recorded" : spot.label) / 2, y - 38, Ui.GOLD);
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
        c.fill(0, 0, w, 17, 0xE0102030);
        game.font.draw(c, field.dungeon == null ? zone.label.toUpperCase() : field.dungeon.label().toUpperCase(), 8, 5, Ui.GOLD);
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
        double scaleX = field.width() / 60.0, scaleY = field.height() / 40.0;
        for (int my = 0; my < 40; my++) for (int mx = 0; mx < 60; mx++) {
            if (field.walkable((mx + 0.5) * scaleX, (my + 0.5) * scaleY)) c.fill(x + 8 + mx, y + 5 + my, 1, 1, 0xFF4B776D);
        }
        // Geography and your own position remain useful; unseen secrets are not advertised.
        for (Field.Spot spot : field.spots) {
            if (!spot.done || spot.hostile() || spot.kind == Field.Kind.MONITOR) continue;
            c.fill(x + 7 + (int) (spot.homeX / scaleX), y + 4 + (int) (spot.homeY / scaleY), 2, 2, Ui.DIM);
        }
        c.fill(x + 7 + (int) (field.x() / scaleX), y + 4 + (int) (field.y() / scaleY), 3, 3, Ui.TEXT);
    }

    public void journal(Game game) {
        List<Story.Line> lines = new ArrayList<>();
        lines.addAll(game.story.scene(zone.island().key() + "-purpose"));
        // Recorded observations only; opening the journal cannot discover content for the player.
        for (String id : new String[] {"memory", "signal", "garden-verse", "garden-song", "orchard-note", "orchard-letter", "horizon"}) {
            if (game.progress.seen(zone.key + "-field-" + id) || game.progress.seen(zone.key + "-" + id)) {
                lines.addAll(game.story.scene(zone.key + "-" + id));
            }
        }
        String note = field.dungeon == null ? "route-note" : "dungeon-note";
        if (game.progress.seen(zone.key + "-field-" + note)) lines.add(new Story.Line(null, field.puzzle.instructions()));
        if (lines.isEmpty()) lines.add(new Story.Line(null, "The pages are still blank."));
        game.swap(new StoryScreen(game, lines, this, () -> game.swap(new MenuScreen(game, this, false))));
    }
}
