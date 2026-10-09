package threeislands;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import threeislands.art.Art;
import threeislands.art.EnemyArt;
import threeislands.art.Font;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.EnemyKind;
import threeislands.core.HeroId;
import threeislands.core.Island;
import threeislands.core.Progress;
import threeislands.core.SaveCodec;
import threeislands.core.Story;
import threeislands.core.Zone;
import threeislands.field.Field;
import threeislands.field.Dungeon;
import threeislands.field.DungeonArt;
import threeislands.field.FieldPath;
import threeislands.field.Stage;
import threeislands.screen.BattleScreen;
import threeislands.screen.EndingScreen;
import threeislands.screen.FieldScreen;
import threeislands.screen.LoadingScreen;
import threeislands.screen.Screen;
import threeislands.screen.StoryScreen;
import threeislands.screen.TitleScreen;
import threeislands.view.Controls;
import threeislands.view.Ui;

/**
 * Shared services and the story flow. Screens call back into here to move between the title,
 * island maps, fields, battles and story scenes; this class decides what comes next (who joins,
 * which emerald is returned, which island follows) so the rules live in one place.
 */
public final class Game {
    public static final String SAVE = "save.txt";
    public static final String BACKUP = "save.bak";

    public final SceneContext ctx;
    public final Art art;
    public final Heroes heroes;
    public final EnemyArt enemies;
    public final Ui ui;
    public final Font font;
    public final Audio audio;
    public final Controls controls = new Controls();
    public final Story story;
    public Progress progress;
    private Screen screen;
    private Screen next;
    private int fade;
    private long ticks;
    private final List<String> notices = new ArrayList<>();
    private Stage cachedStage;
    private final java.util.Map<Zone, DungeonArt> dungeonArt = new java.util.EnumMap<>(Zone.class);

    public Game(SceneContext ctx, byte[] font, byte[] script) {
        this.ctx = ctx;
        this.font = new Font(font);
        this.ui = new Ui(this.font);
        this.art = new Art(ctx.art());
        this.heroes = new Heroes(art);
        this.enemies = new EnemyArt(art, heroes);
        this.audio = new Audio(ctx);
        this.story = new Story(script);
        this.progress = new Progress(1);
        controls.bind(ctx);
        for (Island island : Island.values()) {
            if (!art.has(island.game)) notices.add(name(island.game) + " ROM not found: " + island.label + " will be skipped.");
        }
        swap(new TitleScreen(this));
    }

    public static String name(String game) {
        return switch (game) {
            case "s1" -> "Sonic 1";
            case "s2" -> "Sonic 2";
            default -> "Sonic 3 & Knuckles";
        };
    }

    public List<String> notices() { return List.copyOf(notices); }
    public int width() { return ctx.width(); }
    public int height() { return ctx.height(); }
    public long ticks() { return ticks; }
    public Screen screen() { return screen; }

    /** Changes screen with a short fade through black. */
    public void go(Screen target) {
        next = target;
        if (fade > 12) fade = 24 - fade;
    }

    /** Changes screen immediately (overlays such as menus and story over the current view). */
    public void swap(Screen target) {
        screen = target;
        next = null;
        fade = 0;
    }

    public void update() {
        ticks++;
        controls.bind(ctx);
        audio.tick();
        if (next != null) {
            fade = Math.max(1, fade + 1);
            if (fade >= 12) {
                screen = next;
                next = null;
                fade = 12;
            }
            return;
        }
        if (fade > 0 && ++fade >= 24) fade = 0;
        screen.update(this);
    }

    public void draw(SceneCanvas canvas) {
        screen.draw(this, canvas);
        if (fade > 0) {
            int alpha = fade <= 12 ? fade * 255 / 12 : (24 - fade) * 255 / 12;
            canvas.fill(0, 0, canvas.width(), canvas.height(), Math.max(0, Math.min(255, alpha)) << 24);
        }
    }

    public boolean transitioning() {
        return next != null;
    }

    // ------------------------------------------------------------------ saves

    public boolean hasSave() {
        return readSave().isPresent();
    }

    public Optional<Progress> readSave() {
        for (String name : List.of(SAVE, BACKUP)) {
            try {
                Optional<String> text = ctx.storage().read(name);
                if (text.isPresent()) return Optional.of(SaveCodec.decode(text.get()));
            } catch (RuntimeException damaged) {
                // Try the backup next.
            }
        }
        return Optional.empty();
    }

    public void save() {
        String text = SaveCodec.encode(progress);
        try {
            Optional<String> old = ctx.storage().read(SAVE);
            old.ifPresent(previous -> ctx.storage().write(BACKUP, previous));
            ctx.storage().write(SAVE, text);
        } catch (RuntimeException unavailable) {
            // Storage problems never stop play; the next save tries again.
        }
    }

    // ------------------------------------------------------------------ story flow

    public void newGame() {
        progress = new Progress(System.nanoTime() ^ 0x7131A5D5L);
        enterZone(firstAvailable(), 0);
    }

    private Zone firstAvailable() {
        for (Zone zone : Zone.values()) {
            if (art.has(zone.game)) return zone;
            skipMissing(zone);
        }
        return Zone.ANGEL_ISLAND;
    }

    private void skipMissing(Zone zone) {
        progress.clear(zone);
        progress.addEmerald(zone.emerald);
        for (var hero : progress.party()) if (hero.level() < zone.level + 1) hero.setLevel(zone.level + 1);
        if (zone.islandIndex >= Island.WEST.ordinal()) progress.join(HeroId.TAILS);
        if (zone == Zone.ANGEL_ISLAND) progress.join(HeroId.KNUCKLES);
    }

    private Zone resumeDestination() {
        if (progress.resumeZone() >= 0) {
            Zone zone = Zone.values()[progress.resumeZone()];
            if (art.has(zone.game)) return zone;
        }
        for (Zone zone : Zone.values()) {
            if (!art.has(zone.game)) { if (!progress.isCleared(zone)) skipMissing(zone); }
            else if (!progress.isCleared(zone)) return zone;
        }
        return firstAvailable();
    }

    public void continueGame(Progress loaded) {
        progress = loaded;
        Zone zone = resumeDestination();
        enterZone(zone, progress.resumeZone() == zone.ordinal() ? progress.resumeX() : 0);
    }

    /** Used by chapter transitions and legacy debug tools; travel always arrives in the world. */
    public void arrive(Island island) {
        for (Zone zone : Zone.values()) {
            if (zone.islandIndex < island.ordinal()) continue;
            if (art.has(zone.game)) { enterZone(zone, 0); return; }
            skipMissing(zone);
        }
    }

    /** The neighbouring field in the world, skipping chapters whose ROM is unavailable. */
    public Zone neighbour(Zone current, int direction) {
        for (int i = current.ordinal() + direction; i >= 0 && i < Zone.values().length; i += direction) {
            if (art.has(Zone.values()[i].game)) return Zone.values()[i];
        }
        return null;
    }

    public boolean canTravelForward(Zone current) {
        Zone next = neighbour(current, 1);
        if (next == null) return false;
        if (current == Zone.ANGEL_ISLAND && !progress.isCleared(current)) return false;
        return next.island() == current.island() || progress.islandComplete(current.island());
    }

    public boolean chapterBossReady(Zone zone) {
        List<Zone> chapter = Zone.of(zone.island());
        if (chapter.get(chapter.size() - 1) != zone) return true;
        for (Zone earlier : chapter) if (earlier != zone && !progress.isCleared(earlier)) return false;
        return true;
    }

    /** Walking is the only travel UI. Local paths are open; chapter crossings follow the story. */
    public boolean travel(FieldScreen from, boolean forward) {
        Zone current = from.zone();
        if (forward && !canTravelForward(current)) return false;
        Zone destination = neighbour(current, forward ? 1 : -1);
        if (destination == null) return false;
        if (forward) for (int i = current.ordinal() + 1; i < destination.ordinal(); i++) skipMissing(Zone.values()[i]);
        enterZone(destination, forward ? 0 : -1);
        return true;
    }

    /** A short scene begins after the player walks into an arrival, over the live field. */
    public void fieldEntrance(FieldScreen field) {
        Zone zone = field.zone();
        Runnable zoneScene = () -> playStory(zone.key + "-enter", field, () -> {
            save();
            swap(field);
        });
        if (!progress.seen(zone.island().key() + "-arrive")) {
            playStory(zone.island().key() + "-arrive", field, () -> {
                joinFor(zone.island());
                zoneScene.run();
            });
        } else zoneScene.run();
    }

    /** Tails joins on West Side Island; Knuckles joins after the Angel Island rivalry. */
    private void joinFor(Island island) {
        if (island == Island.WEST) progress.join(HeroId.TAILS);
        if (island.ordinal() > Island.ANGEL.ordinal()) {
            progress.join(HeroId.TAILS);
            progress.join(HeroId.KNUCKLES);
        }
    }

    /**
     * Plays a story scene over {@code background} (or the current screen), then runs
     * {@code then}. Scenes already seen are not repeated; missing scenes are skipped.
     */
    public void playStory(String scene, Screen background, Runnable then) {
        if (!story.has(scene) || progress.seen(scene)) {
            then.run();
            return;
        }
        progress.markSeen(scene);
        swap(new StoryScreen(this, story.scene(scene), background == null ? screen : background, then));
    }

    /** Plays a scene even when already seen (village talk). */
    public void replayStory(String scene, Screen background, Runnable then) {
        if (!story.has(scene)) {
            then.run();
            return;
        }
        swap(new StoryScreen(this, story.scene(scene), background == null ? screen : background, then));
    }

    /** Loads a zone (level kit, route, block pictures and foes) behind a loading screen. */
    public void enterZone(Zone zone, int checkpoint) {
        Screen under = screen instanceof FieldScreen ? screen : null;
        go(new LoadingScreen(this, zone, under, stage -> openField(zone, checkpoint, stage)));
    }

    private void openField(Zone zone, int checkpoint, Stage stage) {
        if (stage == null) throw new IllegalStateException("The starting field requires its configured ROM");
        boolean inside = progress.resumeDungeon() && progress.resumeZone() == zone.ordinal();
        progress.setIsland(zone.island());
        Field field = new Field(zone, stage.path);
        field.restore(progress);
        if (checkpoint > 0) field.resumeAtCamp(checkpoint);
        else if (checkpoint == -1) field.setPosition(field.exitX() - 32, 336);
        progress.setResume(zone, checkpoint == -1 || checkpoint == 2 ? 2 : checkpoint > 0 ? 1 : 0);
        FieldScreen outside = new FieldScreen(this, stage, field);
        swap(outside);
        if (inside) enterDungeon(outside);
        else save();
    }

    /** The stage for a zone, reusing the last one built. */
    public Stage stage(Zone zone) {
        if (cachedStage != null && cachedStage.zone == zone) return cachedStage;
        var kit = art.kit(zone);
        if (kit == null) return null;
        FieldPath path = Stage.route(zone, kit);
        cachedStage = new Stage(zone, kit, path, art.rom(zone.game));
        return cachedStage;
    }

    /** A boss is beaten: return its emerald, play the clear scene, then move the story on. */
    public void zoneCleared(Zone zone, Screen background) {
        boolean emerald = zone.emerald >= 0 && (progress.emeralds() & (1 << zone.emerald)) == 0;
        progress.addEmerald(zone.emerald);
        progress.completeChapter(zone);
        progress.setResume(zone, 2);
        if (zone == Zone.ANGEL_ISLAND) progress.join(HeroId.KNUCKLES);
        progress.restAll();
        if (emerald) audio.jingle(threeislands.audio.Audio.MUS_EMERALD);
        save();
        playStory(zone.key + "-clear", background, () -> {
            if (zone == Zone.DEATH_EGG) {
                progress.setFinished(true);
                save();
                playStory("ending", background, () -> go(new EndingScreen(this)));
            } else {
                save();
                swap(background);
            }
        });
    }

    /** Opens a battle against {@code group} where the party stands. */
    public void battle(FieldScreen field, Field.Spot spot, List<EnemyKind> group) {
        swap(new BattleScreen(this, field, spot, group));
    }

    /** Each story landmark has an independent interior, with its own ROM artwork. */
    public void enterDungeon(FieldScreen outside) {
        Dungeon dungeon = Dungeon.of(outside.zone());
        DungeonArt graphics = dungeonArt.computeIfAbsent(outside.zone(), ignored -> {
            var kit = art.kit(dungeon.zone().game, dungeon.artZone(), dungeon.artAct());
            if (kit == null) throw new IllegalStateException("Missing interior kit for " + dungeon.label());
            return new DungeonArt(kit, dungeon);
        });
        Field inside = new Field(outside.zone(), outside.stage().path, dungeon);
        inside.restore(progress);
        FieldScreen room = new FieldScreen(this, outside.stage(), inside, graphics, outside);
        progress.setResume(outside.zone(), 1);
        progress.setResumeDungeon(true);
        save();
        playStory(outside.zone().key + "-dungeon-enter", room, () -> swap(room));
    }

    public void leaveDungeon(FieldScreen room) {
        FieldScreen outside = room.overworld();
        if (outside == null) throw new IllegalStateException("Dungeon lost its entrance");
        outside.field().restore(progress);
        outside.field().setPosition(240, 176);
        progress.setResume(outside.zone(), 1);
        save();
        if (outside.zone() == Zone.GREEN_HILL && room.field().dungeonComplete()) {
            playStory("ghz-rescue", outside, () -> { save(); swap(outside); });
        } else swap(outside);
    }

    /** Collecting the inner-room discovery advances its journal entry and grants rewards once. */
    public void completeDungeon(FieldScreen room, Field.Spot relic) {
        Field field = room.field();
        if (!field.guardDefeated(0) || !field.guardDefeated(1)) return;
        boolean first = !progress.seen(field.zone.key + "-field-memory");
        field.complete(progress, relic);
        if (first) {
            for (var hero : progress.party()) if (hero.level() < field.zone.level + 1) hero.setLevel(field.zone.level + 1);
            progress.addItem(threeislands.core.Item.SUPER_RING, 2);
        }
        progress.restAll();
        progress.setResume(field.zone, 1);
        progress.setResumeDungeon(true);
        save();
        swap(room);
    }

    public void title() {
        audio.fadeOut();
        go(new TitleScreen(this));
    }
}
